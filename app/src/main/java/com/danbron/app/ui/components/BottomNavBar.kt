package com.danbron.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.danbron.app.ui.theme.*

data class NavItem(val label: String, val icon: ImageVector, val route: String)

@Composable
fun BottomNavBar(currentRoute: String, onNavigate: (String) -> Unit) {
    val items = listOf(
        NavItem("Inicio", Icons.Outlined.Home, "home"),
        NavItem("Bron", Icons.Outlined.ChatBubbleOutline, "chat"),
        NavItem("Notas", Icons.Outlined.EditNote, "notes"),
        NavItem("Progreso", Icons.Outlined.BarChart, "progress"),
        NavItem("Yo", Icons.Outlined.Settings, "settings"),
    )

    Row(
        Modifier.fillMaxWidth().background(BgPrimary.copy(alpha = 0.9f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        items.forEach { item ->
            val active = currentRoute == item.route
            Column(
                Modifier.clip(RoundedCornerShape(12.dp))
                    .background(if (active) GoldGlow else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onNavigate(item.route) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(item.icon, contentDescription = item.label,
                    tint = if (active) Gold else TextTertiary,
                    modifier = Modifier.size(20.dp))
                Spacer(Modifier.height(3.dp))
                Text(item.label, style = DanbronType.caption,
                    color = if (active) Gold else TextTertiary)
            }
        }
    }
}
