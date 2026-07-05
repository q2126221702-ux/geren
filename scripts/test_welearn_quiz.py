import json
import sys
from playwright.sync_api import sync_playwright

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8765"
QUIZ_FILE = "WE Learn_B1U4_Movies_翻译题_20260703.json"
UNIT_ID = "welearn_b1u4"
TRANSLATION_UNIT_IDS = {f"welearn_b1u{i}" for i in range(4, 9)}
issues = []


def check(name, ok, detail=""):
    status = "PASS" if ok else "FAIL"
    print(f"[{status}] {name}" + (f" — {detail}" if detail else ""))
    if not ok:
        issues.append(f"{name}: {detail}")


def load_quiz(page):
    return page.evaluate(f"fetch('data/{QUIZ_FILE}').then(r => r.json())")


def start_welearn_quiz(page, quiz_file):
    page.goto(BASE, wait_until="networkidle")
    page.locator(f'.quiz-item[data-file="{quiz_file}"]').click()
    page.wait_for_timeout(400)


with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page()
    page.on("dialog", lambda d: d.accept())

    page.goto(BASE, wait_until="networkidle", timeout=15000)
    manifest = page.evaluate("fetch('data/manifest.json').then(r => r.json())")
    welearn_units = [q for q in manifest["quizzes"] if q.get("id") in TRANSLATION_UNIT_IDS]
    check("manifest 含 5 个 WE Learn 翻译单元", len(welearn_units) == 5, str(len(welearn_units)))

    b1u4 = next((q for q in manifest["quizzes"] if q["id"] == UNIT_ID), None)
    check("B1U4 单元存在", b1u4 is not None)
    check("B1U4 题目数量 6", b1u4 and b1u4["count"] == 6, str(b1u4))

    quiz = load_quiz(page)
    check("题库 JSON 可加载", bool(quiz.get("questions")))
    check("共 6 题", len(quiz["questions"]) == 6)
    choice = [q for q in quiz["questions"] if q["type"] == "单选题"]
    essay = [q for q in quiz["questions"] if q["type"] == "问答题"]
    check("单选题 5 道", len(choice) == 5)
    check("问答题 1 道", len(essay) == 1)

    for q in quiz["questions"]:
        if q["type"] == "单选题":
            if len(q.get("options", [])) < 2:
                issues.append(f"第{q['sort']}题选项不足")
            if not str(q.get("correct_answer", "")).startswith(("A.", "B.", "C.", "D.")):
                issues.append(f"第{q['sort']}题答案格式异常")
        if q["type"] == "问答题" and not q.get("correct_answer"):
            issues.append(f"第{q['sort']}题缺少参考答案")
    check("题目字段校验", not any("题" in i for i in issues))

    welearn_buttons = page.locator('.quiz-item[data-file*="翻译题"]').all()
    check("首页显示 5 个 WE Learn 翻译单元", len(welearn_buttons) == 5, str(len(welearn_buttons)))

    start_welearn_quiz(page, QUIZ_FILE)
    check("进入答题页", page.locator("#page-quiz").is_visible())
    check("题目标题含单元信息", "B1U4" in page.locator("#question-container h2").inner_text())
    check("答题卡总数 6", page.locator("#total-count").inner_text() == "6")

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
    check("6 题均已作答", answered == "6", answered)

    page.locator("#btn-submit").click()
    page.wait_for_timeout(500)
    check("交卷进入结果页", page.locator("#page-result").is_visible())

    score = page.locator("#result-score").inner_text()
    total = page.locator("#result-total").inner_text()
    check("客观题满分 20", score == "20" and total == "20", f"{score}/{total}")

    page.locator("#btn-review").click()
    page.wait_for_timeout(300)
    current_idx = page.evaluate(
        """() => {
          const btn = document.querySelector('#answer-card button.current');
          return btn ? Number(btn.dataset.index) : -1;
        }"""
    )
    check("全对时查看解析优先进入问答题", current_idx == 5, str(current_idx))
    review_text = page.locator("#question-container").inner_text()
    check("问答题纳入待复习导航", "待复习" in review_text and "对照参考" in review_text)

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
