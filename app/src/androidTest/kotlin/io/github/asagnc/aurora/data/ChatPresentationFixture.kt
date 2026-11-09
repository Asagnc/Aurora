package io.github.asagnc.aurora.data

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.ChatMessage
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit host-driven visual fixture; setup/cleanup are not counted as acceptance tests. */
class ChatPresentationFixture {
    @Test fun manageOwnedFixture() { runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        val nonce = args.getString("auroraChatUiNonce")
        assumeTrue("Opt-in presentation fixture only", nonce != null)
        require(nonce!!.matches(Regex("[a-z0-9]{16}")))
        val store = (instrumentation.targetContext.applicationContext as AuroraApplication).conversations
        val title = "UI 资格样例 · $nonce"
        when (args.getString("auroraChatUiStage")) {
            "prepare" -> {
                require(store.searchTitle(title).none { it.title == title })
                val owner = store.create(title)
                val now = System.currentTimeMillis()
                store.appendMessage(owner, ChatMessage(role = "user", text = "界面验收样例：请检查消息时间和复制位置。",
                    createdAt = now - 86_400_000))
                store.appendMessage(owner, ChatMessage(role = "assistant", text =
                    "这是本地界面夹具，没有调用模型。\n\n复制操作应在回复下方；用户消息可长按选取。\n\n```kotlin\nval preview = true\n```",
                    createdAt = now))
                instrumentation.sendStatus(0, Bundle().apply { putString("auroraChatUiOwner", owner) })
            }
            "cleanup" -> {
                val owner = requireNotNull(args.getString("auroraChatUiOwner"))
                require(store.header(owner)?.title == title)
                store.write { db -> check(db.delete("conversations", "id = ? AND title = ?", arrayOf(owner, title)) == 1) }
                check(store.header(owner) == null)
            }
            else -> error("Explicit prepare/cleanup stage required")
        }
    } }
}
