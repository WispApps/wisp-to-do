package com.wisp.todo

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

object UiTheme {
    private lateinit var app: Context
    var dark by mutableStateOf(true)
        private set
    fun initialize(context: Context) {
        app = context.applicationContext
        dark = app.getSharedPreferences("wisp_settings", 0).getBoolean("dark_theme", true)
    }
    fun selectDark(value: Boolean) {
        app.getSharedPreferences("wisp_settings", 0).edit().putBoolean("dark_theme", value).apply()
        dark = value
    }
}

@Composable
fun ThemeSwitchRow() {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Row(
            Modifier.fillMaxWidth().toggleable(value = UiTheme.dark, role = Role.Switch, onValueChange = UiTheme::selectDark).padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(UiLanguage.text(R.string.ui_theme), style = MaterialTheme.typography.titleMedium)
                Text(UiLanguage.text(if (UiTheme.dark) R.string.ui_theme_dark else R.string.ui_theme_light),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            Switch(checked = UiTheme.dark, onCheckedChange = null)
        }
    }
}
