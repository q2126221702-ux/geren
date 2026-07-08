# -*- coding: utf-8 -*-
"""分散 comprehensive#43，避免在 7 套卷中重复超过 5 次。"""
from __future__ import annotations

import json
from pathlib import Path

from build_exam_supplement_fg import apply_slot_patch
from build_industrial_exam_100 import load_all_questions, validate_plan

SCRIPTS = Path(__file__).resolve().parent
PLANS_PATH = SCRIPTS / "exam_plans_fixed.json"

# A/B/C 保留 comp43；D~G 改用其他 OPC 单选「难」
SWAPS = {
    "D": {14: ("opc", 9), 19: ("opc", 1)},
    "E": {19: ("opc", 6)},
    "F": {14: ("opc", 2), 19: ("opc", 7)},
    "G": {14: ("opc", 5), 19: ("comprehensive", 44)},
}


def main() -> None:
    plans = json.loads(PLANS_PATH.read_text(encoding="utf-8"))
    pool = apply_slot_patch(load_all_questions())

    for label, idx_map in SWAPS.items():
        items = plans[label]
        for idx, key in idx_map.items():
            src, sort_num = key
            items[idx] = {
                "source": src,
                "sort": sort_num,
                "score": 2.0,
                "chapter": "OPC",
            }

    for label in "ABCDEFG":
        plan = [(x["source"], x["sort"], x["score"], x["chapter"]) for x in plans[label]]
        supplement = label in "FG"
        issues = validate_plan(label, plan, pool, supplement=supplement)
        if issues:
            raise SystemExit(f"试卷{label} 校验失败: {issues}")
        keys = [(x["source"], x["sort"]) for x in plans[label]]
        if len(keys) != len(set(keys)):
            raise SystemExit(f"试卷{label} 卷内重复")

    PLANS_PATH.write_text(json.dumps(plans, ensure_ascii=False, indent=2), encoding="utf-8")
    print("已更新 exam_plans_fixed.json")


if __name__ == "__main__":
    main()
