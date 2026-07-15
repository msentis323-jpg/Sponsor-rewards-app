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
        }
    }

    fun clearAuthStateMessage() {
        _authStateMessage.value = null
    }

    fun clearOperationMessage() {
        _operationMessage.value = null
    }

    // Authentication Actions
    fun login(username: String, passwordRaw: String) {
        viewModelScope.launch {
            val user = repository.loginUser(username, passwordRaw)
            if (user != null) {
                _currentUser.value = user
                _authStateMessage.value = Pair(true, "Welcome back, ${user.username}!")
                loadUserData(user.id)
            } else {
                _authStateMessage.value = Pair(false, "Invalid username or password")
            }
        }
    }

    fun register(username: String, email: String, passwordRaw: String) {
        viewModelScope.launch {
            val result = repository.registerUser(username, email, passwordRaw)
            _authStateMessage.value = result
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
    }

    fun updateDailyWatchCount(userId: Int) {
        viewModelScope.launch {
            val count = repository.getWatchCountInLast24Hours(userId)
            _dailyWatchesCount.value = count
        }
    }

    fun getVideoById(id: Int): Flow<VideoEntity?> = repository.getVideoById(id)

    // User Operations
    fun watchVideo(videoId: Int, onComplete: () -> Unit) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val result = repository.recordVideoWatch(user.id, videoId)
            _operationMessage.value = result
            if (result.first) {
                // Refresh data
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

    // Admin Operations - Managing Withdrawals
    fun approveWithdrawal(withdrawal: WithdrawalRequestEntity) {
        viewModelScope.launch {
            repository.approveWithdrawal(withdrawal)
            _operationMessage.value = Pair(true, "Withdrawal approved and processed!")
        }
    }

    fun rejectWithdrawal(withdrawal: WithdrawalRequestEntity) {
        viewModelScope.launch {
            repository.rejectWithdrawal(withdrawal)
            _operationMessage.value = Pair(true, "Withdrawal rejected. Funds refunded to user.")
        }
    }

    // Admin Operations - Managing Users
    fun updateUserBalanceByAdmin(userId: Int, newBalance: Double) {
        viewModelScope.launch {
            val user = repository.getUserByIdOneShot(userId)
            if (user != null) {
                repository.updateUserProfile(user.copy(walletBalance = newBalance))
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
