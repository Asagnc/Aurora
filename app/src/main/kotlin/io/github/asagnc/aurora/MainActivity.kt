package io.github.asagnc.aurora

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.asagnc.aurora.ui.AuroraScreen
import io.github.asagnc.aurora.ui.AuroraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showTask(intent)
        enableEdgeToEdge()
        setContent { AuroraTheme { AuroraScreen(viewModel()) } }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        showTask(intent)
    }

    private fun showTask(intent: Intent?) {
        intent?.getStringExtra("agentOwner")?.let { owner ->
            intent.removeExtra("agentOwner")
            (application as AuroraApplication).chat.selectConversation(owner)
        }
    }
}
