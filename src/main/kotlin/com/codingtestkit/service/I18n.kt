package com.codingtestkit.service

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager

object I18n {

    enum class Lang(val displayName: String) {
        KO("한국어"),
        EN("English")
    }

    var currentLang: Lang = Lang.KO
        private set

    /** 언어 변경 리스너. UI가 즉시 다시 그려지도록 (도구 창 콘텐츠 재생성 등). */
    private val changeListeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    fun addChangeListener(listener: () -> Unit) { changeListeners.add(listener) }
    fun removeChangeListener(listener: () -> Unit) { changeListeners.remove(listener) }

    init {
        currentLang = loadSavedLanguage()
    }

    fun setLanguage(lang: Lang) {
        if (lang == currentLang) return
        currentLang = lang
        if (ApplicationManager.getApplication() != null) {
            PropertiesComponent.getInstance().setValue("codingtestkit.language", lang.name)
        }
        changeListeners.forEach { runCatching { it() } }
    }

    /** 현재 언어에 따라 한국어/영어 문자열 반환 */
    fun t(ko: String, en: String): String = when (currentLang) {
        Lang.KO -> ko
        Lang.EN -> en
    }

    private fun loadSavedLanguage(): Lang {
        if (ApplicationManager.getApplication() == null) return Lang.KO
        val saved = PropertiesComponent.getInstance().getValue("codingtestkit.language", "KO")
        return try { Lang.valueOf(saved) } catch (_: Exception) { Lang.KO }
    }
}
