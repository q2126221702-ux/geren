"""将 WE Learn 整理格式转为 quiz-web 题库格式."""
import json
from datetime import datetime
from pathlib import Path

SRC = Path(__file__).parent.parent.parent / "welearn-output" / "WE Learn_B1U4-U8_翻译题_20260703.json"
OUT = Path(__file__).parent.parent / "data" / "WE Learn_B1U4-U8_翻译题_20260703.json"
MANIFEST = Path(__file__).parent.parent / "data" / "manifest.json"


def convert(data: dict) -> dict:
    questions = []
    sort = 1
    for unit in data.get("units", []):
        unit_name = unit["unit"]
        for ex in unit.get("exercises", []):
            if ex.get("section") == "Translation F":
                for q in ex.get("questions", []):
                    letter = q["correct_answer"]
                    idx = {"A": 0, "B": 1, "C": 2, "D": 3}[letter]
                    opt_text = q["options"][idx]
                    questions.append({
                        "sort": sort,
                        "type": "单选题",
                        "title": f"【{unit_name} · 中译英】{q['prompt_zh']}",
                        "options": q["options"],
                        "correct_answer": f"{letter}. {opt_text}",
                        "your_answer": "",
                        "score": "0",
                        "full_score": 4.0,
                    })
                    sort += 1
            elif ex.get("section") == "Translation G":
                questions.append({
                    "sort": sort,
                    "type": "问答题",
                    "title": f"【{unit_name} · 英译中】\n\n{ex['prompt_en']}",
                    "options": [],
                    "correct_answer": ex["answer_zh"],
                    "your_answer": "",
                    "score": "0",
                    "full_score": 0,
                })
                sort += 1
    return {
        "title": "WE Learn 实用综合教程 B1U4-U8 翻译练习",
        "exported_at": datetime.now().isoformat(),
        "source": data.get("source", "WE Learn 截图整理"),
        "questions": questions,
    }


def update_manifest(filename: str, title: str, count: int):
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    entry = {
        "id": "welearn_b1u4_u8",
        "title": title,
        "file": filename,
        "count": count,
    }
    manifest["quizzes"] = [q for q in manifest["quizzes"] if q.get("id") != entry["id"]]
    manifest["quizzes"].append(entry)
    MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")


def main():
    data = json.loads(SRC.read_text(encoding="utf-8"))
    quiz = convert(data)
    OUT.write_text(json.dumps(quiz, ensure_ascii=False, indent=2), encoding="utf-8")
    update_manifest(OUT.name, quiz["title"], len(quiz["questions"]))
    print(f"已写入 {OUT}，共 {len(quiz['questions'])} 题")
    print(f"已更新 {MANIFEST}")


if __name__ == "__main__":
    main()
