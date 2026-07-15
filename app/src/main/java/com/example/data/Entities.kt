package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,
    val email: String,
    val passwordHash: String,
    val role: String, // "USER" or "ADMIN"
    val walletBalance: Double = 0.0,
    val registerTime: Long = System.currentTimeMillis()
)

@Entity(tableName = "advertisers")
data class AdvertiserEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val industry: String,
    val contactEmail: String,
    val totalBudget: Double,
    val remainingBudget: Double
)

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val advertiserId: Int,
    val advertiserName: String,
    val rewardAmount: Double,
    val durationSeconds: Int,
    val category: String // "Tech", "Entertainment", "Finance", "Lifestyle", etc.
)

@Entity(tableName = "watch_logs")
data class WatchLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val videoId: Int,
    val watchedAt: Long = System.currentTimeMillis(),
    val earnedAmount: Double
)

@Entity(tableName = "withdrawals")
data class WithdrawalRequestEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val username: String,
    val amount: Double,
    val paymentMethod: String, // "PayPal", "Bank Transfer", "Gift Card"
    val accountDetails: String,
    val status: String = "PENDING", // "PENDING", "APPROVED", "REJECTED"
    val requestedAt: Long = System.currentTimeMillis()
)
