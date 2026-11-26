package com.cerniera.trafila

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.cerniera.kit.Card
import com.cerniera.kit.Chips
import com.cerniera.kit.Crash
import com.cerniera.kit.Fit
import com.cerniera.kit.Fold
import com.cerniera.kit.Kit
import com.cerniera.kit.Row
import com.cerniera.kit.Screen
import com.cerniera.kit.Sheet
import com.cerniera.kit.doctrineCard
import com.cerniera.kit.endValue
import com.cerniera.kit.eyebrow
import com.cerniera.kit.field
import com.cerniera.kit.primary
import com.cerniera.kit.px
import com.cerniera.kit.text
import com.cerniera.kit.withAlpha
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Trafila's one screen. On the cover, the road alone, and an application's file rises as a sheet.
 * On the main screen (or any window 600 dp wide) the road is the left page and the chosen file the
 * right. Every change is kept the moment it is made, so a turn, a fold or a closed sheet loses nothing.
 */
class MainActivity : Activity() {

    private val store by lazy { Store.of(this) }
    private val main = Handler(Looper.getMainLooper())
    private val day = DateTimeFormatter.ofPattern("EEE d MMM")

    /** The file shown (right page, or the open sheet); 0 for none. */
    private var selected = 0L
    private var sheet: Sheet? = null
    private var reopenSheet = false
    private var twoPanes = false
    private var roadHolder: FrameLayout? = null
    private var note: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Kit.follow(this)
        if (!crashKept) { Crash.install(applicationContext, "Trafila"); crashKept = true }
        if (Crash.show(this, "Trafila")) return
        selected = savedInstanceState?.getLong("selected") ?: 0L
        reopenSheet = savedInstanceState?.getBoolean("sheet") ?: false
        if (savedInstanceState == null) handle(intent)
        build()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
        build()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong("selected", selected)
        outState.putBoolean("sheet", sheet != null)
    }

    // ---- Arriving: a shared link becomes a new application; a shared or opened CSV is imported

    private fun handle(i: Intent?) {
        if (i == null) return
        val stream = if (android.os.Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else @Suppress("DEPRECATION") i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        when {
            i.action == Intent.ACTION_VIEW && i.data != null -> importFrom(i.data!!)
            i.action == Intent.ACTION_SEND && stream != null -> importFrom(stream)
            i.action == Intent.ACTION_SEND -> {
                val text = i.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
                if (text.isEmpty()) return
                if (text.lines().firstOrNull()?.lowercase()?.contains("firm") == true && text.contains(',')) { importText(text); return }
                val subject = i.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().orEmpty()
                val today = LocalDate.now()
                selected = store.add(Application(role = subject, notes = text, route = guessRoute(text), lastContact = today, created = today))
                reopenSheet = true
            }
        }
        setIntent(Intent(this, MainActivity::class.java))
    }

    private fun guessRoute(s: String) = when {
        s.contains("linkedin.com", true) -> Route.LINKEDIN
        s.contains("indeed.", true) || s.contains("cv-library", true) || s.contains("efinancialcareers", true) || s.contains("cwjobs", true) -> Route.BOARD
        else -> Route.DIRECT
    }

    private fun importFrom(uri: Uri) {
        val text = runCatching { contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
        if (text == null) { note = "That file couldn't be read."; return }
        importText(text)
    }

    private fun importText(text: String) {
        val list = Csv.read(text)
        val n = store.import(list)
        note = when {
            list.isEmpty() -> "No applications found in that file. It needs a header line with firm and role."
            n == 0 -> "Every application in that file is already here."
            else -> "Added $n of ${list.size}."
        }
    }

    // ---- Layout

    private fun build() {
        sheet = null
        twoPanes = Fit.twoPanes(this)
        val holder = FrameLayout(this)
        roadHolder = holder
        holder.addView(road().root)
        if (twoPanes) {
            val all = store.all()
            if (selected == 0L || all.none { it.id == selected }) selected = (Rules.owed(all, LocalDate.now()).firstOrNull()?.app ?: Rules.road(all).firstOrNull())?.id ?: 0L
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(Kit.GROUND) }
            row.addView(holder, LinearLayout.LayoutParams(0, -1, 1f))
            row.addView(View(this).apply { setBackgroundColor(Kit.HAIRLINE) }, LinearLayout.LayoutParams(px(1), -1))
            row.addView(filePage(), LinearLayout.LayoutParams(0, -1, 1.1f))
            setContentView(row)
        } else {
            setContentView(holder)
            if (reopenSheet && selected != 0L) holder.post { openSheet(selected) }
        }
        reopenSheet = false
        window.decorView.setBackgroundColor(Kit.GROUND)
    }

    /** Redraws the road alone, keeping its place, after a change in the file beside it. */
    private val refreshRoad = Runnable {
        val h = roadHolder ?: return@Runnable
        val old = h.getChildAt(0) as? android.widget.ScrollView
        val y = old?.scrollY ?: 0
        val s = road()
        h.removeAllViews()
        h.addView(s.root)
        s.root.post { s.root.scrollTo(0, y) }
    }

    private fun road(): Screen {
        val s = Screen(this, "Trafila", "Every application, one road")
        val all = store.all()
        val today = LocalDate.now()
        note?.let { s.add(text(it, 14f, Kit.MUTED).apply { setPadding(px(4), px(8), 0, 0) }); note = null }

        // Today: what is owed, then threads gone quiet. Never a badge.
        s.section("Today")
        val owed = Rules.owed(all, today)
        s.add(Card(this).apply {
            if (owed.isEmpty()) row(Row(this@MainActivity, "Nothing owed today", if (all.isEmpty()) "Add an application, share a job link here, or import a list." else "Every live thread has been heard from in the last ten working days."))
            owed.forEach { o ->
                row(Row(this@MainActivity, o.app.name, o.why, endValue(o.app.stage.title, if (o.quiet) Kit.MUTED else Kit.CHAMPAGNE)) { select(o.app.id) })
            }
        }, px(10))

        // The road: furthest along first
        val road = Rules.road(all)
        s.add(eyebrow("On the road · ${road.size}").apply { setPadding(px(4), px(30), 0, px(10)) })
        if (road.isNotEmpty()) s.add(Card(this).apply {
            road.forEach { a -> row(appRow(a, today)) }
        })
        s.add(primary("Add an application") {
            val t = LocalDate.now()
            select(store.add(Application(lastContact = t, created = t)))
        }, px(16))

        // Closed, folded away
        val closed = Rules.closed(all)
        if (closed.isNotEmpty()) s.add(Fold(this, "Closed · ${closed.size}", "Unsuccessful or withdrawn, newest first").apply {
            body.addView(Card(this@MainActivity).apply { closed.forEach { a -> row(appRow(a, today)) } })
        }, px(30))

        s.add(Fold(this, "Import and export", "A list in, or the whole road out, as CSV").apply {
            body.addView(Card(this@MainActivity).apply {
                row(Row(this@MainActivity, "Import", "A CSV with a header line: firm, role, route, via, contact, stage, last_contact, next, next_on, notes. Ones already here are skipped.", endValue("Choose", Kit.CHAMPAGNE)) {
                    runCatching { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                        .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/csv")), OPEN) }
                })
                row(Row(this@MainActivity, "Export", "Every application, live and closed, saved where you choose.", endValue("Save", Kit.CHAMPAGNE)) {
                    runCatching { startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/csv")
                        .putExtra(Intent.EXTRA_TITLE, "trafila-${LocalDate.now()}.csv"), SAVE) }
                })
            })
        }, if (closed.isEmpty()) px(30) else px(12))
        s.add(doctrineCard(DOCTRINE), px(12))
        return s
    }

    private fun appRow(a: Application, today: LocalDate): View {
        val heard = Rules.ago(ChronoUnit.DAYS.between(a.heard, today))
        val bits = listOfNotNull(
            a.role.takeIf { it.isNotBlank() },
            if (a.route == Route.AGENCY && a.via.isNotBlank()) "via ${a.via}" else a.route.title,
            "heard $heard",
            a.nextOn?.takeIf { a.live }?.let { "next ${it.format(day)}" },
        )
        return Row(this, a.name, bits.joinToString(" · "), Dots(this, a.stage)) { select(a.id) }.apply {
            // A thread gone quiet drifts to the verge
            if (Rules.quiet(a, today)) alpha = 0.55f
            if (twoPanes && a.id == selected) setBackgroundColor(withAlpha(0xD9BB86, 0.08f))
        }
    }

    private fun select(id: Long) {
        if (id != selected) tidy(selected)
        selected = id
        if (twoPanes) build() else openSheet(id)
    }

    /** A new application left empty is removed rather than kept as a blank line. */
    private fun tidy(id: Long) {
        if (id == 0L) return
        val a = store.get(id) ?: return
        if (a.firm.isBlank() && a.role.isBlank() && a.notes.isBlank() && a.next.isBlank()) {
            store.delete(id)
            if (selected == id) selected = 0L
        }
    }

    private fun openSheet(id: Long) {
        val a = store.get(id) ?: return
        sheet?.dismiss()
        sheet = Sheet.show(this, a.firm.ifBlank { "Application" }, onClose = {
            sheet = null
            tidy(id)
            if (!twoPanes) { selected = 0L; refreshRoad.run() }
        }, action = "Done" to { s: Sheet -> s.dismiss() }) { sh -> sh.body.addView(file(a)) }
    }

    /** The right page: the chosen application's file, or a word when there is none. */
    private fun filePage(): View {
        val s = Screen(this, "File", "The chosen application")
        // A second Screen in the window: only the road's scroll position is kept by id
        s.root.id = View.NO_ID
        val a = store.get(selected)
        if (a == null) s.add(text("Choose an application on the road, or add one.", 15f, Kit.MUTED).apply { setPadding(px(4), px(12), 0, 0) })
        else s.add(file(a), px(16))
        return s.root
    }

    // ---- The file: every field kept as it changes

    private fun file(start: Application): View {
        var a = start
        val today = LocalDate.now()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun keep(next: Application) {
            a = next
            store.save(a)
            if (twoPanes) { main.removeCallbacks(refreshRoad); main.postDelayed(refreshRoad, 700) }
        }
        fun label(s: String, top: Int = 18) = col.addView(text(s.uppercase(), 11f, Kit.MUTED, 500).apply { letterSpacing = 0.18f },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(top); bottomMargin = px(8) })
        fun input(hint: String, value: String, multi: Boolean = false, set: (String) -> Application): EditText =
            field(hint, value).apply {
                if (multi) {
                    isSingleLine = false; minLines = 3; gravity = Gravity.TOP
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                }
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                    override fun afterTextChanged(s: Editable?) { keep(set(s?.toString().orEmpty())) }
                })
                col.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(10) })
            }

        label("The role", 0)
        input("Firm", a.firm) { a.copy(firm = it) }
        input("Role", a.role) { a.copy(role = it) }

        label("Where it stands")
        col.addView(Chips(this, Stage.values().map { it.title }, a.stage.ordinal) { i ->
            // Moving it on counts as hearing from them
            keep(a.copy(stage = Stage.values()[i], lastContact = today))
        })

        label("How it came")
        col.addView(Chips(this, Route.values().map { it.title }, a.route.ordinal) { i -> keep(a.copy(route = Route.values()[i])) })
        input("Agency or consultant", a.via) { a.copy(via = it) }
        input("Contact: name, email or phone", a.contact) { a.copy(contact = it) }

        label("Last heard")
        val heardRow = Row(this, "Heard from them", Rules.ago(ChronoUnit.DAYS.between(a.heard, today)) + " · " + a.heard.format(day),
            endValue("Today", Kit.CHAMPAGNE))
        heardRow.setOnClickListener {
            keep(a.copy(lastContact = LocalDate.now()))
            heardRow.setSub("today · " + LocalDate.now().format(day))
        }
        col.addView(Card(this).apply { row(heardRow) })

        label("Next")
        input("What you owe them, or they owe you", a.next) { a.copy(next = it) }
        val when_ = text(a.nextOn?.let { "On ${it.format(day)}" } ?: "No date", 13.5f, Kit.MUTED).apply { setPadding(px(4), px(10), 0, 0) }
        col.addView(Chips(this, listOf("No date", "Today", "Tomorrow", "In 3 days", "Next week"), -1) { i ->
            val d = Rules.nextDay(i, LocalDate.now())
            keep(a.copy(nextOn = d))
            when_.text = d?.let { "On ${it.format(day)}" } ?: "No date"
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(12) })
        col.addView(when_)

        label("Notes")
        input("Links, salary or rate, what was said", a.notes, multi = true) { a.copy(notes = it) }

        col.addView(text("Delete this application", 14f, Kit.ROSSO, 500).apply {
            setPadding(px(4), px(26), 0, px(8))
            setOnClickListener {
                store.delete(a.id)
                selected = 0L
                val sh = sheet
                if (sh != null) sh.dismiss() else build()
            }
        })
        return col
    }

    // ---- Import and export results

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        when (requestCode) {
            OPEN -> { importFrom(uri); build() }
            SAVE -> {
                val ok = runCatching { contentResolver.openOutputStream(uri, "wt")?.use { it.write(Csv.write(store.all()).toByteArray()) } }.isSuccess
                note = if (ok) "Saved every application as CSV." else "That file couldn't be written."
                build()
            }
        }
    }

    companion object {
        private var crashKept = false
        private const val OPEN = 1
        private const val SAVE = 2

        private val DOCTRINE = listOf(
            "Monolithic membrane" to "The road is one volume: an application that moves on draws forward and the rest close up behind it.",
            "Concentrato" to "Fling the road and the lines under each firm fall away; slow down and the role, route and next step return.",
            "Giulietta" to "Contacts, notes and the next step stay in the file until you open it.",
        )
    }
}

/** Where an application stands on the road: a dot for each stage, lit up to the current one, rosso at it. */
class Dots(c: Context, private val stage: Stage) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val n = Stage.values().size - 1   // Closed is not a place on the road

    init { contentDescription = stage.title }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(context.px(14 * n), context.px(14))

    override fun onDraw(cv: Canvas) {
        val step = width / n.toFloat()
        val cy = height / 2f
        val r = context.px(3).toFloat()
        p.color = Kit.HAIRLINE
        p.strokeWidth = context.px(1).toFloat()
        cv.drawLine(step / 2f, cy, width - step / 2f, cy, p)
        for (i in 0 until n) {
            val x = step * i + step / 2f
            p.color = when {
                stage == Stage.CLOSED -> Kit.HAIRLINE
                i < stage.ordinal -> Kit.CHAMPAGNE
                i == stage.ordinal -> Kit.ROSSO
                else -> Kit.RAISED
            }
            cv.drawCircle(x, cy, if (i == stage.ordinal) r * 1.35f else r, p)
        }
    }
}
