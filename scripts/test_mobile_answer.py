#!/usr/bin/env python3
"""Mobile answering-mode UX checks (bottom dock + answer sheet)."""
import sys

from playwright.sync_api import sync_playwright

BASE = sys.argv[1] if len(sys.argv) > 1 else __import__("quiz_constants", fromlist=["DEFAULT_TEST_BASE"]).DEFAULT_TEST_BASE


def main():
    issues = []
    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = browser.new_page(viewport={"width": 390, "height": 844})
        page.goto(BASE, wait_until="networkidle")
        page.locator('[data-home-category="english"]').click()
        page.wait_for_timeout(250)
        page.locator('.quiz-item:has-text("语法选择")').click()
        page.wait_for_selector("#question-container")

        layout = page.evaluate(
            """() => ({
              mobileLayout: document.getElementById('page-quiz')?.classList.contains('quiz-mobile-layout'),
              asideHidden: getComputedStyle(document.querySelector('#page-quiz aside')).display === 'none',
              navHidden: getComputedStyle(document.querySelector('.quiz-nav-row')).display === 'none',
            })"""
        )
        dock_visible = page.locator("#quiz-answer-dock:not(.hidden)").count()
        meta = page.locator("#answer-dock-meta").inner_text()
        header = page.locator("#quiz-progress-text").inner_text()
        answer_meta = page.locator(".answer-meta").count()

        print(f"mobile layout: {layout}")
        print(f"answer dock visible: {dock_visible}")
        print(f"dock meta: {meta}")
        print(f"header: {header}")
        print(f"answer meta banner: {answer_meta}")

        if not layout.get("mobileLayout"):
            issues.append("page should have quiz-mobile-layout while answering")
        if not layout.get("asideHidden"):
            issues.append("aside should be hidden on mobile answering")
        if dock_visible != 1:
            issues.append("answer dock not visible")
        if answer_meta < 1:
            issues.append("missing answer-meta banner in question card")
        if "已答" not in header:
            issues.append("header should show answered count")

        page.locator("#btn-answer-sheet").click()
        page.wait_for_selector("#review-sheet.open")
        title = page.locator("#answer-sheet-title").inner_text()
        summary = page.locator("#review-sheet-summary").inner_text()
        filter_hidden = page.locator("#answer-sheet-filter-row").evaluate(
            "el => el.classList.contains('hidden')"
        )
        grid_count = page.locator("#review-sheet-grid button").count()
        print(f"sheet title: {title}, summary: {summary}, filter hidden: {filter_hidden}, grid: {grid_count}")

        if title != "答题卡":
            issues.append("answer sheet title wrong while answering")
        if not filter_hidden:
            issues.append("wrong/all filter should be hidden while answering")
        if grid_count < 10:
            issues.append("answer sheet grid too small")

        page.locator("#review-sheet-grid button").nth(3).click()
        page.wait_for_timeout(350)
        after_jump = page.locator("#answer-dock-meta").inner_text()
        print(f"after jump to Q4: {after_jump}")
        if not after_jump.startswith("4/"):
            issues.append("dock meta did not update after sheet jump")

        page.locator("label.option-item").first.click()
        page.wait_for_timeout(300)
        submit_text = page.locator("#btn-submit-mobile").inner_text()
        dock_after_answer = page.locator("#answer-dock-meta").inner_text()
        print(f"after answer (auto-advance): dock={dock_after_answer}, submit={submit_text}")
        if "已答 1/" not in submit_text:
            issues.append("submit button should reflect answered count")
        if not dock_after_answer.startswith("5/"):
            issues.append("choice answer should auto-advance to next question")

        page.locator("#btn-answer-prev").click()
        page.wait_for_timeout(200)
        prev_meta = page.locator("#answer-dock-meta").inner_text()
        answered_meta = page.locator(".answer-meta").inner_text()
        print(f"back to Q4: {prev_meta}, meta={answered_meta}")
        if not prev_meta.startswith("4/"):
            issues.append("prev button did not go back")
        if "已作答" not in answered_meta:
            issues.append("answered question should show 已作答 in answer-meta")

        page.locator("#btn-answer-next").click()
        page.wait_for_timeout(150)
        next_meta = page.locator("#answer-dock-meta").inner_text()
        print(f"after next: {next_meta}")
        if not next_meta.startswith("5/"):
            issues.append("next button did not advance question")

        # Last question: meta banner should update without auto-advance
        page.locator("#btn-answer-sheet").click()
        page.wait_for_selector("#review-sheet.open")
        total = page.locator("#review-sheet-grid button").count()
        page.locator(f"#review-sheet-grid button:nth-child({total})").click()
        page.wait_for_timeout(300)
        page.locator("label.option-item").first.click()
        page.wait_for_timeout(200)
        last_meta = page.locator(".answer-meta").inner_text()
        print(f"last question answered: {last_meta}")
        if "已作答" not in last_meta:
            issues.append("last question answer-meta should show 已作答 without navigating away")

        browser.close()

    if issues:
        print("ISSUES:")
        for i in issues:
            print(" -", i)
        sys.exit(1)
    print("OK")


if __name__ == "__main__":
    main()
