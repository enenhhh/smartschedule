package com.enenhhh.smartschedule

import com.enenhhh.smartschedule.util.DateUtils
import com.enenhhh.smartschedule.legal.ReadingGate
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class WeekAndConsentTest {
    private fun d(s: String) = LocalDate.parse(s)
    @Test fun sundayTomorrowUsesNextMondayWeek() {
        val start = d("2026-09-07")
        assertEquals(1, DateUtils.weekNumber(start, d("2026-09-13")))
        assertEquals(2, DateUtils.weekNumber(start, d("2026-09-13").plusDays(1)))
        assertFalse(DateUtils.isOddWeek(start, d("2026-09-14")))
    }
    @Test fun midWeekSemesterStartIsAlignedToMonday() {
        assertEquals(1, DateUtils.weekNumber(d("2026-09-09"), d("2026-09-13")))
        assertEquals(2, DateUtils.weekNumber(d("2026-09-09"), d("2026-09-14")))
    }
    @Test fun yearBoundaryAndLeapDay() {
        assertEquals(2, DateUtils.weekNumber(d("2026-12-28"), d("2027-01-04")))
        assertEquals(1, DateUtils.weekNumber(d("2024-02-26"), d("2024-02-29")))
        assertEquals(2, DateUtils.weekNumber(d("2024-02-26"), d("2024-03-04")))
    }
    @Test fun beforeSemesterDoesNotRoundTowardZero() {
        assertEquals(0, DateUtils.weekNumber(d("2026-09-07"), d("2026-09-06")))
        assertEquals(-1, DateUtils.weekNumber(d("2026-09-07"), d("2026-08-30")))
    }
    @Test fun reverseWeekConfiguration() {
        val today = d("2026-10-16")
        for (week in 1..32) {
            assertEquals(week, DateUtils.weekNumber(DateUtils.startDateForCurrentWeek(week, today), today))
        }
    }
    @Test fun layoutMustExistBeforeReadingCanComplete() {
        assertFalse(ReadingGate.reachedEnd(0, 0, 0))
        assertFalse(ReadingGate.reachedEnd(0, Int.MAX_VALUE, 800))
        assertFalse(ReadingGate.reachedEnd(0, 1000, 800))
    }
    @Test fun requireTheActualEndOfEachDocument() {
        assertFalse(ReadingGate.reachedEnd(700, 1000, 800))
        assertTrue(ReadingGate.reachedEnd(1000, 1000, 800))
        assertTrue(ReadingGate.reachedEnd(0, 0, 800)) // Entire text visible on a large display.
    }
    @Test fun scrollingDoesNotAutomaticallyGrantConsent() {
        assertFalse(ReadingGate.canContinue(false, false))
        assertFalse(ReadingGate.canContinue(false, true))
        assertFalse(ReadingGate.canContinue(true, false))
        assertTrue(ReadingGate.canContinue(true, true))
    }
}
