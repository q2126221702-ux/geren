"""Shared phrase_cloze title/answer builder."""
import re


def phrase_cloze_title_answer(en: str, zh: str, tag: str) -> tuple[str, str]:
    if "..." in en or " / " in en or "/" in en:
        return f"{tag}【短语填空】\n{zh}\n请填写完整英文短语：______", en

    words = en.split()
    n = len(words)
    if n == 1:
        return f"{tag}【中→英】\n{zh}\n请填写英文：______", en
    if n == 2:
        title_line = f"{words[0]} ______"
        answer = words[1]
    elif n == 3:
        title_line = f"{words[0]} ______ {words[2]}"
        answer = words[1]
    else:
        mid = n // 2
        title_line = f"{' '.join(words[:mid])} ______ {' '.join(words[mid + 1:])}"
        answer = words[mid]

    filled = title_line.replace("______", answer)
    if re.sub(r"\s+", " ", filled).strip() != re.sub(r"\s+", " ", en).strip():
        return f"{tag}【短语填空】\n{zh}\n请填写完整英文短语：______", en

    return f"{tag}【短语填空】\n{zh}\n{title_line}", answer
