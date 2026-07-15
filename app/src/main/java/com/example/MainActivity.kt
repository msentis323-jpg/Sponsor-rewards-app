package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AppNavigation
import com.example.ui.AppViewModel
import com.example.ui.theme.SponsorRewardsTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      SponsorRewardsTheme {
        val viewModel: AppViewModel = viewModel()
        val activeVideoId = remember { mutableStateOf<Int?>(null) }

        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          AppNavigation(
            viewModel = viewModel,
            onNavigateToVideo = { id ->
              activeVideoId.value = if (id == -1) null else id
            },
            activeVideoId = activeVideoId
          )
        }
      }
    }
  }
}
