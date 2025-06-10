package com.monekx.curfewnotifier.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Основные цвета
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF4CAF50),       // зелёный
    onPrimary = Color.White,
    secondary = Color(0xFFFFC107),     // жёлтый
    onSecondary = Color.Black,
    background = Color(0xFFF5F5F5),    // светло-серый фон
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    error = Color(0xFFF44336),         // красный
    onError = Color.White
)

// Можно добавить кастомные шрифты, если хочешь — пока стандартный Roboto
private val AppTypography = Typography(
    bodyLarge = androidx.compose.ui.text.TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    titleLarge = androidx.compose.ui.text.TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    )
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(0.dp)
)

@Composable
fun CurfewNotifierTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
