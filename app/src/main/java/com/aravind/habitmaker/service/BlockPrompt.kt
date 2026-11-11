package com.aravind.habitmaker.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.aravind.habitmaker.core.blocking.BlockReason
import com.aravind.habitmaker.core.prefs.ThemeMode
import com.aravind.habitmaker.ui.components.AppButton
import com.aravind.habitmaker.ui.components.Badge
import com.aravind.habitmaker.ui.components.ButtonKind
import com.aravind.habitmaker.ui.components.IconTile
import com.aravind.habitmaker.ui.components.Tone
import com.aravind.habitmaker.ui.theme.Accent
import com.aravind.habitmaker.ui.theme.HabitMakerTheme
import com.aravind.habitmaker.ui.theme.colors
import kotlinx.coroutines.delay

/** What the prompt shows. */
data class PromptInfo(
    val pkg: String,
    val appName: String,
    val reason: BlockReason,
    val detail: String?,
    val message: String,
    /** Strict: no "5 more minutes". */
    val strict: Boolean,
    /** Shorts / websites: leaving means going back inside the app, not closing it. */
    val inApp: Boolean,
    val theme: ThemeMode,
    val accent: Accent,
)

/**
 * A full-screen pop-up drawn over the blocked app (an accessibility overlay,
 * so no extra permission). The app is NOT closed until the user chooses.
 */
class BlockPrompt(private val service: AccessibilityService) {
    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: View? = null
    private var owner: OverlayOwner? = null

    /** Package the prompt is shown over, or null. */
    var shownFor: String? = null
        private set

    val showing get() = view != null

    @SuppressLint("ClickableViewAccessibility")
    fun show(info: PromptInfo, onLeave: () -> Unit, onSnooze: () -> Unit) {
        hide()
        val o = OverlayOwner().also { it.start() }
        val compose = ComposeView(service).apply {
            setContent { HabitMakerTheme(info.theme, info.accent) { Prompt(info, onLeave, onSnooze) } }
        }
        // Back key = leave.
        val root = object : FrameLayout(service) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) onLeave()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        root.addView(compose)
        root.setViewTreeLifecycleOwner(o)
        root.setViewTreeSavedStateRegistryOwner(o)
        root.setViewTreeViewModelStoreOwner(o)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        runCatching { wm.addView(root, params) }.onSuccess {
            view = root
            owner = o
            shownFor = info.pkg
        }.onFailure { o.stop() }
    }

    fun hide() {
        view?.let { runCatching { wm.removeView(it) } }
        owner?.stop()
        view = null
        owner = null
        shownFor = null
    }
}

@Composable
private fun Prompt(info: PromptInfo, onLeave: () -> Unit, onSnooze: () -> Unit) {
    val c = MaterialTheme.colors
    val wait by produceState(SNOOZE_WAIT_S) { while (value > 0) { delay(1000); value-- } }
    Column(
        Modifier.fillMaxSize().background(c.background.copy(alpha = 0.97f)).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconTile(if (info.reason == BlockReason.FOCUS) Icons.Filled.SelfImprovement else Icons.Filled.Block, c.danger, 72.dp)
        Spacer(Modifier.height(24.dp))
        Text(info.reason.title, style = MaterialTheme.typography.headlineMedium, color = c.foreground, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(info.detail ?: info.appName, style = MaterialTheme.typography.titleMedium, color = c.muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(info.reason.message, style = MaterialTheme.typography.bodyLarge, color = c.muted, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 360.dp))
        Spacer(Modifier.height(24.dp))
        Text("“${info.message}”", style = MaterialTheme.typography.titleMedium, color = c.foreground, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 360.dp))
        Spacer(Modifier.height(40.dp))
        AppButton(if (info.inApp) "Go back" else "Close ${info.appName}", onLeave, modifier = Modifier.widthIn(max = 320.dp))
        Spacer(Modifier.height(10.dp))
        if (info.strict) {
            Badge("Strict mode is on — this can't be skipped", Tone.WARNING, Icons.Filled.Lock)
        } else {
            AppButton(
                if (wait > 0) "5 more minutes (wait ${wait}s)" else "5 more minutes",
                onSnooze, modifier = Modifier.widthIn(max = 320.dp), kind = ButtonKind.SECONDARY, enabled = wait == 0,
            )
        }
    }
}

private const val SNOOZE_WAIT_S = 5

/** Lifecycle + saved state for a Compose view that lives outside an Activity. */
private class OverlayOwner : SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry get() = controller.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    fun start() {
        controller.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun stop() {
        registry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}
