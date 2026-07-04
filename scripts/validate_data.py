"""校验 manifest 与题库 JSON 一致性."""
import json
from pathlib import Path

DATA = Path(__file__).parent.parent / "data"
manifest = json.loads((DATA / "manifest.json").read_text(encoding="utf-8"))
issues = []

for q in manifest["quizzes"]:
    fp = DATA / q["file"]
    if not fp.exists():
        issues.append(f"missing file: {q['file']}")
        continue
    data = json.loads(fp.read_text(encoding="utf-8"))
    actual = len(data.get("questions", []))
    if actual != q["count"]:
        issues.append(f"{q['id']}: manifest count {q['count']} != actual {actual}")
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

welearn = [q for q in manifest["quizzes"] if q["id"].startswith("welearn_")]
print(f"Manifest quizzes: {len(manifest['quizzes'])}")
print(f"WE Learn units: {len(welearn)}")
if issues:
    print("ISSUES:")
    for i in issues:
        print(" -", i)
    raise SystemExit(1)
print("All manifest/data checks OK")
