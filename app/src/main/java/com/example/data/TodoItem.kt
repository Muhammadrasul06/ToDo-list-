package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "todo_items")
data class TodoItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val notes: String = "",
    val category: String = "Inbox", // Inbox, Work, Home, Projects, etc.
    val isCompleted: Boolean = false,
    val isUrgent: Boolean = false,
    val dueDate: Long? = null,
    val reminderTime: Long? = null,
    val syncStatus: String = "pending", // pending, synced
    val lastUpdated: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)
