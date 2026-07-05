"""从 WE Learn iWords JSON 生成多样化填空题库."""
import json
import re
from datetime import datetime
from pathlib import Path

from phrase_cloze_helper import phrase_cloze_title_answer

SRC = Path(__file__).parent.parent.parent / "welearn-output" / "7月4日" / "WE Learn_B1U4-U8_iWords_20260704.json"
DATA_DIR = Path(__file__).parent.parent / "data"
MANIFEST = DATA_DIR / "manifest.json"
DATE_TAG = "20260704"

SINGLE_WORD_PATTERNS = ["zh2en", "en2zh", "spell", "pos", "assoc"]
PHRASE_PATTERNS = ["zh2en", "en2zh", "spell", "phrase_cloze"]

REMIX_MAP = {
    "zh2en": "en2zh",
    "en2zh": "zh2en",
    "spell": "en2zh",
    "pos": "spell",
    "assoc": "en2zh",
    "phrase_cloze": "en2zh",
}


def assoc_head(en: str) -> str:
    return en[: max(2, min(3, len(en) - 1))]


def remix_for(pattern: str) -> str:
    return REMIX_MAP.get(pattern, "zh2en")

# 英文词性 → 中文（判分时中英文缩写均接受）
POS_ZH = {
    "n.": "名词",
    "v.": "动词",
    "adj.": "形容词",
    "adv.": "副词",
    "prep.": "介词",
    "int.": "感叹词",
    "abbr.": "缩写",
    "conj.": "连词",
    "pron.": "代词",
    "num.": "数词",
    "art.": "冠词",
}


def pos_variants(pos: str) -> tuple[str, str]:
    """返回 (主推中文答案, 全部可接受答案用 ; 连接)"""
    if not pos:
        return "短语", "短语;phr"
    bare = pos.rstrip(".")
    zh = POS_ZH.get(pos) or POS_ZH.get(f"{bare}.") or pos
    accepts = []
    for item in (zh, pos, bare, f"{bare}."):
        if item and item not in accepts:
            accepts.append(item)
    return zh, ";".join(accepts)


def fill_hint_for(lang: str) -> str:
    if lang == "pos":
        return "填中文词性，如：名词、动词、形容词（也可填 n. / v. / adj.）"
    if lang == "en":
        return "填英文单词，小写即可；短语按原样填写"
    if lang == "zh":
        return "填中文释义，关键词即可"
    return "请输入答案"


def unit_id(unit_name: str) -> str:
    m = re.match(r"(B1U\d+)", unit_name, re.I)
    return f"welearn_{m.group(1).lower()}_iwords" if m else re.sub(r"\W+", "_", unit_name.lower()) + "_iwords"


def unit_file_name(unit_name: str) -> str:
    safe = unit_name.replace(" ", "_")
    return f"WE Learn_{safe}_iWords_{DATE_TAG}.json"


def zh_short(zh: str) -> str:
    return re.split(r"[，；;,/]", zh)[0].strip()


def is_phrase(en: str) -> bool:
    return " " in en.strip() or "..." in en or "/" in en


def memory_hook(pattern: str, en: str, zh: str, pos: str) -> str:
    short = zh_short(zh)
    if pattern == "zh2en":
        return f"「{short}」→ {en}（{len(en.replace(' ', ''))} 字母，首字母 {en[0].upper()}）"
    if pattern == "en2zh":
        return f"{en} = {short}（先记中文，再反推拼写）"
    if pattern == "spell":
        letters = len(en.replace(" ", ""))
        return f"逐字母：{'-'.join(en.replace(' ', ''))}（共 {letters} 字母）"
    if pattern == "pos":
        return f"{en} 是{POS_ZH.get(pos, pos or '短语')}，核心义：{short}"
    if pattern == "assoc":
        head = assoc_head(en)
        return f"联想：{head}{'_' * (len(en) - len(head))} → {en}（{short}）"
    if pattern == "phrase_cloze":
        return f"短语 {en} ↔ {short}（整句记，再拆关键词）"
    return f"{en} — {short}"


def pick_pattern(en: str, idx: int) -> str:
    if is_phrase(en):
        return PHRASE_PATTERNS[idx % len(PHRASE_PATTERNS)]
    return SINGLE_WORD_PATTERNS[idx % len(SINGLE_WORD_PATTERNS)]


def build_question(entry: dict, pattern: str, sort: int, unit: str, section: str) -> dict:
    en = entry["word"].strip()
    zh = entry.get("meaning_zh", "").strip()
    pos = entry.get("pos", "").strip()
    ph = entry.get("phonetic", "").strip()
    tag = f"【{unit} · {section}】"
    meta = {
        "en": en,
        "zh": zh,
        "pos": pos,
        "phonetic": ph,
        "pattern": pattern,
        "unit_tag": tag,
    }

    pos_line = f"{pos} " if pos else ""
    ph_line = f"  {ph}" if ph else ""

    if pattern == "zh2en":
        title = f"{tag}【中→英】\n{pos_line}{zh}{ph_line}\n请填写英文：______"
        answer = en
        lang = "en"
    elif pattern == "en2zh":
        title = f"{tag}【英→中】\n{en}{ph_line}  {pos}\n中文释义：______"
        answer = zh_short(zh)
        if "；" in zh or ";" in zh:
            answer = zh.replace("，", "；").replace(",", "；")
            parts = [p.strip() for p in re.split(r"[；;]", zh) if p.strip()]
            answer = "；".join(parts[:2]) if len(parts) > 1 else parts[0]
        lang = "zh"
    elif pattern == "spell":
        letters = len(en.replace(" ", ""))
        title = f"{tag}【拼写】\n{pos_line}{zh}\n首字母 {en[0].upper()}，共 {letters} 个字母：______"
        answer = en
        lang = "en"
    elif pattern == "pos":
        pos_zh, accept = pos_variants(pos)
        title = f"{tag}【词性】\n{en}{ph_line}  {zh}\n词性（填中文，如名词、动词、形容词）：______"
        answer = accept
        lang = "pos"
        meta["pos_zh"] = pos_zh
    elif pattern == "assoc":
        head = assoc_head(en)
        tail = "_" * max(1, len(en) - len(head))
        title = f"{tag}【联想拼写】\n{zh}（{pos or '短语'}）\n{head}{tail} 共 {len(en)} 字母：______"
        answer = en
        lang = "en"
    else:  # phrase_cloze
        title, answer = phrase_cloze_title_answer(en, zh, tag)
        lang = "en"
        meta["fill_hint"] = "只填空格处缺失的英文（一个词）"

    meta["lang"] = lang
    meta["hook"] = memory_hook(pattern, en, zh, pos)
    if pattern != "phrase_cloze":
        meta["fill_hint"] = fill_hint_for(lang)

    meta["remix_pattern"] = remix_for(pattern)

    return {
        "sort": sort,
        "type": "填空题(客观)",
        "title": title,
        "options": [""],
        "correct_answer": answer,
        "your_answer": "",
        "score": "0",
        "full_score": 1,
        "fill_hint": meta.get("fill_hint") or fill_hint_for(lang),
        "memory": meta,
    }


def convert_unit(unit: dict) -> dict:
    questions = []
    sort = 1
    idx = 0
    for sec in unit.get("sections", []):
        section = sec.get("section", "iWords")
        for entry in sec.get("words", []):
            pattern = pick_pattern(entry["word"], idx)
            questions.append(build_question(entry, pattern, sort, unit["unit"], section))
            sort += 1
            idx += 1
    return {
        "title": f"WE Learn {unit['unit']} iWords 词汇填空",
        "exported_at": datetime.now().isoformat(),
        "source": "WE Learn iWords 截图整理",
        "unit": unit["unit"],
        "quiz_type": "iwords_fill",
        "questions": questions,
    }


def update_manifest(entries: list[dict]):
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    remove_ids = {e["id"] for e in entries}
    manifest["quizzes"] = [q for q in manifest["quizzes"] if q.get("id") not in remove_ids]

    insert_at = next(
        (i for i, q in enumerate(manifest["quizzes"]) if q.get("id") == "welearn_b1u8"),
        len(manifest["quizzes"]),
    )
    insert_at += 1
    for i, entry in enumerate(entries):
        manifest["quizzes"].insert(insert_at + i, entry)

    MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")


def main():
    data = json.loads(SRC.read_text(encoding="utf-8"))
    manifest_entries = []
    for unit in data.get("units", []):
        quiz = convert_unit(unit)
        filename = unit_file_name(unit["unit"])
        out = DATA_DIR / filename
        out.write_text(json.dumps(quiz, ensure_ascii=False, indent=2), encoding="utf-8")
        manifest_entries.append(
            {
                "id": unit_id(unit["unit"]),
                "title": quiz["title"],
                "file": filename,
                "count": len(quiz["questions"]),
            }
        )
        print(f"  {filename} — {len(quiz['questions'])} 题")

    update_manifest(manifest_entries)
    print(f"共 {len(manifest_entries)} 个单元，已更新 {MANIFEST}")


if __name__ == "__main__":
    main()
