#!/usr/bin/env python3
"""Mobile review-mode UX checks (bottom sheet pattern)."""
import sys

from playwright.sync_api import sync_playwright

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8765"


def answer_wrong_quiz(page):
    page.locator('.quiz-item:has-text("语法选择")').click()
    page.wait_for_selector("#question-container")
    page.locator("#btn-answer-sheet").click()
    page.wait_for_selector("#review-sheet.open")
    total = page.locator("#review-sheet-grid button").count()
    page.locator("#btn-close-review-sheet").click()
    page.wait_for_timeout(300)

    for _ in range(total):
        opts = page.locator("label.option-item")
        if opts.count() >= 2:
            opts.nth(1).click()
            page.wait_for_timeout(120)
    page.locator("#btn-submit-mobile").click()
    page.wait_for_selector("#page-result", timeout=8000)
    page.locator("#btn-review").click()
    page.wait_for_selector("#question-container .review-banner")


def main():
    issues = []
    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = browser.new_page(viewport={"width": 390, "height": 844})
        page.goto(BASE, wait_until="networkidle")
        answer_wrong_quiz(page)

        aside_hidden = page.evaluate(
            """() => getComputedStyle(document.querySelector('#page-quiz aside')).display === 'none'"""
        )
        dock_visible = page.locator("#quiz-review-dock:not(.hidden)").count()
        strip_gone = page.locator("#quiz-review-strip").count() == 0
        banner = page.locator(".review-banner").count()
        header = page.locator("#quiz-progress-text").inner_text()

        print(f"aside hidden on mobile review: {aside_hidden}")
        print(f"review dock visible: {dock_visible}")
        print(f"old amber strip removed: {strip_gone}")
        print(f"review banner in question: {banner}")
        print(f"header: {header}")

        if not aside_hidden:
            issues.append("aside should be hidden in mobile review")
        if dock_visible != 1:
            issues.append("review dock not visible")
        if not strip_gone:
            issues.append("old review strip still present")
        if banner < 1:
            issues.append("missing review banner in question card")

        page.locator("#btn-review-sheet").click()
        page.wait_for_selector("#review-sheet.open")
        grid_count = page.locator("#review-sheet-grid button").count()
        print(f"sheet grid buttons: {grid_count}")
        if grid_count < 10:
            issues.append("answer sheet grid too small")

        page.locator("#review-filter-wrong").click()
        page.wait_for_timeout(200)
        wrong_count = page.locator("#review-sheet-grid button").count()
        print(f"wrong-only grid: {wrong_count}")
        if wrong_count < 5:
            issues.append("wrong filter did not reduce grid sensibly")

        before = page.locator("#review-dock-meta").inner_text()
        page.locator("#btn-close-review-sheet").click()
        page.wait_for_timeout(350)
        page.locator("#btn-review-next-wrong").click()
        page.wait_for_timeout(250)
        after = page.locator("#review-dock-meta").inner_text()
        print(f"dock meta: {before} -> {after}")
        if before == after:
            issues.append("next-wrong did not update dock meta")

        browser.close()

    if issues:
        print("ISSUES:")
        for i in issues:
            print(" -", i)
        sys.exit(1)
    print("OK")


if __name__ == "__main__":
    main()
