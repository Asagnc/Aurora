package io.github.asagnc.aurora.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.itos.coui.shape.CouiShape
import com.itos.coui.theme.CouiTheme

internal val LocalFormEnabled = staticCompositionLocalOf { true }

/** Native editing with an external caption and one COUI field surface. Secrets stay in caller memory. */
@Composable internal fun AuroraInput(
    value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = LocalFormEnabled.current, singleLine: Boolean = false,
    label: String? = null, placeholder: (@Composable () -> Unit)? = null,
    prefix: (@Composable () -> Unit)? = null, trailingIcon: (@Composable () -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    shape: Shape = CouiShape(14.dp, 2f), textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    var focused by remember { mutableStateOf(false) }
    val container = if (CouiTheme.isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.05f)
    val border by animateColorAsState(if (focused && enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else Color.Transparent,
        animationSpec = tween(150))
    BasicTextField(value, onValueChange, modifier.onFocusChanged { focused = it.isFocused }.semantics { if (label != null) contentDescription = label }, enabled = enabled,
        singleLine = singleLine, textStyle = textStyle.copy(color = if (enabled) AuroraInk else AuroraMuted),
        visualTransformation = visualTransformation, keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), decorationBox = { editor ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (label != null) CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodySmall,
                    LocalContentColor provides if (focused && enabled) MaterialTheme.colorScheme.primary else AuroraMuted) {
                    Text(label, Modifier.padding(horizontal = 4.dp).clearAndSetSemantics {})
                }
                Row(Modifier.fillMaxWidth().clip(shape).background(container).border(1.dp, border, shape)
                    .padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    prefix?.invoke()
                    Box(Modifier.weight(1f).padding(vertical = 11.dp).heightIn(min = 22.dp), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) CompositionLocalProvider(LocalTextStyle provides textStyle, LocalContentColor provides AuroraMuted) {
                            Box(if (label != null) Modifier.clearAndSetSemantics {} else Modifier) {
                                if (placeholder != null) placeholder() else label?.let { Text(it) }
                            }
                        }
                        editor()
                    }
                    trailingIcon?.invoke()
                }
            }
        })
}
