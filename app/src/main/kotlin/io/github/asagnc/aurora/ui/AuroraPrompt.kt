package io.github.asagnc.aurora.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.Dialog

/** One action layout for prompts; long content scrolls while actions remain reachable. */
@Composable internal fun AuroraPrompt(title: String, confirmLabel: String, confirm: () -> Unit,
    dismiss: () -> Unit, message: String? = null, cancelLabel: String? = null,
    cancel: () -> Unit = dismiss, confirmEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit = {}) {
    Dialog(onDismissRequest = dismiss, title = title, message = message,
        confirmAction = CouiDialogAction(confirmLabel, confirm, confirmEnabled),
        dismissAction = cancelLabel?.let { CouiDialogAction(it, cancel) }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
