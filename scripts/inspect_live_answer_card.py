#!/usr/bin/env python3
"""Inspect live answer card current highlight rendering."""
import re
import sys
from pathlib import Path

from playwright.sync_api import sync_playwright

BASE = sys.argv[1] if len(sys.argv) > 1 else "https://q2126221702-ux.github.io/geren/"
OUT = Path(__file__).parent


def inspect(page, label):
    data = page.evaluate(
        """() => {
          const btn = document.querySelector('#answer-card button.current');
          const box = document.getElementById('answer-card');
          if (!btn || !box) return null;
          const bs = getComputedStyle(btn);
          const cs = getComputedStyle(box);
          const br = btn.getBoundingClientRect();
          const cr = box.getBoundingClientRect();
          const canvas = document.createElement('canvas');
          canvas.width = 1;
          canvas.height = 1;
          const ctx = canvas.getContext('2d');
          const sample = (x, y) => {
            ctx.clearRect(0, 0, 1, 1);
            // Not available cross-origin; use elementFromPoint colors via getComputedStyle only
            return null;
          };
          return {
            classes: btn.className,
            borderTopWidth: bs.borderTopWidth,
            borderRightWidth: bs.borderRightWidth,
            borderBottomWidth: bs.borderBottomWidth,
            borderLeftWidth: bs.borderLeftWidth,
            borderTopColor: bs.borderTopColor,
            borderRightColor: bs.borderRightColor,
            borderBottomColor: bs.borderBottomColor,
            borderLeftColor: bs.borderLeftColor,
            boxShadow: bs.boxShadow,
            overflowY: cs.overflowY,
            paddingTop: cs.paddingTop,
            btnTop: br.top,
            boxTop: cr.top,
            btnLeft: br.left,
            boxLeft: cr.left,
            topInset: br.top - cr.top,
            leftInset: br.left - cr.left,
          };
        }"""
    )
    page.locator("#answer-card").screenshot(path=str(OUT / f"_live_{label}.png"))
    print(f"\n=== {label} ===")
    print(data)


with sync_playwright() as p:
    browser = p.chromium.launch()
    page = browser.new_page(viewport={"width": 390, "height": 844})
    page.goto(BASE, wait_until="networkidle")
    html = page.content()
    print("cache:", re.search(r"app\.js\?v=(\d+)", html).group(1))
    print("css border fix:", "answer-card-btn.current { border-color" in html)

    page.locator('.quiz-item[data-file="WE Learn_B1U4_Movies_翻译题_20260703.json"]').click()
    page.wait_for_selector("#answer-card button.current")
    inspect(page, "welearn_q1")

    page.locator('#answer-card button[data-index="5"]').click()
    page.wait_for_timeout(200)
    inspect(page, "welearn_q6")

    page.locator("#btn-back").click()
    page.wait_for_selector("#quiz-list")
    page.locator('.quiz-item[data-file="OPC规范_20260626_185227.json"]').click()
    page.wait_for_selector("#answer-card button.current")
    inspect(page, "opc_q1")

    browser.close()
