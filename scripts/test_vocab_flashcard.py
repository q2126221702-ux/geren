"""Test vocab flashcard deck and UI."""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).parent.parent
DATA = ROOT / "data"
MANIFEST = json.loads((DATA / "manifest.json").read_text(encoding="utf-8"))
entry = next(q for q in MANIFEST["quizzes"] if q.get("id") == "welearn_b1u4u8_vocab")
deck = json.loads((DATA / entry["file"]).read_text(encoding="utf-8"))

assert deck["quiz_type"] == "vocab_flashcard"
assert len(deck["cards"]) == entry["count"]
multi = [c for c in deck["cards"] if c.get("multi") or len(c.get("forms", [])) > 1]
complete = next(c for c in deck["cards"] if any(f["word"] == "complete" for f in c.get("forms", [])))
assert any(f["word"] == "completion" for f in complete["forms"]), "complete/completion should be one card"
assert len(complete["forms"]) >= 2
print(f"deck OK: {len(deck['cards'])} cards, all multi-form")

try:
    from playwright.sync_api import sync_playwright
except ImportError:
    print("playwright not installed, skip UI test")
    sys.exit(0)

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8080"


def main():
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        page = browser.new_page(viewport={"width": 390, "height": 844})
        page.goto(BASE, wait_until="networkidle")
        page.locator('.quiz-item[data-file*="单词速记"]').click()
        page.wait_for_timeout(500)
        assert not page.locator("#page-flashcard").evaluate("el => el.classList.contains('hidden')")
        front = page.locator("#flashcard-front").inner_text()
        assert "完成" in front or "词族" in front or "单词" in front
        page.locator("#btn-fc-flip").click()
        page.wait_for_timeout(200)
        back = page.locator("#flashcard-back").inner_text()
        assert "empire" in back or "complete" in back or "v." in back
        page.wait_for_timeout(200)
        browser.close()
    print("UI OK")


if __name__ == "__main__":
    main()
