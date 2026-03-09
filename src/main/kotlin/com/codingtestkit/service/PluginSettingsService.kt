package com.codingtestkit.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service
@State(name = "CodingTestKitSettings", storages = [Storage("codingtestkit-settings.xml")])
class PluginSettingsService : PersistentStateComponent<PluginSettingsService.SettingsState> {

    data class SettingsState(
        var generateReadme: Boolean = false
    )

    private var state = SettingsState()

    override fun getState(): SettingsState = state
    override fun loadState(state: SettingsState) { this.state = state }

    var generateReadme: Boolean
        get() = state.generateReadme
        set(value) { state.generateReadme = value }

    companion object {
        fun getInstance(): PluginSettingsService =
            ApplicationManager.getApplication().getService(PluginSettingsService::class.java)
    }
}
