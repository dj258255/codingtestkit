package com.codingtestkit.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service
@State(name = "CodingTestKitSettings", storages = [Storage("codingtestkit-settings.xml")])
class PluginSettingsService : PersistentStateComponent<PluginSettingsService.SettingsState> {

    /** 임베드 웹페이지(제출·로그인·가져오기 등) 테마 (이슈 #34) */
    enum class EmbedTheme { FOLLOW_IDE, LIGHT, DARK }

    data class SettingsState(
        var generateReadme: Boolean = false,
        var embedTheme: String = EmbedTheme.FOLLOW_IDE.name
    )

    private var state = SettingsState()

    override fun getState(): SettingsState = state
    override fun loadState(state: SettingsState) { this.state = state }

    var generateReadme: Boolean
        get() = state.generateReadme
        set(value) { state.generateReadme = value }

    var embedTheme: EmbedTheme
        get() = try { EmbedTheme.valueOf(state.embedTheme) } catch (_: Exception) { EmbedTheme.FOLLOW_IDE }
        set(value) {
            if (state.embedTheme == value.name) return
            state.embedTheme = value.name
            embedThemeListeners.forEach { runCatching { it() } }
        }

    /** 임베드 테마 변경 리스너 — 이미 렌더된 지문을 즉시 다시 그리기 위해 (I18n과 같은 패턴) */
    private val embedThemeListeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()
    fun addEmbedThemeListener(listener: () -> Unit) { embedThemeListeners.add(listener) }
    fun removeEmbedThemeListener(listener: () -> Unit) { embedThemeListeners.remove(listener) }

    companion object {
        fun getInstance(): PluginSettingsService =
            ApplicationManager.getApplication().getService(PluginSettingsService::class.java)
    }
}
