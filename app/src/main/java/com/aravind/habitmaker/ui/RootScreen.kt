package com.aravind.habitmaker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aravind.habitmaker.app
import com.aravind.habitmaker.core.auth.AuthState
import com.aravind.habitmaker.core.prefs.Settings
import com.aravind.habitmaker.feature.auth.AuthFlow
import com.aravind.habitmaker.feature.onboarding.OnboardingScreen
import com.aravind.habitmaker.feature.permissions.PermissionsScreen
import com.aravind.habitmaker.navigation.AppShell
import com.aravind.habitmaker.ui.components.IconTile
import com.aravind.habitmaker.ui.theme.colors
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Decides the first page: splash → onboarding → sign in → permissions → the app. */
@Composable
fun RootScreen(openRoute: String?) {
    val app = LocalContext.current.app
    val scope = rememberCoroutineScope()
    val flow = remember { combine(app.prefs.settings, app.auth.state) { s, a -> s to a } }
    val state: Pair<Settings, AuthState>? by flow.collectAsStateWithLifecycle(initialValue = null)
    val (s, a) = state ?: run { SplashScreen(); return }

    when {
        !s.onboardingDone -> OnboardingScreen { goal, target ->
            scope.launch { app.prefs.update { it.copy(onboardingDone = true, goal = goal, dailyTargetMin = target) } }
        }
        !a.inside -> AuthFlow()
        !s.permissionsSeen -> PermissionsScreen(onBack = null) {
            scope.launch { app.prefs.update { it.copy(permissionsSeen = true) } }
        }
        else -> AppShell(openRoute)
    }
}

@Composable
fun SplashScreen() {
    val c = MaterialTheme.colors
    Column(
        Modifier.fillMaxSize().background(c.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconTile(Icons.Filled.CheckCircle, c.foreground, 72.dp)
        Spacer(Modifier.height(16.dp))
        Text("Habit Maker", style = MaterialTheme.typography.headlineMedium, color = c.foreground)
    }
}
