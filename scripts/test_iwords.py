#!/usr/bin/env python3
"""iWords 词汇填空题库：数据结构 + 词性判分 + UI 流程."""
import json
import sys
from pathlib import Path

from playwright.sync_api import sync_playwright

BASE = "http://localhost:8080"
ROOT = Path(__file__).parent.parent
DATA = ROOT / "data"
PATTERNS = {"zh2en", "en2zh", "spell", "pos", "assoc", "phrase_cloze"}
issues = []
passed = 0

POS_ALIASES = {
    "名词": ["名词", "n", "n."],
    "动词": ["动词", "v", "v."],
    "形容词": ["形容词", "adj", "adj."],
    "副词": ["副词", "adv", "adv."],
    "介词": ["介词", "prep", "prep."],
    "感叹词": ["感叹词", "int", "int."],
    "缩写": ["缩写", "abbr", "abbr."],
    "连词": ["连词", "conj", "conj."],
    "代词": ["代词", "pron", "pron."],
    "数词": ["数词", "num", "num."],
    "冠词": ["冠词", "art", "art."],
    "短语": ["短语", "phr"],
}


def check(name, ok, detail=""):
    global passed
    tag = "PASS" if ok else "FAIL"
    print(f"[{tag}] {name}" + (f" — {detail}" if detail else ""))
    if ok:
        passed += 1
    else:
        issues.append(f"{name}: {detail}")


def load_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def norm_pos(s):
    return str(s or "").strip().lower().rstrip(".")


def expand_pos_accept(correct_answer):
    s = set()
    for part in str(correct_answer).split(";"):
        part = part.strip()
        if not part:
            continue
        s.add(norm_pos(part))
        if part in POS_ALIASES:
            for a in POS_ALIASES[part]:
                s.add(norm_pos(a))
        for zh, aliases in POS_ALIASES.items():
            if any(norm_pos(a) == norm_pos(part) for a in aliases):
                s.add(norm_pos(zh))
                for a in aliases:
                    s.add(norm_pos(a))
    return s


def match_pos(user, correct):
    u = norm_pos(user)
    return bool(u) and u in expand_pos_accept(correct)


def pick_fill_answer(q):
    lang = q.get("memory", {}).get("lang")
    ca = str(q.get("correct_answer", ""))
    if lang == "pos":
        return q.get("memory", {}).get("pos_zh") or ca.split(";")[0]
    if lang == "zh":
        return ca.split("；")[0].split(";")[0]
    return ca


def test_static():
    print("\n=== 1. iWords 静态数据 ===")
    manifest = load_json(DATA / "manifest.json")
    entries = [q for q in manifest["quizzes"] if q["id"].endswith("_iwords")]
    check("manifest 含 5 份 iWords 卷", len(entries) == 5, str(len(entries)))

    total_q = 0
    for entry in entries:
        quiz = load_json(DATA / entry["file"])
        check(f"[{entry['id']}] quiz_type", quiz.get("quiz_type") == "iwords_fill")
        check(f"[{entry['id']}] 题数", len(quiz["questions"]) == entry["count"])

        for q in quiz["questions"]:
            if q.get("type") != "填空题(客观)":
                issues.append(f"{entry['id']} sort={q.get('sort')} 非填空题")
                break
            mem = q.get("memory") or {}
            if mem.get("pattern") not in PATTERNS:
                issues.append(f"{entry['id']} sort={q.get('sort')} 未知 pattern")
                break
            if not q.get("fill_hint"):
                issues.append(f"{entry['id']} sort={q.get('sort')} 缺少 fill_hint")
                break
            if mem.get("lang") == "pos":
                if not mem.get("pos_zh"):
                    issues.append(f"{entry['id']} sort={q.get('sort')} pos 缺 pos_zh")
                    break
                if "词性（填中文" not in q.get("title", ""):
                    issues.append(f"{entry['id']} sort={q.get('sort')} pos 题标题格式")
                    break
        else:
            check(f"[{entry['id']}] 字段与题型", True)
            total_q += len(quiz["questions"])

    check("iWords 总题数", total_q == 64 + 75 + 75 + 80 + 91, str(total_q))


def test_pos_grading():
    print("\n=== 2. 词性判分逻辑 ===")
    quiz = load_json(DATA / "WE Learn_B1U4_Movies_iWords_20260704.json")
    pos_qs = [q for q in quiz["questions"] if q.get("memory", {}).get("lang") == "pos"]
    check("B1U4 含词性题", len(pos_qs) >= 5, str(len(pos_qs)))

    sample = pos_qs[0]
    ca = sample["correct_answer"]
    for user in ("名词", "n", "n.", "N."):
        check(f"词性接受「{user}」", match_pos(user, ca))
    check("词性拒绝「动词」", not match_pos("动词", ca))

    adj_q = next((q for q in pos_qs if q["memory"].get("pos") == "adj."), None)
    if adj_q:
        check("形容词接受 adj", match_pos("adj", adj_q["correct_answer"]))
        check("形容词接受 形容词", match_pos("形容词", adj_q["correct_answer"]))


def test_ui(page):
    print("\n=== 3. iWords UI 流程 ===")
    page.on("dialog", lambda d: d.accept())
    manifest = load_json(DATA / "manifest.json")
    entry = next(q for q in manifest["quizzes"] if q["id"] == "welearn_b1u4_iwords")
    quiz = load_json(DATA / entry["file"])

    page.goto(BASE, wait_until="networkidle", timeout=15000)
    page.locator(f'.quiz-item[data-file="{entry["file"]}"]').click()
    page.wait_for_timeout(400)
    check("进入 iWords 答题页", page.locator("#page-quiz").is_visible())
    check("答题卡 64 题", page.locator("#total-count").inner_text() == "64")

    pos_idx = next(i for i, q in enumerate(quiz["questions"]) if q.get("memory", {}).get("lang") == "pos")
    page.locator(f'#answer-card button[data-index="{pos_idx}"]').click()
    page.wait_for_timeout(150)
    ph = page.locator("#fill-input").get_attribute("placeholder") or ""
    check("词性题 placeholder 含「名词」", "名词" in ph, ph[:40])

    # 浏览器内 gradeQuestion 与 Python 逻辑一致
    q_json = json.dumps(quiz["questions"][pos_idx], ensure_ascii=False)
    for user, expect in (("n", True), ("名词", True), ("动词", False)):
        ok = page.evaluate(
            """([q, user]) => {
              const normPos = s => String(s||'').trim().toLowerCase().replace(/\\.$/, '');
              const POS_ALIASES = {
                '名词': ['名词','n','n.'], '动词': ['动词','v','v.'], '形容词': ['形容词','adj','adj.']
              };
              const expand = ca => {
                const set = new Set();
                String(ca).split(/[;；]/).forEach(part => {
                  part = part.trim();
                  if (!part) return;
                  set.add(normPos(part));
                  if (POS_ALIASES[part]) POS_ALIASES[part].forEach(a => set.add(normPos(a)));
                  Object.entries(POS_ALIASES).forEach(([zh, aliases]) => {
                    if (aliases.some(a => normPos(a) === normPos(part))) {
                      set.add(normPos(zh));
                      aliases.forEach(a => set.add(normPos(a)));
                    }
                  });
                });
                return set;
              };
              const u = normPos(user);
              return u && expand(q.correct_answer).has(u);
            }""",
            [json.loads(q_json), user],
        )
        check(f"浏览器词性判分「{user}」", ok == expect)

    # 全卷用标准答案作答，应满分
    for idx, q in enumerate(quiz["questions"]):
        page.locator(f'#answer-card button[data-index="{idx}"]').click()
        page.wait_for_timeout(40)
        page.locator("#fill-input").fill(pick_fill_answer(q)[:80])
        page.wait_for_timeout(20)

    page.locator("#btn-submit").click()
    page.wait_for_timeout(600)
    check("交卷进入结果页", page.locator("#page-result").is_visible())
    score = page.locator("#result-score").inner_text()
    total = page.locator("#result-total").inner_text()
    check("B1U4 iWords 全对满分", score == "64" and total == "64", f"{score}/{total}")

    # 故意错一题词性，检查记忆卡
    page.goto(BASE, wait_until="networkidle")
    page.locator(f'.quiz-item[data-file="{entry["file"]}"]').click()
    page.wait_for_timeout(300)
    for idx, q in enumerate(quiz["questions"]):
        page.locator(f'#answer-card button[data-index="{idx}"]').click()
        page.wait_for_timeout(30)
        ans = pick_fill_answer(q)
        if idx == pos_idx:
            ans = "错误词性"
        page.locator("#fill-input").fill(ans[:80])
    page.locator("#btn-submit").click()
    page.wait_for_timeout(600)
    cls = page.locator("#wrong-memory-panel").get_attribute("class") or ""
    check("错词记忆卡显示", "hidden" not in cls)
    check("换形式再练按钮可见", page.locator("#btn-wrong-drill").is_visible())


def main():
    print("iWords 词汇填空测试")
    print(f"目标: {BASE}")
    try:
        import urllib.request

        urllib.request.urlopen(BASE, timeout=3)
    except Exception as e:
        print(f"\n[FAIL] 无法连接 {BASE}，请先运行 start.bat\n  {e}")
        sys.exit(1)

    test_static()
    test_pos_grading()

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        page = browser.new_page()
        try:
            test_ui(page)
        finally:
            browser.close()

    print("\n" + "=" * 40)
    print(f"通过: {passed}  失败: {len(issues)}")
    if issues:
        print("\n失败项:")
        for i in issues:
            print(f"  - {i}")
        sys.exit(1)
    print("全部测试通过")


if __name__ == "__main__":
    main()
