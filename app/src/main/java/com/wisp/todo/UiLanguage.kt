package com.wisp.todo

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

object UiLanguage {
    val tags = listOf("en", "pl", "uk", "de", "fr", "es", "it", "pt", "nl", "ru")
    val names = listOf("English", "Polski", "Українська", "Deutsch", "Français", "Español", "Italiano", "Português", "Nederlands", "Русский")
    private lateinit var app: Context
    var tag by mutableStateOf("en")
        private set
    private var resourcesContext: Context? = null
    fun initialize(context: Context) {
        app = context.applicationContext
        val prefs = app.getSharedPreferences("wisp_settings", 0)
        val stored = prefs.getString("language_tag", null)
        val legacy = names.indexOf(prefs.getString("language", null))
        tag = stored?.takeIf { it in tags } ?: if (legacy >= 0) tags[legacy] else Locale.getDefault().language.takeIf { it in tags } ?: "en"
        resourcesContext = null
    }
    fun select(value: String) {
        require(value in tags)
        resourcesContext = null
        tag = value
        app.getSharedPreferences("wisp_settings", 0).edit().putString("language_tag", value).apply()
    }
    fun name() = names[tags.indexOf(tag)]
    fun localized(base: Context): Context {
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag))
        return base.createConfigurationContext(config)
    }
    fun text(id: Int, vararg args: Any): String {
        val currentTag = tag // Compose observes the language in every text call.
        val ctx = resourcesContext ?: localized(app).also { resourcesContext = it }
        return if (args.isEmpty()) ctx.getString(id) else String.format(Locale.forLanguageTag(currentTag), ctx.getString(id), *args)
    }
    private val ids = mapOf(
        "Язык приложения" to R.string.ui_language,
        "Продолжить" to R.string.ui_continue,
        "Назад" to R.string.ui_back,
        "Отмена" to R.string.ui_cancel,
        "Готово" to R.string.ui_done,
        "Создать" to R.string.ui_create,
        "Экспорт" to R.string.ui_export,
        "Импорт" to R.string.ui_import,
        "Сохранить" to R.string.ui_save,
        "Удалить" to R.string.ui_delete,
        "Подтверждение" to R.string.ui_confirm,
        "Поиск" to R.string.ui_search,
        "Настройки" to R.string.ui_settings,
        "О приложении Wisp To Do" to R.string.ui_about,
        "Исходный код на GitLab" to R.string.ui_source_gitlab,
        "Исходный код на GitHub" to R.string.ui_source_github,
        "Лицензия GNU GPL" to R.string.ui_gpl_license,
        "Мой день" to R.string.ui_today,
        "Избранное" to R.string.ui_favorites,
        "Запланировано" to R.string.ui_planned,
        "Все задачи" to R.string.ui_all,
        "Проекты" to R.string.ui_projects,
        "Проект" to R.string.ui_project,
        "Списки" to R.string.ui_lists,
        "Мои списки" to R.string.ui_my_lists,
        "Задачи" to R.string.ui_tasks,
        "Добавить" to R.string.ui_add,
        "Новый список" to R.string.ui_new_list,
        "Новый проект" to R.string.ui_new_project,
        "Новая задача" to R.string.ui_new_task,
        "Название списка" to R.string.ui_list_name,
        "Название проекта" to R.string.ui_project_name,
        "Без проекта" to R.string.ui_no_project,
        "Создайте первый список" to R.string.ui_first_list,
        "Создать список" to R.string.ui_create_list,
        "Здесь пока спокойно" to R.string.ui_empty,
        "Добавьте задачу, когда будете готовы" to R.string.ui_empty_hint,
        "Поиск списков и задач" to R.string.ui_search_hint,
        "Ничего не найдено" to R.string.ui_no_results,
        "Что нужно сделать?" to R.string.ui_task_hint,
        "Добавить напоминание" to R.string.ui_add_reminder,
        "Убрать напоминание" to R.string.ui_remove_reminder,
        "Спокойный порядок каждый день" to R.string.ui_tagline,
        "Подключить" to R.string.ui_connect,
        "Изменить" to R.string.ui_change,
        "Не подключён" to R.string.ui_not_connected,
        "Загрузить" to R.string.ui_upload,
        "Восстановить" to R.string.ui_restore,
        "Синхронизация ещё не выполнялась" to R.string.ui_sync_never,
        "В облако отправляется только backup.atodo.enc" to R.string.ui_cloud_cipher,
        "Безопасность и резервная копия" to R.string.ui_security,
        "Подключить Nextcloud" to R.string.ui_connect_nextcloud,
        "Используйте полный WebDAV URL и отдельный пароль приложения." to R.string.ui_dav_help,
        "Логин" to R.string.ui_login,
        "Пароль приложения" to R.string.ui_app_password,
        "Активные задачи: %1\$d" to R.string.ui_active_count,
        "Списки: %1\$d" to R.string.ui_lists_count,
        "Выполненные (%1\$d)" to R.string.ui_completed_count,
        "Удалить задачу «%1\$s»?" to R.string.ui_delete_task,
        "Удалить список «%1\$s» и все его задачи?" to R.string.ui_delete_list,
        "Удалить проект «%1\$s»? Списки останутся без проекта." to R.string.ui_delete_project,
        "Сохраните сид-фразу" to R.string.ui_recovery_title,
        "При удалении приложения, очистке данных или потере телефона локальные задачи могут быть потеряны. Для восстановления нужны зашифрованная резервная копия И эта фраза. Без фразы копию расшифровать нельзя. Одна фраза не восстанавливает отсутствующую копию." to R.string.ui_recovery_warning,
        "Скопировать фразу" to R.string.ui_copy,
        "Фраза скопирована" to R.string.ui_copied,
        "Буфер обмена может быть доступен другим приложениям. Лучше хранить фразу офлайн. Никому её не отправляйте." to R.string.ui_clipboard_warning,
        "Я сохранил фразу отдельно" to R.string.ui_saved_phrase,
        "Введите слова №1 и №24 для проверки" to R.string.ui_check_words,
        "Слово №1" to R.string.ui_word_first,
        "Слово №24" to R.string.ui_word_last,
        "Напоминания" to R.string.ui_notifications,
        "Разрешить напоминания" to R.string.ui_allow,
        "Уведомления уже разрешены" to R.string.ui_notifications_allowed,
        "Продолжить без уведомлений" to R.string.ui_skip,
        "Разрешение нужно для напоминаний о задачах. Вы можете включить уведомления позже в настройках Android." to R.string.ui_notification_help,
        "Передача завершена" to R.string.ui_transfer_ok,
        "Не удалось передать данные. Проверьте подключение, реквизиты и сид-фразу." to R.string.ui_transfer_error
        ,"Восстановить данные" to R.string.ui_restore_data
        ,"Сид-фраза" to R.string.ui_seed_phrase
        ,"Выберите зашифрованную резервную копию, вставьте сид-фразу и продолжите работу с прежними задачами." to R.string.ui_restore_help
        ,"Вставить из буфера" to R.string.ui_paste_clipboard
        ,"Выбрать файл и импортировать" to R.string.ui_choose_import
        ,"Начать без импорта" to R.string.ui_start_without_import
        ,"Доступ к резервной копии" to R.string.ui_backup_access
        ,"Откроется системный выбор файлов. Wisp To Do получит доступ только к файлу, который вы сами выберете. Можно нажать «Отмена» и вернуться назад." to R.string.ui_file_access_onboarding
        ,"Открыть выбор файлов" to R.string.ui_open_file_picker
        ,"Разрешение не выдано. Вы можете попробовать снова или продолжить без уведомлений." to R.string.ui_permission_denied_retry
        ,"Не удалось открыть резервную копию. Проверьте файл и сид-фразу." to R.string.ui_import_error
        ,"Изменить задачу" to R.string.ui_edit_task
        ,"Разрешение на уведомления" to R.string.ui_notification_permission
        ,"Android запросит разрешение на показ напоминаний. Если вы откажетесь, календарь не откроется и задача останется без уведомления." to R.string.ui_notification_prompt_task
        ,"Без разрешения Android уведомление не будет показано." to R.string.ui_notification_required_task
        ,"Откроется системный выбор файлов. Приложение получит доступ только к выбранному вами файлу." to R.string.ui_file_access_settings
        ,"Android покажет системный запрос. При отказе вы останетесь в настройках и сможете включить разрешение позже." to R.string.ui_notification_prompt_settings
    )
    fun text(source: String, vararg args: Any): String = ids[source]?.let { text(it, *args) } ?: source
}
fun tr(source: String, vararg args: Any): String = UiLanguage.text(source, *args)
