package com.example.projectwatchapp.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["userId"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("userId")]
)
data class Category(
    @PrimaryKey(autoGenerate = true)
    val categoryId: Long = 0,
    val userId: Long,
    val name: String,
    @ColumnInfo(defaultValue = "#FFBB86FC")
    val colorHex: String = "#FFBB86FC",
    val iconName: String = "default_icon"
)