package com.wisp.todo.data

import java.util.UUID

class TaskRepository(private val dao: WispDao) {
    val lists = dao.observeLists()
    val favorites = dao.observeFavorites()
    val projects = dao.observeProjects()
    suspend fun addList(title: String, projectId: String? = null) = dao.upsertList(TaskListEntity(UUID.randomUUID().toString(), title.trim(), projectId = projectId))
    suspend fun addTask(listId: String, title: String, reminderAt: Long? = null): TaskEntity {
        val task = TaskEntity(UUID.randomUUID().toString(), listId, title.trim(), reminderAt = reminderAt)
        dao.upsertTask(task)
        return task
    }
    suspend fun save(task: TaskEntity) = dao.upsertTask(task.copy(updatedAt = System.currentTimeMillis()))
    suspend fun deleteTask(task: TaskEntity) = dao.deleteTask(task)
    suspend fun renameList(list: TaskListEntity, title: String) { if (title.isNotBlank()) dao.renameList(list.id, title.trim()) }
    suspend fun renameProject(project: ProjectEntity, title: String) { if (title.isNotBlank()) dao.renameProject(project.id, title.trim()) }
    suspend fun deleteList(id: String) = dao.deleteList(id)
    suspend fun addProject(title: String) = dao.upsertProject(ProjectEntity(UUID.randomUUID().toString(), title.trim()))
    suspend fun deleteProject(id: String) { dao.detachProjectLists(id); dao.deleteProject(id) }
    suspend fun setBackground(list: TaskListEntity, uri: String?) = dao.upsertList(list.copy(backgroundUri = uri))
    suspend fun snapshot() = Triple(dao.allLists(), dao.allTasks(), dao.allProjects())
    suspend fun merge(lists: List<TaskListEntity>, tasks: List<TaskEntity>) {
        lists.forEach { dao.upsertList(it) }
        tasks.forEach { dao.upsertTask(it) }
    }
    suspend fun merge(projects: List<ProjectEntity>, lists: List<TaskListEntity>, tasks: List<TaskEntity>) {
        projects.forEach { dao.upsertProject(it) }
        lists.forEach { dao.upsertList(it) }
        tasks.forEach { dao.upsertTask(it) }
    }
}
