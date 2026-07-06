import json
import collections
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "data"
files = {
    "profinet": "Profinet工业以太网测验_20260626_185300.json",
    "opc": "OPC规范_20260626_185227.json",
    "modbus": "MODBUS协议及应用_20260626_185238.json",
    "serial": "串口及应用_20260626_185247.json",
    "comprehensive": "网络及工业通信综合考核_145题.json",
}

for name, fname in files.items():
    with open(ROOT / fname, encoding="utf-8") as f:
        d = json.load(f)
    types = collections.Counter(q["type"] for q in d["questions"])
    print(f"=== {name} ({len(d['questions'])}题) ===")
    for t, c in types.most_common():
        print(f"  {t}: {c}")
    scores = collections.Counter(q.get("full_score", 0) for q in d["questions"])
    print("  scores:", dict(sorted(scores.items())))

print("\n=== comprehensive type breakdown with scores ===")
with open(ROOT / files["comprehensive"], encoding="utf-8") as f:
    comp = json.load(f)
by_type = collections.defaultdict(list)
for q in comp["questions"]:
    by_type[q["type"]].append(q.get("full_score", 0))
for t, scores in sorted(by_type.items()):
    print(f"{t}: count={len(scores)}, scores={sorted(set(scores))}")
