#!/usr/bin/env python3
"""Audit iWords fill questions for cloze/answer/display consistency."""
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from phrase_cloze_helper import phrase_cloze_title_answer

DATA = Path(__file__).parent.parent / "data"
issues = []


def add(kind, quiz_id, sort, detail):
    issues.append({"kind": kind, "quiz_id": quiz_id, "sort": sort, "detail": detail})


def audit_phrase_cloze(quiz_id, q):
    sort = q.get("sort")
    mem = q.get("memory") or {}
    en = mem.get("en", "")
    zh = mem.get("zh", "")
    ans = q.get("correct_answer", "")
    title = q.get("title", "")
    tag_m = re.match(r"(【[^】]+】)", title)
    tag = tag_m.group(1) if tag_m else ""

    expected_title, expected_ans = phrase_cloze_title_answer(en, zh, tag)
    if ans != expected_ans:
        add("phrase_cloze_answer", quiz_id, sort, f"{en!r} stored={ans!r} expected={expected_ans!r}")

    cloze_line = title.split("\n")[-1]
    if "请填写完整" not in cloze_line and "______" in cloze_line:
        filled = cloze_line.replace("______", ans)
        if re.sub(r"\s+", " ", filled).strip() != re.sub(r"\s+", " ", en).strip():
            add("phrase_cloze_fill", quiz_id, sort, f"{en!r} fill={filled!r}")

    hint = q.get("fill_hint") or mem.get("fill_hint") or ""
    if "按原样填写" in hint and "请填写完整" not in cloze_line:
        add("phrase_cloze_hint", quiz_id, sort, f"misleading hint: {hint!r}")

    if ans == en and "请填写完整" not in cloze_line and "..." not in en and "/" not in en:
        add("phrase_cloze_full_as_blank", quiz_id, sort, f"{en!r} answer equals full phrase but not full-blank title")

    if ans != en and "请填写完整" in cloze_line:
        add("phrase_cloze_partial_full_title", quiz_id, sort, f"{en!r} partial answer {ans!r} with full-blank title")


def audit_fill_general(quiz_id, q):
    sort = q.get("sort")
    mem = q.get("memory") or {}
    pattern = mem.get("pattern")
    en = mem.get("en", "")
    ans = q.get("correct_answer", "")

    if pattern in ("zh2en", "spell", "assoc") and mem.get("lang") == "en":
        if ans != en:
            add("en_pattern_answer", quiz_id, sort, f"{pattern} {en!r} answer={ans!r}")

    if pattern == "en2zh" and mem.get("lang") == "zh":
        if not ans:
            add("zh_empty", quiz_id, sort, "en2zh missing answer")

    if "______" in q.get("title", ""):
        # Only count standalone fill blanks, not assoc hints like com_____
        blanks = len(re.findall(r"(?<![_\w])______(?![_\w])", q.get("title", "")))
        if blanks > 1 and pattern != "phrase_cloze":
            add("multi_blank", quiz_id, sort, f"{pattern} has {blanks} blanks")

    if pattern and pattern not in {
        "zh2en", "en2zh", "spell", "pos", "assoc", "phrase_cloze"
    }:
        add("unknown_pattern", quiz_id, sort, pattern)


def main():
    manifest = json.loads((DATA / "manifest.json").read_text(encoding="utf-8"))
    stats = {"phrase_cloze": 0, "total_fill": 0}

    for entry in manifest["quizzes"]:
        if not str(entry.get("id", "")).endswith("_iwords"):
            continue
        path = DATA / entry["file"]
        quiz = json.loads(path.read_text(encoding="utf-8"))
        for q in quiz.get("questions", []):
            if "填空" not in q.get("type", ""):
                continue
            stats["total_fill"] += 1
            mem = q.get("memory") or {}
            if mem.get("pattern") == "phrase_cloze":
                stats["phrase_cloze"] += 1
                audit_phrase_cloze(entry["id"], q)
            audit_fill_general(entry["id"], q)

    print(f"Audited {stats['total_fill']} fill questions, {stats['phrase_cloze']} phrase_cloze")
    if not issues:
        print("OK: no issues found")
        return 0

    by_kind = {}
    for i in issues:
        by_kind.setdefault(i["kind"], []).append(i)

    print(f"ISSUES: {len(issues)}")
    for kind, items in sorted(by_kind.items()):
        print(f"\n[{kind}] x{len(items)}")
        for it in items[:8]:
            print(f"  {it['quiz_id']} Q{it['sort']}: {it['detail']}")
        if len(items) > 8:
            print(f"  ... and {len(items) - 8} more")
    return 1


if __name__ == "__main__":
    sys.exit(main())
