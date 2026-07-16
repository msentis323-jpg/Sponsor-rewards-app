package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): UserEntity?

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id")
    fun getUserById(id: Int): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getUserByIdOneShot(id: Int): UserEntity?

    @Query("SELECT * FROM users ORDER BY registerTime DESC")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUser(user: UserEntity): Long

    @Update
    suspend fun updateUser(user: UserEntity)

    @Delete
    suspend fun deleteUser(user: UserEntity)
}

@Dao
interface AdvertiserDao {
    @Query("SELECT * FROM advertisers ORDER BY name ASC")
    fun getAllAdvertisers(): Flow<List<AdvertiserEntity>>

    @Query("SELECT * FROM advertisers ORDER BY name ASC")
    suspend fun getAdvertisersOneShot(): List<AdvertiserEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAdvertiser(advertiser: AdvertiserEntity): Long

    @Update
    suspend fun updateAdvertiser(advertiser: AdvertiserEntity)

    @Delete
    suspend fun deleteAdvertiser(advertiser: AdvertiserEntity)
}

@Dao
interface VideoDao {
    @Query("SELECT * FROM videos ORDER BY id DESC")
    fun getAllVideos(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos ORDER BY id DESC")
    suspend fun getVideosOneShot(): List<VideoEntity>

    @Query("SELECT * FROM videos WHERE id = :id")
    fun getVideoById(id: Int): Flow<VideoEntity?>

    @Query("SELECT * FROM videos WHERE id = :id")
    suspend fun getVideoByIdOneShot(id: Int): VideoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: VideoEntity): Long

    @Update
    suspend fun updateVideo(video: VideoEntity)

    @Delete
    suspend fun deleteVideo(video: VideoEntity)
}

@Dao
interface WatchLogDao {
    @Query("SELECT * FROM watch_logs WHERE userId = :userId ORDER BY watchedAt DESC")
    fun getLogsForUser(userId: Int): Flow<List<WatchLogEntity>>

    @Query("SELECT * FROM watch_logs WHERE userId = :userId AND watchedAt >= :sinceTimestamp")
    suspend fun getLogsForUserInLast24Hours(userId: Int, sinceTimestamp: Long): List<WatchLogEntity>

    @Query("SELECT * FROM watch_logs ORDER BY watchedAt DESC")
    fun getAllLogs(): Flow<List<WatchLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchLog(watchLog: WatchLogEntity): Long
}

@Dao
interface WithdrawalDao {
    @Query("SELECT * FROM withdrawals WHERE userId = :userId ORDER BY requestedAt DESC")
    fun getWithdrawalsForUser(userId: Int): Flow<List<WithdrawalRequestEntity>>

    @Query("SELECT * FROM withdrawals WHERE userId = :userId ORDER BY requestedAt DESC")
    suspend fun getWithdrawalsForUserOneShot(userId: Int): List<WithdrawalRequestEntity>

    @Query("SELECT * FROM withdrawals ORDER BY requestedAt DESC")
    fun getAllWithdrawals(): Flow<List<WithdrawalRequestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWithdrawal(withdrawal: WithdrawalRequestEntity): Long

    @Update
    suspend fun updateWithdrawal(withdrawal: WithdrawalRequestEntity)
}
