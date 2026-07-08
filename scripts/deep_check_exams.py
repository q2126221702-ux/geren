# -*- coding: utf-8 -*-
"""深度检查 5 套期末卷 JSON 与组卷方案。"""
import json
from collections import Counter
from pathlib import Path

from build_industrial_exam_100 import load_all_questions, validate_cross_paper, validate_plan

DATA = Path(__file__).resolve().parent.parent / "data"
SCRIPTS = Path(__file__).resolve().parent


def main():
    pool = load_all_questions()
    plans_raw = json.loads((SCRIPTS / "exam_plans_fixed.json").read_text(encoding="utf-8"))
    plans = {
        label: [(x["source"], x["sort"], x["score"], x["chapter"]) for x in items]
        for label, items in plans_raw.items()
    }
    issues: list[str] = []

    for label in "ABCDE":
        fp = DATA / f"工业网络技术期末考核_100分_{label}.json"
        exam = json.loads(fp.read_text(encoding="utf-8"))
        if len(exam["questions"]) != 44:
            issues.append(f"{label} JSON 题量 {len(exam['questions'])}")
        total = sum(q["full_score"] for q in exam["questions"])
        if abs(total - 100) > 0.01:
            issues.append(f"{label} JSON 总分 {total}")
        for i, q in enumerate(exam["questions"], 1):
            src, sort, _, _ = plans[label][i - 1]
            raw = pool[(src, sort)]["raw"]
            if q["title"] != raw["title"]:
                issues.append(f"{label} Q{i} 标题与题库不一致")
            if q["correct_answer"] != raw["correct_answer"]:
                issues.append(f"{label} Q{i} 答案与题库不一致")
            if q["type"] == "单选题" and not q["correct_answer"][:2].startswith(
                ("A.", "B.", "C.", "D.")
            ):
                issues.append(f"{label} Q{i} 单选答案格式异常")
            if q["type"] == "判断题" and str(q["correct_answer"]).strip() not in ("0", "1"):
                issues.append(f"{label} Q{i} 判断答案异常: {q['correct_answer']}")

        types = [q["type"] for q in exam["questions"]]
        if types[-4:] != ["问答题"] * 4:
            issues.append(f"{label} 问答题未在卷末")
        for q in exam["questions"]:
            if "CANopen" in q["title"] or "ThingsBoard" in q["title"]:
                issues.append(f"{label} 超纲题")
            if "PLC" in q["title"] and "Modbus RTU读取" in q["title"]:
                issues.append(f"{label} 含禁用 PLC 编程题")

        chs = [pool[(s, n)]["chapter"] for s, n, _, _ in plans[label][:40]]
        run = 1
        for i in range(1, len(chs)):
            if chs[i] == chs[i - 1]:
                run += 1
                if run >= 3:
                    issues.append(f"{label} 第{i+1}题 {chs[i]} 连续≥3题")
            else:
                run = 1

    all_keys = [(x[0], x[1]) for p in plans.values() for x in p]
    heavy = [k for k, v in Counter(all_keys).items() if v > 3]
    if heavy:
        issues.append(f"有 {len(heavy)} 道题在 5 套卷中出现超过 3 次")

    cross = validate_cross_paper(plans)

    print("=== 深度检查 ===")
    if issues:
        for i in issues:
            print("✗", i)
    else:
        print("✓ 数据完整性、答案格式、卷末问答、超纲筛查：通过")

    print("\n=== 组卷规则（单套）===")
    for label in "ABCDE":
        iss = validate_plan(label, plans[label], pool)
        print(f"试卷{label}:", "通过" if not iss else iss)

    print("\n=== 跨卷重复率 ===")
    for a, b in __import__("itertools").combinations("ABCDE", 2):
        sa = {(x[0], x[1]) for x in plans[a]}
        sb = {(x[0], x[1]) for x in plans[b]}
        ov = len(sa & sb)
        flag = "✓" if 0.20 <= ov / 44 <= 0.30 else "△"
        print(f"  {flag} {a}-{b}: {ov}题 ({ov/44:.1%})")
    if cross:
        print("跨卷提示:", "; ".join(cross))

    print("\n=== 问答题一览 ===")
    for label in "ABCDE":
        titles = [
            pool[(s, n)]["title"][:50]
            for s, n, _, _ in plans[label]
            if pool[(s, n)]["type"] == "问答题"
        ]
        print(f"试卷{label}:")
        for t in titles:
            print(f"  · {t}")


if __name__ == "__main__":
    main()
