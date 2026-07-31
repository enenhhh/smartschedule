package com.enenhhh.smartschedule.ui.app

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enenhhh.smartschedule.ui.FontSwitchDialog
import com.enenhhh.smartschedule.ui.HomeScreen
import com.enenhhh.smartschedule.ui.HomeworkScreen
import com.enenhhh.smartschedule.ui.ScheduleScreen
import com.enenhhh.smartschedule.ui.Text
import com.enenhhh.smartschedule.ui.components.BrandTitle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartScheduleApp(
    openSettings: () -> Unit
) {
    var currentScreen by remember { mutableStateOf(AppScreen.Home) }
    var showFontDialog by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    fun go(target: AppScreen) {
        currentScreen = target
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                BrandTitle(
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                    titleSize = 24.sp,
                    subtitleSize = 12.sp
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(8.dp))

                DrawerItem("首页", Icons.Default.Home, currentScreen == AppScreen.Home) {
                    go(AppScreen.Home)
                }
                DrawerItem("课表", Icons.Default.CalendarMonth, currentScreen == AppScreen.Schedule) {
                    go(AppScreen.Schedule)
                }
                DrawerItem("作业", Icons.AutoMirrored.Filled.Assignment, currentScreen == AppScreen.Homework) {
                    go(AppScreen.Homework)
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(8.dp))

                DrawerItem("字体设置", Icons.Default.TextFields, false) {
                    showFontDialog = true
                    scope.launch { drawerState.close() }
                }
                DrawerItem("设置", Icons.Default.Settings, false) {
                    openSettings()
                    scope.launch { drawerState.close() }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { BrandTitle(titleSize = 22.sp, subtitleSize = 11.sp) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "菜单")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    )
                )
            }
        ) { innerPadding ->
            when (currentScreen) {
                AppScreen.Home -> HomeScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    onOpenSchedule = { currentScreen = AppScreen.Schedule },
                    onOpenHomework = { currentScreen = AppScreen.Homework }
                )

                AppScreen.Schedule -> ScheduleScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )

                AppScreen.Homework -> HomeworkScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }

            if (showFontDialog) {
                FontSwitchDialog(onDismiss = { showFontDialog = false })
            }
        }
    }
}

@Composable
private fun DrawerItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        icon = { Icon(icon, contentDescription = null) },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}
