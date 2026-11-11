package com.aravind.habitmaker.feature.auth

import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aravind.habitmaker.app
import com.aravind.habitmaker.core.auth.AuthException
import com.aravind.habitmaker.ui.components.AppButton
import com.aravind.habitmaker.ui.components.AppTextField
import com.aravind.habitmaker.ui.components.ButtonKind
import com.aravind.habitmaker.ui.components.ConfirmDialog
import com.aravind.habitmaker.ui.components.Divider
import com.aravind.habitmaker.ui.components.AppLogo
import com.aravind.habitmaker.ui.components.InlineBanner
import com.aravind.habitmaker.ui.components.Tone
import com.aravind.habitmaker.ui.theme.colors
import kotlinx.coroutines.launch

private enum class AuthPage { LOGIN, REGISTER, FORGOT }

/** Sign in / create account / forgot password, switching in place. */
@Composable
fun AuthFlow() {
    val auth = LocalContext.current.app.auth
    var page by remember { mutableStateOf<AuthPage?>(null) }
    LaunchedEffect(Unit) { page = if (auth.hasAccount()) AuthPage.LOGIN else AuthPage.REGISTER }
    when (page) {
        AuthPage.LOGIN -> LoginScreen(onRegister = { page = AuthPage.REGISTER }, onForgot = { page = AuthPage.FORGOT })
        AuthPage.REGISTER -> RegisterScreen(onLogin = { page = AuthPage.LOGIN })
        AuthPage.FORGOT -> ForgotScreen(onBack = { page = AuthPage.LOGIN }, onReset = { page = AuthPage.REGISTER })
        null -> Unit
    }
}

/** Logo, title, subtitle and the form, centred and scrollable over the keyboard. */
@Composable
private fun AuthLayout(title: String, subtitle: String, onBack: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = MaterialTheme.colors
    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
        if (onBack != null) IconButton(onClick = onBack, modifier = Modifier.padding(4.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = c.foreground)
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(24.dp))
                AppLogo(88.dp)
                Spacer(Modifier.height(16.dp))
                Text(title, style = MaterialTheme.typography.headlineMedium, color = c.foreground, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.muted, textAlign = TextAlign.Center)
                Spacer(Modifier.height(28.dp))
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
            }
        }
    }
}

private fun validEmail(e: String) = Patterns.EMAIL_ADDRESS.matcher(e.trim()).matches()

@Composable
private fun GuestButton() {
    val auth = LocalContext.current.app.auth
    val scope = rememberCoroutineScope()
    val c = MaterialTheme.colors
    Text("or", style = MaterialTheme.typography.bodySmall, color = c.faint, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    AppButton("Continue without an account", { scope.launch { auth.continueAsGuest() } }, kind = ButtonKind.SECONDARY)
}

@Composable
private fun LoginScreen(onRegister: () -> Unit, onForgot: () -> Unit) {
    val auth = LocalContext.current.app.auth
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var tried by remember { mutableStateOf(false) }

    AuthLayout("Welcome back", "Sign in to continue your streaks.") {
        error?.let { InlineBanner(it) }
        AppTextField(email, { email = it }, "Email", leading = Icons.Filled.Email, keyboardType = KeyboardType.Email,
            error = if (tried && !validEmail(email)) "Enter a valid email" else null)
        AppTextField(password, { password = it }, "Password", leading = Icons.Filled.Lock, password = true, imeAction = ImeAction.Done,
            error = if (tried && password.isEmpty()) "Enter your password" else null)
        AppButton("Forgot password?", onForgot, kind = ButtonKind.GHOST, fullWidth = false)
        AppButton("Sign in", {
            tried = true
            if (!validEmail(email) || password.isEmpty()) return@AppButton
            busy = true; error = null
            scope.launch {
                try { auth.signIn(email, password) } catch (e: AuthException) { error = e.message }
                busy = false
            }
        }, loading = busy, loadingText = "Signing in…")
        AppButton("Create an account", onRegister, kind = ButtonKind.GHOST)
        GuestButton()
    }
}

@Composable
private fun RegisterScreen(onLogin: () -> Unit) {
    val auth = LocalContext.current.app.auth
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var tried by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val nameErr = if (tried && name.isBlank()) "Enter your name" else null
    val emailErr = if (tried && !validEmail(email)) "Enter a valid email" else null
    val passErr = if (tried && password.length < 6) "At least 6 characters" else null
    val confirmErr = if (tried && confirm != password) "Passwords don't match" else null

    AuthLayout("Create your account", "Your habits and settings stay on this phone.") {
        AppTextField(name, { name = it }, "Name", leading = Icons.Filled.Person, error = nameErr)
        AppTextField(email, { email = it }, "Email", leading = Icons.Filled.Email, keyboardType = KeyboardType.Email, error = emailErr)
        AppTextField(password, { password = it }, "Password", leading = Icons.Filled.Lock, password = true, error = passErr)
        AppTextField(confirm, { confirm = it }, "Confirm password", leading = Icons.Filled.Lock, password = true, imeAction = ImeAction.Done, error = confirmErr)
        AppButton("Create account", {
            tried = true
            if (name.isBlank() || !validEmail(email) || password.length < 6 || confirm != password) return@AppButton
            busy = true
            scope.launch { auth.register(name, email, password); busy = false }
        }, loading = busy, loadingText = "Creating…")
        AppButton("I already have an account", onLogin, kind = ButtonKind.GHOST)
        GuestButton()
    }
}

@Composable
private fun ForgotScreen(onBack: () -> Unit, onReset: () -> Unit) {
    val auth = LocalContext.current.app.auth
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    if (confirm) ConfirmDialog(
        "Reset account?", "This removes the sign-in on this phone. Your habits and history are kept.", "Reset",
        onConfirm = { scope.launch { auth.resetAccount(); onReset() } }, onDismiss = { confirm = false },
    )
    AuthLayout("Forgot password", "Your account is stored only on this phone.", onBack = onBack) {
        InlineBanner("There is no server to send a reset email yet. You can reset the account on this phone and create a new password.", Tone.INFO)
        AppButton("Reset account on this phone", { confirm = true }, kind = ButtonKind.DANGER)
        AppButton("Back to sign in", onBack, kind = ButtonKind.GHOST)
    }
}
