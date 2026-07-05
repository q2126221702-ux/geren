"""Analyze word families in iWords source."""
import json
from collections import defaultdict
from pathlib import Path

SRC = Path(__file__).parent.parent.parent / "welearn-output" / "7月4日" / "WE Learn_B1U4-U8_iWords_20260704.json"


def is_phrase(w: str) -> bool:
    return " " in w.strip() or "..." in w or "/" in w


def main():
    data = json.loads(SRC.read_text(encoding="utf-8"))
    words = []
    for unit in data["units"]:
        for sec in unit.get("sections", []):
            for e in sec.get("words", []):
                w = e["word"].strip()
                if is_phrase(w):
                    continue
                if not e.get("pos"):
                    continue
                words.append({**e, "word": w, "unit": unit["unit"]})

    print("single words with pos:", len(words))

    suffixes = [
        "tion", "sion", "ment", "ness", "ity", "ly", "al", "ive", "ous",
        "ful", "less", "er", "or", "ist", "ance", "ence", "able", "ible",
        "ize", "ise", "ing", "ed", "es", "s",
    ]
    prefixes = ["un", "re", "dis", "mis", "over", "under", "inter", "intra", "extra"]

    def stems(w: str) -> set[str]:
        w = w.lower()
        out = {w}
        for pre in prefixes:
            if w.startswith(pre) and len(w) > len(pre) + 2:
                out.add(w[len(pre) :])
        for suf in suffixes:
            if w.endswith(suf) and len(w) > len(suf) + 2:
                out.add(w[: -len(suf)])
        return out

    by_stem: dict[str, list] = defaultdict(list)
    for e in words:
        for s in stems(e["word"].lower()):
            by_stem[s].append(e)

    families = []
    used = set()
    for _, group in sorted(by_stem.items(), key=lambda x: -len(x[1])):
        uniq = {x["word"].lower(): x for x in group}
        group = list(uniq.values())
        if len(group) < 2:
            continue
        key = tuple(sorted(x["word"].lower() for x in group))
        if key in used:
            continue
        used.add(key)
        pos_set = {x["pos"] for x in group}
        if len(pos_set) < 2:
            continue
        families.append(group)

    print("word families (2+ forms, 2+ pos):", len(families))
    for g in families[:20]:
        print(" | ".join(f"{x['word']} {x['pos']}" for x in sorted(g, key=lambda x: x["word"])))

    in_family = {x["word"].lower() for g in families for x in g}
    solo = [e for e in words if e["word"].lower() not in in_family]
    print("solo words:", len(solo))


if __name__ == "__main__":
    main()
