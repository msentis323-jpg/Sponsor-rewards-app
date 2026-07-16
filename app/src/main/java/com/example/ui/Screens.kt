package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

// Helper to format date
fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    viewModel: AppViewModel,
    onNavigateToVideo: (Int) -> Unit,
    activeVideoId: State<Int?>
) {
    val currentUser by viewModel.currentUser.collectAsState()
    var currentScreen by remember { mutableStateOf("login") }

    // Navigation interceptor for auth
    LaunchedEffect(currentUser) {
        if (currentUser == null) {
            currentScreen = "login"
        } else if (currentScreen == "login" || currentScreen == "register") {
            currentScreen = if (currentUser?.role == "ADMIN") "admin_dashboard" else "user_home"
        }
    }

    val authState by viewModel.authStateMessage.collectAsState()
    val operationState by viewModel.operationMessage.collectAsState()

    // Status snackbar alerts
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(authState) {
        authState?.let {
            snackbarHostState.showSnackbar(it.second)
            viewModel.clearAuthStateMessage()
        }
    }

    LaunchedEffect(operationState) {
        operationState?.let {
            snackbarHostState.showSnackbar(it.second)
            viewModel.clearOperationMessage()
        }
    }

    if (activeVideoId.value != null) {
        // Video overlay mode
        VideoPlayerScreen(
            videoId = activeVideoId.value!!,
            viewModel = viewModel,
            onDismiss = { onNavigateToVideo(-1) } // clear active video
        )
    } else {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (currentUser != null && currentScreen != "login" && currentScreen != "register") {
                    AppTopBar(
                        role = currentUser?.role ?: "USER",
                        username = currentUser?.username ?: "User"
                    )
                }
            },
            bottomBar = {
                if (currentUser != null) {
                    AppBottomBar(
                        role = currentUser?.role ?: "USER",
                        currentScreen = currentScreen,
                        onScreenSelect = { currentScreen = it }
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentScreen) {
                    "login" -> LoginScreen(
                        viewModel = viewModel,
                        onNavigateToRegister = { currentScreen = "register" }
                    )
                    "register" -> RegisterScreen(
                        viewModel = viewModel,
                        onNavigateToLogin = { currentScreen = "login" }
                    )
                    "user_home" -> UserHomeScreen(
                        viewModel = viewModel,
                        onWatchClick = { videoId -> onNavigateToVideo(videoId) },
                        onNavigateToScreen = { currentScreen = it }
                    )
                    "wallet" -> WalletScreen(viewModel = viewModel)
                    "profile" -> ProfileScreen(viewModel = viewModel)
                    "admin_dashboard" -> AdminDashboardScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun AppTopBar(
    role: String,
    username: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PureWhite,
        shadowElevation = 0.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Profile/Logo Badge with Circle shape bg-[#2E7D32]
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(ForestGreen, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "S",
                            color = PureWhite,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column {
                        Text(
                            text = "Sponsor Rewards",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Charcoal
                        )
                        Text(
                            text = if (role == "ADMIN") "SYSTEM ADMINISTRATOR" else "VERIFIED EARNER",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = ForestGreen,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                // Small right indicator icon/avatar
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(OffWhite)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "User Account",
                        tint = ForestGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Divider(color = DividerGray, thickness = 1.dp)
        }
    }
}

@Composable
fun AppBottomBar(
    role: String,
    currentScreen: String,
    onScreenSelect: (String) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        if (role == "ADMIN") {
            NavigationBarItem(
                selected = currentScreen == "admin_dashboard",
                onClick = { onScreenSelect("admin_dashboard") },
                icon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = "Admin") },
                label = { Text("Admin") },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = ForestGreen,
                    selectedTextColor = ForestGreen,
                    indicatorColor = LightMint
                )
            )
            NavigationBarItem(
                selected = currentScreen == "profile",
                onClick = { onScreenSelect("profile") },
                icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                label = { Text("Profile") },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = ForestGreen,
                    selectedTextColor = ForestGreen,
                    indicatorColor = LightMint
                )
            )
        } else {
            NavigationBarItem(
                selected = currentScreen == "user_home",
                onClick = { onScreenSelect("user_home") },
                icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Watch Ads") },
                label = { Text("Earn") },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = ForestGreen,
                    selectedTextColor = ForestGreen,
                    indicatorColor = LightMint
                )
            )
            NavigationBarItem(
                selected = currentScreen == "wallet",
                onClick = { onScreenSelect("wallet") },
                icon = { Icon(Icons.Default.Wallet, contentDescription = "Wallet") },
                label = { Text("Wallet") },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = ForestGreen,
                    selectedTextColor = ForestGreen,
                    indicatorColor = LightMint
                )
            )
            NavigationBarItem(
                selected = currentScreen == "profile",
                onClick = { onScreenSelect("profile") },
                icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                label = { Text("Profile") },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = ForestGreen,
                    selectedTextColor = ForestGreen,
                    indicatorColor = LightMint
                )
            )
        }
    }
}

// ==========================================
// AUTH SCREENS
// ==========================================

@Composable
fun LoginScreen(viewModel: AppViewModel, onNavigateToRegister: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(ForestGreen, DarkSlate),
                    startY = 0f,
                    endY = 1400f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = PureWhite)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Logo
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(GoldAccent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MonetizationOn,
                        contentDescription = "Dollar Logo",
                        tint = ForestGreen,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Sponsor Rewards",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Charcoal
                )

                Text(
                    text = "Watch, Earn, and Redeem Cash",
                    fontSize = 14.sp,
                    color = GrayText,
                    modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = ForestGreen) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("username_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ForestGreen,
                        focusedLabelColor = ForestGreen
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = ForestGreen) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("password_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ForestGreen,
                        focusedLabelColor = ForestGreen
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { viewModel.login(email, password) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("submit_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Sign In", color = PureWhite, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("New here? ", color = Charcoal)
                    Text(
                        text = "Create Account",
                        color = EmeraldGreen,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { onNavigateToRegister() }
                            .padding(4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                // Demo note
                Text(
                    text = "Tip: Log in with username 'admin' & 'admin123' for administrator control panel.",
                    fontSize = 11.sp,
                    color = ForestGreen,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun RegisterScreen(viewModel: AppViewModel, onNavigateToLogin: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(ForestGreen, DarkSlate),
                    startY = 0f,
                    endY = 1400f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = PureWhite)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(GoldAccent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AppRegistration,
                        contentDescription = "Register Logo",
                        tint = ForestGreen,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Get Started",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Charcoal
                )

                Text(
                    text = "Join Sponsor Rewards to earn cash",
                    fontSize = 14.sp,
                    color = GrayText,
                    modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = ForestGreen) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ForestGreen,
                        focusedLabelColor = ForestGreen
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = ForestGreen) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ForestGreen,
                        focusedLabelColor = ForestGreen
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = ForestGreen) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ForestGreen,
                        focusedLabelColor = ForestGreen
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        viewModel.register(username, email, password)
                        onNavigateToLogin() // Go to login after registering
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Register Now", color = PureWhite, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("Already registered? ", color = Charcoal)
                    Text(
                        text = "Sign In",
                        color = EmeraldGreen,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { onNavigateToLogin() }
                            .padding(4.dp)
                    )
                }
            }
        }
    }
}

// ==========================================
// USER HOME SCREEN
// ==========================================

@Composable
fun UserHomeScreen(
    viewModel: AppViewModel,
    onWatchClick: (Int) -> Unit,
    onNavigateToScreen: (String) -> Unit = {}
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val videos by viewModel.campaignsList.collectAsState()
    val dailyWatches by viewModel.dailyWatchesCount.collectAsState()

    // Dynamic countdown timer showing remaining time until the next daily reset (midnight of the next day)
    var countdownText by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        importJavaUtilAndRunTimer { text ->
            countdownText = text
        }
    }

    // Always fetch daily watch count and refresh campaigns on display
    LaunchedEffect(currentUser) {
        currentUser?.let {
            viewModel.updateDailyWatchCount(it.id)
            viewModel.loadCampaigns()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(OffWhite),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcome and Username Card
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Welcome Back,",
                        fontSize = 14.sp,
                        color = GrayText,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = currentUser?.username ?: "Verified Earner",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Charcoal
                    )
                }

                // Small quick indicator
                Box(
                    modifier = Modifier
                        .background(ForestGreen.copy(alpha = 0.1f), CircleShape)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(ForestGreen, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Firestore Sync",
                            color = ForestGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 1. Wallet Card (Charcoal high-contrast theme matching HTML bg-[#1C1B1F])
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSlate),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    // Decorative top-right circle representing the geometric balance accent
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = 16.dp, y = (-16).dp)
                            .background(GoldAccent.copy(alpha = 0.1f), CircleShape)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column {
                                Text(
                                    text = "AVAILABLE BALANCE",
                                    color = GrayText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "$${String.format("%.2f", currentUser?.walletBalance ?: 0.0)}",
                                    color = GoldAccent,
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Gold Member badge
                            Box(
                                modifier = Modifier
                                    .background(ForestGreen, RoundedCornerShape(20.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "GOLD MEMBER",
                                    color = PureWhite,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // High contrast buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { onNavigateToScreen("wallet") },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PureWhite,
                                    contentColor = DarkSlate
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Text(
                                    "Withdraw",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }

                            OutlinedButton(
                                onClick = { onNavigateToScreen("wallet") },
                                border = androidx.compose.foundation.BorderStroke(1.dp, PureWhite.copy(alpha = 0.3f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = PureWhite
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Text(
                                    "History",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Daily Activity / Reset Countdown Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = PureWhite),
                border = androidx.compose.foundation.BorderStroke(1.dp, DividerGray)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Daily Reset Countdown",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = GrayText
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = "Countdown Timer",
                                    tint = ForestGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = countdownText.ifEmpty { "00:00:00" },
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Charcoal
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Daily Limit",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = GrayText
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .background(LightMint, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "3 VIDEOS / 24 HRS",
                                    color = ForestGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Segmented/Visual representation of watches
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Today's Progress",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Charcoal
                        )

                        Text(
                            text = "$dailyWatches of 3 Completed",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (dailyWatches >= 3) ForestGreen else GoldAmber
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Progress Bar matching HTML design (h-3 bg-slate-100 rounded-full flex)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(CircleShape)
                            .background(DividerGray.copy(alpha = 0.5f))
                    ) {
                        val progressFraction = (dailyWatches / 3f).coerceIn(0f, 1f)
                        Row(modifier = Modifier.fillMaxSize()) {
                            if (progressFraction > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .weight(progressFraction)
                                        .background(ForestGreen)
                                )
                            }
                            if (progressFraction < 1f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .weight(1f - progressFraction)
                                        .background(DividerGray)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Dynamic watch helper or status banner
                    if (dailyWatches < 3) {
                        Button(
                            onClick = {
                                val uncompletedVideo = videos.firstOrNull()
                                if (uncompletedVideo != null) {
                                    onWatchClick(uncompletedVideo.id)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = PureWhite)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Watch Next Video", fontWeight = FontWeight.Bold, color = PureWhite)
                                }
                                Box(
                                    modifier = Modifier
                                        .background(PureWhite.copy(alpha = 0.2f), CircleShape)
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("+$0.75 avg", color = PureWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(LightMint, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ForestGreen)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Daily limit reached! Come back in $countdownText.",
                                color = ForestGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // 3. Campaign Header
        item {
            Text(
                text = "Sponsored Campaigns",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Charcoal,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // 4. Video cards list
        if (videos.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = PureWhite),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DividerGray)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.VideoLibrary,
                            contentDescription = null,
                            tint = GrayText,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No active campaigns right now.", color = GrayText)
                    }
                }
            }
        } else {
            items(videos) { video ->
                SponsoredVideoItemCard(
                    video = video,
                    isQuotaFull = dailyWatches >= 3,
                    onWatch = { onWatchClick(video.id) }
                )
            }
        }
    }
}

/**
 * Clean inline helper to calculate countdown until midnight and execute dynamic updates
 */
private suspend fun importJavaUtilAndRunTimer(onUpdate: (String) -> Unit) {
    while (true) {
        val now = java.util.Calendar.getInstance()
        val resetTime = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_YEAR, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val diff = resetTime.timeInMillis - now.timeInMillis
        if (diff > 0) {
            val hours = diff / (3600 * 1000)
            val minutes = (diff % (3600 * 1000)) / (60 * 1000)
            val seconds = (diff % (60 * 1000)) / 1000
            onUpdate(String.format("%02d:%02d:%02d", hours, minutes, seconds))
        } else {
            onUpdate("00:00:00")
        }
        kotlinx.coroutines.delay(1000L)
    }
}

@Composable
fun SponsoredVideoItemCard(
    video: VideoEntity,
    isQuotaFull: Boolean,
    onWatch: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, DividerGray)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Tag
                Box(
                    modifier = Modifier
                        .background(LightMint, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = video.category.uppercase(),
                        color = ForestGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                // Reward Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(LightMint, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Default.MonetizationOn,
                        contentDescription = "Reward",
                        tint = GoldAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "+$${String.format("%.2f", video.rewardAmount)}",
                        color = ForestGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = video.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Charcoal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = video.description,
                fontSize = 13.sp,
                color = GrayText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Divider(color = DividerGray, thickness = 1.dp)

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = "Duration",
                            tint = GrayText,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "${video.durationSeconds}s", fontSize = 11.sp, color = GrayText)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Business,
                            contentDescription = "Advertiser",
                            tint = GrayText,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = video.advertiserName,
                            fontSize = 11.sp,
                            color = GrayText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 100.dp)
                        )
                    }
                }

                Button(
                    onClick = onWatch,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isQuotaFull) GrayText else ForestGreen
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = PureWhite,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Watch Video", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ==========================================
// WALLET AND WITHDRAWALS SCREEN
// ==========================================

@Composable
fun WalletScreen(viewModel: AppViewModel) {
    val currentUser by viewModel.currentUser.collectAsState()
    val withdrawals by viewModel.userWithdrawals.collectAsState()

    var showForm by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("") }
    var selectedMethod by remember { mutableStateOf("PayPal") }
    var accountDetails by remember { mutableStateOf("") }

    val methods = listOf("PayPal", "Bank Transfer", "USDT Wallet", "Amazon Gift Card")
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OffWhite)
            .padding(16.dp)
    ) {
        // Elegant Wallet balance card (Charcoal + Gold theme)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSlate),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                // Decorative top-right circle representing the geometric balance accent
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 24.dp, y = (-24).dp)
                        .background(GoldAccent.copy(alpha = 0.08f), CircleShape)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "REDEEMABLE BALANCE",
                        color = GrayText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "$${String.format("%.2f", currentUser?.walletBalance ?: 0.0)}",
                        color = GoldAccent,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { showForm = !showForm },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PureWhite,
                            contentColor = DarkSlate
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = if (showForm) Icons.Default.Close else Icons.Default.Payments,
                            contentDescription = null,
                            tint = DarkSlate
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (showForm) "Cancel Request" else "Request Cashout",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        AnimatedVisibility(
            visible = showForm,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = PureWhite),
                border = androidx.compose.foundation.BorderStroke(1.dp, DividerGray)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "Withdrawal Form",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Charcoal,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Amount ($)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ForestGreen,
                            focusedLabelColor = ForestGreen
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Simulated Dropdown
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedMethod,
                            onValueChange = { },
                            label = { Text("Method") },
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { expanded = !expanded }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            methods.forEach { method ->
                                DropdownMenuItem(
                                    text = { Text(method) },
                                    onClick = {
                                        selectedMethod = method
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = accountDetails,
                        onValueChange = { accountDetails = it },
                        label = { Text("Payment Address/Account (e.g. Email, IBAN)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ForestGreen,
                            focusedLabelColor = ForestGreen
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val parsedAmount = amount.toDoubleOrNull()
                            if (parsedAmount != null) {
                                viewModel.requestWithdrawal(parsedAmount, selectedMethod, accountDetails)
                                amount = ""
                                accountDetails = ""
                                showForm = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Submit Request ($5.00 min)", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // History list
        Text(
            text = "Withdrawal History",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Charcoal,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (withdrawals.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "You haven't requested any cashouts yet.",
                    color = GrayText,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(withdrawals) { wr ->
                    WithdrawalHistoryItem(wr = wr)
                }
            }
        }
    }
}

@Composable
fun WithdrawalHistoryItem(wr: WithdrawalRequestEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, DividerGray)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "${wr.paymentMethod} Payment",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Charcoal
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = wr.accountDetails,
                    fontSize = 11.sp,
                    color = GrayText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(180.dp)
                )
                Text(
                    text = formatDate(wr.requestedAt),
                    fontSize = 11.sp,
                    color = GrayText
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$${String.format("%.2f", wr.amount)}",
                    fontWeight = FontWeight.Bold,
                    color = ForestGreen,
                    fontSize = 16.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                val badgeColor = when (wr.status) {
                    "APPROVED" -> Color(0xFF2E7D32)
                    "PAID" -> Color(0xFF1B5E20)
                    "REJECTED" -> Color(0xFFD32F2F)
                    "UNDER_REVIEW" -> Color(0xFFEF6C00)
                    "SUBMITTED" -> Color(0xFF1565C0)
                    else -> GoldAmber
                }

                Box(
                    modifier = Modifier
                        .background(badgeColor.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = when (wr.status) {
                            "PENDING" -> "SUBMITTED"
                            else -> wr.status
                        },
                        color = badgeColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ==========================================
// PROFILE SCREEN
// ==========================================

@Composable
fun ProfileScreen(viewModel: AppViewModel) {
    val currentUser by viewModel.currentUser.collectAsState()
    val watchLogs by viewModel.userWatchLogs.collectAsState()

    var email by remember(currentUser) { mutableStateOf(currentUser?.email ?: "") }
    var password by remember(currentUser) { mutableStateOf(currentUser?.passwordHash ?: "") }

    var isEditing by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OffWhite)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Profile Info Avatar Banner
        Box(
            modifier = Modifier
                .size(90.dp)
                .background(ForestGreen, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = GoldAccent,
                modifier = Modifier.size(50.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = currentUser?.username ?: "User",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Charcoal
        )

        val roleLabel = if (currentUser?.role == "ADMIN") "Administrator Account" else "Standard Earner Member"
        Text(
            text = roleLabel,
            fontSize = 12.sp,
            color = ForestGreen,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Simple stats panel
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = PureWhite),
            border = androidx.compose.foundation.BorderStroke(1.dp, DividerGray)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Watch Count", fontSize = 11.sp, color = GrayText, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "${watchLogs.size}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Charcoal)
                }
                Divider(
                    color = DividerGray,
                    modifier = Modifier
                        .height(36.dp)
                        .width(1.dp)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total Account Balance", fontSize = 11.sp, color = GrayText, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    val totalWithdrawn = currentUser?.walletBalance ?: 0.0
                    Text(text = "$${String.format("%.2f", totalWithdrawn)}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Charcoal)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Settings / details card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = PureWhite),
            border = androidx.compose.foundation.BorderStroke(1.dp, DividerGray)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Profile Settings",
                        fontWeight = FontWeight.Bold,
                        color = Charcoal,
                        fontSize = 16.sp
                    )

                    IconButton(onClick = { isEditing = !isEditing }) {
                        Icon(
                            imageVector = if (isEditing) Icons.Default.Close else Icons.Default.Edit,
                            contentDescription = "Edit Profile",
                            tint = ForestGreen
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { if (isEditing) email = it },
                    label = { Text("Email Address") },
                    readOnly = !isEditing,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ForestGreen,
                        focusedLabelColor = ForestGreen
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { if (isEditing) password = it },
                    label = { Text("Password") },
                    readOnly = !isEditing,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ForestGreen,
                        focusedLabelColor = ForestGreen
                    )
                )

                if (isEditing) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            viewModel.updateProfile(email, password)
                            isEditing = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Save Profile Changes", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { viewModel.logout() },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.ExitToApp, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sign Out", fontWeight = FontWeight.Bold)
        }
    }
}

// ==========================================
// SIMULATED VIDEO PLAYER SCREEN
// ==========================================

@Composable
fun VideoPlayerScreen(
    videoId: Int,
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val videoFlowState = viewModel.getVideoById(videoId).collectAsState(initial = null)
    val video = videoFlowState.value

    var timeRemaining by remember { mutableIntStateOf(0) }
    var totalDuration by remember { mutableIntStateOf(10) }
    var isPlaying by remember { mutableStateOf(false) }
    var watchCompleted by remember { mutableStateOf(false) }

    // Init values
    LaunchedEffect(video) {
        video?.let {
            timeRemaining = it.durationSeconds
            totalDuration = it.durationSeconds
            isPlaying = true
        }
    }

    // Timer Loop
    LaunchedEffect(isPlaying, timeRemaining) {
        if (isPlaying && timeRemaining > 0) {
            delay(1000L)
            timeRemaining -= 1
            if (timeRemaining == 0) {
                isPlaying = false
                watchCompleted = true
            }
        }
    }

    Scaffold(
        containerColor = Color.Black
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = PureWhite
                    )
                }

                Text(
                    text = "Sponsored Video View",
                    color = PureWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                // Placeholder empty box for layout
                Spacer(modifier = Modifier.size(48.dp))
            }

            // Interactive Mock Stream Canvas Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(Color(0xFF151515))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (watchCompleted) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(GoldAccent, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Done",
                                tint = ForestGreen,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Verified! Tap Below to Claim",
                            color = GoldAccent,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Animated pulsing bar representer
                        CircularProgressIndicator(
                            progress = { (totalDuration - timeRemaining).toFloat() / totalDuration },
                            modifier = Modifier.size(72.dp),
                            color = GoldAccent,
                            strokeWidth = 6.dp,
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = if (isPlaying) "Playing sponsored video ad..." else "Video Paused",
                            color = PureWhite,
                            fontSize = 14.sp
                        )

                        Text(
                            text = "$timeRemaining seconds remaining",
                            color = GoldAccent,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Info Block
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(SurfaceDark)
                    .padding(24.dp)
            ) {
                Text(
                    text = video?.title ?: "Loading Campaign Video...",
                    color = PureWhite,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Sponsor: ${video?.advertiserName ?: "Advertiser"}",
                        color = GoldAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .background(GoldAmber.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "+$${String.format("%.2f", video?.rewardAmount ?: 0.0)}",
                            color = GoldAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = video?.description ?: "",
                    color = OffWhite.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.weight(1f))

                // Media Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!watchCompleted) {
                        IconButton(
                            onClick = { isPlaying = !isPlaying },
                            modifier = Modifier
                                .size(56.dp)
                                .background(PureWhite.copy(alpha = 0.1f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = PureWhite,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                viewModel.watchVideo(videoId) {
                                    onDismiss()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldAccent),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Icon(
                                Icons.Default.Redeem,
                                contentDescription = null,
                                tint = Charcoal
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Claim $${String.format("%.2f", video?.rewardAmount ?: 0.0)} Reward",
                                color = Charcoal,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// ADMIN DASHBOARD SCREEN
// ==========================================

@Composable
fun AdminDashboardScreen(viewModel: AppViewModel) {
    val users by viewModel.allUsers.collectAsState()
    val advertisers by viewModel.allAdvertisers.collectAsState()
    val videos by viewModel.allVideos.collectAsState()
    val withdrawals by viewModel.allWithdrawals.collectAsState()

    var activeTab by remember { mutableStateOf("stats") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OffWhite)
    ) {
        // Admin header banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(ForestGreen)
                .padding(20.dp)
        ) {
            Column {
                Text(
                    text = "Control Center",
                    color = PureWhite,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Manage Sponsor Rewards Infrastructure",
                    color = LightMint,
                    fontSize = 12.sp
                )
            }
        }

        // Horizontal tabs
        ScrollableTabRow(
            selectedTabIndex = when (activeTab) {
                "stats" -> 0
                "advertisers" -> 1
                "videos" -> 2
                "withdrawals" -> 3
                "users" -> 4
                else -> 0
            },
            containerColor = PureWhite,
            contentColor = ForestGreen
        ) {
            Tab(selected = activeTab == "stats", onClick = { activeTab = "stats" }) {
                Text("Stats", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Medium)
            }
            Tab(selected = activeTab == "advertisers", onClick = { activeTab = "advertisers" }) {
                Text("Sponsors", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Medium)
            }
            Tab(selected = activeTab == "videos", onClick = { activeTab = "videos" }) {
                Text("Videos", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Medium)
            }
            Tab(selected = activeTab == "withdrawals", onClick = { activeTab = "withdrawals" }) {
                Text("Payouts", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Medium)
            }
            Tab(selected = activeTab == "users", onClick = { activeTab = "users" }) {
                Text("Users", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Medium)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp)
        ) {
            when (activeTab) {
                "stats" -> AdminStatsPanel(
                    usersCount = users.size,
                    advertisersCount = advertisers.size,
                    videosCount = videos.size,
                    pendingWithdrawalsCount = withdrawals.filter { it.status == "PENDING" || it.status == "SUBMITTED" || it.status == "UNDER_REVIEW" }.size
                )
                "advertisers" -> AdminAdvertisersPanel(
                    advertisers = advertisers,
                    onAdd = { name, ind, email, budget ->
                        viewModel.addAdvertiser(name, ind, email, budget)
                    },
                    onDelete = { viewModel.deleteAdvertiser(it) }
                )
                "videos" -> AdminVideosPanel(
                    videos = videos,
                    advertisers = advertisers,
                    onAdd = { title, desc, advId, advName, reward, dur, cat ->
                        viewModel.addVideo(title, desc, advId, advName, reward, dur, cat)
                    },
                    onDelete = { viewModel.deleteVideo(it) }
                )
                "withdrawals" -> AdminWithdrawalsPanel(
                    withdrawals = withdrawals,
                    onStatusUpdate = { wr, status -> viewModel.updateWithdrawalStatusByAdmin(wr, status) }
                )
                "users" -> AdminUsersPanel(
                    users = users,
                    onAdjustBalance = { id, bal -> viewModel.updateUserBalanceByAdmin(id, bal) },
                    onDeleteUser = { viewModel.deleteUser(it) }
                )
            }
        }
    }
}

@Composable
fun AdminStatsPanel(
    usersCount: Int,
    advertisersCount: Int,
    videosCount: Int,
    pendingWithdrawalsCount: Int
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = "Overview Performance",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Charcoal
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AdminStatCard(
                    title = "Active Users",
                    value = "$usersCount",
                    icon = Icons.Default.Group,
                    color = ForestGreen,
                    modifier = Modifier.weight(1f)
                )

                AdminStatCard(
                    title = "Sponsors",
                    value = "$advertisersCount",
                    icon = Icons.Default.Business,
                    color = GoldAmber,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AdminStatCard(
                    title = "Campaign Ads",
                    value = "$videosCount",
                    icon = Icons.Default.VideoLibrary,
                    color = Color(0xFF0288D1),
                    modifier = Modifier.weight(1f)
                )

                AdminStatCard(
                    title = "Pending Cashouts",
                    value = "$pendingWithdrawalsCount",
                    icon = Icons.Default.PendingActions,
                    color = Color(0xFFD32F2F),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = PureWhite),
                border = androidx.compose.foundation.BorderStroke(1.dp, DividerGray)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Developer System Logs",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Charcoal
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "SQLite schema integrity verified. Automatic video distribution service running offline.",
                        color = GrayText,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun AdminStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, DividerGray)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(text = value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Charcoal)
            Text(text = title, fontSize = 11.sp, color = GrayText)
        }
    }
}

// 1. Advertisers Panel
@Composable
fun AdminAdvertisersPanel(
    advertisers: List<AdvertiserEntity>,
    onAdd: (String, String, String, Double) -> Unit,
    onDelete: (AdvertiserEntity) -> Unit
) {
    var showAddForm by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var industry by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var budget by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Advertisers & Sponsors", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Button(
                onClick = { showAddForm = !showAddForm },
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(if (showAddForm) Icons.Default.Close else Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (showAddForm) "Close" else "Add Sponsor", fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        AnimatedVisibility(visible = showAddForm) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = PureWhite)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Add Sponsor Details", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Brand Name") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = industry, onValueChange = { industry = it }, label = { Text("Industry") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Contact Email") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = budget, onValueChange = { budget = it }, label = { Text("Total Budget ($)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val b = budget.toDoubleOrNull() ?: 0.0
                            onAdd(name, industry, email, b)
                            name = ""
                            industry = ""
                            email = ""
                            budget = ""
                            showAddForm = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Sponsor")
                    }
                }
            }
        }

        if (advertisers.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("No advertisers present", color = GrayText)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(advertisers) { adv ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = PureWhite)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(adv.name, fontWeight = FontWeight.Bold, color = Charcoal)
                                Text("${adv.industry} | ${adv.contactEmail}", fontSize = 11.sp, color = GrayText)
                                Text("Budget: $${String.format("%.2f", adv.remainingBudget)} remaining / $${String.format("%.2f", adv.totalBudget)}", fontSize = 12.sp, color = ForestGreen, fontWeight = FontWeight.Medium)
                            }

                            IconButton(onClick = { onDelete(adv) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFC62828))
                            }
                        }
                    }
                }
            }
        }
    }
}

// 2. Videos Panel
@Composable
fun AdminVideosPanel(
    videos: List<VideoEntity>,
    advertisers: List<AdvertiserEntity>,
    onAdd: (String, String, Int, String, Double, Int, String) -> Unit,
    onDelete: (VideoEntity) -> Unit
) {
    var showAddForm by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var reward by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Tech") }

    var selectedAdvIndex by remember { mutableIntStateOf(0) }
    var advExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Campaign Sponsored Videos", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Button(
                onClick = { showAddForm = !showAddForm },
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(if (showAddForm) Icons.Default.Close else Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (showAddForm) "Close" else "Add Campaign", fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        AnimatedVisibility(visible = showAddForm) {
            if (advertisers.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text("Error: Please register an advertiser first.", color = Color.Red, modifier = Modifier.padding(16.dp))
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = PureWhite)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("New Campaign Video", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Campaign Title") }, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(8.dp))

                        // Select advertiser dropdown
                        Box(modifier = Modifier.fillMaxWidth()) {
                            val selectedAdv = advertisers.getOrNull(selectedAdvIndex)
                            OutlinedTextField(
                                value = selectedAdv?.name ?: "Select Advertiser",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Sponsoring Brand") },
                                trailingIcon = {
                                    IconButton(onClick = { advExpanded = !advExpanded }) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            DropdownMenu(expanded = advExpanded, onDismissRequest = { advExpanded = false }) {
                                advertisers.forEachIndexed { idx, adv ->
                                    DropdownMenuItem(
                                        text = { Text(adv.name) },
                                        onClick = {
                                            selectedAdvIndex = idx
                                            advExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = reward, onValueChange = { reward = it }, label = { Text("Reward ($)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                            OutlinedTextField(value = duration, onValueChange = { duration = it }, label = { Text("Length (s)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category (e.g. Sports, Tech)") }, modifier = Modifier.fillMaxWidth())

                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val rew = reward.toDoubleOrNull() ?: 0.0
                                val dur = duration.toIntOrNull() ?: 15
                                val adv = advertisers[selectedAdvIndex]
                                onAdd(title, desc, adv.id, adv.name, rew, dur, category)
                                title = ""
                                desc = ""
                                reward = ""
                                duration = ""
                                showAddForm = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Deploy Video Campaign")
                        }
                    }
                }
            }
        }

        if (videos.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("No active video campaigns", color = GrayText)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(videos) { video ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = PureWhite)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(video.title, fontWeight = FontWeight.Bold, color = Charcoal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Sponsor: ${video.advertiserName} | Cat: ${video.category}", fontSize = 11.sp, color = GrayText)
                                Text("Payout: $${String.format("%.2f", video.rewardAmount)} | Time: ${video.durationSeconds}s", fontSize = 11.sp, color = ForestGreen, fontWeight = FontWeight.Bold)
                            }

                            IconButton(onClick = { onDelete(video) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFC62828))
                            }
                        }
                    }
                }
            }
        }
    }
}

// 3. Withdrawals Panel
@Composable
fun AdminWithdrawalsPanel(
    withdrawals: List<WithdrawalRequestEntity>,
    onStatusUpdate: (WithdrawalRequestEntity, String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Payout Approvals Hub", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 12.dp))

        if (withdrawals.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("No payout requests found.", color = GrayText)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(withdrawals) { wr ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = PureWhite),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("User: ${wr.username}", fontWeight = FontWeight.Bold, color = Charcoal)
                                    Text("${wr.paymentMethod}: ${wr.accountDetails}", fontSize = 11.sp, color = GrayText)
                                    Text("Date: ${formatDate(wr.requestedAt)}", fontSize = 11.sp, color = GrayText)
                                }

                                Text(
                                    text = "$${String.format("%.2f", wr.amount)}",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ForestGreen
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val badgeColor = when (wr.status) {
                                    "APPROVED" -> Color(0xFF2E7D32)
                                    "PAID" -> Color(0xFF1B5E20)
                                    "REJECTED" -> Color(0xFFD32F2F)
                                    "UNDER_REVIEW" -> Color(0xFFEF6C00)
                                    "SUBMITTED" -> Color(0xFF1565C0)
                                    else -> GoldAmber
                                }

                                Box(
                                    modifier = Modifier
                                        .background(
                                            badgeColor.copy(alpha = 0.15f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = when (wr.status) {
                                            "PENDING" -> "SUBMITTED"
                                            else -> wr.status
                                        },
                                        color = badgeColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Interactive action buttons based on status
                                if (wr.status != "PAID" && wr.status != "REJECTED") {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        // Reject button is always available for active states
                                        Button(
                                            onClick = { onStatusUpdate(wr, "REJECTED") },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text("Reject", fontSize = 10.sp)
                                        }

                                        when (wr.status) {
                                            "PENDING", "SUBMITTED" -> {
                                                Button(
                                                    onClick = { onStatusUpdate(wr, "UNDER_REVIEW") },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF6C00)),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                                ) {
                                                    Icon(Icons.Default.Pending, contentDescription = null, modifier = Modifier.size(12.dp))
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                    Text("Review", fontSize = 10.sp)
                                                }
                                            }
                                            "UNDER_REVIEW" -> {
                                                Button(
                                                    onClick = { onStatusUpdate(wr, "APPROVED") },
                                                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                                ) {
                                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp))
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                    Text("Approve & Pay", fontSize = 10.sp)
                                                }
                                            }
                                            "APPROVED" -> {
                                                Button(
                                                    onClick = { onStatusUpdate(wr, "PAID") },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20)),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                                ) {
                                                    Icon(Icons.Default.Paid, contentDescription = null, modifier = Modifier.size(12.dp))
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                    Text("Mark Paid", fontSize = 10.sp)
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // Terminal state
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (wr.status == "PAID") Icons.Default.CheckCircle else Icons.Default.Cancel,
                                            contentDescription = null,
                                            tint = badgeColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = if (wr.status == "PAID") "Settled" else "Cancelled",
                                            fontSize = 11.sp,
                                            color = badgeColor,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// 4. Users Panel
@Composable
fun AdminUsersPanel(
    users: List<UserEntity>,
    onAdjustBalance: (Int, Double) -> Unit,
    onDeleteUser: (UserEntity) -> Unit
) {
    var editingUserId by remember { mutableIntStateOf(-1) }
    var adjustAmountText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Registered Members", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(users) { user ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = PureWhite)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("${user.username} (${user.role})", fontWeight = FontWeight.Bold, color = Charcoal)
                                Text("Email: ${user.email}", fontSize = 11.sp, color = GrayText)
                                Text("Member since: ${formatDate(user.registerTime)}", fontSize = 11.sp, color = GrayText)
                                Text("Wallet Balance: $${String.format("%.2f", user.walletBalance)}", fontSize = 13.sp, color = ForestGreen, fontWeight = FontWeight.Bold)
                            }

                            Row {
                                IconButton(onClick = {
                                    editingUserId = if (editingUserId == user.id) -1 else user.id
                                    adjustAmountText = user.walletBalance.toString()
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Adjust Balance", tint = ForestGreen)
                                }
                                if (user.username != "admin") {
                                    IconButton(onClick = { onDeleteUser(user) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFC62828))
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(visible = editingUserId == user.id) {
                            Column(modifier = Modifier.padding(top = 10.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = adjustAmountText,
                                        onValueChange = { adjustAmountText = it },
                                        label = { Text("Set Wallet Balance ($)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Button(
                                        onClick = {
                                            val amt = adjustAmountText.toDoubleOrNull()
                                            if (amt != null) {
                                                onAdjustBalance(user.id, amt)
                                                editingUserId = -1
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                                    ) {
                                        Text("Set")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
