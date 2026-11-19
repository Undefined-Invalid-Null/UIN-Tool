// app/src/main/java/com/UIN/Tool/ui/screen/files/RemoteFtpServer.kt
package com.UIN.Tool.ui.screen.files

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * FTP 服务端（RFC 959 常用子集，**可读可写**）。
 *
 * 覆盖文件管理器真正会用到的那批命令：登录、被动/主动数据通道、目录列举、
 * 上下载、增删改名建目录、大小与修改时间。没实现的（`REST` 断点续传、`APPE` 追加、
 * `SITE` 扩展）一律回 `502`，客户端会自己降级继续 —— 协议本身就是这么设计的。
 *
 * ## 为什么同时支持 PASV 和 PORT
 *
 * 被动模式（PASV）在 NAT 后面几乎必胜，但**手机当服务器**时方向反了：
 * 电脑主动连进来时，某些路由器会把 PORT 命令里的地址改写成外网地址。
 * 两个都实现，客户端挑哪个都能用；优先级交给客户端（它们默认先试 PASV）。
 *
 * ## 被动模式的 IP 用哪个
 *
 * 用**控制连接自己的本地地址**（`socket.localAddress`），不是 `lanIpv4()`。
 * 手机同时挂着 Wi-Fi 和热点时有两张网卡，凭网段猜会猜错；
 * 而控制连接走的是哪张网卡，客户端就连得上哪张 —— 这是唯一不会错的来源。
 */
class FtpServer : RemoteServer {

    private val _state = MutableStateFlow(RemoteState())
    override val state: StateFlow<RemoteState> = _state.asStateFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    override val log: StateFlow<List<String>> = _log.asStateFlow()

    /** 客户端看到的 `/` 就是它。越界判定统一交给 [resolveWithin]。 */
    @Volatile
    var root: File = FilePrefs.rootFile()

    /** 账号密码。**两者都空 = 不校验**（家里局域图个方便），UI 上会明确提示这一点。 */
    @Volatile
    var username: String = ""

    @Volatile
    var password: String = ""

    @Volatile
    private var listener: ServerSocket? = null

    @Volatile
    private var stopped = true

    override fun appendLog(line: String) {
        // 只留最近若干行：长连接的 FTP 会话能刷出几千行，全存着纯耗内存
        _log.value = (_log.value + line).takeLast(LOG_LIMIT)
    }

    /** 起服务。[port] 传 0 = 让系统随机分配（启动后从 [RemoteState.port] 读实际值）。 */
    fun start(port: Int) {
        if (_state.value.running) return
        stopped = false
        try {
            val ss = ServerSocket()
            // 端口被占时（上次没退干净）不直接失败，交给系统换一个
            ss.reuseAddress = true
            ss.bind(InetSocketAddress(port))
            listener = ss
            _state.value = RemoteState(running = true, port = ss.localPort)
            appendLog("FTP 已启动 · 端口 ${ss.localPort}")
            Thread({ acceptLoop(ss) }, "ftp-accept").apply {
                isDaemon = true
                start()
            }
        } catch (e: Exception) {
            val reason = e.message ?: e.javaClass.simpleName
            _state.value = RemoteState(running = false, port = 0, error = reason)
            appendLog("FTP 启动失败：$reason")
        }
    }

    fun stop() {
        stopped = true
        try {
            listener?.close()
        } catch (_: Exception) {
        }
        listener = null
        if (_state.value.running) appendLog("FTP 已停止")
        _state.value = RemoteState()
    }

    private fun acceptLoop(ss: ServerSocket) {
        while (!stopped) {
            val client = try {
                ss.accept()
            } catch (_: Exception) {
                break
            }
            Thread({ serve(client) }, "ftp-client").apply {
                isDaemon = true
                start()
            }
        }
    }

    /** 单个控制连接的完整生命周期：一行一条命令，直到 QUIT / 断线。 */
    private fun serve(client: Socket) {
        val peer = client.inetAddress?.hostAddress ?: "?"
        appendLog("[$peer] 已连接")
        try {
            client.use { sock ->
                sock.soTimeout = SOCKET_TIMEOUT_MS
                val reader = BufferedReader(
                    InputStreamReader(sock.getInputStream(), Charsets.UTF_8)
                )
                val writer = BufferedWriter(
                    OutputStreamWriter(sock.getOutputStream(), Charsets.UTF_8)
                ).also { it.flush() }

                fun reply(code: Int, msg: String) {
                    // 多行应答用 `code-` 开头、`code ` 结尾（RFC 959 §4.1.2）
                    writer.write("$code $msg\r\n")
                    writer.flush()
                }

                // ---- 会话级状态 ----
                var cwd: File = root
                var loggedIn = false
                var pendingUser: String = ""
                var renameFrom: File? = null
                var passiveSock: ServerSocket? = null
                var activeTarget: InetSocketAddress? = null

                reply(220, "UIN Tool FTP server ready")

                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    val cut = line.indexOf(' ')
                    val cmd = (if (cut < 0) line else line.substring(0, cut))
                        .uppercase(Locale.US)
                    val arg = if (cut < 0) "" else line.substring(cut + 1).trim()

                    // 登录前只放行身份类命令（RFC 959 的 530 语义）
                    val needLogin = cmd !in OPEN_COMMANDS
                    if (needLogin && !loggedIn) {
                        reply(530, "Please login with USER and PASS")
                        continue
                    }

                    when (cmd) {
                        "USER" -> {
                            pendingUser = arg
                            reply(331, "User name ok, need password")
                        }

                        "PASS" -> {
                            val open = username.isEmpty() && password.isEmpty()
                            if (open || (pendingUser == username && arg == password)) {
                                loggedIn = true
                                reply(230, if (open) "Anonymous access granted" else "User logged in")
                            } else {
                                loggedIn = false
                                reply(530, "Login incorrect")
                            }
                        }

                        "QUIT" -> {
                            reply(221, "Goodbye")
                            break
                        }

                        "NOOP" -> reply(200, "OK")
                        "SYST" -> reply(215, "UNIX Type: L8")
                        "TYPE" -> reply(200, "Type set")
                        "MODE" -> reply(200, "Mode set")
                        "STRU" -> reply(200, "Structure set")
                        "HELP" -> reply(214, "USER PASS QUIT PASV PORT LIST NLST RETR STOR DELE MKD RMD CWD PWD SIZE MDTM RNFR RNTO")

                        "FEAT" -> {
                            writer.write("211-Features:\r\n")
                            writer.write(" SIZE\r\n")
                            writer.write(" MDTM\r\n")
                            writer.write(" UTF8\r\n")
                            writer.write(" MLST type*;size*;modify*;\r\n")
                            writer.write(" PASV\r\n")
                            writer.write("211 End\r\n")
                            writer.flush()
                        }

                        "OPTS" -> reply(200, "Option accepted")

                        "PWD" -> reply(257, "\"${pwdOf(cwd)}\" is current directory")

                        "CWD" -> {
                            val target = ftpTarget(root, cwd, arg)
                            when {
                                target == null -> reply(550, "Permission denied")
                                !target.isDirectory -> reply(550, "Not a directory")
                                else -> {
                                    cwd = target
                                    reply(250, "Directory changed")
                                }
                            }
                        }

                        "CDUP" -> {
                            // f22：原先拿 `resolveWithin(root, parent.path)` 判越界 ——
                            // 那是**绝对路径**，而 resolveWithin 把以 `/` 开头的串当「相对 root」，
                            // 于是永远返回 null，「上一级」永远跳回服务根。改成直接比路径前缀。
                            val parent = cwd.absoluteFile.parentFile
                            cwd = if (parent != null && withinRoot(root, parent)) parent else root
                            reply(250, "Directory changed")
                        }

                        "PASV" -> {
                            closeQuietly(passiveSock)
                            val ss = ServerSocket()
                            ss.reuseAddress = true
                            ss.bind(InetSocketAddress(0))
                            passiveSock = ss
                            activeTarget = null
                            // RFC 959 的 PASV 应答是**四个独立数字** `h1,h2,h3,h4,p1,p2`，
                            // 不是点分字符串 —— 拼成 `192.168.1.5,20,1` 客户端直接解析失败
                            val ip = pasvIp(sock).replace('.', ',')
                            val p = ss.localPort
                            // 端口号拆成两个 7 位数：p = hi * 256 + lo
                            reply(
                                227,
                                "Entering Passive Mode ($ip,${p / 256},${p % 256})"
                            )
                        }

                        "PORT" -> {
                            val parts = arg.split(',').mapNotNull { it.trim().toIntOrNull() }
                            if (parts.size != 6 || parts.any { it !in 0..255 }) {
                                reply(500, "Bad PORT command")
                            } else {
                                closeQuietly(passiveSock)
                                passiveSock = null
                                val addr = "${parts[0]}.${parts[1]}.${parts[2]}.${parts[3]}"
                                val port = parts[4] * 256 + parts[5]
                                // 用控制连接对端的地址，不用命令里给的 —— 后者可伪造，
                                // 而且 NAT 后面客户端填的常是它自己的内网地址，未必是回程路
                                val real = client.inetAddress?.hostAddress ?: addr
                                activeTarget = InetSocketAddress(real, port)
                                reply(200, "PORT command successful")
                            }
                        }

                        "LIST", "NLST", "MLSD" -> {
                            // f22：空参数 = 当前目录、`-a`/`-al` 是选项不是路径，
                            // 两者都在 ftpTarget 里收口（原先直接拿 cwd.path 去 resolveWithin，
                            // 被当成相对 root 的路径 → 当前目录解析成不存在的路径 → 550）
                            val target = ftpTarget(root, cwd, arg, hasOptions = true)
                            if (target == null || !target.isDirectory) {
                                reply(550, "Failed to open directory")
                            } else {
                                val data = openData(client, passiveSock, activeTarget)
                                if (data == null) {
                                    reply(425, "Use PASV or PORT first")
                                } else {
                                    reply(150, "Here comes the directory listing")
                                    try {
                                        data.use { ds ->
                                            val w = BufferedWriter(
                                                OutputStreamWriter(ds.getOutputStream(), Charsets.UTF_8)
                                            )
                                            val names = target.listFiles()
                                                ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                                                .orEmpty()
                                            for (f in names) {
                                                w.write(
                                                    when (cmd) {
                                                        "NLST" -> f.name
                                                        // MLSD 的行格式与 LIST 完全不同：
                                                        // `fact=value;...; 名字`（RFC 3659），
                                                        // 现代客户端优先用它，给不出就退 LIST
                                                        "MLSD" -> mlsdLine(f)
                                                        else -> listLine(f)
                                                    }
                                                )
                                                w.write("\r\n")
                                            }
                                            w.flush()
                                        }
                                        reply(226, "Directory send ok")
                                    } catch (e: Exception) {
                                        appendLog("[$peer] LIST 失败：${e.message}")
                                        reply(451, "Local error in processing")
                                    }
                                }
                            }
                        }

                        "RETR" -> {
                            val target = ftpTarget(root, cwd, arg)
                            when {
                                target == null -> reply(550, "Permission denied")
                                !target.isFile -> reply(550, "Failed to open file")
                                else -> {
                                    val data = openData(client, passiveSock, activeTarget)
                                    if (data == null) {
                                        reply(425, "Use PASV or PORT first")
                                    } else {
                                        reply(150, "Opening binary mode data connection")
                                        try {
                                            data.use { ds ->
                                                target.inputStream().use { it.copyTo(ds.getOutputStream()) }
                                            }
                                            appendLog("[$peer] 下载 ${target.name}")
                                            reply(226, "Transfer complete")
                                        } catch (e: Exception) {
                                            appendLog("[$peer] 下载失败 ${target.name}：${e.message}")
                                            reply(451, "Local error in processing")
                                        }
                                    }
                                }
                            }
                        }

                        "STOR" -> {
                            val target = ftpTarget(root, cwd, arg)
                            if (target == null) {
                                reply(550, "Permission denied")
                            } else {
                                val data = openData(client, passiveSock, activeTarget)
                                if (data == null) {
                                    reply(425, "Use PASV or PORT first")
                                } else {
                                    reply(150, "Opening binary mode data connection")
                                    try {
                                        data.use { ds ->
                                            // CREATE 先截断：STOR 语义就是覆盖
                                            target.outputStream().use { out ->
                                                ds.getInputStream().copyTo(out)
                                            }
                                        }
                                        appendLog("[$peer] 上传 ${target.name}")
                                        reply(226, "Transfer complete")
                                    } catch (e: Exception) {
                                        appendLog("[$peer] 上传失败 ${target.name}：${e.message}")
                                        reply(451, "Local error in processing")
                                    }
                                }
                            }
                        }

                        "DELE" -> {
                            val target = ftpTarget(root, cwd, arg)
                            when {
                                target == null -> reply(550, "Permission denied")
                                !target.isFile -> reply(550, "Failed to delete file")
                                !target.delete() -> reply(550, "Delete failed")
                                else -> {
                                    appendLog("[$peer] 删除 ${target.name}")
                                    reply(250, "File deleted")
                                }
                            }
                        }

                        "MKD" -> {
                            val target = ftpTarget(root, cwd, arg)
                            when {
                                target == null -> reply(550, "Permission denied")
                                target.exists() -> reply(550, "Directory already exists")
                                !target.mkdirs() && !target.isDirectory -> reply(550, "Create failed")
                                else -> {
                                    appendLog("[$peer] 新建目录 ${target.name}")
                                    reply(257, "\"${pwdOf(target)}\" created")
                                }
                            }
                        }

                        "RMD" -> {
                            val target = ftpTarget(root, cwd, arg)
                            when {
                                target == null -> reply(550, "Permission denied")
                                target == root -> reply(550, "Refusing to remove root")
                                !target.isDirectory -> reply(550, "Not a directory")
                                !target.delete() -> reply(550, "Remove failed (目录非空?)")
                                else -> {
                                    appendLog("[$peer] 删除目录 ${target.name}")
                                    reply(250, "Directory removed")
                                }
                            }
                        }

                        "RNFR" -> {
                            val target = ftpTarget(root, cwd, arg)
                            if (target == null || !target.exists()) {
                                renameFrom = null
                                reply(550, "Failed to open file")
                            } else {
                                renameFrom = target
                                reply(350, "Ready for destination name")
                            }
                        }

                        "RNTO" -> {
                            val from = renameFrom
                            val to = ftpTarget(root, cwd, arg)
                            renameFrom = null
                            when {
                                from == null -> reply(503, "Bad sequence of commands")
                                to == null -> reply(550, "Permission denied")
                                to.exists() -> reply(550, "Target already exists")
                                !from.renameTo(to) -> reply(550, "Rename failed")
                                else -> {
                                    appendLog("[$peer] 重命名 ${from.name} → ${to.name}")
                                    reply(250, "Rename successful")
                                }
                            }
                        }

                        "SIZE" -> {
                            val target = ftpTarget(root, cwd, arg)
                            when {
                                target == null -> reply(550, "Permission denied")
                                !target.isFile -> reply(550, "Not a regular file")
                                else -> reply(213, target.length().toString())
                            }
                        }

                        "MDTM" -> {
                            val target = ftpTarget(root, cwd, arg)
                            when {
                                target == null -> reply(550, "Permission denied")
                                !target.exists() -> reply(550, "File not found")
                                else -> {
                                    // MDTM 要求 UTC：`yyyyMMddHHmmss` 后面不带时区
                                    val fmt = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply {
                                        timeZone = java.util.TimeZone.getTimeZone("UTC")
                                    }
                                    reply(213, fmt.format(Date(target.lastModified())))
                                }
                            }
                        }

                        else -> reply(502, "Command not implemented")
                    }
                }

                closeQuietly(passiveSock)
                appendLog("[$peer] 已断开")
            }
        } catch (e: Exception) {
            appendLog("[$peer] 连接异常：${e.message}")
        }
    }

    /**
     * 取数据连接：被动模式等客户端连进来，主动模式我们连过去。
     *
     * 被动套接字**只 accept 一次就交出去**，交给调用方 `use {}` 关掉 ——
     * 留着不关的话，下一条 PASV 之前的连接会一直堆在 backlog 里。
     */
    private fun openData(
        client: Socket,
        passive: ServerSocket?,
        active: InetSocketAddress?
    ): Socket? = try {
        when {
            passive != null -> {
                passive.soTimeout = DATA_ACCEPT_TIMEOUT_MS
                val s = passive.accept()
                s.soTimeout = SOCKET_TIMEOUT_MS
                s
            }
            active != null -> {
                val s = Socket()
                s.connect(active, CONNECT_TIMEOUT_MS)
                s.soTimeout = SOCKET_TIMEOUT_MS
                s
            }
            else -> null
        }
    } catch (_: Exception) {
        null
    }

    /** PASV 应答里报的地址：优先控制连接的本地地址（唯一不会猜错的来源）。 */
    private fun pasvIp(sock: Socket): String {
        val local = sock.localAddress
        if (local is Inet4Address && !local.isLoopbackAddress && !local.isLinkLocalAddress) {
            local.hostAddress?.let { return it }
        }
        return lanIpv4() ?: "0.0.0.0"
    }

    /** `PWD` 用的路径：以客户端视角写，root 就是 `/`。 */
    private fun pwdOf(dir: File): String {
        val rootPath = root.path.let { if (it.endsWith("/")) it else "$it/" }
        val full = dir.path
        val rel = when {
            full == root.path -> ""
            full.startsWith(rootPath) -> full.removePrefix(rootPath)
            else -> return full
        }
        // RFC 959：路径里的 `"` 要写成 `""`
        val escaped = rel.replace("\"", "\"\"")
        return if (escaped.isEmpty()) "/" else "/$escaped"
    }

    /** `LIST` 的一行，`ls -l` 风格 —— 大多数客户端按这个解析出目录/大小/时间。 */
    /**
     * FTP 命令里的路径参数 → 真实文件（f22）。
     *
     * 三件事在这里收口，缺一条就会回 550：
     *  1. **空参数 = 当前目录**。原先 `LIST` 分支写的是 `cwd.path`（绝对路径），
     *     而 [resolveWithin] 把以 `/` 开头的串当「相对 root」—— 当前目录于是被算成
     *     `root/storage/emulated/0` 这种不存在的路径，一律 550「Failed to open directory」。
     *  2. **相对路径相对的是当前目录**，不是服务根。`CWD sub` 之后客户端发 `RETR a.txt`，
     *     原先会去根目录找 `a.txt`。
     *  3. **客户端会带选项**（`LIST -a`、`LIST -al`、`LIST -la /sub`）：选项不是路径，
     *     先剔掉，剩下的才是路径。
     */
    private fun ftpTarget(
        root: File,
        cwd: File,
        arg: String,
        hasOptions: Boolean = false
    ): File? {
        var a = arg.trim()
        if (hasOptions) {
            a = a.split(' ').filterNot { it.startsWith("-") }.joinToString(" ").trim()
        }
        if (a.isEmpty()) return cwd
        // FTP 的 `/` 是「服务根」，交给 resolveWithin 统一做越界判定
        if (a.startsWith("/")) return resolveWithin(root, a)
        val base = pwdOf(cwd).trimEnd('/')
        return resolveWithin(root, "$base/$a")
    }

    /** [file] 是否落在 [root] 之内（含 root 本身）。两边都按 canonical 比。 */
    private fun withinRoot(root: File, file: File): Boolean {
        val r = runCatching { root.canonicalPath }.getOrDefault(root.absolutePath)
        val f = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
        return f == r || f.startsWith(if (r.endsWith("/")) r else "$r/")
    }

    /**
     * MLSD 的一行（RFC 3659）：`type=...;size=...;modify=YYYYMMDDHHMMSS; 名字`。
     *
     * 时间必须是 UTC 的 14 位数字（客户端拿它的 `modify` 与本地时间对比判新旧），
     * 用 `Locale.US` 与 `TimeZone` 固定，别让系统时区把时间平移。
     */
    private fun mlsdLine(f: File): String {
        val isDir = f.isDirectory
        val fmt = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val stamp = fmt.format(Date(if (f.lastModified() > 0) f.lastModified() else 0))
        val facts = StringBuilder("type=").append(if (isDir) "dir" else "file")
        if (!isDir) facts.append(";size=").append(f.length())
        facts.append(";modify=").append(stamp).append(";")
        return "$facts ${f.name}"
    }

    private fun listLine(f: File): String {
        val isDir = f.isDirectory
        val perms = if (isDir) "drwxr-xr-x" else "-rw-r--r--"
        // 链接数、属主、组在 Android 上没有意义，给固定值即可，客户端只校验列数
        val size = if (isDir) 4096 else f.length()
        // `MMM dd HH:mm`：FileZilla / WinSCP / 浏览器型客户端都认
        val fmt = SimpleDateFormat("MMM dd HH:mm", Locale.US)
        val stamp = fmt.format(Date(f.lastModified()))
        return String.format(
            Locale.US,
            "%s 1 owner group %d %s %s",
            perms, size, stamp, f.name
        )
    }

    private fun closeQuietly(ss: ServerSocket?) {
        try {
            ss?.close()
        } catch (_: Exception) {
        }
    }

    companion object {
        /** 登录前也放行的命令。 */
        private val OPEN_COMMANDS = setOf("USER", "PASS", "QUIT", "NOOP", "SYST", "FEAT", "OPTS", "HELP")

        /** 单连接空闲超时：客户端挂着不发命令就断，免得僵尸连接堆着。 */
        private const val SOCKET_TIMEOUT_MS = 5 * 60 * 1000

        /** 被动 accept 的等待上限，比上面短 —— 数据通道本该立刻建立。 */
        private const val DATA_ACCEPT_TIMEOUT_MS = 30 * 1000

        private const val CONNECT_TIMEOUT_MS = 10 * 1000

        /** 日志只保留最近这些行。 */
        private const val LOG_LIMIT = 200
    }
}
