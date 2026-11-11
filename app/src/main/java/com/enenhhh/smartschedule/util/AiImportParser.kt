package com.enenhhh.smartschedule.util

import com.enenhhh.smartschedule.models.DaySchedule
import com.enenhhh.smartschedule.models.TimePeriod
import com.enenhhh.smartschedule.models.TimePlan
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * 「AI 快速导入」的提示词与解析。
 *
 * 思路：不在 App 里接任何大模型 API。App 生成一段提示词（相当于输出规则），用户把它和课表 / 作业照片
 * 一起发给 DeepSeek、豆包等 AI 助手，再把 AI 的回复粘贴回来。这里负责：
 *  - [timetablePrompt] / [homeworkPrompt]：生成提示词，带上用户已有的课程名、作息节数、今天的日期；
 *  - [parse]：从 AI 回复里找出 JSON 并宽松地解析。AI 的输出不总是规整的，所以能容忍代码块包裹、
 *    前后的解释文字、中文引号、末尾多余的逗号、用「周一」代替数字等常见偏差。
 *
 * 只依赖 org.json 和 java.time，不碰 Context，方便单独测试。
 */
object AiImportParser {

    const val TYPE_TIMETABLE = "smartschedule.timetable"
    const val TYPE_HOMEWORK = "smartschedule.homework"
    const val MAX_PERIODS = 16
    private const val MAX_NAME = 20

    // ------------------------------------------------------------------ 结果

    data class TimetableResult(
        val name: String?,
        /** 1 = 周一 … 7 = 周日；只包含 AI 输出了的那几天 */
        val days: Map<Int, DaySchedule>,
        val periods: List<TimePeriod>,
        val morningStart: String?,
        val morningEnd: String?,
        val notes: List<String>
    ) {
        val maxPeriods: Int get() = days.values.maxOfOrNull { d -> maxOf(d.courses.size, d.evenCourses?.size ?: 0) } ?: 0
        val courseCount: Int get() = days.values.sumOf { d -> d.courses.count { it.isNotBlank() } }
        val alternatingDays: Int get() = days.values.count { it.alternates }
        val isEmpty: Boolean get() = days.values.all { it.isEmpty() }
    }

    data class HomeworkItem(
        val subject: String,
        val title: String,
        val deadline: LocalDate,
        val note: String,
        /** AI 没给截止日期、由 App 补成下一个上学日 */
        val deadlineGuessed: Boolean
    )

    data class HomeworkResult(val items: List<HomeworkItem>, val notes: List<String>)

    sealed class Parsed {
        data class Timetable(val result: TimetableResult) : Parsed()
        data class Homework(val result: HomeworkResult) : Parsed()
    }

    class ParseException(message: String) : Exception(message)

    // ------------------------------------------------------------------ 提示词

    /**
     * @param knownCourses 用户已经在用的课程名，让 AI 尽量沿用同样的写法
     * @param periodCount  当前时间方案每天几节（0 表示还没有时间方案）
     */
    fun timetablePrompt(knownCourses: List<String>, periodCount: Int, firstStart: String?): String = buildString {
        appendLine("请识别我发给你的课表图片（照片、截图或表格都可以），按下面的规则整理，供「智能课表」App 导入。")
        appendLine()
        appendLine("【输出】只输出一个 ```json 代码块，代码块前后不要写任何其它文字。格式如下：")
        appendLine("```json")
        appendLine("{")
        appendLine("  \"type\": \"$TYPE_TIMETABLE\",")
        appendLine("  \"name\": \"高一(3)班课表\",")
        appendLine("  \"periods\": [{\"start\": \"08:00\", \"end\": \"08:40\"}, {\"start\": \"08:50\", \"end\": \"09:30\"}],")
        appendLine("  \"morning\": {\"start\": \"07:30\", \"end\": \"08:00\"},")
        appendLine("  \"days\": [")
        appendLine("    {\"day\": 1, \"morning\": \"语文\", \"courses\": [\"数学\", \"语文\", \"\", \"英语\"]},")
        appendLine("    {\"day\": 3, \"courses\": [\"物理\", \"化学\", \"体育\"], \"even\": [\"化学\", \"物理\", \"体育\"]}")
        appendLine("  ],")
        appendLine("  \"notes\": []")
        appendLine("}")
        appendLine("```")
        appendLine()
        appendLine("【规则】")
        appendLine("1. day：1 = 周一，2 = 周二 … 7 = 周日。课表上有哪几天就写哪几天。")
        appendLine("2. courses：按第 1、2、3… 节的顺序写，第 n 个就是第 n 节。某节没课写 \"\"，不能跳过，保证节次对得上。连堂课每一节都要写。")
        appendLine("3. 晨读 / 早读 / 早自习写在这一天的 morning 里，不算进 courses。午休、课间、大课间、眼保健操不算一节；晚自习按顺序算在最后几节。")
        appendLine("4. 单双周：某一天单周和双周的课不一样时，courses 写单周的课，even 写双周的课，两个列表一样长；单双周相同的天不要写 even。格子里写着「单周物理 / 双周化学」「物理(单) 化学(双)」之类的，按这条拆开。")
        if (knownCourses.isNotEmpty()) {
            appendLine("5. 课程名写全称。我已经在用这些名字：${knownCourses.take(24).joinToString("、")}。意思相同的课请用我的写法。")
        } else {
            appendLine("5. 课程名写全称，例如「数学」「道德与法治」，不要只写一个字。")
        }
        appendLine("6. periods 和 morning 是上下课时间（24 小时制 HH:mm），按节次顺序写。图片上看不到时间，或者只有开始时间没有结束时间，就不要写这两项。")
        if (periodCount > 0) {
            appendLine("   我现在的作息是每天 $periodCount 节${if (firstStart != null) "，第 1 节 $firstStart 开始" else ""}，可以参考。")
        }
        appendLine("7. name 是课表名称，看不出来就写「我的课表」。")
        appendLine("8. 看不清的字按最可能的写，并在 notes 里用一句话说明；不要编造图片里没有的课。")
    }

    /** @param today 今天；用来让 AI 把「明天」「周五前」换算成具体日期 */
    fun homeworkPrompt(knownCourses: List<String>, today: LocalDate, defaultDeadline: LocalDate): String = buildString {
        appendLine("请识别我发给你的作业内容（黑板照片、作业群截图、聊天记录、手写清单都可以），按下面的规则整理，供「智能课表」App 导入。")
        appendLine()
        appendLine("今天是 ${DateUtils.format(today)}（${DateUtils.dayName(DateUtils.dayIndex(today))}）。")
        appendLine()
        appendLine("【输出】只输出一个 ```json 代码块，代码块前后不要写任何其它文字。格式如下：")
        appendLine("```json")
        appendLine("{")
        appendLine("  \"type\": \"$TYPE_HOMEWORK\",")
        appendLine("  \"items\": [")
        appendLine("    {\"subject\": \"数学\", \"title\": \"练习册 P12–P15\", \"deadline\": \"${DateUtils.format(defaultDeadline)}\", \"note\": \"\"},")
        appendLine("    {\"subject\": \"语文\", \"title\": \"背诵《岳阳楼记》第 2 段\", \"deadline\": \"${DateUtils.format(defaultDeadline)}\", \"note\": \"家长签字\"}")
        appendLine("  ],")
        appendLine("  \"notes\": []")
        appendLine("}")
        appendLine("```")
        appendLine()
        appendLine("【规则】")
        appendLine("1. 每项作业单独一条。同一科目有几项不同的作业，就写几条。")
        if (knownCourses.isNotEmpty()) {
            appendLine("2. subject 是科目，尽量用这些写法：${knownCourses.take(24).joinToString("、")}。看不出科目就写 \"\"。")
        } else {
            appendLine("2. subject 是科目全称，例如「数学」「英语」。看不出科目就写 \"\"。")
        }
        appendLine("3. title 是作业内容：简洁、完整，保留页码、题号、篇目，不要重复写科目名。")
        appendLine("4. deadline 是截止日期，格式 yyyy-MM-dd。「明天」「周五前」「下周一交」请按今天的日期换算。没写截止时间的，填 ${DateUtils.format(defaultDeadline)}（下一个上学日）。")
        appendLine("5. note 写老师的附加要求，比如「家长签字」「拍照上传」；没有就写 \"\"。")
        appendLine("6. 看不清的字按最可能的写，并在 notes 里用一句话说明；不要编造原文里没有的作业。")
    }

    /** 下一个上学日：明天；明天是周六、周日（且周末不上课）时顺延到下周一 */
    fun nextSchoolDay(today: LocalDate, weekendClasses: Boolean = false): LocalDate {
        val t = today.plusDays(1)
        if (weekendClasses) return t
        return when (t.dayOfWeek) {
            DayOfWeek.SATURDAY -> t.plusDays(2)
            DayOfWeek.SUNDAY -> t.plusDays(1)
            else -> t
        }
    }

    // ------------------------------------------------------------------ 解析入口

    /**
     * 解析 AI 的回复。
     * @param knownCourses 用户已有的课程名（用于把「道法」这类简称对回全称）
     * @param shortNames   用户自定义的「全称 → 简称」
     */
    fun parse(
        text: String,
        today: LocalDate,
        knownCourses: List<String> = emptyList(),
        shortNames: Map<String, String> = emptyMap(),
        defaultDeadline: LocalDate = nextSchoolDay(today)
    ): Parsed {
        if (text.isBlank()) throw ParseException("还没有粘贴内容")
        val json = extractJson(text)
            ?: throw ParseException("没找到 AI 输出的内容。请确认复制的是 AI 的整段回复（包含 { } 的那段代码）。")
        val names = NameMatcher(knownCourses, shortNames)
        return when (detectKind(json)) {
            Kind.Timetable -> Parsed.Timetable(parseTimetable(json as JSONObject, names))
            Kind.Homework -> Parsed.Homework(parseHomework(json, today, names, defaultDeadline))
            null -> throw ParseException("内容格式不对：既不像课表也不像作业。请把提示词重新发给 AI，让它按格式输出。")
        }
    }

    private enum class Kind { Timetable, Homework }

    private fun detectKind(json: Any): Kind? {
        if (json is JSONArray) return if (looksLikeHomeworkArray(json)) Kind.Homework else null
        val o = json as JSONObject
        val type = o.optString("type", "")
        return when {
            type.contains("timetable", true) || type.contains("课表") -> Kind.Timetable
            type.contains("homework", true) || type.contains("作业") -> Kind.Homework
            o.has("days") || o.has("课表") -> Kind.Timetable
            o.has("items") || o.has("homework") || o.has("作业") -> Kind.Homework
            else -> null
        }
    }

    private fun looksLikeHomeworkArray(a: JSONArray): Boolean =
        a.length() > 0 && (0 until a.length()).all { i -> a.opt(i) is JSONObject } &&
            a.optJSONObject(0)!!.let { it.has("title") || it.has("content") || it.has("内容") || it.has("subject") }

    // ------------------------------------------------------------------ 找 JSON

    /** 依次尝试：代码块里的内容 → 整段文字；每段里找第一个能解析的 { … } 或 [ … ] */
    fun extractJson(text: String): Any? {
        val candidates = ArrayList<String>()
        Regex("```[a-zA-Z]*\\s*\\n?([\\s\\S]*?)```").findAll(text).forEach { candidates.add(it.groupValues[1]) }
        candidates.add(text)
        for (c in candidates) {
            for (start in c.indices) {
                val ch = c[start]
                if (ch != '{' && ch != '[') continue
                val end = matchingClose(c, start) ?: continue
                val raw = c.substring(start, end + 1)
                tryParse(raw)?.let { return it }
                val basic = cleanSyntax(raw)
                tryParse(basic)?.let { return it }
                tryParse(basic.replace('“', '"').replace('”', '"'))?.let { return it }
            }
        }
        return null
    }

    private fun tryParse(s: String): Any? = runCatching {
        if (s.trimStart().startsWith("[")) JSONArray(s) else JSONObject(s)
    }.getOrNull()?.takeIf { it !is JSONObject || (it.length() > 0 && it.keys().asSequence().none { k -> k.contains('“') || k.contains('”') }) }
    // org.json 允许不带引号的键，「“type”」会被当成一个带中文引号的键名读进来；这种情况算失败，交给后面换引号再试

    /** 找到与 [start] 处括号配对的位置（跳过字符串里的括号）；中文引号也当作字符串边界 */
    private fun matchingClose(s: String, start: Int): Int? {
        var depth = 0
        var inStr = false
        var quote = '"'
        var i = start
        while (i < s.length) {
            val c = s[i]
            if (inStr) {
                if (c == '\\') { i += 2; continue }
                if (c == quote || (quote == '“' && c == '”')) inStr = false
            } else when (c) {
                '"', '“' -> { inStr = true; quote = c }
                '{', '[' -> depth++
                '}', ']' -> { depth--; if (depth == 0) return i }
            }
            i++
        }
        return null
    }

    /** 修掉 AI 常见的小毛病：照抄了示例里的注释、末尾多余的逗号。中文引号放到最后一步再换，免得误伤正文 */
    private fun cleanSyntax(s: String): String = s
        .lines().joinToString("\n") { line ->
            line.replace(Regex("^(\\s*)//.*$"), "$1")
                .replace(Regex("([\",\\d\\]}\\[{]|true|false|null)\\s*//[^\"]*$"), "$1")
        }
        .replace(Regex(",\\s*([}\\]])"), "$1")

    // ------------------------------------------------------------------ 课表

    private fun parseTimetable(o: JSONObject, names: NameMatcher): TimetableResult {
        val daysAny = o.opt("days") ?: o.opt("课表")
        val raw = LinkedHashMap<Int, DaySchedule>()
        when (daysAny) {
            is JSONArray -> for (i in 0 until daysAny.length()) {
                val d = daysAny.optJSONObject(i) ?: continue
                val idx = dayIndexOf(d.opt("day") ?: d.opt("weekday") ?: d.opt("星期")) ?: continue
                raw[idx] = parseDay(d, names)
            }
            is JSONObject -> daysAny.keys().forEach { k ->
                val idx = dayIndexOf(k) ?: return@forEach
                when (val v = daysAny.opt(k)) {
                    is JSONObject -> raw[idx] = parseDay(v, names)
                    is JSONArray -> raw[idx] = DaySchedule(courses = courseList(v, names))
                }
            }
            else -> throw ParseException("课表里没有 days（每天的课）。请把提示词重新发给 AI。")
        }
        if (raw.isEmpty()) throw ParseException("没有识别到任何一天的课。请确认照片清楚，或把提示词重新发给 AI。")

        val periods = parsePeriods(o.opt("periods") ?: o.opt("times") ?: o.opt("作息"))
        val morning = o.optJSONObject("morning")
        val ms = morning?.let { normTime(it.optString("start", "")) }
        val me = morning?.let { normTime(it.optString("end", "")) }
        val notes = stringList(o.opt("notes")).filter { it.isNotBlank() }.take(10)
        val name = o.optString("name", "").trim().take(30).takeIf { it.isNotBlank() && it != "null" }
        return TimetableResult(
            name = name,
            days = raw.toSortedMap(),
            periods = periods,
            morningStart = if (ms != null && me != null && TimePlan.timeToMinutes(ms) < TimePlan.timeToMinutes(me)) ms else null,
            morningEnd = if (ms != null && me != null && TimePlan.timeToMinutes(ms) < TimePlan.timeToMinutes(me)) me else null,
            notes = notes
        )
    }

    private fun parseDay(d: JSONObject, names: NameMatcher): DaySchedule {
        val coursesAny = d.opt("courses") ?: d.opt("odd") ?: d.opt("课程")
        var odd = (coursesAny as? JSONArray)?.let { courseCells(it) } ?: emptyList()
        var even = (d.opt("even") as? JSONArray)?.let { courseCells(it) }
        // 格子里写着「物理(单)/化学(双)」时拆开
        val split = odd.map { splitOddEven(it) }
        if (split.any { it != null }) {
            val e = ArrayList(even ?: odd)
            while (e.size < odd.size) e.add("")
            odd = odd.mapIndexed { i, c -> split[i]?.first ?: c }
            split.forEachIndexed { i, p -> if (p != null) e[i] = p.second }
            even = e
        }
        odd = odd.map { names.match(it) }.take(MAX_PERIODS)
        even = even?.map { names.match(it) }?.take(MAX_PERIODS)
        // 补齐长度；单双周其实一样时去掉 even
        if (even != null) {
            val n = maxOf(odd.size, even.size)
            odd = odd + List(n - odd.size) { "" }
            even = even + List(n - even.size) { "" }
            if (even == odd) even = null
        }
        if (even == null) odd = odd.dropLastWhile { it.isBlank() }
        val mr = names.match(cell(d.opt("morning") ?: d.opt("morningRead") ?: d.opt("晨读")))
        val emr = (d.opt("evenMorning") ?: d.opt("evenMorningRead"))?.let { names.match(cell(it)) }
        return DaySchedule(courses = odd, evenCourses = even, morningRead = mr, evenMorningRead = emr?.takeIf { it != mr })
    }

    private fun courseList(a: JSONArray, names: NameMatcher): List<String> =
        courseCells(a).map { names.match(it) }.take(MAX_PERIODS).dropLastWhile { it.isBlank() }

    private fun courseCells(a: JSONArray): List<String> = List(a.length()) { i -> cell(a.opt(i)) }

    private val EMPTY_WORDS = setOf("无", "空", "-", "—", "–", "/", "null", "none", "n/a", "没课", "无课", "休息")

    /** 单元格 → 课程名；空、占位符都变成 "" */
    private fun cell(v: Any?): String {
        val s = when (v) {
            null, JSONObject.NULL -> ""
            is JSONObject -> v.optString("name", v.optString("course", ""))
            else -> v.toString()
        }.trim().trim('"', '\'', '「', '」')
        if (s.lowercase() in EMPTY_WORDS) return ""
        return s.replace(Regex("\\s+"), " ").take(MAX_NAME)
    }

    private val ODD_EVEN_PATTERNS = listOf(
        Regex("^(.+?)\\s*[（(]单周?[)）]\\s*[/／|、,，;；\\s]*\\s*(.+?)\\s*[（(]双周?[)）]$"),
        Regex("^单周?[:：]?\\s*(.+?)\\s*[/／|、,，;；\\s]+\\s*双周?[:：]?\\s*(.+)$"),
    )
    private val EVEN_ODD_PATTERNS = listOf(
        Regex("^(.+?)\\s*[（(]双周?[)）]\\s*[/／|、,，;；\\s]*\\s*(.+?)\\s*[（(]单周?[)）]$"),
        Regex("^双周?[:：]?\\s*(.+?)\\s*[/／|、,，;；\\s]+\\s*单周?[:：]?\\s*(.+)$"),
    )

    /** 「物理(单)/化学(双)」「单周物理 双周化学」→ (物理, 化学)；不是这种写法返回 null */
    fun splitOddEven(s: String): Pair<String, String>? {
        if (!s.contains('单') || !s.contains('双')) return null
        ODD_EVEN_PATTERNS.forEach { r -> r.find(s)?.let { return it.groupValues[1].trim() to it.groupValues[2].trim() } }
        EVEN_ODD_PATTERNS.forEach { r -> r.find(s)?.let { return it.groupValues[2].trim() to it.groupValues[1].trim() } }
        return null
    }

    private fun parsePeriods(any: Any?): List<TimePeriod> {
        val a = any as? JSONArray ?: return emptyList()
        val out = ArrayList<TimePeriod>()
        for (i in 0 until a.length()) {
            val (s, e) = when (val v = a.opt(i)) {
                is JSONObject -> v.optString("start", "") to v.optString("end", "")
                is String -> v.split(Regex("\\s*[-–—~～至到]\\s*")).let { (it.getOrNull(0) ?: "") to (it.getOrNull(1) ?: "") }
                else -> "" to ""
            }
            val ns = normTime(s) ?: return emptyList()
            val ne = normTime(e) ?: return emptyList()
            out.add(TimePeriod(ns, ne))
        }
        // 必须是递增、每节开始早于结束；否则宁可不用（让用户沿用现有作息）
        val ok = out.isNotEmpty() && out.size <= MAX_PERIODS && out.all { it.startMinutes < it.endMinutes } &&
            out.zipWithNext().all { (p, n) -> p.endMinutes <= n.startMinutes }
        return if (ok) out else emptyList()
    }

    /** "8:00" / "08：00" / "8.00" → "08:00"；不合法返回 null */
    fun normTime(s: String): String? {
        val m = Regex("^\\s*(\\d{1,2})\\s*[:：.点]\\s*(\\d{2})\\s*$").find(s) ?: return null
        val h = m.groupValues[1].toInt(); val mm = m.groupValues[2].toInt()
        if (h !in 0..23 || mm !in 0..59) return null
        return "%02d:%02d".format(h, mm)
    }

    private val DAY_WORDS = mapOf("一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6, "日" to 7, "天" to 7, "七" to 7)
    private val DAY_EN = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")

    /** 1..7、"1"、"周一"、"星期一"、"礼拜天"、"Mon"、"Monday" → 1..7 */
    fun dayIndexOf(v: Any?): Int? {
        when (v) {
            is Number -> return v.toInt().takeIf { it in 1..7 }
            null -> return null
        }
        val s = v.toString().trim().lowercase()
        s.toIntOrNull()?.let { return it.takeIf { d -> d in 1..7 } }
        DAY_EN.indexOfFirst { s.startsWith(it) }.takeIf { it >= 0 }?.let { return it + 1 }
        val t = s.removePrefix("星期").removePrefix("礼拜").removePrefix("周")
        return DAY_WORDS[t.take(1)]
    }

    // ------------------------------------------------------------------ 作业

    private fun parseHomework(json: Any, today: LocalDate, names: NameMatcher, defaultDeadline: LocalDate): HomeworkResult {
        val arr: JSONArray
        var notes = emptyList<String>()
        if (json is JSONArray) arr = json else {
            val o = json as JSONObject
            arr = (o.opt("items") ?: o.opt("homework") ?: o.opt("作业")) as? JSONArray
                ?: throw ParseException("作业里没有 items（作业列表）。请把提示词重新发给 AI。")
            notes = stringList(o.opt("notes")).filter { it.isNotBlank() }.take(10)
        }
        val items = ArrayList<HomeworkItem>()
        for (i in 0 until arr.length()) {
            val entry = arr.opt(i)
            if (entry is String) {                    // AI 偶尔会直接给一串字符串
                if (entry.isNotBlank()) items.add(HomeworkItem("", entry.trim().take(100), defaultDeadline, "", true))
                continue
            }
            val o = entry as? JSONObject ?: continue
            val subject = names.match(cell(o.opt("subject") ?: o.opt("科目")))
            var title = (o.opt("title") ?: o.opt("content") ?: o.opt("内容") ?: o.opt("task"))?.toString()?.trim().orEmpty()
            val note = (o.opt("note") ?: o.opt("description") ?: o.opt("备注"))?.toString()?.trim()?.takeIf { it != "null" }.orEmpty()
            if (subject.isNotBlank()) title = title.removePrefix(subject).trimStart(':', '：', ' ', '-', '—', '·')
            if (title.isBlank() || title == "null") title = note
            if (title.isBlank()) continue
            val dl = parseDate((o.opt("deadline") ?: o.opt("due") ?: o.opt("截止") ?: o.opt("date"))?.toString(), today)
            items.add(HomeworkItem(subject, title.take(100), dl ?: defaultDeadline, if (note == title) "" else note.take(200), dl == null))
        }
        if (items.isEmpty()) throw ParseException("没有识别到作业。请确认照片清楚，或把提示词重新发给 AI。")
        return HomeworkResult(items.take(60), notes)
    }

    /** 2026-11-10、2026/11/10、11-10、11月10日、明天、周五、下周一 → 日期 */
    fun parseDate(s: String?, today: LocalDate): LocalDate? {
        val t = s?.trim().orEmpty()
        if (t.isBlank() || t == "null") return null
        Regex("^(\\d{4})[-/.年](\\d{1,2})[-/.月](\\d{1,2})日?$").find(t)?.let { m ->
            return runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull()
        }
        Regex("^(\\d{1,2})[-/.月](\\d{1,2})日?$").find(t)?.let { m ->
            val d = runCatching { LocalDate.of(today.year, m.groupValues[1].toInt(), m.groupValues[2].toInt()) }.getOrNull() ?: return null
            return if (d.isBefore(today.minusMonths(2))) d.plusYears(1) else d   // 12 月底写「1-5」指明年
        }
        when (t) {
            "今天", "今日" -> return today
            "明天", "明日" -> return today.plusDays(1)
            "后天" -> return today.plusDays(2)
        }
        val next = t.startsWith("下周") || t.startsWith("下星期")
        val day = dayIndexOf(t.removePrefix("下"))
        if (day != null && (t.contains("周") || t.contains("星期") || t.contains("礼拜"))) {
            val target = DayOfWeek.of(day)
            return if (next) DateUtils.mondayOf(today).plusWeeks(1).with(TemporalAdjusters.nextOrSame(target))
            else today.with(TemporalAdjusters.nextOrSame(target))
        }
        return null
    }

    private fun stringList(v: Any?): List<String> = when (v) {
        is JSONArray -> List(v.length()) { v.optString(it, "") }
        is String -> listOf(v)
        else -> emptyList()
    }

    // ------------------------------------------------------------------ 课程名对齐

    /**
     * 把 AI 写的课程名对到用户已有的写法：
     *  - 和已有名字完全一样：保留；
     *  - 是用户自定义的简称（例如「道法」→「道德与法治」）：换成全称；
     *  - 两个字、且正好是某个已有课程名的前两个字（默认简称规则），并且只对得上一个：换成那个全称。
     */
    private class NameMatcher(known: List<String>, shortNames: Map<String, String>) {
        private val known = known.filter { it.isNotBlank() }.toSet()
        private val byShort = shortNames.entries.groupBy({ it.value }, { it.key }).filterValues { it.size == 1 }.mapValues { it.value.first() }
        private val byPrefix = this.known.filter { it.length > 2 }.groupBy { it.take(2) }.filterValues { it.size == 1 }.mapValues { it.value.first() }

        fun match(name: String): String {
            if (name.isBlank() || name in known) return name
            byShort[name]?.let { return it }
            if (name.length == 2) byPrefix[name]?.let { return it }
            return name
        }
    }
}
