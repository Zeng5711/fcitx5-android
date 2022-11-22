package org.fcitx.fcitx5.android.input.candidates

import android.content.Context
import android.graphics.Color
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.keyboard.CustomGestureView
import org.fcitx.fcitx5.android.utils.pressHighlightDrawable
import splitties.views.dsl.core.*
import splitties.views.gravityTopStart
import splitties.views.gravityCenter

class CandidateItemUi(override val ctx: Context, theme: Theme) : Ui {

    companion object {
        val systemTouchSounds by AppPrefs.getInstance().keyboard.systemTouchSounds
    }

    val text = textView {
        textSize = 20f // sp
        isSingleLine = true
        gravity = gravityCenter
        setTextColor(theme.keyTextColor)
    }
    
     val index = textView {
        textSize = 5f
        isSingleLine = true
        gravity = gravityCenter
        setTextColor(Color.RED)
    }

    override val root = view(::CustomGestureView) {
        isSoundEffectsEnabled = systemTouchSounds
        background = pressHighlightDrawable(theme.keyPressHighlightColor)

        add(text, lParams(wrapContent, matchParent) {
            gravity = gravityCenter
        })
        
        add(index,lParams(wrapContent,wrapContent){
            gravity = gravityTopStart
        })
    }
}
