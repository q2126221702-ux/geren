"""从《高职英语课程标准2021》词汇表 PDF 文本提取词级."""
import re
import json
from pathlib import Path

# 由 ministry PDF 复制出的附录词汇区（scripts/curriculum_vocab_raw.txt）
RAW = Path(__file__).parent / "curriculum_vocab_raw.txt"
OUT = Path(__file__).parent / "curriculum_vocab_tiers.json"


def parse_vocab_text(text: str) -> dict[str, int]:
    """返回 word -> tier: 0=入学, 1=基础*, 2=拓展**"""
    tiers: dict[str, int] = {}
    for line in text.splitlines():
        line = line.strip()
        if not line or line.startswith("附录") or "本表共收" in line:
            continue
        # 去掉行首 ** / * 标记（可能多个）
        tier = 0
        while line.startswith("*"):
            tier += 1
            line = line[1:].lstrip()
        # 提取英文词（含连字符复合词）
        for token in re.findall(r"[A-Za-z][A-Za-z'-]*", line):
            w = token.lower().strip("'")
            if len(w) < 2:
                continue
            # 保留最高 tier
            tiers[w] = max(tiers.get(w, 0), min(tier, 2))
    return tiers


def main():
    if not RAW.exists():
        print(f"missing {RAW}")
        return
    tiers = parse_vocab_text(RAW.read_text(encoding="utf-8", errors="ignore"))
    OUT.write_text(json.dumps(tiers, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"parsed {len(tiers)} curriculum words -> {OUT}")


if __name__ == "__main__":
    main()
