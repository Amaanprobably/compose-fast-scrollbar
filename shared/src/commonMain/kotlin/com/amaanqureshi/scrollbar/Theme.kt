package com.amaanqureshi.scrollbar

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.amaanprobably.scrollbar.ScrollbarDefaults
import io.github.amaanprobably.scrollbar.ScrollbarLabel
import io.github.amaanprobably.scrollbar.ScrollbarLabelStyle
import io.github.amaanprobably.scrollbar.ScrollbarStyle
import io.github.amaanprobably.scrollbar.ScrollbarVisibility
import io.github.amaanprobably.scrollbar.ThumbAppearance
import io.github.amaanprobably.scrollbar.ThumbSizing

/** The demo's brand colors. Each showcase card gets one accent. */
internal object Brand {
    val Coral = Color(0xFFFF4F2E)
    val CoralBright = Color(0xFFFF6B4A)
    val Violet = Color(0xFF6C4DFF)
    val VioletBright = Color(0xFF9C86FF)
    val Mint = Color(0xFF22C995)
    val Amber = Color(0xFFFFB020)
    val Sky = Color(0xFF2E9BFF)
    val Pink = Color(0xFFFF4FA3)
    val Lime = Color(0xFF8DB33A)
    val Ink = Color(0xFF16141D)
    val Cream = Color(0xFFFBF7F0)
}

private val LightColors = lightColorScheme(
    primary = Brand.Coral,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE1D9),
    onPrimaryContainer = Color(0xFF5A1405),
    secondary = Brand.Violet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6E0FF),
    onSecondaryContainer = Color(0xFF22126B),
    background = Brand.Cream,
    onBackground = Brand.Ink,
    surface = Brand.Cream,
    onSurface = Brand.Ink,
    surfaceVariant = Color(0xFFF0E9DF),
    onSurfaceVariant = Color(0xFF615B66),
    outline = Color(0xFFD6CCBF),
    outlineVariant = Color(0xFFE6DED3),
    inverseSurface = Brand.Ink,
    inverseOnSurface = Brand.Cream,
)

private val DarkColors = darkColorScheme(
    primary = Brand.CoralBright,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5A1D10),
    onPrimaryContainer = Color(0xFFFFDAD1),
    secondary = Brand.VioletBright,
    onSecondary = Brand.Ink,
    secondaryContainer = Color(0xFF2F2466),
    onSecondaryContainer = Color(0xFFE6E0FF),
    background = Color(0xFF0F0E14),
    onBackground = Color(0xFFF4F0EA),
    surface = Color(0xFF0F0E14),
    onSurface = Color(0xFFF4F0EA),
    surfaceVariant = Color(0xFF221F2B),
    onSurfaceVariant = Color(0xFFA9A2B0),
    outline = Color(0xFF3A3645),
    outlineVariant = Color(0xFF2B2834),
    inverseSurface = Color(0xFFF4F0EA),
    inverseOnSurface = Brand.Ink,
)

/** Heavy, tightly spaced headings; monospace for small tags. */
private val BrandTypography: Typography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(
            fontWeight = FontWeight.Black, fontSize = 60.sp, lineHeight = 56.sp, letterSpacing = (-2.5).sp,
        ),
        displaySmall = base.displaySmall.copy(
            fontWeight = FontWeight.Black, fontSize = 32.sp, lineHeight = 34.sp, letterSpacing = (-1).sp,
        ),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold),
        labelSmall = base.labelSmall.copy(
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp,
        ),
    )
}

@Composable
internal fun DemoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = BrandTypography,
        content = content,
    )
}

/**
 * The library's style in the brand colors. The size and look still follow the platform
 * (capsule on Android, bar on iOS and desktop) unless [thumbAppearance] or [thumbSizing] is given.
 */
@Composable
internal fun brandScrollbarStyle(
    visibility: ScrollbarVisibility = ScrollbarDefaults.style().visibility,
    thumbAppearance: ThumbAppearance? = null,
    thumbSizing: ThumbSizing? = null,
): ScrollbarStyle {
    val colors = MaterialTheme.colorScheme
    return remember(colors.primary, colors.onSurface, visibility, thumbAppearance, thumbSizing) {
        val thumbColor = colors.onSurface
        val capsuleColor = colors.onSurface.copy(alpha = 0.25f)
        when {
            thumbAppearance != null && thumbSizing != null -> ScrollbarDefaults.style(
                visibility = visibility, thumbAppearance = thumbAppearance, thumbSizing = thumbSizing,
                thumbColor = thumbColor, pressedThumbColor = colors.primary, capsuleColor = capsuleColor,
            )
            thumbAppearance != null -> ScrollbarDefaults.style(
                visibility = visibility, thumbAppearance = thumbAppearance,
                thumbColor = thumbColor, pressedThumbColor = colors.primary, capsuleColor = capsuleColor,
            )
            thumbSizing != null -> ScrollbarDefaults.style(
                visibility = visibility, thumbSizing = thumbSizing,
                thumbColor = thumbColor, pressedThumbColor = colors.primary, capsuleColor = capsuleColor,
            )
            else -> ScrollbarDefaults.style(
                visibility = visibility,
                thumbColor = thumbColor, pressedThumbColor = colors.primary, capsuleColor = capsuleColor,
            )
        }
    }
}

/** A bold coral bubble for [ScrollbarLabel]. */
@Composable
internal fun brandLabelStyle(): ScrollbarLabelStyle {
    val colors = MaterialTheme.colorScheme
    return remember(colors.primary, colors.onPrimary) {
        ScrollbarLabelStyle(
            textStyle = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
            textColor = colors.onPrimary,
            bubbleColor = colors.primary,
            cornerRadius = 18.dp,
            horizontalPadding = 18.dp,
            verticalPadding = 10.dp,
            gap = 12.dp,
        )
    }
}