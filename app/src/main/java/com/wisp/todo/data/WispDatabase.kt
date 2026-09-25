package com.wisp.todo.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao interface WispDao {
    @Transaction @Query("SELECT * FROM task_lists ORDER BY createdAt DESC") fun observeLists(): Flow<List<ListWithTasks>>
    @Query("SELECT * FROM tasks WHERE favorite = 1 ORDER BY completed, updatedAt DESC") fun observeFavorites(): Flow<List<TaskEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertList(value: TaskListEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertTask(value: TaskEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertProject(value: ProjectEntity)
    @Query("UPDATE task_lists SET title = :title WHERE id = :id") suspend fun renameList(id: String, title: String)
    @Query("UPDATE projects SET title = :title WHERE id = :id") suspend fun renameProject(id: String, title: String)
    @Delete suspend fun deleteTask(value: TaskEntity)
    @Query("DELETE FROM task_lists WHERE id = :id") suspend fun deleteList(id: String)
    @Query("DELETE FROM projects WHERE id = :id") suspend fun deleteProject(id: String)
    @Query("UPDATE task_lists SET projectId = NULL WHERE projectId = :projectId") suspend fun detachProjectLists(projectId: String)
    @Query("SELECT * FROM projects ORDER BY createdAt DESC") fun observeProjects(): Flow<List<ProjectEntity>>
    @Query("SELECT * FROM task_lists") suspend fun allLists(): List<TaskListEntity>
    @Query("SELECT * FROM tasks") suspend fun allTasks(): List<TaskEntity>
    @Query("SELECT * FROM projects") suspend fun allProjects(): List<ProjectEntity>
}

@Database(entities = [TaskListEntity::class, TaskEntity::class, ProjectEntity::class], version = 2, exportSchema = false)
abstract class WispDatabase : RoomDatabase() {
    abstract fun dao(): WispDao
    companion object {
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS projects (id TEXT NOT NULL, title TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("ALTER TABLE task_lists ADD COLUMN projectId TEXT")
            }
        }
        fun create(context: Context) = Room.databaseBuilder(context, WispDatabase::class.java, "wisp_todo.db").addMigrations(MIGRATION_1_2).build()
    }
}
