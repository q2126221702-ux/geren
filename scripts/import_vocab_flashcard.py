"""从 WE Learn iWords 生成单词速记闪卡（纯背诵，含词性转化）."""
import json
import re
from datetime import datetime
from pathlib import Path

SRC = Path(__file__).parent.parent.parent / "welearn-output" / "7月4日" / "WE Learn_B1U4-U8_iWords_20260704.json"
DATA_DIR = Path(__file__).parent.parent / "data"
MANIFEST = DATA_DIR / "manifest.json"
OUT_FILE = DATA_DIR / "WE Learn_B1U4-U8_单词速记_20260704.json"
DECK_ID = "welearn_b1u4u8_vocab"
DATE_TAG = "20260704"

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

DERIVE_RULES = [
    (r"(.+)tion$", r"\1t", "v → n：-tion"),
    (r"(.+)tion$", r"\1te", "v → n：-tion"),
    (r"(.+)sion$", r"\1de", "v → n：-sion"),
    (r"(.+)sion$", r"\1d", "v → n：-sion"),
    (r"(.+)ment$", r"\1", "v → n：-ment"),
    (r"(.+)ment$", r"\1e", "v → n：-ment"),
    (r"(.+)ness$", r"\1", "adj → n：-ness"),
    (r"(.+)ness$", r"\1y", "adj → n：-ness"),
    (r"(.+)ly$", r"\1", "adj → adv：-ly"),
    (r"(.+)al$", r"\1", "n → adj：-al"),
    (r"(.+)al$", r"\1e", "n → adj：-al"),
    (r"(.+)ive$", r"\1e", "v → adj：-ive"),
    (r"(.+)ive$", r"\1", "v → adj：-ive"),
    (r"(.+)er$", r"\1", "v → n：-er"),
    (r"(.+)er$", r"\1e", "v → n：-er"),
    (r"(.+)or$", r"\1", "v → n：-or"),
    (r"(.+)or$", r"\1e", "v → n：-or"),
    (r"(.+)ist$", r"\1", "n → n：-ist"),
    (r"(.+)ist$", r"\1e", "n → n：-ist"),
    (r"(.+)ance$", r"\1", "v → n：-ance"),
    (r"(.+)ence$", r"\1", "v → n：-ence"),
    (r"(.+)ity$", r"\1", "adj → n：-ity"),
    (r"(.+)ity$", r"\1e", "adj → n：-ity"),
    (r"(.+)ful$", r"\1", "n → adj：-ful"),
    (r"(.+)less$", r"\1", "n → adj：-less"),
    (r"^un(.+)$", r"\1", "反义：un-"),
    (r"^in(.+)$", r"\1", "否定/向内：in-"),
    (r"^dis(.+)$", r"\1", "否定：dis-"),
    (r"^re(.+)$", r"\1", "再：re-"),
]


def is_phrase(w: str) -> bool:
    return " " in w.strip() or "..." in w or "/" in w


def zh_short(zh: str) -> str:
    return re.split(r"[，；;,/]", zh)[0].strip()


def pos_zh(pos: str) -> str:
    return POS_ZH.get(pos, pos or "短语")


def derive_candidates(w: str) -> list[tuple[str, str]]:
    w = w.lower()
    out: list[tuple[str, str]] = []
    seen = set()
    for pat, repl, rule in DERIVE_RULES:
        if not re.match(pat, w):
            continue
        c = re.sub(pat, repl, w)
        for cand in (c, c + "e"):
            if cand and cand not in seen:
                seen.add(cand)
                out.append((cand, rule))
    return out


def find_related(base: str, by_word: dict) -> list[dict]:
    related = []
    seen = {base.lower()}
    for cand, rule in derive_candidates(base):
        hit = by_word.get(cand)
        if hit and cand not in seen:
            seen.add(cand)
            related.append(
                {
                    "word": hit["word"],
                    "pos": hit["pos"],
                    "pos_zh": pos_zh(hit["pos"]),
                    "zh": hit["meaning_zh"],
                    "phonetic": hit.get("phonetic", ""),
                    "rule": rule,
                }
            )
    return related


def build_hook(word: str, pos: str, zh: str, forms: list[dict]) -> str:
    short = zh_short(zh)
    if len(forms) > 1:
        chain = " → ".join(f"{f['word']}({f['pos']})" for f in forms[:4])
        return f"词性链：{chain}"
    letters = len(word.replace(" ", ""))
    return f"{word} = {short}（{pos_zh(pos)}，{letters} 字母，首字母 {word[0].upper()}）"


def collect_words(data: dict) -> list[dict]:
    rows = []
    for unit in data.get("units", []):
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
                    }
                )
    return rows


def build_cards(rows: list[dict]) -> list[dict]:
    by_word = {r["word"].lower(): r for r in rows}
    cards = []
    for i, row in enumerate(rows, start=1):
        related = find_related(row["word"], by_word)
        base = {
            "word": row["word"],
            "pos": row["pos"],
            "pos_zh": pos_zh(row["pos"]),
            "zh": row["meaning_zh"],
            "phonetic": row["phonetic"],
            "role": "base",
        }
        forms = [base] + related
        cards.append(
            {
                "sort": i,
                "word": row["word"],
                "phonetic": row["phonetic"],
                "pos": row["pos"],
                "pos_zh": pos_zh(row["pos"]),
                "zh": row["meaning_zh"],
                "forms": forms,
                "hook": build_hook(row["word"], row["pos"], row["meaning_zh"], forms),
            }
        )
    return cards


def update_manifest(count: int):
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    entry = {
        "id": DECK_ID,
        "title": "WE Learn B1U4~U8 单词速记",
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
    rows = collect_words(data)
    cards = build_cards(rows)
    with_forms = sum(1 for c in cards if len(c["forms"]) > 1)

    deck = {
        "title": "WE Learn B1U4~U8 单词速记",
        "exported_at": datetime.now().isoformat(),
        "source": "WE Learn iWords 截图整理（7月4日）",
        "quiz_type": "vocab_flashcard",
        "description": f"共 {len(cards)} 个单词 · 含 {with_forms} 张词性转化 · 纯背诵",
        "cards": cards,
    }
    OUT_FILE.write_text(json.dumps(deck, ensure_ascii=False, indent=2), encoding="utf-8")
    update_manifest(len(cards))
    print(f"  {OUT_FILE.name} — {len(cards)} 张（{with_forms} 张含词性转化）")
    print(f"已更新 {MANIFEST}")


if __name__ == "__main__":
    main()
