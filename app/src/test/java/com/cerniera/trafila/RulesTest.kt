package com.cerniera.trafila

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RulesTest {
    private val thu = LocalDate.of(2026, 11, 26)   // a Thursday

    @Test fun workingDaysSkipWeekends() {
        assertEquals(0, Rules.workingDays(thu, thu))
        assertEquals(1, Rules.workingDays(thu, thu.plusDays(1)))          // Fri
        assertEquals(0, Rules.workingDays(thu.plusDays(1), thu.plusDays(3)))   // Fri to Sun: the weekend only
        assertEquals(5, Rules.workingDays(thu, thu.plusDays(7)))
        assertEquals(12, Rules.workingDays(LocalDate.of(2026, 11, 10), thu))  // Spinwell: 10 Nov to 26 Nov
        assertEquals(0, Rules.workingDays(thu, thu.minusDays(3)))
    }

    @Test fun quietAfterTenWorkingDays() {
        val nine = Application(firm = "A", lastContact = LocalDate.of(2026, 11, 13))   // 9 working days
        val ten = Application(firm = "B", lastContact = LocalDate.of(2026, 11, 12))    // 10
        assertFalse(Rules.quiet(nine, thu))
        assertTrue(Rules.quiet(ten, thu))
        assertFalse(Rules.quiet(ten.copy(stage = Stage.CLOSED), thu))
        assertFalse(Rules.quiet(ten.copy(stage = Stage.OFFER), thu))
    }

    @Test fun owedPutsDueFirstThenQuiet() {
        val due = Application(id = 1, firm = "Due", lastContact = thu, next = "Chase", nextOn = thu.minusDays(1))
        val quiet = Application(id = 2, firm = "Quiet", lastContact = LocalDate.of(2026, 11, 4))
        val fine = Application(id = 3, firm = "Fine", lastContact = thu.minusDays(2))
        val later = Application(id = 4, firm = "Later", lastContact = LocalDate.of(2026, 11, 4), nextOn = thu.plusDays(2))
        val o = Rules.owed(listOf(fine, quiet, due, later), thu)
        assertEquals(listOf("Due", "Quiet"), o.map { it.app.firm })
        assertEquals("Chase (from yesterday)", o[0].why)
        assertTrue(o[1].quiet)
    }

    @Test fun roadIsFurthestAlongFirst() {
        val a = Application(firm = "Applied", stage = Stage.APPLIED, lastContact = thu)
        val i = Application(firm = "Interview", stage = Stage.INTERVIEW, lastContact = thu.minusDays(5))
        val c = Application(firm = "Closed", stage = Stage.CLOSED)
        assertEquals(listOf("Interview", "Applied"), Rules.road(listOf(a, c, i)).map { it.firm })
    }

    @Test fun nextMondayFromThursday() {
        assertEquals(LocalDate.of(2026, 11, 30), Rules.nextDay(4, thu))
        assertEquals(null, Rules.nextDay(0, thu))
    }

    @Test fun csvRoundTrip() {
        val apps = listOf(
            Application(firm = "Rathbones", role = "Business Analyst, Financial Crime Remediation", route = Route.AGENCY, via = "Hydrogen",
                stage = Stage.INTERVIEW, lastContact = thu.minusDays(3), next = "Chase \"Hydrogen\"", nextOn = thu.plusDays(4), notes = "Line one\nline two"),
            Application(firm = "Capitex", role = "Fraud Data Analyst", stage = Stage.APPLIED, lastContact = LocalDate.of(2026, 11, 4)),
        )
        val back = Csv.read(Csv.write(apps), thu)
        assertEquals(apps.map { it.copy(created = thu) }.map { it.firm to it.role }, back.map { it.firm to it.role })
        assertEquals(apps[0].notes, back[0].notes)
        assertEquals(apps[0].next, back[0].next)
        assertEquals(Route.AGENCY, back[0].route)
        assertEquals(Stage.INTERVIEW, back[0].stage)
        assertEquals(apps[0].nextOn, back[0].nextOn)
    }

    @Test fun csvReadsColumnsInAnyOrderAndSkipsBlanks() {
        val t = "Stage,Firm,Last contact\r\ninterview,Delta Capita,2026-11-19\r\n,,\r\n"
        val r = Csv.read(t, thu)
        assertEquals(1, r.size)
        assertEquals(Stage.INTERVIEW, r[0].stage)
        assertEquals(LocalDate.of(2026, 11, 19), r[0].lastContact)
    }
}
