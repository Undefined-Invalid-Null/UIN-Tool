// app 之外的纯 JVM 验证用例 —— **不要**放进 app/src，否则会被打进 APK。
//
// CodeFormatters.kt 不依赖任何 Android API，所以可以脱离 Android 工程直接编译运行：
//
//   kotlinc app/src/main/java/com/UIN/Tool/ui/screen/dev/editor/CodeFormatters.kt \
//           tools/formatter-test/TestMain.kt -include-runtime -d /tmp/fmttest.jar
//   java -jar /tmp/fmttest.jar
//
// 期望输出末行为 ALL TESTS PASSED。
// 改动 CodeFormatters.kt 后请重跑一遍：JSON 有 14 个「保值不变量」样本，
// 会断言格式化前后的非空白非注释 token 序列逐个相同，用来守住「只动空白、绝不动值」这条铁律。
package com.UIN.Tool.ui.screen.dev.editor

import kotlin.system.exitProcess

var failures = 0

fun check(name: String, actual: String?, expected: String?) {
    if (actual == expected) {
        println("  PASS  $name")
    } else {
        failures++
        println("  FAIL  $name")
        println("        expected=${expected?.replace("\n", "\\n")}")
        println("        actual  =${actual?.replace("\n", "\\n")}")
    }
}

fun j(src: String) = CodeFormatters.formatText("a.json", src)

// ---- 保值不变量：格式化前后，「非空白非注释」的 token 序列必须逐个相同 ----
fun jsonTokens(src: String): List<String> {
    val toks = ArrayList<String>()
    var i = 0
    while (i < src.length) {
        val c = src[i]
        when {
            c.isWhitespace() -> i++
            c == '"' || c == '\'' -> {
                val q = c; var k = i + 1; var end = -1
                while (k < src.length) {
                    val ch = src[k]
                    if (ch == '\\') { k += 2; continue }
                    if (ch == q) { end = k + 1; break }
                    k++
                }
                if (end < 0) return emptyList()
                toks.add(src.substring(i, end)); i = end
            }
            c == '/' && i + 1 < src.length && src[i + 1] == '/' -> {
                while (i < src.length && src[i] != '\n') i++
            }
            c == '/' && i + 1 < src.length && src[i + 1] == '*' -> {
                val e = src.indexOf("*/", i + 2); if (e < 0) return emptyList(); i = e + 2
            }
            c == ',' || c == ':' || c == '{' || c == '}' || c == '[' || c == ']' -> {
                toks.add(c.toString()); i++
            }
            else -> {
                var k = i
                while (k < src.length) {
                    val ch = src[k]
                    if (ch.isWhitespace() || ch == ',' || ch == ':' || ch == '{' || ch == '}' || ch == '[' || ch == ']') break
                    k++
                }
                toks.add(src.substring(i, k)); i = k
            }
        }
    }
    return toks
}

/** 格式化成功时，必须证明「只改了空白」。 */
fun checkPreserve(name: String, src: String, fileName: String = "a.json") {
    val out = CodeFormatters.formatText(fileName, src) ?: run {
        failures++; println("  FAIL  $name （格式化失败，本用例应成功）"); return
    }
    val a = jsonTokens(src)
    val b = jsonTokens(out)
    if (a == b) println("  PASS  $name [保值]")
    else {
        failures++; println("  FAIL  $name [保值]")
        println("        in =$a")
        println("        out=$b")
    }
}
fun y(src: String) = CodeFormatters.formatText("a.yaml", src)

fun main() {
    // ===================== JSON =====================
    println("== JSON ==")
    check(
        "简单对象",
        j("""{"a":1,"b":[1,2,{"c":null}],"d":{}}"""),
        "{\n  \"a\": 1,\n  \"b\": [\n    1,\n    2,\n    {\n      \"c\": null\n    }\n  ],\n  \"d\": {}\n}"
    )
    check(
        "空数组/对象保持同行",
        j("""{"e":[],"o":{},"x":{"y":1}}"""),
        "{\n  \"e\": [],\n  \"o\": {},\n  \"x\": {\n    \"y\": 1\n  }\n}"
    )
    check("已是格式化形态则不变", j("{\n  \"a\": 1\n}"), "{\n  \"a\": 1\n}")
    check("顶层裸值", j("123"), "123")
    check("顶层裸字符串", j("\"hi\""), "\"hi\"")
    check("空输入", j(""), "")
    check("字符串内结构符不动", j("""{"s":"a:b,c{d}[e]"}"""), "{\n  \"s\": \"a:b,c{d}[e]\"\n}")
    check("字符串内转义引号", j("""{"s":"quote\"inside"}"""), "{\n  \"s\": \"quote\\\"inside\"\n}")
    check("大整数保值", j("""{"n":123456789012345678901234567890}"""), "{\n  \"n\": 123456789012345678901234567890\n}")
    check("科学计数法/字面量", j("""{"a":-1.5e-3,"b":true,"c":false}"""), "{\n  \"a\": -1.5e-3,\n  \"b\": true,\n  \"c\": false\n}")
    check("未闭合字符串 -> null", j("""{"a":"unclosed"""), null)
    check("括号不配对 -> null", j("""{"a":[1,2}"""), null)
    check("括号未闭合 -> null", j("""{"a":[1,2"""), null)
    check("顶层多值 -> null", j("1 2"), null)
    check("顶层逗号 -> null", j("1,2"), null)
    check("多余闭括号 -> null", j("]"), null)
    check(
        "JSON5 单引号 + 注释 + 尾逗量",
        CodeFormatters.formatText("a.json5", "// 头注释\n{'a': 1, /* inline */ 'b': [1, 2,],}"),
        "// 头注释\n{\n  'a': 1,\n  /* inline */ 'b': [\n    1,\n    2,\n  ],\n}"
    )
    check("嵌套数组套对象", j("""[[1,2],[{"k":"v"}]]"""), "[\n  [\n    1,\n    2\n  ],\n  [\n    {\n      \"k\": \"v\"\n    }\n  ]\n]")
    check("多行原始 JSON 去杂乱空白", j("{\r\n\t\"a\" : 1 ,\r\n\t\"b\" : { \"c\" : [ ] }\r\n}"), "{\n  \"a\": 1,\n  \"b\": {\n    \"c\": []\n  }\n}")
    check(
        "supports",
        "${CodeFormatters.supports("x.json")}${CodeFormatters.supports("x.yml")}" +
            "${CodeFormatters.supports("x.xml")}${CodeFormatters.supports("x.txt")}" +
            "${CodeFormatters.supports("x.JSONC")}",
        "truetruefalsefalsetrue"
    )
    check("不支持的扩展名 -> null", CodeFormatters.formatText("a.xml", """{"a":1}"""), null)
    // 回归：行注释会吃掉整行，下一个 token 必须另起一行，否则整段被注释掉
    check("行注释后不吞后续内容", j("{\"a\":1 // 备注\n,\"b\":2}"), "{\n  \"a\": 1, // 备注\n  \"b\": 2\n}")
    check("头注释后不吞对象", j("// 头\n{\"a\":1}"), "// 头\n{\n  \"a\": 1\n}")
    check("空对象里只有注释", j("{// c\n}"), "{\n  // c\n}")

    // ===================== YAML =====================
    println("== YAML ==")
    check("行尾空白", y("a: 1   \nb: 2   \n"), "a: 1\nb: 2\n")
    check("行首 tab 缩进展开", y("\ta: 1   \n\t\tb: 2\n"), "  a: 1\n    b: 2\n")
    check("行内 tab 不动", y("a: 1   \nb:\t2   \n"), "a: 1\nb:\t2\n")
    check("连续空行折叠", y("a: 1\n\n\n\nb: 2\n"), "a: 1\n\nb: 2\n")
    check("首尾空行去掉", y("\n\na: 1\n\n\n"), "a: 1\n")
    check("块标量原样保留", y("a: |\n  keep   \n\ttabbed\n\nb: 2   \n"), "a: |\n  keep   \n\ttabbed\n\nb: 2\n")
    check("块标量带缩进指示", y("a: >-\n  folded   \nb: 1   \n"), "a: >-\n  folded   \nb: 1\n")
    check(
        "多行双引号标量原样保留",
        y("a: \"line one   \n  still quoted   \"\nb: 1   \n"),
        "a: \"line one   \n  still quoted   \"\nb: 1\n"
    )
    check("单引号 '' 转义不误判闭合", y("a: 'it''s here'   \nb: 1   \n"), "a: 'it''s here'\nb: 1\n")
    check("注释里的引号不影响", y("a: 1 # don't   \nb: 2   \n"), "a: 1 # don't\nb: 2\n")
    check("行内 > 不误判为块标量", y("a: echo x > y   \nb: 2   \n"), "a: echo x > y\nb: 2\n")
    check("CRLF 行尾被统一", y("a: 1   \r\nb: 2   \r\n"), "a: 1\nb: 2\n")
    check("块标量内的连续空行不折叠", y("a: |\n  x\n\n\n  y\nb: 1   \n"), "a: |\n  x\n\n\n  y\nb: 1\n")
    check("文件以块标量收尾时保留末尾换行", y("a: |\n  x\n"), "a: |\n  x\n")
    check("空文件", y(""), "")
    check("注释块", y("# 头注释   \n\na: 1   \n"), "# 头注释\n\na: 1\n")
    check("序列列表", y("- a   \n- b   \n\n- c\n"), "- a\n- b\n\n- c\n")

    // ---- 保值不变量 ----
    println("== 保值不变量 ==")
    val samples = listOf(
        """{"a":1,"b":[1,2,{"c":null}],"d":{}}""",
        """{"e":[],"o":{},"x":{"y":1}}""",
        """{"s":"a:b,c{d}[e]"}""",
        """{"s":"quote\"inside"}""",
        """{"n":123456789012345678901234567890}""",
        """{"a":-1.5e-3,"b":true,"c":false}""",
        """[[1,2],[{"k":"v"}]]""",
        """{\r\n\t"a" : 1 ,\r\n\t"b" : { "c" : [ ] }\r\n}""",
        "{\"a\":1 // 备注\n,\"b\":2}",
        """// 头\n{"a":1}""",
        """{"a":1,"b":2}""",
        """[]""",
        """{}""",
        """{"a":{"b":{"c":{"d":[1,2,3]}}}}"""
    )
    for ((idx, sample) in samples.withIndex()) checkPreserve("样本${idx + 1}", sample)

    println()
    if (failures == 0) println("ALL TESTS PASSED") else println("$failures TEST(S) FAILED")
    if (failures > 0) exitProcess(1)
}
