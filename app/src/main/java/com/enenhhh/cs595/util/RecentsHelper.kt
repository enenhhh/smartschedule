package com.enenhhh.smartschedule.util

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.os.Build

object RecentsHelper {
    fun apply(activity: Activity) {
        val hide = Prefs.isHideRecents(activity)
        try {
            val am = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.appTasks?.forEach { task ->
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        task.setExcludeFromRecents(hide)
                    }
                } catch (_: Throwable) { }
            }
        } catch (_: Throwable) { }
    }
}