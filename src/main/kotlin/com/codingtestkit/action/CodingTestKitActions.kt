package com.codingtestkit.action

import com.codingtestkit.service.CodingTestKitActionService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class FetchProblemAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        e.project?.let { CodingTestKitActionService.getInstance(it).fetchAction?.invoke() }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}

class RunAllTestsAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        e.project?.let { CodingTestKitActionService.getInstance(it).runAllAction?.invoke() }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}

class SubmitCodeAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        e.project?.let { CodingTestKitActionService.getInstance(it).submitAction?.invoke() }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}

class TranslateProblemAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        e.project?.let { CodingTestKitActionService.getInstance(it).translateAction?.invoke() }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
