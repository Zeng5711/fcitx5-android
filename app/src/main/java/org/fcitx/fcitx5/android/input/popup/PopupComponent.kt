package org.fcitx.fcitx5.android.input.popup

import android.graphics.Rect
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.input.dependency.context
import org.fcitx.fcitx5.android.input.dependency.inputMethodService
import org.fcitx.fcitx5.android.input.dependency.theme
import org.fcitx.fcitx5.android.input.keyboard.KeyAction
import org.fcitx.fcitx5.android.input.keyboard.KeyDef
import org.fcitx.fcitx5.android.input.punctuation.PunctuationComponent
import org.mechdancer.dependency.Dependent
import org.mechdancer.dependency.UniqueComponent
import org.mechdancer.dependency.manager.ManagedHandler
import org.mechdancer.dependency.manager.managedHandler
import org.mechdancer.dependency.manager.must
import splitties.dimensions.dp
import splitties.views.dsl.core.add
import splitties.views.dsl.core.frameLayout
import splitties.views.dsl.core.lParams
import java.util.*

class PopupComponent :
    UniqueComponent<PopupComponent>(), Dependent, ManagedHandler by managedHandler() {

    private val service by manager.inputMethodService()
    private val context by manager.context()
    private val theme by manager.theme()
    private val punctuation: PunctuationComponent by manager.must()

    private val showingEntryUi = HashMap<Int, PopupEntryUi>()
    private val dismissJobs = HashMap<Int, Job>()
    private val freeEntryUi = LinkedList<PopupEntryUi>()

    private val showingContainerUi = HashMap<Int, PopupContainerUi>()

    private val keyBottomMargin by lazy {
        context.dp(ThemeManager.prefs.keyVerticalMargin.getValue())
    }
    private val popupWidth by lazy {
        context.dp(38)
    }
    private val popupHeight by lazy {
        context.dp(116)
    }
    private val popupKeyHeight by lazy {
        context.dp(48)
    }
    private val popupRadius by lazy {
        context.dp(ThemeManager.prefs.keyRadius.getValue()).toFloat()
    }
    private val hideThreshold = 100L

    val root by lazy {
        context.frameLayout {
            isClickable = false
            isFocusable = false
        }
    }

    fun showPopup(viewId: Int, content: String, bounds: Rect) {
        showingEntryUi[viewId]?.apply {
            dismissJobs[viewId]?.also {
                dismissJobs.remove(viewId)?.cancel()
            }
            lastShowTime = System.currentTimeMillis()
            setText(content)
            return
        }
        val popup = (freeEntryUi.poll()
            ?: PopupEntryUi(context, theme, popupKeyHeight, popupRadius)).apply {
            lastShowTime = System.currentTimeMillis()
            setText(content)
        }
        root.apply {
            add(popup.root, lParams(popupWidth, popupHeight) {
                // align popup bottom with key border bottom [^1]
                topMargin = bounds.bottom - popupHeight - keyBottomMargin
                leftMargin = (bounds.left + bounds.right - popupWidth) / 2
            })
        }
        showingEntryUi[viewId] = popup
    }

    fun updatePopup(viewId: Int, content: String) {
        showingEntryUi[viewId]?.setText(content)
    }

    fun showKeyboard(viewId: Int, keyboard: KeyDef.Popup.Keyboard, bounds: Rect) {
        val keys = PopupPreset[keyboard.label] ?: return
        val entryUi = showingEntryUi[viewId]
        if (entryUi != null) {
            entryUi.setText("")
            reallyShowKeyboard(viewId, keys, bounds)
        } else {
            showPopup(viewId, "", bounds)
            // in case popup preview is disabled, wait newly created popup entry to layout
            ContextCompat.getMainExecutor(service).execute {
                reallyShowKeyboard(viewId, keys, bounds)
            }
        }
    }

    private fun reallyShowKeyboard(viewId: Int, keys: Array<String>, bounds: Rect) {
        val labels = if (punctuation.enabled) {
            Array(keys.size) { punctuation.transform(keys[it]) }
        } else keys
        val keyboardUi = PopupKeyboardUi(
            context,
            theme,
            bounds,
            { dismissPopup(viewId) },
            popupRadius,
            popupWidth,
            popupKeyHeight,
            // position popup keyboard higher, because of [^1]
            popupHeight + keyBottomMargin,
            keys,
            labels
        )
        root.apply {
            add(keyboardUi.root, lParams {
                leftMargin = bounds.left + keyboardUi.offsetX
                topMargin = bounds.top + keyboardUi.offsetY
            })
        }
        showingContainerUi[viewId] = keyboardUi
    }

    fun showMenu(viewId: Int, menu: KeyDef.Popup.Menu, bounds: Rect) {
        showingEntryUi[viewId]?.let {
            dismissPopupEntry(viewId, it)
        }
        val menuUi = PopupMenuUi(
            context,
            theme,
            bounds,
            { dismissPopup(viewId) },
            menu.items,
        )
        root.apply {
            add(menuUi.root, lParams {
                leftMargin = bounds.left + menuUi.offsetX
                topMargin = bounds.top + menuUi.offsetY
            })
        }
        showingContainerUi[viewId] = menuUi
    }

    fun changeFocus(viewId: Int, x: Float, y: Float): Boolean {
        return showingContainerUi[viewId]?.changeFocus(x, y) ?: false
    }

    fun triggerFocused(viewId: Int): KeyAction? {
        return showingContainerUi[viewId]?.onTrigger()
    }

    fun dismissPopup(viewId: Int) {
        dismissPopupContainer(viewId)
        showingEntryUi[viewId]?.also {
            val timeLeft = it.lastShowTime + hideThreshold - System.currentTimeMillis()
            if (timeLeft <= 0L) {
                dismissPopupEntry(viewId, it)
            } else {
                dismissJobs[viewId] = service.lifecycleScope.launch {
                    delay(timeLeft)
                    dismissPopupEntry(viewId, it)
                    dismissJobs.remove(viewId)
                }
            }
        }
    }

    private fun dismissPopupContainer(viewId: Int) {
        showingContainerUi[viewId]?.also {
            showingContainerUi.remove(viewId)
            root.removeView(it.root)
        }
    }

    private fun dismissPopupEntry(viewId: Int, popup: PopupEntryUi) {
        showingEntryUi.remove(viewId)
        root.removeView(popup.root)
        freeEntryUi.add(popup)
    }

    fun dismissAll() {
        // avoid modifying collection while iterating
        dismissJobs.forEach { (_, job) ->
            job.cancel()
        }
        dismissJobs.clear()
        // too
        showingContainerUi.forEach { (_, container) ->
            root.removeView(container.root)
        }
        showingContainerUi.clear()
        // too too
        showingEntryUi.forEach { (_, entry) ->
            root.removeView(entry.root)
            freeEntryUi.add(entry)
        }
        showingEntryUi.clear()
    }

    val listener: PopupListener = object : PopupListener {
        override fun onPreview(viewId: Int, content: String, bounds: Rect) {
            showPopup(viewId, content, bounds)
        }

        override fun onPreviewUpdate(viewId: Int, content: String) {
            updatePopup(viewId, content)
        }

        override fun onDismiss(viewId: Int) {
            dismissPopup(viewId)
        }

        override fun onShowKeyboard(viewId: Int, keyboard: KeyDef.Popup.Keyboard, bounds: Rect) {
            showKeyboard(viewId, keyboard, bounds)
        }

        override fun onShowMenu(viewId: Int, menu: KeyDef.Popup.Menu, bounds: Rect) {
            showMenu(viewId, menu, bounds)
        }

        override fun onChangeFocus(viewId: Int, x: Float, y: Float): Boolean {
            return changeFocus(viewId, x, y)
        }

        override fun onTrigger(viewId: Int): KeyAction? {
            return triggerFocused(viewId)
        }
    }
}
