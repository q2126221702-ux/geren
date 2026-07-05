#!/usr/bin/env python3
"""quiz-web 完整测试：数据校验 + 各题库加载 + 判分逻辑 + 关键 UI 流程."""
import json
import sys
from pathlib import Path

from playwright.sync_api import sync_playwright

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8765"
ROOT = Path(__file__).parent.parent
DATA = ROOT / "data"
issues = []
passed = 0


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


def validate_question(q, sort_hint):
    t = q.get("type", "")
    if t in ("单选题", "多选题", "判断题"):
        if len(q.get("options", [])) < 2:
            return f"第{sort_hint}题({t})选项不足"
        if not q.get("correct_answer"):
            return f"第{sort_hint}题({t})缺少正确答案"
    elif t == "问答题":
        if not q.get("correct_answer"):
            return f"第{sort_hint}题问答题缺少参考答案"
    elif "填空" in t:
        if not q.get("correct_answer"):
            return f"第{sort_hint}题填空缺少答案"
    return None


def grade_multi_gaokao(q, user_answer):
    """与 app.js gradeMultiChoiceGaokao 一致."""
    letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"

    def norm_list(ans):
        if isinstance(ans, list):
            parts = [str(letters.index(x)) if x in letters else x for x in ans]
        else:
            parts = [s.strip() for s in str(ans or "").split(",") if s.strip()]
        return sorted(parts, key=lambda x: int(x) if x.isdigit() else x)

    def to_set(ans):
        return set(norm_list(ans))

    full = float(q.get("full_score") or 1)
    correct = to_set(q["correct_answer"])
    user = to_set(user_answer)
    if not user:
        return {"score": 0, "correct": False, "partial": False, "maxScore": full}
    if user - correct:
        return {"score": 0, "correct": False, "partial": False, "maxScore": full}
    if user == correct:
        return {"score": full, "correct": True, "partial": False, "maxScore": full}
    k = len(user & correct)
    raw = full * k / len(correct)
    score = int(raw) if raw == int(raw) else round(raw, 1)
    return {"score": score, "correct": False, "partial": True, "maxScore": full}


def answer_quiz_page(page, quiz, mode="correct"):
    """mode: correct | wrong_multi_partial"""
    letter_idx = {"A": 0, "B": 1, "C": 2, "D": 3, "E": 4}
    for idx, q in enumerate(quiz["questions"]):
        page.locator(f'#answer-card button[data-index="{idx}"]').click()
        page.wait_for_timeout(80)
        if q["type"] == "问答题":
            page.locator("#essay-input").fill("自动化测试作答")
            continue
        if q["type"] == "多选题":
            if mode == "wrong_multi_partial" and idx == next(
                i for i, qq in enumerate(quiz["questions"]) if qq["type"] == "多选题"
            ):
                page.locator("label.option-item").nth(0).click()
                page.locator("label.option-item").nth(1).click()
                continue
            correct = sorted(q["correct_answer"].split(","), key=int)
            for i in correct:
                page.locator("label.option-item").nth(int(i)).click()
                page.wait_for_timeout(40)
            continue
        if "填空" in q["type"]:
            mem = q.get("memory") or {}
            lang = mem.get("lang")
            ca = str(q.get("correct_answer", ""))
            if lang == "pos":
                fill = mem.get("pos_zh") or ca.split(";")[0]
            elif lang == "zh":
                fill = ca.split("；")[0].split(";")[0]
            else:
                fill = ca
            page.locator("#fill-input").fill(fill[:80])
            continue
        if q["type"] == "判断题":
            letter = "A" if str(q["correct_answer"]).strip() in ("0", "A") else "B"
            page.locator("label.option-item").nth(letter_idx[letter]).click()
            continue
        # 单选题
        letter = q["correct_answer"][0]
        page.locator("label.option-item").nth(letter_idx.get(letter, 0)).click()
        page.wait_for_timeout(60)


def test_static():
    print("\n=== 1. 静态数据校验 ===")
    manifest = load_json(DATA / "manifest.json")
    check("manifest 存在", bool(manifest.get("quizzes")))

    for entry in manifest["quizzes"]:
        fp = DATA / entry["file"]
        check(f"[{entry['id']}] 文件存在", fp.exists(), entry["file"])
        if not fp.exists():
            continue
        quiz = load_json(fp)
        is_flashcard = quiz.get("quiz_type") == "vocab_flashcard" or entry.get("kind") == "flashcard"
        if is_flashcard:
            n = len(quiz.get("cards", []))
            check(f"[{entry['id']}] 卡片数匹配 manifest", n == entry["count"], f"{n} vs {entry['count']}")
            check(f"[{entry['id']}] 有标题", bool(quiz.get("title")))
            continue
        n = len(quiz.get("questions", []))
        check(f"[{entry['id']}] 题数匹配 manifest", n == entry["count"], f"{n} vs {entry['count']}")
        check(f"[{entry['id']}] 有标题", bool(quiz.get("title")))
        bad = None
        for q in quiz["questions"]:
            bad = validate_question(q, q.get("sort", "?"))
            if bad:
                break
        check(f"[{entry['id']}] 题目字段完整", bad is None, bad or "")


def test_multichoice_logic():
    print("\n=== 2. 多选题判分逻辑 ===")
    opc = load_json(DATA / "OPC规范_20260626_185227.json")
    q3 = next(q for q in opc["questions"] if q["type"] == "多选题" and q["correct_answer"] == "0,1,2")
    full = float(q3["full_score"])

    r = grade_multi_gaokao(q3, ["A", "B", "C"])
    check("全对得满分", r["score"] == full, str(r))

    r = grade_multi_gaokao(q3, ["A", "B"])
    expect = round(full * 2 / 3, 1)
    check("3选2按比例得分", r["score"] == expect, f"{r['score']} vs {expect}")

    r = grade_multi_gaokao(q3, ["A"])
    expect = round(full * 1 / 3, 1)
    check("3选1按比例得分", r["score"] == expect, f"{r['score']} vs {expect}")

    q2 = next(q for q in opc["questions"] if q["type"] == "多选题" and q["correct_answer"] == "2,3")
    r = grade_multi_gaokao(q2, ["C"])
    check("2选1得一半分", r["score"] == full / 2, str(r["score"]))

    r = grade_multi_gaokao(q3, ["A", "D"])
    check("有错选0分", r["score"] == 0)

    r = grade_multi_gaokao(q3, [])
    check("未答0分", r["score"] == 0)


def test_ui(page):
    print("\n=== 3. UI 与流程 ===")
    page.on("dialog", lambda d: d.accept())
    page.goto(BASE, wait_until="networkidle", timeout=15000)
    check("首页可访问", "在线测验" in page.title() or page.locator("#quiz-list").count() > 0)

    manifest = load_json(DATA / "manifest.json")
    buttons = page.locator(".quiz-item").all()
    check("首页题库数量", len(buttons) == len(manifest["quizzes"]), f"{len(buttons)}/{len(manifest['quizzes'])}")

    for entry in manifest["quizzes"]:
        quiz = load_json(DATA / entry["file"])
        is_flashcard = quiz.get("quiz_type") == "vocab_flashcard" or entry.get("kind") == "flashcard"
        page.goto(BASE, wait_until="networkidle")
        page.locator(f'.quiz-item[data-file="{entry["file"]}"]').click()
        page.wait_for_timeout(400)
        if is_flashcard:
            check(f"[{entry['id']}] 进入闪卡页", page.locator("#page-flashcard").is_visible())
            progress = page.locator("#flashcard-progress-text").inner_text()
            check(
                f"[{entry['id']}] 闪卡数量",
                f"/ {entry['count']} 张" in progress,
                progress,
            )
            continue
        check(f"[{entry['id']}] 进入答题页", page.locator("#page-quiz").is_visible())
        total = int(page.locator("#total-count").inner_text())
        check(f"[{entry['id']}] 答题卡题数", total == entry["count"], str(total))

    # WE Learn B1U4 全对交卷
    we = next(e for e in manifest["quizzes"] if e["id"] == "welearn_b1u4")
    quiz = load_json(DATA / we["file"])
    page.goto(BASE, wait_until="networkidle")
    page.locator(f'.quiz-item[data-file="{we["file"]}"]').click()
    page.wait_for_timeout(400)
    answer_quiz_page(page, quiz, "correct")
    page.locator("#btn-submit").click()
    page.wait_for_timeout(500)
    check("WE Learn B1U4 交卷成功", page.locator("#page-result").is_visible())
    check("WE Learn B1U4 客观题满分20", page.locator("#result-score").inner_text() == "20")
    check("WE Learn B1U4 总分20", page.locator("#result-total").inner_text() == "20")

    page.locator("#btn-review").click()
    page.wait_for_timeout(300)
    page.locator('#answer-card button[data-index="5"]').click()
    page.wait_for_timeout(200)
    check("WE Learn B1U4 问答题解析", "参考答案" in page.locator("#question-container").inner_text())

    # OPC 多选部分得分 UI
    opc = load_json(DATA / "OPC规范_20260626_185227.json")
    page.goto(BASE, wait_until="networkidle")
    page.locator(".quiz-item", has_text="OPC").click()
    page.wait_for_timeout(400)
    multi_idx = next(i for i, q in enumerate(opc["questions"]) if q["type"] == "多选题")
    q = opc["questions"][multi_idx]
    page.locator(f'#answer-card button[data-index="{multi_idx}"]').click()
    page.wait_for_timeout(150)
    page.locator("label.option-item").nth(0).click()
    page.locator("label.option-item").nth(1).click()
    page.wait_for_timeout(150)
    page.locator("#btn-submit").click()
    page.wait_for_timeout(500)
    page.locator("#btn-review").click()
    page.wait_for_timeout(300)
    page.locator(f'#answer-card button[data-index="{multi_idx}"]').click()
    page.wait_for_timeout(150)
    review = page.locator("#question-container").inner_text()
    check("OPC 多选部分正确提示", "部分正确" in review)
    cls = page.locator(f'#answer-card button[data-index="{multi_idx}"]').get_attribute("class") or ""
    check("OPC 多选答题卡橙色", "partial" in cls)

    expected = grade_multi_gaokao(q, ["A", "B"])["score"]
    check("OPC 多选部分得分可解析", f"{expected}" in review or str(int(expected)) in review, review[:60])

    # Profinet 填空题渲染
    page.goto(BASE, wait_until="networkidle")
    page.locator(".quiz-item", has_text="Profinet").click()
    page.wait_for_timeout(400)
    prof = load_json(DATA / "Profinet工业以太网测验_20260626_185300.json")
    fill_idx = next(i for i, q in enumerate(prof["questions"]) if "填空" in q["type"])
    page.locator(f'#answer-card button[data-index="{fill_idx}"]').click()
    page.wait_for_timeout(150)
    check("Profinet 填空题输入框", page.locator("#fill-input").count() == 1)

    # MODBUS 判断题
    page.goto(BASE, wait_until="networkidle")
    page.locator(".quiz-item", has_text="MODBUS").click()
    page.wait_for_timeout(400)
    mod = load_json(DATA / "MODBUS协议及应用_20260626_185238.json")
    j_idx = next(i for i, q in enumerate(mod["questions"]) if q["type"] == "判断题")
    page.locator(f'#answer-card button[data-index="{j_idx}"]').click()
    page.wait_for_timeout(150)
    check("MODBUS 判断题选项", page.locator("label.option-item").count() >= 2)


def main():
    print("quiz-web 完整测试")
    print(f"目标: {BASE}")

    try:
        import urllib.request
        urllib.request.urlopen(BASE, timeout=3)
    except Exception as e:
        print(f"\n[FAIL] 无法连接 {BASE}，请先运行 start.bat\n  {e}")
        sys.exit(1)

    test_static()
    test_multichoice_logic()

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
