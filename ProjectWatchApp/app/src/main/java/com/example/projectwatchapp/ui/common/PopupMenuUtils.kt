package com.example.projectwatchapp.ui.common

import android.widget.PopupMenu

object PopupMenuUtils {
    fun forceShowIcons(popupMenu: PopupMenu) {
        runCatching {
            val field = PopupMenu::class.java.getDeclaredField("mPopup")
            field.isAccessible = true
            val menuHelper = field.get(popupMenu)
            val classPopupHelper = Class.forName(menuHelper.javaClass.name)
            val setForceIcons = classPopupHelper.getMethod("setForceShowIcon", Boolean::class.javaPrimitiveType)
            setForceIcons.invoke(menuHelper, true)
        }
    }
}
