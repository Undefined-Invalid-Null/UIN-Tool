// app/src/main/java/com/UIN/Tool/ui/screen/files/RemoteHttpServer.kt
package com.UIN.Tool.ui.screen.files

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TreeMap
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * HTTP 服务端 + 网页文件管理器（RFC 9110 常用子集）。
 *
 * 手机当服务器，电脑开浏览器就能浏览、下载、上传、建目录 —— 不用装任何客户端，
 * 这是它相对 FTP 的核心价值（FTP 那条路留给已经装好客户端的人）。
 *
 * 支持的动词：
 *
 * | 方法 | 用途 |
 * |---|---|
 * | `GET`  | 目录 → HTML 列表；文件 → 下载（带 `Range`，视频能拖进度条）；`action=zip` → 打包下载 |
 * | `HEAD` | 只要头，浏览器探测大文件时用 |
 * | `POST` | 网页表单上传（`multipart/form-data`）、新建文件夹（`action=mkdir`） |
 * | `PUT`  | 裸上传，给 `curl -T` 这类命令行工具用 |
 *
 * **网页**只做「只读 + 上传」这一侧的活：删除、移动、改名这些会丢数据的动作一律不放在
 * 浏览器里（误触一个按钮就没了，而且电脑那头的权限是整个共享存储）。要动文件请回到手机端。
 *
 * 页面自带：面包屑、按名称/大小/时间排序（记住顺序）、实时过滤 + 计数、全选、
 * 打包下载所选 / 单目录打包、新建文件夹、拖拽上传（带进度）、显示隐藏文件开关。
 * 样式是黑白极简，跟随系统深浅色。
 *
 * ## 三个安全点
 *
 * 1. **路径钉死在 root 内**：统一走 [resolveWithin]（canonical path 比前缀），
 *    `GET /../../data/data/...` 与 `%2e%2e` 都过不去。
 * 2. **文件名一律 HTML 转义**：目录列表是服务端拼出来的，文件名里带 `<script>`
 *    就是同源 XSS —— 而这个页面的同源权限里有**整个共享存储**。
 * 3. **Basic 认证**：设了账号密码就强制校验；没设则完全开放（UI 会明说）。
 */
class HttpServer : RemoteServer {

    private val _state = MutableStateFlow(RemoteState())
    override val state: StateFlow<RemoteState> = _state.asStateFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    override val log: StateFlow<List<String>> = _log.asStateFlow()

    @Volatile
    var root: File = FilePrefs.rootFile()

    /** 与 FTP 同一套语义：都空 = 不校验。 */
    @Volatile
    var username: String = ""

    @Volatile
    var password: String = ""

    @Volatile
    private var listener: ServerSocket? = null

    @Volatile
    private var stopped = true

    override fun appendLog(line: String) {
        _log.value = (_log.value + line).takeLast(LOG_LIMIT)
    }

    fun start(port: Int) {
        if (_state.value.running) return
        stopped = false
        try {
            val ss = ServerSocket()
            ss.reuseAddress = true
            ss.bind(InetSocketAddress(port))
            listener = ss
            _state.value = RemoteState(running = true, port = ss.localPort)
            appendLog("HTTP 已启动 · 端口 ${ss.localPort}")
            Thread({ acceptLoop(ss) }, "http-accept").apply {
                isDaemon = true
                start()
            }
        } catch (e: Exception) {
            val reason = e.message ?: e.javaClass.simpleName
            _state.value = RemoteState(running = false, port = 0, error = reason)
            appendLog("HTTP 启动失败：$reason")
        }
    }

    fun stop() {
        stopped = true
        try {
            listener?.close()
        } catch (_: Exception) {
        }
        listener = null
        if (_state.value.running) appendLog("HTTP 已停止")
        _state.value = RemoteState()
    }

    private fun acceptLoop(ss: ServerSocket) {
        while (!stopped) {
            val client = try {
                ss.accept()
            } catch (_: Exception) {
                break
            }
            Thread({ serve(client) }, "http-client").apply {
                isDaemon = true
                start()
            }
        }
    }

    private fun serve(client: Socket) {
        val peer = client.inetAddress?.hostAddress ?: "?"
        try {
            client.use { sock ->
                sock.soTimeout = SOCKET_TIMEOUT_MS
                val input = BufferedInputStream(sock.getInputStream())
                val output = BufferedOutputStream(sock.getOutputStream())

                val requestLine = readLine(input) ?: return
                // 请求行超长一律回 414：readLine 已经把内存限死，这里负责把话说清楚，
                // 而不是让它拼出一条几百 KB 的路径再拿去 resolve。
                if (requestLine.length >= MAX_LINE) {
                    writeSimple(output, 414, "URI Too Long")
                    return
                }
                val parts = requestLine.split(' ')
                if (parts.size < 3) {
                    writeSimple(output, 400, "Bad Request")
                    return
                }
                val method = parts[0].uppercase(Locale.US)
                val rawTarget = parts[1]

                // 头按行读到空行为止。
                // 必须是 TreeMap 而不是 HashMap：HTTP 头字段名大小写不敏感
                // （`Content-Length` / `content-length` 都得命中），而 Java 的 HashMap
                // **没有**接受 Comparator 的构造函数 —— 那是 TreeMap 的。
                // 写成 HashMap(Comparator) 会直接编译不过，且会让后面所有
                // `headers[...]` 的类型跟着塌掉（实测连报 6 条不相干的错）。
                val headers = TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER)
                while (true) {
                    val line = readLine(input) ?: break
                    if (line.isEmpty()) break
                    val cut = line.indexOf(':')
                    if (cut > 0) {
                        headers[line.substring(0, cut).trim()] =
                            line.substring(cut + 1).trim()
                    }
                }

                if (!authorized(headers)) {
                    output.write(
                        ("HTTP/1.1 401 Unauthorized\r\n" +
                            "WWW-Authenticate: Basic realm=\"UIN Tool\"\r\n" +
                            "Content-Length: 0\r\nConnection: close\r\n\r\n")
                            .toByteArray(Charsets.US_ASCII)
                    )
                    output.flush()
                    appendLog("[$peer] 401 $method $rawTarget")
                    return
                }

                // `Expect: 100-continue`：客户端先只发头、等我们点头才发体（大上传常见）。
                // 不回这一句它就一直等着 —— 5 分钟后超时，日志里留一条
                // 「连接异常：Read timed out」，页面上则是上传卡死没反应。
                if (headers["Expect"].equals("100-continue", ignoreCase = true)) {
                    output.write("HTTP/1.1 100 Continue\r\n\r\n".toByteArray(Charsets.US_ASCII))
                    output.flush()
                }

                // 目标里可能带查询串，分开处理
                val pathPart = rawTarget.substringBefore('?')
                val query = rawTarget.substringAfter('?', "")
                val path = urlDecode(pathPart, plusAsSpace = false)
                val target = resolveWithin(root, path)
                val q = parseQuery(query)
                val action = q["action"]?.firstOrNull().orEmpty()

                appendLog("[$peer] $method $path")

                when (method) {
                    "GET", "HEAD" -> {
                        if (target == null) {
                            writeSimple(output, 403, "Forbidden")
                        } else if (action == "zip") {
                            // 打包下载：单条或多选都走这一条。**在 exists 判断之前** ——
                            // 它本来就不依赖 target 存不存在，只看显式给的 path 列表。
                            val wanted = (q["path"] ?: emptyList()).mapNotNull {
                                resolveWithin(root, it)
                            }
                            val sources = if (wanted.isNotEmpty()) wanted else listOf(target)
                            if (sources.isEmpty()) writeSimple(output, 404, "Not Found")
                            else serveZip(output, sources, method == "HEAD")
                        } else if (!target.exists()) {
                            // ── 这一句是「连接异常」的真正来源 ──
                            // 路径不存在时原来直接 `target.inputStream()`，抛出的
                            // FileNotFoundException 一路跑到外层 catch：**一个字节都不回**，
                            // 浏览器看到的是连接被断，日志里留一句谁也看不懂的异常。
                            // 浏览器几乎每次都会要 `/favicon.ico`，所以这行日志天天有。
                            writeSimple(output, 404, "Not Found")
                        } else if (target.isDirectory) {
                            if (pathPart.endsWith("/") || pathPart.isEmpty()) {
                                val html = renderDirectory(target, pathPart, q)
                                if (html == null) {
                                    // 目录在、但读不出内容（权限被收走 / 已被卸载）：
                                    // 说 403 比画一个「空目录」诚实
                                    writeSimple(output, 403, "Forbidden")
                                } else {
                                    writeBytes(
                                        output, 200, "text/html; charset=utf-8",
                                        html.toByteArray(Charsets.UTF_8), method == "HEAD"
                                    )
                                }
                            } else {
                                // 不带尾斜杠时相对链接会解析错，先跳一次
                                writeRedirect(output, "$pathPart/")
                            }
                        } else {
                            serveFile(output, target, headers, method == "HEAD")
                        }
                    }

                    "PUT" -> {
                        val len = headers["Content-Length"]?.toLongOrNull()
                        when {
                            // 分块传输没有 Content-Length，而我们是按长度读体的 ——
                            // 回 411 说清楚，别当「0 字节」处理完再把连接丢掉
                            isChunked(headers) -> writeSimple(output, 411, "Length Required")
                            target == null -> writeSimple(output, 403, "Forbidden")
                            len == null -> writeSimple(output, 400, "Content-Length required")
                            len < 0 -> writeSimple(output, 400, "Bad Content-Length")
                            len > MAX_UPLOAD_BYTES -> writeSimple(output, 413, "Payload Too Large")
                            else -> {
                                // 流式落盘：PUT 是给 curl 传大文件用的，整块进内存必 OOM
                                target.parentFile?.mkdirs()
                                target.outputStream().use { copyN(input, it, len) }
                                writeSimple(output, 201, "Created")
                            }
                        }
                    }

                    "POST" -> {
                        if (isChunked(headers)) {
                            writeSimple(output, 411, "Length Required")
                            return
                        }
                        val len = headers["Content-Length"]?.toLongOrNull() ?: 0L
                        if (len < 0) {
                            writeSimple(output, 400, "Bad Content-Length")
                            return
                        }
                        // multipart 要整块进内存才能切分隔符，所以这里给的是**小得多**的上限；
                        // 超了回 413 并让用户改走 PUT（流式）或 FTP。
                        if (len > MAX_FORM_BYTES) {
                            writeSimple(output, 413, "Too large, use PUT or FTP instead")
                            return
                        }
                        val body = if (len > 0) readN(input, len) else ByteArray(0)
                        val ct = headers["Content-Type"] ?: ""
                        // 表单里藏的 action（新建文件夹用的普通 urlencoded 表单）
                        val form = if (ct.startsWith("application/x-www-form-urlencoded")) {
                            parsePairs(String(body, Charsets.UTF_8))
                        } else {
                            emptyMap()
                        }
                        val act = action.ifEmpty { form["action"]?.firstOrNull().orEmpty() }

                        when {
                            act == "mkdir" -> {
                                val dir = resolveWithin(root, pathPart) ?: root
                                val name = (form["name"]?.firstOrNull() ?: "").trim()
                                val dest = if (dir.isDirectory) File(dir, name) else null
                                // 名字里出现分隔符或 `..` 就会逃到别处去，`resolveWithin`
                                // 管不到这一层（它只看整条路径），所以名字本身得自己验
                                val legal = name.isNotEmpty() &&
                                    name != "." && name != ".." &&
                                    !name.contains('/') && !name.contains('\\')
                                // 分支顺序即智能转换：先判空，后面几支才能直接用 dest
                                when {
                                    dest == null || !legal -> writeSimple(output, 400, "Invalid name")
                                    dest.exists() -> writeSimple(output, 409, "Already exists")
                                    dest.mkdir() -> {
                                        appendLog("[$peer] 新建文件夹 $name")
                                        writeRedirect(output, pathPart)
                                    }

                                    else -> writeSimple(output, 409, "Cannot create folder")
                                }
                            }

                            ct.startsWith("multipart/form-data") -> {
                                val boundary = ct.substringAfter("boundary=", "")
                                    .trim().trim('"')
                                val targetDir = resolveWithin(root, pathPart) ?: root
                                val saved = saveMultipart(body, boundary, targetDir)
                                appendLog("[$peer] 上传 ${saved.size} 个文件")
                                writeRedirect(output, pathPart)
                            }

                            else -> writeSimple(output, 400, "Unsupported POST")
                        }
                    }

                    else -> writeSimple(output, 405, "Method Not Allowed")
                }
            }
        } catch (e: Exception) {
            // 客户端**主动断开**是常态（点停止、切页面、下载完就关、打包下到一半关掉），
            // 不是故障。原来一律记成「连接异常」，日志里全是噪音、真问题反而看不见。
            when (e) {
                is java.net.SocketTimeoutException ->
                    appendLog("[$peer] 空闲超时，已关闭连接")

                is java.net.SocketException ->
                    appendLog("[$peer] 断开：${e.message}")

                is java.io.EOFException ->
                    appendLog("[$peer] 断开：对端关闭")

                else ->
                    // 真异常必须带上**异常类型**：只有 message 的话，
                    // `Permission denied` 和 `Read timed out` 长得一模一样，查不出差别
                    appendLog("[$peer] 异常 ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    // ==================== 认证 ====================

    private fun authorized(headers: Map<String, String>): Boolean {
        if (username.isEmpty() && password.isEmpty()) return true
        val raw = headers["Authorization"] ?: return false
        if (!raw.startsWith("Basic ", ignoreCase = true)) return false
        return try {
            val decoded = String(
                android.util.Base64.decode(raw.substring(6).trim(), android.util.Base64.DEFAULT),
                Charsets.UTF_8
            )
            val cut = decoded.indexOf(':')
            if (cut < 0) false
            else decoded.substring(0, cut) == username && decoded.substring(cut + 1) == password
        } catch (_: Exception) {
            false
        }
    }

    // ==================== 文件下发 ====================

    /**
     * 带 `Range` 的文件下发。
     *
     * 支持单段 `bytes=a-b` 就够了 —— 浏览器看视频只发单段；多段（`multipart/byteranges`）
     * 没有客户端在局域网场景下会用到，回整文件反而兼容性最好。
     */
    private fun serveFile(
        out: OutputStream,
        file: File,
        headers: Map<String, String>,
        headOnly: Boolean
    ) {
        val total = file.length()
        val mime = guessMime(file.name)
        val range = headers["Range"]

        val start: Long
        var end = total - 1
        var code = 200
        if (range != null && range.startsWith("bytes=")) {
            val spec = range.removePrefix("bytes=").substringBefore(',').trim()
            val dash = spec.indexOf('-')
            val s = if (dash > 0) spec.substring(0, dash).toLongOrNull() else null
            val e = if (dash >= 0 && dash < spec.length - 1) {
                spec.substring(dash + 1).toLongOrNull()
            } else null
            when {
                s == null && e != null -> {
                    // suffix 形式 `bytes=-500`：最后 500 字节
                    start = (total - e).coerceAtLeast(0)
                }
                s != null -> {
                    start = s
                    if (e != null) end = e.coerceAtMost(total - 1)
                }
                else -> {
                    start = 0
                }
            }
            if (start >= total || start > end) {
                out.write(
                    ("HTTP/1.1 416 Range Not Satisfiable\r\n" +
                        "Content-Range: bytes */$total\r\nContent-Length: 0\r\n" +
                        "Connection: close\r\n\r\n").toByteArray(Charsets.US_ASCII)
                )
                out.flush()
                return
            }
            code = 206
        } else {
            start = 0
        }

        val len = end - start + 1
        val head = StringBuilder()
        head.append("HTTP/1.1 $code ${httpReason(code)}\r\n")
        head.append("Content-Type: $mime\r\n")
        head.append("Content-Length: $len\r\n")
        head.append("Accept-Ranges: bytes\r\n")
        if (code == 206) head.append("Content-Range: bytes $start-$end/$total\r\n")
        head.append("Connection: close\r\n\r\n")
        out.write(head.toString().toByteArray(Charsets.US_ASCII))
        out.flush()

        if (headOnly) return

        file.inputStream().use { src ->
            // 先跳过 start：Range 从任意偏移开始，顺序读到那儿最省事
            var skipped = 0L
            while (skipped < start) {
                val n = src.skip(start - skipped)
                if (n <= 0) break
                skipped += n
            }
            val buf = ByteArray(DOWNLOAD_BUF)
            var remaining = len
            while (remaining > 0) {
                val n = src.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                if (n <= 0) break
                out.write(buf, 0, n)
                remaining -= n
            }
        }
        out.flush()
    }

    // ==================== 网页 ====================

    /**
     * 目录列表页。
     *
     * 全部内联样式、零外部引用 —— 手机在局域网里当服务器，页面再引 CDN 就会
     * 在无外网的环境里白屏。
     *
     * 用户反馈⑲的四件事：
     * 1. **功能多一些**：面包屑、按名称/大小/时间排序（跨层保持）、即时过滤框、
     *    全选、**勾选后打包下载成 zip**（含目录行的「打包」）、新建文件夹、
     *    拖拽上传带进度、隐藏文件开关；
     * 2. **不给删除**：连同服务端那条 `action=delete` 分支一起摘掉 ——
     *    页面上摆一个「递归删」按钮，误点一下就是不可逆的；
     * 3. **黑白极简**：全站只有黑与白（暗色下反过来），零彩色、零圆角，
     *    分隔线用半透明的黑/白而不是灰色色块；
     * 4. 顺手修掉日志里那条天天出现的「连接异常」（见 [HttpServer.serve] 的 404 分支）。
     *
     * 行内 `onclick` 只是交互，真正的门禁在服务端 —— JS 关掉照样删不掉东西。
     *
     * @param q 解析好的查询串：`sort` / `desc` / `hide` 在这里生效，
     *           `action=zip` 由 [HttpServer.serve] 在更早的地方截走了。
     * @return `null` = 目录读不出（权限被收走），调用方回 403。
     */
    private fun renderDirectory(dir: File, rawPath: String, q: Map<String, List<String>>): String? {
        val listing = dir.listFiles() ?: return null
        val path = if (rawPath.isEmpty()) "/" else rawPath

        val sort = q["sort"]?.firstOrNull()?.takeIf { it == "size" || it == "time" } ?: "name"
        val desc = q["desc"]?.firstOrNull() == "1"
        val hide = q["hide"]?.firstOrNull() == "1"

        // 链接要**带着当前排序走**：否则按大小逛一圈，每进一层又变回名称序
        val keep = ArrayList<String>(3)
        if (sort != "name") keep.add("sort=$sort")
        if (desc) keep.add("desc=1")
        if (hide) keep.add("hide=1")
        val suffix = if (keep.isEmpty()) "" else "?" + keep.joinToString("&")
        // HTML 属性里的 `&` 必须写成 `&amp;`，否则解析器把它当下一个实体的开头
        val sfx = suffix.replace("&", "&amp;")

        val keyCmp: Comparator<File> = when (sort) {
            "size" -> if (desc) compareByDescending<File> { it.length() }
            else compareBy<File> { it.length() }

            "time" -> if (desc) compareByDescending<File> { it.lastModified() }
            else compareBy<File> { it.lastModified() }

            else -> if (desc) compareByDescending<File> { it.name.lowercase(Locale.US) }
            else compareBy<File> { it.name.lowercase(Locale.US) }
        }
        // 目录永远在前 —— 「目录优先」和升降序是两件事，跟着一起倒会让人找不到文件夹
        val entries = listing
            .filter { !(hide && it.name.startsWith(".")) }
            .sortedWith(Comparator { a, b ->
                val d = (if (a.isDirectory) 0 else 1) - (if (b.isDirectory) 0 else 1)
                if (d != 0) d else keyCmp.compare(a, b)
            })

        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val rows = StringBuilder()
        if (path != "/") {
            rows.append(
                row(
                    rawPath = parentOf(path), name = "…", size = "", time = "",
                    isDir = true, isParent = true, sfx = sfx
                )
            )
        }
        for (f in entries) {
            val child = path + (if (path.endsWith("/")) "" else "/") + f.name
            rows.append(
                row(
                    rawPath = child,
                    name = htmlEscape(f.name),
                    size = if (f.isDirectory) "" else humanSize(f.length()),
                    time = fmt.format(Date(f.lastModified())),
                    isDir = f.isDirectory,
                    isParent = false,
                    sfx = sfx
                )
            )
        }
        if (entries.isEmpty()) {
            val msg = if (listing.isEmpty()) "（空目录）" else "（无可见项）"
            rows.append("<tr><td colspan=\"3\" class=\"empty\">$msg</td></tr>")
        }

        // ── 面包屑 ── 最后一段是「现在」，不做成链接（点了也是原地刷新）
        val crumbs = StringBuilder("<a href=\"/${sfx}\">/</a>")
        val segs = path.trim('/').split('/').filter { it.isNotEmpty() }
        var acc = ""
        for ((i, s) in segs.withIndex()) {
            acc += "$s/"
            crumbs.append("<span>/</span>")
            if (i == segs.lastIndex) crumbs.append(" <b>${htmlEscape(s)}</b>")
            else crumbs.append(" <a href=\"${htmlEscape("/$acc")}$sfx\">${htmlEscape(s)}</a>")
        }

        // ── 排序导航：点当前那项翻转升降序，点别项按新键升序 ──
        val nav = StringBuilder()
        for ((key, label) in listOf("name" to "名称", "size" to "大小", "time" to "时间")) {
            val active = key == sort
            val parts = ArrayList<String>(3)
            parts.add("sort=$key")
            if (active && !desc) parts.add("desc=1")
            if (hide) parts.add("hide=1")
            val arrow = if (active) if (desc) " ↓" else " ↑" else ""
            nav.append(
                "<a class=\"${if (active) "on" else ""}\" " +
                    "href=\"?${parts.joinToString("&amp;")}\">$label$arrow</a>"
            )
        }

        val hideParts = ArrayList<String>(3)
        if (sort != "name") hideParts.add("sort=$sort")
        if (desc) hideParts.add("desc=1")
        if (!hide) hideParts.add("hide=1")
        val hideHref = if (hideParts.isEmpty()) "?" else "?" + hideParts.joinToString("&amp;")
        val hideLabel = if (hide) "显示隐藏项" else "隐藏 . 开头"

        val uploadTo = htmlEscape(path + suffix)
        val plainPath = htmlEscape(path)
        return """<!DOCTYPE html>
<html lang="zh"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>${htmlEscape(path)}</title>
<style>
:root{color-scheme:light dark;--bg:#fff;--fg:#000;--ln:#000;--mt:#6b6b6b;--hair:rgba(0,0,0,.16);--iv:#000;--ivf:#fff}
@media(prefers-color-scheme:dark){
 :root{--bg:#000;--fg:#fff;--ln:#fff;--mt:#9b9b9b;--hair:rgba(255,255,255,.22);--iv:#fff;--ivf:#000}
}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--fg);font:14px/1.5 system-ui,-apple-system,"Noto Sans SC",sans-serif}
header{position:sticky;top:0;z-index:2;background:var(--bg);border-bottom:1px solid var(--ln);padding:12px 14px}
.crumbs{font-size:13px;word-break:break-all;margin-bottom:10px}
.crumbs a{color:var(--fg);text-decoration:none}
.crumbs a:hover{text-decoration:underline}
.crumbs b{font-weight:600}
.crumbs span{color:var(--mt);margin:0 3px}
.bar{display:flex;flex-wrap:wrap;gap:8px;align-items:center;margin-top:8px}
form{display:flex;gap:8px;align-items:center}
input[type=text]{font:inherit;color:var(--fg);background:var(--bg);border:1px solid var(--ln);padding:7px 9px;min-width:0}
input[type=file]{font:inherit;color:var(--fg);border:1px dashed var(--ln);padding:6px 8px;max-width:230px}
button,a.bt{font:inherit;color:var(--ivf);background:var(--iv);border:1px solid var(--ln);padding:7px 12px;cursor:pointer;text-decoration:none}
button.gh,a.gh{color:var(--fg);background:var(--bg)}
nav a{color:var(--fg);text-decoration:none;border:1px solid transparent;padding:5px 7px}
nav a.on{border-color:var(--ln);font-weight:600}
nav a:hover{text-decoration:underline}
table{width:100%;border-collapse:collapse;border-top:1px solid var(--ln)}
th{font-size:12px;font-weight:600;text-align:left;color:var(--mt);padding:8px 12px;border-bottom:1px solid var(--ln)}
td{padding:9px 12px;border-bottom:1px solid var(--hair);vertical-align:middle}
td.n{word-break:break-all}
a{color:var(--fg);text-decoration:none}
a:hover{text-decoration:underline}
.meta{color:var(--mt);font-size:12px;white-space:nowrap}
.meta a{border-bottom:1px solid var(--ln)}
.empty{color:var(--mt);text-align:center;padding:40px}
input.c{width:16px;height:16px;accent-color:currentColor;margin-right:8px;vertical-align:-3px}
#ct,#pr{font-size:12px;color:var(--mt)}
#pr{display:none}
#ov{display:none;position:fixed;inset:0;z-index:9;background:rgba(0,0,0,.8);color:#fff;align-items:center;justify-content:center;text-align:center;padding:20px}
#ov.on{display:flex}
@media(max-width:560px){input[type=file]{max-width:150px}td,th{padding:8px 6px}}
</style></head><body>
<header>
<div class="crumbs">$crumbs</div>
<div class="bar">
<form id="up" method="post" enctype="multipart/form-data" action="$uploadTo">
<input type="file" name="file" multiple required>
<button type="submit">上传</button>
</form>
<form method="post" action="$plainPath">
<input type="hidden" name="action" value="mkdir">
<input type="text" name="name" placeholder="新建文件夹" required maxlength="255">
<button type="submit">新建</button>
</form>
</div>
<div class="bar">
<input type="text" id="q" placeholder="过滤当前目录…" oninput="f()">
<span id="ct">${entries.size} 项</span>
<button class="gh" type="button" onclick="sa()">全选</button>
<button type="button" onclick="zs()">打包下载所选</button>
<nav>$nav</nav>
<a class="bt gh" href="$hideHref">$hideLabel</a>
<span id="pr"></span>
</div>
</header>
<table><tr><th>名称</th><th class="meta">大小</th><th class="meta">修改时间</th></tr>
$rows</table>
<div id="ov">松手上传到当前目录</div>
<script>
function el(i){return document.getElementById(i)}
function f(){var v=el('q').value.toLowerCase(),rs=document.querySelectorAll('tr.r'),n=0;
for(var i=0;i<rs.length;i++){var m=!v||rs[i].getAttribute('data-n').indexOf(v)>=0;
rs[i].style.display=m?'':'none';if(m)n++}el('ct').textContent=n+' 项'}
function off(){var c=document.querySelectorAll('input.c');
for(var i=0;i<c.length;i++)if(!c[i].checked)return true;return false}
function sa(){var c=document.querySelectorAll('input.c'),t=off();
for(var i=0;i<c.length;i++)c[i].checked=t}
function zs(){var c=document.querySelectorAll('input.c:checked'),u='?action=zip';
for(var i=0;i<c.length;i++)u+='&path='+encodeURIComponent(c[i].getAttribute('data-p'));
if(c.length===0){alert('先勾选要打包的内容');return}location.href=u}
function up(fs){if(!fs.length)return;var d=new FormData();
for(var i=0;i<fs.length;i++)d.append('file',fs[i]);
var x=new XMLHttpRequest();x.open('POST',document.getElementById('up').action);
var pr=el('pr');pr.style.display='block';pr.textContent='上传中…';
x.upload.onprogress=function(e){if(e.lengthComputable)pr.textContent='上传中 '+Math.round(e.loaded/e.total*100)+'%'};
x.onload=function(){location.reload()};
x.onerror=function(){pr.textContent='上传失败'};
x.send(d)}
document.addEventListener('dragover',function(e){e.preventDefault()});
document.addEventListener('dragenter',function(e){e.preventDefault();el('ov').classList.add('on')});
document.addEventListener('dragleave',function(e){if(e.clientX<=0||e.clientY<=0)el('ov').classList.remove('on')});
document.addEventListener('drop',function(e){e.preventDefault();el('ov').classList.remove('on');
up(e.dataTransfer.files)});
</script></body></html>"""
    }

    /**
     * 表格一行。
     *
     * [rawPath] 是**未转义**的绝对路径，[name] 已经过 HTML 转义，两种转义在这里各做各的：
     *
     * - 链接 `href` 只做 HTML 转义 —— 空格、中文交给浏览器自己处理；
     * - 勾选框的 `data-p` 存的是**原样路径**，由 JS 用 `encodeURIComponent` 现编 ——
     *   服务端 `parseQuery` 会把值解回来。先编后转义、转义后又解一次的顺序只要搞错，
     *   中文路径就断成两截。
     *
     * **没有删除按钮**（用户反馈⑲）：网页上摆一个「递归删」，误点一下不可逆，
     * 而删除在手机端本来就有专门的确认流程。
     *
     * [isParent] 行不给勾选框 —— 「…」不是文件，勾了也无处可打。
     */
    private fun row(
        rawPath: String,
        name: String,
        size: String,
        time: String,
        isDir: Boolean,
        isParent: Boolean,
        sfx: String
    ): String {
        val href = htmlEscape(rawPath) + sfx
        val shown = if (isDir && !isParent) "$name/" else name
        val ck = if (isParent) "" else
            "<input type=\"checkbox\" class=\"c\" data-p=\"${htmlEscape(rawPath)}\">"
        // 目录给一个「打包」：整夹下载不必先把内容摊到屏幕上
        val zip = if (isDir && !isParent) {
            " <a href=\"?action=zip&amp;path=${htmlEscape(urlEncode(rawPath))}\">打包</a>"
        } else ""
        val nl = name.lowercase(Locale.US)
        return "<tr class=\"r\" data-n=\"$nl\"><td class=\"n\">$ck" +
            "<a href=\"$href\">$shown</a></td>" +
            "<td class=\"meta\">$size</td>" +
            "<td class=\"meta\">$time$zip</td></tr>"
    }

    // ==================== multipart 上传 ====================

    /**
     * 解 `multipart/form-data` 并落盘。
     *
     * 手写解析而不是引库：只服务自己这个页面的表单（`name="file"` + 可多个），
     * 不需要兼容各种边缘格式；引一个 multipart 库又要多一个离线依赖。
     *
     * 分隔符按 `--boundary` 切，**不按 `\r\n` 全切** —— 文件内容里出现分隔符的概率极低，
     * 而按 CRLF 切会把二进制文件切碎。
     */
    private fun saveMultipart(body: ByteArray, boundary: String, dir: File): List<String> {
        if (boundary.isEmpty()) return emptyList()
        val marker = ("--$boundary").toByteArray(Charsets.US_ASCII)
        val saved = mutableListOf<String>()
        dir.mkdirs()

        var idx = indexOf(body, marker, 0)
        while (idx >= 0) {
            var start = idx + marker.size
            // 结束标记 `--boundary--`
            if (start + 1 < body.size && body[start] == '-'.code.toByte() &&
                body[start + 1] == '-'.code.toByte()
            ) break
            // 跳过 boundary 后的 CRLF
            if (start + 1 < body.size && body[start] == '\r'.code.toByte() &&
                body[start + 1] == '\n'.code.toByte()
            ) start += 2

            val next = indexOf(body, marker, start)
            var end = if (next >= 0) next else body.size
            // 本段尾部的 CRLF 属于分隔符，不属于内容
            if (end >= 2 && body[end - 2] == '\r'.code.toByte() && body[end - 1] == '\n'.code.toByte()) {
                end -= 2
            }

            if (end > start) {
                val headerEnd = indexOf(body, "\r\n\r\n".toByteArray(Charsets.US_ASCII), start)
                if (headerEnd in start until end) {
                    val headerText = String(body, start, headerEnd - start, Charsets.UTF_8)
                    val dataStart = headerEnd + 4
                    val filename = headerText
                        .lineSequence()
                        .firstOrNull { it.startsWith("Content-Disposition", ignoreCase = true) }
                        ?.substringAfter("filename=\"", "")
                        ?.substringBefore("\"", "")
                    if (!filename.isNullOrBlank()) {
                        // 只取最后一段，挡掉 `C:\fakepath\` 与 `../../` 这类客户端前缀
                        val safe = File(filename).name
                        if (safe.isNotEmpty() && safe != "." && safe != "..") {
                            // 注意：这里**不能用 continue** —— `idx` 在循环末尾才推进，
                            // 提前 continue 会让同一个 marker 反复命中，直接死循环。
                            val dest = resolveWithin(dir, safe)
                            if (dest != null) {
                                dest.outputStream().use { it.write(body, dataStart, end - dataStart) }
                                saved += safe
                            }
                        }
                    }
                }
            }
            idx = if (next >= 0) next else -1
        }
        return saved
    }

    // ==================== 查询串 / 打包下载 ====================

    /**
     * 解 `a=1&b=2&b=3` —— GET 查询串与 `urlencoded` 表单体是同一套语法，
     * 所以两者共用 [parsePairs]（新建文件夹那张表单就靠它把 `action` 认出来）。
     *
     * 值**要解码**：勾选框的路径是 JS 用 `encodeURIComponent` 现编的，
     * 不解回来 `resolveWithin` 只会拿到一串 `%E4%B8%AD`。
     */
    private fun parseQuery(q: String): Map<String, List<String>> =
        if (q.isEmpty()) emptyMap() else parsePairs(q)

    private fun parsePairs(s: String): Map<String, List<String>> {
        if (s.isEmpty()) return emptyMap()
        val out = LinkedHashMap<String, MutableList<String>>()
        for (kv in s.split('&')) {
            if (kv.isEmpty()) continue
            val k = urlDecode(kv.substringBefore('='), plusAsSpace = true)
            val v = urlDecode(kv.substringAfter('=', ""), plusAsSpace = true)
            out.getOrPut(k) { mutableListOf() }.add(v)
        }
        return out
    }

    /** 分块传输**没有** Content-Length，我们整块读体，读不了就得直说（411）。 */
    private fun isChunked(headers: Map<String, String>): Boolean =
        headers["Transfer-Encoding"].equals("chunked", ignoreCase = true)

    /**
     * 把若干路径打成 zip 流给浏览器。
     *
     * **不给 Content-Length**：条目没遍历完之前算不出体积，而 zip 是 DEFLATE，
     * 连「未压缩大小加表头」这种近似都算不准。按 RFC 9112 §6.3，
     * 既无 CL 也无 chunked、由连接关闭收尾的响应是**合法**的 —— 也是最简单服务器
     * 发 zip 的通行做法，浏览器照下不误（只是进度条没有总长）。
     *
     * [headOnly] 走 HEAD：只回头不回体。
     */
    private fun serveZip(out: OutputStream, targets: List<File>, headOnly: Boolean) {
        var name = if (targets.size == 1) targets[0].name else "files.zip"
        name = name.trim().trimEnd('/').ifEmpty { "files.zip" }
        // 老客户端认 ASCII 的 filename，认得新的认 filename* —— 两份都给，
        // 中文目录名才既不会变成下划线、也不会在旧浏览器上显示成乱码
        val ascii = name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifEmpty { "files.zip" }

        val head = StringBuilder()
        head.append("HTTP/1.1 200 ${httpReason(200)}\r\n")
        head.append("Content-Type: application/zip\r\n")
        head.append(
            "Content-Disposition: attachment; filename=\"$ascii\"; " +
                "filename*=UTF-8''${urlEncode(name)}\r\n"
        )
        head.append("Connection: close\r\n\r\n")
        out.write(head.toString().toByteArray(Charsets.US_ASCII))
        out.flush()
        if (headOnly) return

        ZipOutputStream(out).use { z ->
            val used = HashSet<String>()
            // 递归本地函数：状态（[used]、深度）都是这一次请求的，
            // 不该提升成成员变量 —— 服务端是并发的，共享一个计数器必出事
            fun put(f: File, entry: String, depth: Int) {
                if (depth > MAX_ZIP_DEPTH) return
                if (f.isDirectory) {
                    // 空目录也要给一条 entry，否则解出来这一层整个消失
                    z.putNextEntry(ZipEntry("$entry/"))
                    z.closeEntry()
                    val kids = f.listFiles() ?: return
                    for (k in kids.sortedWith(
                        compareBy({ !it.isDirectory }, { it.name.lowercase(Locale.US) })
                    )) {
                        put(k, "$entry/${k.name}", depth + 1)
                    }
                } else {
                    // 先量再开：已经 putNextEntry 之后才发现读不动，
                    // 只能留下一条截断的文件 —— 那比整个包缺一个更难查
                    if (!f.exists() || !f.canRead()) return
                    z.putNextEntry(ZipEntry(entry))
                    f.inputStream().use { it.copyTo(z, COPY_BUF) }
                    z.closeEntry()
                }
            }
            for (t in targets) {
                val top = t.name.trim().trimEnd('/').ifEmpty { "files.zip" }
                // 从不同目录各挑一个同名文件时，第二条要改名 ——
                // ZipEntry 重名在解压端要么覆盖、要么报错
                var unique = top
                var i = 2
                while (!used.add(unique)) {
                    unique = "${i}_$top"
                    i++
                }
                put(t, unique, 0)
            }
        }
    }

    // ==================== 工具 ====================

    /**
     * 读一行（以 `\r\n` 或 `\n` 结尾），到 EOF 返回 null。
     *
     * **超长即截断**（见 [MAX_LINE]）：这一行是对端唯一能直接喂给内存的东西，
     * 不封顶就等于把堆交给对面支配。截断后仍要把这一行读完才返回，
     * 否则剩下的字节会被当成下一行的开头，头表整个错位。
     */
    private fun readLine(input: BufferedInputStream): String? {
        val buf = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return if (buf.length == 0) null else buf.toString()
            if (b == '\n'.code) break
            if (b != '\r'.code && buf.length < MAX_LINE) buf.append(b.toChar())
        }
        return buf.toString()
    }

    /**
     * 读满 [n] 字节后返回（不足则返回实际读到的部分）。
     *
     * **不再静默截断**：原先这里写的是 `coerceAtMost(8MB)`，100MB 的表单只会读前 8MB
     * 就当成成功 —— 上传出来的文件被截断，页面却显示成功，是最难发现的那种坏。
     * 现在改成调用方先用 [MAX_FORM_BYTES] 挡，这里按调用方给的长度老老实实读满。
     */
    private fun readN(input: BufferedInputStream, n: Long): ByteArray {
        val out = ByteArray(n.toInt())
        var off = 0
        while (off < out.size) {
            val r = input.read(out, off, out.size - off)
            if (r < 0) break
            off += r
        }
        return if (off == out.size) out else out.copyOf(off)
    }

    /** 从输入流搬 [n] 字节到文件，**边读边写**（大文件不能整块进内存）。 */
    private fun copyN(input: BufferedInputStream, out: OutputStream, n: Long) {
        val buf = ByteArray(DOWNLOAD_BUF)
        var remaining = n
        while (remaining > 0) {
            val r = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
            if (r < 0) break
            out.write(buf, 0, r)
            remaining -= r
        }
    }

    private fun writeSimple(out: OutputStream, code: Int, message: String) {
        writeBytes(out, code, "text/plain; charset=utf-8", message.toByteArray(Charsets.UTF_8), false)
    }

    private fun writeRedirect(out: OutputStream, to: String) {
        out.write(
            ("HTTP/1.1 301 ${httpReason(301)}\r\nLocation: $to\r\n" +
                "Content-Length: 0\r\nConnection: close\r\n\r\n")
                .toByteArray(Charsets.US_ASCII)
        )
        out.flush()
    }

    private fun writeBytes(
        out: OutputStream,
        code: Int,
        contentType: String,
        body: ByteArray,
        headOnly: Boolean
    ) {
        val head = StringBuilder()
        head.append("HTTP/1.1 $code ${httpReason(code)}\r\n")
        head.append("Content-Type: $contentType\r\n")
        head.append("Content-Length: ${body.size}\r\n")
        head.append("Connection: close\r\n\r\n")
        out.write(head.toString().toByteArray(Charsets.US_ASCII))
        if (!headOnly) out.write(body)
        out.flush()
    }

    private fun parentOf(path: String): String {
        val trimmed = path.removeSuffix("/")
        val cut = trimmed.lastIndexOf('/')
        return if (cut <= 0) "/" else trimmed.substring(0, cut + 1)
    }

    /** 字节串搜索（`String.indexOf` 的 ByteArray 版）。 */
    private fun indexOf(hay: ByteArray, needle: ByteArray, from: Int): Int {
        if (needle.isEmpty() || from < 0) return -1
        outer@ for (i in from..hay.size - needle.size) {
            for (j in needle.indices) if (hay[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }

    private fun humanSize(bytes: Long): String = when {
        bytes >= 1 shl 30 -> String.format(Locale.US, "%.1f GB", bytes / (1 shl 30).toDouble())
        bytes >= 1 shl 20 -> String.format(Locale.US, "%.1f MB", bytes / (1 shl 20).toDouble())
        bytes >= 1 shl 10 -> String.format(Locale.US, "%.1f KB", bytes / (1 shl 10).toDouble())
        else -> "$bytes B"
    }

    companion object {
        private const val SOCKET_TIMEOUT_MS = 5 * 60 * 1000
        private const val LOG_LIMIT = 200
        private const val DOWNLOAD_BUF = 32 * 1024

        /** 打包/上传的搬运缓冲。 */
        private const val COPY_BUF = 64 * 1024

        /**
         * 请求行与单条头的长度上限。
         *
         * [readLine] 是逐字节往 StringBuilder 里塞的，不设上限就等于把内存
         * 交给对端支配，所以它读到 [MAX_LINE] 就不再往里塞（但会把这一行读完）。
         * 请求行超长由调用方回 **414**；头超长只是没匹配上（回 400），
         * 两种都比 OOM 强。
         */
        private const val MAX_LINE = 16 * 1024

        /**
         * 打包时的目录深度上限。
         *
         * `File.listFiles` 是**跟符号链接**的，遇到 `a -> ..` 这种回环
         * 会一路走到底；深度封顶把它变成有限的一串超长路径，而不是死循环。
         */
        private const val MAX_ZIP_DEPTH = 32

        /**
         * 网页表单（multipart）体的上限。
         *
         * multipart 要整块进内存才能切分隔符，所以这个值远小于 [MAX_UPLOAD_BYTES]。
         * 64MB 是低端机上一次能安全分配的量级；再大的文件改走 FTP —— 那边纯流式，
         * 不吃内存，上限只有磁盘。
         */
        private const val MAX_FORM_BYTES = 64L * 1024 * 1024

        /** `PUT` 单次上传上限 2GB —— 走的是流式落盘，卡的是传输时长不是内存。 */
        private const val MAX_UPLOAD_BYTES = 2L * 1024 * 1024 * 1024

        /** 文件名 → MIME。查不到就给个安全的二进制类型，别让浏览器当文本渲染。 */
        fun guessMime(name: String): String {
            val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
            return when (ext) {
                "html", "htm" -> "text/html; charset=utf-8"
                "txt", "log", "md", "ini", "cfg", "conf", "xml", "json", "csv" ->
                    "text/plain; charset=utf-8"
                "css" -> "text/css"
                "js" -> "application/javascript"
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                "svg" -> "image/svg+xml"
                "mp4", "m4v" -> "video/mp4"
                "mkv" -> "video/x-matroska"
                "webm" -> "video/webm"
                "mp3" -> "audio/mpeg"
                "flac" -> "audio/flac"
                "wav" -> "audio/wav"
                "ogg" -> "audio/ogg"
                "pdf" -> "application/pdf"
                "zip" -> "application/zip"
                "7z" -> "application/x-7z-compressed"
                "gz", "tgz" -> "application/gzip"
                "apk" -> "application/vnd.android.package-archive"
                else -> "application/octet-stream"
            }
        }

        /**
         * HTML 转义。
         *
         * 目录列表是服务端拿**文件名**拼出来的，文件名里带 `<script>` 就能在
         * 这个页面上执行 —— 而该页面的同源权限覆盖整个共享存储。`&` 必须先转，
         * 否则 `&lt;` 会被二次转义成 `&amp;lt;`。
         */
        fun htmlEscape(s: String): String = s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
