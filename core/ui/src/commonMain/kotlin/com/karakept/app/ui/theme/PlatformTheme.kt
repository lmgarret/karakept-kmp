package com.karakept.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

@Composable
expect fun getDynamicColorScheme(darkTheme: Boolean): ColorScheme?
