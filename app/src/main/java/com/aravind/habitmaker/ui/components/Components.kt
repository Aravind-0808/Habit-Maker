package com.aravind.habitmaker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aravind.habitmaker.ui.theme.colors

@Composable
fun AppHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    navigation: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = MaterialTheme.colors
    Column(Modifier.background(c.background).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                onBack != null -> IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = c.foreground) }
                navigation != null -> navigation()
                else -> Spacer(Modifier.size(12.dp))
            }
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.foreground, modifier = Modifier.weight(1f).padding(horizontal = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            actions()
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
    }
}

enum class ButtonKind { PRIMARY, SECONDARY, DANGER, GHOST }

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.PRIMARY,
    enabled: Boolean = true,
    loading: Boolean = false,
    loadingText: String? = null,
    icon: ImageVector? = null,
    fullWidth: Boolean = true,
) {
    val c = MaterialTheme.colors
    val m = (if (fullWidth) modifier.fillMaxWidth() else modifier).heightIn(min = 48.dp)
    val content: @Composable RowScope.() -> Unit = {
        if (loading) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = LocalContentColorFor(kind))
            Spacer(Modifier.size(8.dp))
            Text(loadingText ?: text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        } else {
            if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.size(8.dp)) }
            Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
    val shape = RoundedCornerShape(50)
    when (kind) {
        ButtonKind.PRIMARY -> Button(onClick, m, enabled && !loading, shape = shape,
            colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary, disabledContainerColor = c.primary.copy(alpha = 0.4f), disabledContentColor = c.onPrimary.copy(alpha = 0.8f)), content = content)
        ButtonKind.DANGER -> Button(onClick, m, enabled && !loading, shape = shape,
            colors = ButtonDefaults.buttonColors(containerColor = c.danger, contentColor = Color.White), content = content)
        ButtonKind.SECONDARY -> OutlinedButton(onClick, m, enabled && !loading, shape = shape,
            border = BorderStroke(1.dp, c.border), colors = ButtonDefaults.outlinedButtonColors(contentColor = c.foreground), content = content)
        ButtonKind.GHOST -> TextButton(onClick, m, enabled && !loading, shape = shape,
            colors = ButtonDefaults.textButtonColors(contentColor = c.foreground), content = content)
    }
}

@Composable
private fun LocalContentColorFor(kind: ButtonKind): Color = when (kind) {
    ButtonKind.PRIMARY -> MaterialTheme.colors.onPrimary
    ButtonKind.DANGER -> Color.White
    else -> MaterialTheme.colors.foreground
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    placeholder: String? = null,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    leading: ImageVector? = null,
) {
    val c = MaterialTheme.colors
    var visible by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.foreground)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = singleLine,
            isError = error != null,
            placeholder = placeholder?.let { { Text(it, color = c.faint) } },
            leadingIcon = leading?.let { { Icon(it, null, tint = c.muted) } },
            visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType, imeAction = imeAction),
            trailingIcon = if (password) {
                { IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (visible) "Hide password" else "Show password", tint = c.muted) } }
            } else null,
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = c.foreground, unfocusedBorderColor = c.border, errorBorderColor = c.danger,
                focusedContainerColor = c.surface, unfocusedContainerColor = c.surface,
            ),
        )
        if (error != null) {
            Spacer(Modifier.height(4.dp))
            Text(error, style = MaterialTheme.typography.bodySmall, color = c.danger)
        }
    }
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    borderColor: Color? = null,
    elevated: Boolean = false,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = MaterialTheme.colors
    val shape = MaterialTheme.shapes.large
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (elevated) c.surfaceElevated else c.surface)
            .then(if (borderColor != null) Modifier.border(1.dp, borderColor, shape) else Modifier)
            .then(if (onClick != null) Modifier.bouncyClickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

enum class Tone { SUCCESS, WARNING, DANGER, INFO, NEUTRAL }

@Composable
fun toneColors(tone: Tone): Pair<Color, Color> {
    val c = MaterialTheme.colors
    return when (tone) {
        Tone.SUCCESS -> c.success to c.successSoft
        Tone.WARNING -> c.warning to c.warningSoft
        Tone.DANGER -> c.danger to c.dangerSoft
        Tone.INFO -> c.info to c.infoSoft
        Tone.NEUTRAL -> c.muted to c.sunken
    }
}

@Composable
fun Badge(text: String, tone: Tone = Tone.NEUTRAL, icon: ImageVector? = null) {
    val (fg, bg) = toneColors(tone)
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(12.dp), tint = fg); Spacer(Modifier.size(4.dp)) }
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colors.primary, trackColor: Color = MaterialTheme.colors.sunken) {
    val animatedFraction by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "progress_bar_anim",
    )
    LinearProgressIndicator(
        progress = { animatedFraction },
        modifier = modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
        color = color,
        trackColor = trackColor,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

@Composable
fun IconTile(icon: ImageVector, tint: Color, size: Dp = 40.dp) {
    Box(Modifier.size(size).clip(MaterialTheme.shapes.medium).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size / 2))
    }
}

@Composable
fun Avatar(initials: String, size: Dp = 40.dp, onClick: (() -> Unit)? = null) {
    val c = MaterialTheme.colors
    Box(
        Modifier.size(size).clip(CircleShape).background(c.primary)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, color = c.onPrimary, style = if (size > 48.dp) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    SkeletonLoading(modifier)
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val c = MaterialTheme.colors
    Column(modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(c.sunken), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = c.muted)
        }
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.foreground, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = c.muted, textAlign = TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(16.dp)); action() }
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, title: String = "Something went wrong", modifier: Modifier = Modifier) {
    EmptyState(Icons.Filled.ErrorOutline, title, message, modifier) {
        AppButton("Try again", onRetry, kind = ButtonKind.SECONDARY, fullWidth = false)
    }
}

@Composable
fun InlineBanner(message: String, tone: Tone = Tone.DANGER, modifier: Modifier = Modifier) {
    AppNotice(message = message, tone = tone, modifier = modifier)
}

/** Calm, readable notice shared by permission, routine and blocking screens. */
@Composable
fun AppNotice(
    message: String,
    tone: Tone = Tone.INFO,
    modifier: Modifier = Modifier,
    title: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = MaterialTheme.colors
    val (accent, _) = toneColors(tone)
    AppCard(modifier = modifier, borderColor = accent.copy(alpha = 0.18f), padding = 16.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            IconTile(Icons.Filled.ErrorOutline, accent, 32.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (title != null) Text(title, style = MaterialTheme.typography.titleSmall, color = c.foreground, fontWeight = FontWeight.SemiBold)
                Text(message, style = MaterialTheme.typography.bodySmall, color = c.muted)
                if (actionLabel != null && onAction != null) TextButton(onAction) {
                    Text(actionLabel, color = c.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun OfflineBanner() {
    val c = MaterialTheme.colors
    Row(Modifier.fillMaxWidth().background(c.warningSoft).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.WifiOff, null, tint = c.warning, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(8.dp))
        Text("You're offline — some features may be unavailable.", style = MaterialTheme.typography.bodySmall, color = c.warning)
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = true,
) {
    val c = MaterialTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(message, color = c.muted) },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirmText, color = if (danger) c.danger else c.foreground, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.muted) } },
    )
}

/** "Load more" with "{loaded} of {total} {noun}s"; hidden when nothing is left. */
@Composable
fun LoadMoreFooter(loaded: Int, total: Int, noun: String, hasMore: Boolean, loading: Boolean, onLoadMore: () -> Unit) {
    if (!hasMore) return
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AppButton(if (loading) "Loading…" else "Load more", onLoadMore, kind = ButtonKind.SECONDARY, loading = loading, fullWidth = false)
        Spacer(Modifier.height(6.dp))
        Text("$loaded of $total ${noun}s", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colors.faint)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshBox(refreshing: Boolean, onRefresh: () -> Unit, modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize(), content = content)
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colors.muted, modifier = modifier.padding(bottom = 8.dp))
}

@Composable
fun MenuRow(icon: ImageVector, label: String, onClick: (() -> Unit)?, trailing: (@Composable () -> Unit)? = null, tint: Color? = null, value: String? = null) {
    val c = MaterialTheme.colors
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint ?: c.muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint ?: c.foreground, modifier = Modifier.weight(1f))
        if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium, color = c.muted)
        trailing?.invoke()
    }
}

val ScreenPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)

@Composable
fun Divider() { Box(Modifier.fillMaxWidth().height(0.5.dp).background(MaterialTheme.colors.border.copy(alpha = 0.35f))) }

/** The Habit Maker brand logo (rounded-square artwork). */
@Composable
fun AppLogo(size: Dp = 72.dp) {
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(com.aravind.habitmaker.R.drawable.app_logo),
        contentDescription = "Habit Maker",
        modifier = Modifier.size(size),
    )
}
