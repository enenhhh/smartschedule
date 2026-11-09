package com.enenhhh.smartschedule.ui.app

import com.enenhhh.smartschedule.ui.legal.LegalDocumentScreen
import com.enenhhh.smartschedule.ui.legal.PermissionsScreen
import com.enenhhh.smartschedule.ui.motion.motionEnabled
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.enenhhh.smartschedule.data.ScheduleManager
import com.enenhhh.smartschedule.data.TempSwaps
import com.enenhhh.smartschedule.ui.glass.LocalHazeState
import com.enenhhh.smartschedule.ui.components.LocalRoute
import com.enenhhh.smartschedule.ui.components.LocalTopBarRegistry
import com.enenhhh.smartschedule.ui.components.TopBarRegistry
import com.enenhhh.smartschedule.ui.glass.MeshBackground
import com.enenhhh.smartschedule.ui.homework.HomeworkScreen
import com.enenhhh.smartschedule.ui.nav.Navigator
import com.enenhhh.smartschedule.ui.nav.Route
import com.enenhhh.smartschedule.ui.nav.rememberNavigator
import com.enenhhh.smartschedule.ui.onboarding.OnboardingScreen
import com.enenhhh.smartschedule.ui.settings.AboutScreen
import com.enenhhh.smartschedule.ui.settings.AppearanceScreen
import com.enenhhh.smartschedule.ui.settings.DataScreen
import com.enenhhh.smartschedule.ui.settings.RemindersScreen
import com.enenhhh.smartschedule.ui.settings.SemesterScreen
import com.enenhhh.smartschedule.ui.settings.SettingsScreen
import com.enenhhh.smartschedule.ui.settings.ShortNamesScreen
import com.enenhhh.smartschedule.ui.timeplan.TimePlanEditorScreen
import com.enenhhh.smartschedule.ui.timeplan.TimePlansScreen
import com.enenhhh.smartschedule.ui.timetable.TimetableEditorScreen
import com.enenhhh.smartschedule.ui.timetable.TimetableScreen
import com.enenhhh.smartschedule.ui.timetable.TimetablesScreen
import com.enenhhh.smartschedule.ui.today.TodayScreen
import com.enenhhh.smartschedule.util.Prefs
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("Navigator not provided") }
val LocalScheduleManager = staticCompositionLocalOf<ScheduleManager> { error("ScheduleManager not provided") }
val LocalSnackbar = staticCompositionLocalOf<SnackbarHostState> { error("Snackbar not provided") }

/**
 * 应用根布局。层次（自下而上）：
 *   1. 流动背景 + 页面内容  —— 注册为 hazeSource，供上层玻璃取样
 *   2. 底部玻璃导航胶囊（仅顶层页面）
 *   3. Snackbar
 */
@Composable
fun SmartScheduleApp(pendingUpdateDialog: @Composable () -> Unit = {}) {
    val context = LocalContext.current
    val manager = remember { ScheduleManager(context) }
    val firstRun = remember { !Prefs.isFirstRunDone(context) }
    val nav = rememberNavigator(if (firstRun) Route.Onboarding else Route.Today)
    val haze = rememberHazeState()
    val snackbar = remember { SnackbarHostState() }
    val topBars = remember { TopBarRegistry() }
    val motion = motionEnabled()

    LaunchedEffect(Unit) { TempSwaps.clearExpired(context) }
    // 心跳：界面在前台期间每 15 秒对一次表；日期翻页时顺手清理过期调课
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(15_000)
            if (com.enenhhh.smartschedule.util.AppClock.refresh()) TempSwaps.clearExpired(context)
        }
    }

    BackHandler(enabled = nav.canGoBack) { nav.pop() }

    CompositionLocalProvider(
        LocalHazeState provides haze,
        LocalTopBarRegistry provides topBars,
        LocalNavigator provides nav,
        LocalScheduleManager provides manager,
        LocalSnackbar provides snackbar
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .hazeSource(state = haze)
            ) {
                MeshBackground(Modifier.fillMaxSize())
                AnimatedContent(
                    targetState = nav.current,
                    transitionSpec = {
                        val forward = nav.lastMoveForward
                        if (!motion) {
                            fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                        } else if (initialState.isTopLevel && targetState.isTopLevel) {
                            (fadeIn(tween(230)) + scaleIn(tween(330, easing = FastOutSlowInEasing), initialScale = 0.965f)) togetherWith
                                (fadeOut(tween(100)) + scaleOut(tween(190), targetScale = 1.015f))
                        } else if (forward) {
                            (slideInHorizontally(tween(320)) { it / 5 } + fadeIn(tween(260))) togetherWith
                                (slideOutHorizontally(tween(320)) { -it / 8 } + fadeOut(tween(200)))
                        } else {
                            (slideInHorizontally(tween(320)) { -it / 8 } + fadeIn(tween(260))) togetherWith
                                (slideOutHorizontally(tween(320)) { it / 5 } + fadeOut(tween(200)))
                        }
                    },
                    label = "routes"
                ) { route ->
                    CompositionLocalProvider(LocalRoute provides route) {
                        RouteContent(route)
                    }
                }
            }

            // 子页面顶栏：在 hazeSource 之外渲染（Haze 不允许效果节点位于其取样源之内）
            AnimatedContent(
                targetState = nav.current,
                transitionSpec = { fadeIn(tween(if (motion) 220 else 0)) togetherWith fadeOut(tween(if (motion) 160 else 0)) },
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                label = "topbar"
            ) { route ->
                Box(Modifier.fillMaxWidth()) { topBars.Render(route) }
            }

            AnimatedVisibility(
                visible = nav.current.isTopLevel,
                enter = slideInVertically(tween(if (motion) 280 else 0)) { it } + fadeIn(),
                exit = slideOutVertically(tween(if (motion) 220 else 0)) { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                GlassBottomNav(current = nav.current, onSelect = { nav.switchTab(it) })
            }

            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 100.dp)
            )

            pendingUpdateDialog()
        }
    }
}

@Composable
private fun RouteContent(route: Route) {
    when (route) {
        Route.Today -> TodayScreen()
        Route.Timetable -> TimetableScreen()
        Route.Homework -> HomeworkScreen()
        Route.Settings -> SettingsScreen()
        Route.Timetables -> TimetablesScreen()
        is Route.TimetableEditor -> TimetableEditorScreen(route.id)
        Route.TimePlans -> TimePlansScreen()
        is Route.TimePlanEditor -> TimePlanEditorScreen(route.name)
        Route.ShortNames -> ShortNamesScreen()
        Route.Semester -> SemesterScreen()
        Route.Appearance -> AppearanceScreen()
        Route.Reminders -> RemindersScreen()
        Route.DataManagement -> DataScreen()
        is Route.LegalDocument -> LegalDocumentScreen(route.id)
        Route.Permissions -> PermissionsScreen()
        Route.About -> AboutScreen()
        Route.Onboarding -> OnboardingScreen()
    }
}
