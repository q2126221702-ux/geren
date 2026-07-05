#!/usr/bin/env python3
"""Mobile layout checks: answer card gap + essay review collapsible."""
import sys
from pathlib import Path

from playwright.sync_api import sync_playwright

ROOT = Path(__file__).parent.parent
BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080"


def main():
    issues = []
    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = browser.new_page(viewport={"width": 390, "height": 844})
        page.goto(BASE, wait_until="networkidle")

        # OPC quiz — answer card layout
        page.locator('.quiz-item:has-text("OPC规范")').click()
        page.wait_for_selector("#answer-card button")

        nav = page.locator("#btn-next")
        card_title = page.locator('aside h2:has-text("答题卡")')
        gap = card_title.bounding_box()["y"] - (
            nav.bounding_box()["y"] + nav.bounding_box()["height"]
        )
        print(f"gap between nav and answer card: {gap:.0f}px")
        if gap > 80:
            issues.append(f"answer card too far below nav ({gap:.0f}px)")

        grid = page.locator("#answer-card")
        grid_box = grid.bounding_box()
        btn1 = page.locator('#answer-card button[data-index="0"]')
        btn6 = page.locator('#answer-card button[data-index="5"]')
        row_gap = btn6.bounding_box()["y"] - (
            btn1.bounding_box()["y"] + btn1.bounding_box()["height"]
        )
        print(f"answer card grid width: {grid_box['width']:.0f}px, row gap (1->6): {row_gap:.0f}px")
        if row_gap < 0:
            issues.append(f"answer card rows overlap ({row_gap:.0f}px)")

        # Essay review collapsible (WE Learn B1U4)
        page.locator("#btn-back").click()
        page.wait_for_selector("#quiz-list")
        page.locator('.quiz-item[data-file="WE Learn_B1U4_Movies_翻译题_20260703.json"]').click()
        for idx in range(6):
            page.locator(f'#answer-card button[data-index="{idx}"]').click()
            if idx < 5:
                page.locator("label.option-item").first.click()
        page.locator("#essay-input").fill("测试翻译")
        page.locator("#btn-submit").click()
        page.wait_for_selector("#page-result")
        page.locator("#btn-review").click()
        page.locator('#answer-card button[data-index="5"]').click()
        page.wait_for_selector("#question-container")

        has_fold = page.locator(".essay-prompt-fold").count()
        has_sticky_css = page.evaluate(
            """() => {
              const el = document.querySelector('.essay-prompt');
              if (!el) return false;
              return getComputedStyle(el).position === 'sticky';
            }"""
        )
        print(f"essay review fold: {has_fold}, sticky prompt: {has_sticky_css}")
        if not has_fold:
            issues.append("essay review missing collapsible prompt")
        if has_sticky_css:
            issues.append("essay prompt still sticky")

        browser.close()

    if issues:
        print("ISSUES:")
        for i in issues:
            print(" -", i)
        sys.exit(1)
    print("OK")


if __name__ == "__main__":
    main()
