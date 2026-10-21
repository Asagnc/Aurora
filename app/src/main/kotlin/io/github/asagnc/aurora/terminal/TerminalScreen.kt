package io.github.asagnc.aurora.terminal

import android.graphics.Canvas
import android.graphics.Typeface
import com.termux.terminal.*
import com.termux.view.TerminalRenderer

/** Parsing belongs to the drain worker. Drawing and snapshots share this short-held lock. */
internal class TerminalScreen(request: PtyRequest, private val reply: (ByteArray) -> Unit) {
    private val lock = Any()
    private val output = object : TerminalOutput() {
        override fun write(data: ByteArray, offset: Int, count: Int) { reply(data.copyOfRange(offset, offset + count)) }
        override fun titleChanged(oldTitle: String?, newTitle: String?) = Unit
        override fun onCopyTextToClipboard(text: String?) = Unit
        override fun onPasteTextFromClipboard() = Unit
        override fun onBell() = Unit
        override fun onColorsChanged() = Unit
    }
    private val emulator = TerminalEmulator(output, request.columns, request.rows, 0, 0,
        minOf(5000, 65_536 / request.columns).coerceAtLeast(request.rows), QuietTerminalClient).apply { setMaximumBufferCells(65_536) }
    fun append(bytes: ByteArray, count: Int) = synchronized(lock) { emulator.append(bytes, count) }
    fun resize(columns: Int, rows: Int) = synchronized(lock) { emulator.resize(columns, rows, 0, 0) }
    fun snapshot(): String = synchronized(lock) { emulator.getSelectedText(0, 0, emulator.mColumns - 1, emulator.mRows - 1) }
    fun transcript(): String = synchronized(lock) { emulator.screen.transcriptText }
    fun draw(canvas: Canvas, renderer: TerminalRenderer, scroll: Int): Int = synchronized(lock) {
        val top = scroll.coerceIn(-emulator.screen.activeTranscriptRows, 0)
        renderer.render(emulator, canvas, top, -1, -1, -1, -1)
        top
    }
    fun key(code: Int, modifiers: Int): String? = synchronized(lock) {
        KeyHandler.getCode(code, modifiers, emulator.isCursorKeysApplicationMode, emulator.isKeypadApplicationMode)
    }
    fun paste(text: String) = synchronized(lock) { emulator.paste(text) }
}

/** Terminal content must not be written into Android logs or change system clipboard implicitly. */
private object QuietTerminalClient : TerminalSessionClient {
    override fun onTerminalCursorStateChange(state: Boolean) = Unit
    override fun getTerminalCursorStyle(): Int? = null
    override fun logError(tag: String?, message: String?) = Unit
    override fun logWarn(tag: String?, message: String?) = Unit
    override fun logInfo(tag: String?, message: String?) = Unit
    override fun logDebug(tag: String?, message: String?) = Unit
    override fun logVerbose(tag: String?, message: String?) = Unit
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) = Unit
    override fun logStackTrace(tag: String?, e: Exception?) = Unit
}
