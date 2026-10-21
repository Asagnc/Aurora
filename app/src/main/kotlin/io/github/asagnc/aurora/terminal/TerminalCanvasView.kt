package io.github.asagnc.aurora.terminal

import android.content.Context
import android.graphics.*
import android.text.InputType
import android.view.*
import android.view.inputmethod.*
import com.termux.terminal.KeyHandler
import com.termux.view.TerminalRenderer

/** Native Canvas terminal inside the Compose page; no WebView/JS data bridge. */
internal class TerminalCanvasView(context: Context) : View(context) {
    var screen: TerminalScreen? = null
    var send: (String) -> Unit = {}
    var resized: (Int, Int) -> Unit = { _, _ -> }
    private var font = 14f
    private var scroll = 0
    private var down = 0f
    private var moved = false
    private var renderer = TerminalRenderer(pixels(), Typeface.MONOSPACE)
    private val metrics = Paint().apply { typeface = Typeface.MONOSPACE; textSize = pixels().toFloat() }
    private val scale = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            font = (font * detector.scaleFactor).coerceIn(9f, 24f)
            renderer = TerminalRenderer(pixels(), Typeface.MONOSPACE)
            metrics.textSize = pixels().toFloat(); layoutTerminal(); invalidate()
            return true
        }
    })
    init { isFocusableInTouchMode = true; importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS }
    private fun pixels() = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, font, resources.displayMetrics).toInt().coerceAtLeast(1)
    private fun layoutTerminal() {
        val spacing = kotlin.math.ceil(metrics.fontSpacing.toDouble())
        val offset = spacing + kotlin.math.ceil(metrics.ascent().toDouble())
        if (width > 0 && height > 0) resized((width / metrics.measureText("X")).toInt().coerceIn(2, 300),
            ((height - offset) / spacing).toInt().coerceIn(2, 200))
    }
    fun fit() = layoutTerminal()
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { layoutTerminal() }
    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        screen?.let { scroll = it.draw(canvas, renderer, scroll) }
    }
    fun follow() { scroll = 0; postInvalidateOnAnimation() }
    override fun onCheckIsTextEditor() = true
    override fun onCreateInputConnection(info: EditorInfo): InputConnection {
        info.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        info.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        return object : BaseInputConnection(this, false) {
            private val composition = android.text.SpannableStringBuilder()
            override fun getEditable(): android.text.Editable = composition
            override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
                composition.replace(0, composition.length, text ?: ""); return true
            }
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                composition.clear(); text?.let { send(it.toString()) }; return true
            }
            override fun finishComposingText(): Boolean {
                val pending = composition.toString(); composition.clear()
                if (pending.isNotEmpty()) send(pending)
                return true
            }
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (composition.isNotEmpty()) composition.delete((composition.length - beforeLength.coerceAtLeast(0)).coerceAtLeast(0), composition.length)
                else repeat(beforeLength.coerceIn(0, 100)) { send("\u007f") }
                return true
            }
        }
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val modifiers = (if (event.isCtrlPressed) KeyHandler.KEYMOD_CTRL else 0) or
            (if (event.isAltPressed) KeyHandler.KEYMOD_ALT else 0) or (if (event.isShiftPressed) KeyHandler.KEYMOD_SHIFT else 0)
        screen?.key(keyCode, modifiers)?.let { send(it); return true }
        val unicode = event.getUnicodeChar(event.metaState and (KeyEvent.META_CTRL_MASK or KeyEvent.META_ALT_MASK).inv())
        if (unicode != 0) {
            val code = if (event.isCtrlPressed && unicode in 64..127) unicode and 31 else unicode
            send((if (event.isAltPressed) "\u001b" else "") + String(Character.toChars(code))); return true
        }
        return super.onKeyDown(keyCode, event)
    }
    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        scale.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { down = event.y; moved = false; return true }
            MotionEvent.ACTION_MOVE -> {
                if (!scale.isInProgress) {
                    val rows = ((event.y - down) / metrics.fontSpacing).toInt()
                    if (rows != 0) { scroll -= rows; down = event.y; moved = true; invalidate() }
                }
                return true
            }
            MotionEvent.ACTION_UP -> { if (!moved && !scale.isInProgress) performClick(); return true }
        }
        return true
    }
    override fun performClick(): Boolean {
        super.performClick(); requestFocus()
        windowInsetsController?.show(WindowInsets.Type.ime())
        return true
    }
}
