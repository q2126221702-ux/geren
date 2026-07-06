#!/usr/bin/env python3
"""Layout checks: desktop answer card + mobile essay review."""
import sys

from playwright.sync_api import sync_playwright

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8765"


def main():
    issues = []
    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = browser.new_page(viewport={"width": 1280, "height": 900})
        page.goto(BASE, wait_until="networkidle")

        # Desktop — sidebar answer card layout
        page.locator('.quiz-item:has-text("OPC规范")').click()
        page.wait_for_selector("#answer-card button")

        nav = page.locator("#btn-next")
        card_title = page.locator('aside h2:has-text("答题卡")')
        nav_box = nav.bounding_box()
        card_box = card_title.bounding_box()
        aside_visible = page.evaluate(
            "() => getComputedStyle(document.querySelector('#page-quiz aside')).display !== 'none'"
        )
        print(f"desktop aside visible: {aside_visible}, card title y={card_box['y']:.0f}, nav y={nav_box['y']:.0f}")
        if not aside_visible:
            issues.append("desktop aside should be visible")

        clip = page.evaluate(
            """() => {
              const btn = document.querySelector('#answer-card button.current');
              const box = document.querySelector('.answer-card-scroll') || document.getElementById('answer-card');
              if (!btn || !box) return { ok: false, skipped: true };
              const overflow = getComputedStyle(box).overflowY;
              if (overflow === 'visible') return { ok: true, skipped: true };
              const br = btn.getBoundingClientRect();
              const cr = box.getBoundingClientRect();
              const spread = 2;
              return {
                ok: br.top - spread >= cr.top + 0.5
                  && br.left - spread >= cr.left + 0.5
                  && br.bottom + spread <= cr.bottom + 0.5
                  && br.right + spread <= cr.right + 0.5,
                skipped: false,
              };
            }"""
        )
        print(f"desktop current highlight visible: {clip}")
        if not clip.get("ok") and not clip.get("skipped"):
            issues.append("current question highlight clipped by answer card container")

        page.locator("#btn-back").click()
        page.wait_for_selector("#quiz-list")

        # Mobile — essay review collapsible
        page.set_viewport_size({"width": 390, "height": 844})
        page.locator('[data-home-category="english"]').click()
        page.wait_for_timeout(250)
        page.locator('.quiz-item[data-file="WE Learn_B1U4_Movies_翻译题_20260703.json"]').click()
        page.wait_for_selector("#question-container")
        for _ in range(5):
            page.locator("label.option-item").first.click()
            page.wait_for_timeout(120)
        page.locator("#essay-input").fill("测试翻译")
        page.locator("#essay-input").blur()
        page.wait_for_timeout(250)
        page.locator("#btn-submit-mobile").click()
        page.wait_for_selector("#page-result")
        page.locator("#btn-review").click()
        page.wait_for_selector("#quiz-review-dock:not(.hidden)")

        page.locator("#btn-review-sheet").click()
        page.wait_for_selector("#review-sheet.open")
        page.locator('#review-sheet-grid button[data-index="5"]').click()
        page.wait_for_timeout(350)
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
