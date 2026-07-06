#!/usr/bin/env python3
"""Mobile essay (translation Q6) UX audit."""
import json
import sys
from pathlib import Path

from playwright.sync_api import sync_playwright

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8080"
FILE = "WE Learn_B1U4_Movies_翻译题_20260703.json"
issues = []


def measure(page):
    return page.evaluate(
        """() => {
      const header = document.querySelector('#page-quiz header');
      const fold = document.querySelector('.essay-prompt-fold');
      const prompt = document.querySelector('.essay-prompt');
      const input = document.getElementById('essay-input');
      const bar = document.getElementById('quiz-answer-dock');
      const submit = document.getElementById('btn-submit-mobile');
      const pageEl = document.getElementById('page-quiz');
      const vh = window.innerHeight;
      const box = (el) => {
        if (!el || el.classList.contains('hidden')) return null;
        const r = el.getBoundingClientRect();
        return { top: r.top, bottom: r.bottom, height: r.height, visible: r.top >= 0 && r.bottom <= vh };
      };
      return {
        vh,
        scrollY: window.scrollY,
        fold: box(fold),
        promptOpen: fold ? fold.open : false,
        input: box(input),
        bar: box(bar),
        submit: box(submit),
        keyboardOpenClass: pageEl?.classList.contains('quiz-keyboard-open'),
        essayMobile: pageEl?.classList.contains('quiz-essay-mobile'),
      };
    }"""
    )


def add(msg):
    issues.append(msg)
    print("ISSUE:", msg)


with sync_playwright() as p:
    browser = p.chromium.launch()
    page = browser.new_page(
        viewport={"width": 390, "height": 844},
        is_mobile=True,
        has_touch=True,
    )
    page.goto(BASE, wait_until="networkidle")
    page.locator('[data-home-category="english"]').click()
    page.wait_for_timeout(250)
    page.locator(f'.quiz-item[data-file="{FILE}"]').click()
    page.wait_for_selector("#question-container")
    for _ in range(5):
        page.locator("label.option-item").first.click()
        page.wait_for_timeout(120)
    page.wait_for_selector("#essay-input")

    m0 = measure(page)
    print("on enter Q6:", json.dumps(m0, ensure_ascii=False, indent=2))

    if page.locator(".essay-prompt-fold").count() == 0:
        add("answer mode should use collapsible English prompt on mobile")
    if not m0.get("essayMobile"):
        add("page should have quiz-essay-mobile class")
    if not m0.get("bar"):
        add("mobile submit bar not visible")
    if m0.get("input") and m0["input"]["top"] > m0["vh"] * 0.58:
        add(f"textarea too low on enter (top={m0['input']['top']:.0f})")
    if m0.get("promptOpen"):
        add("English prompt should be collapsed by default on mobile")

    page.on("dialog", lambda d: d.dismiss())
    page.locator("#essay-input").click()
    page.wait_for_timeout(400)
    m1 = measure(page)
    print("after focus:", json.dumps(m1, ensure_ascii=False, indent=2))
    if m1.get("input") and m1["input"]["top"] > m1["vh"] * 0.72:
        add(f"textarea too low after focus (top={m1['input']['top']:.0f})")
    if not m1.get("keyboardOpenClass"):
        add("submit bar should hide while input focused")

    page.locator("#essay-input").blur()
    page.wait_for_timeout(250)
    page.locator("#btn-submit-mobile").click()
    page.wait_for_timeout(200)

    browser.close()

if issues:
    print("\nFAILED", len(issues))
    sys.exit(1)
print("\nOK")
