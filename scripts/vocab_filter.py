"""筛选 iWords 重点词汇（高职课标2021 + 实用综合教程1 + PRETCO B级导向）."""
import json
import re
from pathlib import Path

SRC = Path(__file__).parent.parent.parent / "welearn-output" / "7月4日" / "WE Learn_B1U4-U8_iWords_20260704.json"
TIERS = Path(__file__).parent / "curriculum_vocab_tiers.json"

# standard: 约99张 · sprint: 考前冲刺约55–60张
PROFILES = {
    "standard": {"per_unit_single": 18, "min_single_score": 0, "strict_tier": False},
    "sprint": {"per_unit_single": 10, "min_single_score": 24, "strict_tier": True},
}

DEFAULT_PROFILE = "sprint"

# 地名等不必背
SKIP_WORDS = {
    "asia", "europe", "hong kong",
}

# B级冲刺高频（教材 + 真题常见）
TEXTBOOK_PRIORITY = {
    "contribution", "conservation", "generation", "extraordinary", "international",
    "traditional", "community", "campaign", "opportunity", "profession", "equipment",
    "complete", "completion", "employ", "employer", "health", "environment",
    "festival", "activity", "active", "fitness", "fit", "perfect", "perfectly",
    "reference", "refer", "observe", "prevent", "treatment", "disease", "symptom",
    "participate", "volunteer", "career", "salary", "interview", "resume",
    "pollution", "climate", "solar", "energy", "recycle", "preserve",
    "character", "cartoon", "studio", "popular", "create", "expand",
    "appear", "barrier", "billion", "launch", "movement", "urgent", "global",
    "rescue", "muscle", "calorie", "threat", "stress", "balance", "injury",
}

POS_WEIGHT = {"v.": 12, "n.": 8, "adj.": 7, "adv.": 6, "prep.": 3, "int.": 2, "abbr.": 1}


def is_phrase(w: str) -> bool:
    return " " in w.strip() or "..." in w or "/" in w


def unit_short(name: str) -> str:
    m = re.match(r"(B1U\d+)", name, re.I)
    return m.group(1).upper() if m else name


def word_tier(word: str, tiers: dict) -> int:
    w = word.lower()
    if w in TEXTBOOK_PRIORITY:
        return max(tiers.get(w, -1), 1)
    return tiers.get(w, -1)


def score_form(form: dict, tiers: dict, profile: str = DEFAULT_PROFILE) -> int:
    w = form["word"].lower()
    if w in SKIP_WORDS:
        return -999
    cfg = PROFILES[profile]
    t = word_tier(w, tiers)
    if cfg["strict_tier"] and t < 0 and w not in TEXTBOOK_PRIORITY:
        return -999
    s = {2: 42, 1: 32, 0: 12, -1: 8}.get(t, 8)
    if cfg["strict_tier"] and t == 0:
        s -= 4
    s += POS_WEIGHT.get(form.get("pos", ""), 4)
    ln = len(w.replace("-", ""))
    if 6 <= ln <= 12:
        s += 4
    elif ln >= 13:
        s += 2
    return s


def score_card(card: dict, tiers: dict, profile: str = DEFAULT_PROFILE) -> int:
    if card.get("multi"):
        return 1000 + sum(score_form(f, tiers, profile) for f in card.get("forms", []))
    return max(score_form(f, tiers, profile) for f in card.get("forms", []))


def filter_cards(cards: list[dict], tiers: dict, profile: str = DEFAULT_PROFILE) -> list[dict]:
    cfg = PROFILES[profile]
    per_unit = cfg["per_unit_single"]
    min_score = cfg["min_single_score"]
    multi = [c for c in cards if c.get("multi")]
    single = [c for c in cards if not c.get("multi")]
    units = sorted({c.get("unit", "B1U4") for c in cards})

    picked: list[dict] = []
    picked_keys: set[str] = set()

    for u in units:
        for c in multi:
            if c.get("unit") != u:
                continue
            key = c["headword"].lower()
            if key in picked_keys:
                continue
            picked.append(c)
            picked_keys.add(key)

        ranked = sorted(
            (c for c in single if c.get("unit") == u),
            key=lambda c: score_card(c, tiers, profile),
            reverse=True,
        )
        n = 0
        for c in ranked:
            if n >= per_unit:
                break
            sc = score_card(c, tiers, profile)
            if sc < min_score:
                continue
            key = c["headword"].lower()
            if key in picked_keys:
                continue
            picked.append(c)
            picked_keys.add(key)
            n += 1

    picked.sort(key=lambda c: c.get("sort", 9999))
    for i, c in enumerate(picked, 1):
        c["sort"] = i
    return picked


def profile_meta(profile: str, count: int, multi: int) -> dict:
    if profile == "sprint":
        return {
            "title": f"WE Learn B1U4~U8 考前冲刺（{count}词）",
            "description": f"共 {count} 张 · {multi} 张词族 · 课标*/** + B级冲刺",
            "filter_criteria": "考前冲刺：词族全留，每单元约10词，优先课标*/**与B级高频",
        }
    return {
        "title": f"WE Learn B1U4~U8 单词速记（重点{count}词）",
        "description": f"共 {count} 张 · {multi} 张词族 · 课标2021+AB级考点精选",
        "filter_criteria": "教育部《高职英语课标2021》词汇等级 + 教材iWords + PRETCO B级导向，每单元约18词",
    }


def preview(profile: str = DEFAULT_PROFILE):
    import import_vocab_flashcard as imp

    tiers = json.loads(TIERS.read_text(encoding="utf-8"))
    data = json.loads(SRC.read_text(encoding="utf-8"))
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
    rows = imp.merge_rows(rows)
    # attach unit on merge - keep first unit
    families = imp.build_families(rows)
    cards = imp.build_cards(families)
    # restore unit from rows
    word_unit = {}
    for r in rows:
        word_unit.setdefault(r["word"].lower(), r["unit"])
    for c in cards:
        c["unit"] = word_unit.get(c["headword"].lower(), "B1U4")

    filtered = filter_cards(cards, tiers, profile)
    from collections import Counter

    print(f"[{profile}] total {len(cards)} -> {len(filtered)}")
    print("by unit:", dict(Counter(c["unit"] for c in filtered)))
    print("multi:", sum(1 for c in filtered if c["multi"]))
    tier_hits = Counter()
    for c in filtered:
        for f in c["forms"]:
            tier_hits[word_tier(f["word"], tiers)] += 1
    print("form tiers:", dict(tier_hits))


if __name__ == "__main__":
    import sys

    p = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_PROFILE
    preview(p)
