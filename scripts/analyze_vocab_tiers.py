"""分析 iWords 与课标/PRETCO 词表的匹配情况."""
import json
import re
from pathlib import Path

SRC = Path(__file__).parent.parent.parent / "welearn-output" / "7月4日" / "WE Learn_B1U4-U8_iWords_20260704.json"
TIERS = Path(__file__).parent / "curriculum_vocab_tiers.json"

SKIP = {
    "asia", "europe", "hong kong",  # 专有地名
}

def is_phrase(w):
    return " " in w.strip() or "..." in w

def main():
    tiers = json.loads(TIERS.read_text(encoding="utf-8"))
    data = json.loads(SRC.read_text(encoding="utf-8"))
    rows = []
    for unit in data["units"]:
        for sec in unit.get("sections", []):
            for e in sec.get("words", []):
                w = e["word"].strip()
                if is_phrase(w) or not e.get("pos"):
                    continue
                t = tiers.get(w.lower(), -1)
                rows.append((w, t, e["pos"], unit["unit"][:6]))
    from collections import Counter
    c = Counter(t for _, t, _, _ in rows)
    print("tier match:", dict(sorted(c.items())))
    print("unmatched sample:", [w for w,t,_,_ in rows if t == -1][:20])
    print("** sample:", [w for w,t,_,_ in rows if t == 2][:15])
    print("* sample:", [w for w,t,_,_ in rows if t == 1][:15])

if __name__ == "__main__":
    main()
