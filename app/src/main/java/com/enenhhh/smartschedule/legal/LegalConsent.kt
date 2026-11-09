package com.enenhhh.smartschedule.legal

import android.content.Context

/** Separate, non-exportable preferences: somebody else's backup cannot grant consent. */
object LegalConsent {
    private const val PREF = "legal_consent"
    fun isAccepted(context: Context): Boolean =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString("revision", null) == LegalContent.REVISION

    fun accept(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString("revision", LegalContent.REVISION)
            .putLong("accepted_at", System.currentTimeMillis()).apply()
    }
}

/** Pure guard, shared by the UI and unit tests. No timer or synthetic "read" button. */
object ReadingGate {
    fun reachedEnd(value: Int, maxValue: Int, viewport: Int): Boolean =
        viewport > 0 && maxValue >= 0 && maxValue < Int.MAX_VALUE && value >= maxValue - 2
    fun canContinue(reached: Boolean, checked: Boolean): Boolean = reached && checked
}
