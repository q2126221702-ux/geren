package com.xuzheng.tiyuengine.data

enum class QuestionType { SINGLE, MULTIPLE, TRUE_FALSE, FILL, ESSAY }

data class Question(
    val id: String,
    val prompt: String,
    val options: List<String>,
    val answerIndex: Int,
    val explanation: String,
    val type: QuestionType = QuestionType.SINGLE,
    val answerIndices: Set<Int> = setOf(answerIndex),
    val acceptedAnswers: List<String> = emptyList(),
    val referenceAnswer: String = "",
)

data class AnswerBundle(
    val optionAnswers: Map<String, Set<Int>> = emptyMap(),
    val textAnswers: Map<String, String> = emptyMap(),
) {
    fun isAnswered(question: Question): Boolean = when (question.type) {
        QuestionType.SINGLE, QuestionType.MULTIPLE, QuestionType.TRUE_FALSE -> optionAnswers[question.id].orEmpty().isNotEmpty()
        QuestionType.FILL, QuestionType.ESSAY -> textAnswers[question.id].orEmpty().isNotBlank()
    }
}

data class Quiz(
    val id: String,
    val title: String,
    val subtitle: String,
    val questions: List<Question>,
)

object QuizCatalog {
    val quizzes = listOf(
        Quiz(
            id = "industrial_network",
            title = "工业以太网基础",
            subtitle = "PROFINET · OPC · MODBUS",
            questions = listOf(
                Question("net_1", "PROFINET 主要用于哪类场景？", listOf("工业自动化通信", "卫星导航", "网页排版", "音频压缩"), 0, "PROFINET 是面向工业自动化的实时以太网通信标准。"),
                Question("net_2", "MODBUS TCP 默认使用的端口是？", listOf("21", "80", "443", "502"), 3, "MODBUS TCP 通常使用 TCP 502 端口。"),
                Question("net_3", "OPC UA 的一个重要特点是？", listOf("只能运行在 Windows", "跨平台并支持信息建模", "只能点对点通信", "不支持安全机制"), 1, "OPC UA 支持跨平台、安全通信与结构化信息建模。"),
                Question("net_4", "交换机根据什么转发以太网帧？", listOf("MAC 地址表", "文件名", "进程号", "屏幕分辨率"), 0, "二层交换机学习源 MAC 地址，并依据 MAC 地址表转发帧。"),
                Question("net_5", "工业网络采用环网冗余主要是为了？", listOf("增大显示面积", "降低电源电压", "提高网络可用性", "替代所有防火墙"), 2, "环网冗余可在链路故障时切换路径，提高系统可用性。"),
            ),
        ),
        Quiz(
            id = "english_basic",
            title = "英语基础练习",
            subtitle = "词汇 · 语法 · 翻译",
            questions = listOf(
                Question("eng_1", "Choose the correct form: She ___ to school every day.", listOf("go", "goes", "going", "gone"), 1, "第三人称单数的一般现在时，动词使用 goes。"),
                Question("eng_2", "“reliable”的中文含义是？", listOf("昂贵的", "可靠的", "临时的", "复杂的"), 1, "reliable 表示“可靠的、可信赖的”。"),
                Question("eng_3", "Which sentence is in the present perfect tense?", listOf("I finish it.", "I finished it.", "I have finished it.", "I will finish it."), 2, "现在完成时结构为 have/has + 过去分词。"),
                Question("eng_4", "The opposite of “increase” is ___.", listOf("improve", "decrease", "include", "create"), 1, "increase 是增加，反义词 decrease 是减少。"),
                Question("eng_5", "“Please check the network connection.” 最合适的翻译是？", listOf("请关闭网络", "请检查网络连接", "请创建新账户", "请更新题库"), 1, "check the network connection 表示检查网络连接。"),
            ),
        ),
        Quiz(
            id = "mixed_demo",
            title = "综合题型体验",
            subtitle = "多选 · 判断 · 填空 · 问答",
            questions = listOf(
                Question("mix_1", "下列哪些属于工业以太网相关技术？", listOf("PROFINET", "OPC UA", "MODBUS TCP", "JPEG"), 0, "PROFINET、OPC UA 和 MODBUS TCP 都用于工业通信场景。", QuestionType.MULTIPLE, setOf(0, 1, 2)),
                Question("mix_2", "MODBUS TCP 通常运行在 TCP/IP 网络之上。", listOf("正确", "错误"), 0, "MODBUS TCP 将 MODBUS 应用协议封装在 TCP/IP 网络中。", QuestionType.TRUE_FALSE),
                Question("mix_3", "MODBUS TCP 默认端口是 ____。", emptyList(), 0, "默认端口为 502。", QuestionType.FILL, acceptedAnswers = listOf("502")),
                Question("mix_4", "请简述工业网络采用冗余设计的主要目的。", emptyList(), 0, "问答题交卷后对照参考答案自评。", QuestionType.ESSAY, referenceAnswer = "当链路或设备发生故障时，冗余路径能够快速接替通信，从而减少停机时间，提高工业网络的可靠性和可用性。"),
            ),
        ),
    )

    fun questionById(id: String): Question? = quizzes.flatMap { it.questions }.find { it.id == id }
}

object QuizEngine {
    fun isCorrect(question: Question, answers: AnswerBundle): Boolean = when (question.type) {
        QuestionType.SINGLE, QuestionType.MULTIPLE, QuestionType.TRUE_FALSE -> answers.optionAnswers[question.id] == question.answerIndices
        QuestionType.FILL -> answers.textAnswers[question.id].orEmpty().trim().let { input -> question.acceptedAnswers.any { it.equals(input, ignoreCase = true) } }
        QuestionType.ESSAY -> false
    }

    fun score(questions: List<Question>, answers: AnswerBundle): Int =
        questions.count { isCorrect(it, answers) }
}
