package com.wisp.todo

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.wisp.todo.security.RecoveryKeyManager

@Composable
fun SetupFlow(manager: RecoveryKeyManager, restore: (String, Uri, (Boolean) -> Unit) -> Unit, finished: () -> Unit) {
    // Language changes never recreate this state or generate another key.
    var step by rememberSaveable { mutableStateOf(0) }
    var restored by rememberSaveable { mutableStateOf(false) }
    BackHandler(step > 0) { step = if (step == 3) { if (restored) 1 else 2 } else step - 1 }
    when (step) {
        0 -> LanguageStep { step = 1 }
        1 -> ImportChoice(manager, restore, { step = 0 }, { restored = true; step = 3 }) { restored = false; step = 2 }
        2 -> RecoveryStep(manager, { step = 1 }) { step = 3 }
        else -> NotificationStep({ step = if (restored) 1 else 2 }, finished)
    }
}

@Composable
private fun LanguageStep(next: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("Wisp To Do", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Text(tr("Язык приложения"), style = MaterialTheme.typography.titleLarge)
        UiLanguage.names.forEachIndexed { index, name ->
            TextButton(onClick = { UiLanguage.select(UiLanguage.tags[index]) }, modifier = Modifier.fillMaxWidth()) {
                RadioButton(UiLanguage.tag == UiLanguage.tags[index], onClick = null)
                Text(name, Modifier.weight(1f).padding(start = 12.dp))
            }
        }
        Button(next, Modifier.fillMaxWidth()) { Text(tr("Продолжить")) }
    }
}

@Composable
private fun ImportChoice(manager: RecoveryKeyManager, restore: (String, Uri, (Boolean) -> Unit) -> Unit, back: () -> Unit, imported: () -> Unit, fresh: () -> Unit) {
    val context = LocalContext.current
    var phrase by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var restoring by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && phrase.isNotBlank()) {
            restoring = true
            restore(phrase.trim(), uri) { ok ->
                restoring = false
                if (ok) { manager.save(phrase); manager.confirmSaved(); imported() } else message = tr("Не удалось открыть резервную копию. Проверьте файл и сид-фразу.")
            }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp)) {
        TextButton(back) { Text("‹ " + tr("Назад")) }
        Text(tr("Восстановить данные"), style = MaterialTheme.typography.headlineSmall)
        Text(tr("Выберите зашифрованную резервную копию, вставьте сид-фразу и продолжите работу с прежними задачами."), modifier = Modifier.padding(vertical = 12.dp))
        OutlinedTextField(phrase, { phrase = it }, label = { Text(tr("Сид-фраза")) }, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation())
        OutlinedButton({
            val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
            phrase = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        }, Modifier.fillMaxWidth()) { Text(tr("Вставить из буфера")) }
        Button({ picker.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth(), enabled = phrase.isNotBlank() && !restoring) { Text(tr("Выбрать файл и импортировать")) }
        if (restoring) CircularProgressIndicator(Modifier.padding(top = 16.dp))
        message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
        TextButton(fresh, Modifier.fillMaxWidth(), enabled = !restoring) { Text(tr("Начать без импорта")) }
    }

}

@Composable
private fun RecoveryStep(manager: RecoveryKeyManager, back: () -> Unit, next: () -> Unit) {
    val context = LocalContext.current
    var phrase by remember { mutableStateOf<String?>(null) }
    var saved by rememberSaveable { mutableStateOf(false) }
    var first by rememberSaveable { mutableStateOf("") }
    var last by rememberSaveable { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }
    var copyWarning by remember { mutableStateOf(false) }
    // Existing v4 phrases are reused, including previously unconfirmed phrases.
    LaunchedEffect(manager) { phrase = manager.create() }
    DisposableEffect(Unit) {
        val window = (context as? ComponentActivity)?.window
        val alreadySecure = ((window?.attributes?.flags ?: 0) and WindowManager.LayoutParams.FLAG_SECURE) != 0
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (!alreadySecure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp)) {
        TextButton(back) { Text("‹ " + tr("Назад")) }
        Text(tr("Сохраните сид-фразу"), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(tr("При удалении приложения, очистке данных или потере телефона локальные задачи могут быть потеряны. Для восстановления нужны зашифрованная резервная копия И эта фраза. Без фразы копию расшифровать нельзя. Одна фраза не восстанавливает отсутствующую копию."))
        Spacer(Modifier.height(16.dp))
        val words = phrase?.split(" ")
        if (words == null) {
            CircularProgressIndicator()
        } else {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    words.chunked(2).forEachIndexed { row, pair ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                            pair.forEachIndexed { col, word -> Text("${row * 2 + col + 1}. $word", Modifier.weight(1f)) }
                        }
                    }
                }
            }
            OutlinedButton({ copyWarning = true }, Modifier.fillMaxWidth()) {
                Text(tr(if (copied) "Фраза скопирована" else "Скопировать фразу"))
            }
            Text(tr("Введите слова №1 и №24 для проверки"))
            OutlinedTextField(first, { first = it }, label = { Text(tr("Слово №1")) },
                singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(last, { last = it }, label = { Text(tr("Слово №24")) },
                singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Row {
                Checkbox(saved, { saved = it })
                Text(tr("Я сохранил фразу отдельно"), Modifier.padding(top = 12.dp))
            }
            Button(onClick = { manager.confirmSaved(); next() }, modifier = Modifier.fillMaxWidth(),
                enabled = saved && first.trim().equals(words.first(), true) && last.trim().equals(words.last(), true)) {
                Text(tr("Продолжить"))
            }
        }
    }
    if (copyWarning) AlertDialog(
        onDismissRequest = { copyWarning = false },
        title = { Text(tr("Скопировать фразу")) },
        text = { Text(tr("Буфер обмена может быть доступен другим приложениям. Лучше хранить фразу офлайн. Никому её не отправляйте.")) },
        dismissButton = { TextButton({ copyWarning = false }) { Text(tr("Отмена")) } },
        confirmButton = {
            Button({
                val clip = ClipData.newPlainText("Wisp recovery", phrase ?: return@Button)
                if (Build.VERSION.SDK_INT >= 33) {
                    clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
                }
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
                copied = true
                copyWarning = false
            }) { Text(tr("Скопировать фразу")) }
        }
    )
}

@Composable
private fun NotificationStep(back: () -> Unit, finished: () -> Unit) {
    var denied by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        if (granted) finished()
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        TextButton(back) { Text("‹ " + tr("Назад")) }
        Text(tr("Напоминания"), style = MaterialTheme.typography.headlineMedium)
        Text(tr("Разрешение нужно для напоминаний о задачах. Вы можете включить уведомления позже в настройках Android."), Modifier.padding(vertical = 24.dp))
        Button({
            if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else finished()
        }, Modifier.fillMaxWidth()) { Text(tr("Разрешить напоминания")) }
        if (denied) Text(tr("Разрешение не выдано. Вы можете попробовать снова или продолжить без уведомлений."), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
        TextButton(finished) { Text(tr("Продолжить без уведомлений")) }
    }
}
