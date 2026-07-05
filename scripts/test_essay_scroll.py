"""移动端问答题滚动行为探测."""
from playwright.sync_api import sync_playwright

FILE = "WE Learn_B1U4_Movies_翻译题_20260703.json"


def rect(page, sel):
    return page.evaluate(
        """(sel) => {
      const el = document.querySelector(sel);
      if (!el) return null;
      const r = el.getBoundingClientRect();
      return { top: r.top, bottom: r.bottom, scrollY: window.scrollY };
    }""",
        sel,
    )


with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page(viewport={"width": 390, "height": 844})
    page.goto("http://localhost:8080", wait_until="networkidle")
    page.locator(f'.quiz-item[data-file="{FILE}"]').click()
    page.wait_for_timeout(400)
    page.locator('#answer-card button[data-index="5"]').click()
    page.wait_for_timeout(300)

    has_prompt = page.locator(".essay-prompt").count()
    print("essay-prompt exists:", has_prompt)
    before = rect(page, ".essay-prompt") or rect(page, "#question-container h2")
    page.locator("#essay-input").click()
    page.wait_for_timeout(400)
    after_focus = rect(page, ".essay-prompt") or rect(page, "#question-container h2")
    page.locator("#essay-input").fill("测试翻译一行")
    page.wait_for_timeout(200)
    after_type = rect(page, ".essay-prompt") or rect(page, "#question-container h2")

    print("title before focus:", before)
    print("title after focus:", after_focus)
    print("title after type:", after_type)
    if before and after_focus and after_focus["top"] < -50:
        print("ISSUE: title scrolled off screen after focus")
    browser.close()
