# -*- coding: utf-8 -*-
"""Parse comprehensive exam docx text into quiz JSON."""
import json
import re
import zipfile
import xml.etree.ElementTree as ET
from datetime import datetime
from pathlib import Path

W = "{http://schemas.openxmlformats.org/wordprocessingml/2006/main}"


def extract_paragraphs(docx_path: Path) -> list[str]:
    with zipfile.ZipFile(docx_path) as zf:
        root = ET.fromstring(zf.read("word/document.xml"))
    paras = []
    for p in root.iter(W + "p"):
        text = "".join(node.text or "" for node in p.iter(W + "t"))
        if text.strip():
            paras.append(text.strip())
    return paras


def parse_choice_answers(line: str) -> dict[int, str]:
    line = re.sub(r"^[^(：:]+[：:]", "", line)
    answers = {}
    for num, letter in re.findall(r"(\d+)\.([A-D])", line):
        answers[int(num)] = letter
    return answers


def parse_judgment_answers(line: str) -> dict[int, str]:
    line = re.sub(r"^[^(：:]+[：:]", "", line)
    answers = {}
    for num, mark in re.findall(r"(\d+)([×√xX✓])", line):
        # 0 = 正确(A), 1 = 错误(B)
        answers[int(num)] = "0" if mark in "√✓" else "1"
    return answers


def split_options(line: str) -> list[str]:
    parts = re.split(r"\s+(?=[A-D]\.)", line.strip())
    options = []
    for part in parts:
        m = re.match(r"^[A-D]\.\s*(.*)$", part.strip())
        if m:
            options.append(m.group(1).strip())
    return options


def parse_single_choice(paras: list[str], start: int, end: int, answer_line: str) -> list[dict]:
    answers = parse_choice_answers(answer_line)
    questions = []
    i = start
    q_num = 0
    while i < end:
        m = re.match(r"^(\d+)[、,](.+)$", paras[i])
        if m:
            q_num = int(m.group(1))
            title = m.group(2).strip()
            options = []
            i += 1
            if i < end and re.search(r"[A-D]\.", paras[i]):
                options = split_options(paras[i])
                i += 1
            letter = answers.get(q_num, "A")
            opt_text = options[ord(letter) - ord("A")] if letter and len(options) >= ord(letter) - ord("A") + 1 else ""
            questions.append(
                {
                    "sort": q_num,
                    "type": "单选题",
                    "title": title,
                    "options": options,
                    "correct_answer": f"{letter}. {opt_text}" if opt_text else letter,
                    "full_score": 2.0,
                }
            )
            continue
        i += 1
    return questions


def parse_fill_answers(line: str) -> list[str]:
    line = re.sub(r"^[^(：:]+[：:]", "", line)
    line = re.sub(r"（[^）]*）$", "", line).strip()
    m0 = re.search(r"\d+\.", line)
    if m0:
        line = line[m0.start():]
    parts = re.split(r"\s+(?=\d+\.)", line)
    ordered = []
    for part in parts:
        m = re.match(r"(\d+)\.(.*)", part.strip(), re.S)
        if m:
            ordered.append(m.group(2).strip())
    return ordered


def parse_fill_blank(paras: list[str], start: int, end: int, answer_line: str) -> list[dict]:
    answer_list = parse_fill_answers(answer_line)

    questions = []
    i = start
    while i < end:
        m = re.match(r"^(\d+)[、,](.+)$", paras[i])
        if m and "________" in m.group(2):
            q_num = int(m.group(1))
            questions.append(
                {
                    "local_num": q_num,
                    "sort": 50 + q_num,
                    "type": "填空题(客观)",
                    "title": m.group(2).strip(),
                    "options": [""],
                    "correct_answer": "",
                    "full_score": 2.0,
                }
            )
        i += 1

    for idx, q in enumerate(questions):
        if idx < len(answer_list):
            q["correct_answer"] = answer_list[idx]
        del q["local_num"]

    return questions


def parse_judgment(paras: list[str], start: int, end: int, answer_line: str) -> list[dict]:
    answers = parse_judgment_answers(answer_line)
    questions = []
    i = start
    while i < end:
        m = re.match(r"^(\d+)[、,](.+（）)$", paras[i])
        if m:
            q_num = int(m.group(1))
            questions.append(
                {
                    "sort": 100 + q_num,
                    "type": "判断题",
                    "title": m.group(2).strip(),
                    "options": ["正确", "错误"],
                    "correct_answer": answers.get(q_num, "0"),
                    "full_score": 2.0,
                }
            )
        i += 1
    return questions


def parse_essay(paras: list[str], start: int, end: int) -> list[dict]:
    questions = []
    i = start
    while i < end:
        m = re.match(r"^(\d+)[、,](.+)$", paras[i])
        if m and not paras[i].startswith("http"):
            q_num = int(m.group(1))
            questions.append(
                {
                    "sort": 130 + q_num,
                    "type": "问答题",
                    "title": m.group(2).strip(),
                    "options": [],
                    "correct_answer": "",
                    "full_score": 0,
                }
            )
        i += 1

    ref_start = end
    ref_parts = paras[ref_start:]
    ref_text = "\n".join(ref_parts)
    for q in questions:
        num = q["sort"] - 130
        m = re.search(rf"(?:^|\n){num}[、.](.+?)(?=\n\d+[、.]|$)", ref_text, re.S)
        if m:
            q["correct_answer"] = re.sub(r"\s+", " ", m.group(1).strip())

    return questions


def parse_programming(paras: list[str], start: int) -> list[dict]:
    questions = []
    i = start
    q_num = 0
    while i < len(paras):
        m = re.match(r"^(\d+)[、,](.+)$", paras[i])
        if m and ("编程" in m.group(2) or "程序" in m.group(2) or "PLC" in m.group(2)):
            q_num = int(m.group(1))
            title = m.group(2).strip()
            ref = ""
            if i + 1 < len(paras):
                ref = paras[i + 1]
            questions.append(
                {
                    "sort": 140 + q_num,
                    "type": "问答题",
                    "title": title,
                    "options": [],
                    "correct_answer": ref,
                    "full_score": 0,
                }
            )
        i += 1
    return questions


def find_index(paras: list[str], pattern: str, start: int = 0) -> int:
    for i in range(start, len(paras)):
        if re.search(pattern, paras[i]):
            return i
    return -1


def find_section(paras: list[str], section_name: str) -> int:
    return find_index(paras, re.escape(section_name))


def main():
    base = Path(r"c:\Users\shen\Desktop\新建文件夹 (2)")
    docx = base / "output" / "网络及工业通信综合考核题库（全套145题）.docx"
    out_dir = base / "quiz-web" / "data"
    paras = extract_paragraphs(docx)

    part1 = find_section(paras, "第一部分")
    part2 = find_section(paras, "第二部分")
    part3 = find_section(paras, "第三部分")
    part4 = find_section(paras, "第四部分")
    part5 = find_section(paras, "第五部分")

    idx_single_start = find_index(paras, r"^1[、,].*（）$", part1)
    idx_single_ans = find_index(paras, r"^单选题答案", part1)
    idx_fill_start = find_index(paras, r"^1[、,].*________", part2)
    idx_fill_ans = find_index(paras, r"^填空题标准答案", part2)
    idx_judge_start = find_index(paras, r"^1[、,].*（）$", part3)
    idx_judge_ans = find_index(paras, r"^判断题答案", part3)
    idx_essay_start = find_index(paras, r"^1[、,]", part4)
    idx_essay_ans = find_index(paras, r"^问答题参考答案", part4)
    idx_prog_start = part5
    idx_essay_end = idx_essay_ans if idx_essay_ans > 0 else part5

    single = parse_single_choice(paras, idx_single_start, idx_single_ans, paras[idx_single_ans])
    fill = parse_fill_blank(paras, idx_fill_start, idx_fill_ans, paras[idx_fill_ans])
    judge = parse_judgment(paras, idx_judge_start, idx_judge_ans, paras[idx_judge_ans])
    essay = parse_essay(paras, idx_essay_start, idx_essay_end)
    prog = parse_programming(paras, idx_prog_start + 1)

    questions = single + fill + judge + essay + prog
    questions.sort(key=lambda q: q["sort"])

    quiz = {
        "title": "网络及工业通信综合考核",
        "exported_at": datetime.now().isoformat(),
        "questions": questions,
    }

    filename = "网络及工业通信综合考核_145题.json"
    out_path = out_dir / filename
    out_path.write_text(json.dumps(quiz, ensure_ascii=False, indent=2), encoding="utf-8")

    manifest_path = out_dir / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    entry = {
        "id": "comprehensive",
        "title": "网络及工业通信综合考核",
        "file": filename,
        "count": len(questions),
    }
    if not any(q["id"] == "comprehensive" for q in manifest["quizzes"]):
        manifest["quizzes"].append(entry)
    else:
        manifest["quizzes"] = [entry if q["id"] == "comprehensive" else q for q in manifest["quizzes"]]
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"Generated {len(questions)} questions (docx header says 145, source has {len(questions)} parseable items)")
    print(f"  单选题: {len(single)}")
    print(f"  填空题: {len(fill)}")
    print(f"  判断题: {len(judge)}")
    print(f"  问答题: {len(essay)}")
    print(f"  程序设计: {len(prog)}")
    missing_fill = [q for q in fill if not q["correct_answer"]]
    if missing_fill:
        print(f"  WARNING: {len(missing_fill)} fill questions missing answers")
        for q in missing_fill[:5]:
            print(f"    - {q['sort']}: {q['title'][:40]}")


if __name__ == "__main__":
    main()
