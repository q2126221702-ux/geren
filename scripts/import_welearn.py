"""将 WE Learn 整理格式转为 quiz-web 题库格式（按单元拆分）."""
import json
import re
from datetime import datetime
from pathlib import Path

SRC = Path(__file__).parent.parent.parent / "welearn-output" / "WE Learn_B1U4-U8_翻译题_20260703.json"
DATA_DIR = Path(__file__).parent.parent / "data"
MANIFEST = DATA_DIR / "manifest.json"
DATE_TAG = "20260703"

# 旧版合并题库（迁移后删除）
LEGACY_COMBINED = DATA_DIR / f"WE Learn_B1U4-U8_翻译题_{DATE_TAG}.json"
LEGACY_MANIFEST_ID = "welearn_b1u4_u8"


def unit_id(unit_name: str) -> str:
    m = re.match(r"(B1U\d+)", unit_name, re.I)
    return f"welearn_{m.group(1).lower()}" if m else re.sub(r"\W+", "_", unit_name.lower())


def unit_file_name(unit_name: str) -> str:
    safe = unit_name.replace(" ", "_")
    return f"WE Learn_{safe}_翻译题_{DATE_TAG}.json"


def convert_unit(unit: dict) -> dict:
    unit_name = unit["unit"]
    questions = []
    sort = 1
    for ex in unit.get("exercises", []):
        if ex.get("section") == "Translation F":
            for q in ex.get("questions", []):
                letter = q["correct_answer"]
                idx = {"A": 0, "B": 1, "C": 2, "D": 3}[letter]
                opt_text = q["options"][idx]
                questions.append(
                    {
                        "sort": sort,
                        "type": "单选题",
                        "title": f"【{unit_name} · 中译英】{q['prompt_zh']}",
                        "options": q["options"],
                        "correct_answer": f"{letter}. {opt_text}",
                        "your_answer": "",
                        "score": "0",
                        "full_score": 4.0,
                    }
                )
                sort += 1
        elif ex.get("section") == "Translation G":
            questions.append(
                {
                    "sort": sort,
                    "type": "问答题",
                    "title": f"【{unit_name} · 英译中】\n\n{ex['prompt_en']}",
                    "options": [],
                    "correct_answer": ex["answer_zh"],
                    "your_answer": "",
                    "score": "0",
                    "full_score": 0,
                }
            )
            sort += 1
    return {
        "title": f"WE Learn {unit_name} 翻译练习",
        "exported_at": datetime.now().isoformat(),
        "source": unit.get("source") or "WE Learn 截图整理",
        "unit": unit_name,
        "questions": questions,
    }


def update_manifest(entries: list[dict]):
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    remove_ids = {LEGACY_MANIFEST_ID} | {e["id"] for e in entries}
    manifest["quizzes"] = [q for q in manifest["quizzes"] if q.get("id") not in remove_ids]

    # 插在工业通信题库之后、保持 WE Learn 五单元连续
    insert_at = next(
        (i for i, q in enumerate(manifest["quizzes"]) if q.get("id") == "comprehensive"),
        len(manifest["quizzes"]) - 1,
    )
    insert_at += 1
    for i, entry in enumerate(entries):
        manifest["quizzes"].insert(insert_at + i, entry)

    MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")


def main():
    data = json.loads(SRC.read_text(encoding="utf-8"))
    manifest_entries = []

    for unit in data.get("units", []):
        quiz = convert_unit(unit)
        filename = unit_file_name(unit["unit"])
        out_path = DATA_DIR / filename
        out_path.write_text(json.dumps(quiz, ensure_ascii=False, indent=2), encoding="utf-8")
        manifest_entries.append(
            {
                "id": unit_id(unit["unit"]),
                "title": quiz["title"],
                "file": filename,
                "count": len(quiz["questions"]),
            }
        )
        print(f"  {filename} — {len(quiz['questions'])} 题")

    update_manifest(manifest_entries)

    if LEGACY_COMBINED.exists():
        LEGACY_COMBINED.unlink()
        print(f"已删除合并题库 {LEGACY_COMBINED.name}")

    print(f"共 {len(manifest_entries)} 个单元，已更新 {MANIFEST}")


if __name__ == "__main__":
    main()
