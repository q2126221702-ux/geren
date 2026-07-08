# -*- coding: utf-8 -*-
"""从 exam_plans_fixed.json 重新生成 A~G 期末卷 JSON。"""
from __future__ import annotations

import json
from pathlib import Path

from build_exam_supplement_fg import apply_slot_patch
from build_industrial_exam_100 import DATA, SCRIPTS, build_exam_json, load_all_questions, load_fixed_plans

SUPPLEMENT_LABELS = frozenset("FG")


def main() -> None:
    pool = apply_slot_patch(load_all_questions())
    plans = load_fixed_plans()
    if not plans or not all(lb in plans for lb in "ABCDEFG"):
        raise SystemExit("exam_plans_fixed.json 须含 A~G 七套方案")

    for label in "ABCDEFG":
        exam = build_exam_json(label, plans[label], pool)
        if label in SUPPLEMENT_LABELS:
            exam["meta"]["exam_role"] = "supplement"
            exam["meta"]["coverage_note"] = "补充卷：与 A~E 合并覆盖全部工业组卷题库"
        fname = f"工业网络技术期末考核_100分_{label}.json"
        (DATA / fname).write_text(json.dumps(exam, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"写入 {fname}")


if __name__ == "__main__":
    main()
