"""Find verb/noun/adj/adv pairs in iWords."""
import json
import re
from pathlib import Path

SRC = Path(__file__).parent.parent.parent / "welearn-output" / "7月4日" / "WE Learn_B1U4-U8_iWords_20260704.json"

RULES = [
    (r"(.+)tion$", r"\1t"),  # completion -> complet -> complete?
    (r"(.+)tion$", r"\1te"),
    (r"(.+)sion$", r"\1de"),
    (r"(.+)sion$", r"\1d"),
    (r"(.+)ment$", r"\1"),
    (r"(.+)ment$", r"\1e"),
    (r"(.+)ness$", r"\1"),
    (r"(.+)ness$", r"\1y"),
    (r"(.+)ly$", r"\1"),
    (r"(.+)al$", r"\1"),
    (r"(.+)al$", r"\1e"),
    (r"(.+)ive$", r"\1e"),
    (r"(.+)ive$", r"\1"),
    (r"(.+)er$", r"\1"),
    (r"(.+)er$", r"\1e"),
    (r"(.+)or$", r"\1"),
    (r"(.+)or$", r"\1e"),
    (r"(.+)ist$", r"\1"),
    (r"(.+)ist$", r"\1e"),
    (r"(.+)ance$", r"\1"),
    (r"(.+)ence$", r"\1"),
    (r"(.+)ity$", r"\1"),
    (r"(.+)ity$", r"\1e"),
    (r"(.+)ful$", r"\1"),
    (r"(.+)less$", r"\1"),
    (r"^un(.+)$", r"\1"),
    (r"^in(.+)$", r"\1"),
    (r"^dis(.+)$", r"\1"),
    (r"^re(.+)$", r"\1"),
]


def is_phrase(w):
    return " " in w.strip() or "..." in w or "/" in w


def candidates(w):
    w = w.lower()
    out = {w}
    for pat, repl in RULES:
        m = re.match(pat, w)
        if m:
            out.add(re.sub(pat, repl, w))
            out.add(re.sub(pat, repl, w) + "e")
    return out


def main():
    data = json.loads(SRC.read_text(encoding="utf-8"))
    by_word = {}
    for unit in data["units"]:
        for sec in unit.get("sections", []):
            for e in sec.get("words", []):
                w = e["word"].strip()
                if is_phrase(w) or not e.get("pos"):
                    continue
                by_word[w.lower()] = {**e, "word": w}

    pairs = []
    seen = set()
    for w, e in by_word.items():
        for c in candidates(w):
            if c in by_word and c != w:
                key = tuple(sorted([w, c]))
                if key in seen:
                    continue
                seen.add(key)
                a, b = by_word[w], by_word[c]
                if a["pos"] != b["pos"]:
                    pairs.append((a, b))

    print("pairs:", len(pairs))
    for a, b in sorted(pairs, key=lambda x: x[0]["word"]):
        print(f"  {a['word']} {a['pos']} ↔ {b['word']} {b['pos']}")


if __name__ == "__main__":
    main()
