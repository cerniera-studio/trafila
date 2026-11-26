package com.cerniera.trafila

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Where an application stands, in the order it travels. */
enum class Stage(val title: String) {
    APPLIED("Applied"),
    SCREENING("Screening"),
    INTERVIEW("Interview"),
    ASSESSMENT("Assessment"),
    OFFER("Offer"),
    CLOSED("Closed"),
}

/** How it came: straight to the firm, through an agency, through LinkedIn, or a job board. */
enum class Route(val title: String) {
    DIRECT("Direct"),
    AGENCY("Agency"),
    LINKEDIN("LinkedIn"),
    BOARD("Job board"),
}

/** One application. Dates are days; [lastContact] is the last time either side heard from the other. */
data class Application(
    val id: Long = 0L,
    val firm: String = "",
    val role: String = "",
    val route: Route = Route.DIRECT,
    /** The agency, the consultant, or whoever carries it. */
    val via: String = "",
    val contact: String = "",
    val stage: Stage = Stage.APPLIED,
    val lastContact: LocalDate? = null,
    /** The next thing owed, in words ("Chase Laura"), and when. */
    val next: String = "",
    val nextOn: LocalDate? = null,
    val notes: String = "",
    val created: LocalDate = LocalDate.now(),
) {
    val live get() = stage != Stage.CLOSED
    val heard get() = lastContact ?: created
    val name get() = firm.ifBlank { role.ifBlank { "New application" } }
}

/** Something owed today, and why. */
data class Owed(val app: Application, val why: String, val quiet: Boolean)

/**
 * The rules, free of Android so they run on the JVM in tests. Trafila never badges and never
 * nags: a thread gone quiet earns one gentle line in Today, nothing more.
 */
object Rules {
    /** Ten working days without a word, and a live thread has gone quiet. */
    const val QUIET = 10

    /** Working days (Monday to Friday) after [from] up to and including [to]; 0 if [to] is not later. */
    fun workingDays(from: LocalDate, to: LocalDate): Int {
        if (!to.isAfter(from)) return 0
        val days = ChronoUnit.DAYS.between(from, to).toInt()
        val weeks = days / 7
        var n = weeks * 5
        var d = from.plusDays(weeks * 7L)
        while (d.isBefore(to)) {
            d = d.plusDays(1)
            if (d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY) n++
        }
        return n
    }

    fun quiet(a: Application, today: LocalDate) =
        a.live && a.stage != Stage.OFFER && workingDays(a.heard, today) >= QUIET

    fun due(a: Application, today: LocalDate) = a.live && a.nextOn != null && !a.nextOn.isAfter(today)

    /** Today's list: what is due (oldest first), then what has gone quiet with nothing planned. */
    fun owed(all: List<Application>, today: LocalDate): List<Owed> {
        val due = all.filter { due(it, today) }.sortedBy { it.nextOn }.map {
            val late = ChronoUnit.DAYS.between(it.nextOn, today)
            Owed(it, it.next.ifBlank { "Follow up" } + if (late > 0) " (from ${ago(late)})" else "", false)
        }
        val quiet = all.filter { quiet(it, today) && !due(it, today) && (it.nextOn == null || it.nextOn.isBefore(today)) }
            .sortedBy { it.heard }
            .map { Owed(it, "Quiet ${workingDays(it.heard, today)} working days: chase or close", true) }
        return due + quiet
    }

    /** The road: live applications furthest along first, then the most recently heard from. */
    fun road(all: List<Application>): List<Application> =
        all.filter { it.live }.sortedWith(compareByDescending<Application> { it.stage.ordinal }.thenByDescending { it.heard })

    fun closed(all: List<Application>): List<Application> = all.filter { !it.live }.sortedByDescending { it.heard }

    /** "today", "yesterday", "3 days ago", "2 weeks ago". */
    fun ago(days: Long): String = when {
        days <= 0L -> "today"
        days == 1L -> "yesterday"
        days < 14L -> "$days days ago"
        else -> "${days / 7} weeks ago"
    }

    /** The day a next-step choice lands on: Today, Tomorrow, In 3 days, Next week (Monday). */
    fun nextDay(choice: Int, today: LocalDate): LocalDate? = when (choice) {
        1 -> today
        2 -> today.plusDays(1)
        3 -> today.plusDays(3)
        4 -> today.plusDays((8 - today.dayOfWeek.value).toLong())
        else -> null
    }
}

/**
 * CSV in and out, so the road can be filled from a list and taken anywhere. One header line;
 * dates as yyyy-mm-dd; stage and route by their titles (or names). Quotes as RFC 4180.
 */
object Csv {
    val HEADER = listOf("firm", "role", "route", "via", "contact", "stage", "last_contact", "next", "next_on", "notes")

    fun write(all: List<Application>): String = buildString {
        appendLine(HEADER.joinToString(","))
        for (a in all) appendLine(listOf(a.firm, a.role, a.route.title, a.via, a.contact, a.stage.title,
            a.lastContact?.toString() ?: "", a.next, a.nextOn?.toString() ?: "", a.notes).joinToString(",") { quote(it) })
    }

    private fun quote(s: String) = if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    /** Rows of fields, honouring quotes (commas and line breaks inside them). */
    fun rows(text: String): List<List<String>> {
        val out = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val f = StringBuilder()
        var q = false
        var i = 0
        val s = text.removePrefix("﻿")
        while (i < s.length) {
            val c = s[i]
            if (q) {
                if (c == '"') { if (i + 1 < s.length && s[i + 1] == '"') { f.append('"'); i++ } else q = false } else f.append(c)
            } else when (c) {
                '"' -> q = true
                ',' -> { row += f.toString(); f.clear() }
                '\r' -> {}
                '\n' -> { row += f.toString(); f.clear(); if (row.any { it.isNotBlank() }) out += row; row = mutableListOf() }
                else -> f.append(c)
            }
            i++
        }
        row += f.toString()
        if (row.any { it.isNotBlank() }) out += row
        return out
    }

    /** Applications from CSV text. Columns are found by the header's names, in any order; unknown ones are ignored. */
    fun read(text: String, today: LocalDate = LocalDate.now()): List<Application> {
        val rows = rows(text)
        if (rows.isEmpty()) return emptyList()
        val head = rows[0].map { it.trim().lowercase().replace(' ', '_') }
        fun col(r: List<String>, name: String) = head.indexOf(name).takeIf { it >= 0 }?.let { r.getOrNull(it)?.trim() } ?: ""
        fun date(s: String) = runCatching { LocalDate.parse(s) }.getOrNull()
        fun <E : Enum<E>> pick(values: Array<E>, s: String, title: (E) -> String, fallback: E) =
            values.firstOrNull { title(it).equals(s, true) || it.name.equals(s, true) } ?: fallback
        return rows.drop(1).mapNotNull { r ->
            val firm = col(r, "firm"); val role = col(r, "role")
            if (firm.isBlank() && role.isBlank()) return@mapNotNull null
            Application(
                firm = firm, role = role,
                route = pick(Route.values(), col(r, "route"), { it.title }, Route.DIRECT),
                via = col(r, "via"), contact = col(r, "contact"),
                stage = pick(Stage.values(), col(r, "stage"), { it.title }, Stage.APPLIED),
                lastContact = date(col(r, "last_contact")),
                next = col(r, "next"), nextOn = date(col(r, "next_on")),
                notes = col(r, "notes"),
                created = date(col(r, "last_contact")) ?: today,
            )
        }
    }
}
