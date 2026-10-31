#!/usr/bin/env python3
"""A-31：default(values) 与 values-zh-rCN 的字符串键/占位符必须对齐，防回归。

检查项：
  1. 键集合完全一致（string / string-array / plurals）
  2. 单文件内无重复键
  3. 同一键的格式化占位符一致（%1$s/%s/%d/%%...，formatted="false" 跳过）

任一不一致退出码 1，供 CI 直接 fail。
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

RES = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res"
BASE = RES / "values" / "strings.xml"
ZH = RES / "values-zh-rCN" / "strings.xml"

# Android 格式说明符：位置 + 可选宽度/精度 + 类型；%% 为字面量
SPEC_RE = re.compile(r"%(?:\d+\$)?[-+# 0]*(?:\d+)?(?:\.\d+)?([a-zA-Z%])")


def load(path: Path):
    """返回 {kind:name: element}，并收集重复键。"""
    root = ET.parse(path).getroot()
    keys = {}
    dups = []
    for kind in ("string", "string-array", "plurals"):
        for el in root.findall(kind):
            name = el.get("name")
            if name is None:
                continue
            k = (kind, name)
            if k in keys:
                dups.append(k)
            keys[k] = el
    return keys, dups


def specs(el: ET.Element):
    if el.get("formatted") == "false":
        return None  # 非格式化串不检查占位符
    text = "".join(el.itertext())
    return sorted(SPEC_RE.findall(text))


def main() -> int:
    base, base_dups = load(BASE)
    zh, zh_dups = load(ZH)
    errors = []

    for d in base_dups:
        errors.append(f"{BASE}: duplicate key {d}")
    for d in zh_dups:
        errors.append(f"{ZH}: duplicate key {d}")

    only_base = sorted(set(base) - set(zh))
    only_zh = sorted(set(zh) - set(base))
    for k in only_base:
        errors.append(f"missing in zh-rCN: {k[0]}/{k[1]}")
    for k in only_zh:
        errors.append(f"missing in default: {k[0]}/{k[1]}")

    for k in sorted(set(base) & set(zh)):
        a, b = specs(base[k]), specs(zh[k])
        if a is not None and b is not None and a != b:
            errors.append(
                f"placeholder mismatch {k[0]}/{k[1]}: default={a} zh-rCN={b}"
            )

    if errors:
        print(f"FAIL: {len(errors)} problem(s)")
        for e in errors:
            print("  -", e)
        return 1
    print(f"OK: {len(base)} keys aligned (string/string-array/plurals), 0 placeholder diffs")
    return 0


if __name__ == "__main__":
    sys.exit(main())
