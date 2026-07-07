"""Python 测试与校验脚本共用的题库常量."""

INDUSTRIAL_QUIZ_IDS = frozenset(
    {"profinet", "opc", "modbus", "serial", "comprehensive", "exam100"}
)
INDUSTRIAL_COUNT = len(INDUSTRIAL_QUIZ_IDS)

DEFAULT_TEST_BASE = "http://localhost:8080"
