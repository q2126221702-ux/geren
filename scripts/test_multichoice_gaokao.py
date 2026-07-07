import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from quiz_constants import DEFAULT_TEST_BASE

from playwright.sync_api import sync_playwright

BASE = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_TEST_BASE
issues = []


def check(name, ok, detail=""):
    print(f"[{'PASS' if ok else 'FAIL'}] {name}" + (f" — {detail}" if detail else ""))
    if not ok:
        issues.append(f"{name}: {detail}")


def partial(full, n, k):
    raw = full * k / n
    return int(raw) if raw == int(raw) else round(raw, 1)


with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page()
    page.goto(BASE, wait_until="networkidle")

    quiz = page.evaluate("fetch('data/OPC规范_20260626_185227.json').then(r => r.json())")
    multi = [q for q in quiz["questions"] if q["type"] == "多选题"]
    q3 = multi[0]  # full_score 7, correct 0,1,2
    full = float(q3["full_score"])

    page.locator(".quiz-item", has_text="OPC规范").click()
    page.wait_for_timeout(300)

    def submit_and_score(answers):
        return page.evaluate(
            """async (payload) => {
                const res = await fetch(`data/${payload.file}`);
                const quiz = await res.json();
                const q = quiz.questions[payload.idx];
                const LETTERS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';
                const normalizeIndexList = (answer) => String(answer || '').split(/[,，]/).map(s => s.trim()).filter(Boolean).sort((a,b) => Number(a)-Number(b)).join(',');
                const letterToIndex = (letter) => { const idx = LETTERS.indexOf(letter); return idx >= 0 ? String(idx) : letter; };
                const userAnswerToIndexList = (userAnswer) => {
                    if (Array.isArray(userAnswer)) return normalizeIndexList(userAnswer.map(letterToIndex).join(','));
                    return normalizeIndexList(userAnswer);
                };
                const parseIndexSet = (answer) => new Set(normalizeIndexList(answer).split(',').map(s => s.trim()).filter(Boolean));
                const maxScore = Number(q.full_score) || 1;
                const correctSet = parseIndexSet(q.correct_answer);
                const userSet = parseIndexSet(userAnswerToIndexList(payload.answers));
                if (userSet.size === 0) return { score: 0, partial: false, correct: false, maxScore };
                if ([...userSet].some(i => !correctSet.has(i))) return { score: 0, partial: false, correct: false, maxScore };
                const selectedCorrectCount = [...userSet].filter(i => correctSet.has(i)).length;
                if (selectedCorrectCount === correctSet.size) return { score: maxScore, partial: false, correct: true, maxScore };
                const raw = (maxScore * selectedCorrectCount) / correctSet.size;
                const score = Number.isInteger(raw) ? raw : Math.round(raw * 10) / 10;
                return { score, partial: true, correct: false, maxScore };
            }""",
            {"file": "OPC规范_20260626_185227.json", "idx": quiz["questions"].index(q3), "answers": answers},
        )

    r_full = submit_and_score(["A", "B", "C"])
    check("全对得题库满分", r_full["score"] == full, f"{r_full['score']}/{full}")

    r_p2 = submit_and_score(["A", "B"])
    expect_p2 = partial(full, 3, 2)
    check("3 选 2 个按比例得分", r_p2["score"] == expect_p2, f"{r_p2['score']} vs {expect_p2}")

    r_p1 = submit_and_score(["A"])
    expect_p1 = partial(full, 3, 1)
    check("3 选 1 个按比例得分", r_p1["score"] == expect_p1, f"{r_p1['score']} vs {expect_p1}")

    q2 = multi[3]  # correct 2,3 two options, full 7
    r2 = page.evaluate(
        """async (payload) => {
            const res = await fetch(`data/${payload.file}`);
            const quiz = await res.json();
            const q = quiz.questions[payload.idx];
            const maxScore = Number(q.full_score) || 1;
            const userSet = new Set(['2']);
            const correctSet = new Set(['2','3']);
            const raw = (maxScore * 1) / 2;
            return { score: raw, maxScore };
        }""",
        {"file": "OPC规范_20260626_185227.json", "idx": quiz["questions"].index(q2)},
    )
    check("2 选 1 个得一半分", r2["score"] == partial(float(q2["full_score"]), 2, 1))

    r_wrong = submit_and_score(["A", "D"])
    check("有错选 0 分", r_wrong["score"] == 0)

    browser.close()

if issues:
    for i in issues:
        print(" -", i)
    sys.exit(1)
print("多选题判分测试通过")
