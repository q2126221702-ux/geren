"""校验 manifest 与题库 JSON 一致性."""
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from phrase_cloze_helper import phrase_cloze_title_answer

DATA = Path(__file__).parent.parent / "data"
manifest = json.loads((DATA / "manifest.json").read_text(encoding="utf-8"))
issues = []

VALID_IWORMS_PATTERNS = {"zh2en", "en2zh", "spell", "pos", "assoc", "phrase_cloze"}
REMIX_MAP = {
    "zh2en": "en2zh",
    "en2zh": "zh2en",
    "spell": "en2zh",
    "pos": "spell",
    "assoc": "en2zh",
    "phrase_cloze": "en2zh",
}


def is_phrase(en: str) -> bool:
    return " " in en.strip() or "..." in en or "/" in en


for q in manifest["quizzes"]:
    fp = DATA / q["file"]
    if not fp.exists():
        issues.append(f"missing file: {q['file']}")
        continue
    data = json.loads(fp.read_text(encoding="utf-8"))
    is_flashcard = data.get("quiz_type") == "vocab_flashcard" or q.get("kind") == "flashcard"

    if is_flashcard:
        actual = len(data.get("cards", []))
        if actual != q["count"]:
            issues.append(f"{q['id']}: manifest count {q['count']} != actual {actual}")
        if data.get("quiz_type") != "vocab_flashcard":
            issues.append(f"{q['id']}: expected quiz_type vocab_flashcard")
        for card in data.get("cards", []):
            sort = card.get("sort", "?")
            if not card.get("forms"):
                issues.append(f"{q['id']} card {sort} missing forms")
            if not (card.get("word") or card.get("headword")):
                issues.append(f"{q['id']} card {sort} missing word/headword")
        continue

    actual = len(data.get("questions", []))
    if actual != q["count"]:
        issues.append(f"{q['id']}: manifest count {q['count']} != actual {actual}")

    is_iwords = data.get("quiz_type") == "iwords_fill" or q["id"].endswith("_iwords")
    if is_iwords and data.get("quiz_type") != "iwords_fill":
        issues.append(f"{q['id']}: expected quiz_type iwords_fill")

    for qu in data["questions"]:
        sort = qu.get("sort", "?")
        if qu.get("type") == "单选题":
            ca = qu.get("correct_answer", "")
            if not ca[:2].startswith(("A.", "B.", "C.", "D.")):
                issues.append(f"{q['id']} Q{sort} bad answer format")
            opts = qu.get("options", [])
            if len(opts) < 2:
                issues.append(f"{q['id']} Q{sort} options<2")
            letter = ca[0]
            idx = {"A": 0, "B": 1, "C": 2, "D": 3}.get(letter)
            if idx is not None and idx < len(opts):
                expected = f"{letter}. {opts[idx]}"
                if ca != expected:
                    issues.append(f"{q['id']} Q{sort} answer/option mismatch")
        if qu.get("type") == "问答题" and not qu.get("correct_answer"):
            issues.append(f"{q['id']} Q{sort} essay missing ref")

        if is_iwords:
            if qu.get("type") != "填空题(客观)":
                issues.append(f"{q['id']} Q{sort} iwords not fill type")
            if not qu.get("correct_answer"):
                issues.append(f"{q['id']} Q{sort} fill missing answer")
            if not qu.get("fill_hint"):
                issues.append(f"{q['id']} Q{sort} missing fill_hint")
            mem = qu.get("memory") or {}
            pattern = mem.get("pattern")
            if pattern not in VALID_IWORMS_PATTERNS:
                issues.append(f"{q['id']} Q{sort} bad pattern {pattern}")
            if mem.get("lang") == "pos" and not mem.get("pos_zh"):
                issues.append(f"{q['id']} Q{sort} pos missing pos_zh")
            remix = mem.get("remix_pattern")
            if remix and remix != REMIX_MAP.get(pattern):
                issues.append(f"{q['id']} Q{sort} remix {remix} != expected {REMIX_MAP.get(pattern)}")
            if pattern == "phrase_cloze" and is_phrase(mem.get("en", "")):
                if "【短语填空】" not in qu.get("title", ""):
                    issues.append(f"{q['id']} Q{sort} phrase_cloze title mismatch")
            if pattern == "phrase_cloze" and not is_phrase(mem.get("en", "")):
                issues.append(f"{q['id']} Q{sort} phrase_cloze on single word")
            if pattern == "phrase_cloze":
                en = mem.get("en", "")
                zh = mem.get("zh", "")
                tag_m = re.match(r"(【[^】]+】)", qu.get("title", ""))
                tag = tag_m.group(1) if tag_m else ""
                expected_title, expected_ans = phrase_cloze_title_answer(en, zh, tag)
                if qu.get("correct_answer") != expected_ans:
                    issues.append(
                        f"{q['id']} Q{sort} phrase_cloze answer {qu.get('correct_answer')!r} != {expected_ans!r}"
                    )
                cloze_line = qu.get("title", "").split("\n")[-1]
                if "______" in cloze_line and "请填写完整" not in cloze_line:
                    filled = cloze_line.replace("______", expected_ans)
                    if re.sub(r"\s+", " ", filled).strip() != re.sub(r"\s+", " ", en).strip():
                        issues.append(f"{q['id']} Q{sort} phrase_cloze fill mismatch for {en!r}")

welearn = [q for q in manifest["quizzes"] if q["id"].startswith("welearn_")]
print(f"Manifest quizzes: {len(manifest['quizzes'])}")
print(f"WE Learn units: {len(welearn)}")
if issues:
    print("ISSUES:")
    for i in issues:
        print(" -", i)
    raise SystemExit(1)
print("All manifest/data checks OK")
