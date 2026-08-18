package com.codingtestkit.service

import com.codingtestkit.model.CodeTemplate
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(name = "CodingTestKitTemplates", storages = [Storage("codingtestkit-templates.xml")])
class TemplateService : PersistentStateComponent<TemplateService.TemplateState> {

    data class TemplateState(
        var templatesJson: String = "[]"
    )

    private var state = TemplateState()
    private val gson = Gson()

    override fun getState(): TemplateState = state

    override fun loadState(state: TemplateState) {
        this.state = state
    }

    fun getTemplates(): MutableList<CodeTemplate> {
        return try {
            val type = object : TypeToken<MutableList<CodeTemplate>>() {}.type
            gson.fromJson(state.templatesJson, type) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun saveTemplate(template: CodeTemplate) {
        val templates = getTemplates()
        // 플랫폼 기본은 (플랫폼, 언어)당 하나 — 새로 지정하면 같은 조합을 쥔
        // 다른 템플릿에서 그 플랫폼만 떼어낸다 (이슈 #35).
        // 한 템플릿이 여러 플랫폼의 기본일 수 있으므로 다른 플랫폼 지정은 건드리지 않는다.
        val claimed = template.defaultPlatforms()
        if (claimed.isNotEmpty()) {
            for (other in templates) {
                if (other.name == template.name || other.language != template.language) continue
                val remaining = other.defaultPlatforms() - claimed
                if (remaining.size != other.defaultPlatforms().size) {
                    val updated = other.withDefaultPlatforms(remaining)
                    other.defaultForPlatform = updated.defaultForPlatform
                    other.defaultForPlatforms = updated.defaultForPlatforms
                }
            }
        }
        val existingIndex = templates.indexOfFirst { it.name == template.name }
        if (existingIndex >= 0) {
            templates[existingIndex] = template
        } else {
            templates.add(template)
        }
        state.templatesJson = gson.toJson(templates)
    }

    /**
     * (플랫폼, 언어)로 지정된 기본 템플릿 (이슈 #35).
     * 새 문제 파일 생성 시 하드코딩 스텁 대신 이 템플릿이 초기 코드가 된다.
     */
    fun findPlatformDefault(source: com.codingtestkit.model.ProblemSource, language: com.codingtestkit.model.Language): CodeTemplate? =
        getTemplates().find { source.name in it.defaultPlatforms() && it.language == language.displayName }

    fun deleteTemplate(name: String) {
        val templates = getTemplates()
        templates.removeAll { it.name == name }
        state.templatesJson = gson.toJson(templates)
    }

    fun getTemplate(name: String): CodeTemplate? {
        return getTemplates().find { it.name == name }
    }

    companion object {
        fun getInstance(project: Project): TemplateService {
            return project.getService(TemplateService::class.java)
        }
    }
}
