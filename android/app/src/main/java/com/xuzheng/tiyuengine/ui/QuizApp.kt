package com.xuzheng.tiyuengine.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import com.xuzheng.tiyuengine.data.AppUpdater
import com.xuzheng.tiyuengine.data.NetworkMonitor
import com.xuzheng.tiyuengine.data.UpdateInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xuzheng.tiyuengine.data.Question
import com.xuzheng.tiyuengine.data.QuestionType
import com.xuzheng.tiyuengine.data.Quiz
import com.xuzheng.tiyuengine.data.AnswerBundle
import com.xuzheng.tiyuengine.data.QuizRepository
import com.xuzheng.tiyuengine.data.SyncResult
import com.xuzheng.tiyuengine.data.QuizEngine
import com.xuzheng.tiyuengine.data.WrongBookStore
import com.xuzheng.tiyuengine.data.WrongItem
import com.xuzheng.tiyuengine.data.ReviewStatus
import com.xuzheng.tiyuengine.data.LearningRecord
import com.xuzheng.tiyuengine.data.QuestionAttempt
import com.xuzheng.tiyuengine.data.LearningStats
import com.xuzheng.tiyuengine.data.LearningStore
import com.xuzheng.tiyuengine.data.FavoriteStore
import com.xuzheng.tiyuengine.data.AiSettingsStore
import com.xuzheng.tiyuengine.data.AiClient
import com.xuzheng.tiyuengine.data.AiMode
import com.xuzheng.tiyuengine.data.AiQuestionResult
import com.xuzheng.tiyuengine.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal enum class Screen {
    HOME, QUIZZES, ANSWER, RESULT, WRONG_BOOK, FAVORITES, PROFILE,
    SETTINGS, BACKUP, UPDATE, ABOUT, AI_SETTINGS, LEARNING_REPORT,
}

internal fun shouldAutoAdvance(
    questionType: QuestionType,
    previousAnswer: Set<Int>,
    selectedOption: Int,
    questionIndex: Int,
    lastQuestionIndex: Int,
): Boolean = questionType in setOf(QuestionType.SINGLE, QuestionType.TRUE_FALSE) &&
    previousAnswer != setOf(selectedOption) &&
    questionIndex < lastQuestionIndex

@Composable
fun QuizApp() {
    val context = LocalContext.current
    ProvideAppColors {
        val colors = appColors()
        val snackbarHostState = remember { SnackbarHostState() }
        val messenger = rememberAppMessenger(snackbarHostState)
        CompositionLocalProvider(LocalAppMessenger provides messenger) {
            QuizAppContent(snackbarHostState, colors)
        }
    }
}

@Composable
private fun QuizAppContent(snackbarHostState: SnackbarHostState, colors: AppColors) {
    val context = LocalContext.current
    val messenger = LocalAppMessenger.current
    var isOnline by remember { mutableStateOf(NetworkMonitor.isOnline(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            isOnline = NetworkMonitor.isOnline(context)
            kotlinx.coroutines.delay(3_000)
        }
    }
    val store = remember(context) { WrongBookStore(context) }
    val learningStore = remember(context) { LearningStore(context) }
    val favoriteStore = remember(context) { FavoriteStore(context) }
    val quizRepository = remember(context) { QuizRepository(context) }
    var quizzes by remember { mutableStateOf(quizRepository.loadQuizzes()) }
    var screen by remember { mutableStateOf(Screen.HOME) }
    var quizOrigin by remember { mutableStateOf(Screen.HOME) }
    var showAnswerExitConfirm by remember { mutableStateOf(false) }
    var pendingExamVariants by remember { mutableStateOf<List<Quiz>?>(null) }
    var openUpdateOnLaunch by remember { mutableStateOf(false) }
    var activeQuiz by remember { mutableStateOf<Quiz?>(null) }
    var result by remember { mutableIntStateOf(0) }
    var lastAnswers by remember { mutableStateOf(AnswerBundle()) }
    var wrongItems by remember { mutableStateOf(store.loadItems()) }
    var learningRecords by remember { mutableStateOf(learningStore.load()) }
    var lastDurationSeconds by remember { mutableStateOf(0L) }
    var favoriteIds by remember { mutableStateOf(favoriteStore.loadIds()) }
    val dueQuestions = wrongItems.filter { it.status != ReviewStatus.MASTERED && it.nextReviewAt <= System.currentTimeMillis() }
        .mapNotNull { item -> quizzes.flatMap { it.questions }.find { it.id == item.questionId } }
    val recommendedQuiz = if (dueQuestions.isNotEmpty()) Quiz("wrong_review", "今日错题复练", "到期错题优先巩固", dueQuestions)
        else quizzes.find { it.id == LearningStats.recommendedQuizId(learningRecords, quizzes) } ?: quizzes.firstOrNull()

    fun openQuiz(quiz: Quiz, from: Screen) {
        activeQuiz = quiz
        quizOrigin = from
        screen = Screen.ANSWER
    }

    fun leaveQuizFlow() {
        screen = quizOrigin
        activeQuiz = null
    }

    val goHome = { screen = Screen.HOME }
    BackHandler(enabled = screen != Screen.HOME || showAnswerExitConfirm) {
        when {
            showAnswerExitConfirm -> showAnswerExitConfirm = false
            screen == Screen.ANSWER -> showAnswerExitConfirm = true
            screen == Screen.RESULT -> leaveQuizFlow()
            screen == Screen.AI_SETTINGS || screen == Screen.BACKUP || screen == Screen.UPDATE || screen == Screen.ABOUT -> screen = Screen.SETTINGS
            screen == Screen.SETTINGS || screen == Screen.LEARNING_REPORT -> screen = Screen.PROFILE
            screen == Screen.FAVORITES -> screen = Screen.HOME
            else -> screen = Screen.HOME
        }
    }

    if (showAnswerExitConfirm) {
        AlertDialog(
            onDismissRequest = { showAnswerExitConfirm = false },
            title = { Text("确定退出练习？") },
            text = { Text("退出后本次作答进度不会保存。") },
            confirmButton = { Button(onClick = { showAnswerExitConfirm = false; leaveQuizFlow() }) { Text("退出") } },
            dismissButton = { TextButton(onClick = { showAnswerExitConfirm = false }) { Text("继续答题") } },
        )
    }

    pendingExamVariants?.let { variants ->
        ExamPickerSheet(
            variants = variants,
            onDismiss = { pendingExamVariants = null },
            onSelect = { quiz ->
                pendingExamVariants = null
                openQuiz(quiz, Screen.QUIZZES)
            },
        )
    }

    LaunchedEffect(openUpdateOnLaunch) {
        if (openUpdateOnLaunch) {
            openUpdateOnLaunch = false
            screen = Screen.UPDATE
        }
    }

    QuizAppShell(screen = screen, isOnline = isOnline, snackbarHostState = snackbarHostState, onOpenUpdate = { openUpdateOnLaunch = true }) { targetScreen ->
    when (targetScreen) {
        Screen.HOME -> HomeScreen(
            wrongCount = wrongItems.count { it.status != ReviewStatus.MASTERED && it.nextReviewAt <= System.currentTimeMillis() },
            learningRecords = learningRecords,
            quizzes = quizzes,
            recommendedQuiz = recommendedQuiz,
            onStart = { recommendedQuiz?.let { openQuiz(it, Screen.HOME) } },
            onLibrary = { screen = Screen.QUIZZES },
            onWrongBook = { screen = Screen.WRONG_BOOK },
            onFavorites = { screen = Screen.FAVORITES },
            onProfile = { screen = Screen.PROFILE },
            onQuizSelect = { openQuiz(it, Screen.HOME) },
        )
        Screen.QUIZZES -> QuizListScreen(
            quizzes = quizzes,
            onHome = goHome,
            onWrongBook = { screen = Screen.WRONG_BOOK },
            onProfile = { screen = Screen.PROFILE },
            onSelect = { openQuiz(it, Screen.QUIZZES) },
            onOpenExamPack = { pendingExamVariants = it },
        )
        Screen.ANSWER -> activeQuiz?.let { quiz ->
            AnswerScreen(
                quiz = quiz,
                favoriteIds = favoriteIds,
                onToggleFavorite = { questionId -> favoriteIds = favoriteStore.toggle(questionId) },
                onBack = { showAnswerExitConfirm = true },
            ) { answers, durationSeconds ->
                result = QuizEngine.score(quiz.questions, answers)
                lastAnswers = answers
                lastDurationSeconds = durationSeconds
                store.updateAfterSubmission(quiz.questions, answers, isReview = quiz.id == "wrong_review")
                wrongItems = store.loadItems()
                val objectiveCount = quiz.questions.count { it.type != QuestionType.ESSAY }
                val attempts = quiz.questions.filter { it.type != QuestionType.ESSAY }.map { question ->
                    QuestionAttempt(
                        question.id,
                        question.type,
                        QuizEngine.isCorrect(question, answers),
                        answers.optionAnswers[question.id]?.sorted()?.joinToString(",") ?: answers.textAnswers[question.id].orEmpty(),
                    )
                }
                learningStore.add(LearningRecord(quiz.id, quiz.title, result, objectiveCount, quiz.questions.size, durationSeconds, System.currentTimeMillis(), attempts))
                learningRecords = learningStore.load()
                screen = Screen.RESULT
            }
        }
        Screen.RESULT -> activeQuiz?.let { quiz ->
            ResultScreen(
                quiz = quiz,
                score = result,
                answers = lastAnswers,
                durationSeconds = lastDurationSeconds,
                favoriteIds = favoriteIds,
                onToggleFavorite = { questionId -> favoriteIds = favoriteStore.toggle(questionId) },
                isOnline = isOnline,
                onHome = { leaveQuizFlow() },
                onRetry = { screen = Screen.ANSWER },
                onWrongBook = { leaveQuizFlow(); screen = Screen.WRONG_BOOK },
            )
        }
        Screen.WRONG_BOOK -> WrongBookScreen(
            quizzes = quizzes,
            wrongItems = wrongItems,
            onHome = goHome,
            onLibrary = { screen = Screen.QUIZZES },
            onProfile = { screen = Screen.PROFILE },
            onPractice = { questions ->
                openQuiz(Quiz("wrong_review", "错题复练", "连续答对 3 次后归档", questions), Screen.WRONG_BOOK)
            },
        )
        Screen.FAVORITES -> FavoritesScreen(
            quizzes = quizzes,
            favoriteIds = favoriteIds,
            onToggleFavorite = { questionId -> favoriteIds = favoriteStore.toggle(questionId) },
            onHome = goHome,
            onLibrary = { screen = Screen.QUIZZES },
            onWrongBook = { screen = Screen.WRONG_BOOK },
            onProfile = { screen = Screen.PROFILE },
            onPractice = { questions ->
                openQuiz(Quiz("favorites_review", "收藏练习", "集中巩固收藏题目", questions), Screen.FAVORITES)
            },
        )
        Screen.PROFILE -> ProfileScreen(
            records = learningRecords,
            wrongItems = wrongItems,
            quizCount = quizzes.size,
            questionCount = quizzes.sumOf { it.questions.size },
            lastSyncedAt = quizRepository.lastSyncedAt(),
            isOnline = isOnline,
            onSync = {
                if (!NetworkMonitor.isOnline(context)) error("当前无网络，请检查连接后重试")
                val result = withContext(Dispatchers.IO) { quizRepository.syncFromGithub() }
                quizzes = quizRepository.loadQuizzes()
                result
            },
            onSyncComplete = { messenger.show("同步完成：${it.quizCount} 套 · ${it.questionCount} 题") },
            onSyncFailed = { messenger.show(it) },
            onHome = goHome,
            onLibrary = { screen = Screen.QUIZZES },
            onWrongBook = { screen = Screen.WRONG_BOOK },
            onSettings = { screen = Screen.SETTINGS },
            onLearningReport = { screen = Screen.LEARNING_REPORT },
        )
        Screen.SETTINGS -> SettingsScreen(
            onBack = { screen = Screen.PROFILE },
            onBackup = { screen = Screen.BACKUP },
            onAiSettings = { screen = Screen.AI_SETTINGS },
            onUpdate = { screen = Screen.UPDATE },
            onAbout = { screen = Screen.ABOUT },
        )
        Screen.BACKUP -> BackupSettingsScreen(
            onBack = { screen = Screen.SETTINGS },
            onDataImported = {
                learningRecords = learningStore.load()
                wrongItems = store.loadItems()
                favoriteIds = favoriteStore.loadIds()
            },
        )
        Screen.UPDATE -> UpdateSettingsScreen(onBack = { screen = Screen.SETTINGS })
        Screen.ABOUT -> AboutScreen(onBack = { screen = Screen.SETTINGS })
        Screen.AI_SETTINGS -> AiSettingsScreen(onBack = { screen = Screen.SETTINGS })
        Screen.LEARNING_REPORT -> LearningReportScreen(
            records = learningRecords,
            quizzes = quizzes,
            wrongItems = wrongItems,
            onBack = { screen = Screen.PROFILE },
        )
    }
    }
}

@Composable
private fun HomeScreen(wrongCount: Int, learningRecords: List<LearningRecord>, quizzes: List<Quiz>, recommendedQuiz: Quiz?, onStart: () -> Unit, onLibrary: () -> Unit, onWrongBook: () -> Unit, onFavorites: () -> Unit, onProfile: () -> Unit, onQuizSelect: (Quiz) -> Unit) {
    var selectedTab by remember { mutableStateOf("最近") }
    val regularQuizzes = remember(quizzes) { quizzes.filter { !it.id.startsWith("exam100_") } }
    val visibleQuizzes = when (selectedTab) {
        "专项" -> regularQuizzes.drop(1).take(4)
        else -> regularQuizzes.take(5)
    }
    Scaffold(
        containerColor = Color(0xFFF6F8FB),
        bottomBar = { AppBottomBar(Screen.HOME, onHome = {}, onLibrary = onLibrary, onWrongBook = onWrongBook, onProfile = onProfile) },
    ) { padding ->
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 20.dp, vertical = 22.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column { Text("题域引擎", fontSize = 31.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0D2748)); Text("工业网络 · 英语备考", color = Color(0xFF64748B), fontSize = 15.sp) }
                    Surface(onClick = onWrongBook, color = Color(0xFFFFF5E8), shape = RoundedCornerShape(12.dp)) { Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.NotificationsNone, null, tint = Color(0xFFC66A12), modifier = Modifier.size(20.dp)); Text("${wrongCount} 题待复习", modifier = Modifier.padding(start = 5.dp), color = Color(0xFF7A4A18), fontSize = 13.sp) } }
                }
            }
        }
        item {
            Card(Modifier.padding(horizontal = 16.dp, vertical = 16.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF082E59)), shape = RoundedCornerShape(20.dp)) {
                Box(Modifier.fillMaxWidth()) {
                    Image(painterResource(R.drawable.network_topology_motif), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize().alpha(.52f))
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Surface(color = Color(0xFF00A7D6), shape = RoundedCornerShape(7.dp)) { Text("推荐练习", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), fontSize = 13.sp) }
                    Text(recommendedQuiz?.title ?: "选择一套练习", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) { HeroMeta(Icons.Default.Description, "${recommendedQuiz?.questions?.size ?: 0} 道题"); HeroMeta(Icons.Default.AccessTime, "约 ${recommendedQuiz?.questions?.size?.coerceAtLeast(1) ?: 1} 分钟") }
                    Text(recommendedQuiz?.subtitle ?: "题库准备中", color = Color(0xFFC9D8E8), fontSize = 14.sp)
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF315474)))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.SignalCellularAlt, null, tint = Color(0xFF16BDEB)); Text(" 准备就绪", color = Color(0xFFDCE8F3), fontSize = 14.sp) }
                        Button(onClick = onStart, shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.PlayArrow, null); Text("开始练习", modifier = Modifier.padding(start = 5.dp)) }
                    }
                }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Surface(color = Color.White, shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) { listOf("最近", "收藏", "专项").forEach { tab -> TextButton(onClick = { if (tab == "收藏") onFavorites() else selectedTab = tab }, modifier = Modifier.weight(1f)) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(tab, color = if (selectedTab == tab) Color(0xFF0759BD) else Color(0xFF526174), fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal); if (selectedTab == tab) Box(Modifier.padding(top = 8.dp).height(3.dp).fillMaxWidth(.38f).background(Color(0xFF0759BD), RoundedCornerShape(2.dp))) } } } }
                }
                Column(Modifier.fillMaxWidth().background(Color.White)) {
                    visibleQuizzes.forEachIndexed { listIndex, quiz ->
                        PracticeRow(
                            quiz = quiz,
                            index = listIndex,
                            wrongCount = wrongCount,
                            mastery = LearningStats.masteryPercent(
                                learningRecords,
                                quiz.id,
                                quiz.questions.count { it.type != QuestionType.ESSAY },
                            ),
                            onClick = { onQuizSelect(quiz) },
                        )
                        if (listIndex < visibleQuizzes.lastIndex) Box(Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(1.dp).background(Color(0xFFE4E9F0)))
                    }
                    TextButton(onClick = onLibrary, modifier = Modifier.fillMaxWidth().padding(6.dp)) { Text("查看全部练习"); Icon(Icons.Default.ChevronRight, null) }
                }
            }
        }
        item { Spacer(Modifier.height(18.dp)) }
    }
    }
}

@Composable
private fun HeroMeta(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(19.dp)); Text(text, color = Color.White, modifier = Modifier.padding(start = 6.dp), fontSize = 14.sp) }
}

@Composable
private fun PracticeRow(quiz: Quiz, index: Int, wrongCount: Int, mastery: Int?, onClick: () -> Unit) {
    val icon = when (index % 3) { 0 -> Icons.Default.Description; 1 -> Icons.Default.Hub; else -> Icons.Default.ChatBubbleOutline }
    val tint = when (index % 3) { 0 -> Color(0xFF0759BD); 1 -> Color(0xFF138A5B); else -> Color(0xFF6B4CC5) }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).background(tint.copy(alpha = .1f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp)) }
        Column(Modifier.padding(start = 12.dp).weight(1f)) { Text(quiz.title, fontWeight = FontWeight.Bold, fontSize = 15.sp); Text("${quiz.subtitle} · ${quiz.questions.size} 题", color = Color(0xFF64748B), fontSize = 12.sp, maxLines = 1); Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.AccessTime, null, tint = Color(0xFF64748B), modifier = Modifier.size(14.dp)); Text(" 约 5 分钟", color = Color(0xFF64748B), fontSize = 11.sp) } }
        Column(horizontalAlignment = Alignment.End) { Text(if (wrongCount > 0 && index == 0) "待巩固 $wrongCount 题" else "可开始", color = if (wrongCount > 0 && index == 0) Color(0xFFC65D08) else Color(0xFF138A5B), fontSize = 13.sp); Text(mastery?.let { "掌握度 $it%" } ?: "尚未测评", color = Color(0xFF64748B), fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp)); Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF64748B), modifier = Modifier.padding(top = 5.dp)) }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.background(Color.White, RoundedCornerShape(16.dp)).padding(18.dp)) { Text(value, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Color(0xFF123A70)); Text(label, color = Color(0xFF697586)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuizListScreen(
    quizzes: List<Quiz>,
    onHome: () -> Unit,
    onWrongBook: () -> Unit,
    onProfile: () -> Unit,
    onSelect: (Quiz) -> Unit,
    onOpenExamPack: (List<Quiz>) -> Unit,
) {
    val colors = appColors()
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("全部") }
    val entries = remember(quizzes) { buildLibraryEntries(quizzes) }
    val filtered = entries.filter { entry ->
        (category == "全部" || entry.subtitle.startsWith(category)) &&
            (query.isBlank() || entry.title.contains(query, true) || entry.subtitle.contains(query, true))
    }
    Scaffold(containerColor = colors.surfaceMuted, topBar = { TopAppBar(title = { Column { Text("题库", fontWeight = FontWeight.Bold, fontSize = 25.sp); Text("按目标选择练习", fontSize = 13.sp, color = colors.textSecondary) } }) }, bottomBar = { AppBottomBar(Screen.QUIZZES, onHome, {}, onWrongBook, onProfile) }) { padding ->
        LazyColumn(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            item { Column(Modifier.background(colors.surface).padding(horizontal = 18.dp, vertical = 14.dp)) { OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("搜索题库、协议或知识点") }, singleLine = true, shape = RoundedCornerShape(14.dp)); Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("全部", "工业网络", "英语").forEach { item -> Surface(onClick = { category = item }, color = if (category == item) colors.primary else colors.primarySoft, shape = RoundedCornerShape(10.dp)) { Text(item, color = if (category == item) colors.onPrimary else colors.textSecondary, modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp), fontWeight = if (category == item) FontWeight.Bold else FontWeight.Normal) } } } } }
            item { Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 17.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("${filtered.size} 套练习", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = colors.textPrimary); Text("离线可用", color = colors.success, fontSize = 13.sp) } }
            if (filtered.isEmpty()) item { Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Search, null, tint = colors.textSecondary, modifier = Modifier.size(42.dp)); Text("没有找到相关题库", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp), color = colors.textPrimary); TextButton(onClick = { query = ""; category = "全部" }) { Text("清除筛选") } } }
            items(filtered, key = { it.id }) { entry ->
                LibraryEntryRow(entry) {
                    if (entry.examVariants != null) onOpenExamPack(entry.examVariants) else entry.quiz?.let(onSelect)
                }
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

@Composable
private fun LibraryEntryRow(entry: LibraryEntry, onClick: () -> Unit) {
    val colors = appColors()
    val industrial = entry.subtitle.startsWith("工业网络")
    Row(Modifier.fillMaxWidth().background(colors.surface).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 17.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(50.dp).background(if (industrial) Color(0xFFE4F5F2) else Color(0xFFEEF0FF), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Icon(if (entry.examVariants != null) Icons.Default.Description else if (industrial) Icons.Default.Hub else Icons.Default.ChatBubbleOutline, null, tint = if (entry.examVariants != null) colors.primary else if (industrial) colors.success else Color(0xFF6550C9))
        }
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = colors.textPrimary)
                if (entry.examVariants != null) Surface(color = colors.primarySoft, shape = RoundedCornerShape(5.dp), modifier = Modifier.padding(start = 7.dp)) {
                    Text("${entry.examVariants.size} 套", color = colors.primary, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                }
            }
            Text(entry.subtitle, color = colors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp))
            Text("${entry.questionCount} 题 · 约 5 分钟", color = colors.primary, fontSize = 12.sp, modifier = Modifier.padding(top = 7.dp))
        }
        Icon(Icons.Default.ChevronRight, null, tint = colors.textSecondary)
    }
    Box(Modifier.padding(start = 82.dp, end = 18.dp).fillMaxWidth().height(1.dp).background(colors.border))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnswerScreen(quiz: Quiz, favoriteIds: Set<String>, onToggleFavorite: (String) -> Unit, onBack: () -> Unit, onSubmit: (AnswerBundle, Long) -> Unit) {
    val startedAt = remember(quiz.id) { System.currentTimeMillis() }
    val optionAnswers = remember(quiz.id) { mutableStateMapOf<String, Set<Int>>() }
    val textAnswers = remember(quiz.id) { mutableStateMapOf<String, String>() }
    var index by remember(quiz.id) { mutableIntStateOf(0) }
    var showAnswerSheet by remember { mutableStateOf(false) }
    var showSubmitConfirm by remember { mutableStateOf(false) }
    val questionScroll = rememberScrollState()
    val question = quiz.questions[index]
    LaunchedEffect(index) { questionScroll.scrollTo(0) }
    fun bundle() = AnswerBundle(optionAnswers.toMap(), textAnswers.toMap())
    fun answeredCount() = quiz.questions.count { bundle().isAnswered(it) }
    fun selectOption(optionIndex: Int) {
        val previousAnswer = optionAnswers[question.id].orEmpty()
        if (question.type == QuestionType.MULTIPLE) {
            optionAnswers[question.id] = previousAnswer.toMutableSet().apply {
                if (!add(optionIndex)) remove(optionIndex)
            }
        } else {
            optionAnswers[question.id] = setOf(optionIndex)
            if (shouldAutoAdvance(question.type, previousAnswer, optionIndex, index, quiz.questions.lastIndex)) index++
        }
    }
    val submit = {
        if (answeredCount() < quiz.questions.size) showSubmitConfirm = true else onSubmit(bundle(), (System.currentTimeMillis() - startedAt) / 1000)
    }
    Scaffold(
        containerColor = Color(0xFFF4F6FA),
        topBar = { TopAppBar(title = { Column { Text(quiz.title, fontWeight = FontWeight.Bold); Text(question.type.label(), fontSize = 13.sp, color = Color(0xFF697586)) } }, navigationIcon = { TextButton(onClick = onBack) { Text("退出") } }, actions = { IconButton(onClick = { onToggleFavorite(question.id) }) { Icon(if (question.id in favoriteIds) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, if (question.id in favoriteIds) "取消收藏" else "收藏题目", tint = Color(0xFF0759BD)) }; TextButton(onClick = { showAnswerSheet = true }) { Text("答题卡") } }) },
        bottomBar = {
            Column(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { index-- }, enabled = index > 0, modifier = Modifier.weight(1f)) { Text("上一题") }
                    if (index < quiz.questions.lastIndex) Button(onClick = { index++ }, modifier = Modifier.weight(1f)) { Text("下一题") }
                    else Button(onClick = submit, modifier = Modifier.weight(1f)) { Text("提交试卷") }
                }
                Text("已完成 ${answeredCount()} / ${quiz.questions.size}", modifier = Modifier.align(Alignment.CenterHorizontally), color = Color(0xFF697586), fontSize = 13.sp)
            }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(questionScroll).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("第 ${index + 1} 题", fontWeight = FontWeight.SemiBold, color = Color(0xFF123A70)); Text("共 ${quiz.questions.size} 题", color = Color(0xFF697586)) }
            LinearProgressIndicator(progress = { (index + 1f) / quiz.questions.size }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            Text(question.prompt, fontSize = 22.sp, lineHeight = 33.sp, fontWeight = FontWeight.SemiBold)
            Text(question.type.instruction(), color = Color(0xFF697586))
            if (question.type == QuestionType.FILL || question.type == QuestionType.ESSAY) {
                OutlinedTextField(
                    value = textAnswers[question.id].orEmpty(),
                    onValueChange = { textAnswers[question.id] = it },
                    modifier = Modifier.fillMaxWidth().height(if (question.type == QuestionType.ESSAY) 190.dp else 72.dp),
                    placeholder = { Text(if (question.type == QuestionType.ESSAY) "请输入你的回答，可分点作答" else "请输入答案") },
                    shape = RoundedCornerShape(14.dp),
                )
            }
            question.options.forEachIndexed { optionIndex, option ->
                val selected = optionAnswers[question.id].orEmpty().contains(optionIndex)
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { selectOption(optionIndex) },
                    colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFFE8EFF9) else Color.White),
                    border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) Color(0xFF123A70) else Color(0xFFE1E6EE)),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(34.dp).background(if (selected) Color(0xFF123A70) else Color(0xFFF1F3F7), if (question.type == QuestionType.MULTIPLE) RoundedCornerShape(9.dp) else CircleShape), contentAlignment = Alignment.Center) { Text(('A'.code + optionIndex).toChar().toString(), color = if (selected) Color.White else Color(0xFF4A5567), fontWeight = FontWeight.Bold) }; Text(option, modifier = Modifier.padding(start = 14.dp).weight(1f), fontSize = 17.sp); if (question.type == QuestionType.MULTIPLE) Box(Modifier.size(26.dp).border(2.dp, if (selected) Color(0xFF123A70) else Color(0xFF697586), RoundedCornerShape(7.dp)).background(if (selected) Color(0xFF123A70) else Color.Transparent, RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) { if (selected) Text("✓", color = Color.White, fontWeight = FontWeight.Bold) } else RadioButton(selected = selected, onClick = { selectOption(optionIndex) }) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    if (showAnswerSheet) {
        AnswerSheetBottomSheet(
            quiz = quiz,
            currentIndex = index,
            answers = bundle(),
            onDismiss = { showAnswerSheet = false },
            onJump = { index = it },
            onSubmit = submit,
        )
    }
    if (showSubmitConfirm) {
        AlertDialog(
            onDismissRequest = { showSubmitConfirm = false },
            title = { Text("还有题目未完成") },
            text = { Text("当前还有 ${quiz.questions.size - answeredCount()} 道题未作答。未答题将按错误计算，确定提交吗？") },
            confirmButton = { Button(onClick = { showSubmitConfirm = false; onSubmit(bundle(), (System.currentTimeMillis() - startedAt) / 1000) }) { Text("仍然提交") } },
            dismissButton = { TextButton(onClick = { showSubmitConfirm = false }) { Text("继续答题") } },
        )
    }
}

@Composable
private fun ResultScreen(
    quiz: Quiz,
    score: Int,
    answers: AnswerBundle,
    durationSeconds: Long,
    favoriteIds: Set<String>,
    onToggleFavorite: (String) -> Unit,
    isOnline: Boolean,
    onHome: () -> Unit,
    onRetry: () -> Unit,
    onWrongBook: () -> Unit,
) {
    val colors = appColors()
    val objectiveCount = quiz.questions.count { it.type != QuestionType.ESSAY }.coerceAtLeast(1)
    val wrongCount = objectiveCount - score
    val rate = score * 100 / objectiveCount
    var reviewFilter by remember { mutableStateOf("需要巩固") }
    val reviewQuestions = if (reviewFilter == "全部") quiz.questions else quiz.questions.filter { it.type != QuestionType.ESSAY && !QuizEngine.isCorrect(it, answers) }
    LazyColumn(Modifier.fillMaxSize().background(colors.surfaceMuted), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        item { Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onHome) { Icon(Icons.Default.ArrowBack, "返回") }; Column { Text("练习结果", fontWeight = FontWeight.Bold, fontSize = 21.sp); Text(quiz.title, color = Color(0xFF64748B), fontSize = 12.sp) } } }
        item { Card(Modifier.padding(16.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF082E59)), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column { Text(if (rate >= 80) "掌握良好" else "还需巩固", color = Color(0xFF15C5ED), fontWeight = FontWeight.Bold); Row(verticalAlignment = Alignment.Bottom) { Text("$rate", color = Color.White, fontSize = 54.sp, fontWeight = FontWeight.Bold); Text(" 分", color = Color(0xFFC9D8E8), modifier = Modifier.padding(bottom = 10.dp)) } }; Icon(if (rate >= 80) Icons.Default.CheckCircle else Icons.Default.ErrorOutline, null, tint = if (rate >= 80) Color(0xFF36C98F) else Color(0xFFFFB357), modifier = Modifier.size(58.dp)) }; Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { ResultStat("答对", "$score 题", Modifier.weight(1f)); ResultStat("待巩固", "${objectiveCount - score} 题", Modifier.weight(1f)); ResultStat("用时", formatDuration(durationSeconds), Modifier.weight(1f)) }; Text(if (rate >= 80) "整体掌握良好，可以进入下一套练习。" else "建议先查看解析，再完成一次错题复练。", color = Color(0xFFD4E0EC), fontSize = 14.sp) } } }
        item { Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { Button(onClick = if (objectiveCount - score > 0) onWrongBook else onHome, modifier = Modifier.weight(1f)) { Text(if (objectiveCount - score > 0) "复习错题" else "返回首页") }; OutlinedButton(onClick = onRetry, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp)); Text(" 再练一次") } } }
        item { AiAnalysisPanel(quiz, score, answers, isOnline, defaultCollapsed = wrongCount > 0) }
        item { Column(Modifier.padding(top = 18.dp).background(colors.surface)) { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) { listOf("需要巩固", "全部").forEach { tab -> TextButton(onClick = { reviewFilter = tab }) { Text(tab, color = if (reviewFilter == tab) colors.primary else colors.textSecondary, fontWeight = if (reviewFilter == tab) FontWeight.Bold else FontWeight.Normal) } } }; if (wrongCount > 0 && reviewFilter == "需要巩固") Text("$wrongCount 题", color = colors.warning, fontSize = 12.sp) }; Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border)) } }
        if (reviewQuestions.isEmpty()) item { Column(Modifier.fillMaxWidth().background(colors.surface).padding(38.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.CheckCircle, null, tint = colors.success, modifier = Modifier.size(42.dp)); Text("本次没有错题", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp), color = colors.textPrimary) } }
        items(reviewQuestions) { question -> Box(Modifier.background(colors.surface).padding(horizontal = 16.dp, vertical = 7.dp)) { ReviewCard(question, answers, favoriteIds, onToggleFavorite, isOnline) } }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ResultStat(label: String, value: String, modifier: Modifier = Modifier) { Column(modifier.background(Color(0xFF183F69), RoundedCornerShape(12.dp)).padding(12.dp)) { Text(value, color = Color.White, fontWeight = FontWeight.Bold); Text(label, color = Color(0xFFBFD0E2), fontSize = 11.sp) } }

private fun QuestionType.label() = when (this) { QuestionType.SINGLE -> "单选题"; QuestionType.MULTIPLE -> "多选题"; QuestionType.TRUE_FALSE -> "判断题"; QuestionType.FILL -> "填空题"; QuestionType.ESSAY -> "问答题" }
private fun QuestionType.instruction() = when (this) { QuestionType.SINGLE, QuestionType.TRUE_FALSE -> "请选择一个最合适的答案"; QuestionType.MULTIPLE -> "本题有多个正确答案，请选择全部正确项"; QuestionType.FILL -> "请在下方填写答案"; QuestionType.ESSAY -> "请根据要点组织你的回答" }

@Composable
private fun ReviewCard(
    question: Question,
    answers: AnswerBundle,
    favoriteIds: Set<String>,
    onToggleFavorite: (String) -> Unit,
    isOnline: Boolean,
) {
    val colors = appColors()
    val messenger = LocalAppMessenger.current
    val context = LocalContext.current
    val client = remember(context) { AiClient(context) }
    val scope = rememberCoroutineScope()
    var aiResult by remember(question.id) { mutableStateOf<AiQuestionResult?>(null) }
    var aiText by remember(question.id) { mutableStateOf("") }
    var aiError by remember(question.id) { mutableStateOf("") }
    var aiLoading by remember(question.id) { mutableStateOf(false) }
    val correct = QuizEngine.isCorrect(question, answers)
    val answerText = when (question.type) {
        QuestionType.SINGLE, QuestionType.MULTIPLE, QuestionType.TRUE_FALSE -> question.answerIndices.sorted().joinToString("、") { question.options[it] }
        QuestionType.FILL -> question.acceptedAnswers.joinToString(" / ")
        QuestionType.ESSAY -> question.referenceAnswer
    }
    Card(colors = CardDefaults.cardColors(containerColor = colors.surface), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(question.type.label(), color = colors.textSecondary, fontSize = 13.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onToggleFavorite(question.id); messenger.show(if (question.id in favoriteIds) "已取消收藏" else "已加入收藏") }) {
                        Icon(if (question.id in favoriteIds) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, if (question.id in favoriteIds) "取消收藏" else "收藏题目", tint = colors.primary)
                    }
                    Text(if (question.type == QuestionType.ESSAY) "自评" else if (correct) "回答正确" else "需要巩固", color = if (correct) colors.success else colors.warning, fontWeight = FontWeight.SemiBold)
                }
            }
            Text(question.prompt, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            Text(if (question.type == QuestionType.ESSAY) "参考答案：$answerText" else "正确答案：$answerText", color = colors.textPrimary)
            Text(question.explanation, color = colors.textSecondary, lineHeight = 22.sp)
            if (aiText.isNotBlank() || aiError.isNotBlank() || aiLoading) {
                Surface(color = colors.primarySoft, shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.AutoAwesome, null, tint = colors.primary, modifier = Modifier.size(18.dp)); Text(" AI 深度解析", color = colors.textPrimary, fontWeight = FontWeight.Bold) }
                        aiResult?.score?.let { Text("AI 评分：${formatAiScore(it)} / ${aiResult?.maxScore ?: 10}", color = colors.success, fontWeight = FontWeight.Bold) }
                        if (aiText.isNotBlank()) AiFormattedText(aiText)
                        if (aiError.isNotBlank()) Text(aiError, color = if (aiError.contains("冷却")) colors.warning else colors.danger, fontSize = 13.sp)
                        if (aiLoading && aiText.isBlank()) Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Text(" 正在生成解析…", color = colors.textSecondary, fontSize = 13.sp) }
                    }
                }
            }
            OutlinedButton(
                enabled = !aiLoading,
                onClick = {
                    if (!isOnline) {
                        aiError = "当前无网络，无法使用 AI 解析"
                        return@OutlinedButton
                    }
                    aiLoading = true
                    aiError = ""
                    aiText = ""
                    aiResult = null
                    scope.launch {
                        runCatching { client.explain(question, answers) { partial -> aiText = partial } }
                            .onSuccess { result -> aiResult = result; aiText = result.text }
                            .onFailure { aiError = it.message ?: "AI 解析失败，请检查网络后重试" }
                        aiLoading = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) { Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(17.dp)); Text(if (aiResult == null) " AI 深度解析" else " 重新生成解析") }
        }
    }
}

@Composable
private fun AiAnalysisPanel(quiz: Quiz, score: Int, answers: AnswerBundle, isOnline: Boolean, defaultCollapsed: Boolean = false) {
    val colors = appColors()
    val context = LocalContext.current
    val client = remember(context) { AiClient(context) }
    val settingsStore = remember(context) { AiSettingsStore(context) }
    val scope = rememberCoroutineScope()
    var text by remember(quiz.id, answers) { mutableStateOf("") }
    var info by remember(quiz.id, answers) { mutableStateOf("") }
    var loading by remember(quiz.id, answers) { mutableStateOf(false) }
    var collapsed by remember(quiz.id, answers) { mutableStateOf(defaultCollapsed) }
    var lastGeneratedAt by remember(quiz.id, answers) { mutableStateOf(0L) }
    var cooldownLeftMs by remember { mutableStateOf(0L) }
    LaunchedEffect(lastGeneratedAt) {
        while (true) {
            val settings = settingsStore.load()
            cooldownLeftMs = if (settings.mode == AiMode.SHARED && lastGeneratedAt > 0) {
                (120_000 - (System.currentTimeMillis() - lastGeneratedAt)).coerceAtLeast(0)
            } else 0L
            kotlinx.coroutines.delay(1_000)
        }
    }
    Card(Modifier.padding(horizontal = 16.dp, vertical = 16.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = colors.surface), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(38.dp).background(colors.primarySoft, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.AutoAwesome, null, tint = colors.primary) }; Column(Modifier.padding(start = 11.dp)) { Text("AI 学情分析", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = colors.textPrimary); Text(if (settingsStore.load().mode == AiMode.OWN_KEY) "自带 Key · 完整模式" else "站点默认 AI · 精炼模式", color = colors.textSecondary, fontSize = 12.sp) } }
                if (text.isNotBlank()) IconButton(onClick = { collapsed = !collapsed }) { Icon(if (collapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess, if (collapsed) "展开" else "收起") }
            }
            if (!collapsed) {
                if (text.isBlank() && !loading && info.isBlank()) Text("根据本次作答总结薄弱知识点、典型错因和记忆方法。", color = colors.textSecondary, lineHeight = 21.sp)
                if (text.isNotBlank()) AiFormattedText(text)
                if (loading && text.isBlank()) Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Text(" 正在分析学情，通常需要 10–30 秒…", color = colors.textSecondary, fontSize = 13.sp) }
                if (info.isNotBlank()) Text(info, color = colors.warning, fontSize = 13.sp)
            }
            Button(
                enabled = !loading && cooldownLeftMs == 0L,
                onClick = {
                    if (!isOnline) {
                        info = "当前无网络，无法生成学情分析"
                        return@Button
                    }
                    val settings = settingsStore.load()
                    if (settings.mode == AiMode.SHARED && lastGeneratedAt > 0 && cooldownLeftMs > 0) {
                        info = "共享 AI 冷却中，约 ${cooldownLeftMs / 60_000 + 1} 分钟后可重新分析"
                    } else {
                        loading = true
                        collapsed = false
                        info = ""
                        text = ""
                        scope.launch {
                            runCatching { client.analyze(quiz, score, answers) { partial -> text = partial } }
                                .onSuccess { result -> text = result; lastGeneratedAt = System.currentTimeMillis() }
                                .onFailure { info = it.message ?: "学情分析失败，请检查网络后重试" }
                            loading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(17.dp))
                Text(
                    when {
                        loading -> " 正在分析…"
                        cooldownLeftMs > 0 && text.isNotBlank() -> " 冷却中 ${cooldownLeftMs / 60_000}:${"%02d".format((cooldownLeftMs / 1000) % 60)}"
                        text.isBlank() -> " 生成学情分析"
                        else -> " 重新分析"
                    },
                )
            }
        }
    }
}

@Composable
private fun AiFormattedText(text: String) {
    val colors = appColors()
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        text.lines().filter { it.isNotBlank() }.forEach { line ->
            val heading = line.startsWith("**") && line.indexOf("**", startIndex = 2) >= 2
            Text(line.replace("**", "").trim(), color = if (heading) colors.textPrimary else colors.textSecondary, fontWeight = if (heading) FontWeight.Bold else FontWeight.Normal, fontSize = if (heading) 15.sp else 14.sp, lineHeight = 22.sp)
        }
    }
}

private fun formatAiScore(score: Double): String = if (score % 1.0 == 0.0) score.toInt().toString() else "%.1f".format(Locale.CHINA, score)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WrongBookScreen(quizzes: List<Quiz>, wrongItems: List<WrongItem>, onHome: () -> Unit, onLibrary: () -> Unit, onProfile: () -> Unit, onPractice: (List<Question>) -> Unit) {
    val questionsById = remember(quizzes) { quizzes.flatMap { it.questions }.associateBy { it.id } }
    val validItems = wrongItems.filter { it.questionId in questionsById }
    val now = System.currentTimeMillis()
    val dueItems = validItems.filter { it.status != ReviewStatus.MASTERED && it.nextReviewAt <= now }
    var filter by remember { mutableStateOf("今日复习") }
    val filteredItems = when (filter) {
        "未掌握" -> validItems.filter { it.status == ReviewStatus.UNMASTERED }
        "复习中" -> validItems.filter { it.status == ReviewStatus.REVIEWING }
        "已掌握" -> validItems.filter { it.status == ReviewStatus.MASTERED }
        else -> dueItems
    }
    val practiceQuestions = dueItems.mapNotNull { questionsById[it.questionId] }
    Scaffold(containerColor = Color(0xFFF6F8FB), topBar = { TopAppBar(title = { Column { Text("复习", fontWeight = FontWeight.Bold, fontSize = 25.sp); Text("巩固薄弱知识点", fontSize = 13.sp, color = Color(0xFF64748B)) } }) }, bottomBar = { AppBottomBar(Screen.WRONG_BOOK, onHome, onLibrary, {}, onProfile) }) { padding ->
        if (validItems.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Box(Modifier.size(72.dp).background(Color(0xFFE5F5EF), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF138A5B), modifier = Modifier.size(38.dp)) }; Text("今天没有待复习题目", fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp)); Text("完成练习后，薄弱题目会自动出现在这里", color = Color(0xFF64748B), modifier = Modifier.padding(top = 7.dp)); Button(onClick = onLibrary, modifier = Modifier.padding(top = 22.dp)) { Text("去选择一套练习") } }
        } else {
            LazyColumn(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                item { Card(Modifier.padding(16.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF082E59)), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("今日复习", color = Color(0xFF15C5ED), fontWeight = FontWeight.Bold); Text(if (dueItems.isEmpty()) "今日任务已完成" else "${dueItems.size} 道题等待巩固", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold); Text(if (dueItems.isEmpty()) "下一批题目会按计划自动出现" else "预计 ${dueItems.size.coerceAtLeast(1) * 2} 分钟完成", color = Color(0xFFC9D8E8)); Button(enabled = practiceQuestions.isNotEmpty(), onClick = { onPractice(practiceQuestions) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.PlayArrow, null); Text(if (practiceQuestions.isEmpty()) "今日已完成" else "开始今日复习") } } } }
                item { Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) { listOf("今日复习", "未掌握", "复习中", "已掌握").forEach { tab -> TextButton(onClick = { filter = tab }, modifier = Modifier.weight(1f)) { Text(tab, fontSize = 13.sp, color = if (filter == tab) Color(0xFF0759BD) else Color(0xFF64748B), fontWeight = if (filter == tab) FontWeight.Bold else FontWeight.Normal) } } } }
                if (filteredItems.isEmpty()) item { Column(Modifier.fillMaxWidth().background(Color.White).padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF20A66A)); Text("这里暂时没有题目", color = Color(0xFF64748B), modifier = Modifier.padding(top = 8.dp)) } }
                if (filteredItems.isNotEmpty()) item { Text("共 ${filteredItems.size} 题", modifier = Modifier.padding(horizontal = 18.dp, vertical = 15.dp), color = Color(0xFF64748B), fontSize = 13.sp) }
                items(filteredItems) { item ->
                    val question = questionsById[item.questionId] ?: return@items
                    val statusText = when (item.status) { ReviewStatus.UNMASTERED -> "未掌握"; ReviewStatus.REVIEWING -> "复习中 ${item.correctStreak}/3"; ReviewStatus.MASTERED -> "已掌握" }
                    val statusColor = when (item.status) { ReviewStatus.UNMASTERED -> Color(0xFFC65D08); ReviewStatus.REVIEWING -> Color(0xFF0759BD); ReviewStatus.MASTERED -> Color(0xFF138A5B) }
                    Column(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 18.dp, vertical = 15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("${question.type.label()} · 错 ${item.wrongTimes} 次", color = Color(0xFF64748B), fontSize = 12.sp); Text(statusText, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Bold) }; Text(question.prompt, fontWeight = FontWeight.SemiBold); Text("正确答案：${question.correctAnswerText()}", color = Color(0xFF138A5B), fontSize = 13.sp); Text(question.explanation, color = Color(0xFF64748B), fontSize = 13.sp, maxLines = 2); Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE4E9F0))) }
                }
            }
        }
    }
}

private fun Question.correctAnswerText(): String = when (type) {
    QuestionType.SINGLE, QuestionType.MULTIPLE, QuestionType.TRUE_FALSE -> answerIndices.sorted().joinToString("、") { options[it] }
    QuestionType.FILL -> acceptedAnswers.joinToString(" / ")
    QuestionType.ESSAY -> referenceAnswer
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoritesScreen(
    quizzes: List<Quiz>,
    favoriteIds: Set<String>,
    onToggleFavorite: (String) -> Unit,
    onHome: () -> Unit,
    onLibrary: () -> Unit,
    onWrongBook: () -> Unit,
    onProfile: () -> Unit,
    onPractice: (List<Question>) -> Unit,
) {
    val entries = quizzes.flatMap { quiz -> quiz.questions.filter { it.id in favoriteIds }.map { quiz to it } }
    val quizFilters = listOf("全部") + entries.map { it.first.title }.distinct()
    val typeFilters = listOf("全部") + entries.map { it.second.type.label() }.distinct()
    var quizFilter by remember { mutableStateOf("全部") }
    var typeFilter by remember { mutableStateOf("全部") }
    val filtered = entries.filter { (quiz, question) ->
        (quizFilter == "全部" || quiz.title == quizFilter) && (typeFilter == "全部" || question.type.label() == typeFilter)
    }
    Scaffold(
        containerColor = Color(0xFFF6F8FB),
        topBar = { TopAppBar(title = { Column { Text("我的收藏", fontWeight = FontWeight.Bold, fontSize = 25.sp); Text("按题库和题型集中巩固", fontSize = 13.sp, color = Color(0xFF64748B)) } }) },
        bottomBar = { AppBottomBar(null, onHome, onLibrary, onWrongBook, onProfile) },
    ) { padding ->
        LazyColumn(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            if (entries.isEmpty()) {
                item {
                    Column(Modifier.fillParentMaxHeight(.75f).fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.BookmarkBorder, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(54.dp))
                        Text("还没有收藏题目", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
                        Text("答题时点击顶部书签即可收藏", color = Color(0xFF64748B), modifier = Modifier.padding(top = 7.dp))
                        Button(onClick = onLibrary, modifier = Modifier.padding(top = 20.dp)) { Text("去题库练习") }
                    }
                }
            } else {
                item {
                    Column(Modifier.fillMaxWidth().background(Color.White).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("题库", modifier = Modifier.padding(horizontal = 18.dp), color = Color(0xFF64748B), fontSize = 12.sp)
                        LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(quizFilters) { label -> FavoriteFilter(label, quizFilter == label) { quizFilter = label } }
                        }
                        Text("题型", modifier = Modifier.padding(horizontal = 18.dp), color = Color(0xFF64748B), fontSize = 12.sp)
                        LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(typeFilters) { label -> FavoriteFilter(label, typeFilter == label) { typeFilter = label } }
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${filtered.size} 道收藏", fontWeight = FontWeight.Bold)
                        Button(enabled = filtered.isNotEmpty(), onClick = { onPractice(filtered.map { it.second }) }) { Icon(Icons.Default.PlayArrow, null); Text(" 开始练习") }
                    }
                }
                items(filtered, key = { it.second.id }) { (quiz, question) ->
                    Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 18.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("${quiz.title} · ${question.type.label()}", color = Color(0xFF64748B), fontSize = 12.sp)
                            Text(question.prompt, fontWeight = FontWeight.SemiBold, maxLines = 2)
                        }
                        IconButton(onClick = { onToggleFavorite(question.id) }) { Icon(Icons.Default.Bookmark, "取消收藏", tint = Color(0xFF0759BD)) }
                    }
                    Box(Modifier.padding(start = 18.dp).fillMaxWidth().height(1.dp).background(Color(0xFFE4E9F0)))
                }
                item { Spacer(Modifier.height(18.dp)) }
            }
        }
    }
}

@Composable
private fun FavoriteFilter(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = if (selected) Color(0xFF0759BD) else Color(0xFFEEF2F7), shape = RoundedCornerShape(10.dp)) {
        Text(label, color = if (selected) Color.White else Color(0xFF526174), modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp), fontSize = 13.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileScreen(
    records: List<LearningRecord>,
    wrongItems: List<WrongItem>,
    quizCount: Int,
    questionCount: Int,
    lastSyncedAt: Long,
    isOnline: Boolean,
    onSync: suspend () -> SyncResult,
    onSyncComplete: (SyncResult) -> Unit,
    onSyncFailed: (String) -> Unit,
    onHome: () -> Unit,
    onLibrary: () -> Unit,
    onWrongBook: () -> Unit,
    onSettings: () -> Unit,
    onLearningReport: () -> Unit,
) {
    val colors = appColors()
    val summary = LearningStats.summary(records)
    val unmastered = wrongItems.count { it.status == ReviewStatus.UNMASTERED }
    val reviewing = wrongItems.count { it.status == ReviewStatus.REVIEWING }
    val mastered = wrongItems.count { it.status == ReviewStatus.MASTERED }
    val scope = rememberCoroutineScope()
    var syncing by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var syncMessage by remember(lastSyncedAt) {
        mutableStateOf(if (lastSyncedAt == 0L) "尚未在线同步" else "上次同步 ${formatDateTime(lastSyncedAt)}")
    }
    val pullState = rememberPullToRefreshState()
    suspend fun runSync() {
        syncing = true
        syncMessage = "正在从 GitHub 获取最新题库…"
        runCatching { onSync() }
            .onSuccess { result ->
                syncMessage = "上次同步 ${formatDateTime(result.syncedAt)}"
                onSyncComplete(result)
            }
            .onFailure { error -> syncMessage = "同步失败"; onSyncFailed(error.message ?: "请检查网络后重试") }
        syncing = false
        isRefreshing = false
    }
    Scaffold(containerColor = colors.pageBackground, bottomBar = { AppBottomBar(Screen.PROFILE, onHome, onLibrary, onWrongBook, {}) }) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                if (!isOnline) {
                    onSyncFailed("当前无网络，无法同步题库")
                } else {
                    isRefreshing = true
                    scope.launch { runSync() }
                }
            },
            state = pullState,
            modifier = Modifier.padding(padding),
        ) {
        LazyColumn(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Spacer(Modifier.height(18.dp))
                Text("我的", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("学习数据与应用入口", color = Color(0xFF697586))
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(52.dp).background(Color(0xFFE3E9F3), CircleShape), contentAlignment = Alignment.Center) {
                            Text("学", color = Color(0xFF123A70), fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.padding(start = 16.dp)) {
                            Text("本地学习档案", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("数据仅保存在这台设备", color = Color(0xFF697586))
                        }
                    }
                }
            }
            item {
                Text("学习概览", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Metric("累计答题", "${summary.questionCount} 道", Modifier.weight(1f))
                    Metric("累计测验", "${summary.quizCount} 次", Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Metric("今日已完成", "${summary.todayQuestionCount} 道", Modifier.weight(1f))
                    Metric("连续学习", "${LearningStats.streakDays(records)} 天", Modifier.weight(1f))
                }
            }
            item {
                ReportCard("错题掌握进度") {
                    StatProgress("未掌握", unmastered, wrongItems.size, Color(0xFFE15B64))
                    StatProgress("复习中", reviewing, wrongItems.size, Color(0xFFE4A33A))
                    StatProgress("已掌握", mastered, wrongItems.size, Color(0xFF20A66A))
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                    SettingsNavRow("学习报告", "趋势、薄弱点与最近测验", onLearningReport)
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudSync, null, tint = Color(0xFF0759BD))
                            Column(Modifier.padding(start = 12.dp)) {
                                Text("GitHub 题库同步", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Text("当前 $quizCount 套 · $questionCount 题", color = Color(0xFF64748B), fontSize = 13.sp)
                            }
                        }
                        Text(syncMessage, color = if (syncMessage.startsWith("同步失败")) colors.warning else colors.textSecondary, fontSize = 13.sp)
                        if (!isOnline) Text("当前无网络，请连接后下拉刷新或手动同步", color = colors.warning, fontSize = 12.sp)
                        Button(
                            enabled = !syncing && isOnline,
                            onClick = { scope.launch { runSync() } },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) { Text(if (syncing) "正在同步…" else "立即同步题库") }
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                    SettingsNavRow("应用设置", "备份、AI、更新与关于", onSettings)
                }
            }
        }
        }
    }
}

private fun formatDuration(seconds: Long): String = when {
    seconds < 60 -> "${seconds.coerceAtLeast(1)} 秒"
    else -> "${seconds / 60}分${seconds % 60}秒"
}

@Composable
private fun AppBottomBar(selected: Screen?, onHome: () -> Unit, onLibrary: () -> Unit, onWrongBook: () -> Unit, onProfile: () -> Unit) {
    val colors = appColors()
    NavigationBar(containerColor = colors.surface) {
        listOf(
            Triple(Screen.HOME, "首页", onHome),
            Triple(Screen.QUIZZES, "题库", onLibrary),
            Triple(Screen.WRONG_BOOK, "复习", onWrongBook),
            Triple(Screen.PROFILE, "我的", onProfile),
        ).forEach { (screen, label, action) ->
            NavigationBarItem(selected = selected == screen, onClick = action, icon = { Icon(when (screen) { Screen.HOME -> Icons.Default.Home; Screen.QUIZZES -> Icons.Default.MenuBook; Screen.WRONG_BOOK -> Icons.Default.School; else -> Icons.Default.Person }, contentDescription = label) }, label = { Text(label) })
        }
    }
}
