package com.wisp.todo.sync

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.wisp.todo.data.*
import com.wisp.todo.security.RecoveryKeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class NextcloudConfig(val webDavUrl: String, val username: String, val appPassword: String)

class NextcloudConfigStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(context, "nextcloud_credentials", MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(), EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    fun load(): NextcloudConfig? {
        val url = prefs.getString("url", null) ?: return null
        return NextcloudConfig(url, prefs.getString("user", "") ?: "", prefs.getString("password", "") ?: "")
    }
    fun save(value: NextcloudConfig) { prefs.edit().putString("url", value.webDavUrl.trim()).putString("user", value.username.trim()).putString("password", value.appPassword).apply() }
    fun clear() { prefs.edit().clear().apply() }
}

class NextcloudSync(private val repository: TaskRepository, private val phrase: String) {
    private val client = OkHttpClient()
    suspend fun upload(config: NextcloudConfig): String = withContext(Dispatchers.IO) {
        val root = config.webDavUrl.trimEnd('/')
        val auth = Credentials.basic(config.username, config.appPassword)
        client.newCall(Request.Builder().url("$root/WispToDo").header("Authorization", auth).method("MKCOL", ByteArray(0).toRequestBody()).build()).execute().use { response ->
            if (!response.isSuccessful && response.code != 405) error("Nextcloud MKCOL: HTTP ${response.code}")
        }
        val (lists, tasks, projects) = repository.snapshot()
        val encrypted = RecoveryKeyManager.encrypt(CloudBackupCodec.encode(projects, lists, tasks), phrase)
        client.newCall(Request.Builder().url("$root/WispToDo/backup.atodo.enc").header("Authorization", auth).put(encrypted.toRequestBody("application/octet-stream".toMediaType())).build()).execute().use { response ->
            if (!response.isSuccessful) error("Nextcloud upload: HTTP ${response.code}")
        }
        "Зашифрованная копия загружена"
    }
    suspend fun download(config: NextcloudConfig): String = withContext(Dispatchers.IO) {
        val root = config.webDavUrl.trimEnd('/'); val auth = Credentials.basic(config.username, config.appPassword)
        val bytes = client.newCall(Request.Builder().url("$root/WispToDo/backup.atodo.enc").header("Authorization", auth).get().build()).execute().use { response ->
            if (!response.isSuccessful) error("Nextcloud download: HTTP ${response.code}")
            response.body?.bytes() ?: error("Nextcloud returned an empty file")
        }
        val decoded = CloudBackupCodec.decode(RecoveryKeyManager.decrypt(bytes, phrase))
        repository.merge(decoded.first, decoded.second, decoded.third)
        "Данные восстановлены и объединены"
    }
}

private object CloudBackupCodec {
    fun encode(projects: List<ProjectEntity>, lists: List<TaskListEntity>, tasks: List<TaskEntity>): ByteArray = JSONObject()
        .put("version", 2)
        .put("projects", JSONArray().apply { projects.forEach { put(JSONObject().put("id", it.id).put("title", it.title).put("createdAt", it.createdAt)) } })
        .put("lists", JSONArray().apply { lists.forEach { put(JSONObject().put("id", it.id).put("title", it.title).put("color", it.color).put("backgroundUri", it.backgroundUri).put("createdAt", it.createdAt).put("projectId", it.projectId)) } })
        .put("tasks", JSONArray().apply { tasks.forEach { put(JSONObject().put("id", it.id).put("listId", it.listId).put("title", it.title).put("note", it.note).put("completed", it.completed).put("favorite", it.favorite).put("reminderAt", it.reminderAt).put("createdAt", it.createdAt).put("updatedAt", it.updatedAt)) } })
        .toString().toByteArray()

    fun decode(data: ByteArray): Triple<List<ProjectEntity>, List<TaskListEntity>, List<TaskEntity>> {
        val root = JSONObject(data.decodeToString())
        val projects = root.optJSONArray("projects")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).run { ProjectEntity(getString("id"), getString("title"), getLong("createdAt")) } } } ?: emptyList()
        val lists = root.getJSONArray("lists").let { a -> (0 until a.length()).map { a.getJSONObject(it).run { TaskListEntity(getString("id"), getString("title"), getLong("color"), optString("backgroundUri").takeIf(String::isNotBlank), getLong("createdAt"), optString("projectId").takeIf(String::isNotBlank)) } } }
        val tasks = root.getJSONArray("tasks").let { a -> (0 until a.length()).map { a.getJSONObject(it).run { TaskEntity(getString("id"), getString("listId"), getString("title"), optString("note"), getBoolean("completed"), getBoolean("favorite"), if (isNull("reminderAt")) null else getLong("reminderAt"), getLong("createdAt"), getLong("updatedAt")) } } }
        return Triple(projects, lists, tasks)
    }
}
