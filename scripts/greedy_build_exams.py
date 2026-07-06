# -*- coding: utf-8 -*-
"""独立组卷 + 优选跨卷重复率（目标 20%~30%）。"""
from __future__ import annotations

import json
import random
from collections import Counter
from itertools import combinations
from pathlib import Path

from build_industrial_exam_100 import (
    build_section_slots,
    load_all_questions,
    validate_cross_paper,
    validate_plan,
)

SCRIPTS = Path(__file__).resolve().parent


def build_one(slots, pool, rng: random.Random) -> list:
    used: set = set()
    plan = []
    buckets: dict = {}
    for k, v in pool.items():
        buckets.setdefault((v["type"], v["chapter"], v["difficulty"]), []).append(k)
    for b in buckets.values():
        rng.shuffle(b)

    for slot in slots:
        key = (slot["type"], slot["chapter"], slot["difficulty"])
        cands = [k for k in buckets.get(key, []) if k not in used]
        if not cands:
            cands = [
                k
                for k, v in pool.items()
                if v["type"] == slot["type"] and v["chapter"] == slot["chapter"] and k not in used
            ]
            rng.shuffle(cands)
        if not cands:
            raise RuntimeError(f"无题 {slot}")
        pick = cands[0]
        used.add(pick)
        v = pool[pick]
        plan.append((v["source"], v["sort"], slot["score"], v["chapter"]))
    return plan


def build_five(slots, pool, seed: int) -> dict[str, list]:
    rng = random.Random(seed)
    global_used: Counter = Counter()
    plans = {}
    for i, label in enumerate("ABCDE"):
        # 全局使用次数少的题优先，降低后续试卷重复
        used: set = set()
        plan = []
        for slot in slots:
            key = (slot["type"], slot["chapter"], slot["difficulty"])
            cands = [
                k
                for k, v in pool.items()
                if (v["type"], v["chapter"], v["difficulty"]) == key and k not in used
            ]
            if not cands:
                cands = [
                    k
                    for k, v in pool.items()
                    if v["type"] == slot["type"] and v["chapter"] == slot["chapter"] and k not in used
                ]
            cands.sort(key=lambda k: (global_used[k], rng.random()))
            pick = cands[0]
            used.add(pick)
            global_used[pick] += 1
            v = pool[pick]
            plan.append((v["source"], v["sort"], slot["score"], v["chapter"]))
        plans[label] = plan
    return plans


def score_plans(plans: dict) -> tuple[int, float, int]:
    rates = []
    in_range = 0
    for a, b in combinations(plans.keys(), 2):
        sa = {(x[0], x[1]) for x in plans[a]}
        sb = {(x[0], x[1]) for x in plans[b]}
        r = len(sa & sb) / 44
        rates.append(r)
        if 0.20 <= r <= 0.30:
            in_range += 1
    avg = sum(rates) / len(rates) if rates else 0
    return in_range, avg, len(validate_cross_paper(plans))


def main():
    pool = load_all_questions()
    slots = build_section_slots()
    best = None
    best_key = (-1, -1.0)

    for attempt in range(12000):
        try:
            plans = build_five(slots, pool, seed=attempt + 20260706)
        except RuntimeError:
            continue
        if any(validate_plan(l, plans[l], pool) for l in plans):
            continue
        in_range, avg, cross_n = score_plans(plans)
        key = (in_range, -abs(avg - 0.25))
        if key > best_key:
            best_key = key
            best = plans
        if in_range == 10 and 0.22 <= avg <= 0.28:
            print(f"attempt {attempt} 完美匹配")
            best = plans
            break

    if best is None:
        raise SystemExit("组卷失败")

    in_range, avg, _ = score_plans(best)
    print(f"优选方案: {in_range}/10 对落在 20%~30%，平均重复率 {avg:.1%}")

    for label in "ABCDE":
        print(f"试卷{label}", dict(Counter(pool[(s, n)]["difficulty"] for s, n, _, _ in best[label])))
    for a, b in combinations("ABCDE", 2):
        sa = {(x[0], x[1]) for x in best[a]}
        sb = {(x[0], x[1]) for x in best[b]}
        print(f"  {a}-{b}: {len(sa & sb)} ({len(sa & sb)/44:.1%})")

    cross = validate_cross_paper(best)
    if cross:
        print("跨卷提示:", cross[:5], "..." if len(cross) > 5 else "")

    export = {
        label: [{"source": s, "sort": n, "score": sc, "chapter": c} for s, n, sc, c in plan]
        for label, plan in best.items()
    }
    (SCRIPTS / "exam_plans_fixed.json").write_text(json.dumps(export, ensure_ascii=False, indent=2), encoding="utf-8")
    print("已写入 exam_plans_fixed.json")


if __name__ == "__main__":
    main()
