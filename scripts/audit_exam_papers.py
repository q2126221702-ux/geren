# -*- coding: utf-8 -*-
"""打印 7 套期末卷（A~G）组卷规则校验报告。"""
import json
from collections import Counter
from itertools import combinations
from pathlib import Path

from build_exam_supplement_fg import apply_slot_patch
from build_industrial_exam_100 import load_all_questions, validate_cross_paper, validate_plan

SCRIPTS = Path(__file__).resolve().parent
LABELS = "ABCDEFG"
MAIN_LABELS = "ABCDE"
SUPPLEMENT_LABELS = "FG"


def main():
    pool = apply_slot_patch(load_all_questions())
    plans_raw = json.loads((SCRIPTS / "exam_plans_fixed.json").read_text(encoding="utf-8"))
    plan_dict = {
        label: [(x["source"], x["sort"], x["score"], x["chapter"]) for x in items]
        for label, items in plans_raw.items()
    }

    print("=== 单套规则 ===")
    for label in LABELS:
        if label not in plan_dict:
            continue
        supplement = label in SUPPLEMENT_LABELS
        issues = validate_plan(label, plan_dict[label], pool, supplement=supplement)
        role = "（补充）" if supplement else ""
        status = "通过" if not issues else "未通过"
        print(f"试卷{label}{role}: {status}")
        for i in issues:
            print(f"  - {i}")

    all_keys = [(x[0], x[1]) for p in plan_dict.values() for x in p]
    heavy = [k for k, v in Counter(all_keys).items() if v > 5]
    if heavy:
        print(f"\n⚠ 有 {len(heavy)} 道题在 7 套卷中出现超过 5 次")
        for k in heavy:
            print(f"  - {k[0]} #{k[1]} × {Counter(all_keys)[k]}")
    else:
        used = set(all_keys)
        print(f"\n✓ 无超频题；去重覆盖 {len(used)}/{len(pool)}")

    print("\n=== 跨卷重复率（A~E 主卷）===")
    main_plans = {k: plan_dict[k] for k in MAIN_LABELS if k in plan_dict}
    for a, b in combinations(MAIN_LABELS, 2):
        sa = {(x[0], x[1]) for x in main_plans[a]}
        sb = {(x[0], x[1]) for x in main_plans[b]}
        ov = len(sa & sb)
        flag = "✓" if 0.20 <= ov / 44 <= 0.30 else "△"
        print(f"  {flag} {a}-{b}: {ov}题 ({ov/44:.1%})")

    cross = validate_cross_paper(main_plans)
    print("\n跨卷校验（A~E）:", "通过" if not cross else "；".join(cross))

    if all(lb in plan_dict for lb in SUPPLEMENT_LABELS):
        sae = {(x[0], x[1]) for lb in MAIN_LABELS for x in plan_dict[lb]}
        print("\n=== 补充卷 F/G ===")
        for label in SUPPLEMENT_LABELS:
            sf = {(x[0], x[1]) for x in plan_dict[label]}
            print(f"  {label} 相对 A~E 新增题: {len(sf - sae)}")


if __name__ == "__main__":
    main()
