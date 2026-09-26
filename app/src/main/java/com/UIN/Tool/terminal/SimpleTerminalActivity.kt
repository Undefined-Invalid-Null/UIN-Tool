package com.UIN.Tool.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.UIN.Tool.R
import com.UIN.Tool.log.Logger
import com.UIN.Tool.plugin.ProotContainerManager
import com.UIN.Tool.proot.ProotInstaller
import com.UIN.Tool.proot.ProotRuntime
import com.UIN.Tool.proot.RootfsManager
import com.UIN.Tool.terminal.TerminalSession
import com.UIN.Tool.terminal.TerminalSessionClient
import com.UIN.Tool.ui.components.unified.UnifiedButton
import com.UIN.Tool.ui.components.unified.UnifiedDialog
import com.UIN.Tool.ui.components.unified.ButtonVariant
import com.UIN.Tool.ui.theme.AppDimens
import com.UIN.Tool.ui.theme.DarkBackground
import com.UIN.Tool.ui.theme.DarkSurface
import com.UIN.Tool.ui.theme.DarkSurfaceVariant
import com.UIN.Tool.ui.theme.DarkTextPrimary
import com.UIN.Tool.ui.theme.DarkTextSecondary
import com.UIN.Tool.ui.theme.DarkPrimaryGray
import com.UIN.Tool.ui.theme.DarkOutline
import com.UIN.Tool.ui.theme.UINToolTheme
import androidx.compose.ui.graphics.toArgb
import com.UIN.Tool.utils.Str
import com.UIN.Tool.view.TerminalView
import com.UIN.Tool.view.TerminalViewClient
import java.io.File

class SimpleTerminalActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(applyLocaleContext(newBase))
    }

    private fun applyLocaleContext(base: Context): android.content.Context {
        val uiConfig = try { com.UIN.Tool.utils.UIConfig.getInstance() } catch (_: Exception) { return base }
        val lang = uiConfig.getLanguage()
        val locale = when (lang) {
            "zh" -> java.util.Locale.CHINESE
            "en" -> java.util.Locale.ENGLISH
            else -> java.util.Locale.getDefault()
        }
        java.util.Locale.setDefault(locale)
        val config = android.content.res.Configuration(base.resources.configuration)
        config.setLocales(android.os.LocaleList(locale))
        return base.createConfigurationContext(config)
    }

    private var terminalView: TerminalView? = null
    private var drawerLayout: DrawerLayout? = null
    private var statusText: TextView? = null
    private var extraKeysContainer: LinearLayout? = null
    private var dialogHost: ComposeView? = null

    private val sessions = mutableListOf<TerminalSession>()
    private var currentSessionIndex = -1
    private var currentSession: TerminalSession? = null

    private var ctrlPressed = false
    private var altPressed = false
    private var ctrlButton: TextView? = null
    private var altButton: TextView? = null
    private var textSizePx = 28
    private var sessionCounter = 0
    private val sessionNames = mutableMapOf<Int, String>()

    // ==================== Theme Colors (Dark) ====================
    private val colorBackground = DarkBackground
    private val colorSurface = DarkSurface
    private val colorSurfaceVariant = DarkSurfaceVariant
    private val colorTextPrimary = DarkTextPrimary
    private val colorTextSecondary = DarkTextSecondary
    private val colorPrimary = DarkPrimaryGray
    private val colorOutline = DarkOutline
    private val colorActive = DarkPrimaryGray

    private val terminalViewClient = object : TerminalViewClient {
        override fun onScale(scale: Float): Float {
            if (scale > 1.06f) changeTextSize(2) else if (scale < 0.94f) changeTextSize(-2)
            return 1.0f
        }
        override fun onSingleTapUp(e: MotionEvent?) { showSoftKeyboard() }
        override fun shouldBackButtonBeMappedToEscape(): Boolean = false
        override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
        override fun isTerminalViewSelected(): Boolean = true
        override fun copyModeChanged(copyMode: Boolean) = Unit
        override fun onKeyDown(keyCode: Int, e: KeyEvent?, session: TerminalSession?): Boolean {
            if (keyCode == KeyEvent.KEYCODE_ENTER && session != null && !session.isRunning) {
                finish()
                return true
            }
            return false
        }
        override fun onKeyUp(keyCode: Int, e: KeyEvent?): Boolean = false
        override fun onLongPress(event: MotionEvent?): Boolean = false
        override fun readControlKey(): Boolean {
            val v = ctrlPressed; ctrlPressed = false; updateModifierButtons(); return v
        }
        override fun readAltKey(): Boolean {
            val v = altPressed; altPressed = false; updateModifierButtons(); return v
        }
        override fun readShiftKey(): Boolean = false
        override fun readFnKey(): Boolean = false
        override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean = false
        override fun shouldEnforceCharBasedInput(): Boolean = false
        override fun onEmulatorSet() = Unit
        override fun logError(tag: String?, msg: String?) { Logger.e(tag ?: "TerminalView", msg ?: "") }
        override fun logWarn(tag: String?, msg: String?) { Logger.w(tag ?: "TerminalView", msg ?: "") }
        override fun logInfo(tag: String?, msg: String?) { Logger.i(tag ?: "TerminalView", msg ?: "") }
        override fun logDebug(tag: String?, msg: String?) { Logger.d(tag ?: "TerminalView", msg ?: "") }
        override fun logVerbose(tag: String?, msg: String?) {}
        override fun logStackTraceWithMessage(tag: String?, msg: String?, e: Exception?) { Logger.e(tag ?: "TerminalView", msg ?: "", e) }
        override fun logStackTrace(tag: String?, e: Exception?) { Logger.e(tag ?: "TerminalView", "error", e) }
    }

    private val terminalSessionClient = object : TerminalSessionClient {
        override fun onTextChanged(changedSession: TerminalSession) {
            terminalView?.onScreenUpdated()
        }
        override fun onTitleChanged(changedSession: TerminalSession) {
            updateStatus()
            updateSessionList()
        }
        override fun onSessionFinished(finishedSession: TerminalSession) {
            Logger.i(TAG, "Session finished exit=${finishedSession.exitStatus}")
            updateStatus()
            updateSessionList()
        }
        override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("terminal selection", text.orEmpty()))
            com.UIN.Tool.utils.AppToast.success(this@SimpleTerminalActivity, getString(R.string.terminal_log_copied))
        }
        override fun onPasteTextFromClipboard(session: TerminalSession?) { pasteFromClipboard() }
        override fun onBell(session: TerminalSession) = Unit
        override fun onColorsChanged(session: TerminalSession) = Unit
        override fun onTerminalCursorStateChange(state: Boolean) = Unit
        override fun setTerminalShellPid(session: TerminalSession, pid: Int) {
            Logger.i(TAG, "Session pid=$pid")
            updateStatus()
        }
        override fun getTerminalCursorStyle(): Int = 0
        override fun logError(tag: String?, msg: String?) {}
        override fun logWarn(tag: String?, msg: String?) {}
        override fun logInfo(tag: String?, msg: String?) {}
        override fun logDebug(tag: String?, msg: String?) {}
        override fun logVerbose(tag: String?, msg: String?) {}
        override fun logStackTraceWithMessage(tag: String?, msg: String?, e: Exception?) {}
        override fun logStackTrace(tag: String?, e: Exception?) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = colorBackground.toArgb()
        window.navigationBarColor = colorBackground.toArgb()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        // 读取外部传入的命令（CUI 插件使用）
        intentCommand = intent.getStringExtra(EXTRA_COMMAND)

        setContentView(R.layout.terminal_activity_layout)
        bindViews()
        setupToolbar()
        setupExtraKeys()
        setupSidebar()
        setupDialogHost()

        if (!prepareRuntime()) return
        startNewSession()
    }

    override fun onDestroy() {
        sessions.forEach { it.finishIfRunning() }
        sessions.clear()
        super.onDestroy()
    }

    private fun prepareRuntime(): Boolean {
        if (!ProotInstaller.isInstalled(this)) {
            showError(getString(R.string.terminal_proot_not_installed))
            return false
        }
        val name = RootfsManager.getDefaultContainer(this)
        if (!RootfsManager.isContainerReady(this, name)) {
            showError(getString(R.string.terminal_container_not_ready))
            return false
        }
        return true
    }

    private fun bindViews() {
        terminalView = findViewById(R.id.terminal_view)
        drawerLayout = findViewById(R.id.terminal_drawer)
        statusText = findViewById(R.id.terminal_status_text)
        extraKeysContainer = findViewById(R.id.extra_keys_container)

        terminalView?.apply {
            setBackgroundColor(colorBackground.toArgb())
            setTextSize(textSizePx)
            keepScreenOn = true
            isFocusable = true
            isFocusableInTouchMode = true
            setTerminalViewClient(terminalViewClient)
        }
        updateStatus()
    }

    private fun setupToolbar() {
        styleToolbarButton(findViewById(R.id.terminal_close_button)) { finish() }
    }

    private fun setupDialogHost() {
        if (dialogHost != null) return
        dialogHost = ComposeView(this).apply {
            setContent {
                UINToolTheme(fillBackground = false) {
                    TerminalDialogHost()
                }
            }
        }
        val rootView = findViewById<ViewGroup>(android.R.id.content)
        rootView.addView(
            dialogHost,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        dialogHost?.bringToFront()
    }

    private var showRenameDialog by mutableStateOf(false)
    private var renamePosition by mutableStateOf(0)
    private var renameText by mutableStateOf("")
    private var renameEditText: android.widget.EditText? = null

    @Composable
    private fun TerminalDialogHost() {
        if (showRenameDialog) {
            UnifiedDialog(
                onDismissRequest = {
                    showRenameDialog = false
                },
                title = Str.get(R.string.terminal_rename_session),
                content = {
                    Column {
                        Text(
                            text = Str.get(R.string.terminal_session_number, renamePosition + 1),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val textColor = MaterialTheme.colorScheme.onSurface
                        val hintColor = MaterialTheme.colorScheme.onSurfaceVariant
                        val bgColor = MaterialTheme.colorScheme.surfaceVariant
                        val primaryColor = MaterialTheme.colorScheme.primary
                        val outlineColor = MaterialTheme.colorScheme.outline
                        val radius = AppDimens.radiusMedium
                        androidx.compose.ui.viewinterop.AndroidView(
                            factory = { ctx ->
                                android.widget.EditText(ctx).apply {
                                    renameEditText = this
                                    setText(renameText)
                                    setSelectAllOnFocus(true)
                                    isSingleLine = true
                                    hint = Str.get(R.string.terminal_rename_session)
                                    setTextColor(textColor.toArgb())
                                    setHintTextColor(hintColor.toArgb())
                                    background = android.graphics.drawable.GradientDrawable().apply {
                                        setColor(bgColor.toArgb())
                                        cornerRadius = radius.value * ctx.resources.displayMetrics.density
                                        setStroke((1.5f * ctx.resources.displayMetrics.density).toInt(), outlineColor.toArgb())
                                    }
                                    setPadding(
                                        (16 * ctx.resources.displayMetrics.density).toInt(),
                                        (12 * ctx.resources.displayMetrics.density).toInt(),
                                        (16 * ctx.resources.displayMetrics.density).toInt(),
                                        (12 * ctx.resources.displayMetrics.density).toInt()
                                    )
                                    addTextChangedListener(object : android.text.TextWatcher {
                                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                                            renameText = s?.toString() ?: ""
                                        }
                                        override fun afterTextChanged(s: android.text.Editable?) {}
                                    })
                                    setOnFocusChangeListener { _, hasFocus ->
                                        if (hasFocus) {
                                            background = android.graphics.drawable.GradientDrawable().apply {
                                                setColor(bgColor.toArgb())
                                                cornerRadius = radius.value * ctx.resources.displayMetrics.density
                                                setStroke((1.5f * ctx.resources.displayMetrics.density).toInt(), primaryColor.toArgb())
                                            }
                                            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                                            imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                                        } else {
                                            background = android.graphics.drawable.GradientDrawable().apply {
                                                setColor(bgColor.toArgb())
                                                cornerRadius = radius.value * ctx.resources.displayMetrics.density
                                                setStroke((1.5f * ctx.resources.displayMetrics.density).toInt(), outlineColor.toArgb())
                                            }
                                        }
                                    }
                                    post {
                                        requestFocus()
                                        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                                        imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    UnifiedButton(
                        text = Str.get(R.string.ok_2),
                        onClick = {
                            val newName = renameEditText?.text?.toString()?.trim() ?: renameText
                            if (newName.isNotEmpty()) {
                                sessionNames[renamePosition] = newName
                                updateSessionList()
                                updateStatus()
                            }
                            renameEditText = null
                            showRenameDialog = false
                        },
                        modifier = Modifier.weight(1f)
                    )
                },
                dismissButton = {
                    UnifiedButton(
                        text = Str.get(R.string.cancel),
                        onClick = { showRenameDialog = false },
                        variant = ButtonVariant.Outlined,
                        modifier = Modifier.weight(1f)
                    )
                }
            )
        }
    }

    private fun setupSidebar() {
        findViewById<TextView>(R.id.new_session_button)?.setOnClickListener {
            startNewSession()
            drawerLayout?.closeDrawers()
        }
    }

    private fun updateSessionList() {
        val recyclerView = findViewById<RecyclerView>(R.id.session_list) ?: return
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = object : RecyclerView.Adapter<SessionViewHolder>() {
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): SessionViewHolder {
                val view = layoutInflater.inflate(R.layout.terminal_session_item, parent, false)
                return SessionViewHolder(view)
            }
            override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
                val session = sessions[position]
                val displayName = sessionNames.getOrDefault(position, getString(R.string.terminal_session_number, position + 1))
                holder.nameText.text = displayName
                holder.nameText.setTextColor(if (position == currentSessionIndex) colorPrimary.toArgb() else colorTextPrimary.toArgb())
                holder.itemView.setOnClickListener {
                    switchSession(position)
                    drawerLayout?.closeDrawers()
                }
                holder.itemView.setOnLongClickListener {
                    renamePosition = position
                    renameText = sessionNames.getOrDefault(position, getString(R.string.terminal_session_number, position + 1))
                    showRenameDialog = true
                    true
                }
                holder.closeButton.setOnClickListener {
                    closeSession(position)
                }
            }
            override fun getItemCount() = sessions.size
        }
    }

    private class SessionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.session_name)
        val closeButton: TextView = view.findViewById(R.id.session_close)
    }

    fun startNewSession() {
        if (!prepareRuntime()) return
        sessionCounter++
        val containerName = RootfsManager.getDefaultContainer(this)
        val rootfsDir = RootfsManager.rootfsDir(this, containerName).canonicalFile
        val prootTmpDir = File(cacheDir, "proot_tmp").apply { mkdirs() }.absolutePath

        val command = if (!intentCommand.isNullOrBlank()) {
            // CUI 插件：使用传入的启动命令（含环境变量 export）
            val rootfs = RootfsManager.rootfsDir(this, containerName)
            val shellPath = RootfsManager.findShell(rootfs) ?: "/bin/sh"
            val prootBin = com.UIN.Tool.proot.ProotInstaller.prootBinaryPath(this)

            // 构建 proot 参数
            val args = mutableListOf<String>()
            args.add(prootBin.absolutePath)
            args.addAll(listOf("-L", "--kill-on-exit", "--link2symlink", "--sysvipc", "--change-id=0:0"))
            args.addAll(listOf("--kernel-release=\\Linux\\uin-tool\\6.17.0-UIN-Tool\\#1 SMP PREEMPT_DYNAMIC\\aarch64\\localdomain\\-1\\"))
            args.addAll(listOf("-r", rootfs.canonicalPath))

            // 绑定挂载
            val bindMounts = listOf("/dev", "/proc", "/sys", "/storage/emulated/0")
            for (mount in bindMounts) {
                args.addAll(listOf("-b", mount))
            }
            args.addAll(listOf("-b", "/dev/urandom:/dev/random"))
            args.addAll(listOf("-b", "/proc/self/fd:/dev/fd"))
            args.addAll(listOf("-b", "/proc/self/fd/0:/dev/stdin"))
            args.addAll(listOf("-b", "/proc/self/fd/1:/dev/stdout"))
            args.addAll(listOf("-b", "/proc/self/fd/2:/dev/stderr"))

            // /dev/shm
            val shmDir = java.io.File(rootfs.parentFile ?: rootfs, "shm").apply { mkdirs() }
            args.addAll(listOf("-b", "${shmDir.absolutePath}:/dev/shm"))

            args.addAll(listOf("-w", "/"))
            args.addAll(listOf(shellPath, "-lc", intentCommand!!))
            args
        } else {
            ProotContainerManager.buildInteractiveShell(this, containerName)
        }
        val shellPath = command[0]
        val args = command.toTypedArray()
        val cwd = RootfsManager.rootfsDir(this, containerName).absolutePath

        // 环境变量通过 JNI env 参数传递（进程级），proot 直接继承
        val defaultEnv = ProotRuntime.getDefaultEnvWithL2s(rootfsDir)
        val mergedEnv = defaultEnv.toMutableMap().apply { put("PROOT_TMP_DIR", prootTmpDir) }
        val env = mergedEnv.map { (k, v) -> "$k=$v" }.toTypedArray()

        val session = TerminalSession(shellPath, cwd, args, env, 4000, terminalSessionClient)
        sessions.add(session)
        currentSessionIndex = sessions.size - 1
        currentSession = session

        terminalView?.attachSession(session)
        terminalView?.onScreenUpdated()
        terminalView?.requestFocus()
        updateModifierButtons()
        updateStatus()
        updateSessionList()
        Logger.i(TAG, "Started session #$sessionCounter (total=${sessions.size})")
    }

    private fun switchSession(index: Int) {
        if (index < 0 || index >= sessions.size) return
        currentSessionIndex = index
        currentSession = sessions[index]
        terminalView?.attachSession(sessions[index])
        terminalView?.onScreenUpdated()
        terminalView?.requestFocus()
        updateModifierButtons()
        updateStatus()
        updateSessionList()
    }

    private fun closeSession(index: Int) {
        if (index < 0 || index >= sessions.size) return
        sessions[index].finishIfRunning()
        sessions.removeAt(index)
        if (sessions.isEmpty()) {
            finish()
            return
        }
        val newIndex = index.coerceAtMost(sessions.size - 1)
        switchSession(newIndex)
    }

    private fun restartSession() {
        currentSession?.finishIfRunning()
        currentSession?.let { sessions.remove(it) }
        currentSession = null
        startNewSession()
    }

    private fun setupExtraKeys() {
        val container = extraKeysContainer ?: return
        container.removeAllViews()
        ctrlButton = null
        altButton = null

        val row1 = listOf(
            ExtraKeyDef("ESC", "\u001b"),
            ExtraKeyDef("/", "/"),
            ExtraKeyDef("\u2014", "-"),
            ExtraKeyDef("HOME", "\u001b[H"),
            ExtraKeyDef("\u2191", "\u001b[A"),
            ExtraKeyDef("END", "\u001b[F"),
            ExtraKeyDef("PGUP", "\u001b[5~"),
        )
        val row2 = listOf(
            ExtraKeyDef("\u21E5", "\t"),
            ExtraKeyDef("CTRL", isCtrl = true),
            ExtraKeyDef("ALT", isAlt = true),
            ExtraKeyDef("\u2190", "\u001b[D"),
            ExtraKeyDef("\u2193", "\u001b[B"),
            ExtraKeyDef("\u2192", "\u001b[C"),
            ExtraKeyDef("PGDN", "\u001b[6~"),
        )

        container.addView(createKeyRow(row1))
        container.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(3))
        })
        container.addView(createKeyRow(row2))
    }

    private fun createKeyRow(keys: List<ExtraKeyDef>): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        keys.forEach { key ->
            val button = keyButton(key.label) { handleExtraKey(key) }
            button.layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                marginStart = dp(1)
                marginEnd = dp(1)
            }
            if (key.isCtrl) ctrlButton = button
            if (key.isAlt) altButton = button
            addView(button)
        }
    }

    private fun handleExtraKey(key: ExtraKeyDef) {
        when {
            key.isCtrl -> ctrlPressed = !ctrlPressed
            key.isAlt -> altPressed = !altPressed
            else -> currentSession?.write(key.sequence)
        }
        updateModifierButtons()
        terminalView?.requestFocus()
    }

    private fun pasteFromClipboard() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).coerceToText(this).toString()
            currentSession?.write(text)
        } else {
            com.UIN.Tool.utils.AppToast.info(this, getString(R.string.terminal_log_empty))
        }
        terminalView?.requestFocus()
    }

    private fun showSoftKeyboard() {
        terminalView?.requestFocusFromTouch()
        terminalView?.post {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.restartInput(terminalView)
            imm.showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun changeTextSize(deltaPx: Int) {
        textSizePx = (textSizePx + deltaPx).coerceIn(24, 72)
        terminalView?.setTextSize(textSizePx)
        updateStatus()
    }

    private fun updateStatus() {
        val name = sessionNames.getOrDefault(currentSessionIndex, getString(R.string.terminal_session_number, currentSessionIndex + 1))
        val s = currentSession
        statusText?.text = if (s != null && s.pid > 0) {
            getString(R.string.terminal_session_pid, "$name (${currentSessionIndex + 1}/${sessions.size})", "${s.pid}")
        } else {
            "$name (${currentSessionIndex + 1}/${sessions.size})"
        }
    }

    private fun showError(message: String) {
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(colorBackground.toArgb())
            setPadding(dp(24), dp(24), dp(24), dp(24))
            addView(TextView(this@SimpleTerminalActivity).apply {
                text = message
                setTextColor(colorTextPrimary.toArgb())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                this@apply.gravity = Gravity.CENTER
            })
            addView(TextView(this@SimpleTerminalActivity).apply {
                text = getString(R.string.terminal_close)
                setTextColor(colorPrimary.toArgb())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                this@apply.gravity = Gravity.CENTER
                setPadding(0, dp(20), 0, 0)
                setOnClickListener { finish() }
            })
        })
    }

    private fun styleToolbarButton(button: TextView, onClick: () -> Unit) {
        button.typeface = Typeface.DEFAULT_BOLD
        button.gravity = Gravity.CENTER
        button.background = keyBackground(false, 8)
        button.isClickable = true
        button.isFocusable = true
        button.setOnClickListener { onClick() }
    }

    private fun keyButton(text: String, onClick: () -> Unit): TextView = TextView(this).apply {
        this.text = text
        setTextColor(colorTextPrimary.toArgb())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        typeface = Typeface.MONOSPACE
        gravity = Gravity.CENTER
        background = keyBackground(false, 6)
        minimumWidth = dp(0)
        setPadding(dp(8), dp(6), dp(8), dp(6))
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun keyBackground(active: Boolean, radiusDp: Int): android.graphics.drawable.Drawable {
        val shape = GradientDrawable().apply {
            setColor(if (active) colorActive.toArgb() else colorSurface.toArgb())
            cornerRadius = dp(radiusDp).toFloat()
        }
        val rippleColor = android.graphics.Color.argb(64, 0x8B, 0x94, 0x9E)
        return android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(rippleColor),
            shape,
            null
        )
    }

    private fun updateModifierButtons() {
        ctrlButton?.background = keyBackground(ctrlPressed, 4)
        altButton?.background = keyBackground(altPressed, 4)
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()

    private data class ExtraKeyDef(
        val label: String,
        val sequence: String = "",
        val isCtrl: Boolean = false,
        val isAlt: Boolean = false
    )

    companion object {
        private const val TAG = "SimpleTerminalActivity"
        private const val EXTRA_COMMAND = "command"

        fun start(context: Context, command: String? = null) {
            val intent = Intent(context, SimpleTerminalActivity::class.java)
            if (!command.isNullOrBlank()) {
                intent.putExtra(EXTRA_COMMAND, command)
            }
            context.startActivity(intent)
        }
    }

    private var intentCommand: String? = null
}
