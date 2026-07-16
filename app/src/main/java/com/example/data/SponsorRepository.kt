package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.util.Calendar

class SponsorRepository(private val database: AppDatabase) {
    private val userDao = database.userDao()
    private val advertiserDao = database.advertiserDao()
    private val videoDao = database.videoDao()
    private val watchLogDao = database.watchLogDao()
    private val withdrawalDao = database.withdrawalDao()

    // Users
    fun getUserById(id: Int): Flow<UserEntity?> = userDao.getUserById(id)
    suspend fun getUserByIdOneShot(id: Int): UserEntity? = userDao.getUserByIdOneShot(id)
    fun getAllUsers(): Flow<List<UserEntity>> = userDao.getAllUsers()
    suspend fun updateUserProfile(user: UserEntity) = userDao.updateUser(user)
    suspend fun deleteUser(user: UserEntity) = userDao.deleteUser(user)

    // Auth
    suspend fun registerUser(username: String, email: String, passwordRaw: String, role: String = "USER"): Pair<Boolean, String> {
        if (username.isBlank() || email.isBlank() || passwordRaw.isBlank()) {
            return Pair(false, "Fields cannot be blank")
        }
        val existingUser = userDao.getUserByUsername(username)
        if (existingUser != null) {
            return Pair(false, "Username already exists")
        }
        val existingEmail = userDao.getUserByEmail(email)
        if (existingEmail != null) {
            return Pair(false, "Email already registered")
        }
        val user = UserEntity(
            username = username,
            email = email,
            passwordHash = passwordRaw, // Using simple plain password storage for prototype demo ease
            role = role,
            walletBalance = 0.0
        )
        userDao.insertUser(user)
        return Pair(true, "Registration successful!")
    }

    suspend fun loginUser(username: String, passwordRaw: String): UserEntity? {
        val user = userDao.getUserByUsername(username)
        if (user != null && user.passwordHash == passwordRaw) {
            return user
        }
        return null
    }

    // Advertisers
    val allAdvertisers: Flow<List<AdvertiserEntity>> = advertiserDao.getAllAdvertisers()
    suspend fun getAdvertisersOneShot(): List<AdvertiserEntity> = advertiserDao.getAdvertisersOneShot()
    suspend fun insertAdvertiser(advertiser: AdvertiserEntity) = advertiserDao.insertAdvertiser(advertiser)
    suspend fun updateAdvertiser(advertiser: AdvertiserEntity) = advertiserDao.updateAdvertiser(advertiser)
    suspend fun deleteAdvertiser(advertiser: AdvertiserEntity) = advertiserDao.deleteAdvertiser(advertiser)

    // Videos
    val allVideos: Flow<List<VideoEntity>> = videoDao.getAllVideos()
    suspend fun getVideosOneShot(): List<VideoEntity> = videoDao.getVideosOneShot()
    fun getVideoById(id: Int): Flow<VideoEntity?> = videoDao.getVideoById(id)
    suspend fun getVideoByIdOneShot(id: Int): VideoEntity? = videoDao.getVideoByIdOneShot(id)
    suspend fun insertVideo(video: VideoEntity) = videoDao.insertVideo(video)
    suspend fun updateVideo(video: VideoEntity) = videoDao.updateVideo(video)
    suspend fun deleteVideo(video: VideoEntity) = videoDao.deleteVideo(video)

    // Logs
    fun getWatchLogsForUser(userId: Int): Flow<List<WatchLogEntity>> = watchLogDao.getLogsForUser(userId)
    fun getAllWatchLogs(): Flow<List<WatchLogEntity>> = watchLogDao.getAllLogs()

    suspend fun getWatchCountInLast24Hours(userId: Int): Int {
        val past24Hours = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
        val logs = watchLogDao.getLogsForUserInLast24Hours(userId, past24Hours)
        return logs.size
    }

    // Record Watch & Earn Reward (Transaction Simulation)
    suspend fun recordVideoWatch(userId: Int, videoId: Int): Pair<Boolean, String> {
        val user = userDao.getUserByIdOneShot(userId) ?: return Pair(false, "User not found")
        val video = videoDao.getVideoByIdOneShot(videoId) ?: return Pair(false, "Video not found")

        // Check if user exceeded 3 videos in 24 hours
        val past24Hours = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
        val logsToday = watchLogDao.getLogsForUserInLast24Hours(userId, past24Hours)
        if (logsToday.size >= 3) {
            return Pair(false, "Daily watch limit reached (Max 3 videos every 24 hours)")
        }

        // Record log
        val log = WatchLogEntity(
            userId = userId,
            videoId = videoId,
            earnedAmount = video.rewardAmount
        )
        watchLogDao.insertWatchLog(log)

        // Credit user wallet
        val updatedUser = user.copy(walletBalance = user.walletBalance + video.rewardAmount)
        userDao.updateUser(updatedUser)

        // Deduct remaining budget from advertiser if possible
        val advertiser = advertiserDao.getAdvertisersOneShot().find { it.id == video.advertiserId }
        if (advertiser != null) {
            val updatedBudget = (advertiser.remainingBudget - video.rewardAmount).coerceAtLeast(0.0)
            advertiserDao.updateAdvertiser(advertiser.copy(remainingBudget = updatedBudget))
        }

        return Pair(true, "Video watched! You earned $${String.format("%.2f", video.rewardAmount)}")
    }

    // Withdrawals
    fun getWithdrawalsForUser(userId: Int): Flow<List<WithdrawalRequestEntity>> = withdrawalDao.getWithdrawalsForUser(userId)
    fun getAllWithdrawals(): Flow<List<WithdrawalRequestEntity>> = withdrawalDao.getAllWithdrawals()

    suspend fun requestWithdrawal(userId: Int, amount: Double, method: String, details: String): Pair<Boolean, String> {
        if (amount < 5.0) {
            return Pair(false, "Minimum withdrawal amount is $5.00")
        }
        val user = userDao.getUserByIdOneShot(userId) ?: return Pair(false, "User not found")
        if (user.walletBalance < amount) {
            return Pair(false, "Insufficient balance. Your balance is $${String.format("%.2f", user.walletBalance)}")
        }

        // Deduct immediately (hold in escrow)
        val updatedUser = user.copy(walletBalance = user.walletBalance - amount)
        userDao.updateUser(updatedUser)

        // Create withdrawal request with SUBMITTED status
        val request = WithdrawalRequestEntity(
            userId = userId,
            username = user.username,
            amount = amount,
            paymentMethod = method,
            accountDetails = details,
            status = "SUBMITTED"
        )
        withdrawalDao.insertWithdrawal(request)
        return Pair(true, "Withdrawal request submitted! $${String.format("%.2f", amount)} is pending review.")
    }

    suspend fun updateWithdrawalStatus(withdrawal: WithdrawalRequestEntity, newStatus: String): Pair<Boolean, String> {
        val currentStatus = withdrawal.status
        if (currentStatus == newStatus) {
            return Pair(false, "Status is already $newStatus")
        }
        if (currentStatus == "PAID" || currentStatus == "REJECTED") {
            return Pair(false, "Cannot modify a transaction that is already $currentStatus")
        }

        val updated = withdrawal.copy(status = newStatus)
        withdrawalDao.updateWithdrawal(updated)

        // If transitioning to REJECTED, refund user
        if (newStatus == "REJECTED") {
            val user = userDao.getUserByIdOneShot(withdrawal.userId)
            if (user != null) {
                userDao.updateUser(user.copy(walletBalance = user.walletBalance + withdrawal.amount))
            }
        }
        return Pair(true, "Withdrawal status updated to $newStatus.")
    }

    suspend fun approveWithdrawal(withdrawal: WithdrawalRequestEntity) {
        updateWithdrawalStatus(withdrawal, "APPROVED")
    }

    suspend fun rejectWithdrawal(withdrawal: WithdrawalRequestEntity) {
        updateWithdrawalStatus(withdrawal, "REJECTED")
    }

    // Seeding dummy data
    suspend fun seedDatabaseIfEmpty() {
        // Check if admin exists
        val adminUser = userDao.getUserByUsername("admin")
        if (adminUser == null) {
            // Seed Admin
            userDao.insertUser(
                UserEntity(
                    username = "admin",
                    email = "admin@sponsorrewards.com",
                    passwordHash = "admin123",
                    role = "ADMIN"
                )
            )
            // Seed a standard user too
            userDao.insertUser(
                UserEntity(
                    username = "demo",
                    email = "demo@sponsorrewards.com",
                    passwordHash = "demo123",
                    role = "USER",
                    walletBalance = 1.20 // seed with some existing balance
                )
            )
        }

        // Check if advertisers exist
        val advertisers = advertiserDao.getAdvertisersOneShot()
        if (advertisers.isEmpty()) {
            val nikeId = advertiserDao.insertAdvertiser(
                AdvertiserEntity(
                    name = "Apex Athletics",
                    industry = "Sports & Fitness",
                    contactEmail = "ads@apexathletics.com",
                    totalBudget = 1000.00,
                    remainingBudget = 985.50
                )
            ).toInt()

            val teslaId = advertiserDao.insertAdvertiser(
                AdvertiserEntity(
                    name = "Volo Electric Motors",
                    industry = "Automotive",
                    contactEmail = "marketing@vololectric.com",
                    totalBudget = 5000.00,
                    remainingBudget = 4995.00
                )
            ).toInt()

            val starbucksId = advertiserDao.insertAdvertiser(
                AdvertiserEntity(
                    name = "Beanstalk Coffee",
                    industry = "Food & Beverages",
                    contactEmail = "campaigns@beanstalk.com",
                    totalBudget = 500.00,
                    remainingBudget = 480.00
                )
            ).toInt()

            val codecraftersId = advertiserDao.insertAdvertiser(
                AdvertiserEntity(
                    name = "ByteAcademy",
                    industry = "Education",
                    contactEmail = "learn@byteacademy.com",
                    totalBudget = 250.00,
                    remainingBudget = 250.00
                )
            ).toInt()

            // Seed sponsored videos
            val videos = videoDao.getVideosOneShot()
            if (videos.isEmpty()) {
                videoDao.insertVideo(
                    VideoEntity(
                        title = "Unleash Your Limits with Beanstalk Cold Brew",
                        description = "Fuel your daily grind with the all-new 100% Organic Espresso Cold Brew. Rich, creamy, and loaded with performance-boosting electrolytes.",
                        advertiserId = starbucksId,
                        advertiserName = "Beanstalk Coffee",
                        rewardAmount = 0.50,
                        durationSeconds = 15,
                        category = "Food & Drink"
                    )
                )

                videoDao.insertVideo(
                    VideoEntity(
                        title = "Volo Horizon: Redefining Electric Autonomy",
                        description = "Explore the future of highway cruising. Experience zero-emissions autopilot navigation with a stunning 480-mile driving range.",
                        advertiserId = teslaId,
                        advertiserName = "Volo Electric Motors",
                        rewardAmount = 1.25,
                        durationSeconds = 30,
                        category = "Technology"
                    )
                )

                videoDao.insertVideo(
                    VideoEntity(
                        title = "Apex Elite Run Trainer: Step Into Tomorrow",
                        description = "Achieve your personal best. The revolutionary dual-carbon sole delivers 80% maximum energy return for marathon endurance runners.",
                        advertiserId = nikeId,
                        advertiserName = "Apex Athletics",
                        rewardAmount = 0.75,
                        durationSeconds = 20,
                        category = "Sports"
                    )
                )

                videoDao.insertVideo(
                    VideoEntity(
                        title = "Master Kotlin & Jetpack Compose in 4 Weeks",
                        description = "Transform your career today. Enroll in ByteAcademy's expert-guided native Android development program and build 10+ live apps.",
                        advertiserId = codecraftersId,
                        advertiserName = "ByteAcademy",
                        rewardAmount = 1.50,
                        durationSeconds = 30,
                        category = "Education"
                    )
                )
            }
        }
    }
}
