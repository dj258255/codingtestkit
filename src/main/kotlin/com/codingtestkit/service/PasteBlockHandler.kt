package com.codingtestkit.service

import com.codingtestkit.ui.SettingsPanel
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.ide.CopyPasteManager
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor

class PasteBlockHandler(private val originalHandler: EditorActionHandler) : EditorActionHandler() {

    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
        if (SettingsPanel.ExamModeState.pasteBlockEnabled) {
            val internalText = try {
                CopyPasteManager.getInstance().contents
                    ?.getTransferData(DataFlavor.stringFlavor) as? String
            } catch (_: Exception) { null }

            val systemText = try {
                Toolkit.getDefaultToolkit().systemClipboard
                    .getData(DataFlavor.stringFlavor) as? String
            } catch (_: Exception) { null }

            if (internalText != systemText) {
                Toolkit.getDefaultToolkit().beep()
                return
            }
        }
        originalHandler.execute(editor, caret, dataContext)
    }

    override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext): Boolean {
        return originalHandler.isEnabled(editor, caret, dataContext)
    }
}
