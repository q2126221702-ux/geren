# -*- coding: utf-8 -*-
"""工业期末考核题库元数据：章节、难度、题型标签。供组卷与校验使用。"""
from __future__ import annotations

# 章节（教材 9787115663788 已教单元）
CHAPTERS = ("基础", "串口", "Modbus", "Profinet", "OPC")

# 难度
EASY, MEDIUM, HARD = "易", "中", "难"

# 超纲 / 禁用题（source, sort）
BANNED = {
    ("comprehensive", 141),  # PLC 编程专项
}

# (source, sort) -> {chapter, difficulty}
# 未列出的题按标题规则自动推断（见 infer_meta）
EXPLICIT_META: dict[tuple[str, int], dict] = {
    # ── 串口 unit ──
    ("serial", 1): {"chapter": "串口", "difficulty": EASY},
    ("serial", 2): {"chapter": "串口", "difficulty": EASY},
    ("serial", 3): {"chapter": "串口", "difficulty": MEDIUM},
    ("serial", 4): {"chapter": "串口", "difficulty": MEDIUM},
    ("serial", 5): {"chapter": "串口", "difficulty": EASY},
    ("serial", 6): {"chapter": "串口", "difficulty": EASY},
    ("serial", 7): {"chapter": "串口", "difficulty": MEDIUM},
    ("serial", 8): {"chapter": "串口", "difficulty": EASY},
    ("serial", 9): {"chapter": "串口", "difficulty": EASY},
    ("serial", 10): {"chapter": "串口", "difficulty": MEDIUM},
    ("serial", 11): {"chapter": "串口", "difficulty": EASY},
    ("serial", 12): {"chapter": "串口", "difficulty": MEDIUM},
    ("serial", 13): {"chapter": "串口", "difficulty": EASY},
    ("serial", 14): {"chapter": "串口", "difficulty": MEDIUM},
    ("serial", 15): {"chapter": "串口", "difficulty": HARD},
    # ── Modbus unit ──
    ("modbus", 1): {"chapter": "Modbus", "difficulty": MEDIUM},
    ("modbus", 2): {"chapter": "Modbus", "difficulty": EASY},
    ("modbus", 3): {"chapter": "Modbus", "difficulty": EASY},
    ("modbus", 4): {"chapter": "Modbus", "difficulty": MEDIUM},
    ("modbus", 5): {"chapter": "Modbus", "difficulty": MEDIUM},
    ("modbus", 6): {"chapter": "Modbus", "difficulty": MEDIUM},
    ("modbus", 7): {"chapter": "Modbus", "difficulty": EASY},
    ("modbus", 8): {"chapter": "Modbus", "difficulty": EASY},
    ("modbus", 9): {"chapter": "Modbus", "difficulty": EASY},
    ("modbus", 10): {"chapter": "Modbus", "difficulty": MEDIUM},
    ("modbus", 11): {"chapter": "Modbus", "difficulty": MEDIUM},
    ("modbus", 12): {"chapter": "Modbus", "difficulty": EASY},
    ("modbus", 13): {"chapter": "Modbus", "difficulty": EASY},
    ("modbus", 14): {"chapter": "Modbus", "difficulty": MEDIUM},
    ("modbus", 15): {"chapter": "Modbus", "difficulty": HARD},
    # ── Profinet unit ──
    ("profinet", 1): {"chapter": "Profinet", "difficulty": EASY},
    ("profinet", 2): {"chapter": "Profinet", "difficulty": MEDIUM},
    ("profinet", 3): {"chapter": "Profinet", "difficulty": EASY},
    ("profinet", 4): {"chapter": "Profinet", "difficulty": EASY},
    ("profinet", 5): {"chapter": "Profinet", "difficulty": MEDIUM},
    ("profinet", 6): {"chapter": "Profinet", "difficulty": MEDIUM},
    ("profinet", 7): {"chapter": "Profinet", "difficulty": EASY},
    ("profinet", 8): {"chapter": "Profinet", "difficulty": EASY},
    ("profinet", 9): {"chapter": "Profinet", "difficulty": HARD},
    ("profinet", 10): {"chapter": "Profinet", "difficulty": MEDIUM},
    ("profinet", 11): {"chapter": "Profinet", "difficulty": EASY},
    ("profinet", 12): {"chapter": "Profinet", "difficulty": EASY},
    ("profinet", 13): {"chapter": "Profinet", "difficulty": MEDIUM},
    ("profinet", 14): {"chapter": "Profinet", "difficulty": EASY},
    ("profinet", 15): {"chapter": "Profinet", "difficulty": EASY},
    ("profinet", 16): {"chapter": "Profinet", "difficulty": EASY},
    ("profinet", 17): {"chapter": "Profinet", "difficulty": MEDIUM},
    ("profinet", 18): {"chapter": "Profinet", "difficulty": HARD},
    ("profinet", 19): {"chapter": "Profinet", "difficulty": MEDIUM},
    ("profinet", 20): {"chapter": "Profinet", "difficulty": EASY},
    ("profinet", 21): {"chapter": "Profinet", "difficulty": HARD},
    ("profinet", 22): {"chapter": "Profinet", "difficulty": MEDIUM},
    ("profinet", 23): {"chapter": "Profinet", "difficulty": MEDIUM},
    ("profinet", 24): {"chapter": "Profinet", "difficulty": HARD},
    ("profinet", 25): {"chapter": "Profinet", "difficulty": HARD},
    # ── OPC unit ──
    ("opc", 1): {"chapter": "OPC", "difficulty": MEDIUM},
    ("opc", 2): {"chapter": "OPC", "difficulty": MEDIUM},
    ("opc", 3): {"chapter": "OPC", "difficulty": EASY},
    ("opc", 4): {"chapter": "OPC", "difficulty": EASY},
    ("opc", 5): {"chapter": "OPC", "difficulty": MEDIUM},
    ("opc", 6): {"chapter": "OPC", "difficulty": MEDIUM},
    ("opc", 7): {"chapter": "OPC", "difficulty": MEDIUM},
    ("opc", 8): {"chapter": "OPC", "difficulty": EASY},
    ("opc", 9): {"chapter": "OPC", "difficulty": MEDIUM},
    ("opc", 15): {"chapter": "OPC", "difficulty": EASY},
    ("opc", 16): {"chapter": "OPC", "difficulty": EASY},
    ("opc", 17): {"chapter": "OPC", "difficulty": MEDIUM},
    ("opc", 18): {"chapter": "OPC", "difficulty": EASY},
    ("opc", 19): {"chapter": "OPC", "difficulty": MEDIUM},
    ("opc", 20): {"chapter": "OPC", "difficulty": EASY},
    ("opc", 21): {"chapter": "OPC", "difficulty": MEDIUM},
    ("opc", 22): {"chapter": "OPC", "difficulty": EASY},
    ("opc", 23): {"chapter": "OPC", "difficulty": MEDIUM},
    ("opc", 24): {"chapter": "OPC", "difficulty": HARD},
    # ── comprehensive 单选 1-16 工业网络基础 ──
    ("comprehensive", 1): {"chapter": "基础", "difficulty": EASY},
    ("comprehensive", 2): {"chapter": "基础", "difficulty": EASY},
    ("comprehensive", 3): {"chapter": "基础", "difficulty": EASY},
    ("comprehensive", 4): {"chapter": "基础", "difficulty": MEDIUM},
    ("comprehensive", 5): {"chapter": "基础", "difficulty": EASY},
    ("comprehensive", 6): {"chapter": "基础", "difficulty": MEDIUM},
    ("comprehensive", 7): {"chapter": "基础", "difficulty": EASY},
    ("comprehensive", 8): {"chapter": "基础", "difficulty": MEDIUM},
    ("comprehensive", 9): {"chapter": "基础", "difficulty": EASY},
    ("comprehensive", 10): {"chapter": "基础", "difficulty": EASY},
    ("comprehensive", 11): {"chapter": "基础", "difficulty": EASY},
    ("comprehensive", 12): {"chapter": "基础", "difficulty": MEDIUM},
    ("comprehensive", 13): {"chapter": "基础", "difficulty": EASY},
    ("comprehensive", 14): {"chapter": "基础", "difficulty": EASY},
    ("comprehensive", 15): {"chapter": "基础", "difficulty": MEDIUM},
    ("comprehensive", 16): {"chapter": "基础", "difficulty": EASY},
    # 17-28 串口
    ("comprehensive", 17): {"chapter": "串口", "difficulty": EASY},
    ("comprehensive", 18): {"chapter": "串口", "difficulty": EASY},
    ("comprehensive", 19): {"chapter": "串口", "difficulty": EASY},
    ("comprehensive", 20): {"chapter": "串口", "difficulty": MEDIUM},
    ("comprehensive", 21): {"chapter": "串口", "difficulty": EASY},
    ("comprehensive", 22): {"chapter": "串口", "difficulty": EASY},
    ("comprehensive", 23): {"chapter": "串口", "difficulty": MEDIUM},
    ("comprehensive", 24): {"chapter": "串口", "difficulty": EASY},
    ("comprehensive", 25): {"chapter": "串口", "difficulty": MEDIUM},
    ("comprehensive", 26): {"chapter": "串口", "difficulty": MEDIUM},
    ("comprehensive", 27): {"chapter": "串口", "difficulty": EASY},
    ("comprehensive", 28): {"chapter": "串口", "difficulty": EASY},
    # 29-38 Modbus
    ("comprehensive", 29): {"chapter": "Modbus", "difficulty": EASY},
    ("comprehensive", 30): {"chapter": "Modbus", "difficulty": MEDIUM},
    ("comprehensive", 31): {"chapter": "Modbus", "difficulty": EASY},
    ("comprehensive", 32): {"chapter": "Modbus", "difficulty": EASY},
    ("comprehensive", 33): {"chapter": "Modbus", "difficulty": MEDIUM},
    ("comprehensive", 34): {"chapter": "Modbus", "difficulty": EASY},
    ("comprehensive", 35): {"chapter": "Modbus", "difficulty": MEDIUM},
    ("comprehensive", 36): {"chapter": "Modbus", "difficulty": EASY},
    ("comprehensive", 37): {"chapter": "Modbus", "difficulty": EASY},
    ("comprehensive", 38): {"chapter": "Modbus", "difficulty": MEDIUM},
    # 39-44 OPC
    ("comprehensive", 39): {"chapter": "OPC", "difficulty": EASY},
    ("comprehensive", 40): {"chapter": "OPC", "difficulty": MEDIUM},
    ("comprehensive", 41): {"chapter": "OPC", "difficulty": MEDIUM},
    ("comprehensive", 42): {"chapter": "OPC", "difficulty": EASY},
    ("comprehensive", 43): {"chapter": "OPC", "difficulty": HARD},
    ("comprehensive", 44): {"chapter": "OPC", "difficulty": MEDIUM},
    # 45-50 Profinet
    ("comprehensive", 45): {"chapter": "Profinet", "difficulty": EASY},
    ("comprehensive", 46): {"chapter": "Profinet", "difficulty": MEDIUM},
    ("comprehensive", 47): {"chapter": "Profinet", "difficulty": MEDIUM},
    ("comprehensive", 48): {"chapter": "Profinet", "difficulty": HARD},
    ("comprehensive", 49): {"chapter": "Profinet", "difficulty": MEDIUM},
    ("comprehensive", 50): {"chapter": "Profinet", "difficulty": MEDIUM},
    # 填空 51-66 基础
    **{( "comprehensive", s): {"chapter": "基础", "difficulty": EASY} for s in range(51, 67)},
    # 填空 67-74 串口
    **{( "comprehensive", s): {"chapter": "串口", "difficulty": EASY} for s in range(67, 75)},
    # 填空 79-86 Modbus
    **{( "comprehensive", s): {"chapter": "Modbus", "difficulty": EASY if s < 83 else MEDIUM} for s in range(79, 87)},
    # 填空 89-94 OPC
    ("comprehensive", 89): {"chapter": "OPC", "difficulty": EASY},
    ("comprehensive", 90): {"chapter": "OPC", "difficulty": EASY},
    ("comprehensive", 91): {"chapter": "OPC", "difficulty": EASY},
    ("comprehensive", 92): {"chapter": "OPC", "difficulty": MEDIUM},
    ("comprehensive", 93): {"chapter": "OPC", "difficulty": EASY},
    ("comprehensive", 94): {"chapter": "OPC", "difficulty": MEDIUM},
    # 填空 95-100 Profinet
    **{( "comprehensive", s): {"chapter": "Profinet", "difficulty": EASY if s < 98 else MEDIUM} for s in range(95, 101)},
    # 判断 101-110 基础
    ("comprehensive", 101): {"chapter": "基础", "difficulty": EASY},
    ("comprehensive", 102): {"chapter": "基础", "difficulty": EASY},
    ("comprehensive", 103): {"chapter": "基础", "difficulty": EASY},
    ("comprehensive", 104): {"chapter": "基础", "difficulty": MEDIUM},
    ("comprehensive", 105): {"chapter": "基础", "difficulty": EASY},
    ("comprehensive", 106): {"chapter": "基础", "difficulty": EASY},
    ("comprehensive", 107): {"chapter": "基础", "difficulty": MEDIUM},
    ("comprehensive", 108): {"chapter": "基础", "difficulty": EASY},
    ("comprehensive", 109): {"chapter": "基础", "difficulty": MEDIUM},
    ("comprehensive", 110): {"chapter": "基础", "difficulty": MEDIUM},
    # 判断 111-118 串口
    ("comprehensive", 111): {"chapter": "串口", "difficulty": EASY},
    ("comprehensive", 112): {"chapter": "串口", "difficulty": MEDIUM},
    ("comprehensive", 113): {"chapter": "串口", "difficulty": EASY},
    ("comprehensive", 114): {"chapter": "串口", "difficulty": MEDIUM},
    ("comprehensive", 115): {"chapter": "串口", "difficulty": MEDIUM},
    ("comprehensive", 116): {"chapter": "串口", "difficulty": EASY},
    ("comprehensive", 117): {"chapter": "串口", "difficulty": EASY},
    ("comprehensive", 118): {"chapter": "串口", "difficulty": EASY},
    # 判断 119-126 Modbus
    ("comprehensive", 119): {"chapter": "Modbus", "difficulty": MEDIUM},
    ("comprehensive", 120): {"chapter": "Modbus", "difficulty": EASY},
    ("comprehensive", 121): {"chapter": "OPC", "difficulty": MEDIUM},
    ("comprehensive", 122): {"chapter": "Profinet", "difficulty": MEDIUM},
    ("comprehensive", 123): {"chapter": "Modbus", "difficulty": EASY},
    ("comprehensive", 124): {"chapter": "OPC", "difficulty": EASY},
    ("comprehensive", 125): {"chapter": "Profinet", "difficulty": MEDIUM},
    ("comprehensive", 126): {"chapter": "Modbus", "difficulty": MEDIUM},
    # 判断 127-130
    ("comprehensive", 127): {"chapter": "Profinet", "difficulty": EASY},
    ("comprehensive", 128): {"chapter": "串口", "difficulty": MEDIUM},
    ("comprehensive", 129): {"chapter": "基础", "difficulty": MEDIUM},
    ("comprehensive", 130): {"chapter": "基础", "difficulty": EASY},
    # 问答
    ("comprehensive", 131): {"chapter": "串口", "difficulty": MEDIUM},
    ("comprehensive", 132): {"chapter": "Modbus", "difficulty": MEDIUM},
    ("comprehensive", 133): {"chapter": "基础", "difficulty": HARD},
    ("comprehensive", 134): {"chapter": "Modbus", "difficulty": HARD},
    ("comprehensive", 135): {"chapter": "串口", "difficulty": HARD},
    ("comprehensive", 136): {"chapter": "OPC", "difficulty": MEDIUM},
    ("comprehensive", 137): {"chapter": "Profinet", "difficulty": HARD},
    ("comprehensive", 138): {"chapter": "串口", "difficulty": MEDIUM},
    ("comprehensive", 139): {"chapter": "基础", "difficulty": HARD},
    ("comprehensive", 140): {"chapter": "基础", "difficulty": HARD},
}


def infer_chapter(title: str, qtype: str, source: str) -> str:
    t = title.lower()
    if any(k in title for k in ("RS232", "RS485", "RS-232", "RS-485", "串口", "波特率", "终端电阻")):
        return "串口"
    if "modbus" in t or "功能码" in title or "寄存器" in title or "crc" in t:
        return "Modbus"
    if "profinet" in t or "irt" in t or "mrp" in t or "dcp" in t:
        return "Profinet"
    if "opc" in t or "dcom" in t or "62541" in title:
        return "OPC"
    if source == "serial":
        return "串口"
    if source == "modbus":
        return "Modbus"
    if source == "profinet":
        return "Profinet"
    if source == "opc":
        return "OPC"
    return "基础"


def infer_difficulty(title: str, qtype: str, source: str, sort: int) -> str:
    if qtype == "问答题":
        if "综合" in title or "层级" in title or "混用" in title or "五大" in title or "完整流程" in title:
            return HARD
        if "简述" in title and len(title) > 20:
            return MEDIUM
        return HARD
    if qtype == "判断题":
        if any(k in title for k in ("只能", "必须", "所有", "随意", "远大于", "短于", "主动")):
            return MEDIUM
        return EASY
    if qtype == "填空题(客观)":
        if sort >= 95:
            return MEDIUM
        return EASY
    if qtype == "单选题":
        if any(k in title for k in ("故障代码", "兼容", "最小", "区别于", "本质", "错误的是")):
            return HARD
        if any(k in title for k in ("对比", "相较", "核心优势", "正确的是", "不包含")):
            return MEDIUM
        return EASY
    return MEDIUM


def get_meta(source: str, sort: int, title: str, qtype: str) -> dict:
    key = (source, sort)
    if key in EXPLICIT_META:
        return dict(EXPLICIT_META[key])
    return {
        "chapter": infer_chapter(title, qtype, source),
        "difficulty": infer_difficulty(title, qtype, source, sort),
    }
