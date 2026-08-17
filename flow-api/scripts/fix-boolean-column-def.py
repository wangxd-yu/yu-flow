# -*- coding: utf-8 -*-
import re
from pathlib import Path

root = Path(r"f:/Git_Project/yu-flow/yu-flow/flow-api/src/main/java")
pat1 = re.compile(
    r'@Column\(columnDefinition = "tinyint\(1\) default \d+"\)\n(\s*private Boolean )'
)
pat2 = re.compile(
    r'@Column\(name = "([^"]+)", columnDefinition = "tinyint\(1\) default \d+"\)'
)

for f in root.rglob("*DO.java"):
    text = f.read_text(encoding="utf-8")
    n = pat1.sub(r"@Column(nullable = false)\n\1", text)
    n = pat2.sub(r'@Column(name = "\1", nullable = false)', n)
    if n != text:
        f.write_text(n, encoding="utf-8", newline="\n")
        print("updated", f.relative_to(root))
