package com.wisp.todo.data

import androidx.room.*

@Entity(tableName = "task_lists")
data class TaskListEntity(@PrimaryKey val id: String, val title: String, val color: Long = 0xFF27CFA3, val backgroundUri: String? = null, val createdAt: Long = System.currentTimeMillis(), val projectId: String? = null)

@Entity(tableName = "projects")
data class ProjectEntity(@PrimaryKey val id: String, val title: String, val createdAt: Long = System.currentTimeMillis())

@Entity(tableName = "tasks", foreignKeys = [ForeignKey(entity = TaskListEntity::class, parentColumns = ["id"], childColumns = ["listId"], onDelete = ForeignKey.CASCADE)], indices = [Index("listId")])
data class TaskEntity(@PrimaryKey val id: String, val listId: String, val title: String, val note: String = "", val completed: Boolean = false, val favorite: Boolean = false, val reminderAt: Long? = null, val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = System.currentTimeMillis())

data class ListWithTasks(@Embedded val list: TaskListEntity, @Relation(parentColumn = "id", entityColumn = "listId") val tasks: List<TaskEntity>)
