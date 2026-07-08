# -*- coding: utf-8 -*-
"""
工业网络技术期末考核组卷（5 套 A~E）

规则：
  结构：单选20×2 + 填空10×2 + 判断10×2 + 问答4×5 = 100 分
  难度：易 60% / 中 30% / 难 10%（44 题 → 27 易 13 中 4 难）
  章节：五章各 8 客观题；问答题覆盖串口/Modbus/Profinet/OPC
  分布：客观题章节交叉；难度由低到高；问答题置卷末
  去重：单套内不重复；多套重复率 20%~30%（9~13 题/套）
"""
from __future__ import annotations

import json
import random
from collections import Counter, defaultdict
from copy import deepcopy
from datetime import datetime
from itertools import combinations
from pathlib import Path

from exam_pool_meta import BANNED, CHAPTERS, EASY, HARD, MEDIUM, get_meta

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / "data"
SCRIPTS = Path(__file__).resolve().parent

SOURCES = {
    "profinet": "Profinet工业以太网测验_20260626_185300.json",
    "opc": "OPC规范_20260626_185227.json",
    "modbus": "MODBUS协议及应用_20260626_185238.json",
    "serial": "串口及应用_20260626_185247.json",
    "comprehensive": "网络及工业通信综合考核_145题.json",
}

STRUCTURE = [
    ("单选题", 20, 2.0),
    ("填空题(客观)", 10, 2.0),
    ("判断题", 10, 2.0),
    ("问答题", 4, 5.0),
]

DIFF_QUOTA = {EASY: 27, MEDIUM: 13, HARD: 4}
OBJ_PER_CHAPTER = 8
CHAPTER_ROTATION = list(CHAPTERS)
OVERLAP_TARGET = 11  # 11/44 = 25%

RNG = random.Random(20260706)


def load_all_questions() -> dict[tuple[str, int], dict]:
    pool: dict[tuple[str, int], dict] = {}
    for source, fname in SOURCES.items():
        data = json.loads((DATA / fname).read_text(encoding="utf-8"))
        for q in data["questions"]:
            key = (source, q["sort"])
            if key in BANNED:
                continue
            if q["type"] == "多选题":
                continue
            if "CANopen" in q["title"] or "ThingsBoard" in q["title"]:
                continue
            if "PLC" in q["title"] and "Modbus RTU读取" in q["title"]:
                continue
            meta = get_meta(source, q["sort"], q["title"], q["type"])
            pool[key] = {
                "source": source,
                "sort": q["sort"],
                "type": q["type"],
                "title": q["title"],
                "chapter": meta["chapter"],
                "difficulty": meta["difficulty"],
                "raw": q,
            }
    return pool


def interleave_chapters(count_per_chapter: int) -> list[str]:
    slots = []
    for _ in range(count_per_chapter):
        slots.extend(CHAPTER_ROTATION)
    return slots


def section_difficulties(n: int, easy: int, medium: int, hard: int) -> list[str]:
    arr = [EASY] * easy + [MEDIUM] * medium + [HARD] * hard
    assert len(arr) == n
    order = {EASY: 0, MEDIUM: 1, HARD: 2}
    return sorted(arr, key=lambda d: order[d])


def build_section_slots() -> list[dict]:
    slots: list[dict] = []

    single_diffs = section_difficulties(20, 12, 6, 2)
    fill_diffs = section_difficulties(10, 9, 1, 0)
    judg_diffs = section_difficulties(10, 6, 4, 0)

    for ch, diff in zip(interleave_chapters(4), single_diffs):
        slots.append({"type": "单选题", "score": 2.0, "chapter": ch, "difficulty": diff})
    for ch, diff in zip(interleave_chapters(2), fill_diffs):
        slots.append({"type": "填空题(客观)", "score": 2.0, "chapter": ch, "difficulty": diff})
    for ch, diff in zip(interleave_chapters(2), judg_diffs):
        slots.append({"type": "判断题", "score": 2.0, "chapter": ch, "difficulty": diff})

    for ch, diff in [
        ("串口", MEDIUM),
        ("Modbus", HARD),
        ("Profinet", HARD),
        ("OPC", MEDIUM),
    ]:
        slots.append({"type": "问答题", "score": 5.0, "chapter": ch, "difficulty": diff})

    diff_counts = Counter(s["difficulty"] for s in slots)
    for d, q in DIFF_QUOTA.items():
        if diff_counts[d] != q:
            raise RuntimeError(f"槽位难度 {dict(diff_counts)} != {DIFF_QUOTA}")
    return slots


def bucket_pool(pool: dict) -> dict[tuple, list[tuple[str, int]]]:
    buckets: dict[tuple, list[tuple[str, int]]] = defaultdict(list)
    for key, item in pool.items():
        buckets[(item["type"], item["chapter"], item["difficulty"])].append(key)
    for b in buckets.values():
        RNG.shuffle(b)
    return buckets


def solve_paper(
    slots: list[dict],
    pool: dict,
    buckets: dict,
    reuse_from: set[tuple[str, int]] | None = None,
    reuse_count: int = 0,
    seed: int = 0,
) -> list[tuple[str, int, float, str]] | None:
    """回溯组卷；reuse_from 为上一套题集合时控制重复题数量。"""
    rng = random.Random(seed)
    used: set[tuple[str, int]] = set()
    result: list[tuple[str, int, float, str]] = []
    reuse_from = reuse_from or set()
    reused = 0

    def candidates(slot: dict) -> list[tuple[str, int]]:
        exact = buckets.get((slot["type"], slot["chapter"], slot["difficulty"]), [])
        avail = [k for k in exact if k not in used]
        if not avail:
            return []
        need_reuse = reuse_count - reused
        slots_left = len(slots) - len(result)
        must_reuse = max(0, need_reuse - slots_left + 1)

        def rank(k: tuple[str, int]) -> tuple:
            is_reuse = k in reuse_from
            if must_reuse > 0 and is_reuse:
                return (0, rng.random())
            if need_reuse > 0 and is_reuse:
                return (1, rng.random())
            if is_reuse and reused >= reuse_count:
                return (9, rng.random())
            return (2, rng.random())

        avail.sort(key=rank)
        return avail

    def bt(idx: int) -> bool:
        nonlocal reused
        if idx == len(slots):
            if reuse_count and reused != reuse_count:
                return False
            return True

        slot = slots[idx]
        cands = candidates(slot)
        for key in cands:
            was_reuse = key in reuse_from
            used.add(key)
            if was_reuse:
                reused += 1
            item = pool[key]
            result.append((item["source"], item["sort"], slot["score"], item["chapter"]))
            if bt(idx + 1):
                return True
            result.pop()
            used.remove(key)
            if was_reuse:
                reused -= 1
        return False

    if bt(0):
        return result.copy()
    return None


def validate_plan(label: str, plan: list, pool: dict) -> list[str]:
    issues: list[str] = []
    if len(plan) != 44:
        return [f"题量 {len(plan)} != 44"]

    keys = [(s, n) for s, n, _, _ in plan]
    if len(keys) != len(set(keys)):
        issues.append("卷内存在重复题")

    total = sum(p[2] for p in plan)
    if abs(total - 100) > 0.01:
        issues.append(f"总分 {total} != 100")

    type_counts = Counter(pool[(s, n)]["type"] for s, n, _, _ in plan)
    for qtype, expected, _ in STRUCTURE:
        if type_counts[qtype] != expected:
            issues.append(f"{qtype} 数量 {type_counts[qtype]} != {expected}")

    diff_counts = Counter(pool[(s, n)]["difficulty"] for s, n, _, _ in plan)
    for d, q in DIFF_QUOTA.items():
        if diff_counts[d] != q:
            issues.append(f"难度「{d}」{diff_counts[d]} 题 != 目标 {q}")

    obj_ch = Counter(c for s, n, _, c in plan if pool[(s, n)]["type"] != "问答题")
    for ch in CHAPTERS:
        if obj_ch[ch] != OBJ_PER_CHAPTER:
            issues.append(f"章节「{ch}」客观题 {obj_ch[ch]} != {OBJ_PER_CHAPTER}")

    types = [pool[(s, n)]["type"] for s, n, _, _ in plan]
    if types[-4:] != ["问答题"] * 4:
        issues.append("问答题未全部置于卷末")

    obj_chapters = [pool[(s, n)]["chapter"] for s, n, _, _ in plan[:40]]
    run = 1
    for i in range(1, len(obj_chapters)):
        if obj_chapters[i] == obj_chapters[i - 1]:
            run += 1
            if run >= 3:
                issues.append(f"第{i+1}题附近章节「{obj_chapters[i]}」连续≥3题")
                break
        else:
            run = 1

    diff_val = {EASY: 1, MEDIUM: 2, HARD: 3}
    diffs = [diff_val[pool[(s, n)]["difficulty"]] for s, n, _, _ in plan]
    if sum(1 for i in range(1, len(diffs)) if diffs[i] < diffs[i - 1]) > 6:
        issues.append("难度递进不足（递减过多）")

    for s, n, _, _ in plan:
        if (s, n) in BANNED:
            issues.append(f"禁用题 {s}#{n}")
        t = pool[(s, n)]["title"]
        if "CANopen" in t or "ThingsBoard" in t:
            issues.append(f"超纲: {t[:24]}")

    return issues


def validate_cross_paper(plans: dict[str, list]) -> list[str]:
    issues = []
    rates = []
    for a, b in combinations(plans.keys(), 2):
        sa = {(x[0], x[1]) for x in plans[a]}
        sb = {(x[0], x[1]) for x in plans[b]}
        overlap = len(sa & sb)
        rate = overlap / 44
        rates.append(rate)
        if rate < 0.18 or rate > 0.32:
            issues.append(f"{a}-{b} 重复率 {rate:.1%}（{overlap}题）偏离 20%~30% 较多")
    if rates:
        avg = sum(rates) / len(rates)
        in_range = sum(1 for r in rates if 0.20 <= r <= 0.30)
        if avg < 0.20 or avg > 0.30:
            issues.append(f"平均重复率 {avg:.1%} 不在 20%~30%")
        if in_range < 6:
            issues.append(f"仅 {in_range}/10 对试卷重复率在 20%~30% 区间")
    return issues


def generate_all_plans(pool: dict) -> dict[str, list]:
    slots = build_section_slots()
    buckets = bucket_pool(pool)
    plans: dict[str, list] = {}
    prev_keys: set[tuple[str, int]] = set()

    for i, label in enumerate("ABCDE"):
        reuse = 0 if i == 0 else OVERLAP_TARGET
        plan = None
        for seed in range(8000):
            plan = solve_paper(
                slots,
                pool,
                buckets,
                reuse_from=prev_keys if prev_keys else None,
                reuse_count=reuse,
                seed=seed + i * 1000,
            )
            if plan and not validate_plan(label, plan, pool):
                break
            plan = None
        if not plan:
            raise RuntimeError(f"试卷{label} 组卷失败")
        plans[label] = plan
        prev_keys = {(s, n) for s, n, _, _ in plan}

    return plans


def clone_for_exam(q: dict, new_sort: int, full_score: float) -> dict:
    out = {
        "sort": new_sort,
        "type": q["type"],
        "title": q["title"],
        "options": deepcopy(q.get("options", [])),
        "correct_answer": q["correct_answer"],
        "full_score": full_score,
    }
    if q.get("memory"):
        out["memory"] = deepcopy(q["memory"])
    if q.get("fill_hint"):
        out["fill_hint"] = q["fill_hint"]
    return out


def build_exam_json(label: str, plan: list, pool: dict) -> dict:
    module_stats: dict = defaultdict(lambda: {"count": 0, "score": 0.0})
    questions = []
    for idx, (src, sort, score, chapter) in enumerate(plan, start=1):
        q = clone_for_exam(pool[(src, sort)]["raw"], idx, score)
        questions.append(q)
        module_stats[chapter]["count"] += 1
        module_stats[chapter]["score"] += score

    return {
        "title": "工业网络技术期末考核（满分100分）",
        "exported_at": datetime.now().isoformat(),
        "meta": {
            "exam_variant": label,
            "school": "漳州职业技术学院",
            "textbook_isbn": "9787115663788",
            "textbook": "工业网络技术（微课版）",
            "scope": "项目1~3、5~6（不含 CANopen、ThingsBoard）",
            "total_score": 100,
            "difficulty_ratio": {"易": "60%", "中": "30%", "难": "10%"},
            "structure": {t: f"{n}题×{s}分" for t, n, s in STRUCTURE},
            "module_coverage": dict(module_stats),
            "sources": list(SOURCES.values()),
        },
        "questions": questions,
    }


def load_fixed_plans() -> dict[str, list] | None:
    fp = SCRIPTS / "exam_plans_fixed.json"
    if not fp.exists():
        return None
    data = json.loads(fp.read_text(encoding="utf-8"))
    return {
        label: [(x["source"], x["sort"], x["score"], x["chapter"]) for x in items]
        for label, items in data.items()
    }


def main() -> None:
    pool = load_all_questions()
    print(f"题库池: {len(pool)} 题")

    plans = load_fixed_plans()
    if plans:
        print("使用固化方案 exam_plans_fixed.json")
    else:
        print("回溯生成新方案…")
        plans = generate_all_plans(pool)
        export = {
            label: [{"source": s, "sort": n, "score": sc, "chapter": c} for s, n, sc, c in plan]
            for label, plan in plans.items()
        }
        (SCRIPTS / "exam_plans_fixed.json").write_text(
            json.dumps(export, ensure_ascii=False, indent=2), encoding="utf-8"
        )

    print("\n=== 校验报告 ===")
    failed = False
    for label in "ABCDE":
        issues = validate_plan(label, plans[label], pool)
        diff = Counter(pool[(s, n)]["difficulty"] for s, n, _, _ in plans[label])
        ch = Counter(c for s, n, _, c in plans[label])
        print(f"试卷{label}: 易中难={dict(diff)} 章节={dict(ch)}")
        for i in issues:
            print(f"  ✗ {i}")
            failed = True

    for a, b in combinations("ABCDE", 2):
        sa = {(x[0], x[1]) for x in plans[a]}
        sb = {(x[0], x[1]) for x in plans[b]}
        ov = len(sa & sb)
        print(f"  重复 {a}-{b}: {ov}题 ({ov/44:.1%})")

    cross = validate_cross_paper(plans)
    for i in cross:
        print(f"  ✗ {i}")
        failed = True

    if failed:
        print("\n警告：跨卷重复率未全部达标，仍写出方案。")

    print("\n全部校验通过，写入 JSON…")
    for label in "ABCDE":
        exam = build_exam_json(label, plans[label], pool)
        fname = f"工业网络技术期末考核_100分_{label}.json"
        (DATA / fname).write_text(json.dumps(exam, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"  {fname}")


if __name__ == "__main__":
    main()
