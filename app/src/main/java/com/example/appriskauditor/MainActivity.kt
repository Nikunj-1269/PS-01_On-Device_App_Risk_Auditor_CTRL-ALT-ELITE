package com.example.appriskauditor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.appriskauditor.ui.AppDetailScreen
import com.example.appriskauditor.ui.AppListScreen
import com.example.appriskauditor.ui.MainViewModel
import com.example.appriskauditor.ui.theme.AppRiskAuditorTheme

/**
 * Single-activity host. Navigation is handled manually with a simple
 * backstack state variable — no Jetpack Navigation dependency needed.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppRiskAuditorTheme {
                AppNavHost()
            }
        }
    }
}

/** Minimal nav host: list screen → detail screen, back press returns to list. */
@Composable
private fun AppNavHost() {
    // Share the same ViewModel across both screens
    val viewModel: MainViewModel = viewModel()

    // Simple navigation state: null = list screen, non-null = detail for that package
    var selectedPackage by remember { mutableStateOf<String?>(null) }

    if (selectedPackage == null) {
        AppListScreen(
            onAppClick = { pkg -> selectedPackage = pkg },
            viewModel  = viewModel
        )
    } else {
        AppDetailScreen(
            packageName = selectedPackage!!,
            onBack      = { selectedPackage = null },
            viewModel   = viewModel
        )
    }
}
