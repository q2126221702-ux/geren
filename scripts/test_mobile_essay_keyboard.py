#!/usr/bin/env python3
"""Simulate mobile keyboard opening on essay translation input."""
import json
import sys
from pathlib import Path

from playwright.sync_api import sync_playwright

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8080"
FILE = "WE Learn_B1U4_Movies_翻译题_20260703.json"
FULL_H = 844
KEYBOARD_H = 380
issues = []


def measure(page):
    return page.evaluate(
        """() => {
      const vv = window.visualViewport;
      const input = document.getElementById('essay-input');
      const bar = document.getElementById('quiz-answer-dock');
      const pageEl = document.getElementById('page-quiz');
      const header = document.querySelector('#page-quiz header');
      const box = (el) => {
        if (!el) return null;
        const hidden = el.classList.contains('hidden') || getComputedStyle(el).display === 'none';
        const r = el.getBoundingClientRect();
        return { top: r.top, bottom: r.bottom, height: r.height, hidden };
      };
      const inRect = input ? input.getBoundingClientRect() : null;
      const viewBottom = vv ? vv.height + vv.offsetTop : window.innerHeight;
      const headerBottom = header ? header.getBoundingClientRect().bottom : 0;
      const margin = 12;
      const inputVisibleInViewport =
        inRect &&
        inRect.top >= headerBottom - 4 &&
        inRect.bottom <= viewBottom - margin;
      const coveredByBar =
        bar &&
        !bar.classList.contains('hidden') &&
        getComputedStyle(bar).display !== 'none' &&
        inRect &&
        inRect.bottom > bar.getBoundingClientRect().top - 4;
      return {
        innerHeight: window.innerHeight,
        vvHeight: vv?.height,
        vvOffsetTop: vv?.offsetTop,
        viewBottom,
        keyboardOpenClass: pageEl?.classList.contains('quiz-keyboard-open'),
        active: document.activeElement?.id || null,
        input: box(input),
        bar: box(bar),
        inputVisibleInViewport,
        coveredByBar,
        scrollY: window.scrollY,
      };
    }"""
    )


def add(msg, data=None):
    issues.append(msg)
    print("ISSUE:", msg, data or "")


with sync_playwright() as p:
    browser = p.chromium.launch()
    page = browser.new_page(
        viewport={"width": 390, "height": FULL_H},
        is_mobile=True,
        has_touch=True,
    )
    page.goto(BASE, wait_until="networkidle")
    page.locator(f'.quiz-item[data-file="{FILE}"]').click()
    page.wait_for_selector("#question-container")
    for _ in range(5):
        page.locator("label.option-item").first.click()
        page.wait_for_timeout(120)
    page.wait_for_selector("#essay-input")

    before = measure(page)
    print("before focus:", json.dumps(before, ensure_ascii=False))

    page.locator("#essay-input").click()
    page.wait_for_timeout(350)

    mid = measure(page)
    print("after focus (keyboard closed):", json.dumps(mid, ensure_ascii=False))

    if mid.get("active") != "essay-input":
        add("essay input not focused after click")
    if not mid.get("keyboardOpenClass"):
        add("should hide chrome immediately on focus")

    page.set_viewport_size({"width": 390, "height": KEYBOARD_H})
    page.evaluate(
        """() => {
      window.dispatchEvent(new Event('resize'));
      if (window.visualViewport) window.visualViewport.dispatchEvent(new Event('resize'));
    }"""
    )
    page.wait_for_timeout(450)

    after = measure(page)
    print("after keyboard (viewport shrunk):", json.dumps(after, ensure_ascii=False))

    if after.get("input") and not after.get("inputVisibleInViewport"):
        add(
            "textarea not fully visible when keyboard open",
            {
                "inputBottom": after["input"]["bottom"],
                "viewBottom": after.get("viewBottom"),
            },
        )
    if after.get("coveredByBar"):
        add("sticky submit bar covers textarea when keyboard open")
    if after.get("bar") and not after["bar"].get("hidden") and not after.get("keyboardOpenClass"):
        add("mobile bar still visible during keyboard without keyboard-open class")

    page.screenshot(path=str(Path(__file__).parent / "_mobile_essay_keyboard.png"))

    page.set_viewport_size({"width": 390, "height": FULL_H})
    page.locator("#essay-input").blur()
    page.wait_for_timeout(200)
    restored = measure(page)
    print("after keyboard dismissed:", json.dumps(restored, ensure_ascii=False))
    if restored.get("bar") and restored["bar"].get("hidden"):
        add("submit bar did not restore after keyboard dismiss")

    browser.close()

if issues:
    print("\nFAILED", len(issues))
    sys.exit(1)
print("\nOK")
