package com.xuzheng.tiyuengine.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xuzheng.tiyuengine.R
import com.xuzheng.tiyuengine.data.AiClient
import com.xuzheng.tiyuengine.data.AiMode
import com.xuzheng.tiyuengine.data.AiQuestionResult
import com.xuzheng.tiyuengine.data.AiSettingsStore
import com.xuzheng.tiyuengine.data.AnswerBundle
import com.xuzheng.tiyuengine.data.FavoriteStore
import com.xuzheng.tiyuengine.data.LearningRecord
import com.xuzheng.tiyuengine.data.LearningStats
import com.xuzheng.tiyuengine.data.LearningStore
import com.xuzheng.tiyuengine.data.NetworkMonitor
import com.xuzheng.tiyuengine.data.Question
import com.xuzheng.tiyuengine.data.QuestionAttempt
import com.xuzheng.tiyuengine.data.QuestionType
import com.xuzheng.tiyuengine.data.Quiz
import com.xuzheng.tiyuengine.data.QuizEngine
import com.xuzheng.tiyuengine.data.QuizRepository
import com.xuzheng.tiyuengine.data.ReviewStatus
import com.xuzheng.tiyuengine.data.SyncResult
import com.xuzheng.tiyuengine.data.WrongBookStore
import com.xuzheng.tiyuengine.data.WrongItem
import com.xuzheng.tiyuengine.data.aiFailureMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val dueQuestions = wrongItems.filter {
        it.status != ReviewStatus.MASTERED && it.nextReviewAt <= System.currentTimeMillis()
    }
        .mapNotNull { item -> quizzes.flatMap { it.questions }.find { it.id == item.questionId } }
    val recommendedQuiz = if (dueQuestions.isNotEmpty()) {
        Quiz("wrong_review", "今日错题复练", "到期错题优先巩固", dueQuestions)
    } else {
        quizzes.find { it.id == LearningStats.recommendedQuizId(learningRecords, quizzes) } ?: quizzes.firstOrNull()
    }

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
        val dialogColors = appColors()
        AlertDialog(
            onDismissRequest = { showAnswerExitConfirm = false },
            title = { Text("确定退出练习？") },
            text = { Text("退出后本次作答进度不会保存。") },
            icon = { Icon(Icons.Default.ErrorOutline, null, tint = dialogColors.warning) },
            confirmButton = {
                Button(
                    onClick = { showAnswerExitConfirm = false },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = dialogColors.actionOrange,
                        contentColor = dialogColors.onPrimary
                    ),
                ) { Text("继续答题") }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showAnswerExitConfirm = false;
                    leaveQuizFlow()
                }) { Text("退出") }
            },
            containerColor = dialogColors.surface,
            shape = RoundedCornerShape(24.dp),
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

    QuizAppShell(screen = screen, isOnline = isOnline, snackbarHostState = snackbarHostState, onOpenUpdate = {
        openUpdateOnLaunch = true
    }) { targetScreen ->
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
                    learningStore.add(
                        LearningRecord(
                            quiz.id,
                            quiz.title,
                            result,
                            objectiveCount,
                            quiz.questions.size,
                            durationSeconds,
                            System.currentTimeMillis(),
                            attempts
                        )
                    )
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
                    onHome = { leaveQuizFlow() },
                    onRetry = { screen = Screen.ANSWER },
                    onWrongBook = {
                        leaveQuizFlow();
                        screen = Screen.WRONG_BOOK
                    },
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
private fun HomeScreen(
    wrongCount: Int,
    learningRecords: List<LearningRecord>,
    quizzes: List<Quiz>,
    recommendedQuiz: Quiz?,
    onStart: () -> Unit,
    onLibrary: () -> Unit,
    onWrongBook: () -> Unit,
    onFavorites: () -> Unit,
    onProfile: () -> Unit,
    onQuizSelect: (Quiz) -> Unit
) {
    val colors = appColors()
    val compactHero = LocalConfiguration.current.screenHeightDp < 720 || LocalDensity.current.fontScale > 1.25f
    var selectedTab by remember { mutableStateOf("最近") }
    val regularQuizzes = remember(quizzes) { quizzes.filter { !it.id.startsWith("exam100_") } }
    val recentQuizzes = remember(regularQuizzes, learningRecords) {
        val byId = regularQuizzes.associateBy { it.id }
        learningRecords.sortedByDescending { it.submittedAt }.mapNotNull { byId[it.quizId] }.distinctBy { it.id }
    }
    val visibleQuizzes = when (selectedTab) {
        "专项" -> regularQuizzes.drop(1).take(3)
        else -> (recentQuizzes + regularQuizzes).distinctBy { it.id }.take(3)
    }
    Scaffold(
        containerColor = colors.pageBackground,
        bottomBar = {
            AppBottomBar(
                Screen.HOME,
                onHome = {},
                onLibrary = onLibrary,
                onWrongBook = onWrongBook,
                onProfile = onProfile
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                Column(Modifier.fillMaxWidth().background(colors.heroSky)) {
                    Row(
                        Modifier.fillMaxWidth().padding(
                            start = 20.dp,
                            end = 16.dp,
                            top = if (compactHero) 12.dp else 20.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "题域引擎",
                                fontSize = 25.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = colors.textPrimary
                            )
                            Text("工业网络 · 英语备考", fontSize = 13.sp, color = colors.textSecondary)
                        }
                        Surface(
                            onClick = onWrongBook,
                            color = colors.actionOrangeSoft,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.NotificationsNone,
                                    null,
                                    tint = colors.actionOrange,
                                    modifier = Modifier.size(19.dp)
                                )
                                Text(
                                    "$wrongCount 待复习",
                                    color = colors.textPrimary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 3.dp)
                                )
                            }
                        }
                        Surface(
                            onClick = onFavorites,
                            color = colors.surface,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Star,
                                    "我的收藏",
                                    tint = colors.actionOrange,
                                    modifier = Modifier.size(23.dp)
                                )
                            }
                        }
                    }
                    Text(
                        "从一套好题开始",
                        modifier = Modifier.padding(
                            start = 20.dp,
                            end = 20.dp,
                            top = if (compactHero) 16.dp else 30.dp
                        ),
                        color = colors.textPrimary,
                        fontSize = if (compactHero) 24.sp else 29.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "系统练习 · 掌握核心知识点",
                        modifier = Modifier.padding(start = 20.dp, top = 5.dp),
                        color = colors.textSecondary,
                        fontSize = 14.sp
                    )
                    Image(
                        painterResource(R.drawable.home_network_hero),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(
                            if (compactHero) 100.dp else 205.dp
                        ).padding(horizontal = 10.dp),
                    )
                }
            }
            item {
                Card(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = if (compactHero) 8.dp else 12.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Column(
                        Modifier.padding(if (compactHero) 14.dp else 20.dp),
                        verticalArrangement = Arrangement.spacedBy(if (compactHero) 8.dp else 12.dp)
                    ) {
                        Surface(color = colors.greenSoft, shape = RoundedCornerShape(8.dp)) {
                            Text(
                                "推荐练习",
                                color = colors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                        Text(
                            recommendedQuiz?.title ?: "选择一套练习",
                            color = colors.textPrimary,
                            fontSize = if (compactHero) 18.sp else 21.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = if (compactHero) 1 else 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            recommendedQuiz?.subtitle ?: "题库准备中",
                            color = colors.textSecondary,
                            fontSize = 13.sp,
                            maxLines = if (compactHero) 1 else 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            HeroMeta(Icons.Default.Description, "${recommendedQuiz?.questions?.size ?: 0} 道题")
                            HeroMeta(
                                Icons.Default.AccessTime,
                                "约 ${recommendedQuiz?.questions?.size?.coerceAtLeast(1) ?: 1} 分钟"
                            )
                        }
                        Button(
                            onClick = onStart,
                            enabled = recommendedQuiz != null,
                            modifier = Modifier.fillMaxWidth().height(if (compactHero) 48.dp else 52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.actionOrange,
                                contentColor = colors.onPrimary
                            ),
                        ) {
                            Text("开始练习", fontSize = 17.sp, fontWeight = FontWeight.Bold);
                            Icon(Icons.Default.ChevronRight, null, modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 22.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (selectedTab == "专项") "专项练习" else if (recentQuizzes.isEmpty()) "精选练习" else "最近练习",
                        color = colors.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onLibrary) {
                        Text("查看全部", color = colors.primary);
                        Icon(Icons.Default.ChevronRight, null, tint = colors.primary)
                    }
                }
            }
            item {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("最近", "收藏", "专项").forEach { tab ->
                        val active = selectedTab == tab
                        Surface(
                            onClick = { if (tab == "收藏") onFavorites() else selectedTab = tab },
                            color = if (active) colors.primarySoft else colors.surface,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(44.dp),
                        ) {
                            Box(Modifier.padding(horizontal = 17.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    tab,
                                    color = if (active) colors.primary else colors.textSecondary,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
            items(visibleQuizzes, key = { it.id }) { quiz ->
                val index = visibleQuizzes.indexOf(quiz)
                PracticeRow(
                    quiz = quiz,
                    index = index,
                    mastery = LearningStats.masteryPercent(learningRecords, quiz.id, quiz.questions.count {
                        it.type != QuestionType.ESSAY
                    }),
                    onClick = { onQuizSelect(quiz) },
                )
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

@Composable
private fun HeroMeta(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    val colors = appColors()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = colors.primary, modifier = Modifier.size(17.dp));
        Text(text, color = colors.textSecondary, modifier = Modifier.padding(start = 5.dp), fontSize = 13.sp)
    }
}

@Composable
private fun PracticeRow(quiz: Quiz, index: Int, mastery: Int?, onClick: () -> Unit) {
    val colors = appColors()
    val tint = when (index % 3) { 0 -> colors.success;
        1 -> colors.violet;
        else -> colors.primary }
    val soft = when (index % 3) { 0 -> colors.greenSoft;
        1 -> colors.violetSoft;
        else -> colors.primarySoft }
    val icon = when (index % 3) { 0 -> Icons.Default.Hub;
        1 -> Icons.Default.ChatBubbleOutline;
        else -> Icons.Default.Description }
    Surface(
        onClick = onClick,
        color = soft,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(48.dp).background(tint.copy(alpha = .13f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(26.dp))
            }
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    quiz.title,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    quiz.subtitle,
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${quiz.questions.size} 题${mastery?.let { " · 掌握度 $it%" } ?: ""}",
                    color = colors.primary,
                    fontSize = 12.sp
                )
            }
            Icon(Icons.Default.ChevronRight, null, tint = tint)
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = appColors()
    val (icon, tint, soft) = when (label) {
        "累计答题" -> Triple(Icons.Default.Description, colors.success, colors.greenSoft)
        "累计测验" -> Triple(Icons.Default.SignalCellularAlt, colors.primary, colors.primarySoft)
        "今日已完成" -> Triple(Icons.Default.CheckCircle, colors.actionOrange, colors.actionOrangeSoft)
        else -> Triple(Icons.Default.AccessTime, colors.violet, colors.violetSoft)
    }
    Row(
        modifier.background(soft, RoundedCornerShape(16.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).background(tint.copy(alpha = .13f), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(23.dp))
        }
        Column(Modifier.weight(1f).padding(start = 9.dp)) {
            Text(
                value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            );
            Text(label, color = colors.textSecondary, fontSize = 11.sp, maxLines = 1)
        }
    }
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
    Scaffold(containerColor = colors.pageBackground, bottomBar = {
        AppBottomBar(Screen.QUIZZES, onHome, {}, onWrongBook, onProfile)
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Box(Modifier.fillMaxWidth().height(165.dp).background(colors.heroSky)) {
                    Image(
                        painterResource(R.drawable.home_network_hero),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.align(Alignment.BottomEnd).fillMaxWidth(.53f).height(145.dp),
                    )
                    Column(Modifier.align(Alignment.CenterStart).padding(start = 20.dp)) {
                        Text("题库", fontWeight = FontWeight.ExtraBold, fontSize = 29.sp, color = colors.textPrimary)
                        Text(
                            "按目标选择练习",
                            fontSize = 14.sp,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = colors.textSecondary) },
                        placeholder = { Text("搜索题库、协议或知识点", color = colors.textSecondary) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("全部", "工业网络", "英语").forEach { item ->
                            val active = category == item
                            Surface(onClick = {
                                category = item
                            }, color = if (active) colors.primary else colors.primarySoft, shape = RoundedCornerShape(
                                12.dp
                            ), modifier = Modifier.height(48.dp)) {
                                Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        item,
                                        color = if (active) colors.onPrimary else colors.textSecondary,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${filtered.size} 套练习",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = colors.textPrimary
                    )
                    Text("离线可用", color = colors.success, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
            if (filtered.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Search, null, tint = colors.textSecondary, modifier = Modifier.size(42.dp))
                        Text(
                            "没有找到相关题库",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 12.dp),
                            color = colors.textPrimary
                        )
                        TextButton(onClick = {
                            query = "";
                            category = "全部"
                        }) { Text("清除筛选") }
                    }
                }
            }
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
    val tint = when (entry.id.hashCode().ushr(1) % 3) { 0 -> colors.success;
        1 -> colors.violet;
        else -> colors.primary }
    val soft = when (entry.id.hashCode().ushr(1) % 3) { 0 -> colors.greenSoft;
        1 -> colors.violetSoft;
        else -> colors.primarySoft }
    Surface(
        onClick = onClick,
        color = soft,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(50.dp).background(tint.copy(alpha = .13f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (entry.examVariants != null) Icons.Default.Description else if (industrial) Icons.Default.Hub else Icons.Default.ChatBubbleOutline,
                    null,
                    tint = tint,
                    modifier = Modifier.size(26.dp)
                )
            }
            Column(Modifier.padding(start = 12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    entry.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    entry.subtitle,
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (entry.examVariants != null) "${entry.examVariants.size} 套试卷 · 离线可用" else "${entry.questionCount} 题 · 离线可用",
                    color = colors.primary,
                    fontSize = 12.sp
                )
            }
            Icon(Icons.Default.ChevronRight, null, tint = tint)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnswerScreen(
    quiz: Quiz,
    favoriteIds: Set<String>,
    onToggleFavorite: (String) -> Unit,
    onBack: () -> Unit,
    onSubmit: (AnswerBundle, Long) -> Unit
) {
    val colors = appColors()
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
        if (answeredCount() < quiz.questions.size) showSubmitConfirm = true else onSubmit(
            bundle(),
            (System.currentTimeMillis() - startedAt) / 1000
        )
    }
    Scaffold(
        containerColor = colors.pageBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        quiz.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { TextButton(onClick = onBack) { Text("退出", color = colors.primary) } },
                actions = {
                    IconButton(onClick = { onToggleFavorite(question.id) }) {
                        Icon(
                            if (question.id in favoriteIds) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            if (question.id in favoriteIds) "取消收藏" else "收藏题目",
                            tint = colors.primary,
                        )
                    }
                    TextButton(onClick = { showAnswerSheet = true }) { Text("答题卡", color = colors.primary) }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = colors.heroSky),
            )
        },
        bottomBar = {
            Column(
                Modifier.fillMaxWidth().background(colors.surface).imePadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { index-- },
                        enabled = index > 0,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                        border = BorderStroke(1.dp, colors.border),
                    ) { Text("上一题", color = if (index > 0) colors.primary else colors.textSecondary) }
                    Button(
                        onClick = { if (index < quiz.questions.lastIndex) index++ else submit() },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = colors.actionOrange,
                            contentColor = colors.onPrimary
                        ),
                    ) { Text(if (index < quiz.questions.lastIndex) "下一题" else "提交试卷") }
                }
                Text(
                    "已完成 ${answeredCount()} / ${quiz.questions.size}",
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = colors.textSecondary,
                    fontSize = 13.sp
                )
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(questionScroll)
                .padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("第 ${index + 1} 题", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    Text(" · ${question.type.label()}", fontSize = 13.sp, color = colors.textSecondary)
                }
                Text("共 ${quiz.questions.size} 题", color = colors.textSecondary)
            }
            LinearProgressIndicator(
                progress = { (index + 1f) / quiz.questions.size },
                modifier = Modifier.fillMaxWidth().height(5.dp),
                color = colors.primary,
                trackColor = colors.progressTrack,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                question.prompt,
                fontSize = 21.sp,
                lineHeight = 31.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            Text(question.type.instruction(), color = colors.textSecondary, fontSize = 14.sp)
            if (question.type == QuestionType.FILL || question.type == QuestionType.ESSAY) {
                OutlinedTextField(
                    value = textAnswers[question.id].orEmpty(),
                    onValueChange = { textAnswers[question.id] = it },
                    modifier = Modifier.fillMaxWidth().height(
                        if (question.type == QuestionType.ESSAY) 190.dp else 72.dp
                    ),
                    placeholder = { Text(if (question.type == QuestionType.ESSAY) "请输入你的回答，可分点作答" else "请输入答案") },
                    shape = RoundedCornerShape(18.dp),
                )
            }
            question.options.forEachIndexed { optionIndex, option ->
                val selected = optionAnswers[question.id].orEmpty().contains(optionIndex)
                Surface(
                    onClick = { selectOption(optionIndex) },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (selected) colors.selectedCard else colors.surface,
                    border = BorderStroke(
                        if (selected) 2.dp else 1.dp,
                        if (selected) colors.primary else colors.border
                    ),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(
                                36.dp
                            ).background(if (selected) colors.primary else colors.primarySoft, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                ('A'.code + optionIndex).toChar().toString(),
                                color = if (selected) colors.onPrimary else colors.textSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            option,
                            modifier = Modifier.padding(start = 14.dp).weight(1f),
                            fontSize = 17.sp,
                            lineHeight = 25.sp,
                            color = colors.textPrimary
                        )
                        if (question.type == QuestionType.MULTIPLE) {
                            Box(
                                Modifier.size(
                                    25.dp
                                ).border(
                                    2.dp,
                                    if (selected) colors.primary else colors.textSecondary,
                                    RoundedCornerShape(7.dp)
                                )
                                    .background(
                                        if (selected) colors.primary else Color.Transparent,
                                        RoundedCornerShape(7.dp)
                                    ),
                                contentAlignment = Alignment.Center,
                            ) { if (selected) Text("✓", color = colors.onPrimary, fontWeight = FontWeight.Bold) }
                        } else {
                            RadioButton(
                                selected = selected,
                                onClick = null,
                                colors = androidx.compose.material3.RadioButtonDefaults.colors(
                                    selectedColor = colors.primary,
                                    unselectedColor = colors.textSecondary
                                ),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
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
            icon = { Icon(Icons.Default.ErrorOutline, null, tint = colors.actionOrange) },
            title = { Text("还有题目未完成") },
            text = { Text("当前还有 ${quiz.questions.size - answeredCount()} 道题未作答。未答题将按错误计算，确定提交吗？") },
            confirmButton = {
                Button(
                    onClick = { showSubmitConfirm = false },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = colors.actionOrange,
                        contentColor = colors.onPrimary
                    ),
                ) { Text("继续答题") }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showSubmitConfirm = false;
                    onSubmit(bundle(), (System.currentTimeMillis() - startedAt) / 1000)
                }) { Text("仍然提交") }
            },
            containerColor = colors.surface,
            shape = RoundedCornerShape(24.dp),
        )
    }
}

private class AiAnalysisState(defaultCollapsed: Boolean) {
    var text by mutableStateOf("")
    var info by mutableStateOf("")
    var loading by mutableStateOf(false)
    var collapsed by mutableStateOf(defaultCollapsed)
    var lastGeneratedAt by mutableStateOf(0L)
}

private class AiReviewState {
    var result by mutableStateOf<AiQuestionResult?>(null)
    var text by mutableStateOf("")
    var error by mutableStateOf("")
    var loading by mutableStateOf(false)
    var expanded by mutableStateOf(false)
}

@Composable
private fun ResultScreen(
    quiz: Quiz,
    score: Int,
    answers: AnswerBundle,
    durationSeconds: Long,
    favoriteIds: Set<String>,
    onToggleFavorite: (String) -> Unit,
    onHome: () -> Unit,
    onRetry: () -> Unit,
    onWrongBook: () -> Unit,
) {
    val colors = appColors()
    val context = LocalContext.current
    val client = remember(context) { AiClient(context) }
    val scope = rememberCoroutineScope()
    val objectiveCount = quiz.questions.count { it.type != QuestionType.ESSAY }
    val wrongCount = (objectiveCount - score).coerceAtLeast(0)
    val rate = if (objectiveCount > 0) score * 100 / objectiveCount else 0
    val analysisState = remember(quiz.id, answers) { AiAnalysisState(wrongCount > 0) }
    val reviewStates = remember(quiz.id, answers) {
        quiz.questions.associate { it.id to AiReviewState() }
    }
    var reviewFilter by remember { mutableStateOf("需要巩固") }
    val reviewQuestions = if (reviewFilter == "全部") quiz.questions else quiz.questions.filter {
        it.type != QuestionType.ESSAY && !QuizEngine.isCorrect(it, answers)
    }
    LazyColumn(
        Modifier.fillMaxSize().background(colors.pageBackground),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().background(colors.heroSky).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onHome) { Icon(Icons.Default.ArrowBack, "返回", tint = colors.primary) }
                Column {
                    Text(
                        quiz.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = colors.textPrimary,
                        maxLines = 1
                    )
                    Text("练习结果", color = colors.textSecondary, fontSize = 12.sp)
                }
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("测验完成", color = colors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        objectiveCount == 0 -> "问答题已提交，可参考答案进行自评。"
                        rate >= 80 -> "继续加油，掌握更多工业网络知识！"
                        else -> "查看解析，巩固本次练习的薄弱点。"
                    },
                    color = colors.textSecondary,
                    fontSize = 14.sp,
                )
            }
        }
        item {
            Card(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, colors.border),
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    if (objectiveCount == 0) "—" else "$rate",
                                    color = colors.primary,
                                    fontSize = 48.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (objectiveCount == 0) " 待自评" else " 分",
                                    color = colors.textPrimary,
                                    fontSize = 17.sp,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                            Text(
                                if (objectiveCount == 0) "本套均为问答题" else "客观题共 $objectiveCount 题",
                                color = colors.textSecondary,
                                fontSize = 13.sp
                            )
                        }
                        Box(
                            Modifier.size(
                                64.dp
                            ).background(
                                if (rate >= 80 && objectiveCount > 0) colors.greenSoft else colors.actionOrangeSoft,
                                CircleShape
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (rate >= 80 && objectiveCount > 0) Icons.Default.CheckCircle else Icons.Default.Description,
                                null,
                                tint = if (rate >= 80 && objectiveCount > 0) colors.success else colors.actionOrange,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ResultStat("答对", "$score / $objectiveCount", Modifier.weight(1f))
                        ResultStat("待巩固", "$wrongCount 题", Modifier.weight(1f))
                        ResultStat("用时", formatDuration(durationSeconds), Modifier.weight(1f))
                    }
                    Button(
                        onClick = if (wrongCount > 0) onWrongBook else onHome,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = colors.actionOrange,
                            contentColor = colors.onPrimary
                        ),
                    ) { Text(if (wrongCount > 0) "复习错题" else "返回首页", fontWeight = FontWeight.Bold) }
                    OutlinedButton(
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, colors.primary),
                    ) {
                        Icon(Icons.Default.Refresh, null, tint = colors.primary, modifier = Modifier.size(18.dp));
                        Text(" 再练一次", color = colors.primary)
                    }
                }
            }
        }
        item { AiAnalysisPanel(quiz, score, answers, client, scope, analysisState) }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(colors.surface, RoundedCornerShape(18.dp)).padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("需要巩固" to wrongCount, "全部" to quiz.questions.size).forEach { (tab, count) ->
                    Surface(
                        onClick = { reviewFilter = tab },
                        modifier = Modifier.weight(1f),
                        color = if (reviewFilter == tab) colors.primarySoft else colors.surface,
                        shape = RoundedCornerShape(13.dp),
                    ) {
                        Box(Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "$tab ($count)",
                                color = if (reviewFilter == tab) colors.primary else colors.textSecondary,
                                fontWeight = if (reviewFilter == tab) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
        if (reviewQuestions.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(38.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CheckCircle, null, tint = colors.success, modifier = Modifier.size(42.dp))
                    Text(
                        if (wrongCount == 0) "本次没有错题" else "这里暂时没有题目",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 10.dp),
                        color = colors.textPrimary
                    )
                }
            }
        }
        items(reviewQuestions) { question ->
            val aiState = reviewStates.getValue(question.id)
            Box(Modifier.padding(horizontal = 16.dp, vertical = 7.dp)) {
                ReviewCard(
                    quiz.questions.indexOf(question) + 1,
                    question,
                    answers,
                    favoriteIds,
                    onToggleFavorite,
                    aiState,
                    onGenerateAi = {
                        if (!aiState.loading) {
                            aiState.loading = true
                            aiState.error = ""
                            aiState.text = ""
                            aiState.result = null
                            scope.launch {
                                runCatching { client.explain(question, answers) { partial -> aiState.text = partial } }
                                    .onSuccess { result ->
                                        aiState.result = result
                                        aiState.text = result.text
                                    }
                                    .onFailure {
                                        if (it is CancellationException) throw it
                                        aiState.error = aiFailureMessage(it, "AI 解析失败，请检查网络后重试")
                                    }
                                aiState.loading = false
                            }
                        }
                    }
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ResultStat(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = appColors()
    Column(
        modifier.background(colors.primarySoft, RoundedCornerShape(13.dp)).padding(horizontal = 9.dp, vertical = 11.dp)
    ) {
        Text(label, color = colors.textSecondary, fontSize = 11.sp)
        Text(value, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

private fun QuestionType.label() = when (this) { QuestionType.SINGLE -> "单选题";
    QuestionType.MULTIPLE -> "多选题";
    QuestionType.TRUE_FALSE -> "判断题";
    QuestionType.FILL -> "填空题";
    QuestionType.ESSAY -> "问答题" }
private fun QuestionType.instruction() = when (this) { QuestionType.SINGLE, QuestionType.TRUE_FALSE -> "请选择一个最合适的答案";
    QuestionType.MULTIPLE -> "本题有多个正确答案，请选择全部正确项";
    QuestionType.FILL -> "请在下方填写答案";
    QuestionType.ESSAY -> "请根据要点组织你的回答" }

@Composable
private fun ReviewCard(
    number: Int,
    question: Question,
    answers: AnswerBundle,
    favoriteIds: Set<String>,
    onToggleFavorite: (String) -> Unit,
    aiState: AiReviewState,
    onGenerateAi: () -> Unit,
) {
    val colors = appColors()
    val messenger = LocalAppMessenger.current
    val correct = QuizEngine.isCorrect(question, answers)
    val answerText = when (question.type) {
        QuestionType.SINGLE, QuestionType.MULTIPLE, QuestionType.TRUE_FALSE -> question.answerIndices.sorted().joinToString(
            "、"
        ) {
            "${('A'.code + it).toChar()} ${question.options.getOrNull(it).orEmpty()}"
        }
        QuestionType.FILL -> question.acceptedAnswers.joinToString(" / ")
        QuestionType.ESSAY -> question.referenceAnswer
    }
    val userAnswer = when (question.type) {
        QuestionType.SINGLE, QuestionType.MULTIPLE, QuestionType.TRUE_FALSE -> answers.optionAnswers[question.id].orEmpty().sorted().joinToString(
            "、"
        ) {
            "${('A'.code + it).toChar()} ${question.options.getOrNull(it).orEmpty()}"
        }.ifBlank { "未作答" }
        QuestionType.FILL, QuestionType.ESSAY -> answers.textAnswers[question.id].orEmpty().ifBlank { "未作答" }
    }
    val status = when {
        question.type == QuestionType.ESSAY -> "待自评"
        !answers.isAnswered(question) -> "未作答"
        correct -> "回答正确"
        else -> "待巩固"
    }
    val statusColor = when {
        question.type == QuestionType.ESSAY -> colors.violet
        correct -> colors.success
        else -> colors.actionOrange
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = if (correct) colors.greenSoft else colors.actionOrangeSoft,
                        shape = RoundedCornerShape(9.dp)
                    ) {
                        Text(
                            "第 $number 题",
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    Text(question.type.label(), color = colors.textSecondary, fontSize = 12.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        onToggleFavorite(question.id);
                        messenger.show(if (question.id in favoriteIds) "已取消收藏" else "已加入收藏")
                    }) {
                        Icon(
                            if (question.id in favoriteIds) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            if (question.id in favoriteIds) "取消收藏" else "收藏题目",
                            tint = colors.primary
                        )
                    }
                    Text(status, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Text(
                question.prompt,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                color = colors.textPrimary
            )
            Column(
                Modifier.fillMaxWidth().background(colors.pageBackground, RoundedCornerShape(14.dp)).padding(13.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "你的答案：$userAnswer",
                    color = if (correct) colors.textPrimary else colors.textSecondary,
                    fontSize = 14.sp,
                    lineHeight = 21.sp
                )
                Text(
                    if (question.type == QuestionType.ESSAY) "参考答案：$answerText" else "正确答案：$answerText",
                    color = colors.success,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 21.sp
                )
            }
            TextButton(onClick = { aiState.expanded = !aiState.expanded }, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    if (aiState.expanded) Icons.Default.ExpandLess else Icons.Default.Description,
                    null,
                    tint = colors.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(if (aiState.expanded) " 收起解析" else " 查看解析", color = colors.primary, fontWeight = FontWeight.Bold)
            }
            if (aiState.expanded) {
                Text(question.explanation, color = colors.textSecondary, fontSize = 14.sp, lineHeight = 22.sp)
                if (aiState.text.isNotBlank() || aiState.error.isNotBlank() || aiState.loading) {
                    Surface(color = colors.violetSoft, shape = RoundedCornerShape(14.dp)) {
                        Column(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    null,
                                    tint = colors.violet,
                                    modifier = Modifier.size(18.dp)
                                );
                                Text(" AI 深度解析", color = colors.textPrimary, fontWeight = FontWeight.Bold)
                            }
                            aiState.result?.score?.let {
                                Text(
                                    "AI 评分：${formatAiScore(it)} / ${aiState.result?.maxScore ?: 10}",
                                    color = colors.success,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (aiState.text.isNotBlank()) AiFormattedText(aiState.text)
                            if (aiState.error.isNotBlank()) {
                                Text(
                                    aiState.error,
                                    color = if (aiState.error.contains("冷却")) colors.warning else colors.danger,
                                    fontSize = 13.sp
                                )
                            }
                            if (aiState.loading && aiState.text.isBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp);
                                    Text(" 正在生成解析…", color = colors.textSecondary, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
                OutlinedButton(
                    enabled = !aiState.loading,
                    onClick = onGenerateAi,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, colors.violet),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = colors.violet
                    ),
                ) {
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(17.dp));
                    Text(
                        when {
                            aiState.loading -> " 正在生成解析…"
                            aiState.result == null -> " AI 深度解析"
                            else -> " 重新生成解析"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AiAnalysisPanel(
    quiz: Quiz,
    score: Int,
    answers: AnswerBundle,
    client: AiClient,
    scope: CoroutineScope,
    state: AiAnalysisState,
) {
    val colors = appColors()
    val context = LocalContext.current
    val settingsStore = remember(context) { AiSettingsStore(context) }
    var cooldownLeftMs by remember { mutableStateOf(0L) }
    LaunchedEffect(state.lastGeneratedAt) {
        while (true) {
            val settings = settingsStore.load()
            cooldownLeftMs = if (settings.mode == AiMode.SHARED && state.lastGeneratedAt > 0) {
                (120_000 - (System.currentTimeMillis() - state.lastGeneratedAt)).coerceAtLeast(0)
            } else {
                0L
            }
            kotlinx.coroutines.delay(1_000)
        }
    }
    Card(
        Modifier.padding(horizontal = 16.dp, vertical = 16.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.violetSoft),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, colors.violet.copy(alpha = .18f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(38.dp).background(colors.surface, RoundedCornerShape(11.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, tint = colors.violet)
                    };
                    Column(Modifier.padding(start = 11.dp)) {
                        Text("AI 学情分析", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = colors.textPrimary);
                        Text(
                            if (settingsStore.load().mode == AiMode.OWN_KEY) "自带 Key · 完整模式" else "站点默认 AI · 精炼模式",
                            color = colors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
                if (state.text.isNotBlank()) {
                    IconButton(onClick = {
                        state.collapsed = !state.collapsed
                    }) {
                        Icon(
                            if (state.collapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                            if (state.collapsed) "展开" else "收起"
                        )
                    }
                }
            }
            if (!state.collapsed) {
                if (state.text.isBlank() && !state.loading && state.info.isBlank()) Text(
                    "根据本次作答总结薄弱知识点、典型错因和记忆方法。",
                    color = colors.textSecondary,
                    lineHeight = 21.sp
                )
                if (state.text.isNotBlank()) AiFormattedText(state.text)
                if (state.loading && state.text.isBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp);
                        Text(" 正在分析学情，请稍候，无需重复点击…", color = colors.textSecondary, fontSize = 13.sp)
                    }
                }
                if (state.info.isNotBlank()) Text(state.info, color = colors.warning, fontSize = 13.sp)
            }
            Button(
                enabled = !state.loading && cooldownLeftMs == 0L,
                onClick = {
                    if (state.loading) return@Button
                    val settings = settingsStore.load()
                    val remainingMs = if (settings.mode == AiMode.SHARED && state.lastGeneratedAt > 0) {
                        (120_000 - (System.currentTimeMillis() - state.lastGeneratedAt)).coerceAtLeast(0)
                    } else {
                        0L
                    }
                    state.collapsed = false
                    if (remainingMs > 0) {
                        state.info = "共享 AI 冷却中，约 ${remainingMs / 60_000 + 1} 分钟后可重新分析"
                    } else {
                        state.loading = true
                        state.info = ""
                        state.text = ""
                        scope.launch {
                            runCatching { client.analyze(quiz, score, answers) { partial -> state.text = partial } }
                                .onSuccess { result ->
                                    state.text = result;
                                    state.lastGeneratedAt = System.currentTimeMillis()
                                }
                                .onFailure {
                                    if (it is CancellationException) throw it
                                    state.info = aiFailureMessage(it, "学情分析失败，请检查网络后重试")
                                }
                            state.loading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = colors.violet,
                    contentColor = colors.onPrimary
                ),
            ) {
                Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(17.dp))
                Text(
                    when {
                        state.loading -> " 正在分析…"
                        cooldownLeftMs > 0 && state.text.isNotBlank() -> " 冷却中 ${cooldownLeftMs / 60_000}:${"%02d".format(
                            (cooldownLeftMs / 1000) % 60
                        )}"
                        state.text.isBlank() -> " 生成学情分析"
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
            Text(
                line.replace("**", "").trim(),
                color = if (heading) colors.textPrimary else colors.textSecondary,
                fontWeight = if (heading) FontWeight.Bold else FontWeight.Normal,
                fontSize = if (heading) 15.sp else 14.sp,
                lineHeight = 22.sp
            )
        }
    }
}

private fun formatAiScore(score: Double): String = if (score % 1.0 == 0.0) score.toInt().toString() else "%.1f".format(
    Locale.CHINA,
    score
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WrongBookScreen(
    quizzes: List<Quiz>,
    wrongItems: List<WrongItem>,
    onHome: () -> Unit,
    onLibrary: () -> Unit,
    onProfile: () -> Unit,
    onPractice: (List<Question>) -> Unit
) {
    val colors = appColors()
    val compactEmpty = LocalConfiguration.current.screenHeightDp < 720 || LocalDensity.current.fontScale > 1.25f
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
    Scaffold(containerColor = colors.pageBackground, bottomBar = {
        AppBottomBar(Screen.WRONG_BOOK, onHome, onLibrary, {}, onProfile)
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Column(
                    Modifier.fillMaxWidth().background(
                        colors.heroSky
                    ).padding(horizontal = 20.dp, vertical = if (compactEmpty) 20.dp else 29.dp)
                ) {
                    Text("复习", fontWeight = FontWeight.ExtraBold, fontSize = 29.sp, color = colors.textPrimary)
                    Text(
                        "巩固薄弱知识点",
                        fontSize = 14.sp,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            item {
                Surface(
                    color = colors.surface,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(5.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        listOf("今日复习", "未掌握", "复习中", "已掌握").forEach { tab ->
                            val active = filter == tab
                            Surface(onClick = {
                                filter = tab
                            }, color = if (active) colors.primary else colors.surface, shape = RoundedCornerShape(
                                15.dp
                            ), modifier = Modifier.weight(1f).height(48.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        tab,
                                        fontSize = 12.sp,
                                        color = if (active) colors.onPrimary else colors.textSecondary,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (validItems.isNotEmpty()) {
                item {
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                if (dueItems.isEmpty()) "今日任务已完成" else "${dueItems.size} 道题等待巩固",
                                color = colors.textPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (dueItems.isEmpty()) "下一批题目会按计划自动出现" else "这些题目已经到了复习时间",
                                color = colors.textSecondary,
                                fontSize = 13.sp
                            )
                            if (practiceQuestions.isNotEmpty()) {
                                Button(
                                    onClick = { onPractice(practiceQuestions) },
                                    modifier = Modifier.fillMaxWidth().height(50.dp),
                                    shape = RoundedCornerShape(15.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.actionOrange,
                                        contentColor = colors.onPrimary
                                    ),
                                ) {
                                    Text("开始今日复习", fontWeight = FontWeight.Bold);
                                    Icon(Icons.Default.ChevronRight, null)
                                }
                            }
                        }
                    }
                }
            }
            if (filteredItems.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painterResource(R.drawable.review_empty_art),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().height(if (compactEmpty) 150.dp else 238.dp)
                        )
                        Text(
                            if (filter == "今日复习") "今天没有待复习题目" else "这里暂时没有题目",
                            color = colors.textPrimary,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        Text(
                            if (filter == "今日复习") "完成练习后，薄弱题目会自动出现在这里" else "切换其他分类看看，或先完成一套练习",
                            color = colors.textSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        if (filter == "今日复习") {
                            Button(
                                onClick = onLibrary,
                                modifier = Modifier.fillMaxWidth().padding(
                                    horizontal = 25.dp,
                                    vertical = if (compactEmpty) 12.dp else 22.dp
                                ).height(52.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.actionOrange,
                                    contentColor = colors.onPrimary
                                ),
                            ) {
                                Text("去选择一套练习", fontWeight = FontWeight.Bold);
                                Icon(Icons.Default.ChevronRight, null)
                            }
                        }
                    }
                }
            }
            if (filteredItems.isNotEmpty()) item {
                Text(
                    "共 ${filteredItems.size} 题",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    color = colors.textSecondary,
                    fontSize = 13.sp
                )
            }
            items(filteredItems, key = { it.questionId }) { item ->
                val question = questionsById[item.questionId] ?: return@items
                val statusText = when (item.status) { ReviewStatus.UNMASTERED -> "未掌握";
                    ReviewStatus.REVIEWING -> "复习中 ${item.correctStreak}/3";
                    ReviewStatus.MASTERED -> "已掌握" }
                val statusColor = when (item.status) { ReviewStatus.UNMASTERED -> colors.actionOrange;
                    ReviewStatus.REVIEWING -> colors.primary;
                    ReviewStatus.MASTERED -> colors.success }
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                "${question.type.label()} · 错 ${item.wrongTimes} 次",
                                color = colors.textSecondary,
                                fontSize = 12.sp
                            )
                            Text(statusText, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            question.prompt,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "正确答案：${question.correctAnswerText()}",
                            color = colors.success,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            question.explanation,
                            color = colors.textSecondary,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

private fun Question.correctAnswerText(): String = when (type) {
    QuestionType.SINGLE, QuestionType.MULTIPLE, QuestionType.TRUE_FALSE -> answerIndices.sorted().joinToString("、") {
        options[it]
    }
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
    val colors = appColors()
    val compactEmpty = LocalConfiguration.current.screenHeightDp < 720 || LocalDensity.current.fontScale > 1.25f
    val entries = quizzes.flatMap { quiz -> quiz.questions.filter { it.id in favoriteIds }.map { quiz to it } }
    val quizFilters = listOf("全部") + entries.map { it.first.title }.distinct()
    val typeFilters = listOf("全部") + entries.map { it.second.type.label() }.distinct()
    var quizFilter by remember { mutableStateOf("全部") }
    var typeFilter by remember { mutableStateOf("全部") }
    var quizMenuOpen by remember { mutableStateOf(false) }
    var typeMenuOpen by remember { mutableStateOf(false) }
    val filtered = entries.filter { (quiz, question) ->
        (quizFilter == "全部" || quiz.title == quizFilter) && (typeFilter == "全部" || question.type.label() == typeFilter)
    }
    Scaffold(containerColor = colors.pageBackground) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Row(
                    Modifier.fillMaxWidth().background(colors.heroSky).padding(horizontal = 16.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onHome, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.ArrowBack, "返回首页", tint = colors.textPrimary)
                    }
                    Text(
                        "我的收藏",
                        modifier = Modifier.weight(1f),
                        color = colors.textPrimary,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Icon(Icons.Default.Bookmark, null, tint = colors.violet, modifier = Modifier.size(25.dp))
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(Modifier.weight(1f)) {
                        Surface(onClick = {
                            quizMenuOpen = true
                        }, color = colors.surface, shape = RoundedCornerShape(
                            14.dp
                        ), border = BorderStroke(
                            1.dp,
                            colors.border
                        ), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (quizFilter == "全部") "全部题库" else quizFilter,
                                    modifier = Modifier.weight(1f),
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 13.sp
                                )
                                Icon(Icons.Default.ExpandMore, null, tint = colors.textSecondary)
                            }
                        }
                        DropdownMenu(expanded = quizMenuOpen, onDismissRequest = { quizMenuOpen = false }) {
                            quizFilters.forEach { label -> DropdownMenuItem(text = {
                                Text(if (label == "全部") "全部题库" else label)
                            }, onClick = {
                                quizFilter = label;
                                quizMenuOpen = false
                            }) }
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        Surface(onClick = {
                            typeMenuOpen = true
                        }, color = colors.surface, shape = RoundedCornerShape(
                            14.dp
                        ), border = BorderStroke(
                            1.dp,
                            colors.border
                        ), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (typeFilter == "全部") "全部题型" else typeFilter,
                                    modifier = Modifier.weight(1f),
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 13.sp
                                )
                                Icon(Icons.Default.ExpandMore, null, tint = colors.textSecondary)
                            }
                        }
                        DropdownMenu(expanded = typeMenuOpen, onDismissRequest = { typeMenuOpen = false }) {
                            typeFilters.forEach { label -> DropdownMenuItem(text = {
                                Text(if (label == "全部") "全部题型" else label)
                            }, onClick = {
                                typeFilter = label;
                                typeMenuOpen = false
                            }) }
                        }
                    }
                }
            }
            item {
                Text(
                    "已收藏 ${filtered.size} 题",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    color = colors.textSecondary,
                    fontSize = 14.sp
                )
            }
            if (filtered.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painterResource(R.drawable.favorites_empty_art),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().height(if (compactEmpty) 170.dp else 250.dp)
                        )
                        Text(
                            if (entries.isEmpty()) "还没有收藏题目" else "没有符合筛选的题目",
                            color = colors.textPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Text(
                            if (entries.isEmpty()) "答题时点击收藏，重要知识点就会留在这里" else "试着切换题库或题型",
                            color = colors.textSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 7.dp)
                        )
                        Button(
                            onClick = { if (entries.isEmpty()) onLibrary() else {
                                quizFilter = "全部";
                                typeFilter = "全部"
                            }
                            },
                            modifier = Modifier.fillMaxWidth().padding(
                                horizontal = 24.dp,
                                vertical = if (compactEmpty) 12.dp else 24.dp
                            ).height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.actionOrange,
                                contentColor = colors.onPrimary
                            ),
                        ) {
                            Text(if (entries.isEmpty()) "去题库练习" else "清除筛选", fontWeight = FontWeight.Bold);
                            Icon(Icons.Default.ChevronRight, null)
                        }
                    }
                }
            }
            if (filtered.isNotEmpty()) {
                item {
                    Button(
                        onClick = { onPractice(filtered.map { it.second }) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.actionOrange,
                            contentColor = colors.onPrimary
                        ),
                    ) {
                        Icon(Icons.Default.PlayArrow, null);
                        Text("开始收藏练习", fontWeight = FontWeight.Bold)
                    }
                }
            }
            items(filtered, key = { it.second.id }) { (quiz, question) ->
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "${quiz.title} · ${question.type.label()}",
                                color = colors.violet,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                question.prompt,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = {
                            onToggleFavorite(question.id)
                        }, modifier = Modifier.size(
                            48.dp
                        )) { Icon(Icons.Default.Bookmark, "取消收藏", tint = colors.violet) }
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
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
            .onFailure { error ->
                syncMessage = "同步失败";
                onSyncFailed(error.message ?: "请检查网络后重试")
            }
        syncing = false
        isRefreshing = false
    }
    Scaffold(containerColor = colors.pageBackground, bottomBar = {
        AppBottomBar(Screen.PROFILE, onHome, onLibrary, onWrongBook, {})
    }) { padding ->
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
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    Box(Modifier.fillMaxWidth().height(145.dp).background(colors.heroSky)) {
                        Column(Modifier.align(Alignment.CenterStart).padding(start = 20.dp)) {
                            Text("我的", fontSize = 29.sp, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary)
                            Text(
                                "学习数据与应用设置",
                                color = colors.textSecondary,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Box(
                            Modifier.align(
                                Alignment.CenterEnd
                            ).padding(
                                end = 25.dp
                            ).size(82.dp).background(colors.primarySoft, RoundedCornerShape(24.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.MenuBook, null, tint = colors.primary, modifier = Modifier.size(43.dp))
                        }
                    }
                }
                item {
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(54.dp).background(colors.greenSoft, RoundedCornerShape(17.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.School, null, tint = colors.success, modifier = Modifier.size(29.dp))
                            }
                            Column(Modifier.padding(start = 14.dp)) {
                                Text(
                                    "本地学习档案",
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )
                                Text(
                                    "数据仅保存在这台设备",
                                    color = colors.textSecondary,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(top = 3.dp)
                                )
                            }
                        }
                    }
                }
                item {
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("学习概览", color = colors.textPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Metric("累计答题", "${summary.questionCount} 道", Modifier.weight(1f))
                                Metric("累计测验", "${summary.quizCount} 次", Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Metric("今日已完成", "${summary.todayQuestionCount} 道", Modifier.weight(1f))
                                Metric("连续学习", "${LearningStats.streakDays(records)} 天", Modifier.weight(1f))
                            }
                        }
                    }
                }
                item {
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "错题掌握进度",
                                    color = colors.textPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "$mastered / ${wrongItems.size}",
                                    color = colors.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            LinearProgressIndicator(
                                progress = { if (wrongItems.isEmpty()) 0f else mastered.toFloat() / wrongItems.size },
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                color = colors.success,
                                trackColor = colors.progressTrack,
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("未掌握 $unmastered", color = colors.actionOrange, fontSize = 12.sp)
                                Text("复习中 $reviewing", color = colors.primary, fontSize = 12.sp)
                                Text("已掌握 $mastered", color = colors.success, fontSize = 12.sp)
                            }
                        }
                    }
                }
                item {
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column {
                            Row(
                                Modifier.fillMaxWidth().clickable(onClick = onLearningReport).padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.size(48.dp).background(colors.greenSoft, RoundedCornerShape(14.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.SignalCellularAlt, null, tint = colors.success)
                                }
                                Column(Modifier.weight(1f).padding(start = 13.dp)) {
                                    Text(
                                        "学习报告",
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    );
                                    Text("查看学习数据与掌握情况", color = colors.textSecondary, fontSize = 12.sp)
                                }
                                Icon(Icons.Default.ChevronRight, null, tint = colors.textSecondary)
                            }
                            Box(
                                Modifier.fillMaxWidth().padding(
                                    horizontal = 16.dp
                                ).height(1.dp).background(colors.border)
                            )
                            Row(Modifier.fillMaxWidth().clickable(enabled = !syncing && isOnline) {
                                scope.launch { runSync() }
                            }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(48.dp).background(colors.violetSoft, RoundedCornerShape(14.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.CloudSync, null, tint = colors.violet)
                                }
                                Column(Modifier.weight(1f).padding(start = 13.dp)) {
                                    Text(
                                        "GitHub 题库同步",
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    );
                                    Text(
                                        if (syncing) "正在同步…" else "当前 $quizCount 套 · $questionCount 题",
                                        color = colors.textSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                                Icon(Icons.Default.ChevronRight, null, tint = colors.textSecondary)
                            }
                            Box(
                                Modifier.fillMaxWidth().padding(
                                    horizontal = 16.dp
                                ).height(1.dp).background(colors.border)
                            )
                            Row(
                                Modifier.fillMaxWidth().clickable(onClick = onSettings).padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.size(48.dp).background(colors.primarySoft, RoundedCornerShape(14.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Tune, null, tint = colors.primary)
                                }
                                Column(Modifier.weight(1f).padding(start = 13.dp)) {
                                    Text(
                                        "应用设置",
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    );
                                    Text("备份、AI、更新与关于", color = colors.textSecondary, fontSize = 12.sp)
                                }
                                Icon(Icons.Default.ChevronRight, null, tint = colors.textSecondary)
                            }
                        }
                    }
                }
                if (syncMessage.startsWith("同步失败") || !isOnline) {
                    item {
                        Text(
                            if (!isOnline) "当前无网络，连接后可同步题库" else syncMessage,
                            modifier = Modifier.padding(horizontal = 24.dp),
                            color = colors.warning,
                            fontSize = 12.sp
                        )
                    }
                }
                if (!syncMessage.startsWith("同步失败")) item {
                    Text(
                        syncMessage,
                        modifier = Modifier.padding(horizontal = 24.dp),
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )
                }
                item { Spacer(Modifier.height(18.dp)) }
            }
        }
    }
}

private fun formatDuration(seconds: Long): String = when {
    seconds < 60 -> "${seconds.coerceAtLeast(1)} 秒"
    else -> "${seconds / 60}分${seconds % 60}秒"
}

@Composable
private fun AppBottomBar(
    selected: Screen?,
    onHome: () -> Unit,
    onLibrary: () -> Unit,
    onWrongBook: () -> Unit,
    onProfile: () -> Unit
) {
    val colors = appColors()
    Box(
        Modifier.fillMaxWidth().background(
            colors.pageBackground
        ).navigationBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Surface(
            color = colors.surface,
            shape = RoundedCornerShape(25.dp),
            shadowElevation = 7.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.fillMaxWidth().height(70.dp)) {
                listOf(
                    Triple(Screen.HOME, "首页", onHome),
                    Triple(Screen.QUIZZES, "题库", onLibrary),
                    Triple(Screen.WRONG_BOOK, "复习", onWrongBook),
                    Triple(Screen.PROFILE, "我的", onProfile),
                ).forEach { (screen, label, action) ->
                    val active = selected == screen
                    Column(
                        Modifier.weight(
                            1f
                        ).fillMaxSize().clickable(onClick = action).padding(top = 8.dp, bottom = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            when (screen) { Screen.HOME -> Icons.Default.Home;
                                Screen.QUIZZES -> Icons.Default.MenuBook;
                                Screen.WRONG_BOOK -> Icons.Default.History;
                                else -> Icons.Default.Person },
                            contentDescription = null,
                            tint = if (active) colors.primary else colors.textSecondary,
                            modifier = Modifier.size(23.dp),
                        )
                        Text(
                            label,
                            color = if (active) colors.primary else colors.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                        )
                        Box(
                            Modifier.padding(
                                top = 3.dp
                            ).size(
                                width = 19.dp,
                                height = 3.dp
                            ).background(if (active) colors.primary else Color.Transparent, RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }
    }
}
