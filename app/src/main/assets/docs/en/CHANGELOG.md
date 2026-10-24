# UIN Tool Changelog

This document records all important version updates and feature changes of UIN Tool.

---

## Version Naming Rules

- **Major version**: Major architectural changes or incompatible API modifications
- **Minor version**: New features, backward compatible
- **Patch version**: Bug fixes, backward compatible

---

## [6.2.0] - 2026-10-16

> Code editor specialization: the pure in-memory draft surface that was previously only reachable from the plugin-creation wizard has been rewritten into a standalone, externally invocable code editor — full file tree + multi-tab + project-level opening + direct disk I/O + an `ACTION_VIEW` entry point, plus two rounds of Sora Editor capability audits, Unified-component UI polish, and four rounds of root-cause fixes for "syntax highlighting disappears entirely".

### External Entry & File Access

- `CodeEditorActivity` is now `exported=true` in the manifest with its own `ACTION_VIEW` filter (`text/*` plus `application/json|xml|javascript|x-sh`), so other apps can open files in the editor via "Open with" (known cost: the system chooser shows two UIN Tool icons)
- File access is now **request-permission-by-default + direct file I/O**: the editor requests storage permission on entry and reads/writes with `java.io.File` once granted; `content://` remains a `ContentResolver` fallback used only when no real path can be resolved
- Android 11+ uses `MANAGE_EXTERNAL_STORAGE` (with a jump to the system settings page), Android 10 and below use `READ/WRITE_EXTERNAL_STORAGE`
- New `ACTION_OPEN_DOCUMENT_TREE` to pick a project root manually; opening a new folder also builds the file tree
- **Fixed "nothing happens after picking a folder"**: `OpenDocumentTree` returns a **tree URI** (`…/tree/primary%3AMine`), but `resolveRealPath` only handled plain document URIs — `isDocumentUri()` is always false for a tree URI, so the docId mapping was never reached, the `_data` column query that followed always failed, and the caller silently `return`ed. Tree URIs now go through `getTreeDocumentId()` and the same `primary:` / `UUID:` mapping; if the result still cannot be turned into a directory the app **reports the failure explicitly** instead of returning silently
- **Memory pre-check before reading**: the old code relied solely on `catch (OutOfMemoryError)`, but by the time a real OOM happens ART is already thrashing (repeated blocking GC / SoftReference clearing — the device log shows the main thread stalled for 12+ seconds), which reads as a **frozen UI**. A pre-check now runs before the read, requiring **3.25× the file size** (1× byte[] + 2× UTF-16 char[] + 25% headroom), so an oversized file reports "file too large, out of memory" immediately. The threshold floats with the machine's free memory — it is **not** a new fixed size cap; the 25% headroom is measured, not guessed — a 31.4 MB file needed a 62.8 MB char[] with only 6 MB left under the heap ceiling, so a bare 3.0× check passed and then died on a concurrent allocation
- **Fixed "the same file sometimes opens, sometimes reports too large"**: the pre-check reads `freeMemory`, which is only the free space inside the **already committed** heap. Right after closing a big document that memory has not been reclaimed yet, so `available` looks small and the file was wrongly rejected — the log shows the same 29.9 MB file opening successfully and then being blocked 25 seconds later (available=88 MB, threshold=29 MB), while opening it through "open with" (a cold start on an empty heap) worked fine. That is why it failed from the file tree yet worked from outside. The check now force-collects once and re-measures, and only gives up if it still does not fit (only before the read — the bytes are still in hand afterwards, so a GC there would free nothing). **Batch 41 fixed a second layer — measuring right after the gc**: `System.gc()` starts a *concurrent* GC that often returns before its sweep finishes, and measuring immediately **underestimates `available` by 10–48 MB** — on 2026-10-17 the same 31 MB file was rejected twice as "file too large" (21:58:06 and 21:58:36), yet 40 seconds later, once the heap had actually been reclaimed, it opened fine. The check now re-measures every 80 ms for up to 320 ms, letting the sweep finish before deciding
- **Restore the last document after process recreation**: new `last_open_file` / `last_open_uri` persistence. Coming back through the recents screen (a brand-new Activity with an empty Intent — no `ACTION_VIEW`, no `project_dir`) re-opens it instead of dropping straight into the "No file open" empty state where it looked like all content was gone. Plugin-wizard mode likewise re-opens its whole draft set; this also fixes `projectDirPath` being skipped under `fromRestore`, which made the wizard receive `RESULT_CANCELED` and lose its directory. **A failed restore now leaves its reason in the empty state** (file deleted / out of memory / sender's grant revoked) instead of a generic "No file open"; on process recreation, an `ACTION_VIEW` or wizard draft that cannot be re-read also falls back to the last document rather than sitting on an empty screen
- **`singleTop` instance reuse — fixes "re-opening the same file reports too large / gets slower each time"**: `CodeEditorActivity` used the default `standard` launch mode, and file managers send `VIEW` with `CLEAR_TOP`, so every "open with" **destroyed and rebuilt** the activity: the fresh instance had no tabs, and because the previous copy of the same file was still on the heap, it had to read it a second time. The device log shows exactly this — an open succeeds, and 25 seconds later the same file fails, with the heap at 451 MB/475 MB and a 62.8 MB char[] request missing the ceiling by 6 MB, or the pre-check rejecting it because the old copy had not been reclaimed. With `singleTop`, `onNewIntent` reuses the activity, the duplicate check just switches back to the already-open tab, and **re-opening the same file costs zero extra memory**; `onNewIntent` also calls `setIntent()` so a later process recreation receives the latest Intent
- The plugin wizard now writes its draft to `cacheDir/editor_draft/<ts>/` and passes only a `project_dir` string via the Intent, eliminating `TransactionTooLargeException`, the deprecated `getSerializableExtra`, and draft loss on process recreation
- **Long-pressing the top-bar Save icon = system "Save as"** (`ActivityResultContracts.CreateDocument`): lets you pick both the **file name and the location**; the old fallback (directory picker only when writing fails) is unchanged

### Architecture Rewrite

- New `EditorModels` / `EditorViewModel` / `FileAccess` / `EditorSettingsStore` layers; `CodeEditorActivity` grew 43 → 289 lines, `CodeEditorScreen` 1118 → 2000+ lines
- Multi-tab: `EditorTab` derives `isModified` from a `savedContent` / `content` comparison, replacing the old broken `hasChanges` flag that only ever set on `SET_NEW_TEXT` (typing never triggered it, so the unsaved marker was permanently hidden)
- File tree: recursively collects all text files; supports new file / new folder (with nested `a/b` paths), rename and delete; directories expand and collapse
- The `EditorController` bridge now drives off `subscribeEvent(ContentChangeEvent / SelectionChangeEvent)` instead of the old 40×50ms polling hack
- Runtime settings application dedupes on an "already-applied settings" triple so that every keystroke-triggered recomposition no longer re-sets editor properties and forces a relayout

### Editing Features

- Find / replace: only the search field shows by default, the replace field appears via the expand arrow; match counting now subscribes to `PublishSearchResultEvent` (the old code read the count immediately after the async `search()` returned, so it always showed 0 / a stale value), and regex mode passes `RegexBackrefGrammar.DEFAULT` so `$1` backreferences actually work
- Save semantics: save / save as / unsaved-changes confirmation on exit (the old version had no `BackHandler`, so back silently discarded the draft)
- Status bar shows live "line:column / character count"
- Hardware keyboard shortcuts: Ctrl+S save, Ctrl+F find, Ctrl+G go to line, Ctrl+Shift+Z redo (the built-in Ctrl+Z branch ignores Shift and would turn redo back into undo); every other key is passed through
- Overflow menu completed: undo / redo (greyed per `canUndo()` / `canRedo()`), indent / outdent, cut line, select line, go to line, go to document start / end, format, select all
- `SymbolInputView` symbol bar; insertion now goes through the proper `commitText()` entry so `(` auto-closes with `)` and flows through auto-indent and quote handling
- Settings panel with 28 persisted options: font size, tab width, word wrap, line numbers, gutter divider, non-printable characters, bracket matching, indent guides, line-panel dock position, ligatures, cursor blink, selection popup, magnifier, pinch zoom, line-height multiplier, character width, symbol bar, new-line separator (LF / CRLF / CR), highlighted code blocks, function placeholder character, large-file mode, file-tree multi-line names, and more
- Editor theme is now independent of the app theme (dracula by default, 9 Sora themes selectable) and no longer follows the system, which defines no editor theme
- Code formatting: new `CodeFormatters` performs **token-preserving re-indentation for JSON (json / json5 / jsonc)** (only rewrites indentation and post-colon spacing; numbers, string literals, escape spelling, key order, JSON5 single quotes and trailing commas are carried over verbatim, with structural validation so an invalid document is never touched) and **conservative YAML tidy-up** (block scalars preserved byte-for-byte, leading tabs expanded to spaces, trailing whitespace stripped, blank runs collapsed, indentation levels never reordered); computed on a background thread and applied via `onFormatSucceed()` (batch edit = one undo step for the whole replacement), with stale results discarded on tab switch or concurrent typing; unsupported extensions grey out the menu item
- Tapping a `#RRGGBB` color literal in the code copies it to the clipboard and selects the range
- **Fixed "big file opens but the UI can't keep up / GC never stops / switching tabs OOM-crashes / restored session reports file too large" (batch 41)**: the hex-color background scan used to run the **full regex over every line** of a 2M-line file — 22–64 s per pass and roughly 45 MB/s of regex garbage; the device log shows a GC round every 0.5–1.5 s with the heap parked at ~400 MB (UI jank, keystrokes going nowhere), and the scan thread kept the whole old document alive after the editor was destroyed, so when the session was recreated the pinned copy made the memory pre-check reject the restore twice as "file too large" (measured at 21:58:06 and 21:58:36). The scan now (1) **prefilters lines without `#` at zero cost** before touching the regex (the same file dropped from 22–64 s to sub-second), (2) checks a cancellation flag every 4096 lines so `detach()` aborts the scan immediately instead of pinning the old document, and (3) stops after **50,000 highlights per file** (also bounding files where every line is a `#` comment). Additionally, **switching back to a large tab now force-collects once before rebuilding the document**, so two copies of a 200 MB+ document cannot coexist and punch through the heap ceiling (the OutOfMemoryError crash at 21:59:52)
- **Fixed "a too-long file name pushes the trailing ⋮ off screen", plus long-press menu and a multi-line display switch for the file tree (batch 42)**: the row's file-name `Text` had no `weight(1f)`, so it measured against the **whole row width** — the ellipsis only kicked in at full width and a long name shoved the ⋮ out of view. The name is now constrained to the remaining space and clipped with an ellipsis (the old `Spacer(weight)` that pinned the ⋮ to the row end is folded into this weight), so ⋮ is always visible. New editor setting **File tree multi-line names** (off = single clipped line; on = wrapped name with adaptive row height); **long-pressing** a file or folder row now opens the same menu as ⋮ (rename / delete / properties), anchored at the ⋮
- **Full rework of the top bar / tab strip / bottom bar: information bar plus page navigation and richer properties (batch 43)**: the top bar **no longer shows the file name** (the tab strip already has it) and gains **undo / redo** on the right (greyed per `canUndo()/canRedo()`, refreshed by bumping a `historyTick` from the cursor/content callbacks); the tab strip grew 38 → 44dp and the close icon 12 → 16dp so tapping × no longer hits a neighbouring tab, and **long-pressing a tab opens the file's full properties** directly; a new **information bar** sits under the tab strip — `n/N lines`, `n bytes`, `n chars`, `n selected` refresh live with the cursor and edits (uri/not-yet-saved tabs show — for bytes). the bottom status bar is rearranged: the **page switcher `n/N`** sits bottom-left (only for multi-page documents; tapping ripples open a dropdown whose top holds an integer-only input that must land in 1..N — press Done or tap the list to jump; 2000 lines per page, and the displayed page follows the cursor), with match count, the read-only badge, the **encoding button** and ⚙ settings on the right (**pinned rightmost**, finalized in batch 44); the **bottom word-wrap icon was removed** (the toggle remains in the editor settings), and Ln/Col plus the selection count moved to the information bar. The **properties dialog** (shared by the tree's ⋮ and tab long-press) gains **lines / bytes / characters**, tallied on a background stream without ever loading the whole file into memory (>128MB reports byte size only), and **every row is tappable (ripple) to copy that value**
- **Fixed the crash on tapping the page switcher, bottom/top bar tweaks per feedback, and rapid property-copy toasts now show only the latest (batch 44)**: the page list inside the dropdown used a LazyColumn — a DropdownMenu popup measures its content with **intrinsics**, which SubcomposeLayout-based components like LazyColumn do not support, so opening it threw `IllegalStateException` (measured in crash_2026-10-18.log at 11:43:45, stack running through AndroidPopup → Scroll → Lazy). Replaced with a plain Column capped at 220dp plus vertical scrolling (ordinary layouts provide intrinsics, so the popup measures fine). Bottom bar per feedback: **page switcher alone bottom-left, encoding button and ⚙ settings moved back to the right with settings pinned rightmost**. Top bar: **undo/redo moved to the leftmost position among the action icons** and tightened to 40dp (others stay 48dp). When tapping several property rows in a row, "Copied to clipboard" no longer surfaces after growing gaps — the Snackbar host is a serial queue, so `AppToast.show` now **dismisses the visible one first and lets the latest message appear immediately**
- **Removed the top-bar "…" mystery title, tightened all six icons, full encoding display, page input matches the search box, notification-stack audit complete (batch 45)**: the user reported "three horizontal dots at the far left of the top bar that don't respond to taps" — that was the title "Code Editor" squeezed to zero width by the action icons and ellipsized into a **plain-text "…"** (not a control), so the **title is removed entirely** per feedback (the file name lives in the tab strip anyway); undo/redo/save/find/syntax/⋮ are now a uniform **40dp**, shrinking the visual gap from 24 to 16dp. The bottom **encoding button lost its 90dp width cap**, showing full names like `Auto-detect·GB18030`. The page-number input in the switcher dropdown now uses the **same `UnifiedTextField` as the search box** (the stock m3 TextField mispositioned the cursor's teardrop inside the popup); `UnifiedTextField` gained optional `keyboardOptions/keyboardActions` pass-through parameters, and an out-of-range page turns red with the new message "Page must be between 1 and N". **App-wide notification-stack audit** (sub-agent sweep): the plugin JS bridge's three raw `Toast` paths (`toast/toastLong/showLoading`) and the hex-color copy's raw `Toast` all now go through AppToast; the plugin `sendNotification` ID changed from a timestamp to a **stable per-plugin ID** so repeats replace in place instead of piling up in the status bar; the remaining ~100 call sites go through AppToast, the editor event flow is latest-wins, and download notifications already use fixed IDs — all confirmed queue-free
- **Huge files open at last: windowed mode, EmEditor-style disk spill (batch 46)**: files that fail the memory pre-check (the whole document does not fit in the heap — including the real "restore after multi-task OOM" incident from the device log) are no longer rejected with "File too large". They now fall back to **windowed mode**: one streaming pass builds a **tiny index** of "byte offset of every page start + total line count" (only tens of KB even at 10 GB — a byte-level scan with no decoding, since the line terminators 0x0A/0x0D cannot occur inside multi-byte sequences of any whitelisted encoding, and UTF-16/32 are scanned as endianness-aligned units), and the editor loads only the **current page (2000 lines)** — the whole file never enters the heap. The info bar shows a **"Windowed" badge** and converts line numbers to **absolute** (the editor's gutter shows page-relative numbers; Help 18.12 explains). The **page switcher really switches pages** now (the outgoing page's edits are flushed to a spool first, then the new page is read from disk and a `contentRev` counter tells the canvas to reset the sora text); "go to line" takes an absolute line and lands on the right page automatically. **Saving = original file + edited pages** streamed into a temp file and **atomically swapped in** — unmodified pages are **byte-copied verbatim** (no decode round-trip, original bytes untouched), edited pages are re-encoded in the file's charset, and the page index is rebuilt afterwards. The save runs on a **plain background thread** that survives leaving the editor ("save and exit" loses nothing). Also: windowed tabs force large-file optimizations (no syntax/indent lines), changing the charset rebuilds the index and returns to page 1, Save As is explicitly blocked (it could only write the current page — silent truncation is worse than an error), and a failed index rebuild locks the tab read-only so a stale offset can never shred the file. The full-load fast path and its guards are untouched — anything that fits still loads whole

- **Windowed mode gains whole-file search/replace: the "Whole file" streaming engine (batch 47)**: paged huge files could previously only search the current page (sora's searcher assumes the whole document in memory). Batch 47 adds the `WholeSearch` streaming engine to close the gap: the search panel gains a **"Whole file" toggle** in windowed mode. With it on, pages are scanned one by one (supplied in order "in-memory edited page → disk spool → original disk", so unsaved edits are visible to the whole-file search immediately), with 450 ms input debounce, live progress and cancel-anytime support (cancellation is checked per page). Results are listed as `absolute line:column` (up to 500 listed, the total is always counted in full), and **tapping a result switches to that page and selects/highlights the match** (`pendingSelection` grew into a length-bearing `PendingSel`, applied via a 4-argument `setSelection` after the page text lands). Replace is **"Replace all (whole file)"** — there is no "current match" in whole-file semantics: every affected page is rewritten into an **on-disk spool** (`cacheDir/uin_spool/`; memory stays at exactly one page, so replacing a high-frequency word in a GB-scale file cannot blow the heap), and saving streams the spool together with the original file through the write-back provider; the spool is cleaned up automatically on save/discard/tab close. Replace is **mutually exclusive** with saving and page switching (all three touch the page supply and spool bookkeeping), and scanning always works on "the text the user sees" — hand-edited pages are migrated into the spool first, and the spool version retires the stale in-memory overlay so it can never be shadowed. Matches spanning the 2000-line page boundary are detected with one page of lookahead overlap and **skipped, never blind-replaced** (the result line reports "N matches spanning page boundaries were skipped"), so a half-replacement across a seam can never happen; regex replacement keeps the `$1` backreference semantics. Everything goes through the write-back spool and **only lands on disk on save**; the result list auto-refreshes after a replace completes. The toggle is hidden in full-load mode — sora already searches the whole document there, no second implementation needed
- **File encoding: auto-detection plus a manual override**. A new **encoding button** in the bottom status bar shows the current encoding directly (`Auto-detect·UTF-8` in auto mode) and opens a picker; in 6.2.0 batch 27 the list was **expanded to the device-supported common set** at the user's request (UTF-8 / GB18030 / GBK / Big5 / Big5-HKSCS / UTF-16 and UTF-32 variants / Shift_JIS / EUC-JP / EUC-KR / ISO-2022-JP / ISO-8859-x / windows-125x / KOI8-R / TIS-620, …), each entry filtered through `Charset.isSupported` so an encoding the device cannot decode never appears — no more picking one and silently falling back to UTF-8. Detection order is **BOM → strict UTF-8 validation → GBK/GB18030 heuristic → ISO-8859-1 fallback**; through 6.1 everything was decoded as UTF-8, so legacy GBK files came out as `锟斤拷`-style mojibake with no way to fix it. Choosing a manual encoding **re-reads the document with it immediately** (the saved baseline is updated too). Saves **write back in the encoding the file was opened with**, so a GBK file is never silently re-saved as UTF-8

### Syntax Highlighting Root Causes

- **Extension → scope mapping**: 7 mappings did not match what the grammar files actually declare (`m/mm` and `c` were wrongly mapped to `source.cpp`, etc.); an unregistered scope now falls back to `text.plain` instead of inheriting the previous file's language; `.tsx` / `.jsx` highlighting is fixed too
- **Only the first grammar per directory registered**: `GrammarDefinition.getName()` returned the directory name and `GrammarRegistry` used it as the dedupe key, so only one grammar per directory could ever register. The grammar file's full path is now used as a globally unique name
- **Shared raw rule table permanently poisoned**: `Grammar` construction calls `ObjectCloner.deepClone`, whose last branch never actually clones plain objects, so the same scope's raw object is shared by the registry and every grammar that `include`s it; whoever compiles first stamps rule ids into the shared object, and later instances throw from `getRule(id)` and kill the analysis thread. Switching language now re-parses a fresh object graph via reflection and evicts the cache; the `testTokenization` probe that concurrently compiled the same instance on a background thread was removed
- **Malformed `captures` in grammar assets**: 4 XML / YAML grammar assets had bare strings nested under `captures`, making tm4e throw `ClassCastException` at compile time; `registerRule` failed before writing the id into the lookup map, so the id stayed in the shared raw forever but was never found → the analysis thread died and the language had no highlighting at all. The 4 assets are fixed and `loadGrammars()` gained a **load-time validator**, so the same class of damage now shows its file name and exact path in the log at startup
- **Upstream underline-token crash**: sora 0.24.4's `TextMateAnalyzer` obtains an `obtainNoExt` span for every token and then calls `setUnderlineColor` on it, so any underlined token (HTML produces them) makes `NoExtSpanImpl` throw `UnsupportedOperationException`, killing the whole `AsyncAnalysis` thread and the file's highlighting. Fixed upstream in 0.24.5 and picked up here with the move to sora 0.24.6
- TextMate theme color fixes: sora uses `>=255` custom color ids that plain `EditorColorScheme.getColor` cannot resolve and returns 0 for (transparent); `TEXT_SELECTED` and `TEXT_ACTION_WINDOW_*` had the same problem and were colored explicitly, fixing "page blank until select-all", "select-all recolors text" and "transparent floating icons"
- Grammar loading now reads the `scopeName` declared by the grammar file itself, skipping injections and damaged assets; two 14-byte stub grammar files whose contents were the literal `404: Not Found` were deleted

### UI Polish

- Full adoption of the Unified component system (`UnifiedDialog` / `UnifiedTextField` / `UnifiedEmptyState` / `UnifiedCard` etc.), plus new `AppColors` / `AppDimens`
- The sidebar became a `ModalNavigationDrawer`: the top-left icon is now the drawer toggle (it used to be an ArrowBack exit arrow) and exit is handled by the back gesture; the drawer is anchored to the left edge so the leftmost swipe opens it without triggering pinch zoom on the content, and dragging the drawer closes it
- File tree filenames moved to `bodyMedium` with row height 34 → 40dp; the overflow menu became a 170dp anchored dropdown instead of a near-full-screen dialog
- Editor chrome (tab bar / status bar / symbol bar / search panel) uniformly uses `pageBackground()` so gradients and glass modes show through and match the rest of the app; modal overlays (dialogs / bottom sheets / drawers) stay opaque, fixing editor dialogs that showed the layer underneath
- Fixed overly translucent UI panels and buttons (`translucentAlpha` default and floor, permanently transparent `glassBorder`)
- Accessibility: `AndroidView` now has a `contentDescription` so TalkBack can read out the file being edited
- The settings panel changed from a bottom `ModalBottomSheet` to a **full-screen settings page** (fixed top bar with back + title, scrolling body), so all 28 items no longer sit cramped in a sheet
- The file-tree overflow menu gained "**Properties**": a dialog showing name / path / type / size / modified time (read-only)
- **Symbol bar made taller**: each symbol now sits in a fixed 44×44dp touch target (previously a ~30dp bare text hotspot) and the font is bumped to `bodyLarge`
- Tab clicks no longer show a ripple (the active tab already has its own background; a ripple only blurred the file name in the 38dp strip)
- **Gutter divider now visible**: TextMate themes set `LINE_DIVIDER` to TRANSPARENT, so the switch never showed anything; it is now re-set to a translucent version of the line-number color
- **Bracket match is visible again, with a thinner stroke (fixes "bracket matching does nothing")**: the stroke width comes from `textBorderWidth`, which had been set to the 4dp text inset and was far too heavy. The previous 6.2.0 build zeroed `HIGHLIGHTED_DELIMITERS_BORDER` to make it thinner, but sora's `drawRowBackgroundRectWithBorder` `return`s entirely when `borderColor == 0`, leaving only sora's default 11%-alpha background — so the feature looked dead. A **visible stroke color is restored** (the theme's bracket foreground color when it has one, otherwise the current line-number color) and `textBorderWidth` is reduced to **1.5dp** (sora's default is 1dp): clearly thinner, still clearly there. Hex swatches keep their borderless design
- **Line panel position now actually applies**: previously only `setDisplayLnPanel` was called and `lnPanelPosition` was never passed to the editor (changing it did nothing), and 6.1 then **assigned it a second time** later in `applyRuntimeSettings`, forcing everything except left/right back to `FOLLOW` (which always hugs the right edge) and overwriting the first assignment. The duplicate is gone and the mode is now `LineInfoPanelPositionMode.FIXED` throughout, so each of the five positions docks where it says. **Worth knowing**: the bubble itself only appears **while dragging the right-hand vertical scrollbar** (sora gates `drawLineInfoPanel` behind `holdVerticalScrollBar()` and exposes no API to force it visible) — that is upstream behaviour and out of scope here
- **System back button now closes overlays one layer at a time**: the old `BackHandler` only knew "close drawer / unsaved confirmation / exit", so pressing back while the settings page or find panel was open **exited the editor outright**. It now unwinds in order: settings → find panel → encoding picker → file-tree drawer → unsaved confirmation → exit
- The horizontal scrollbar thumb got the same rounded pill as the vertical one (it used to be a square solid block)
- **Status bar slimmed down**: the duplicated file name is gone (the top bar and tab strip already show it). The layout is now a **flexible left group taking `weight(1f)` plus a fixed right group (match count / encoding / wrap / settings) that never gets compressed**, with match count and encoding each width-capped and ellipsised — in the 6.1 layout the file name, match count and encoding label claimed space in turn and pushed the ⚙ settings gear off screen on narrow displays

### Performance & Resources

- **Large-file mode (new setting, on by default)**: when content exceeds roughly **2M characters**, it automatically turns off syntax highlighting, sticky scroll and the block guide, and limits hex-color hints to the first 10,000 lines so scrolling stays smooth; **manually picking a syntax still keeps highlighting** (an explicit request wins). **Turning the switch off lifts all of those measures at once** — every large-file performance concession is user-disableable. The same batch **removed the "read-only over 32MB" rule**: any file that fits in memory is editable (the memory pre-check reports "file too large" when it does not), and the read-only flag is now cleared on every open — previously it was only reset by Save As, so once a huge file had been opened, **every file opened afterwards stayed read-only** (one root cause of the user's "large files are not editable" report)
- Hex-color scanning moved off the main thread: it previously ran a full-file regex on the main thread 150ms after each content change (large files felt like "a hiccup after typing a line"); it now scans on a background thread with re-entrancy protection and discards stale results before reordering
- `isModified` changed from an O(n) full-text comparison on every keystroke to an O(1) boolean flag
- `EditorController.detach()` now calls `CodeEditor.release()` (it previously only nulled the field) — this fixes a leaked `TextMateLanguage` background analyzer thread and `Formatter` on every AndroidView recreation
- File reading now returns a three-state `ReadResult` (success / too large / unreadable) and adds a `catch(OutOfMemoryError)` (`catch(Exception)` cannot stop an `Error` and the process would crash), with explicit failure messages; **no fixed size cap** (the former 8MB hard cap is gone) — running out of memory is reported instead of crashing

### Logging

- New `LogcatRecorder`: a background daemon thread started at app launch continuously writes logcat to disk alongside the existing app logs, with rotation and retention policies matched to `Logger` so "clear logs" applies to it too; `READ_LOGS` is signature-level and unavailable to a normal install, but Android lets a process read its own UID's log lines, so the recorder works without the permission
- The log page's logcat mode now reads the on-disk file first, and **export follows the current source** (fixing "switch to logcat and export still writes the empty AppLog file"); export file names also carry the source so the two never overwrite each other
- The developer-tools page no longer embeds its own stale copy of the log viewer; logging is consolidated in `LogViewerScreen`
- **Fixed "after a crash the app reopens into developer tools instead of the log page"**: the crash route was hard-wired to `DevToolsActivity`, but its embedded log section was removed back in batch 37 — the crash content actually lives in `LogViewerScreen` (entered from the Manage page). It now opens `LogViewerActivity`, and the `just_crashed` flag is no longer cleared ahead of the jump: the log page reads it, shows the "an exception occurred, check the crash log below" banner, and clears it itself; a failed jump clears it as a fallback so a stale flag cannot keep re-rerouting every launch

### Completion

- New keyword tables for XML (75) / HTML (49) / CSS / Gradle (48) / config formats (42, shared by json / yaml / toml / ini / properties) — these extensions all returned empty arrays before, so **completion was dead for the config files this app edits most**
- Deliberately lists only tag and property names, never colon-prefixed names like `android:layout_width`: sora's backward prefix scan stops at `:`, so a full name would complete to `android:android:layout_width`
- An unregistered scope falls back to `text.plain` instead of inheriting the previous file's language

### Dependencies & Build

- sora-editor BOM **0.24.4 → 0.24.6** (picks up the upstream underline-token crash fix; both tags' public APIs were compared item by item and no removed member is referenced by this project)
- Kotlin **2.1.0 → 2.3.10** (0.24.6's AARs are built with Kotlin 2.3.x and a 2.1.0 compiler cannot read metadata 2.3.0 — all 35 `e:` errors were this; the version deliberately matches sora's own compiler), with `kotlin.plugin.compose` and `kotlin-stdlib` bumped in lockstep
- Removed the zero-reference dead dependency `language-java` (Java highlighting comes from the TextMate grammar under `assets/textmate/java`, not from that AAR)
- Removed the dead-code class `EditorUiFlags` (all 4 fields unreferenced project-wide)
- Removed the zero-consumer dead `autoSave` switch

### Deliberately Not Done (each verified against the source)

- **XML formatting**: the platform `javax.xml` Transformer is a pretty-printer, not a value-preserving re-indenter (it normalizes attribute quotes, strips whitespace-only text nodes, may drop a DOCTYPE internal subset, and carries XXE-hardening pitfalls), which directly conflicts with "touch whitespace, never touch values"; XML here is mostly AndroidManifest / layout files, which Android Studio already formats
- **`QuickQuoteHandler`**: it can only come from `Language.getQuickQuoteHandler()`, and installing one disables the built-in selection auto-surround; `DefaultSymbolPairs` already auto-closes and auto-surrounds `"` and `'`, so adding it would be a net negative
- **Diagnostics / inlay hints / outline**: Sora has the APIs but the TextMate analyzer produces no data source, so the UI would be dead
- **Snippet completion**: needs a snippet library, i.e. a product decision that has not been made
- **Per-tab undo history**: `Content` already owns its `UndoManager` so tabs are isolated and history is simply cleared on switch; keeping it would require caching `Content` objects, which collides with the `isModified` read points
- **`ContentIO` streaming I/O**: already covered by the memory pre-check (3.25× peak + force-collect) and the OOM fallback (and no hard cap or forced read-only is imposed anymore); the benefit was overestimated
- **`disallowSuggestions`**: when true it force-restarts the input connection and rejects composing text, which would break Chinese / Japanese input

### Batch 53: editor search/replace unified to whole-file + plugin signature fix

- **Search and replace always operate on the whole file for every document (the "Whole file" toggle is gone)**: previously small files used sora's in-page searcher while paged huge files needed a manual "Whole file" toggle — two competing state machines that produced hints only we could understand ("whole-file replace not supported", "please enable Whole file"). The single path is now the `WholeSearch` engine (a single in-memory pass for small files, streaming page-by-page for large ones; 450 ms debounce, live progress, cancellable). Prev/Next move through whole-file results and tapping a result row advances the match cursor too; the status line and the "Search again / All results" buttons sit on two separate rows so long text can never cover the buttons; the full-results list gained a right-side scrollbar and now sizes to content (up to 480 dp) instead of always stretching;
- **Replace routes by document form automatically**: paged files still rewrite page by page into the on-disk write-back spool (lands on disk on save; cross-page-boundary matches are skipped); fully-loaded files now rewrite in one in-memory pass (`WholeSearch.replaceInString`, undo history resets, reaches disk on save), with the confirmation dialog spelling out the difference; the editor is forced read-only while a whole-file replace runs so edits can't be silently overwritten by the replacement;
- **Fixed: plugin updates could never install**: signature verification originally pinned the first-install file hash per plugin ID forever, so every update package of the same plugin was rejected as "tampered". It now stores `version:hash` — same version requires the same hash (real tamper protection), a different version records the new hash and is allowed; legacy bare-hash records migrate automatically, unblocking already-stuck users;
- **Stale results and out-of-bounds protection**: after the document is edited, whole-file results are marked "content changed — results may be outdated" with a manual re-search entry (no automatic re-scan — re-scanning GB-scale files per keystroke is not viable); tapping a stale result / Prev / Next clamps line and column to the current document bounds first, so an outdated line number can no longer crash the editor; switching tabs hides the old tab's results and the same query automatically re-searches the newly active tab;
- **Fix list**: early-exit branches (empty document / index still building) no longer leave the previous scan's results behind; an empty query also clears a finished replace banner; the windowed-replace lock is released in `finally` (an exception during view reset can no longer permanently wedge save/page-switch/search/replace for that tab); regex replacement backreferences `$N`/`${N}` with an out-of-range group index or a non-participating group are echoed literally instead of throwing; backreferences now expand against the original text (no longer skewed by already-rewritten context); streaming scans gained `MULTILINE` to match sora's original semantics (`^`/`$` match every line's start/end); removed the dead "match count" text from the status bar and other sora in-page search leftovers; **switching tabs no longer silently cancels a running replace** — the replace keeps going in the background (the replaced tab stays read-only, saving/page-switching/new replaces are blocked, other tabs are unaffected; replaces are globally single-instance), only closing the replaced tab aborts it, and a spool directory recreated by the close/replace race is cleaned up as well;
- **Older behavior removed in this round**: the info bar's "Windowed" badge (replaced by the "this page" character-count label) and the automatic re-search after a replace (now a manual "Search again") — the corresponding descriptions in the batch-46/47 entries above are superseded by this line.

### Batch 54: device-tested fixes (OOM / fake pagination / cannot edit after search / dialog & scrollbar)

- **Fixed the OOM crash caused by whole-document per-keystroke copying** (measured 2026-10-21 22:26: a 30 MB document ran `Content.toString()` on every keystroke — a 57 MB char array per key — pushing the heap past 531/537 MB): documents over 2 million characters now only set a dirty flag while editing; the latest text is refreshed once before **save / search / replace / switching tabs** (driven by the dirty flag, zero cost for clean documents), so what gets written to disk and scanned is always the editor's latest content;
- **Files over 16 MB now always open in paginated windowed mode (real pagination)**: full-load merely means "it fits right now" — steady-state editing still pays for the sora content tree plus baseline snapshots, and the n/N pager on a full-loaded file is just a cursor-position conversion (it looks like pagination but is not). Files over 16 MB now automatically use windowed mode (2000 lines per page, real page switches, real jumps); "Go to page" on small full-loaded files now uses the same proven path as "Go to line", landing the cursor on the page's first line with n/N updating accordingly;
- **Fixed "cannot edit text after searching"**: while the search field had focus, tapping the editor could not reclaim input focus (the keyboard still went into the search field) — editing only recovered by tapping a search result or closing the panel; touching the editor now restores focus;
- **Full-results dialog height now follows the screen**: capped at 55% of screen height (still at most 480 dp in portrait), so it no longer overflows the dialog in landscape / short screens;
- **The results-list scrollbar is now tappable and draggable**: jumps to a proportional position (20 dp hit area, thumb follows the finger while dragging);
- **Default theme changed to github-dark-dimmed** (was dracula): the theme picker remains and users can still switch at any time; existing configurations with a saved theme are unaffected.

### Batch 55: deep-review fixes (crashes / silent data loss / races)

- **Fixed the "Go to line" crash during large-file index scanning**: confirming a jump before the paginated index is built (total lines still 0) clamped the target into the invalid range `1..0` and threw — jumps are now skipped until the index is ready;
- **Fixed unbounded memory growth while paging**: the full text of every loaded page was kept in the baseline dictionary (paging through a few hundred pages holds hundreds of MB — guaranteed OOM on 512 MB-heap devices); it now stores an 8-byte page fingerprint with identical dirty-detection semantics;
- **"Save as" no longer writes stale snapshots or clears the dirty flag**: after editing a large document it previously wrote the text captured when the picker opened and marked the tab "saved" — the latest content is now refreshed into place before writing;
- **Ctrl+S can no longer bypass the read-only lock**: after a paginated-index rebuild failure the file is temporarily read-only, but the keyboard save path did not check it and could shred the file using invalidated page offsets — the save entry point now blocks this and asks the user to reopen the file;
- **"Save and close" keeps the tab when saving fails**: the old flow closed the tab without checking the result, leaving edits only as an orphan cache copy with no way back in; tabs now close only after a synchronous save succeeds, while windowed / "Save as" async saves defer the close to their completion callbacks and keep the tab on failure;
- **The exit confirmation's "Save" now saves every dirty tab**: previously only the active tab was saved, silently dropping edits in other tabs when exiting;
- **Full mutual exclusion while rebuilding the paginated index for a new charset**: editing / paging / replacing / saving are all refused during the rebuild — previously its completion silently wiped edits and replace results from that window along with the old buffer;
- **File-tree delete now checks for unsaved changes first**: deletion is blocked with a hint when it would hit open tabs with unsaved modifications — previously the tabs were force-closed after deletion and the edits vanished with them;
- **Rename now migrates the windowed document's internal file pointer**: after a rename the paginated document still pointed at the old path — paging failed to read and saving resurrected a copy of the pre-rename file; renames are also refused while a save / replace / charset rebuild is in flight;
- **Fixed a replace-banner race**: closing the replaced tab A and immediately starting a replace on tab B let A's coroutine cleanup wipe B's freshly posted banner, leaving B without its read-only protection against edits; replaces now carry a generation number checked before any banner write, the cancel path finalizes the banner synchronously, and the search error path checks its generation too (an old cancelled scan's failure can no longer overwrite new results with "invalid expression");
- **Fixed whole-load replace marking tabs dirty on zero matches**: a query with no hits still flagged the tab as modified — content is now only marked dirty when it actually changes;
- **Unified the tab-activation entry point**: every path that switches the active tab directly (re-activating on duplicate open, creating a new tab, etc.) first flushes dirty large documents — preventing the editor from resetting to stale text after a switch and making edits "disappear";
- **Atomic background bookkeeping + content-event ownership check**: save-thread / replace-finish read-modify-writes on the tab list now use atomic update, so interleaving with main-thread keystroke edits no longer swallows input or dirty flags; late content events from the previous editor after a tab switch are validated against the tab the editor is attached to, so A's text can never be written into B.

### Batch 56: session cache / pagination byte cap / slimmer search panel / top-bar "All" / settings page animation

- **Search panel drops its inline result list**: the 200dp list of match rows no longer occupies the panel (it duplicated the dialog and ate vertical space) — "Search again" and "Full results" moved onto the **right end of the status row** (no more separate button row, so the panel is shorter); browsing and selecting matches goes through the **Full results dialog only** (full list, lazy previews, tap a row to jump);
- **Pagination is no longer line-count-only**: a **1 MB byte cap per page** was added (2000 lines and the byte cap — whichever comes first ends the page), so pathologically dense lines or very long single lines can no longer push hundreds of MB into one page; a single line over the cap still owns a whole page (page boundaries must fall on line starts, never mid-line); the index's provisional head-page read is truncated by the same cap; the save fast-rebuild's line-count validation now compares against each page's **actual** line count;
- **New session cache — reopen restores the app to where you left off**: the tab list, active tab, windowed page number, unsaved full-load drafts and unsaved edited window pages are flushed to disk automatically after 1.2 s of idle typing (plus a final flush on exit), and the next time the editor opens, tabs and unsaved changes are restored automatically (file bodies are reloaded from disk as expected); whole-file-replace disk spools and oversized (>4M chars) drafts are not carried across sessions in v1;
- **New top-bar "All" icon**: opens a dropdown with **Save all files / Close all files**; if anything is unsaved, closing all first asks for a discard confirmation;
- **Settings page now slides in/out**: opens with a slide-in from the right plus fade-in; closes (page back button or system back key) with a slide-out plus fade-out, unmounting only after the animation finishes.
- **Batch-56 review fixes**: revisiting an already-edited page no longer silently drops its edits (the paging baseline was being polluted with the edited-version fingerprint, so the next flush judged "unmodified" and removed the overlay — this also lost restored windowed changes on the first page flip); cold-start "last opened" and session restore can no longer open the same file as two tabs (a hit merges in place with the draft applied); no intermediate snapshots are written while a restore is still pending (a large-file index takes seconds — the previous behavior deleted unconsumed overlay/draft files as orphans, so a process kill during indexing lost the edits permanently); snapshot writes are ordered by a monotonic sequence number and the fingerprint now covers rename/charset/dirty-flag changes; session entries carry file size + mtime, and restore refuses to graft overlay pages onto an externally modified file (with a notice) instead of corrupting it. A third review round then added: starting a save now auto-cancels any in-flight whole-file search (results computed against the old index would misalign after the rename); no snapshots are written while a save is on disk (so the previous session's persisted edited-page files are never reaped as orphans mid-save — a kill or failure during the write rolls back cleanly); the windowed tab's current-page edits are flushed into the capture surface before a snapshot is taken (backgrounded-process kills no longer drop just-typed current-page text); during restore the provisional paging tab no longer steals focus, and close/index-failure/head-read-failure paths all explicitly retire pending restore entries; and the pagination byte-cap constant in the index builder is now properly qualified (an unqualified reference would fail to compile). A fifth round then fixed: a non-last-page edit whose line count changed no longer duplicates or loses lines on a save→save round trip (when a full rebuild shifts the page boundary, the view is realigned to the new layout — safely reset and marked clean if nothing was typed during the save; otherwise the tab is temporarily read-only with a prompt to reopen, content kept intact); the shared concurrent sets now use thread-safe construction; and "Close all" also clears any pending restores. A sixth round fixed: a tab whose paging index had been invalidated ("dead tab") could be re-enabled for saving by any subsequent "reopen file" (which unconditionally clears the global read-only flag) and then corrupt the file on a second save — save, page-jump and whole-file-replace now all hard-block such tabs individually; the save realignment decision and state updates now run as one atomic block on the main thread, so a keystroke made during the save is never silently overwritten; the read-only/dead flags are only set after re-checking the tab still exists (no zombie marks on force-closed tabs); and the save-failure fallback catches Throwable instead of Exception and releases the save lock first (an OOM can no longer permanently wedge saving and app-wide snapshots). A seventh round finished up: a successful charset rebuild now clears the dead flag (the tab has been fully reset and is no longer blocked); the index-rebuild-failure branch runs its bookkeeping on the main thread as well, closing the same race window; and both save-failure branches clean up the spool of a tab that was closed mid-save, preventing orphaned cache directories.

### Batch 57: editor-side search-result highlighting / full-results dialog fixes / no more "Load more"

- **Search results are now highlighted in the editor** (new): when a whole-file search finishes, every match is painted with the theme's `editor.findMatchBackground` color (github-dark-dimmed = `#966600`; foreground text is still drawn by sora on top of the block) — you can eyeball all hits without opening the Full results dialog; windowed tabs highlight only the current page (the editor only holds that page), line/column semantics match jumping, results auto-clear on a tab that didn't originate the search (same gate as the panel), and the list is capped at 10 000 items per file. **No recalculation while editing**: sora auto-shifts the painted ranges on insertion/deletion (undo/redo included), so highlights track the content instead of jumping back, while the "content changed" banner still flags stale results; the hex-color blocks share sora's single `setHighlightTexts` channel with ownership decided by color type (search = EditorColor, hex = ConstColor), each side carrying the other's items on rewrite so neither clobbers the other, merged in sorted order to avoid O(n²) mid-list shifting; after `setEditorLanguage` clears the container, both are restored as a pair (`restoreDecorations`).
- **"Load more" removed — one shot loads everything**: the full-results page no longer scans on in 100k batches (the 100k batch size and the 500k hard cap are gone); the scan collects every match in one pass and "Loaded N of N" lands immediately; the parallel guard now only fires for **bounded** windows — with unbounded collection each worker gathers only its own chunk, whose merge equals the global total with no redundant amplification, so the 4-way parallel scan stays instead of falling back to a single thread; when the window covers everything the merge reuses the result directly, skipping the two extra list copies of drop/take.
- **Fixed the full-results dialog filling the screen / clipped content**: total dialog height is capped at min(600dp, 75% of screen height), the list row uses `weight(1f, fill = false)` and the scrollbar height follows the measured list height — short result lists shrink the dialog accordingly, and the confirm button is no longer pushed off-screen in landscape.
- **Removed the "showing first 500 only" notice**: the copy became meaningless once the preview list left the panel and now folds into the plain completion text; `editor_search_whole_truncated` and the three loading keys (`full_more` / `full_loading` / `full_failed`) were deleted from both locales — string keys aligned at **1951** with 0 placeholder differences.
- **Fixed "search highlights shift after edits / the space typed next to a match gets painted"** (user report): the highlight-repaint effect is now keyed on the result **list reference** instead of the whole result object — previously every edit triggered a "content changed" stale mark (an object `copy`), restarting the effect and repainting with the pre-edit line/columns, dragging highlights back to their old position (the offset) and swallowing just-typed characters (the space) into the old ranges. Edits are now handled purely by sora's insertion/deletion auto-shift; a repaint only happens when a **new search lands or results are cleared**.
- **Fixed "closing then reopening the search panel wipes results and highlights"** (user report): query / match case / whole word / regex / match cursor are now held at Screen level — the panel is fully unmounted when collapsed and its internal state zeroed, so reopening fired an empty query that tripped "empty input clears results", wiping the finished results together with the editor highlights; with the state hoisted, collapsing and reopening keeps everything **exactly as it was** (same signature + valid results even skips the rescan), and only the panel's **close icon X** clears results and highlights — the toolbar search icon and the back key merely collapse the panel.
- **Plugin wizard draft mode** (user report): when creating a plugin the editor **shows only the wizard's files** — other tabs (e.g. device files the user forgot to close) are hidden rather than closed, and come back untouched from the session once the editor closes after the wizard; plugin drafts **never enter the editor cache** — the session snapshot drops every tab under the draft directory prefix (covering the idle debounce, the exit fallback and the fingerprint at once) and drafts are not recorded as "last opened" (a process death can no longer resurrect plugin drafts as documents), and leftover draft tabs from the previous wizard round are dropped when the next one starts.

### Internationalization

- Chinese and English string keys grew from 1867 to **1951**, with 0 placeholder differences

## [6.1.0] - 2026-10-07

> Maintenance release: a full defect-fix pass over the 6.0.0 codebase (all 233 checklist items closed) covering startup & permissions, main-thread/ANR risks, dark theme, plugin host correctness and security, backup/restore data safety, error visibility, and build hygiene — followed by dead-code & resource cleanup, architecture consolidation, i18n, accessibility and CI hardening. No new features.

### Startup, Permissions & Logging

- Splash no longer gets stuck: custom dangerous permissions (OPEN_PLUGIN / RUN_COMMAND) are requested on Android 11+ and for already-granted-storage users, permission results are re-checked in `onResume`, and a denied permission no longer permanently disables the Allow button
- Plugin permission requests now deliver **real** callback results (the request was never actually dispatched), and progress/completion callbacks fire only after the real reply
- `Logger.init()` moved to the start of `Application.onCreate` so startup-phase logs are kept
- Rotating/recreating the Activity no longer kills the supervisor and every plugin backend
- Cold-start process cleanup runs on a background thread with `kill -0` polling instead of a fixed sleep on the main thread

### Main Thread / ANR

- Terminal session creation splits command building (IO thread) from session creation (main thread); a failed command build now shows a Toast instead of failing silently, and intent commands are consumed only once
- Plugin import/export/uninstall and backup/restore work moved off the main thread (single, batch, set import, export, refresh)
- Container list loading and downloads report progress with throttled updates

### Theme & Readability

- Input text color follows `colorScheme.onSurface`; the Markdown renderer's 27 hardcoded light colors now switch with the dark theme
- Help content is read from the language-matched `Help.md` (en / zh-cn)

### Plugin Host

- `onResume/onPause` now pass the instance key, so WebView resume/pause and plugin lifecycle events actually fire
- Backend path normalization rejects `@`, `//`, `:`, `\`, `..` and control characters (blocks outbound host-redirect bypass), and `openUrl` enforces an `http/https/mailto/tel` scheme allowlist
- Event payloads and error JSON are escaped properly (no more broken scripts on quotes/newlines)
- Retained WebViews are capped at 4 (FIFO, destroyed on eviction) and flushed on `onTrimMemory`
- A 90s backend start timeout now shows a Toast instead of hanging silently

### Data & Backup

- Restore rollback actually works again (the previous condition was dead code), and the replaced plugin directory is only deleted after a full success
- Zip failures propagate instead of silently producing incomplete archives; unzip enforces entry-count and total-size limits (zip bomb protection)
- Work folder / view mode flows now update reactively

### Error Visibility

- Plugin source load failures, disabled sources, download failures and install failures show inline banners with Retry/Dismiss instead of only writing logs (the download path previously erased its own error message)

### Build

- Removed ineffective multidex configuration (minSdk 23 uses platform multidex), `multidex-config.pro` renamed to `app-keep.pro`
- Unified `core`/`core-ktx` to 1.13.1 and dropped the `resolutionStrategy.force`
- Removed duplicate Markwon declarations from the app module and dead dependencies (`commons-lang3`, `error_prone_annotations`, `org.brotli:dec`)
- Switched to `proguard-android-optimize.txt`; CI unit tests are no longer `continue-on-error`

### Security & Plugin Entry Points

- `PluginOpenDispatchActivity` is now properly registered in the manifest with `exported` disabled; external apps enter only via explicit Intents, and the documented Intent-filter list matches the actual declaration
- Removed the orphan permission-explanation stack so the splash permission dialog is the single flow (user decision); removed the self permission-request flow
- The 5 critical JS-bridge entry points (callHost / openUrl / getLocation / getSerialNumber / getMacAddress) passed the audit baseline check
- Supervisor exit codes are propagated with zombie backend reclamation; log redaction and container status wording unified

### Startup, Update & Long-running Tasks

- Startup sequence rewritten as declarative tasks; all first-run IO moved off the main thread
- Update download now runs as a foreground service with a notification cancel action; the update check runs on every launch with window-based freshness
- Update list sorted on a dual-field comparison; silent-update and update-check listeners consolidated into `UpdateViewModel`
- New unified long-task framework (registry + global host), adopted by backup export/restore and container import/export/download with inline progress and errors
- Home-screen widget refresh now runs on WorkManager with a UIConfig switch
- Backend settings and mirror screens moved their state and coroutines entirely into ViewModels

### Architecture & State Consolidation

- `core/` now keeps contract files only (3 non-contract units moved out); 8 dead files removed under `core/plugin`
- Dead runtime fields removed from `PluginInfo` with state externalized; 74 `var`s in three models converted to `val` with mutation points collapsed
- Network / preference singletons consolidated; environment orchestration sunk into `EnvironmentBootstrap`
- Full migration to AppCompat with `AppCompatDelegate` night mode (user decision)
- Storage migration executed as a group; log/backup/TPK storage-direction callbacks moved back to the shared work folder (user decision)

### UI, Accessibility & Dark Theme

- Empty and error states unified behind `UnifiedEmptyState`; added skeletons, uninstall feedback and inline backup errors
- Back handling migrated to Compose `BackHandler`; tab state made savable; optional semantics added to bottom-nav items; shortcut icons scale per density
- Theme trio (color / size / shape) key mapping split and collapsed to a single mapping, narrowing the configuration-save recomposition surface
- **Root `LocalContentColor` fallback**: with gradients enabled the page container is transparent, so Material3 `contentColorFor` has no match and falls back to the root default black — uncolored text (e.g. plugin names) rendered black in dark mode. `onBackground` is now provided as the tree-wide fallback, so both light and dark render correctly
- Default style's dark palette aligned to the neumorphic colors and lifted in three tiers (background < input < card) so cards are distinguishable again
- Dark-gradient pollution prevented: the 5 gradient fields are mode-aware, style switching preserves the current theme mode, and missing values fall back to dark defaults
- Desktop icon restored from Termux `>_` to the original UIN wordmark (vector + 5 density PNG sets)
- Opaque wrapper layers that hid the themed root gradient removed from source-management and doc-viewer screens

### Internationalization & Copy

- Documentation kept in English/Chinese parity (5 files each under `en/` and `zh-cn/`); document loading consolidated
- Logs and fixed copy converted to English; dual-use copy pinned to English; the three `locale` mechanisms collapsed to "in-app switch wins" (user decision)
- English/Chinese string-key parity added as a CI gate to prevent key-set and placeholder regressions
- "Documentation Center" moved to the top of the management screen's software group and the standalone "User Guide" entry removed (the guide is still the first document); group title renamed to **Software Management**
- Terminal selection bar localized ("Copy / Paste / More", overriding the terminal-view module's English defaults); copy confirmation unified to "Copied to clipboard"
- proot install copy fixed to "Installing proot container… / proot container installed"
- The 4 frontend-type options in the create-plugin dialog converted to standard cards with centered text

### Dead Code & Resource Cleanup

- Removed dead Kotlin code (8 target groups), `TrampolineActivity`, dead colors and dead previews
- Removed 186 unreferenced resources and 3 stale assets (proot assets and `host-sdk.jar` retained), plus resource normalization
- Demo TPKs moved out of the release package; orphan layers deleted; dead runtime fields cleaned from `PluginInfo`

### Build & CI

- CI tightened, scripts relocated, compiler callbacks self-cleaned; string-key parity added as a gate
- First real-device build fix chain: avoid `@hide` APIs (`Os.getpgid`/`killpg`), restore an accidentally deleted theme, fix unterminated nested comments caused by a bare `/*` inside a comment, collapse 58 compile errors into 9 root-cause clusters, add the missing `R` import, and eliminate the `getPrefs` JVM signature clash

## [6.0.0] - 2026-09-26

> This version is a **major architectural overhaul**: completely removes dependency on the Termux environment (bootstrap, proot-distro, TermuxActivity, etc.), introduces a self-contained proot container system with direct Debian rootfs support, and adds a full container management UI. The application no longer extends TermuxApplication and directly inherits from Android Application. The terminal is rewritten in Kotlin (SimpleTerminalActivity). Total of 100+ files modified, 3000+ lines added, 5000+ lines removed.

### Removing Termux Environment Dependency

- **Removed Termux bootstrap**: No longer requires `bootstrap-aarch64.zip` or native `termux-bootstrap.c`/`termux-bootstrap-zip.S` code; environment auto-installation changed from bootstrap + Alpine pipeline to proot binary + Debian rootfs direct pipeline
- **Removed proot-distro dependency**: No longer needs `proot-distro install/restore` commands; rootfs extracted directly via `RootfsManager.extractTar()` from built-in `debian-rootfs.tar.xz`
- **Removed TermuxActivity**: Old Java-based terminal (`app/` directory, 32 files) deleted; replaced by `SimpleTerminalActivity` (Kotlin, 1 file) using `TerminalView` + `TerminalSession` directly
- **Removed TermuxService & RunCommandService**: No longer needed for backend process management
- **Removed TermuxApplication base class**: `UinApplication` now directly extends `android.app.Application` instead of `TermuxApplication`
- **Removed Termux shared library bloat**: Reduced from 121 to 83 source files; deleted `crash/`, `data/`, `extrakeys/`, `file/`, `interact/`, `models/`, `notification/`, `plugins/`, `shell/`, `terminal/`, `theme/` subdirectories and `TermuxAppSharedProperties`/`TermuxSharedProperties`
- **Removed Android components**: `DocumentsProvider`, `ContentProvider`, `TermuxOpenReceiver`, `RealTermuxProbeReceiver`, `SystemEventReceiver` all removed
- **Removed permissions**: `MANAGE_DOCUMENTS`, `RUN_COMMAND` no longer declared
- **Removed assets**: `alpine.tar.xz` (Alpine rootfs backup), `xz-aarch64/` decompressor

### PRoot Container System (New)

- **New `proot/` module** with 6 files:
  - `ContainerConfig.kt` — Container configuration data class (name, rootfs path, bind mounts, env vars)
  - `ProotInstaller.kt` — Installs proot binary from assets to app's files directory
  - `ProotRuntime.kt` — Builds proot command lines with bind mounts, fake kernel release, env vars; uses `--link2symlink`, `--sysvipc`, `--change-id=0:0`
  - `RootfsManager.kt` — Full container lifecycle: create, delete, list, export/import tar archives; extracts tar.gz/tar.xz/tar.zst with proper hard link handling
  - `RootfsSetup.kt` — Post-extraction fixups (resolv.conf, hosts, /tmp permissions, /root, /etc/shells)
  - `FakeSysdata.kt` — Creates fake /proc and /sys entries for proot compatibility on Android
- **Default container changed from Alpine to Debian** (`BackendConfig.kt`: `CONTAINER_DEFAULT = "debian"`)
- **Container stored at**: `context.filesDir/containers/debian/rootfs` (internal storage for symlink compatibility)

### Container Management UI (New)

- **New `ui/screen/proot/` module**:
  - `ContainerManagementActivity.kt` — Activity wrapper
  - `ContainerManagementScreen.kt` — Full Compose UI for managing containers (list, download distros, export/import, delete, set default)
- **Features**: Download distros from online sources, export containers as `.tar.zst` (shareable), import from `.tar.zst`/`.tar.xz`/`.tar.gz`, delete containers, set default container
- **Export compression level**: Adjustable 0-22 (zstd), default 9; lower = faster, higher = smaller; auto-caps based on device memory to prevent OOM

### Terminal Rewrite

- **New `SimpleTerminalActivity.kt`**: Kotlin terminal using `TerminalView` + `TerminalSession` classes with Compose UI, integrates directly with proot containers
- **New layout files**: `terminal_activity_layout.xml`, `terminal_session_item.xml`, `terminal_key_bg.xml`, `terminal_key_ripple.xml`
- **Removed layout**: `activity_termux.xml`, `view_terminal_toolbar_extra_keys.xml`

### Plugin System Updates

- **`ProotContainerManager.kt` rewritten**: No longer depends on Termux prefix (`/data/data/.../files/usr/bin/proot-distro`); directly uses `ProotInstaller`, `ProotRuntime`, `RootfsManager`
- **`PluginHostActivity.kt` updated**: Uses `MainScope().launch` coroutines; progress shown via `AppToast`
- **`DevScreen.kt` updated**: Terminal button launches `SimpleTerminalActivity` instead of `TermuxActivity`

### Plugin Backend Startup Fix

- **Environment variables injected into proot command**: `PORT`, `PLUGIN_ID`, `PLUGIN_DIR`, `WORK_DIR`, `HOME`, `PYTHONUNBUFFERED` are now exported before the startup command runs inside the container
- **Host environment cleanup for proot**: Removed host Termux-specific paths (`PREFIX`, `TMPDIR`, `TERMUX_*`) that contaminated the container; set clean container PATH and `PROOT_TMP_DIR`/`PROOT_L2S_DIR`
- **Shell detection fixed**: `findShell()` now prioritizes `/bin/sh` and validates symlink targets to avoid broken symlinks (e.g., `/bin/bash` -> `/usr/bin/bash` in minimal Debian rootfs)
- **`PluginInfo.getInstallCommand()` updated**: Package manager commands changed from `pkg install` to `apt install` with correct Debian package names

### CUI Plugin Improvements

- **Removed placeholder page**: CUI plugins no longer show "正在全屏终端执行命令" placeholder; terminal launches directly
- **Startup command passed to terminal**: `SimpleTerminalActivity` now accepts an optional command via Intent extras; CUI plugins pass `backendPreCommand` with environment variable exports
- **Host path used for plugin directory**: CUI terminal uses `/storage/emulated/0/UIN_Tool/plugins/<id>` (accessible via `/storage/emulated/0` bind mount) instead of non-existent `/plugins/<id>` container path

### Plugin Icon Loading

- **New `PluginIcon` composable**: Asynchronously loads plugin icons from `plugins/<id>/icon.png` using `BitmapFactory` on `Dispatchers.IO`, falls back to first letter of plugin name
- **Applied to**: `PluginListItem`, `PluginGridItem` (ToolsScreen), `PluginManageItem` (PluginManageScreen)

### Documentation Updates

- Version updated to 6.0.0 (build 24) across all docs
- Terminal section: "Based on Termux" → "Based on PRoot"
- Package commands: `pkg` → `apt` throughout
- Alpine → Debian references updated in active documentation
- Changelog written for v6.0.0

### Other Changes

- `termux-shared` module retained (for `terminal-emulator` and `terminal-view` JNI) but significantly trimmed
- Comments cleaned: `build.gradle` header changed from `// Termux/build.gradle` to `// UIN Tool/build.gradle`

---

## [5.7.0] - 2026-08-31

> This version focuses on **plugin distribution system restructuring, update detection, CI automation, and network cache optimization**: switches from single-repo-single-plugin model to source-based multi-plugin model, supports multi-source aggregation, automatic version update detection, GitHub Actions CI auto-build, and icon disk caching with HTTP conditional requests. Total of 50+ files modified, 2500+ lines added.

### Plugin Distribution System Restructuring

- New Source architecture: Each GitHub repository serves as a "source" containing multiple plugins, indexed via `source.json`
- New `SourceIndexFetcher`: Supports GitHub API + raw URL dual-channel fetching with built-in caching
- New `SourceManageActivity`: Source management page with enable/disable/delete custom sources
- `RepoViewModel` rewrite: Multi-source plugin aggregation, source filtering, search filtering
- `RepoScreen` update: Source FilterChip filtering, "update available" indicator, detail dialog with version comparison
- Removed `backendPort` field: Backend ports auto-assigned via `findAvailablePort()` to avoid conflicts

### Plugin Update Detection

- New `hasUpdate` / `installedVersion` fields: Compare local installed version with remote source version
- Installed plugins with updates show "Update available" indicator, button changes to "Update"
- Detail dialog shows current installed version, supports one-click update

### CI Automation

- `build.yml`: Trigger paths include `.github/workflows/**`; deploy uses `git clone --branch dist` (token auth); incremental build uses `github.event.before` for accurate diff detection; auto-copies `plugin.json` + `icon.png` + `.tpk` to dist branch
- `generate-index.sh`: Reads `plugin.json` from dist directory, supports `visible` file for hiding plugins
- `build-plugin.sh`: Native compilation uses `host-sdk.jar`; supports absolute paths; `other` type calls plugin's own `build.sh`
- Plugin build script `build.sh`: Supports `chmod +x` to ensure binary executability

### Network Cache Optimization

- New `DiskCache` utility: SHA-256 hashed file naming, in-memory + disk two-tier caching, 1-day TTL auto-expiration
- HTTP conditional requests: Stores `ETag` / `Last-Modified` headers, sends `If-None-Match` / `If-Modified-Since` to avoid re-downloading unchanged resources
- Icon caching: `CachedPluginIcon` composable loads icons from disk cache first, falls back to network with conditional request
- Startup cache cleanup: Automatically purges expired cache entries on app launch

### UI Improvements

- Plugin detail dialog now displays plugin icon (64dp centered)
- Detail dialog confirm button uses primary theme color with white text
- Plugin card list "Open" button uses outlined style (transparent + theme color text + border)
- Unified UI fixes: dropdown styles aligned across pages, button colors consistent between default and neumorphism modes, hardcoded text moved to string resources

### Bug Fixes

- Fixed `filemanager` plugin path: Changed from app internal storage to `/storage/emulated/0/UIN_Tool/plugins`
- Fixed `calculator` backend startup failure: Removed unnecessary `pip install flask` dependency
- Fixed `sysinfo` plugin binary permission issue: Added `chmod +x` during build
- Fixed `RepoScreen` missing `AppColors` import
- Fixed `SourceManageActivity` theme to use `UINToolTheme`
- Fixed smart cast error in `RepoPluginCard` icon loading (delegated property)

---

## [5.6.0] - 2026-08-30

> This version focuses on **UI system refinement, neumorphism style addition, and language switching functionality**: refines the UI personalization system, adds neumorphism (Neumorphism) style, adds language switching functionality (supports Follow System/Simplified Chinese/English). Total of 92 files modified, 6240 lines added, 3850 lines deleted.

### UI System Refinement

- Refines unified component system: All pages uniformly use UnifiedButton, UnifiedCard, UnifiedTextField, UnifiedSwitch, UnifiedIconButton, UnifiedSlider, UnifiedChip, and other components
- Fixes multiple issues with un-unified original Material3 components (CodeEditorScreen, BackupScreen, BackendSettingsScreen, WidgetConfigScreen, UIConfigPropertyPanel)
- Fixes OnboardingScreen hardcoded colors to theme colors
- Unifies neumorphism shadow effects (neuRaised/neuInset) using BlurMaskFilter implementation
- UnifiedButton supports automatic text wrapping for long text, no longer truncates
- DropdownPropertyRow uses native Row+Text instead of disabled UnifiedTextField, fixes dropdown text gray issue
- UnifiedDialogs refactoring: Popup + AnimatedVisibility for pure fade animation, dialogBackgroundOf() supports gradient backgrounds
- UIComponents bridging layer: All old UIComponents methods delegate to Unified* components
- ManageScreen, PluginManageScreen, ToolsScreen, DevToolsScreen, PermissionManagerScreen, and other pages fully use Unified components

### Neumorphism Style

- Adds neumorphism (Neumorphism) style presets, supports customizable shadow strength, inner shadow, and glow effects
- New file: NeuModifiers.kt (381 lines) - BlurMaskFilter rendering engine, neuRaised/neuInset/neuPressable/neuGlow modifiers
- New file: NeuEffects.kt - Custom ripple and glow effects
- New file: NeumorphicComponents.kt - NeuCard/NeuButton/NeuInput/NeuSwitch and other components
- New file: SpecialComponents.kt - Special components
- New file: UIConfigPreviewScreen.kt - Neumorphism preview page (35 control displays)
- New file: UIConfigNeumorphismPreview.kt - Neumorphism standalone preview
- New file: UIConfigModels.kt - ConfigState data model, loadConfigFromUIConfig/saveConfigToUIConfig serialization
- New file: StylePresets.kt - Default/Neumorphism presets, toConfigState() extension
- New file: StyleManager.kt - Style switching, caching, reset
- Neumorphism component support: cards, buttons, input fields, switches, chips, sliders, etc.
- Neumorphism animation speed adjustable (fast/medium/slow)

### Language Switching

- Adds language switching functionality (Content Properties > Text > Language)
- Supports: Follow System, Simplified Chinese, English
- UinApplication.applyLocale() sets Locale and updates Configuration
- Language settings persisted, takes effect after restart
- UI personalization page all labels support multilingual (Str.get() localization)
- UIConfigSidebar 30+ tree node labels localized

### Other Improvements

- Translucent effect (originally glass effect) renamed, supports transparency adjustment slider
- Fixes translucent effect transparency adjustment not taking effect (glassBackground now correctly applies alpha override)
- Fixes documentation center page top spacing issue (DocBrowserScreen contentPadding)
- Fixes 15 Chinese/English string misalignment issues (neumorphism/animation strings moved to correct positions)
- Ripple effect toggle note: This toggle controls Material3 native ripple; neumorphism components use independent shadow transition animations as click feedback, the two do not affect each other
- New file: SharedSupervisor.kt - Shared Supervisor process management
- build.gradle: versionCode 22, versionName "5.6.0"
- AndroidManifest.xml: Multiple activity attribute updates
- Multiple .tpk test plugin packages updated

---

## [5.5.0] - 2026-08-22

> This version focuses on **native plugin compatibility fixes, real Termux architecture upgrade, and development wizard completion**: fixes native plugin crashes caused by interface default method bytecode incompatibility (`-Xjvm-default=all` + rebuild host-sdk.jar + repackage plugin.dex), CUI plugin proot container script path error, crash log page not jumping, MarkdownRenderer `appendReplacement` crash, DocViewerScreen crash, etc.; adds clipboard pseudo-permission declaration (`READ_CLIPBOARD`/`WRITE_CLIPBOARD`); **development wizard completes all plugin fields** and updates built-in documentation. Adds **permission prompt before opening plugin**, **real Termux shared Supervisor** (single resident process manages all plugin backends, cold startup saves ~5s initialization overhead), **startup environment auto-installation** (bootstrap + Alpine background pre-installation), **4 static desktop shortcuts** replacing dynamic plugin shortcuts.

### Native Plugin Crash Fixes

- Native plugin `AbstractMethodError` crash: Plugin `plugin.dex` compiled against old host-sdk.jar (without `onHostEvent` and other default methods), Kotlin default methods exist as abstract methods at runtime → calling crash. Fix: build.gradle enables `-Xjvm-default=all` (real JVM default methods), rebuild host-sdk.jar (Java mirror, default method signatures match runtime), repackage plugin.dex per template, host adds reflection guard
- CUI plugin proot container `scripts/script.py` not found: proot resets working directory to `/root`, startup command changed to `cd /plugins/<id> && <startup command>`

### Clipboard Permissions (Pseudo-permissions)

- Adds pseudo-permissions `READ_CLIPBOARD` / `WRITE_CLIPBOARD`: only need to be declared in plugin.json `permissions` to take effect, no runtime authorization needed
- Development wizard "Permissions" popup already includes these two pseudo-permissions; permission management page only does "declaration + blocking" control for pseudo-permissions

### Development Wizard Field Completion

- Backend: `backendStartCommand`, `backendTimeout`, `backendHealthCheck`
- External content reception (openWith): toggle + receiver name + MIME type + text/link/file reception toggle
- Permissions: popup multi-select covers all common permissions (including pseudo-permissions)
- Plugin notice (notice) retained
- **Field streamlining (v5.5.0 later)**: No longer provides "Resource limits (max memory/max CPU time/max concurrent tasks), dependencies, API level, backend keep-alive" input fields (not written to wizard-generated plugin.json, see 12.3); **fixes code editor always showing default MainPlugin.java** — entry file regenerated synchronously after main class name change

### Permission Prompt Before Opening Plugin

- **Web plugins** (with/without backend): Before opening, popup lists **ungranted** permissions, provides "OK" / "Don't Prompt Again" / "Manage Permissions" — "Manage Permissions" jumps directly to that plugin's permission management page
- **Native plugins**: Each opening prompts required (declared) permissions, provides "OK" / "Don't Show Again" — **popup first then open plugin** (native permissions enforced by system, application layer does not intercept/block)
- Permission management page only manages **Web plugin** permissions (native/CUI plugins no longer displayed)
- Permission Chinese display name improvement: e.g., `android.permission.READ_EXTERNAL_STORAGE` → Read External Storage, `WRITE_EXTERNAL_STORAGE` → Write External Storage
- Permission prompt popup changed to **host unified style** (`UnifiedDialog`) rendering, visually consistent with other system popups
- Permission management page removes **refresh button**, list auto-refreshes after granting/revoking

### Keep Session Single Window

- "Keep Session on Close" toggle **enabled by default**: default single-window deduplication (shared port mode + Web plugins, same plugin **keeps only one background window** — repeated openings bring existing window to the foreground, no more multi-instance, multi-task window only shows one)
- User explicitly disables in "Development Tools", Web / CUI plugins create independent instances each time (supporting multi-instance)

### Real Termux Backend Auto-Reclamation

- Real Termux processes launched by com.termux's own UID, host cross-app sandbox cannot directly kill; now changed to **shared supervisor unified management**: supervisor checks each plugin process liveness via `kill -0 $pid` each round, cleans up if dead; idle reclamation judges via `idle/<key>.start` startup timestamp + `idle/<key>` timeout minutes (0=infinite), recursively SIGKILL kills backend process tree on timeout, **does not depend on plugin implementing `/stop`**
- Idle reclamation duration supports **custom arbitrary minutes** (settings page "Custom (minutes)" input box), also selectable preset 3 / 5 / 10 / 15 minutes (default 5) or "**Unlimited**" (never auto-reclaimed, only stops when actively stopped; does not write idle files)
- Host no longer polls to determine idleness, only does port detection and state cleanup; supervisor handles actual process killing
- Combined with "Backend Runtime Settings" idle reclamation timeout, after user exits plugin for set duration, backend is auto-killed without manually entering Termux to run commands

### Permission Button Text Shortened

- Permission management page "Grant/Revoke All Permissions" button changed to short text "**Grant All**" / "**Revoke All**" (en: `Grant All` / `Revoke All`), layout `weight(1f)` ensures complete display without truncation

### Plugin Templates Refined by Type

- `README.md.tmpl` rewritten to **typed rendering**: generates corresponding directory tree, development guide, packaging steps, and packaging file description based on plugin type (native / Web / Web+backend / CUI), fixes `{{MAIN_CLASS_PATH}}`/`{{WEB_SECTION}}`/`{{DEVELOPMENT_GUIDE}}` placeholders and `{{PLUGIN_ID}` typo that were never passed in original template
- Web template variable adds `PLUGIN_DESCRIPTION`, blank homepage renders with plugin description

### Documentation and Compatibility

- `permissions` / `dependencies` / MIME and other list fields **simultaneously compatible with comma-separated strings and JSON arrays** (wizard reads back plugin.json also reuses compatible parsing)
- Test plugin `plugin.json` streamlined: removes `signature`/`updateUrl`/`notice`/`backendRuntime`/`backendPort`/`backendEntry`/`backendPreCommand`/`backendMaxRetries`/`backendLogLevel`/`backendArgs` and other unnecessary or legacy fields, only retains actually effective fields; `web_plugin_template` changed to pure frontend (no `plugin.dex`/`src/`)
- Built-in documentation (README / Help / CHANGELOG) updated; version number upgraded to **5.5.0 (Build 21)**

### Real Termux Shared Supervisor

- Real Termux (proot or native mode) changed to **single resident shared supervisor**: container/session initialized only once, all plugin backends run as supervisor child processes, subsequent plugin startup saves proot initialization overhead (cold startup about 5s)
- Built-in Termux (alpine, about 2s) remains unchanged (each plugin has its own proot)
- Communication protocol (control directory `<plugins_root>/.uin/`): `cmd/<key>.cmd` (startup command), `pid/<key>` (backend PID), `stop/<key>` (stop request), `idle/<key>` (idle minutes, 0=infinite), `idle/<key>.start` (startup timestamp), `alive` (supervisor alive marker), `host_alive` (host heartbeat, touch every 30s, supervisor auto-exits on 300s timeout), `shutdown` (exit marker), `keep_alive` (background keep-alive marker)
- proot startup: `proot-distro login <container> --bind '<plugins_root>:/plugins' -- sh -lc 'sh /plugins/.uin/supervisor.sh /plugins'`; native: `sh '<plugins_root>/.uin/supervisor.sh' '<plugins_root>'`
- Idle reclamation: supervisor independently times out and recursively kills process trees per plugin based on `idle/<key>.start` startup timestamps; `kill -0 $pid` detects process liveness
- Supervisor stays resident during host lifetime (even after all backends reclaimed); host writes `shutdown` marker on exit, supervisor auto-exits (container exits accordingly)
- Software preheats supervisor (`prewarm`) in background on startup, also triggered when saving backend settings
- Backend settings page adds "Shared Scheduler" status card (RUN_COMMAND permission + supervisor alive status)
- Performance: removes probeRealTermux (saves 1.5~4s), polling interval 200ms, host_alive 300s timeout; warm startup ~0.5s, warm restart ~2s, cold startup ~4s

### Background Keep-Alive

- Adds "Background Keep-Alive" toggle (backend settings page → real Termux section)
- When enabled: writes `keep_alive` marker, supervisor no longer exits when host process is killed (only exits on explicit `shutdown`); combined with **battery optimization exemption** (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) + notification bar keep-alive + **Shizuku/Dhizuku** permissions to maintain background survival
- Displays current Shizuku/Dhizuku permission status (available/unavailable)

### Crash Fixes and Experience Optimization

- Fixes MarkdownRenderer `appendReplacement` crash: replacement string containing `$` treated as group reference causing `IllegalArgumentException`, all three `appendReplacement` calls changed to use `Matcher.quoteReplacement()` escaping
- Fixes DocViewerScreen crash: `MarkdownRenderer.toHtml()` exception uncaught causing entire Activity crash, adds try-catch to display error information
- Fixes DocViewerScreen background color inconsistent with host: CSS body background changed from hardcoded `#f5f5f5` to `transparent`, WebView sets background color via `UIConfig.getBackgroundColor()`

### Startup Environment Auto-Installation

- Software auto-detects and installs Termux bootstrap + Alpine container in background on startup (built-in mode only), no manual operation needed
- Bootstrap installation immediately unblocks terminal (`_isEnvironmentInstalling = false`), terminal black screen issue fixed
- Alpine installation fully asynchronous, does not block terminal creation; Toast prompt during installation
- `ensureAlpine` adds `_isAlpineInstalling` lock, prevents concurrent restore causing "container is busy"
- After installing bootstrap, auto-writes environment variable file (`writeEnvironmentToFile`), ensures terminal bash has correct environment

### Static Desktop Shortcuts

- Dynamic plugin shortcuts replaced with **4 commonly used page static shortcuts**: Docs (DocBrowserActivity), Terminal (TermuxActivity), Backend Settings (BackendSettingsActivity), UI Personalization (UIConfigActivity)
- Removes dynamic shortcut creation/refresh/deletion logic in `PluginManager`, no longer exceeds limit due to frequent refreshes

---

## [5.4.0] - 2026-08-10

> This version is a **proactive capability extension**: adds **plugin receiving external content** (system "Share / Open with other apps" → relay page selects plugin) and **plugin multi-instance** (same plugin runs multiple independent instances simultaneously), and adds relay page search and plugin management page "Add Desktop Shortcut" entry.

### Plugin Receives External Content (Intent Relay, openWith)

- Adds **outbound open channel**: system/other apps "Share" or "Open with other apps" **text, links, files** can be sent to UIN Tool; relay page lists all plugins declaring `openWith` that match, user selects which plugin to handle
- `plugin.json` adds **`openWith`** field: `enabled` / `label` (relay page display name) / `mimeTypes` (supported MIME, supports `text/*`, `*/*` wildcards) / `acceptText` / `acceptUrl` / `acceptFile` (individual toggles for text, links, files)
- Supports **single file, multi-file (`SEND_MULTIPLE`)** reception, files auto-copied into plugin `<pluginDir>/.incoming/`, readable by plugin backend (proot container mounts `/plugins/<id>/.incoming`)
- **Web plugins** read via: host injects `window.UINOpenData` / `window.getOpenData()`, can also use `UINPlugin.getOpenData()` (returns JSON string `{}`, contains `kind`/`type`/`mime`/`url`/`text`/`uri`/`name`/`filePath`/`files` fields)
- **Native plugins** read via: `onHostEvent("host.open", bundle)` carries `instanceId` / `openDataJson`
- Auto-opens when only 1 plugin matches; no match shows relay page prompt; relay page supports **search** (filter by display name / plugin ID / description)
- Plugins declaring `openWith` appear in system share panel as "UIN Tool" entry (`ACTION_SEND` / `SEND_MULTIPLE` / `VIEW` + `text/*`, `*/*`, generic MIME)

### Plugin Multi-Instance (Multi-Instance)

- **Web / CUI plugins support multi-instance by default**: each opening starts new instance, page state, JS interface, backend mutually isolated
- **Native plugin multi-instance**: default single instance (reuses same instance on repeated opening), after enabling "Native Plugin Multi-Instance (Experimental)" in development tools page, each opening creates independent `PluginInterface` instance and View
- Each instance has globally unique **`instanceId`**, host isolates lifecycle callbacks, WebView cache, native plugin instances and backend by "instance key" (`pluginId:instanceId`)
- **Backend and instance isolation**: default multi-instance reuses same backend process (shared port); "Per-Instance Independent Backend Port" in "Development Tools" when enabled, each instance has its own backend process and port (`startBackendInstance` starts by instance key), mutually independent

### Relay Page Search

- "Open with..." relay page adds **search box**: filters candidate plugins by display name / plugin ID / description in real-time, convenient for quick positioning when there are many plugins

### Plugin Management Page Adds Shortcut

- Plugin management page list item "**Launch**" button changed to "**Add Desktop Shortcut**" (icon `+`), click to directly create desktop shortcut for that plugin, no need to enter details first

### Version Upgrade

- Version number upgraded to **5.4.0 (Build 20)**

---

## [5.3.0] - 2026-08-08

> This version is a **comprehensive refinement**: unified UI component system with glass effect implementation, enhanced color picker (visual color picker + dark mode), enhanced plugin category management, adds Shizuku / Dhizuku permission support, and verifies and fixes all UI personalization page features.

### New Global Gradient Background Effect

- Adds **global gradient background**: can be enabled/disabled in "UI Personalization" > "Effects" page, takes effect on **all pages**
- Supports **single-select (single-color gradient) / multi-select (multi-color gradient)** two modes, multi-select can choose 2-6 colors, supports add/delete/modify
- Supports **gradient direction setting**: can separately specify "**start direction**" and "**end direction**" (6 directions selectable)
- **Default single-color gradient (theme-adaptive)**: light `#FFC4D6DF` / dark `#FF4C4F51`, direction **bottom-right → top-left**; old "multi-color three-color default" auto-migrates, user custom configurations unaffected
- Configuration persists with save, **supports export/import** (includes `color`/`color_dark` fields) and "Restore Defaults"
- Gradient drawn on theme root layout, **top title bar follows gradient background**, auto-adapts with light/dark theme

### Glass Effect Transparency Increased

- Glass card/glass background transparency increased overall, glass texture more pronounced

### Unified UI Component System (Full Implementation)

- `Unified*` components become the **sole implementation source**: unified buttons/cards/input fields/text/switches/tags/icon buttons/list items/progress bars/dialog system, etc.
- `UIComponents` refactored into **thin delegation layer**, all screens (20+) migrated to directly use `Unified*` components, API fully compatible
- Unified cards (Glass)/input fields/dialogs support **glass effect**: semi-transparent background, no border, no shadow, follows theme

### Color Picker Enhancement

- Adds **visual color picker** (hue bar + saturation/brightness panel, click/drag to pick any color) and **hex input box**, real-time sync with RGB/Alpha
- Dialog color scheme **follows light/dark theme** (night mode), retains sliders and preset palette

### Plugin Management and Categories

- Plugin details page and export/delete action bar add "**Change Category**", supports individual/batch modification, can select existing categories or custom
- Category filter bar supports **horizontal swipe**; plugin item **long-press** shows details popup (info, file structure, plugin.json raw text), can directly **change category / uninstall**
- Checkboxes **only show in selection mode**, top bar adds selection mode toggle; **batch delete fix**: supports deleting all selected plugins at once (with quantity confirmation dialog)

### Plugin List Scrollbar (Removed)

- Tools page plugin list and plugin management page list **remove right-side vertical scrollbar**, list items **scroll as-is**, no animation displacement

### Page Slide Transition No Longer Fades Dark

- `slide_in/out_left/right.xml` all remove alpha fade-in/fade-out, old/new pages slide in/out **maintaining brightness throughout**, only retains displacement and scale

### Bottom Navigation Floating Style / Wizard Action Bar / Button Outline

- Bottom navigation changed to **floating style**: card background, rounded corners and shadow, covers content without clipping; clicking **press-to-scale**, selected icon enlarges, no ripple
- Development wizard "Next" action bar changed to **floating pure transparent**: removes fixed height/card background/shadow, tight to buttons; content area **full-height scrolls**
- Outline buttons (Outlined) add explicit 1dp theme outline, white/transparent background buttons have clear contours

### Dialog Background Fully Consistent with Main Background

- Dialogs (unified dialogs/loading/bottom/update/color picker/alert) background **same gradient as main page, fully opaque**, no longer shows through to components behind
- Original Material3 `AlertDialog` unified replaced with `UnifiedAlertDialog`; transparent text buttons add 1dp theme outline (`UnifiedDialogTextButton`)

### UI Personalization Page Optimization

- Top tab bar follows background color and removes bottom border line
- Gradient "mode / start / end direction" changed to **dropdown**, all gradient configuration merged into single card
- Code editor file list background follows main background
- **Feature verification & fixes**: ripple toggle actually works, status bar/navigation bar color customizable, dark primary (`primary_dark`) integrated into theme

### Permission Management Enhancement + Status Auto-Refresh

- Adds **Shizuku** and **Dhizuku** permission support (fully using official API)
- Permission checkmark status **auto-refreshes** (callback/authorization listener/page return trigger), no longer needs manual pull-to-refresh

### Code Structure Optimization

- Mirror constants, file utilities, size formatting converged to single implementation; Repository naming unified (`BackupRepository` → `IBackupRepository`)

### Version Upgrade

- Version number upgraded to **5.3.0 (Build 19)**

---

## [5.2.0] - 2026-08-06

### Packaging Logic Refinement (Package Everything)

- `JavaToDexCompiler.packageTpk` rewrite: no longer selects only specific directories by type for packaging, changed to recursively package entire project directory (`web/`, `scripts/`, `scripts/backend/server.py`, `start.sh`, `res/`, `src/`, and any resources all included in TPK), skips hidden files and `.tpk` output, avoids duplicate entries
- Explicitly adds `plugin.json`, `icon.png`, `README.md`, native placeholder/real `plugin.dex` (prioritizes recognizing real DEX's `dex\n` magic)
- Web plugins without `web/index.html` still write default page as fallback

### Update Logic Refinement

- Silent update (once daily): adds `KEY_LAST_UPDATE_CHECK` (epoch day) and `get/setLastUpdateCheckDay`, `get/setLastChangelog`, `MainContent` top `LaunchedEffect` triggers background check once daily, shows update dialog when new version exists and not ignored
- Adds shared component `ui/components/UpdateContent.kt` (`ReleaseChangelog` Markdown rendering + `UpdateDialog` popup + `UpdateContent` body), shared between Splash and management page "Check for Updates"
- Management page "Check for Updates" card changed from plain text ConfirmDialog (only shows first 200 characters, no Markdown) to full Markdown `UpdateDialog`
- Version update guide page: `isVersionUpdate` with changelog uses full-screen Markdown page (`VersionUpdateScreen`), shares `ReleaseChangelog` rendering with popup

### Built-in Termux Alpine Installation Speedup

- `ProotContainerManager.ensureAlpine()` removes network `pkg install proot-distro -y` (main cause of slow first install), now only does existence check; first install only has `proot-distro restore` (decompresses about 19MB rootfs) one-time overhead
- If proot-distro is indeed missing, only logs/status prompt, restore ends with clear error, no longer silently uses network

### Development Wizard Refinement

- Packaging no longer auto-exits: bottom button shows "Package" before packaging, changes to "Finish" after success, click to exit
- Create plugin configuration page completes all fields (minimum host version, API level, category, update URL, dependencies), adds permission multi-select (37 permissions chip)
- `plugin.json` editor popup uses new `JsonSyntaxHighlighter` (`VisualTransformation`) for JSON syntax highlighting
- Configuration field info icons changed to unified overview: info icons beside each field removed, only retains title row overview button (popup introduces all fields completely)

### Code Editor

- File tree `FileTreeItem` uses `combinedClickable`: long-press shows anchored menu (view properties / rename)
- Properties popup shows filename, type, line count, character count; rename popup validation (non-empty, no duplicates), cross `files`/`contents`/`currentFile` synchronized rename with success/failure prompt

### Plugin Management Page

- Click plugin card opens new scrollable details dialog (`PluginDetailDialog`):
  - **plugin.json fields**: ID, version, minimum host version, API level, name, author, description, category, UI type, entry, main class, update URL, plugin notice, dependencies, permissions, startup command
  - **File structure**: Plugin directory file tree (directories/files with sizes)
  - **plugin.json raw text**: Read from disk and formatted (fallback to `PluginInfo.toJson()` on failure)
  - Bottom shows total plugin size and file count, can directly run plugin

### Backend Settings Reorganization

- "Backend Runtime Settings" changed from popup to complete page (`BackendSettingsActivity` + `BackendSettingsScreen`, with back bar): implementation/environment/container/idle reclamation settings
- Real Termux section adds "Initialization Command" card, upper right copy icon one-click copy; command logic extracted to `BackendConfig.buildRealTermuxSetupCode()`, shared with plugin runtime guide
- Management page card order: Plugin Management → Permissions → Development Tools → Documentation → Backup → UI Personalization → Backend Settings → GitHub Acceleration → Widgets → Check for Updates
- Development page removes backend runtime settings entry and old `BackendSettingsDialog.kt`

---

## [5.1.0] - 2026-08-06

### Backend Runtime Architecture Refactoring (Core)

**Adds Global Backend Runtime Settings:**
- Adds "Backend Runtime Settings" (`BackendConfig` + development/management page settings popup), globally controls runtime environment for all backend plugins, persisted in `uin_backend_prefs`:
  - **Built-in Termux** (default): Uses app's built-in lightweight Termux, forced through Proot shared Alpine container (`alpine`)
  - **Real Termux** (`com.termux`): Launches external Termux via `RUN_COMMAND`, can optionally select Termux native or Proot container (container name configurable, default `alpine`)
- Adds **idle auto-reclamation**: Backend idle beyond configurable time (default 5 minutes, can set 3/5/10/15) auto-stops, active requests refresh the timer

**Backend Startup Command Unified (`backendStartCommand`):**
- Removes old language-based startup (python/node/php/... + `backendPort`/`backendEntry`/`backendPreCommand`), all unified to `backend = "other"` + `backendStartCommand` single path
- After plugin opens, host executes `sh -lc` to start script, environment variables (`$PORT`, `$PLUGIN_ID`, `$PLUGIN_DIR`, `$WORK_DIR`, etc.) inlined
- Legacy backends auto-migrate on loading (synthesize startup command in memory), no changes needed for published plugins
- **Removes pre-start command (`backendPreCommand`) popup flow**: deletes "Run Now/Later/Cancel" query dialog and `PreCommandResultReceiver`, `pre_cmd_done` marker no longer used

**Real Termux Support:**
- Adds `RealTermuxRuntime` wrapping `com.termux.app.RunCommandService`'s `RUN_COMMAND` intent, adds `com.termux.permission.RUN_COMMAND` permission declaration and `com.termux` package detection
- On startup failure, auto-detects and provides guide: `allow-external-apps=true`, `termux-setup-storage`, `proot-distro install`, RUN_COMMAND permission grant instructions
- Real Termux processes cannot be terminated by host, backend stop changed to calling agreed HTTP `/stop` endpoint for graceful shutdown

### CUI Terminal Startup Optimization

- Built-in Termux: Directly launches full-screen `TermuxActivity` in foreground (`EXTRA_SESSION_ACTION = SWITCH_TO_NEW_SESSION_AND_DONT_OPEN_ACTIVITY`), no longer depends on overlay permission
- Real Termux: Launches `com.termux` full-screen terminal after creating session via `RUN_COMMAND`
- Both paths changed to pure fade transition (`overridePendingTransition(R.anim.fade_in, 0)`), fixes system desktop visible during cross-fade gap

### Development Tools Integration

- "Runtime Logs" and "Developer Options" merged into standalone "Development Tools" page (`DevToolsActivity`/`DevToolsScreen`), entered from single management page menu
- Management page removes "Runtime Logs" and "Developer Options" entries, adds "Development Tools" and "Backend Runtime Settings" entries
- Auto-jumps to "Development Tools" page to display crash log after crash (original log page logic migrated to new page)

### Plugin Development Refinement

- web + backend plugin wizard no longer generates `web/script.js`, changed to generate inlined script `web/index.html` (`simple_index.html.tmpl`)
- Backend template changed to unified generation of `scripts/start.sh` (startup command) + `scripts/backend/server.py` (reads `$PORT` + `/health`, `/stop` endpoints), no longer generates different backend entries by language
- `plugin.json` editor dialog completes backend fields (`backend="other"`, `backendStartCommand`, `backendStartEntry`, `backendAutoStart`, `backendTimeout`, `backendHealthCheck`), `applyPluginJson` reads back synchronously
- Development page removes language-based backend selection dialog, "Web UI + Backend" unified enters wizard to fill startup command

---

## [5.0.0] - 2026-08-05

### Full Internationalization (i18n)

- All hardcoded Chinese text in the app migrated to string resources: default English (en) + complete Simplified Chinese (zh-rCN), removed Japanese resources
- Covers 2600+ string keys, involving all screens: main interface, plugin management, permissions, repository, logs, backup, mirrors, documentation/help, development wizard, code editor, desktop widgets, etc.

### Dynamic Theme Engine

- Adds JSON dynamic theme engine: `UINToolTheme` reads UIConfig color scheme and takes effect immediately, supports dark mode synchronization
- Plugin WebView injects `--uin-*` CSS variables, theme color synchronizes with app
- Corner radius, font size all changed to configuration-driven, covering all pages
- Management bottom navigation bar and system status bar color follows theme, fixes purple theme residue

### Bottom Navigation Refactoring

- Bottom navigation changed to self-drawn (Row + clickable(indication=null)), avoids material3 NavigationBar / LocalIndication version differences
- Top thin border drawn along rounded corners, indicator icon changed to terminal prompt `>_`
- Tab switching adds horizontal slide + fade-in transition animation (AnimatedContent)

### Pull-to-Refresh Unification

- Removes all page top-right refresh icons, unified to Material 3 PullToRefreshBox pull-to-refresh
- Empty list state also supports pull-to-refresh; indicator uses theme color and fixed center
- 8 refreshable pages show "last update time", time fades in and auto-fades out after 1 second

### UI Optimization & Interaction

- Plugin list add/remove animation (animateItem + key)
- Repository/plugin management loading changed to skeleton screen (breathing flash placeholder)
- Mirror management "add mirror" changed to bottom-right FAB
- Toast unified to Material Snackbar (global host + lifecycle-aware)
- Glass effects applied to all cards and dialogs (UI personalization toggle control)
- Global Activity switching changed to "**slide + fade-in/fade-out**: old screen slides out and fades out, new screen slides in and fades in (`slide_in/out_*` includes displacement and opacity animation)
- Page slide transition animation **adds scale effect**: old screen **shrinks** when sliding out (scale 1.0→0.9), new screen **enlarges** when sliding in (scale 0.9→1.0), pivot at view center, switching has more front-to-back depth (displacement + scale + opacity compound animation)
- Management page top bar unified to `ManageTopAppBar`, color follows theme and page background

### Other Optimizations

- Light/dark color palettes can be edited and used simultaneously, app loads theme on startup
- Splash restores transparent background icon, faster startup (700ms fade-in scale animation)
- Fixes management page back/save button click not working, mirror dialog purple background, etc.

---

## [4.5.0] - 2026-08-03

### Proot Container Runtime + Custom Backend

#### Proot Container Runtime (`backendRuntime: "proot"`)

- Plugin backend can run in a **shared Alpine container**, isolated from host environment
- Auto-initializes Termux environment on first use, restores Alpine container offline from `assets/alpine.tar.xz` via `proot-distro restore`
- `assets/alpine.tar.xz` is backup generated by `proot-distro backup alpine`, built-in pre-installed Python and other dependencies
- Can use `apk add` inside container to install dependencies, does not pollute host Termux environment
- Plugin directory auto-bound to `/plugins/<pluginId>` inside container, entry file directly visible in container
- `127.0.0.1:PORT` inside container communicates with host, backend API calls require no extra configuration
- Environment pipeline: Termux ready → Alpine ready → pre-start command → start backend

#### Pre-Start Command (`backendPreCommand`)

- Plugins can configure a pre-start command, executed in the Termux terminal (e.g., install dependencies, initialize data)
- On first open, popup to choose: "Run Now" / "Later" / "Cancel"
- After successful execution (exit 0) once, permanently skipped (`pre_cmd_done` marker, stored in `plugin_data_<id>`)
- On execution failure, automatically returns to plugin page and shows exit code and error message

#### Custom Backend Mode (`backend: "other"`)

- Host does not auto-start backend process; pre-start command launches service in terminal
- Backend readiness determined by TCP port polling (200ms), timeout relaxed to 90s+ for container cold startup
- Supports portless plugins (`backendPort: 0`), pre-command session alive means running

#### Backend Connection Speedup

- Three OkHttpClient instances add `.proxy(Proxy.NO_PROXY)`, prevents system proxy hijacking loopback traffic
- `waitForReady` removes 1s hardcoded delay, changed to 200ms TCP port detection + HTTP health check polling
- When stopping backend, terminates by process group `SIGKILL` (`Os.kill(-pid, SIGKILL)`), ensures proot child processes also exit

#### Other Fixes

- Fixes onboarding flash and re-popup after skipping: removes SplashActivity dual navigation paths, unified to Compose-driven, fixes permission dialog first-frame flash

#### Wizard & Documentation

- Plugin wizard supports "Backend Runtime Environment" (Termux native / Proot container) and "Pre-Start Command" configuration
- Backend selection adds "Custom (Manual Start)" type
- **CUI Terminal Plugin**: Create plugin adds "CUI Terminal (Command-Line Interface)" type, 4-step wizard auto-generates `scripts/script.py` example script and startup command configuration
- Changelog, help documentation, README updated

### Template Export Refactoring + Development Tools Optimization

#### Plugin Template Export Refactoring

- Export template changed to directly copy **7 packaged plugins** from `assets/test_plugins/` (cuitest / othertest / termux / allapi / storage / NativeTestPlugin / web_plugin_template) as importable ready-made templates
- Auto-generates `README.txt` on export, listing each template file's purpose and import usage
- Cleans up original scattered plugin templates in assets, unified by built-in packaged plugins

#### Export Flow Fix

- Fixes "Exporting..." button getting stuck: export changed to background thread execution, resets state on main thread after completion
- Toast display thread-safe: background thread calling Toast no longer crashes (auto-switches to main thread)

#### UI Optimization

- Create plugin related buttons changed to pure theme color (`PrimaryButton`), removes gradient style

#### Build Optimization

- Streamlines `proguard-rules.pro`: only retains important code that might be deleted by R8 — **shell plugin host placeholder implementation**, `@JavascriptInterface` methods, plugin JSON models, etc.; removes overly broad rules, reduces release package size

---

## [4.4.4] - 2026-08-02

### Plugin Dialog System Unification + Interaction Fixes

#### Plugin Dialog System Unification

- Plugin dialogs all changed to app's built-in Compose unified dialog components (`UnifiedDialog` / `UnifiedConfirmDialog` / `UnifiedInfoDialog`)
- Removes old `UnifiedViewDialog` custom popup implementation
- JS `alert` / `confirm` / confirm dialog / input dialog / special permission popups unified through the same dialog components

#### Dialog Queue Mechanism

- Multiple popup requests display in sequence, no longer overwrite each other
- Automatically shows the next popup after the previous one closes
- Popup requests support callback style: `showConfirmDialog(title, message, callbackId)`, `showPromptDialog(title, hint, callbackId)`

#### Interaction Fixes

- Fixes plugin page unable to scroll/click: dialog overlay hidden by default, only shows when popup is displayed
- Fixes confirm dialog not showing
- Fixes screenshot function unable to save (changed to view drawing capture method)
- Fixes screenshot silent failure when no storage permission

---

## [4.4.0] - 2026-07-28

### Major Update: Plugin Data Persistent Storage + Permission System Completion

#### Plugin Data Persistent Storage

- **Unified Data Storage System**:
  - SharedPreferences-based key-value storage
  - Supports String, Int, Long, Boolean, Float, JSON full types
  - Each plugin has independent storage space (`plugin_data_{pluginId}`)
  - Data version management, supports plugin upgrade data migration

- **Plugin Data Directory**:
  - `data/` directory: Plugin user data storage
  - `cache/` directory: Plugin cache file storage
  - Plugin update auto-preserves `data/` directory, user data not lost
  - Auto-cleans all data when uninstalling plugin

- **File System API (Web Plugin JavaScript)**:
  - `writeFile(fileName, content)`: Write file
  - `readFile(fileName)`: Read file
  - `deleteFile(fileName)`: Delete file
  - `fileExists(fileName)`: Check if file exists
  - `listFiles()`: List all files
  - `getFileSize(fileName)`: Get file size
  - `clearCache()`: Clear cache

- **KV Storage API (Web Plugin JavaScript)**:
  - `setStorage(key, value)` / `getStorage(key)`: String storage
  - `setStorageInt(key, value)` / `getStorageInt(key, default)`: Integer storage
  - `setStorageBool(key, value)` / `getStorageBool(key, default)`: Boolean storage
  - `setStorageFloat(key, value)` / `getStorageFloat(key, default)`: Float storage
  - `setStorageJSON(key, json)` / `getStorageJSON(key)`: JSON storage
  - `removeStorage(key)`: Delete key
  - `clearStorage()`: Clear all data
  - `containsStorageKey(key)`: Check key existence
  - `getAllStorage()`: Get all data
  - `getStorageKeys()`: Get all keys

- **Batch Operations API (Web Plugin JavaScript)**:
  - `setStorageBatch(jsonData)`: Batch write
  - `getStorageBatch(keys)`: Batch read

- **Data Statistics API (Web Plugin JavaScript)**:
  - `getStorageStats()`: Get storage statistics (KV count, file count, total size, cache size)
  - `getDataVersion()`: Get data version
  - `exportData()`: Export all data as JSON
  - `importData(jsonData)`: Import data from JSON

- **Native Plugin Storage API (Kotlin)**:
  - `PluginContext.putString/getString`: String storage
  - `PluginContext.putInt/getInt`: Integer storage
  - `PluginContext.putBoolean/getBoolean`: Boolean storage
  - `PluginContext.putJSON/getJSON`: JSON storage
  - `PluginContext.writeFile/readFile`: File read/write
  - `PluginContext.deletePluginFile`: Delete file
  - `PluginContext.listPluginFiles`: List files
  - `PluginContext.getPluginFileSize`: Get file size
  - `PluginContext.getStorageStats`: Get storage statistics
  - `PluginContext.getPermissionState/setPermissionState`: Permission state management

- **Data Migration**:
  - Old `web_plugin_` SharedPreferences data auto-migrates to new system
  - Auto-executes migration on first launch, transparent to users

---

#### Permission System Fully Completed

- **Permanent Authorization State**:
  - Plugin permission status persisted to SharedPreferences
  - State values: 0=unauthorized (show popup), 1=authorized (enter directly), 2=denied (enter directly)
  - One-time authorization, permanently effective, no repeat popups

- **Permission State Management**:
  - `PluginContext.getPermissionState()`: Read permission state
  - `PluginContext.setPermissionState(state)`: Write permission state
  - `PluginContext.shouldShowPermissionDialog()`: Check if popup needed
  - Supports clearing permission state (for debugging or re-authorization)

- **Permission Popup Optimization**:
  - Material Design 3 style popup
  - Displays all missing permissions and their descriptions
  - Regular and special permissions grouped display
  - Click cancel exits directly, does not enter plugin
  - Click authorize requests permission, enters plugin after granting

- **Permission Request Flow**:
  - State 1: Enter plugin directly
  - State 0 or 2: Check actual permissions, popup if missing
  - User clicks authorize → request permission → set state to 1 after granting
  - User clicks cancel → set state to 2, exit plugin
  - Some permissions denied → Toast prompt, set state to 1, no more popup

- **Special Permission Handling**:
  - Overlay, modify system settings, and other special permissions
  - Guide users to system settings to manually enable
  - Can still enter plugin after special permissions are denied (some features unavailable)

- **Permission Status Visualization**:
  - Plugin management page displays permission status
  - Permission management page can view each plugin's permission details
  - One-click authorize all permissions

---

#### Web Plugin API Major Expansion

Adds **140+ API** interfaces, covering the following categories:

| Category | API Count | Description |
|----------|-----------|-------------|
| Device Info | 16 | Model, version, screen, memory, CPU, build info, etc. |
| Sensors | 9 | Accelerometer, gyroscope, light, proximity, magnetic field, orientation, pressure, temperature, humidity |
| Location Services | 2 | Get location, reverse geocoding |
| Screen/Display | 5 | Brightness, auto brightness, display info, font scaling |
| System Settings | 7 | Airplane mode, Bluetooth, WiFi, mobile data, location, NFC, auto-rotate, do not disturb |
| Storage Info | 3 | Total capacity, available capacity, usage percentage |
| Network Data | 11 | Network info, WiFi info, signal strength, carrier, IP, speed, Ping |
| Battery | 5 | Level, health, voltage, temperature, technology |
| Audio | 5 | Volume, max volume, mute, headphone status |
| Time/Date | 5 | Current time, timezone, daylight saving |
| System Language | 4 | System language, country, region |
| App Management | 7 | App list, open app, app info |
| File Operations | 15 | Read/write/delete, copy/move, directory operations, file info |
| Network Requests | 5 | GET, POST, PUT, DELETE, download |
| Permissions | 3 | Check, request, batch request |
| UI | 4 | Loading, confirm dialog, input dialog |
| Clipboard | 3 | Copy, get, clear |
| Vibration | 2 | Vibrate, cancel |
| Notifications | 2 | Send, cancel |
| System Operations | 10 | Open various settings, fullscreen, keep awake, screenshot |
| Events | 2 | Send event, add listener |

---

#### Code Editor Enhancement

- **Syntax Highlighting Fix**: Fixes TextMate syntax loading issue
- **Theme Application Optimization**: Correctly applies theme when switching files
- **Performance Optimization**: Reduces unnecessary recomposition

---

#### Bug Fixes

- Fixes permission popup clicking cancel still entering plugin
- Fixes permission state persistence failure
- Fixes some permissions still repeatedly popup after being denied
- Fixes `PluginPermissionManager` method signature error
- Fixes `PluginJSInterface` missing `ping` and other APIs
- Fixes `setStorageBatch` method does not exist
- Fixes permission request result callback not properly forwarded

---

## [4.2.0] - 2026-07-24

### Major Update: Termux Backend Integration + Plugin Development Enhancement

#### Termux Backend Integration

- **Plugin Backend Support**: Web plugins can directly start Termux backend services
  - Supports Python, Node.js, PHP language backends
  - Supports binary executable files as backends
  - Backend services auto-start, transparent to users
  - Backend process lifecycle management (auto-stops when plugin closes)

- **Backend Communication Protocol**:
  - HTTP API communication (no WebSocket needed)
  - Backend must provide `/health` health check endpoint
  - Backend auto-allocates port (default 8000)

- **Backend Template**: `python_template.tpk` template
  - Uses Python built-in `http.server`, no need to install Flask
  - Compatible with Python 3.14+ (removes `cgi` module dependency)
  - Supports computation, logging, querying, system commands, and other APIs

#### Plugin System Enhancement

- **Plugin Notice Feature**:
  - Plugins can declare `notice` field in `plugin.json`
  - Notice popup auto-displays on first plugin open
  - Users can choose "Don't prompt again" or "Remind later"
  - Plugin management page can view complete notice

- **Plugin Creation Wizard Optimization**:
  - Unified "Create Plugin" entry, popup selects frontend type
  - Supports native UI, pure WebView, WebView + backend three modes
  - Backend selection: Python, Node.js, PHP, binary files
  - Web plugins auto-generate blank HTML/CSS/JS files
  - Binary backend supports directly selecting executable files

#### Development Tools Enhancement

- **Export Templates**: Adds `python_template.tpk` template
- **Code Editor**:
  - Both native and Web plugins support code editor
  - Import existing Web projects (ZIP) functionality

#### Backend Management

- **PluginBackendManager**: Unified backend process management
  - Process start/stop/status query
  - Output monitoring (stdout/stderr)
  - Health check (waits for service ready)
  - Port auto-allocation

---

### UI Unification and Optimization

- Plugin notice popup uses Compose `AlertDialog`
- White background, Material 3 button style
- Removes all Emoji
- Development page button colors unified

---

## [4.1.0] - 2026-07-17

### Major Update: Code Editor Upgrade (Sora Editor)

#### Code Editor Comprehensive Upgrade

- **Sora Editor Integration**: Uses professional Sora Editor engine
  - Based on `io.github.rosemoe:sora-editor` 0.24.4
  - Uses `AndroidView` for perfect embedding in Compose
  - Supports syntax highlighting for 30+ programming languages

- **TextMate Syntax Highlighting**:
  - Supports Java, Kotlin, Python, JavaScript, TypeScript
  - Supports HTML, CSS, JSON, XML, Markdown
  - Supports Shell, SQL, Go, Rust, PHP, Ruby, Swift
  - Supports Dart, Lua, Scala, Perl, Haskell, Elixir
  - Supports Erlang, Clojure, Groovy, Dockerfile, Makefile
  - Supports INI, Properties, TOML, YAML, and other 30+ languages

- **Theme System**: Built-in 28+ code editor themes
  - Dark themes: dark-plus, dracula, one-dark-pro, material-theme, etc.
  - Light themes: vitesse-light, github-light, solarized-light, etc.
  - One-click theme switching, takes effect in real-time

- **Editor Feature Enhancement**:
  - Line number display
  - Code folding
  - Bracket matching highlighting
  - Smart auto-indentation
  - Undo/Redo history
  - File tree management (sidebar)
  - File type icons

---

## [4.0.0] - 2026-07-14

### Major Refactoring: Kotlin + Jetpack Compose Comprehensive Upgrade

#### Technology Stack Comprehensive Upgrade

- **Kotlin Full Migration**: Core code migrated from Java to Kotlin
  - 134 Kotlin files, 39 Java files
  - Leverages Kotlin null safety, coroutines, extension functions, etc.
- **Jetpack Compose**: UI fully migrated to declarative Compose framework
  - Compose 2024.09.00 BOM
  - Material 3 design language
- **MVVM Architecture**: Introduces ViewModel + StateFlow reactive state management
- **Repository Pattern**: Data layer and business layer separation
- **Dependency Injection**: ServiceLocator unified service instance management

#### UI Comprehensive Upgrade

- **Material 3 Design System**: New visual style
- **Dark Mode Completion**: Complete dark/light theme switching
- **Glass Effect**: Frosted glass texture UI components
- **Complete Color Picker**: RGB + Alpha channel independent adjustment
- **38+ Color Configuration Items**: All colors customizable
- **7 Corner Radius Configurations**: Comprehensive UI shape control

#### Terminal Features (Based on Termux)

- **Built-in Termux Engine**: Complete Termux terminal emulator integration
- **Complete Linux Environment**: APT package manager, bash/zsh, etc.
- **Multi-Session Support**: Multiple terminal sessions simultaneously
- **Multi-Window Support**: Android 7.0+ multi-window/split-screen

#### Plugin System Enhancement

- **Plugin Permission System**: Plugin permission management based on Android permission model
- **Plugin Dependency Check**: Auto-checks and prompts for missing dependencies
- **UI Configuration Import/Export**: Supports configuration backup and sharing
- **Complete Backup System**: Backup plugins, configurations, UI themes

#### Development Tools Enhancement

- **Plugin Creation Wizard**: Visual step-by-step guidance for creating plugins
- **Built-in Code Editor**: Supports zoom, syntax highlighting, file management
- **Web Project Import**: Supports importing existing Web project ZIP packages

#### Performance Optimization

- Cold startup time optimized by 30%
- Compose recomposition optimization
- WebView cache pool management
- Memory usage optimization

---

## Upgrade Guide

### From v5.0.0 to v5.1.0

v5.1.0 refactored the backend runtime architecture, please note before upgrading:

1. **Backend runtime environment changed to global configuration**: Legacy fields in old plugins (`backendRuntime`/`backendPort`/`backendEntry`/`backendBinary`, etc.) are no longer used in the new startup workflow; they are auto-migrated to `backendStartCommand` on loading (completed in memory, not written back to plugin file), no plugin changes needed
2. **Pre-start command popup removed**: The old "Run Now/Later/Cancel" `backendPreCommand` popup flow has been deleted, `pre_cmd_done` marker no longer used
3. **Runtime environment switching**: If you need to use real Termux to run the backend, switch in "Backend Runtime Settings" on the "Dev"/"Manage" page, and follow the guide to enable `allow-external-apps`, execute `termux-setup-storage`, grant RUN_COMMAND permission
4. **Backend stop method changed**: Real Termux processes cannot be terminated by the host; backends need to implement the `/stop` endpoint for graceful shutdown
5. **New idle auto-reclamation**: Backend idle beyond set time (default 5 minutes) auto-stops

### From v4.4.0 to v4.4.4

v4.4.4 is a plugin dialog and interaction fix version, please note before upgrading:

1. **Dialog appearance changed**: Plugin dialogs unified to built-in Compose dialog components, style and interaction slightly adjusted
2. **Dialog queuing**: Multiple popup requests will display sequentially, no longer overwrite each other
3. **New APIs**: Adds `showConfirmDialog`, `showPromptDialog` callback-style popup APIs
4. **Compatibility**: Fully backward compatible, plugins do not need modification

### From v4.2.0 to v4.4.0

v4.4.0 is a data persistence and permission system completion version, please note before upgrading:

1. **Data auto-migration**: Old plugin data auto-migrates to new storage system, no manual operation needed
2. **Permission state reset**: Some plugin permission states may need re-authorization after upgrade
3. **API compatibility**: New APIs are fully backward compatible, old plugins do not need modification
4. **Storage location change**: Plugin data is now stored in the `data/` directory

### From v4.1.0 to v4.2.0

v4.2.0 is a Termux backend integration version, please note before upgrading:

1. **Termux environment**: Requires Termux terminal environment to be installed
2. **Python backend**: First use auto-installs FastAPI dependencies
3. **Plugin notice**: Old plugins can manually add `notice` field to `plugin.json`
4. **Compatibility**: Fully backward compatible

### From v3.x to v4.0.0

v4.0.0 is a major refactoring, please note before upgrading:

1. **UI completely rewritten**: New Compose-based UI
2. **Configuration incompatible**: Old UI configurations need to be reset
3. **Plugin compatible**: Plugin system remains backward compatible
4. **Terminal feature enhancement**: Complete terminal experience based on Termux

---

| Item | Info |
|------|------|
| Document Version | 6.2.0 |
| Last Updated | October 16, 2026 |
| Corresponding App Version | v6.2.0 (Build 26) |

---

© 2026 UIN Team. All Rights Reserved.
