package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = SponsorRepository(database)
    private val firebaseService = FirebaseService(application, database)

    // Current logged-in user state
    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    // Auth error/success messages
    private val _authStateMessage = MutableStateFlow<Pair<Boolean, String>?>(null) // Pair(isSuccess, message)
    val authStateMessage: StateFlow<Pair<Boolean, String>?> = _authStateMessage.asStateFlow()

    // Video watch statuses
    private val _dailyWatchesCount = MutableStateFlow(0)
    val dailyWatchesCount: StateFlow<Int> = _dailyWatchesCount.asStateFlow()

    // Loading states or transaction responses
    private val _operationMessage = MutableStateFlow<Pair<Boolean, String>?>(null)
    val operationMessage: StateFlow<Pair<Boolean, String>?> = _operationMessage.asStateFlow()

    // Real campaigns loaded from Firestore collection "campaigns"
    private val _campaignsList = MutableStateFlow<List<VideoEntity>>(emptyList())
    val campaignsList: StateFlow<List<VideoEntity>> = _campaignsList.asStateFlow()

    // Lists of records (for Admin or User)
    val allVideos: StateFlow<List<VideoEntity>> = repository.allVideos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAdvertisers: StateFlow<List<AdvertiserEntity>> = repository.allAdvertisers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allUsers: StateFlow<List<UserEntity>> = repository.getAllUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allWithdrawals: StateFlow<List<WithdrawalRequestEntity>> = repository.getAllWithdrawals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allWatchLogs: StateFlow<List<WatchLogEntity>> = repository.getAllWatchLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered lists for the logged-in user
    private val _userWithdrawals = MutableStateFlow<List<WithdrawalRequestEntity>>(emptyList())
    val userWithdrawals: StateFlow<List<WithdrawalRequestEntity>> = _userWithdrawals.asStateFlow()

    private val _userWatchLogs = MutableStateFlow<List<WatchLogEntity>>(emptyList())
    val userWatchLogs: StateFlow<List<WatchLogEntity>> = _userWatchLogs.asStateFlow()

    init {
        viewModelScope.launch {
            // Seed the database if empty on startup
            repository.seedDatabaseIfEmpty()
            // Load and cache real video campaigns from Firestore collection "campaigns"
            loadCampaigns()
        }
    }

    /**
     * Loads sponsored campaigns from the Firestore "campaigns" collection.
     * Keeps Room cache in sync so both Room and Firestore are updated.
     */
    fun loadCampaigns() {
        viewModelScope.launch {
            val list = firebaseService.loadCampaignsFromFirestore()
            _campaignsList.value = list
            // Mirror campaigns into local Room DB for offline support and reference key integrity
            for (v in list) {
                val existing = repository.getVideoByIdOneShot(v.id)
                if (existing == null) {
                    repository.insertVideo(v)
                } else {
                    repository.updateVideo(v.copy(id = existing.id)) // keep consistent local PK
                }
            }
        }
    }

    fun clearAuthStateMessage() {
        _authStateMessage.value = null
    }

    fun clearOperationMessage() {
        _operationMessage.value = null
    }

    // Authentication Actions - Email & Password with Firebase Auth
    fun login(email: String, passwordRaw: String) {
        viewModelScope.launch {
            if (firebaseService.isFirebaseReady()) {
                val result = firebaseService.loginUser(email, passwordRaw)
                if (result.first != null) {
                    val user = result.first!!
                    _currentUser.value = user
                    _authStateMessage.value = Pair(true, result.second)
                    loadUserData(user.id)
                } else {
                    // Try fallback local login using email or username just in case
                    val localUser = repository.loginUser(email, passwordRaw) ?: database.userDao().getUserByEmail(email)
                    if (localUser != null) {
                        _currentUser.value = localUser
                        _authStateMessage.value = Pair(true, "Welcome back, ${localUser.username}! (Offline Mode)")
                        loadUserData(localUser.id)
                    } else {
                        _authStateMessage.value = Pair(false, result.second)
                    }
                }
            } else {
                // Offline fallback login using username/email
                val localUser = repository.loginUser(email, passwordRaw) ?: database.userDao().getUserByEmail(email)
                if (localUser != null) {
                    _currentUser.value = localUser
                    _authStateMessage.value = Pair(true, "Welcome back, ${localUser.username}! (Offline Mode)")
                    loadUserData(localUser.id)
                } else {
                    _authStateMessage.value = Pair(false, "Invalid credentials (offline mode)")
                }
            }
        }
    }

    fun register(username: String, email: String, passwordRaw: String) {
        viewModelScope.launch {
            if (firebaseService.isFirebaseReady()) {
                val result = firebaseService.registerUser(username, email, passwordRaw)
                _authStateMessage.value = result
                if (result.first) {
                    // Fetch local synced user entity
                    val localUser = database.userDao().getUserByEmail(email)
                    if (localUser != null) {
                        _currentUser.value = localUser
                        loadUserData(localUser.id)
                    }
                }
            } else {
                // Offline registration fallback
                val result = repository.registerUser(username, email, passwordRaw)
                _authStateMessage.value = result
                if (result.first) {
                    val localUser = database.userDao().getUserByEmail(email)
                    if (localUser != null) {
                        _currentUser.value = localUser
                        loadUserData(localUser.id)
                    }
                }
            }
        }
    }

    fun logout() {
        _currentUser.value = null
        _dailyWatchesCount.value = 0
        _userWithdrawals.value = emptyList()
        _userWatchLogs.value = emptyList()
    }

    private fun loadUserData(userId: Int) {
        // Collect flows for the user specifically
        viewModelScope.launch {
            repository.getUserById(userId).collect { user ->
                _currentUser.value = user
            }
        }

        viewModelScope.launch {
            repository.getWithdrawalsForUser(userId).collect { list ->
                _userWithdrawals.value = list
            }
        }

        viewModelScope.launch {
            repository.getWatchLogsForUser(userId).collect { list ->
                _userWatchLogs.value = list
            }
        }

        updateDailyWatchCount(userId)
        loadCampaigns() // Ensure campaigns are fresh from Firestore
        
        // Sync withdrawals from Firestore
        viewModelScope.launch {
            firebaseService.syncWithdrawalsFromFirestore()
        }
    }

    fun updateDailyWatchCount(userId: Int) {
        viewModelScope.launch {
            val count = repository.getWatchCountInLast24Hours(userId)
            _dailyWatchesCount.value = count
        }
    }

    fun getVideoById(id: Int): Flow<VideoEntity?> = repository.getVideoById(id)

    // User Operations - Earn and Verify Watch
    fun watchVideo(videoId: Int, onComplete: () -> Unit) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val video = repository.getVideoByIdOneShot(videoId)
            if (video == null) {
                _operationMessage.value = Pair(false, "Campaign video not found")
                return@launch
            }

            // Check if user exceeded 3 videos in 24 hours
            val past24Hours = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
            val logsToday = database.watchLogDao().getLogsForUserInLast24Hours(user.id, past24Hours)
            if (logsToday.size >= 3) {
                _operationMessage.value = Pair(false, "Daily watch limit reached (Max 3 videos every 24 hours)")
                return@launch
            }

            // Reward the user on Firestore and Room after verified completed view
            val result = firebaseService.rewardUserAndLogWatch(user.email, video)
            _operationMessage.value = result
            if (result.first) {
                // Refresh local user state
                val updatedUser = database.userDao().getUserByIdOneShot(user.id)
                _currentUser.value = updatedUser
                updateDailyWatchCount(user.id)
                onComplete()
            }
        }
    }

    fun requestWithdrawal(amount: Double, method: String, details: String) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val result = repository.requestWithdrawal(user.id, amount, method, details)
            _operationMessage.value = result
            if (result.first) {
                // Fetch the latest withdrawal for this user from Room to sync it to Firestore
                val userWithdrawals = database.withdrawalDao().getWithdrawalsForUserOneShot(user.id)
                val latest = userWithdrawals.firstOrNull()
                if (latest != null) {
                    firebaseService.submitWithdrawalToFirestore(latest, user.email)
                }
            }
        }
    }

    fun updateProfile(email: String, passwordRaw: String) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            if (email.isBlank() || passwordRaw.isBlank()) {
                _operationMessage.value = Pair(false, "Profile fields cannot be blank")
                return@launch
            }
            val updated = user.copy(email = email, passwordHash = passwordRaw)
            repository.updateUserProfile(updated)
            _operationMessage.value = Pair(true, "Profile updated successfully!")
        }
    }

    // Admin Operations - Managing Advertisers
    fun addAdvertiser(name: String, industry: String, email: String, totalBudget: Double) {
        viewModelScope.launch {
            if (name.isBlank() || industry.isBlank() || email.isBlank() || totalBudget <= 0) {
                _operationMessage.value = Pair(false, "Please fill in all details with a valid budget")
                return@launch
            }
            val advertiser = AdvertiserEntity(
                name = name,
                industry = industry,
                contactEmail = email,
                totalBudget = totalBudget,
                remainingBudget = totalBudget
            )
            repository.insertAdvertiser(advertiser)
            _operationMessage.value = Pair(true, "Advertiser '$name' added successfully!")
        }
    }

    fun updateAdvertiser(advertiser: AdvertiserEntity) {
        viewModelScope.launch {
            repository.updateAdvertiser(advertiser)
            _operationMessage.value = Pair(true, "Advertiser details updated!")
        }
    }

    fun deleteAdvertiser(advertiser: AdvertiserEntity) {
        viewModelScope.launch {
            repository.deleteAdvertiser(advertiser)
            _operationMessage.value = Pair(true, "Advertiser deleted successfully.")
        }
    }

    // Admin Operations - Managing Videos
    fun addVideo(title: String, description: String, advertiserId: Int, advertiserName: String, reward: Double, duration: Int, category: String) {
        viewModelScope.launch {
            if (title.isBlank() || description.isBlank() || advertiserId == 0 || reward <= 0 || duration <= 0) {
                _operationMessage.value = Pair(false, "Please fill in all details with a valid reward and duration")
                return@launch
            }
            val video = VideoEntity(
                title = title,
                description = description,
                advertiserId = advertiserId,
                advertiserName = advertiserName,
                rewardAmount = reward,
                durationSeconds = duration,
                category = category
            )
            repository.insertVideo(video)
            _operationMessage.value = Pair(true, "Sponsored video added!")
        }
    }

    fun deleteVideo(video: VideoEntity) {
        viewModelScope.launch {
            repository.deleteVideo(video)
            _operationMessage.value = Pair(true, "Video removed successfully.")
        }
    }

    // South African Payment Provider service preparation
    val paymentProviderService = SouthAfricanPaymentProviderService()

    // Admin Operations - Managing Withdrawals & Workflow
    fun updateWithdrawalStatusByAdmin(withdrawal: WithdrawalRequestEntity, newStatus: String) {
        viewModelScope.launch {
            val result = repository.updateWithdrawalStatus(withdrawal, newStatus)
            _operationMessage.value = result
            
            if (result.first) {
                // Sync with Firestore
                firebaseService.updateWithdrawalStatusInFirestore(withdrawal.userId, withdrawal.requestedAt, newStatus)
                
                // If the new status is REJECTED, sync refunded balance to Firestore as well
                if (newStatus == "REJECTED") {
                    val user = repository.getUserByIdOneShot(withdrawal.userId)
                    if (user != null) {
                        firebaseService.updateUserBalanceInFirestore(user.email, user.walletBalance)
                    }
                }
                
                // If approved, prepare and simulate future integration with South African Payment Provider
                if (newStatus == "APPROVED") {
                    _operationMessage.value = Pair(true, "Simulating South African Payment Provider EFT payout...")
                    
                    // Generate realistic SA bank credentials for demonstration
                    val saBank = SABankName.values().random()
                    val accountDetails = BankAccountDetails(
                        bankName = saBank,
                        accountNumber = "12345" + (10000..99999).random().toString(),
                        branchCode = saBank.defaultBranchCode,
                        accountHolderName = withdrawal.username,
                        accountType = "Savings"
                    )
                    
                    val payoutResult = paymentProviderService.processInstantEFTPayout(
                        account = accountDetails,
                        usdAmount = withdrawal.amount,
                        reference = "WD-${withdrawal.id}"
                    )
                    
                    if (payoutResult.success) {
                        // Automatically update state to PAID upon successful South African payment provider mock processing
                        repository.updateWithdrawalStatus(withdrawal.copy(status = "APPROVED"), "PAID")
                        firebaseService.updateWithdrawalStatusInFirestore(withdrawal.userId, withdrawal.requestedAt, "PAID")
                        _operationMessage.value = Pair(true, "EFT payment of R${String.format("%.2f", payoutResult.zarAmount)} processed via Stitch/Ozow! Status set to PAID. Ref: ${payoutResult.providerReference}")
                    } else {
                        _operationMessage.value = Pair(false, "EFT payment failed: ${payoutResult.message}")
                    }
                }
            }
        }
    }

    fun approveWithdrawal(withdrawal: WithdrawalRequestEntity) {
        updateWithdrawalStatusByAdmin(withdrawal, "APPROVED")
    }

    fun rejectWithdrawal(withdrawal: WithdrawalRequestEntity) {
        updateWithdrawalStatusByAdmin(withdrawal, "REJECTED")
    }

    // Admin Operations - Managing Users
    fun updateUserBalanceByAdmin(userId: Int, newBalance: Double) {
        viewModelScope.launch {
            val user = repository.getUserByIdOneShot(userId)
            if (user != null) {
                repository.updateUserProfile(user.copy(walletBalance = newBalance))
                firebaseService.updateUserBalanceInFirestore(user.email, newBalance)
                _operationMessage.value = Pair(true, "User balance adjusted to $${String.format("%.2f", newBalance)}")
            }
        }
    }

    fun deleteUser(user: UserEntity) {
        viewModelScope.launch {
            if (user.username == "admin") {
                _operationMessage.value = Pair(false, "Cannot delete primary administrator")
                return@launch
            }
            repository.deleteUser(user)
            _operationMessage.value = Pair(true, "User deleted successfully.")
        }
    }
}
