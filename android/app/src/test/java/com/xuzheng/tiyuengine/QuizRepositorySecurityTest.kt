package com.xuzheng.tiyuengine

import android.content.Context
import com.xuzheng.tiyuengine.data.QuizRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class QuizRepositorySecurityTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun existingBundledQuizFilesRemainCompatible() {
        // Robolectric's Windows ZIP asset reader cannot open these non-ASCII entry names.
        // Exercise the same repository parser with an isolated copy of the actual bundled files.
        val bundled = File("src/main/assets/data")
        assertTrue(bundled.isDirectory)
        assertTrue(bundled.copyRecursively(File(context.filesDir, "quiz_data")))
        val quizzes = QuizRepository(context).loadQuizzes()
        assertEquals(23, quizzes.size)
        assertEquals(987, quizzes.sumOf { it.questions.size })
    }

    @Test
    fun interruptedSwapRecoversOldQuizzesBeforeReading() {
        val backup = File(context.filesDir, "quiz_data_backup").apply { mkdirs() }
        writeQuiz(backup)
        val quizzes = QuizRepository(context).loadQuizzes()
        assertEquals("saved", quizzes.single().id)
        assertTrue(File(context.filesDir, "quiz_data/manifest.json").isFile)
        assertFalse(backup.exists())
    }

    @Test
    fun manifestCannotReadOutsideQuizDirectory() {
        val active = File(context.filesDir, "quiz_data").apply { mkdirs() }
        active.resolve("manifest.json").writeText("""{"quizzes":[{"id":"saved","file":"../outside.json"}]}""")
        assertThrows(IllegalArgumentException::class.java) { QuizRepository(context).loadQuizzes() }
    }

    @Test
    fun repeatedFileReferencesCannotMultiplyParsedContent() {
        val active = File(context.filesDir, "quiz_data").apply { mkdirs() }
        writeQuiz(active)
        active.resolve("manifest.json").writeText(
            """{"quizzes":[{"id":"a","file":"q.json"},{"id":"b","file":"q.json"}]}""",
        )
        val error = assertThrows(IllegalArgumentException::class.java) {
            QuizRepository(context).loadQuizzes()
        }
        assertEquals("清单重复引用题库文件", error.message)
    }

    private fun writeQuiz(directory: File) {
        directory.resolve("manifest.json").writeText("""{"quizzes":[{"id":"saved","file":"q.json"}]}""")
        directory.resolve("q.json").writeText(
            """{"title":"Saved","questions":[
                {"type":"单选题","title":"Q","options":["A","B"],"correct_answer":"A. A"}
            ]}""",
        )
    }
}
