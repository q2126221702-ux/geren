"""从 WE Learn iWords 生成单词速记闪卡（词族合并 + 课标重点筛选）."""
import json
import re
from collections import defaultdict
from datetime import datetime
from pathlib import Path

from vocab_filter import filter_cards, unit_short

SRC = Path(__file__).parent.parent.parent / "welearn-output" / "7月4日" / "WE Learn_B1U4-U8_iWords_20260704.json"
TIERS_FILE = Path(__file__).parent / "curriculum_vocab_tiers.json"
DATA_DIR = Path(__file__).parent.parent / "data"
MANIFEST = DATA_DIR / "manifest.json"
OUT_FILE = DATA_DIR / "WE Learn_B1U4-U8_单词速记_20260704.json"
DECK_ID = "welearn_b1u4u8_vocab"

POS_ZH = {
    "n.": "名词",
    "v.": "动词",
    "adj.": "形容词",
    "adv.": "副词",
    "prep.": "介词",
    "int.": "感叹词",
    "abbr.": "缩写",
    "conj.": "连词",
    "pron.": "代词",
    "num.": "数词",
    "art.": "冠词",
}

POS_ORDER = {"v.": 0, "n.": 1, "adj.": 2, "adv.": 3, "prep.": 4, "conj.": 5, "pron.": 6, "int.": 7, "num.": 8, "art.": 9, "abbr.": 10}

DERIVE_RULES = [
    (r"(.+)tion$", r"\1t"),
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


def is_phrase(w: str) -> bool:
    return " " in w.strip() or "..." in w or "/" in w


def zh_short(zh: str) -> str:
    return re.split(r"[，；;,/]", zh)[0].strip()


def pos_zh(pos: str) -> str:
    return POS_ZH.get(pos, pos or "短语")


def derive_candidates(w: str) -> set[str]:
    w = w.lower()
    out = set()
    for pat, repl in DERIVE_RULES:
        if re.match(pat, w):
            c = re.sub(pat, repl, w)
            out.add(c)
            out.add(c + "e")
    return out


def merge_rows(rows: list[dict]) -> list[dict]:
    by_word: dict[str, dict] = {}
    for row in rows:
        key = row["word"].lower()
        if key not in by_word:
            by_word[key] = dict(row)
            continue
        prev = by_word[key]
        for zh in row["meaning_zh"].split("；"):
            zh = zh.strip()
            if zh and zh not in prev["meaning_zh"]:
                prev["meaning_zh"] = f"{prev['meaning_zh']}；{zh}" if prev["meaning_zh"] else zh
        if not prev.get("phonetic") and row.get("phonetic"):
            prev["phonetic"] = row["phonetic"]
    return list(by_word.values())


def attach_unit(cards: list[dict], rows: list[dict]) -> None:
    word_unit = {r["word"].lower(): r["unit"] for r in rows}
    for c in cards:
        c["unit"] = word_unit.get(c["headword"].lower(), "B1U4")


def form_entry(row: dict) -> dict:
    return {
        "word": row["word"],
        "pos": row["pos"],
        "pos_zh": pos_zh(row["pos"]),
        "zh": row["meaning_zh"],
        "phonetic": row.get("phonetic", "").strip(),
    }


def sort_forms(forms: list[dict]) -> list[dict]:
    return sorted(forms, key=lambda f: (POS_ORDER.get(f["pos"], 99), f["word"].lower()))


def pick_headword(forms: list[dict]) -> str:
    """Prefer verb, then noun, then shortest."""
    for pos in ("v.", "n.", "adj.", "adv."):
        for f in forms:
            if f["pos"] == pos:
                return f["word"]
    return min(forms, key=lambda f: len(f["word"]))["word"]


def zh_summary(forms: list[dict]) -> str:
    parts = []
    seen = set()
    for f in sort_forms(forms):
        s = zh_short(f["zh"])
        if s and s not in seen:
            seen.add(s)
            parts.append(s)
    return "；".join(parts[:3])


def build_families(rows: list[dict]) -> list[list[dict]]:
    by_word = {r["word"].lower(): r for r in rows}
    parent = {r["word"].lower(): r["word"].lower() for r in rows}

    def find(x: str) -> str:
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x

    def union(a: str, b: str) -> None:
        ra, rb = find(a), find(b)
        if ra != rb:
            parent[rb] = ra

    for row in rows:
        w = row["word"].lower()
        for cand in derive_candidates(w):
            if cand in by_word:
                union(w, cand)

    groups: dict[str, list[dict]] = defaultdict(list)
    for row in rows:
        groups[find(row["word"].lower())].append(row)

    # Preserve source order by first appearance
    order = []
    seen_roots = set()
    for row in rows:
        root = find(row["word"].lower())
        if root not in seen_roots:
            seen_roots.add(root)
            order.append(groups[root])

    return order


def build_cards(families: list[list[dict]]) -> list[dict]:
    cards = []
    for i, group in enumerate(families, start=1):
        uniq = {}
        for r in group:
            uniq[r["word"].lower()] = r
        forms = sort_forms([form_entry(r) for r in uniq.values()])
        head = pick_headword(forms)
        cards.append(
            {
                "sort": i,
                "headword": head,
                "word": head,
                "forms": forms,
                "zh_summary": zh_summary(forms),
                "multi": len(forms) > 1,
            }
        )
    return cards


def update_manifest(count: int, title: str):
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    entry = {
        "id": DECK_ID,
        "title": title,
        "file": OUT_FILE.name,
        "count": count,
        "kind": "flashcard",
    }
    manifest["quizzes"] = [q for q in manifest["quizzes"] if q.get("id") != DECK_ID]
    insert_at = next(
        (i for i, q in enumerate(manifest["quizzes"]) if q.get("id") == "welearn_b1u8_iwords"),
        len(manifest["quizzes"]),
    )
    insert_at += 1
    manifest["quizzes"].insert(insert_at, entry)
    MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")


def main():
    data = json.loads(SRC.read_text(encoding="utf-8"))
    tiers = json.loads(TIERS_FILE.read_text(encoding="utf-8"))
    rows = []
    for unit in data.get("units", []):
        us = unit_short(unit["unit"])
        for sec in unit.get("sections", []):
            for e in sec.get("words", []):
                w = e["word"].strip()
                if is_phrase(w) or not e.get("pos"):
                    continue
                rows.append(
                    {
                        "word": w,
                        "phonetic": e.get("phonetic", "").strip(),
                        "pos": e["pos"].strip(),
                        "meaning_zh": e.get("meaning_zh", "").strip(),
                        "unit": us,
                    }
                )

    rows = merge_rows(rows)
    families = build_families(rows)
    all_cards = build_cards(families)
    attach_unit(all_cards, rows)
    cards = filter_cards(all_cards, tiers)
    multi = sum(1 for c in cards if c["multi"])

    title = f"WE Learn B1U4~U8 单词速记（重点{len(cards)}词）"
    deck = {
        "title": title,
        "exported_at": datetime.now().isoformat(),
        "source": "实用综合教程(第三版)1 ISBN 9787544677301 · WE Learn iWords",
        "quiz_type": "vocab_flashcard",
        "description": f"共 {len(cards)} 张 · {multi} 张词族 · 课标2021+AB级考点精选",
        "filter_criteria": "教育部《高职英语课标2021》词汇等级 + 教材iWords + PRETCO B级导向，每单元约18词",
        "cards": cards,
    }
    OUT_FILE.write_text(json.dumps(deck, ensure_ascii=False, indent=2), encoding="utf-8")
    update_manifest(len(cards), title)
    print(f"  {OUT_FILE.name} — {len(all_cards)} -> {len(cards)} 张（{multi} 词族，原 {len(rows)} 词）")
    from collections import Counter

    print("  单元分布:", dict(Counter(c["unit"] for c in cards)))
    for c in cards:
        if c["multi"]:
            words = " / ".join(f"{f['word']} {f['pos']}" for f in c["forms"])
            print(f"    词族: {words}")


if __name__ == "__main__":
    main()
