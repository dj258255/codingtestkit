package com.codingtestkit.service

import com.intellij.ide.util.PropertiesComponent

object I18n {

    enum class Lang(val displayName: String) {
        KO("한국어"),
        EN("English")
    }

    var currentLang: Lang = Lang.KO
        private set

    init {
        val saved = PropertiesComponent.getInstance().getValue("codingtestkit.language", "KO")
        currentLang = try { Lang.valueOf(saved) } catch (_: Exception) { Lang.KO }
    }

    fun setLanguage(lang: Lang) {
        currentLang = lang
        PropertiesComponent.getInstance().setValue("codingtestkit.language", lang.name)
    }

    /** 현재 언어에 따라 한국어/영어 문자열 반환 */
    fun t(ko: String, en: String): String = when (currentLang) {
        Lang.KO -> ko
        Lang.EN -> en
    }
}
