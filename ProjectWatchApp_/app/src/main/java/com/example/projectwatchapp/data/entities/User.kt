package com.example.projectwatchapp.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true)
    val userId: Long = 0,
    val username: String,
    val email: String,
    val passwordHash: String,
    val xp: Int = 0,
    val level: Int = 1,
    val createdAt: Long = System.currentTimeMillis()
)