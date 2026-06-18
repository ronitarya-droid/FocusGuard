package com.focus.guard

import android.content.Context

data class StudyTask(
    val id: String,
    val subject: String,
    val book: String,
    val chapter: String,
    val title: String,
    val questions: Int,
    val lectures: Int,
    val estimatedMinutes: Int,
    val kind: String
)

object StudyData {
    fun load(context: Context): List<StudyTask> =
        loadBooks(context) + loadLectures(context)

    private fun loadBooks(context: Context): List<StudyTask> {
        val text = readAsset(context, "books_data.txt")
        val tasks = mutableListOf<StudyTask>()
        var subject = "Physics"
        var book = "Physics"
        var chapter = ""
        var moduleIndex = 0
        var chapterIndex = 0

        fun add(taskTitle: String, questions: Int, lectures: Int, kind: String, est: Int = 0) {
            moduleIndex++
            val stable = "${subject}_${book}_${chapterIndex}_${moduleIndex}_${taskTitle}".sanitizeId()
            val estimated = est.takeIf { it > 0 } ?: estimateQuestionTask(questions)
            tasks += StudyTask(stable, subject, book, chapter, taskTitle, questions, lectures, estimated, kind)
        }

        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isBlank()) return@forEach

            when {
                line.equals("PHYSICS STARTS", ignoreCase = true) -> {
                    subject = "Physics"; book = "Physics"; chapter = ""; moduleIndex = 0; chapterIndex = 0
                }
                line.equals("PHYSICAL CHEMISTRY STARTS", ignoreCase = true) -> {
                    subject = "Physical Chemistry"; book = "Physical Chemistry"; chapter = ""; moduleIndex = 0; chapterIndex = 0
                }
                line.equals("ORGANIC CHEMISTRY", ignoreCase = true) -> {
                    subject = "Organic Chemistry"; book = "Organic Chemistry"; chapter = ""; moduleIndex = 0; chapterIndex = 0
                }
                line.equals("ADVANCED ILLUSTRATIONS", ignoreCase = true) -> {
                    subject = "Physics"; book = "Advanced Illustrations"; chapter = ""; moduleIndex = 0; chapterIndex = 0
                }
                line.uppercase() == line && !line.contains(':') && !line.startsWith('*') && !line.contains("STARTS") -> {
                    book = line
                    chapter = ""
                    moduleIndex = 0
                    chapterIndex = 0
                }
                line.startsWith("UNIT ") && line.contains(':') -> {
                    chapterIndex++
                    moduleIndex = 0
                    chapter = line.substringAfter(':').trim()
                }
                line.startsWith("Chapter ") && line.contains(':') -> {
                    chapterIndex++
                    moduleIndex = 0
                    chapter = line.substringAfter(':').trim()
                }
                line.startsWith("* Module") && line.contains(':') -> {
                    val title = line.substringAfter("Module").substringBefore(':').trim()
                        .replace(Regex("^\\d+\\.\\s*"), "")
                    val q = line.substringAfter(':').trim().toIntOrNull() ?: 0
                    add(title, q, 0, "Book questions")
                }
                line.contains("Exercise") && line.contains(':') -> {
                    val q = line.substringAfter(':').trim().toIntOrNull() ?: 0
                    if (q > 0) add(line.substringBefore(':').trim(), q, 0, "Cumulative exercise")
                }
                line.startsWith("Module-") && line.contains(':') -> {
                    val title = line.substringAfter(':').trim()
                    add(title, 0, 0, "Advanced illustration", 12)
                }
                Regex("^\\d+\\.").containsMatchIn(line) && !line.contains("Level") && !line.contains("level") -> {
                    chapterIndex++
                    moduleIndex = 0
                    chapter = line.substringAfter('.').trim()
                }
                Regex("(?i)^level\\s*\\d+\\s*:").containsMatchIn(line) -> {
                    val label = line.substringBefore(':').trim()
                    val q = line.substringAfter(':').trim().toIntOrNull() ?: 0
                    if (q > 0) add(label, q, 0, "Chemistry level questions")
                }
            }
        }
        return tasks
    }

    private fun loadLectures(context: Context): List<StudyTask> {
        val text = readAsset(context, "lectures_data.txt")
        val tasks = mutableListOf<StudyTask>()
        var clazz = ""
        var subject = ""
        var chapter = ""
        var index = 0

        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isBlank()) return@forEach
            when {
                line.startsWith("=== CLASS") -> clazz = line.removePrefix("===").removeSuffix("===").trim()
                line.startsWith("---") -> subject = "$clazz ${line.removePrefix("---").removeSuffix("---").trim()}"
                Regex("^\\d+\\) .+ \\(\\d+\\)$").containsMatchIn(line) -> {
                    index++
                    chapter = line.substringAfter(')').substringBeforeLast('(').trim()
                    val lectures = line.substringAfterLast('(').removeSuffix(")").trim().toIntOrNull() ?: 0
                    val title = "$chapter lectures"
                    val stable = "${subject}_${chapter}_lectures".sanitizeId()
                    tasks += StudyTask(stable, subject, "Lecture plan", chapter, title, 0, lectures, lectures * 90, "Lectures")
                }
            }
        }
        return tasks
    }

    private fun readAsset(context: Context, name: String): String =
        context.assets.open(name).bufferedReader().use { it.readText() }

    private fun estimateQuestionTask(questions: Int): Int =
        (questions * 2).coerceAtLeast(15)

    private fun String.sanitizeId(): String =
        lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
}
