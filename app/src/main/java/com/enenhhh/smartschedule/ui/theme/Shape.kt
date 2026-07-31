package com.enenhhh.smartschedule.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 圆角比 M3 默认略大一点，和课表卡片、方案卡片这类大色块更搭。
 *
 * 各角色的实际使用位置：
 *  - extraSmall: Chip、小按钮
 *  - small:      TextField、Menu
 *  - medium:     Card（本应用里出现最多）
 *  - large:      NavigationDrawer、BottomSheet
 *  - extraLarge: Dialog
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)
