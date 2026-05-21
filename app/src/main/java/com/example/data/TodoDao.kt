package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {
    @Query("SELECT * FROM todo_items WHERE isDeleted = 0 ORDER BY isUrgent DESC, isCompleted ASC, lastUpdated DESC")
    fun getAllActiveItems(): Flow<List<TodoItem>>

    @Query("SELECT * FROM todo_items WHERE isDeleted = 0 AND category = :category ORDER BY isUrgent DESC, isCompleted ASC, lastUpdated DESC")
    fun getActiveItemsByCategory(category: String): Flow<List<TodoItem>>

    @Query("SELECT * FROM todo_items WHERE id = :id")
    suspend fun getItemById(id: String): TodoItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(item: TodoItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(items: List<TodoItem>)

    @Query("SELECT * FROM todo_items WHERE syncStatus = 'pending'")
    suspend fun getPendingSyncItems(): List<TodoItem>

    @Query("SELECT * FROM todo_items")
    suspend fun getAllRawItems(): List<TodoItem>

    @Query("UPDATE todo_items SET isDeleted = 1, syncStatus = 'pending', lastUpdated = :timestamp WHERE id = :id")
    suspend fun softDeleteById(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM todo_items WHERE id = :id")
    suspend fun hardDeleteById(id: String)
}
