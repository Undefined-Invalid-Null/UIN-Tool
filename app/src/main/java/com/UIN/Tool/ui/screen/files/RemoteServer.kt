// app/src/main/java/com/UIN/Tool/ui/screen/files/RemoteServer.kt
package com.UIN.Tool.ui.screen.files

import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 远程管理（FTP / HTTP）的公共地基。
 *
 * ## 为什么自己写而不是引库
 *
 * 1. **没有合适的现成服务端库**：`commons-net` 是 FTP *客户端*，NanoHTTPD 只管 HTTP，
 *    而这台设备上的构建走 AndroidIDE、依赖要现拉 —— 为两个功能各引一个新 artifact，
 *    换来的是一轮不可控的解析失败风险。协议本身（RFC 959 / RFC 9110 的常用子集）
 *    加起来几百行，自己写反而更可控。
 * 2. **生命周期挂在管理器上，不挂在页面上**：起服务是为了让电脑那头连过来，
 *    人多半会切回文件页继续操作。页面一关就掐 socket 的话，功能等于残废。
 *
 * ## 线程模型
 *
 * 两个服务端都是**阻塞式** IO + 每连接一个线程，不走协程：
 * 阻塞读写在协程里要包 `runInterruptible`，还得多一层 dispatcher 管理，
 * 而这里连接数极少（局域网里几台机器），线程池的开销可以忽略。
 */

/** 服务端对外暴露的状态。UI 只读这个，不直接碰 socket。 */
data class RemoteState(
    /** 是否在监听。 */
    val running: Boolean = false,
    /** 实际监听端口（用户填 0 表示随机端口时，这里是系统分的那个）。 */
    val port: Int = 0,
    /** 最近一次启动失败的原因；成功启动后清空。 */
    val error: String? = null
)

/**
 * 两个服务端的公共形状。
 *
 * 用 [StateFlow] 而不是回调：单例在页面出现之前就存在，回调得反复 set/unset，
 * 漏一次就是内存泄漏或幽灵更新；Flow 由页面 `collectAsState` 订阅，页面销毁自动解绑。
 */
interface RemoteServer {
    val state: StateFlow<RemoteState>
    /** 连接日志，最新在末尾；UI 直接渲染。 */
    val log: StateFlow<List<String>>
    fun appendLog(line: String)
}

/**
 * 取本机局域网 IPv4。
 *
 * 不走 `WifiManager`：那要 `ACCESS_WIFI_STATE` 之外还依赖连的是 Wi-Fi（插网线/热点就取不到），
 * 而 `NetworkInterface` 对任何一种网络形态都成立，且不需要额外权限。
 *
 * 排除 loopback 与 link-local（`169.254.x` 是没拿到 DHCP 时的自分配地址，电脑那头连不上），
 * 再按家用路由最常见的网段排序 —— 手机常同时挂着 Wi-Fi 和热点两张网卡，
 * 随便返回第一个会把热点的 `192.168.137.x` 报给用户，那地址电脑未必路由得到。
 */
fun lanIpv4(): String? {
    val interfaces = try {
        NetworkInterface.getNetworkInterfaces()
    } catch (_: SocketException) {
        return null
    } ?: return null

    val found = mutableListOf<String>()
    for (nif in interfaces) {
        try {
            if (!nif.isUp || nif.isLoopback) continue
        } catch (_: SocketException) {
            continue
        }
        for (addr in nif.inetAddresses) {
            if (addr !is Inet4Address) continue
            if (addr.isLoopbackAddress || addr.isLinkLocalAddress) continue
            addr.hostAddress?.let { found += it }
        }
    }
    return found.minByOrNull { ip ->
        when {
            ip.startsWith("192.168.") -> 0
            ip.startsWith("10.") -> 1
            ip.startsWith("172.") -> 2
            else -> 3
        }
    }
}

/**
 * 把客户端给的路径**钉在 [root] 内**，挡住 `../` 逃逸。
 *
 * 这是远程服务端唯一真正的安全边界：FTP 的 `CWD ../../data`、HTTP 的
 * `GET /../../data/data/...` 都是同一类攻击。只做字符串替换不够
 * （`%2e%2e`、符号链接、`a/../../b` 都能绕），所以统一走
 * [File.getCanonicalPath] 解出真实路径后再比前缀。
 *
 * 返回 `null` = 越界，调用方回 550 / 403 即可。
 */
fun resolveWithin(root: File, rawPath: String): File? {
    val rootCanonical = try {
        root.canonicalFile
    } catch (_: Exception) {
        root.absoluteFile
    }
    // FTP 用绝对路径、HTTP 用 URL 路径；HTTP 那边可能带查询串，先剥掉
    val cleaned = rawPath.substringBefore('?').substringBefore('#')
    val target = if (cleaned.startsWith("/")) {
        // HTTP 绝对路径：以 root 为根重新解释（不能拿它当真绝对路径，否则直达 /data）
        File(rootCanonical, cleaned.trimStart('/'))
    } else {
        File(rootCanonical, cleaned)
    }
    val canonical = try {
        target.canonicalFile
    } catch (_: Exception) {
        return null
    }
    val prefix = rootCanonical.path.let { if (it.endsWith("/")) it else "$it/" }
    if (canonical.path != rootCanonical.path && !canonical.path.startsWith(prefix)) return null
    return canonical
}

/**
 * URL 解码（百分号转义 + 可选 `+` 转空格）。
 *
 * 手写而不是直接用 `java.net.URLDecoder` 的差异点：`URLDecoder` **无条件**把 `+` 当空格，
 * 这对 query 成立、对 path 不成立（文件名里合法的 `+` 会被吃掉成空格），
 * 所以路径调用必须传 [plusAsSpace] = false。
 *
 * 另一处坑：百分号转出来的可能是**半个** UTF-8 序列（中文文件名的三字节会被拆成三个 `%XX`），
 * 不能边解边往字符串里塞，必须先攒成字节、最后按 UTF-8 一次解码。
 */
fun urlDecode(s: String, plusAsSpace: Boolean): String {
    if (!s.contains('%') && !(plusAsSpace && s.contains('+'))) return s
    val buf = java.io.ByteArrayOutputStream(s.length)
    var i = 0
    while (i < s.length) {
        val c = s[i]
        when {
            c == '%' && i + 2 < s.length -> {
                val v = s.substring(i + 1, i + 3).toIntOrNull(16)
                if (v != null) {
                    buf.write(v)
                    i += 3
                } else {
                    // 不是合法十六进制：原样当普通字符，别把路径截断
                    buf.write(c.code)
                    i++
                }
            }
            c == '+' && plusAsSpace -> {
                buf.write(' '.code)
                i++
            }
            else -> {
                buf.write(s.substring(i, i + 1).toByteArray(Charsets.UTF_8))
                i++
            }
        }
    }
    return buf.toByteArray().toString(Charsets.UTF_8)
}

/**
 * URL 编码：unreserved 集合（`A-Za-z0-9-_.~`）原样保留，其余按 UTF-8 字节转 `%XX`。
 *
 * 与 [urlDecode] 互为逆运算。用在**把路径塞进 query** 的场合 —— 路径里合法的 `&`、`=`、`?`
 * 不编码的话，服务端 `substringAfter("path=")` 拿到的就是被截断的半条路径，
 * 而这个半条路径会直接拿去删文件。
 */
fun urlEncode(s: String): String {
    if (s.isEmpty()) return s
    val sb = StringBuilder(s.length * 2)
    for (b in s.toByteArray(Charsets.UTF_8)) {
        val c = b.toInt() and 0xFF
        val ch = c.toChar()
        val keep = ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' ||
            ch == '-' || ch == '_' || ch == '.' || ch == '~'
        if (keep) {
            sb.append(ch)
        } else {
            sb.append('%').append("%02X".format(c))
        }
    }
    return sb.toString()
}

/** 把 HTTP 状态码翻成原因短语（只列本服务端会用到的那几个）。 */
fun httpReason(code: Int): String = when (code) {
    200 -> "OK"
    201 -> "Created"
    204 -> "No Content"
    206 -> "Partial Content"
    301 -> "Moved Permanently"
    302 -> "Found"
    400 -> "Bad Request"
    403 -> "Forbidden"
    404 -> "Not Found"
    405 -> "Method Not Allowed"
    409 -> "Conflict"
    411 -> "Length Required"
    413 -> "Payload Too Large"
    414 -> "URI Too Long"
    416 -> "Range Not Satisfiable"
    500 -> "Internal Server Error"
    else -> "OK"
}

/**
 * 单例管理器：两个服务端的唯一持有者。
 *
 * 页面只持有对它的引用，进出页面不改变服务状态 —— 用户从远程管理页切回文件页继续干活，
 * 电脑那头的连接不该断。
 */
object RemoteManager {
    val ftp = FtpServer()
    val http = HttpServer()
}
