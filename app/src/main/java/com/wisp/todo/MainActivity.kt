@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.wisp.todo

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wisp.todo.data.*
import com.wisp.todo.backup.BackupManager
import com.wisp.todo.reminders.ReminderScheduler
import com.wisp.todo.security.RecoveryKeyManager
import com.wisp.todo.sync.NextcloudConfig
import com.wisp.todo.sync.NextcloudConfigStore
import com.wisp.todo.sync.NextcloudSync
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Calendar

private val Mint = Color(0xFF16B991)
private val Blue = Color(0xFF4B8EF7)
private val Ink: Color @Composable get() = MaterialTheme.colorScheme.onSurface
private val Background: Color @Composable get() = MaterialTheme.colorScheme.background

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiLanguage.initialize(this)
        UiTheme.initialize(this)
        window.statusBarColor = AndroidColor.BLACK
        window.navigationBarColor = AndroidColor.BLACK
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContent { WispTheme { WispAppUi() } }
    }
}

class MainViewModel(private val repo: TaskRepository, private val appContext: Context) : ViewModel() {
    val lists = repo.lists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val projects = repo.projects.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun addList(title: String, projectId: String?) = viewModelScope.launch { if (title.isNotBlank()) repo.addList(title, projectId) }
    fun renameList(list: TaskListEntity, title: String) = viewModelScope.launch { repo.renameList(list, title) }
    fun renameProject(project: ProjectEntity, title: String) = viewModelScope.launch { repo.renameProject(project, title) }
    fun deleteList(id: String) = viewModelScope.launch { repo.deleteList(id) }
    fun addProject(title: String) = viewModelScope.launch { if (title.isNotBlank()) repo.addProject(title) }
    fun deleteProject(id: String) = viewModelScope.launch { repo.deleteProject(id) }
    fun addTask(listId: String, title: String, reminderAt: Long?, created: (TaskEntity) -> Unit) = viewModelScope.launch {
        if (title.isNotBlank()) created(repo.addTask(listId, title, reminderAt))
    }
    fun toggle(task: TaskEntity) = viewModelScope.launch { repo.save(task.copy(completed = !task.completed)) }
    fun favorite(task: TaskEntity) = viewModelScope.launch { repo.save(task.copy(favorite = !task.favorite)) }
    fun save(task: TaskEntity) = viewModelScope.launch { repo.save(task) }
    fun deleteTask(task: TaskEntity) = viewModelScope.launch { repo.deleteTask(task) }
    fun exportArchive(phrase: String, write: suspend (ByteArray) -> Unit, result: (Boolean) -> Unit) = viewModelScope.launch {
        result(runCatching { val (lists, tasks, projects) = repo.snapshot(); withContext(Dispatchers.IO) { write(BackupManager.encode(projects, lists, tasks, phrase)) } }.isSuccess)
    }
    fun importArchive(phrase: String, read: suspend () -> ByteArray, result: (Boolean) -> Unit) = viewModelScope.launch {
        val imported = runCatching {
            val data = withContext(Dispatchers.IO) { read() }
            val (projects, lists, tasks) = BackupManager.decode(data, phrase)
            repo.merge(projects, lists, tasks)
        }.isSuccess
        if (imported) rescheduleUpcomingReminders()
        result(imported)
    }
    fun upload(config: NextcloudConfig, phrase: String, result: (String) -> Unit) = viewModelScope.launch { result(runCatching { NextcloudSync(repo, phrase).upload(config) }.getOrElse { "Ошибка: ${it.message}" }) }
    fun download(config: NextcloudConfig, phrase: String, result: (String) -> Unit) = viewModelScope.launch {
        val message = runCatching { NextcloudSync(repo, phrase).download(config) }.getOrElse { "Ошибка: ${it.message}" }
        if (!message.startsWith("Ошибка:")) rescheduleUpcomingReminders()
        result(message)
    }
    private suspend fun rescheduleUpcomingReminders() {
        val tasks = withContext(Dispatchers.IO) { repo.snapshot().second }
        val now = System.currentTimeMillis()
        tasks.filter { task -> !task.completed && task.reminderAt?.let { it > now } == true }
            .forEach { task -> ReminderScheduler.schedule(appContext, task.id, task.title, task.reminderAt!!) }
    }
    class Factory(private val repo: TaskRepository, private val appContext: Context) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(repo, appContext) as T
    }
}

@Composable fun WispTheme(content: @Composable () -> Unit) {
    val dark = UiTheme.dark
    val scheme = if (dark) darkColorScheme(
        primary = Mint, secondary = Blue, background = Color.Black,
        surface = Color(0xFF161A18), onBackground = Color(0xFFF1F5F3), onSurface = Color(0xFFF1F5F3),
        onPrimary = Color.Black, primaryContainer = Color(0xFF173E31), onPrimaryContainer = Color(0xFFF1F5F3),
        surfaceVariant = Color(0xFF252C28), onSurfaceVariant = Color(0xFFBDC8C1)
    ) else lightColorScheme(
        primary = Color(0xFF007D61), secondary = Color(0xFF2866B9), background = Color(0xFFF5FAF8),
        surface = Color.White, onBackground = Color(0xFF17211E), onSurface = Color(0xFF17211E),
        onPrimary = Color.White, primaryContainer = Color(0xFFCFEEE0), onPrimaryContainer = Color(0xFF123A2D),
        surfaceVariant = Color(0xFFE9F2EF), onSurfaceVariant = Color(0xFF46574E)
    )
    val activity = LocalContext.current as? android.app.Activity
    SideEffect {
        activity?.window?.let { window ->
            val color = if (dark) AndroidColor.BLACK else AndroidColor.rgb(245, 250, 248)
            window.statusBarColor = color
            window.navigationBarColor = color
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(color))
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize(), color = scheme.background) { content() }
    }
}

@Composable fun WispAppUi() {
    val context = LocalContext.current
    val app = context.applicationContext as WispApp
    val vm: MainViewModel = viewModel(factory = MainViewModel.Factory(app.repository, app))
    val preferences = remember { context.getSharedPreferences("wisp_settings", 0) }
    val recovery = remember { RecoveryKeyManager(context) }
    var ready by remember { mutableStateOf(preferences.getBoolean("setup_v5_complete", false) && recovery.isConfirmed()) }
    if (!ready) SetupFlow(recovery, { phrase, uri, result ->
        vm.importArchive(phrase, { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Cannot open import file") }, result)
    }) {
        preferences.edit().putBoolean("setup_v5_complete", true).apply()
        ready = true
    } else Home(vm)
}

private sealed interface Destination {
    data object Dashboard : Destination
    data object Search : Destination
    data object Settings : Destination
    data class Project(val id: String) : Destination
    data class ListPage(val id: String) : Destination
    data class Smart(val type: SmartType) : Destination
}
private enum class SmartType(val title: String) { TODAY("Мой день"), IMPORTANT("Избранное"), PLANNED("Запланировано"), ALL("Все задачи") }

private fun Destination.route(): String = when (this) {
    Destination.Dashboard -> "home"
    Destination.Search -> "search"
    Destination.Settings -> "settings"
    is Destination.Project -> "project:$id"
    is Destination.ListPage -> "list:$id"
    is Destination.Smart -> "smart:${type.name}"
}
private fun destination(route: String): Destination = when {
    route == "search" -> Destination.Search
    route == "settings" -> Destination.Settings
    route.startsWith("project:") -> Destination.Project(route.substringAfter(':'))
    route.startsWith("list:") -> Destination.ListPage(route.substringAfter(':'))
    route.startsWith("smart:") -> Destination.Smart(SmartType.valueOf(route.substringAfter(':')))
    else -> Destination.Dashboard
}

@Composable private fun Home(vm: MainViewModel) {
    val context = LocalContext.current; val lists by vm.lists.collectAsState(); val projects by vm.projects.collectAsState()
    val history = rememberSaveable(saver = listSaver<SnapshotStateList<Destination>, String>(
        save = { stack -> stack.map { it.route() } },
        restore = { routes -> routes.map(::destination).toMutableStateList() }
    )) { mutableStateListOf<Destination>(Destination.Dashboard) }
    fun navigate(screen: Destination) { history.add(screen) }
    fun back() { if (history.size > 1) history.removeAt(history.lastIndex) }
    fun toggleTask(task: TaskEntity) {
        if (task.completed) {
            task.reminderAt?.takeIf { it > System.currentTimeMillis() }?.let {
                ReminderScheduler.schedule(context, task.id, task.title, it)
            }
        } else {
            ReminderScheduler.cancel(context, task.id)
        }
        vm.toggle(task)
    }
    BackHandler(history.size > 1) { back() }
    val screen = history.last()
    val stateHolder = rememberSaveableStateHolder()
    SelectionHost(vm, lists, projects, screen.route()) {
    stateHolder.SaveableStateProvider(screen.toString()) {
    when (screen) {
        Destination.Dashboard -> Dashboard(lists, projects, { navigate(Destination.ListPage(it)) }, { navigate(Destination.Project(it)) }, { navigate(Destination.Smart(it)) }, { navigate(Destination.Search) }, { navigate(Destination.Settings) }, vm::addList, vm::addProject, vm::deleteList, vm::deleteProject)
        Destination.Search -> SearchScreen(lists, ::back, { navigate(Destination.ListPage(it)) }, ::toggleTask, vm::favorite, { ReminderScheduler.cancel(context, it.id); vm.deleteTask(it) })
        Destination.Settings -> SettingsScreen(vm, ::back)
        is Destination.Project -> ProjectScreen(projects.firstOrNull { it.id == screen.id }, lists.filter { it.list.projectId == screen.id }, ::back, { navigate(Destination.ListPage(it)) }, { vm.addList(it, screen.id) }, vm::deleteList)
        is Destination.ListPage -> lists.firstOrNull { it.list.id == screen.id }?.let { list -> TaskScreen(list.list.title, list.tasks, ::back, { title, at -> vm.addTask(screen.id, title, at) { if (at != null) ReminderScheduler.schedule(context, it.id, it.title, at) } }, ::toggleTask, vm::favorite, { task, title, at -> ReminderScheduler.cancel(context, task.id); vm.save(task.copy(title = title, reminderAt = at)); if (at != null) ReminderScheduler.schedule(context, task.id, title, at) }, { ReminderScheduler.cancel(context, it.id); vm.deleteTask(it) }) } ?: PageScaffold(tr("Списки"), ::back, {}) { padding -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        is Destination.Smart -> {
            val all = lists.flatMap { it.tasks }; val tasks = when (screen.type) { SmartType.TODAY -> all.filterNot { it.completed }; SmartType.IMPORTANT -> all.filter { it.favorite }; SmartType.PLANNED -> all.filter { it.reminderAt != null }; SmartType.ALL -> all }
            TaskScreen(tr(screen.type.title), tasks, ::back, null, ::toggleTask, vm::favorite, { task, title, at -> ReminderScheduler.cancel(context, task.id); vm.save(task.copy(title = title, reminderAt = at)); if (at != null) ReminderScheduler.schedule(context, task.id, title, at) }, { ReminderScheduler.cancel(context, it.id); vm.deleteTask(it) })
        }
    }
    }
}

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun Dashboard(lists: List<ListWithTasks>, projects: List<ProjectEntity>, openList: (String) -> Unit, openProject: (String) -> Unit, smart: (SmartType) -> Unit, search: () -> Unit, settings: () -> Unit, addList: (String, String?) -> Unit, addProject: (String) -> Unit, deleteList: (String) -> Unit, deleteProject: (String) -> Unit) {
    var createList by remember { mutableStateOf(false) }; var createProject by remember { mutableStateOf(false) }; var pendingListDelete by remember { mutableStateOf<TaskListEntity?>(null) }; var pendingProjectDelete by remember { mutableStateOf<ProjectEntity?>(null) }
    val tasks = lists.flatMap { it.tasks }
    Scaffold(containerColor = Background, topBar = { if (LocalSelection.current.target != null) SelectionBar() else TopAppBar(title = { Column { Text("Wisp To Do", fontWeight = FontWeight.Bold); Text(tr("Спокойный порядок каждый день"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }, actions = { IconButton(search) { Icon(Icons.Outlined.Search, tr("Поиск")) }; IconButton(settings) { Icon(Icons.Outlined.Settings, tr("Настройки")) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)) }, floatingActionButton = { ExtendedFloatingActionButton(onClick = { createList = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text(tr("Новый список")) }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp, 12.dp, 18.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { SmartCard(tr("Мой день"), tasks.count { !it.completed }, Icons.Outlined.WbSunny, Color(0xFFF3B645), Modifier.weight(1f)) { smart(SmartType.TODAY) }; SmartCard(tr("Избранное"), tasks.count { it.favorite && !it.completed }, Icons.Outlined.Star, Color(0xFFE65B8A), Modifier.weight(1f)) { smart(SmartType.IMPORTANT) } } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { SmartCard(tr("Запланировано"), tasks.count { it.reminderAt != null && !it.completed }, Icons.Outlined.Event, Color(0xFF4E9A8A), Modifier.weight(1f)) { smart(SmartType.PLANNED) }; SmartCard(tr("Все задачи"), tasks.count { !it.completed }, Icons.Outlined.Home, Blue, Modifier.weight(1f)) { smart(SmartType.ALL) } } }
            item { SectionTitle(tr("Проекты"), projects.size) { createProject = true } }
            items(projects, key = { it.id }) { project -> ProjectRow(project, lists.count { it.list.projectId == project.id }, { openProject(project.id) }, { pendingProjectDelete = project }) }
            item { SectionTitle(tr("Мои списки"), lists.count { it.list.projectId == null }, null) }
            val loose = lists.filter { it.list.projectId == null }
            if (loose.isEmpty()) item { EmptyLists { createList = true } } else items(loose, key = { it.list.id }) { ListRow(it, { openList(it.list.id) }, { pendingListDelete = it.list }) }
        }
    }
    if (createList) ListDialog(projects, { createList = false }) { title, projectId -> addList(title, projectId); createList = false }
    if (createProject) NameDialog(tr("Новый проект"), tr("Название проекта"), { createProject = false }) { addProject(it); createProject = false }
    pendingListDelete?.let { entity -> ConfirmDelete(tr("Удалить список «%1\$s» и все его задачи?", entity.title), { pendingListDelete = null }) { deleteList(entity.id); pendingListDelete = null } }
    pendingProjectDelete?.let { entity -> ConfirmDelete(tr("Удалить проект «%1\$s»? Списки останутся без проекта.", entity.title), { pendingProjectDelete = null }) { deleteProject(entity.id); pendingProjectDelete = null } }
}

@Composable private fun SectionTitle(title: String, count: Int, add: (() -> Unit)?) = Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) { Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text("$count", color = MaterialTheme.colorScheme.onSurfaceVariant); if (add != null) IconButton(add) { Icon(Icons.Default.Add, tr("Добавить")) } }
@Composable private fun SmartCard(title: String, count: Int, icon: ImageVector, tint: Color, modifier: Modifier, click: () -> Unit) = Surface(modifier.height(112.dp).clickable(onClick = click), RoundedCornerShape(24.dp), MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) { Surface(shape = CircleShape, color = tint.copy(alpha = .13f)) { Icon(icon, null, Modifier.padding(8.dp).size(22.dp), tint) }; Row(verticalAlignment = Alignment.Bottom) { Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); Text("$count", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = tint) } } }

@Composable private fun ProjectRow(project: ProjectEntity, listCount: Int, click: () -> Unit, delete: () -> Unit) {
    val selection = LocalSelection.current
    val target = Selected.Project(project)
    Surface(Modifier.fillMaxWidth().combinedClickable(onClick = { if (selection.target != null) selection.target = target else click() }, onLongClickLabel = tr("Изменить"), onLongClick = { selection.target = target }), RoundedCornerShape(20.dp), if (selection.target?.key == target.key) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) { Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) { Surface(shape = RoundedCornerShape(14.dp), color = Blue.copy(alpha = .12f)) { Icon(Icons.Outlined.Folder, null, Modifier.padding(11.dp).size(22.dp), tint = Blue) }; Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(project.title, fontWeight = FontWeight.SemiBold); Text(tr("Списки: %1\$d", listCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
}
@Composable private fun ListRow(item: ListWithTasks, click: () -> Unit, delete: () -> Unit) {
    val selection = LocalSelection.current
    val target = Selected.ListItem(item.list)
    Surface(Modifier.fillMaxWidth().combinedClickable(onClick = { if (selection.target != null) selection.target = target else click() }, onLongClickLabel = tr("Изменить"), onLongClick = { selection.target = target }), RoundedCornerShape(20.dp), if (selection.target?.key == target.key) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) { Row(Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) { Surface(shape = RoundedCornerShape(14.dp), color = Mint.copy(alpha = .12f)) { Icon(Icons.AutoMirrored.Outlined.List, null, Modifier.padding(11.dp).size(22.dp), tint = Mint) }; Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(item.list.title, fontWeight = FontWeight.SemiBold); Text(tr("Активные задачи: %1\$d", item.tasks.count { !it.completed }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
}
@Composable private fun EmptyLists(create: () -> Unit) = Column(Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, null, Modifier.size(46.dp), tint = Mint); Text(tr("Создайте первый список"), fontWeight = FontWeight.SemiBold); TextButton(create) { Text(tr("Создать список")) } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ProjectScreen(project: ProjectEntity?, lists: List<ListWithTasks>, back: () -> Unit, navigate: (String) -> Unit, add: (String) -> Unit, deleteList: (String) -> Unit) {
    var dialog by remember { mutableStateOf(false) }; var pendingDelete by remember { mutableStateOf<TaskListEntity?>(null) }
    PageScaffold(project?.title ?: tr("Проект"), back, { FloatingActionButton({ dialog = true }) { Icon(Icons.Default.Add, null) } }) { padding -> LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { if (lists.isEmpty()) item { EmptyLists { dialog = true } }; items(lists, key = { it.list.id }) { ListRow(it, { navigate(it.list.id) }, { pendingDelete = it.list }) } } }
    if (dialog) NameDialog(tr("Новый список"), tr("Название списка"), { dialog = false }) { add(it); dialog = false }
    pendingDelete?.let { ConfirmDelete(tr("Удалить список «%1\$s» и все его задачи?", it.title), { pendingDelete = null }) { deleteList(it.id); pendingDelete = null } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun TaskScreen(title: String, tasks: List<TaskEntity>, back: () -> Unit, add: ((String, Long?) -> Unit)?, toggle: (TaskEntity) -> Unit, favorite: (TaskEntity) -> Unit, edit: (TaskEntity, String, Long?) -> Unit, delete: (TaskEntity) -> Unit) {
    var dialog by remember { mutableStateOf(false) }; var editing by remember { mutableStateOf<TaskEntity?>(null) }; var expanded by rememberSaveable { mutableStateOf(false) }; var pendingDelete by remember { mutableStateOf<TaskEntity?>(null) }; val active = tasks.filterNot { it.completed }; val done = tasks.filter { it.completed }
    PageScaffold(title, back, { if (add != null) FloatingActionButton({ dialog = true }) { Icon(Icons.Default.Add, tr("Новая задача")) } }) { padding -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { if (active.isEmpty()) item { EmptyTasks() }; items(active, key = { it.id }) { TaskRow(it, toggle, favorite, { editing = it }) { pendingDelete = it } }; if (done.isNotEmpty()) item { Surface(Modifier.fillMaxWidth().clickable { expanded = !expanded }, RoundedCornerShape(18.dp), MaterialTheme.colorScheme.surface) { Row(Modifier.padding(17.dp)) { Text(tr("Выполненные (%1\$d)", done.size), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null) } } }; if (expanded) items(done, key = { it.id }) { TaskRow(it, toggle, favorite, { editing = it }) { pendingDelete = it } } } }
    if (dialog && add != null) TaskDialog(null, { dialog = false }) { name, at -> add(name, at); dialog = false }
    editing?.let { task -> TaskDialog(task, { editing = null }) { name, at -> edit(task, name, at); editing = null } }
    pendingDelete?.let { ConfirmDelete(tr("Удалить задачу «%1\$s»?", it.title), { pendingDelete = null }) { delete(it); pendingDelete = null } }
}

@Composable private fun TaskRow(task: TaskEntity, toggle: (TaskEntity) -> Unit, favorite: (TaskEntity) -> Unit, edit: () -> Unit, delete: () -> Unit) {
    val selection = LocalSelection.current
    val target = Selected.Task(task)
    Surface(Modifier.fillMaxWidth().combinedClickable(onClick = { if (selection.target != null) selection.target = target }, onLongClickLabel = tr("Изменить"), onLongClick = { selection.target = target }), RoundedCornerShape(18.dp), if (selection.target?.key == target.key) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) { Row(Modifier.padding(start = 10.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) { Checkbox(task.completed, { toggle(task) }); Column(Modifier.weight(1f).padding(horizontal = 6.dp)) { Text(task.title, color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant else Ink, textDecoration = if (task.completed) TextDecoration.LineThrough else null); task.reminderAt?.let { Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, java.util.Locale.forLanguageTag(UiLanguage.tag)).format(it), style = MaterialTheme.typography.bodySmall, color = Mint) } }; IconButton({ favorite(task) }) { Icon(if (task.favorite) Icons.Filled.Star else Icons.Outlined.Star, tr("Избранное"), tint = if (task.favorite) Color(0xFFF2B53D) else MaterialTheme.colorScheme.onSurfaceVariant) } } }
}
@Composable private fun EmptyTasks() = Column(Modifier.fillMaxWidth().padding(vertical = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Outlined.TaskAlt, null, Modifier.size(56.dp), tint = Mint); Text(tr("Здесь пока спокойно"), fontWeight = FontWeight.SemiBold); Text(tr("Добавьте задачу, когда будете готовы"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun SearchScreen(lists: List<ListWithTasks>, back: () -> Unit, navigate: (String) -> Unit, toggle: (TaskEntity) -> Unit, favorite: (TaskEntity) -> Unit, delete: (TaskEntity) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }; var pendingDelete by remember { mutableStateOf<TaskEntity?>(null) }; val matchingLists = lists.filter { it.list.title.contains(query, true) }; val matchingTasks = lists.flatMap { it.tasks }.filter { it.title.contains(query, true) || it.note.contains(query, true) }
    Scaffold(containerColor = Background, topBar = { if (LocalSelection.current.target != null) SelectionBar() else TopAppBar(title = { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text(tr("Поиск списков и задач")) }, leadingIcon = { Icon(Icons.Outlined.Search, null) }) }, navigationIcon = { IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Назад")) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)) }) { padding -> LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { if (query.isNotBlank()) { item { Text(tr("Списки"), fontWeight = FontWeight.Bold) }; items(matchingLists, key = { it.list.id }) { ListRow(it, { navigate(it.list.id) }, {}) }; item { Text(tr("Задачи"), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp)) }; items(matchingTasks, key = { it.id }) { TaskRow(it, toggle, favorite, {}) { pendingDelete = it } }; if (matchingLists.isEmpty() && matchingTasks.isEmpty()) item { Text(tr("Ничего не найдено"), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp)) } } } }
    pendingDelete?.let { ConfirmDelete(tr("Удалить задачу «%1\$s»?", it.title), { pendingDelete = null }) { delete(it); pendingDelete = null } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun SettingsScreen(vm: MainViewModel, back: () -> Unit) {
    val context = LocalContext.current; val store = remember { NextcloudConfigStore(context) }; val recovery = remember { RecoveryKeyManager(context) }; val language = UiLanguage.name(); var languageDialog by remember { mutableStateOf(false) }; var configDialog by remember { mutableStateOf(false) }; var explainNotification by remember { mutableStateOf(false) }; var config by remember { mutableStateOf(store.load()) }; var status by rememberSaveable { mutableStateOf(0) }; var backupStatus by rememberSaveable { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) vm.exportArchive(recovery.phrase(), { bytes -> context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Cannot open export file") }) { ok -> backupStatus = if (ok) "Экспорт создан" else "Не удалось создать экспорт" }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importArchive(recovery.phrase(), { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Cannot open import file") }) { ok -> backupStatus = if (ok) "Данные импортированы" else "Не удалось импортировать файл" }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> backupStatus = if (granted) "Напоминания включены" else "Разрешение на уведомления не выдано" }
    PageScaffold(tr("Настройки"), back, {}) { padding -> LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ThemeSwitchRow() }
        item {
            Surface(Modifier.fillMaxWidth(), RoundedCornerShape(22.dp), MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tr("О приложении Wisp To Do"), fontWeight = FontWeight.SemiBold)
                    ExternalLinkRow(Icons.Outlined.Code, tr("Исходный код на GitLab")) { openExternalLink(context, "https://gitlab.com/wispapps") }
                    ExternalLinkRow(Icons.Outlined.Code, tr("Исходный код на GitHub")) { openExternalLink(context, "https://github.com/WispApps") }
                    ExternalLinkRow(Icons.Outlined.Description, tr("Лицензия GNU GPL")) { openExternalLink(context, "https://www.gnu.org/licenses/") }
                }
            }
        }
        item { SettingsCard(Icons.Outlined.Language, tr("Язык приложения"), language) { languageDialog = true } }
        item { Surface(Modifier.fillMaxWidth(), RoundedCornerShape(22.dp), MaterialTheme.colorScheme.surface) { Column(Modifier.padding(18.dp)) { Text(tr("Безопасность и резервная копия"), fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(10.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton({ exportLauncher.launch("WispToDo-backup.atodo.enc") }, Modifier.weight(1f)) { Icon(Icons.Outlined.FileUpload, null); Spacer(Modifier.width(6.dp)); Text(tr("Экспорт")) }; OutlinedButton({ importLauncher.launch(arrayOf("*/*")) }, Modifier.weight(1f)) { Icon(Icons.Outlined.FileDownload, null); Spacer(Modifier.width(6.dp)); Text(tr("Импорт")) } }; Text("AES-256-GCM", style = MaterialTheme.typography.bodySmall, color = Mint, modifier = Modifier.padding(top = 8.dp)); backupStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) } } } }
        item { SettingsCard(Icons.Outlined.Notifications, tr("Напоминания"), tr("Разрешить напоминания")) { if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) explainNotification = true else backupStatus = "Уведомления уже разрешены" } }
        item { Surface(Modifier.fillMaxWidth(), RoundedCornerShape(22.dp), MaterialTheme.colorScheme.surface) { Column(Modifier.padding(18.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.CloudSync, null, tint = Blue); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text("Nextcloud · E2E", fontWeight = FontWeight.SemiBold); Text(config?.username ?: tr("Не подключён"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; TextButton({ configDialog = true }) { Text(if (config == null) tr("Подключить") else tr("Изменить")) } }; if (config != null) { Spacer(Modifier.height(12.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button({ busy = true; vm.upload(config!!, recovery.phrase()) { status = if (it.startsWith("Ошибка:")) 2 else 1; busy = false } }, Modifier.weight(1f), enabled = !busy) { Text(tr("Загрузить")) }; OutlinedButton({ busy = true; vm.download(config!!, recovery.phrase()) { status = if (it.startsWith("Ошибка:")) 2 else 1; busy = false } }, Modifier.weight(1f), enabled = !busy) { Text(tr("Восстановить")) } }; if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp)); Text(tr(when (status) { 1 -> tr("Передача завершена"); 2 -> tr("Не удалось передать данные. Проверьте подключение, реквизиты и сид-фразу."); else -> tr("Синхронизация ещё не выполнялась") }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)); Text(tr("В облако отправляется только backup.atodo.enc"), style = MaterialTheme.typography.bodySmall, color = Mint) } } } }
    } }
    if (languageDialog) LanguageDialog(language, { languageDialog = false }) { UiLanguage.select(UiLanguage.tags[UiLanguage.names.indexOf(it)]); languageDialog = false }
    if (configDialog) NextcloudDialog(config, { configDialog = false }) { store.save(it); config = it; configDialog = false }
    if (explainNotification) AlertDialog(onDismissRequest = { explainNotification = false }, title = { Text(tr("Разрешение на уведомления")) }, text = { Text(tr("Android покажет системный запрос. При отказе вы останетесь в настройках и сможете включить разрешение позже.")) }, dismissButton = { TextButton({ explainNotification = false }) { Text(tr("Отмена")) } }, confirmButton = { Button({ explainNotification = false; notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text(tr("Продолжить")) } })
}

@Composable private fun NextcloudDialog(current: NextcloudConfig?, dismiss: () -> Unit, save: (NextcloudConfig) -> Unit) {
    var url by remember { mutableStateOf(current?.webDavUrl ?: "") }; var user by remember { mutableStateOf(current?.username ?: "") }; var password by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = dismiss, title = { Text(tr("Подключить Nextcloud")) }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(tr("Используйте полный WebDAV URL и отдельный пароль приложения."), style = MaterialTheme.typography.bodySmall); OutlinedTextField(url, { url = it }, label = { Text("WebDAV URL") }, singleLine = true); OutlinedTextField(user, { user = it }, label = { Text(tr("Логин")) }, singleLine = true); OutlinedTextField(password, { password = it }, label = { Text(tr("Пароль приложения")) }, singleLine = true) } }, dismissButton = { TextButton(dismiss) { Text(tr("Отмена")) } }, confirmButton = { Button({ save(NextcloudConfig(url, user, password.ifBlank { current?.appPassword ?: "" })) }, enabled = url.startsWith("https://") && user.isNotBlank() && (password.isNotBlank() || current != null)) { Text(tr("Сохранить")) } })
}

private fun openExternalLink(context: Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

@Composable private fun ExternalLinkRow(icon: ImageVector, label: String, click: () -> Unit) =
    TextButton(onClick = click, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, null, tint = Mint)
        Spacer(Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(1f))
        Icon(Icons.Outlined.OpenInNew, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }

@Composable private fun SettingsCard(icon: ImageVector, title: String, subtitle: String, click: () -> Unit) = Surface(Modifier.fillMaxWidth().clickable(onClick = click), RoundedCornerShape(22.dp), MaterialTheme.colorScheme.surface) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Mint); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) } }

@OptIn(ExperimentalMaterial3Api::class) @Composable private fun PageScaffold(title: String, back: () -> Unit, fab: @Composable () -> Unit, content: @Composable (PaddingValues) -> Unit) = Scaffold(containerColor = Background, topBar = { if (LocalSelection.current.target != null) SelectionBar() else TopAppBar({ Text(title, fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Назад")) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)) }, floatingActionButton = fab, content = content)

@Composable private fun NameDialog(title: String, hint: String, dismiss: () -> Unit, confirm: (String) -> Unit) { var value by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { OutlinedTextField(value, { value = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text(hint) }) }, dismissButton = { TextButton(dismiss) { Text(tr("Отмена")) } }, confirmButton = { Button({ confirm(value.trim()) }, enabled = value.isNotBlank()) { Text(tr("Создать")) } }) }
@Composable private fun ConfirmDelete(message: String, dismiss: () -> Unit, confirm: () -> Unit) = AlertDialog(onDismissRequest = dismiss, title = { Text(tr("Подтверждение")) }, text = { Text(message) }, dismissButton = { TextButton(dismiss) { Text(tr("Отмена")) } }, confirmButton = { Button(confirm, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(tr("Удалить")) } })

@Composable private fun ListDialog(projects: List<ProjectEntity>, dismiss: () -> Unit, confirm: (String, String?) -> Unit) {
    var value by remember { mutableStateOf("") }; var projectId by remember { mutableStateOf<String?>(null) }; var expanded by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(tr("Новый список")) }, text = { Column { OutlinedTextField(value, { value = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text(tr("Название списка")) }); Spacer(Modifier.height(12.dp)); Box { OutlinedButton({ expanded = true }, Modifier.fillMaxWidth()) { Text(projects.firstOrNull { it.id == projectId }?.title ?: tr("Без проекта"), Modifier.weight(1f)); Icon(Icons.Default.KeyboardArrowDown, null) }; DropdownMenu(expanded, { expanded = false }) { DropdownMenuItem({ Text(tr("Без проекта")) }, { projectId = null; expanded = false }); projects.forEach { project -> DropdownMenuItem({ Text(project.title) }, { projectId = project.id; expanded = false }) } } } } }, dismissButton = { TextButton(dismiss) { Text(tr("Отмена")) } }, confirmButton = { Button({ confirm(value.trim(), projectId) }, enabled = value.isNotBlank()) { Text(tr("Создать")) } })
}

@Composable private fun TaskDialog(task: TaskEntity?, dismiss: () -> Unit, confirm: (String, Long?) -> Unit) {
    val context = LocalContext.current; var value by remember(task?.id) { mutableStateOf(task?.title ?: "") }; var reminder by remember(task?.id) { mutableStateOf(task?.reminderAt) }; var explainPermission by remember { mutableStateOf(false) }; var permissionDenied by remember { mutableStateOf(false) }
    fun showDateAndTimePicker() {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = reminder ?: (System.currentTimeMillis() + 60_000L)
        }
        // A configuration-only Context has no Activity window token and crashes
        // Dialog.show() with BadTokenException. Keep the Activity as the base,
        // and override only locale/theme for the platform pickers.
        val pickerContext = android.view.ContextThemeWrapper(
            context,
            if (UiTheme.dark) android.R.style.Theme_DeviceDefault_Dialog_Alert else android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
        ).apply {
            applyOverrideConfiguration(
                android.content.res.Configuration(context.resources.configuration).apply {
                    setLocale(java.util.Locale.forLanguageTag(UiLanguage.tag))
                }
            )
        }
        val dateDialog = DatePickerDialog(pickerContext, { _, year, month, day ->
            calendar.set(year, month, day)
            val timeDialog = TimePickerDialog(pickerContext, { _, hour, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hour)
                calendar.set(Calendar.MINUTE, minute)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                reminder = calendar.timeInMillis
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true)
            timeDialog.show()
            timeDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).text = tr("Сохранить")
            timeDialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).text = tr("Отмена")
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
        dateDialog.show()
        val headerId = pickerContext.resources.getIdentifier("date_picker_header", "id", "android")
        if (headerId != 0) {
            dateDialog.findViewById<android.view.View>(headerId)?.visibility = android.view.View.GONE
        }
        dateDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).text = tr("Продолжить")
        dateDialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).text = tr("Отмена")
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> permissionDenied = !granted; if (granted || Build.VERSION.SDK_INT < 33) showDateAndTimePicker() }
    fun pickReminder() { if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) explainPermission = true else showDateAndTimePicker() }
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (task == null) tr("Новая задача") else tr("Изменить задачу")) }, text = { Column { OutlinedTextField(value, { value = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text(tr("Что нужно сделать?")) }); Spacer(Modifier.height(12.dp)); OutlinedButton(::pickReminder, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Notifications, null); Spacer(Modifier.width(8.dp)); Text(reminder?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, java.util.Locale.forLanguageTag(UiLanguage.tag)).format(it) } ?: tr("Добавить напоминание")) }; if (permissionDenied) Text(tr("Без разрешения Android уведомление не будет показано."), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall); if (reminder != null) TextButton({ reminder = null }) { Text(tr("Убрать напоминание")) } } }, dismissButton = { TextButton(dismiss) { Text(tr("Отмена")) } }, confirmButton = { Button({ confirm(value.trim(), reminder) }, enabled = value.isNotBlank()) { Text(if (task == null) tr("Создать") else tr("Сохранить")) } })
    if (explainPermission) AlertDialog(onDismissRequest = { explainPermission = false }, title = { Text(tr("Разрешение на уведомления")) }, text = { Text(tr("Android запросит разрешение на показ напоминаний. Если вы откажетесь, календарь не откроется и задача останется без уведомления.")) }, dismissButton = { TextButton({ explainPermission = false }) { Text(tr("Отмена")) } }, confirmButton = { Button({ explainPermission = false; notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text(tr("Продолжить")) } })
}

@Composable private fun LanguageDialog(selected: String, dismiss: () -> Unit, choose: (String) -> Unit) = AlertDialog(onDismissRequest = dismiss, title = { Text(tr("Язык приложения")) }, text = { LazyColumn { items(UiLanguage.names) { item -> Row(Modifier.fillMaxWidth().clickable { choose(item) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected == item, null); Spacer(Modifier.width(8.dp)); Text(item) } } } }, confirmButton = { TextButton(dismiss) { Text(tr("Готово")) } })

private sealed class Selected(val key: String, val title: String) {
    class Task(val value: TaskEntity) : Selected("task:${value.id}", value.title)
    class ListItem(val value: TaskListEntity) : Selected("list:${value.id}", value.title)
    class Project(val value: ProjectEntity) : Selected("project:${value.id}", value.title)
}
private class SelectionState {
    var target by mutableStateOf<Selected?>(null)
    var editing by mutableStateOf(false)
    var deleting by mutableStateOf(false)
    fun clear() { target = null; editing = false; deleting = false }
}
private val LocalSelection = staticCompositionLocalOf { SelectionState() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun SelectionBar() {
    val state = LocalSelection.current
    TopAppBar(
        title = { Text(state.target?.title.orEmpty(), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
        navigationIcon = { IconButton({ state.clear() }) { Icon(Icons.Default.Close, tr("Отмена")) } },
        actions = {
            IconButton({ state.editing = true }) { Icon(Icons.Outlined.Edit, tr("Изменить")) }
            IconButton({ state.deleting = true }) { Icon(Icons.Outlined.Delete, tr("Удалить")) }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    )
}

@Composable private fun SelectionHost(vm: MainViewModel, lists: List<ListWithTasks>, projects: List<ProjectEntity>, route: String, content: @Composable () -> Unit) {
    val state = remember(route) { SelectionState() }
    val context = LocalContext.current
    CompositionLocalProvider(LocalSelection provides state) { content() }
    // Registered after screen handlers so Back first leaves selection mode.
    BackHandler(state.target != null) { state.clear() }
    val selected = when (val target = state.target) {
        is Selected.Task -> lists.asSequence().flatMap { it.tasks.asSequence() }.firstOrNull { it.id == target.value.id }?.let { Selected.Task(it) }
        is Selected.ListItem -> lists.firstOrNull { it.list.id == target.value.id }?.let { Selected.ListItem(it.list) }
        is Selected.Project -> projects.firstOrNull { it.id == target.value.id }?.let { Selected.Project(it) }
        null -> null
    }
    LaunchedEffect(selected?.key) { if (selected == null) state.clear() }
    if (state.editing && selected != null) {
        when (selected) {
            is Selected.Task -> TaskDialog(selected.value, { state.editing = false }) { title, at ->
                ReminderScheduler.cancel(context, selected.value.id)
                vm.save(selected.value.copy(title = title, reminderAt = at))
                if (at != null) ReminderScheduler.schedule(context, selected.value.id, title, at)
                state.clear()
            }
            is Selected.ListItem -> RenameDialog(selected.title, { state.editing = false }) {
                vm.renameList(selected.value, it); state.clear()
            }
            is Selected.Project -> RenameDialog(selected.title, { state.editing = false }) {
                vm.renameProject(selected.value, it); state.clear()
            }
        }
    }
    if (state.deleting && selected != null) {
        val message = when (selected) {
            is Selected.Task -> tr("Удалить задачу «%1\$s»?", selected.title)
            is Selected.ListItem -> tr("Удалить список «%1\$s» и все его задачи?", selected.title)
            is Selected.Project -> tr("Удалить проект «%1\$s»? Списки останутся без проекта.", selected.title)
        }
        ConfirmDelete(message, { state.deleting = false }) {
            when (selected) {
                is Selected.Task -> { ReminderScheduler.cancel(context, selected.value.id); vm.deleteTask(selected.value) }
                is Selected.ListItem -> {
                    lists.firstOrNull { it.list.id == selected.value.id }?.tasks?.forEach { ReminderScheduler.cancel(context, it.id) }
                    vm.deleteList(selected.value.id)
                }
                is Selected.Project -> vm.deleteProject(selected.value.id)
            }
            state.clear()
        }
    }
}

@Composable private fun RenameDialog(initial: String, dismiss: () -> Unit, save: (String) -> Unit) {
    var value by rememberSaveable(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(tr("Изменить")) },
        text = { OutlinedTextField(value, { value = it }, Modifier.fillMaxWidth(), singleLine = true) },
        dismissButton = { TextButton(dismiss) { Text(tr("Отмена")) } },
        confirmButton = { Button({ save(value.trim()) }, enabled = value.isNotBlank()) { Text(tr("Сохранить")) } }
    )
}
