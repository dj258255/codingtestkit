package com.codingtestkit.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class CodingTestKitActionService {
    var fetchAction: (() -> Unit)? = null
    var runAllAction: (() -> Unit)? = null
    var submitAction: (() -> Unit)? = null
    var translateAction: (() -> Unit)? = null

    var currentPlatform: String? = null
    var currentProblemId: String? = null
    var onStatusChanged: (() -> Unit)? = null

    fun updateStatus(platform: String?, problemId: String?) {
        currentPlatform = platform
        currentProblemId = problemId
        onStatusChanged?.invoke()
    }

    companion object {
        fun getInstance(project: Project): CodingTestKitActionService =
            project.getService(CodingTestKitActionService::class.java)
    }
}
