"""自动化测试 quiz-web WE Learn 题库."""
import json
import sys
from playwright.sync_api import sync_playwright

BASE = "http://localhost:8080"
QUIZ_FILE = "WE Learn_B1U4-U8_翻译题_20260703.json"
issues = []


def check(name, ok, detail=""):
    status = "PASS" if ok else "FAIL"
    print(f"[{status}] {name}" + (f" — {detail}" if detail else ""))
    if not ok:
        issues.append(f"{name}: {detail}")


def load_quiz(page):
    return page.evaluate(f"fetch('data/{QUIZ_FILE}').then(r => r.json())")


def start_welearn_quiz(page):
    page.goto(BASE, wait_until="networkidle")
    page.locator(".quiz-item", has_text="WE Learn").click()
    page.wait_for_timeout(400)


with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page()
    page.on("dialog", lambda d: d.accept())

    page.goto(BASE, wait_until="networkidle", timeout=15000)
    manifest = page.evaluate("fetch('data/manifest.json').then(r => r.json())")
    welearn = next((q for q in manifest["quizzes"] if q["id"] == "welearn_b1u4_u8"), None)
    check("manifest 含 WE Learn 题库", welearn is not None)
    check("题目数量 30", welearn and welearn["count"] == 30, str(welearn))

    quiz = load_quiz(page)
    check("题库 JSON 可加载", bool(quiz.get("questions")))
    check("共 30 题", len(quiz["questions"]) == 30)
    choice = [q for q in quiz["questions"] if q["type"] == "单选题"]
    essay = [q for q in quiz["questions"] if q["type"] == "问答题"]
    check("单选题 25 道", len(choice) == 25)
    check("问答题 5 道", len(essay) == 5)

    for q in quiz["questions"]:
        if q["type"] == "单选题":
            if len(q.get("options", [])) < 2:
                issues.append(f"第{q['sort']}题选项不足")
            if not str(q.get("correct_answer", "")).startswith(("A.", "B.", "C.", "D.")):
                issues.append(f"第{q['sort']}题答案格式异常")
        if q["type"] == "问答题" and not q.get("correct_answer"):
            issues.append(f"第{q['sort']}题缺少参考答案")
    check("题目字段校验", not any("题" in i for i in issues))

    check("首页显示题库按钮", any("WE Learn" in el.inner_text() for el in page.locator(".quiz-item").all()))

    # 完整作答流程
    start_welearn_quiz(page)
    check("进入答题页", page.locator("#page-quiz").is_visible())
    check("题目标题含单元信息", "B1U4" in page.locator("#question-container h2").inner_text())
    check("答题卡总数 30", page.locator("#total-count").inner_text() == "30")

    letter_idx = {"A": 0, "B": 1, "C": 2, "D": 3}
    for idx, q in enumerate(quiz["questions"]):
        page.locator(f'#answer-card button[data-index="{idx}"]').click()
        page.wait_for_timeout(150)
        if q["type"] == "问答题":
            page.locator("#essay-input").fill("测试翻译作答")
            continue
        letter = q["correct_answer"][0]
        page.locator("label.option-item").nth(letter_idx[letter]).click()
        page.wait_for_timeout(150)

    answered = page.locator("#answered-count").inner_text()
    check("30 题均已作答", answered == "30", answered)

    page.locator("#btn-submit").click()
    page.wait_for_timeout(500)
    check("交卷进入结果页", page.locator("#page-result").is_visible())

    score = page.locator("#result-score").inner_text()
    total = page.locator("#result-total").inner_text()
    check("客观题满分 100", score == "100" and total == "100", f"{score}/{total}")

    page.locator("#btn-review").click()
    page.wait_for_timeout(300)
    check("解析模式显示正确答案", "正确答案" in page.locator("#question-container").inner_text())

    # 问答题解析
    for idx, q in enumerate(quiz["questions"]):
        if q["type"] != "问答题":
            continue
        page.locator(f'#answer-card button[data-index="{idx}"]').click()
        page.wait_for_timeout(150)
        text = page.locator("#question-container").inner_text()
        if "参考答案" not in text or q["correct_answer"][:12] not in text:
            issues.append(f"第{q['sort']}题解析未显示参考答案")
            break
    else:
        check("问答题解析显示参考答案", True)
    if any("解析未显示" in i for i in issues):
        check("问答题解析显示参考答案", False, issues[-1])

    browser.close()

print("\n---")
if issues:
    print("发现问题:")
    for i in issues:
        print(f"  - {i}")
    sys.exit(1)
print("全部测试通过")
