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
        set(value) { state.embedTheme = value.name }

    companion object {
        fun getInstance(): PluginSettingsService =
            ApplicationManager.getApplication().getService(PluginSettingsService::class.java)
    }
}
