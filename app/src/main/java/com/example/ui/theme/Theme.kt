package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val EchoNoteLightColorScheme = lightColorScheme(
    primary = TerracottaPrimary,
    onPrimary = TerracottaOnPrimary,
    primaryContainer = TerracottaContainer,
    onPrimaryContainer = TerracottaOnContainer,
    secondary = SageSecondary,
    onSecondary = SageOnSecondary,
    secondaryContainer = SageContainer,
    onSecondaryContainer = SageOnContainer,
    tertiary = AmberTertiary,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = AmberOnContainer,
    background = WarmPaperBg,
    onBackground = CharcoalInk,
    surface = IvorySurface,
    onSurface = CharcoalInk,
    surfaceVariant = LinenSurfaceVariant,
    onSurfaceVariant = StoneMuted,
    outline = WarmBorder,
    outlineVariant = WarmBorder,
    error = CrimsonError,
    errorContainer = CrimsonContainer,
    onErrorContainer = CrimsonOnContainer
)

private val EchoNoteDarkColorScheme = darkColorScheme(
    primary = CoralTerracottaDark,
    onPrimary = CoralOnPrimaryDark,
    primaryContainer = CoralContainerDark,
    onPrimaryContainer = CoralOnContainerDark,
    secondary = SoftSageDark,
    onSecondary = CharcoalSurface,
    secondaryContainer = SoftSageContainerDark,
    onSecondaryContainer = SoftSageOnContainerDark,
    tertiary = AmberContainer,
    tertiaryContainer = AmberOnContainer,
    onTertiaryContainer = AmberContainer,
    background = EspressoBg,
    onBackground = BoneInk,
    surface = CharcoalSurface,
    onSurface = BoneInk,
    surfaceVariant = UmberSurfaceVariant,
    onSurfaceVariant = WarmTaupeMuted,
    outline = DarkWarmBorder,
    outlineVariant = DarkWarmBorder,
    error = CrimsonContainer,
    errorContainer = CrimsonOnContainer,
    onErrorContainer = CrimsonContainer
)

val EchoNoteShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun EchoNoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) EchoNoteDarkColorScheme else EchoNoteLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = EchoNoteTypography,
        shapes = EchoNoteShapes,
        content = content
    )
}
