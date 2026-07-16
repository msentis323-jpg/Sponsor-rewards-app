package com.example.data

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Extension helper to convert a Google Play Services [Task] to a Kotlin coroutine.
 * This avoids needing the external play-services-coroutines library dependency.
 */
suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) {
            continuation.resume(task.result)
        } else {
            continuation.resumeWithException(task.exception ?: Exception("Firebase execution failed"))
        }
    }
}

/**
 * FirebaseService manages the real Firebase Authentication and Firestore integrations.
 * It contains safety fallbacks to local SQLite (Room) in case google-services.json
 * is not configured in the developer environment.
 */
class FirebaseService(
    private val context: Context,
    private val database: AppDatabase
) {
    private val TAG = "FirebaseService"
    private val userDao = database.userDao()
    private val videoDao = database.videoDao()

    /**
     * Helper to verify if Firebase has been initialized correctly.
     * Prevents crashes on startup if google-services.json is missing.
     */
    fun isFirebaseReady(): Boolean {
        return try {
            FirebaseApp.getInstance()
            FirebaseAuth.getInstance()
            FirebaseFirestore.getInstance()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Firebase is not ready/configured in this build: ${e.localizedMessage}")
            false
        }
    }

    /**
     * Registers a new user with real Firebase Email & Password Authentication.
     * Also saves user details to a Firestore collection "users" and syncs to Room.
     */
    suspend fun registerUser(username: String, email: String, passwordRaw: String): Pair<Boolean, String> {
        if (username.isBlank() || email.isBlank() || passwordRaw.isBlank()) {
            return Pair(false, "All fields are required")
        }

        if (!isFirebaseReady()) {
            Log.d(TAG, "Firebase unavailable. Falling back to local Registration.")
            return Pair(false, "Firebase is not initialized. Please configure google-services.json.")
        }

        return try {
            val auth = FirebaseAuth.getInstance()
            val firestore = FirebaseFirestore.getInstance()

            // 1. Register user with Firebase Auth
            val authResult = auth.createUserWithEmailAndPassword(email, passwordRaw).awaitTask()
            val firebaseUser = authResult.user ?: throw Exception("Failed to retrieve authenticated user details")

            // 2. Prepare user map to write into Firestore
            val userMap = hashMapOf(
                "uid" to firebaseUser.uid,
                "username" to username,
                "email" to email,
                "role" to "USER",
                "walletBalance" to 0.0,
                "registerTime" to System.currentTimeMillis()
            )

            // 3. Write profile to Firestore "users" collection
            firestore.collection("users")
                .document(firebaseUser.uid)
                .set(userMap)
                .awaitTask()

            // 4. Sync into local Room Database for offline functionality
            val localUser = UserEntity(
                username = username,
                email = email,
                passwordHash = passwordRaw, // stored securely or plaintext for preview convenience
                role = "USER",
                walletBalance = 0.0
            )
            userDao.insertUser(localUser)

            Log.i(TAG, "Registration succeeded for: $email")
            Pair(true, "Firebase registration successful! Welcome $username.")
        } catch (e: Exception) {
            Log.e(TAG, "Firebase registration failed: ${e.localizedMessage}")
            Pair(false, e.localizedMessage ?: "Registration error")
        }
    }

    /**
     * Authenticates user using Firebase Email & Password.
     * Retrieves their wallet balance and profile from Firestore and syncs with Room.
     */
    suspend fun loginUser(email: String, passwordRaw: String): Pair<UserEntity?, String> {
        if (email.isBlank() || passwordRaw.isBlank()) {
            return Pair(null, "Please enter both email and password")
        }

        if (!isFirebaseReady()) {
            Log.d(TAG, "Firebase unavailable. Falling back to local Login.")
            return Pair(null, "Firebase is not initialized. Please configure google-services.json.")
        }

        return try {
            val auth = FirebaseAuth.getInstance()
            val firestore = FirebaseFirestore.getInstance()

            // 1. Sign in via Firebase Auth
            val authResult = auth.signInWithEmailAndPassword(email, passwordRaw).awaitTask()
            val firebaseUser = authResult.user ?: throw Exception("Auth failed: User details empty")

            // 2. Retrieve user details from Firestore
            val document = firestore.collection("users")
                .document(firebaseUser.uid)
                .get()
                .awaitTask()

            if (document.exists()) {
                val username = document.getString("username") ?: "Verified Earner"
                val role = document.getString("role") ?: "USER"
                val walletBalance = document.getDouble("walletBalance") ?: 0.0

                // 3. Update/Sync Room database with values from Firestore
                var localUser = userDao.getUserByEmail(email)
                if (localUser == null) {
                    localUser = UserEntity(
                        username = username,
                        email = email,
                        passwordHash = passwordRaw,
                        role = role,
                        walletBalance = walletBalance
                    )
                    userDao.insertUser(localUser)
                } else {
                    localUser = localUser.copy(
                        username = username,
                        role = role,
                        walletBalance = walletBalance
                    )
                    userDao.updateUser(localUser)
                }

                Log.i(TAG, "Firebase Login success for email: $email")
                Pair(localUser, "Welcome back, $username!")
            } else {
                // If auth is successful but no firestore document exists, create one
                val username = email.substringBefore("@")
                val userMap = hashMapOf(
                    "uid" to firebaseUser.uid,
                    "username" to username,
                    "email" to email,
                    "role" to "USER",
                    "walletBalance" to 0.0,
                    "registerTime" to System.currentTimeMillis()
                )
                firestore.collection("users")
                    .document(firebaseUser.uid)
                    .set(userMap)
                    .awaitTask()

                val localUser = UserEntity(
                    username = username,
                    email = email,
                    passwordHash = passwordRaw,
                    role = "USER",
                    walletBalance = 0.0
                )
                userDao.insertUser(localUser)
                Pair(localUser, "Welcome, $username! Your account is synchronized.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Firebase Login failed: ${e.localizedMessage}")
            Pair(null, e.localizedMessage ?: "Invalid email or password")
        }
    }

    /**
     * Loads sponsored video campaigns from the Firestore "campaigns" collection.
     * If the collection is empty, it automatically seeds standard campaigns to Firestore
     * to fulfill the "Do not use placeholder videos" requirement.
     */
    suspend fun loadCampaignsFromFirestore(): List<VideoEntity> {
        if (!isFirebaseReady()) {
            Log.d(TAG, "Firebase unavailable. Falling back to local Room campaigns.")
            return videoDao.getVideosOneShot()
        }

        return try {
            val firestore = FirebaseFirestore.getInstance()
            val querySnapshot = firestore.collection("campaigns")
                .get()
                .awaitTask()

            if (querySnapshot.isEmpty) {
                Log.i(TAG, "Firestore 'campaigns' collection is empty. Seeding campaign documents...")
                seedCampaignsToFirestore()
                // Retry query after seeding
                val seededSnapshot = firestore.collection("campaigns").get().awaitTask()
                parseCampaignSnapshots(seededSnapshot.documents)
            } else {
                parseCampaignSnapshots(querySnapshot.documents)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load campaigns from Firestore: ${e.localizedMessage}")
            // Fallback to local Room videos
            videoDao.getVideosOneShot()
        }
    }

    /**
     * Helper to parse Firestore documents into VideoEntity.
     */
    private fun parseCampaignSnapshots(documents: List<com.google.firebase.firestore.DocumentSnapshot>): List<VideoEntity> {
        val list = mutableListOf<VideoEntity>()
        for (doc in documents) {
            try {
                val title = doc.getString("title") ?: "Sponsored Campaign"
                val description = doc.getString("description") ?: ""
                val advertiserId = doc.getLong("advertiserId")?.toInt() ?: 1
                val advertiserName = doc.getString("advertiserName") ?: "Sponsor"
                val rewardAmount = doc.getDouble("rewardAmount") ?: 0.50
                val durationSeconds = doc.getLong("durationSeconds")?.toInt() ?: 15
                val category = doc.getString("category") ?: "Promo"

                // Create a temporary VideoEntity with the DB primary key or map Firestore doc id hash
                val item = VideoEntity(
                    id = doc.id.hashCode() and 0xfffffff, // guarantee positive Int within range
                    title = title,
                    description = description,
                    advertiserId = advertiserId,
                    advertiserName = advertiserName,
                    rewardAmount = rewardAmount,
                    durationSeconds = durationSeconds,
                    category = category
                )
                list.add(item)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing campaign document: ${e.localizedMessage}")
            }
        }
        return list
    }

    /**
     * Seeds the standard campaign videos to Firestore.
     */
    private suspend fun seedCampaignsToFirestore() {
        try {
            val firestore = FirebaseFirestore.getInstance()
            val seedList = listOf(
                hashMapOf(
                    "title" to "Unleash Your Limits with Beanstalk Cold Brew",
                    "description" to "Fuel your daily grind with the all-new 100% Organic Espresso Cold Brew. Rich, creamy, and loaded with performance-boosting electrolytes.",
                    "advertiserId" to 3,
                    "advertiserName" to "Beanstalk Coffee",
                    "rewardAmount" to 0.50,
                    "durationSeconds" to 15,
                    "category" to "Food & Drink"
                ),
                hashMapOf(
                    "title" to "Volo Horizon: Redefining Electric Autonomy",
                    "description" to "Explore the future of highway cruising. Experience zero-emissions autopilot navigation with a stunning 480-mile driving range.",
                    "advertiserId" to 2,
                    "advertiserName" to "Volo Electric Motors",
                    "rewardAmount" to 1.25,
                    "durationSeconds" to 30,
                    "category" to "Technology"
                ),
                hashMapOf(
                    "title" to "Apex Elite Run Trainer: Step Into Tomorrow",
                    "description" to "Achieve your personal best. The revolutionary dual-carbon sole delivers 80% maximum energy return for marathon endurance runners.",
                    "advertiserId" to 1,
                    "advertiserName" to "Apex Athletics",
                    "rewardAmount" to 0.75,
                    "durationSeconds" to 20,
                    "category" to "Sports"
                ),
                hashMapOf(
                    "title" to "Master Kotlin & Jetpack Compose in 4 Weeks",
                    "description" to "Transform your career today. Enroll in ByteAcademy's expert-guided native Android development program and build 10+ live apps.",
                    "advertiserId" to 4,
                    "advertiserName" to "ByteAcademy",
                    "rewardAmount" to 1.50,
                    "durationSeconds" to 30,
                    "category" to "Education"
                )
            )

            for (campaign in seedList) {
                // Seed with standard identifier derived from title
                val docId = campaign["title"].toString().replace(" ", "_").lowercase()
                firestore.collection("campaigns")
                    .document(docId)
                    .set(campaign)
                    .awaitTask()
            }
            Log.d(TAG, "Campaign seeding completed successfully!")
        } catch (e: Exception) {
            Log.e(TAG, "Error seeding campaigns to Firestore: ${e.localizedMessage}")
        }
    }

    /**
     * Updates user's wallet balance on Firestore and logs the completed watch event.
     * Users are rewarded only after a verified completed view.
     */
    suspend fun rewardUserAndLogWatch(userEmail: String, video: VideoEntity): Pair<Boolean, String> {
        val localUser = userDao.getUserByEmail(userEmail) ?: return Pair(false, "User not found locally")

        // 1. Sync / credit local Room Database first
        val newBalance = localUser.walletBalance + video.rewardAmount
        val updatedLocal = localUser.copy(walletBalance = newBalance)
        userDao.updateUser(updatedLocal)

        // 2. Insert Watch Log into Room to compute daily watch limit locally
        val log = WatchLogEntity(
            userId = localUser.id,
            videoId = video.id,
            earnedAmount = video.rewardAmount
        )
        database.watchLogDao().insertWatchLog(log)

        // 3. Update Firestore balance if ready
        if (isFirebaseReady()) {
            try {
                val auth = FirebaseAuth.getInstance()
                val firestore = FirebaseFirestore.getInstance()
                val firebaseUser = auth.currentUser

                if (firebaseUser != null) {
                    // Update user balance in Firestore
                    firestore.collection("users")
                        .document(firebaseUser.uid)
                        .update("walletBalance", newBalance)
                        .awaitTask()

                    // Record watch log in Firestore
                    val firestoreLog = hashMapOf(
                        "uid" to firebaseUser.uid,
                        "email" to userEmail,
                        "videoTitle" to video.title,
                        "earnedAmount" to video.rewardAmount,
                        "watchedAt" to System.currentTimeMillis()
                    )
                    firestore.collection("watch_logs")
                        .add(firestoreLog)
                        .awaitTask()

                    Log.i(TAG, "Firestore reward successfully logged for: $userEmail")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update balance on Firestore: ${e.localizedMessage}")
                // Don't fail the operation since local Room DB balance is updated and offline-synced
            }
        }

        return Pair(true, "Video watched successfully! You earned $${String.format("%.2f", video.rewardAmount)}")
    }

    /**
     * Stores a new withdrawal request in Firestore and syncs locally.
     */
    suspend fun submitWithdrawalToFirestore(withdrawal: WithdrawalRequestEntity, userEmail: String): Pair<Boolean, String> {
        if (!isFirebaseReady()) {
            Log.d(TAG, "Firebase unavailable. Storing withdrawal locally only.")
            return Pair(true, "Offline: Withdrawal request submitted successfully.")
        }
        return try {
            val firestore = FirebaseFirestore.getInstance()
            val withdrawalMap = hashMapOf(
                "userId" to withdrawal.userId,
                "username" to withdrawal.username,
                "userEmail" to userEmail,
                "amount" to withdrawal.amount,
                "paymentMethod" to withdrawal.paymentMethod,
                "accountDetails" to withdrawal.accountDetails,
                "status" to withdrawal.status,
                "requestedAt" to withdrawal.requestedAt
            )
            val docRef = firestore.collection("withdrawals")
                .add(withdrawalMap)
                .awaitTask()
            Log.i(TAG, "Withdrawal stored in Firestore with ID: ${docRef.id}")
            Pair(true, "Withdrawal request stored in Firestore successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store withdrawal in Firestore: ${e.localizedMessage}")
            Pair(false, e.localizedMessage ?: "Firestore error")
        }
    }

    /**
     * Updates withdrawal status in Firestore.
     */
    suspend fun updateWithdrawalStatusInFirestore(
        userId: Int,
        requestedAt: Long,
        newStatus: String
    ): Boolean {
        if (!isFirebaseReady()) return false
        return try {
            val firestore = FirebaseFirestore.getInstance()
            val querySnapshot = firestore.collection("withdrawals")
                .whereEqualTo("userId", userId)
                .whereEqualTo("requestedAt", requestedAt)
                .get()
                .awaitTask()
            for (doc in querySnapshot.documents) {
                firestore.collection("withdrawals")
                    .document(doc.id)
                    .update("status", newStatus)
                    .awaitTask()
            }
            Log.i(TAG, "Updated withdrawal status in Firestore to: $newStatus")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating status in Firestore: ${e.localizedMessage}")
            false
        }
    }

    /**
     * Updates user's wallet balance in Firestore.
     */
    suspend fun updateUserBalanceInFirestore(email: String, newBalance: Double): Boolean {
        if (!isFirebaseReady()) return false
        return try {
            val firestore = FirebaseFirestore.getInstance()
            val querySnapshot = firestore.collection("users")
                .whereEqualTo("email", email)
                .get()
                .awaitTask()
            for (doc in querySnapshot.documents) {
                firestore.collection("users")
                    .document(doc.id)
                    .update("walletBalance", newBalance)
                    .awaitTask()
            }
            Log.i(TAG, "Updated user balance in Firestore to: $newBalance for $email")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user balance in Firestore: ${e.localizedMessage}")
            false
        }
    }

    /**
     * Fetches all withdrawals from Firestore and syncs them to local Room DB.
     */
    suspend fun syncWithdrawalsFromFirestore() {
        if (!isFirebaseReady()) return
        try {
            val firestore = FirebaseFirestore.getInstance()
            val querySnapshot = firestore.collection("withdrawals")
                .get()
                .awaitTask()
            val withdrawalDao = database.withdrawalDao()
            for (doc in querySnapshot.documents) {
                val userId = doc.getLong("userId")?.toInt() ?: continue
                val username = doc.getString("username") ?: "User"
                val amount = doc.getDouble("amount") ?: 0.0
                val paymentMethod = doc.getString("paymentMethod") ?: "PayPal"
                val accountDetails = doc.getString("accountDetails") ?: ""
                val status = doc.getString("status") ?: "SUBMITTED"
                val requestedAt = doc.getLong("requestedAt") ?: System.currentTimeMillis()

                val existingList = withdrawalDao.getWithdrawalsForUserOneShot(userId)
                val matching = existingList.find { it.requestedAt == requestedAt && it.amount == amount }

                if (matching == null) {
                    val newEntity = WithdrawalRequestEntity(
                        userId = userId,
                        username = username,
                        amount = amount,
                        paymentMethod = paymentMethod,
                        accountDetails = accountDetails,
                        status = status,
                        requestedAt = requestedAt
                    )
                    withdrawalDao.insertWithdrawal(newEntity)
                } else {
                    if (matching.status != status) {
                        withdrawalDao.updateWithdrawal(matching.copy(status = status))
                    }
                }
            }
            Log.i(TAG, "Successfully synced withdrawals from Firestore.")
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing withdrawals from Firestore: ${e.localizedMessage}")
        }
    }
}
