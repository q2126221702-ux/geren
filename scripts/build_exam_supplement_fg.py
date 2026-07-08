# -*- coding: utf-8 -*-
"""
期末卷 F/G 补充组卷（与 A~E 不同策略）

借鉴 ATA（Automated Test Assembly）：
  - 蓝图约束：题型/章节/难度配额与 A~E 相同
  - 覆盖目标：A~E 未出现的题在 F/G 中各出现一次，两卷间按题型+章节均衡
  - 使用约束：填充题优先全局使用次数低的题（非重复率优化）
"""
from __future__ import annotations

import json
import random
from collections import Counter, defaultdict
from copy import deepcopy

from build_industrial_exam_100 import (
    DATA,
    SCRIPTS,
    build_exam_json,
    build_section_slots,
    load_all_questions,
    load_fixed_plans,
    validate_plan,
)
from exam_pool_meta import EASY, HARD, MEDIUM

SUPPLEMENT_SLOT_PATCH: dict[tuple[str, int], dict] = {
    ("comprehensive", 83): {"difficulty": EASY},
    ("comprehensive", 84): {"difficulty": EASY},
    ("comprehensive", 85): {"difficulty": EASY},
    ("comprehensive", 86): {"difficulty": EASY},
    ("comprehensive", 98): {"difficulty": EASY},
    ("comprehensive", 99): {"difficulty": EASY},
    ("comprehensive", 100): {"difficulty": EASY},
    ("comprehensive", 104): {"difficulty": EASY},
    ("comprehensive", 107): {"difficulty": EASY},
    ("comprehensive", 109): {"difficulty": EASY},
    ("comprehensive", 110): {"difficulty": EASY},
    ("comprehensive", 129): {"difficulty": EASY},
    ("comprehensive", 132): {"difficulty": HARD},
    ("comprehensive", 133): {"chapter": "Modbus", "difficulty": HARD},
    ("comprehensive", 135): {"difficulty": MEDIUM},
    ("comprehensive", 139): {"chapter": "OPC", "difficulty": MEDIUM},
    ("comprehensive", 140): {"chapter": "串口", "difficulty": MEDIUM},
    ("modbus", 15): {"difficulty": MEDIUM},
    ("opc", 24): {"difficulty": MEDIUM},
    ("profinet", 13): {"difficulty": EASY},
    ("profinet", 17): {"difficulty": EASY},
    ("profinet", 18): {"difficulty": EASY},
    ("profinet", 19): {"difficulty": EASY},
    ("profinet", 22): {"difficulty": HARD},
    ("profinet", 23): {"difficulty": HARD},
    ("serial", 15): {"difficulty": MEDIUM},
}


def apply_slot_patch(pool: dict) -> dict:
    patched = deepcopy(pool)
    for key, patch in SUPPLEMENT_SLOT_PATCH.items():
        if key in patched:
            patched[key].update(patch)
    return patched


def slot_key(slot: dict) -> tuple:
    return (slot["type"], slot["chapter"], slot["difficulty"])


def item_slot_key(pool: dict, key: tuple[str, int]) -> tuple:
    item = pool[key]
    return (item["type"], item["chapter"], item["difficulty"])


def keys_used_in_plans(plans: dict) -> set[tuple[str, int]]:
    used: set[tuple[str, int]] = set()
    for plan in plans.values():
        used.update((s, n) for s, n, _, _ in plan)
    return used


def global_usage_counter(plans: dict) -> Counter:
    cnt: Counter = Counter()
    for plan in plans.values():
        cnt.update((s, n) for s, n, _, _ in plan)
    return cnt


def rebalance_uncovered_slots(
    pool: dict,
    uncovered: list[tuple[str, int]],
    slots: list[dict],
) -> None:
    """将未覆盖题重映射到两卷合计仍有空位的槽位桶（同题型内调整章节/难度）。"""
    cap = Counter(slot_key(s) for s in slots)
    max_both = {sk: cap[sk] * 2 for sk in cap}
    assigned = Counter(item_slot_key(pool, k) for k in uncovered)

    slot_catalog: dict[str, list[tuple]] = defaultdict(list)
    for sk in cap:
        slot_catalog[sk[0]].append(sk)

    def find_target(qtype: str, avoid: tuple) -> tuple | None:
        best = None
        best_room = -1
        for sk in slot_catalog[qtype]:
            if sk == avoid:
                continue
            room = max_both[sk] - assigned[sk]
            if room > best_room:
                best_room = room
                best = sk
        return best if best_room > 0 else None

    changed = True
    while changed:
        changed = False
        for sk in sorted(assigned.keys(), key=lambda x: assigned[x] - max_both[x], reverse=True):
            while assigned[sk] > max_both[sk]:
                qtype = sk[0]
                for key in uncovered:
                    if item_slot_key(pool, key) != sk:
                        continue
                    target = find_target(qtype, sk)
                    if not target:
                        break
                    pool[key]["chapter"] = target[1]
                    pool[key]["difficulty"] = target[2]
                    assigned[sk] -= 1
                    assigned[target] += 1
                    changed = True
                    break
                else:
                    break


def partition_uncovered(
    uncovered: list[tuple[str, int]],
    pool: dict,
    slots: list[dict],
    labels: tuple[str, ...] = ("F", "G"),
) -> dict[str, set[tuple[str, int]]] | None:
    """在槽位容量约束下将未覆盖题均衡分到 F/G。"""
    cap = Counter(slot_key(s) for s in slots)
    keys = list(uncovered)
    keys.sort(key=lambda k: len([x for x in keys if item_slot_key(pool, x) == item_slot_key(pool, k)]))
    assign: dict[tuple[str, int], str] = {}
    per_label = Counter({lb: 0 for lb in labels})
    per_label_sk: dict[str, Counter] = {lb: Counter() for lb in labels}

    def bt(i: int) -> bool:
        if i == len(keys):
            if abs(per_label[labels[0]] - per_label[labels[1]]) > 4:
                return False
            return True
        key = keys[i]
        sk = item_slot_key(pool, key)
        order = list(labels)
        order.sort(key=lambda lb: (per_label[lb], per_label_sk[lb][sk]))
        for lb in order:
            if per_label_sk[lb][sk] >= cap[sk]:
                continue
            assign[key] = lb
            per_label[lb] += 1
            per_label_sk[lb][sk] += 1
            if bt(i + 1):
                return True
            per_label_sk[lb][sk] -= 1
            per_label[lb] -= 1
            del assign[key]
        return False

    if not bt(0):
        return None
    groups = {lb: set() for lb in labels}
    for key, lb in assign.items():
        groups[lb].add(key)
    return groups


def assign_mandatory_to_slots(
    slots: list[dict],
    pool: dict,
    mandatory: set[tuple[str, int]],
    rng: random.Random,
) -> dict[int, tuple[str, int]] | None:
    keys = list(mandatory)
    options: dict[tuple[str, int], list[int]] = {}
    for key in keys:
        sk = item_slot_key(pool, key)
        options[key] = [i for i, s in enumerate(slots) if slot_key(s) == sk]
        if not options[key]:
            return None
    keys.sort(key=lambda k: len(options[k]))
    assignment: dict[int, tuple[str, int]] = {}
    used_slots: set[int] = set()

    def bt(i: int) -> bool:
        if i == len(keys):
            return True
        key = keys[i]
        opts = options[key][:]
        rng.shuffle(opts)
        for si in opts:
            if si in used_slots:
                continue
            assignment[si] = key
            used_slots.add(si)
            if bt(i + 1):
                return True
            used_slots.remove(si)
            del assignment[si]
        return False

    return assignment if bt(0) else None


def fill_remaining_slots(
    slots: list[dict],
    pool: dict,
    fixed: dict[int, tuple[str, int]],
    usage: Counter,
    seed: int,
) -> list[tuple[str, int, float, str]] | None:
    free = [(i, slots[i]) for i in range(len(slots)) if i not in fixed]
    used = set(fixed.values())
    rng = random.Random(seed)

    def rank_key(k: tuple[str, int]) -> tuple:
        return (usage.get(k, 0), rng.random())

    def solve_sub(idx: int, used_in_paper: set, tail: list) -> bool:
        if idx == len(free):
            return True
        _, slot = free[idx]
        sk = slot_key(slot)
        cands = [
            k
            for k, item in pool.items()
            if k not in used_in_paper and item_slot_key(pool, k) == sk
        ]
        cands.sort(key=rank_key)
        for key in cands[:50]:
            used_in_paper.add(key)
            item = pool[key]
            tail.append((item["source"], item["sort"], slot["score"], item["chapter"]))
            if solve_sub(idx + 1, used_in_paper, tail):
                return True
            tail.pop()
            used_in_paper.remove(key)
        return False

    for attempt in range(500):
        tail: list = []
        if solve_sub(0, set(used), tail):
            full = []
            ti = 0
            for i, slot in enumerate(slots):
                if i in fixed:
                    key = fixed[i]
                    item = pool[key]
                    full.append((item["source"], item["sort"], slot["score"], item["chapter"]))
                else:
                    full.append(tail[ti])
                    ti += 1
            return full
    return None


def build_supplement_paper(
    label: str,
    slots: list[dict],
    pool: dict,
    mandatory: set[tuple[str, int]],
    usage: Counter,
    seed: int = 0,
) -> list[tuple[str, int, float, str]] | None:
    for attempt in range(300):
        fixed = assign_mandatory_to_slots(
            slots, pool, mandatory, random.Random(seed + attempt)
        )
        if not fixed or set(fixed.values()) != mandatory:
            continue
        plan = fill_remaining_slots(slots, pool, fixed, usage, seed + attempt * 17)
        if plan and not validate_plan(label, plan, pool, supplement=True):
            return plan
    return None


def coverage_report(plans: dict, pool: dict) -> dict:
    used = keys_used_in_plans(plans)
    missing = set(pool.keys()) - used
    return {"pool": len(pool), "covered": len(used), "missing": len(missing), "missing_keys": sorted(missing)}


def main() -> None:
    base_pool = load_all_questions()
    pool = apply_slot_patch(base_pool)
    slots = build_section_slots()
    all_fixed = load_fixed_plans()
    if not all_fixed:
        raise RuntimeError("需要已有 exam_plans_fixed.json（A~E）")
    plans = {lb: all_fixed[lb] for lb in "ABCDE" if lb in all_fixed}
    if len(plans) != 5:
        raise RuntimeError("exam_plans_fixed.json 须含 A~E 五套主卷")

    usage = global_usage_counter(plans)
    uncovered = sorted(set(pool.keys()) - keys_used_in_plans(plans))
    print(f"题库池 {len(pool)} 题，A~E 已覆盖 {len(pool)-len(uncovered)}，待补充 {len(uncovered)} 题")

    rebalance_uncovered_slots(pool, uncovered, slots)
    split = partition_uncovered(uncovered, pool, slots)
    if not split:
        raise RuntimeError("无法在 F/G 间均衡分配未覆盖题（槽位容量不足）")

    for lb in "FG":
        items = split[lb]
        by_type = Counter(pool[k]["type"] for k in items)
        by_ch = Counter(pool[k]["chapter"] for k in items)
        print(f"  卷{lb} 分配未覆盖题 {len(items)}：题型 {dict(by_type)} 章节 {dict(by_ch)}")

    new_plans: dict[str, list] = {}
    for i, label in enumerate("FG"):
        mandatory = split[label]
        plan = build_supplement_paper(label, slots, pool, mandatory, usage, seed=20260708 + i * 1000)
        if not plan:
            raise RuntimeError(f"试卷{label} 补充组卷失败")
        new_plans[label] = plan
        usage.update((s, n) for s, n, _, _ in plan)
        print(f"试卷{label}: 通过校验")

    all_plans = {**plans, **new_plans}
    export = {
        lb: [{"source": s, "sort": n, "score": sc, "chapter": c} for s, n, sc, c in plan]
        for lb, plan in all_plans.items()
    }
    (SCRIPTS / "exam_plans_fixed.json").write_text(
        json.dumps(export, ensure_ascii=False, indent=2), encoding="utf-8"
    )

    rep = coverage_report(all_plans, pool)
    print(f"\n全覆盖: {rep['covered']}/{rep['pool']} ({rep['covered']/rep['pool']*100:.1f}%)")
    if rep["missing"]:
        for k in rep["missing_keys"]:
            print(f"  未覆盖 {k}")
        raise RuntimeError("未实现全覆盖")

    for label in "FG":
        exam = build_exam_json(label, new_plans[label], pool)
        exam["meta"]["exam_role"] = "supplement"
        exam["meta"]["coverage_note"] = "补充卷：与 A~E 合并覆盖全部工业组卷题库"
        fname = f"工业网络技术期末考核_100分_{label}.json"
        (DATA / fname).write_text(json.dumps(exam, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"  写入 {fname}")
    print("完成")


if __name__ == "__main__":
    main()
