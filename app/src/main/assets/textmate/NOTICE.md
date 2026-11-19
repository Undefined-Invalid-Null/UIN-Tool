# Bundled TextMate grammars

Most grammar files under this directory come from
[shikijs/textmate-grammars-themes](https://github.com/shikijs/textmate-grammars-themes)
(MIT). They are data assets copied verbatim; see that repository for the upstream
license and per-grammar attribution.

Grammars added for 6.3.0:

| Directory | Scope | Source |
| --- | --- | --- |
| `csv` | `text.csv` | shikijs/textmate-grammars-themes |
| `cmake` | `source.cmake` | shikijs/textmate-grammars-themes |
| `nginx` | `source.nginx` | shikijs/textmate-grammars-themes |
| `asm` | `source.asm.x86_64` | shikijs/textmate-grammars-themes |
| `glsl` | `source.glsl` | shikijs/textmate-grammars-themes |
| `vue` | `text.html.vue` | shikijs/textmate-grammars-themes |
| `qml` | `source.qml` | shikijs/textmate-grammars-themes |
| `svelte` | `source.svelte` | shikijs/textmate-grammars-themes |
| `lrc` | `text.lrc` | written for UIN Tool (original) |

`lrc` was written in-house because no maintained TextMate grammar was available;
it highlights metadata tags, `[mm:ss.xx]` timestamps, enhanced `<mm:ss.xx>` word
timestamps and `#` comments.
