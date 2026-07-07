#!/usr/bin/env python3
"""错题集专项 UI 测试：收录、重复计数、筛选清空、练习与答对移出、重新作答."""
import json
import sys
from pathlib import Path

from playwright.sync_api import sync_playwright

from quiz_constants import DEFAULT_TEST_BASE

BASE = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_TEST_BASE
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


def load_json(path):
    return json.loads(path.read_text(encoding="utf-8"))


def open_industrial(page):
    page.locator('[data-home-category="industrial"]').click()
    page.wait_for_timeout(200)


def get_store(page):
    raw = page.evaluate("() => localStorage.getItem('quiz-wrong-book-v1')")
    return json.loads(raw) if raw else {"items": []}


def main():
    print("错题集专项测试")
    print(f"目标: {BASE}\n")

    mod = load_json(DATA / "MODBUS协议及应用_20260626_185238.json")
    j_idx = next(i for i, q in enumerate(mod["questions"]) if q["type"] == "判断题")
    qj = mod["questions"][j_idx]
    correct_idx = int(str(qj["correct_answer"]).strip())
    wrong_opt = 1 - correct_idx

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        page = browser.new_page()
        page.on("dialog", lambda d: d.accept())

        page.goto(BASE, wait_until="networkidle", timeout=15000)
        page.evaluate("localStorage.removeItem('quiz-wrong-book-v1')")

        # 1. 交卷收录
        open_industrial(page)
        page.locator(".quiz-item", has_text="MODBUS").click()
        page.wait_for_timeout(400)
        page.locator(f'#answer-card button[data-index="{j_idx}"]').click()
        page.wait_for_timeout(150)
        page.locator("label.option-item").nth(wrong_opt).click()
        page.wait_for_timeout(150)
        page.locator("#btn-submit").click()
        page.wait_for_timeout(500)
        store = get_store(page)
        check("localStorage 有错题", len(store.get("items", [])) >= 1, str(len(store.get("items", []))))

        # 2. 再次做错 → wrongCount 增加
        page.locator("#btn-retry").click()
        page.wait_for_timeout(400)
        page.locator(f'#answer-card button[data-index="{j_idx}"]').click()
        page.wait_for_timeout(150)
        page.locator("label.option-item").nth(wrong_opt).click()
        page.wait_for_timeout(150)
        page.locator("#btn-submit").click()
        page.wait_for_timeout(500)
        store2 = get_store(page)
        item = store2["items"][0]
        check("重复做错 wrongCount>=2", item.get("wrongCount", 0) >= 2, str(item.get("wrongCount")))

        # 3. 筛选 + 清空当前筛选
        page.locator("#btn-home").click()
        page.wait_for_timeout(300)
        page.locator("#btn-open-wrong-book").click()
        page.wait_for_timeout(300)
        before = len(get_store(page)["items"])
        page.locator("#btn-wrong-book-clear-filtered").click()
        page.wait_for_timeout(300)
        after = len(get_store(page)["items"])
        check("清空当前筛选后 localStorage 为空", after == 0, f"{before}->{after}")

        # 4. 再收录一题用于练习测试
        page.locator("#btn-wrong-book-back").click()
        page.wait_for_timeout(200)
        open_industrial(page)
        page.locator(".quiz-item", has_text="MODBUS").click()
        page.wait_for_timeout(400)
        page.locator(f'#answer-card button[data-index="{j_idx}"]').click()
        page.wait_for_timeout(150)
        page.locator("label.option-item").nth(wrong_opt).click()
        page.locator("#btn-submit").click()
        page.wait_for_timeout(500)

        page.locator("#btn-home").click()
        page.wait_for_timeout(300)
        page.locator("#btn-open-wrong-book").click()
        page.wait_for_timeout(300)
        page.locator("#btn-wrong-book-practice").click()
        page.wait_for_timeout(400)
        check("错题练习进入答题页", page.locator("#page-quiz").is_visible())
        check("练习卷标题含错题集", "错题集" in page.locator("#quiz-title").inner_text())

        # 5. 练习答对 → 自动移出
        page.locator("label.option-item").nth(correct_idx).click()
        page.wait_for_timeout(150)
        page.locator("#btn-submit").click()
        page.wait_for_timeout(500)
        store3 = get_store(page)
        check("练习答对后自动移出错题集", len(store3.get("items", [])) == 0, str(len(store3.get("items", []))))
        check("成绩单提示已移除", "已移除" in page.locator("#result-rate").inner_text())

        # 7. 重新作答仍保持错题练习模式
        page.locator("#btn-retry").click()
        page.wait_for_timeout(400)
        check("重新作答后仍为错题练习", "错题集" in page.locator("#quiz-title").inner_text())
        page.locator("#btn-back").click()
        page.wait_for_timeout(200)

        # 8. 导出按钮
        page.locator("#btn-open-wrong-book").click()
        page.wait_for_timeout(200)
        check("空错题集时导出按钮存在", page.locator("#btn-wrong-book-export").count() == 1)

        browser.close()

    print("\n" + "=" * 40)
    print(f"通过: {passed}  失败: {len(issues)}")
    if issues:
        for i in issues:
            print(f"  - {i}")
        sys.exit(1)
    print("错题集专项测试全部通过")


if __name__ == "__main__":
    main()
