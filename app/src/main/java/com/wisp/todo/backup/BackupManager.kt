package com.wisp.todo.backup

import com.wisp.todo.data.*
import com.wisp.todo.security.RecoveryKeyManager
import org.json.JSONArray
import org.json.JSONObject

object BackupManager {
    fun encode(projects: List<ProjectEntity>, lists: List<TaskListEntity>, tasks: List<TaskEntity>, phrase: String): ByteArray {
        val root = JSONObject().put("version", 2)
            .put("projects", JSONArray().apply { projects.forEach { put(JSONObject().put("id", it.id).put("title", it.title).put("createdAt", it.createdAt)) } })
            .put("lists", JSONArray().apply { lists.forEach { put(JSONObject().put("id", it.id).put("title", it.title).put("color", it.color).put("backgroundUri", it.backgroundUri).put("createdAt", it.createdAt).put("projectId", it.projectId)) } })
            .put("tasks", JSONArray().apply { tasks.forEach { put(JSONObject().put("id", it.id).put("listId", it.listId).put("title", it.title).put("note", it.note).put("completed", it.completed).put("favorite", it.favorite).put("reminderAt", it.reminderAt).put("createdAt", it.createdAt).put("updatedAt", it.updatedAt)) } })
        return RecoveryKeyManager.encrypt(root.toString().toByteArray(Charsets.UTF_8), phrase)
    }
    fun decode(bytes: ByteArray, phrase: String): Triple<List<ProjectEntity>, List<TaskListEntity>, List<TaskEntity>> {
        val root = JSONObject(RecoveryKeyManager.decrypt(bytes, phrase).decodeToString())
        require(root.optInt("version") == 2) { "Unsupported Wisp To Do backup" }
        val projects = root.getJSONArray("projects").let { a -> (0 until a.length()).map { a.getJSONObject(it).run { ProjectEntity(getString("id"), getString("title"), getLong("createdAt")) } } }
        val lists = root.getJSONArray("lists").let { a -> (0 until a.length()).map { a.getJSONObject(it).run { TaskListEntity(getString("id"), getString("title"), getLong("color"), optString("backgroundUri").takeIf(String::isNotBlank), getLong("createdAt"), optString("projectId").takeIf(String::isNotBlank)) } } }
        val tasks = root.getJSONArray("tasks").let { a -> (0 until a.length()).map { a.getJSONObject(it).run { TaskEntity(getString("id"), getString("listId"), getString("title"), optString("note"), getBoolean("completed"), getBoolean("favorite"), if (isNull("reminderAt")) null else getLong("reminderAt"), getLong("createdAt"), getLong("updatedAt")) } } }
        return Triple(projects, lists, tasks)
    }
}
