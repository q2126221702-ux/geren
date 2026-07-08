# -*- coding: utf-8 -*-
"""打印 5 套期末卷组卷规则校验报告。"""
import json
from collections import Counter
from itertools import combinations
from pathlib import Path

from build_industrial_exam_100 import (
    DIFF_QUOTA,
    STRUCTURE,
    load_all_questions,
    validate_cross_paper,
    validate_plan,
)

SCRIPTS = Path(__file__).resolve().parent


def main():
    pool = load_all_questions()
    plans = json.loads((SCRIPTS / "exam_plans_fixed.json").read_text(encoding="utf-8"))
    print("=== 单套规则 ===")
    for label, items in plans.items():
        plan = [(x["source"], x["sort"], x["score"], x["chapter"]) for x in items]
        issues = validate_plan(label, plan, pool)
        status = "通过" if not issues else "未通过"
        print(f"试卷{label}: {status}")
        for i in issues:
            print(f"  - {i}")
    print("\n=== 跨卷重复率 ===")
    plan_dict = {
        label: [(x["source"], x["sort"], x["score"], x["chapter"]) for x in items]
        for label, items in plans.items()
    }
    for a, b in combinations("ABCDE", 2):
        sa = {(x[0], x[1]) for x in plan_dict[a]}
        sb = {(x[0], x[1]) for x in plan_dict[b]}
        print(f"  {a}-{b}: {len(sa & sb)}题 ({len(sa & sb)/44:.1%})")
    cross = validate_cross_paper(plan_dict)
    print("跨卷校验:", "通过" if not cross else cross)


if __name__ == "__main__":
    main()
