"""对比 welearn-output 源数据与 quiz-web 生成结果."""
import json
from pathlib import Path

ROOT = Path(__file__).parent.parent.parent
SRC = ROOT / "welearn-output" / "WE Learn_B1U4-U8_翻译题_20260703.json"
DATA = Path(__file__).parent.parent / "data"

src = json.loads(SRC.read_text(encoding="utf-8"))
issues = []

for unit in src["units"]:
    name = unit["unit"]
    safe = name.replace(" ", "_")
    fp = DATA / f"WE Learn_{safe}_翻译题_20260703.json"
    if not fp.exists():
        issues.append(f"missing generated: {name}")
        continue
    gen = json.loads(fp.read_text(encoding="utf-8"))
    f_count = sum(1 for ex in unit["exercises"] if ex.get("section") == "Translation F" for _ in ex["questions"])
    g_count = sum(1 for ex in unit["exercises"] if ex.get("section") == "Translation G")
    expected = f_count + g_count
    actual = len(gen["questions"])
    if expected != actual:
        issues.append(f"{name}: expected {expected}, got {actual}")

if issues:
    print("SOURCE MISMATCH:")
    for i in issues:
        print(" -", i)
    raise SystemExit(1)
print(f"Source vs generated OK ({len(src['units'])} units)")
