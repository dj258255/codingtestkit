package com.codingtestkit.ui

import com.codingtestkit.service.I18n
import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBColor
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.ui.JBUI
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.*
import javax.swing.*

class ReferencePanel : JPanel(BorderLayout()) {

    private val useCef = try { JBCefApp.isSupported() } catch (_: Exception) { false }
    private var cefBrowser: JBCefBrowser? = null

    private data class LangRef(
        val label: String,
        val homeUrl: String,
        val shortcuts: List<Pair<String, String>>
    )

    private val languages = listOf(
        LangRef(
            "Java",
            "https://devdocs.io/openjdk~21/",
            listOf(
                "String" to "https://devdocs.io/openjdk~21/java.base/java/lang/string",
                "Arrays" to "https://devdocs.io/openjdk~21/java.base/java/util/arrays",
                "ArrayList" to "https://devdocs.io/openjdk~21/java.base/java/util/arraylist",
                "HashMap" to "https://devdocs.io/openjdk~21/java.base/java/util/hashmap",
                "Collections" to "https://devdocs.io/openjdk~21/java.base/java/util/collections",
                "PriorityQueue" to "https://devdocs.io/openjdk~21/java.base/java/util/priorityqueue",
                "Math" to "https://devdocs.io/openjdk~21/java.base/java/lang/math",
                "Scanner" to "https://devdocs.io/openjdk~21/java.base/java/util/scanner",
                "BufferedReader" to "https://devdocs.io/openjdk~21/java.base/java/io/bufferedreader",
                "Stream" to "https://devdocs.io/openjdk~21/java.base/java/util/stream/stream"
            )
        ),
        LangRef(
            "C++",
            "https://devdocs.io/cpp/",
            listOf(
                "string" to "https://devdocs.io/cpp/string/basic_string",
                "vector" to "https://devdocs.io/cpp/container/vector",
                "map" to "https://devdocs.io/cpp/container/map",
                "set" to "https://devdocs.io/cpp/container/set",
                "queue" to "https://devdocs.io/cpp/container/queue",
                "stack" to "https://devdocs.io/cpp/container/stack",
                "algorithm" to "https://devdocs.io/cpp/algorithm",
                "priority_queue" to "https://devdocs.io/cpp/container/priority_queue",
                "unordered_map" to "https://devdocs.io/cpp/container/unordered_map",
                "deque" to "https://devdocs.io/cpp/container/deque"
            )
        ),
        LangRef(
            "Python",
            "https://devdocs.io/python~3.12/",
            listOf(
                "str" to "https://devdocs.io/python~3.12/library/stdtypes#text-sequence-type-str",
                "list" to "https://devdocs.io/python~3.12/library/stdtypes#lists",
                "dict" to "https://devdocs.io/python~3.12/library/stdtypes#mapping-types-dict",
                "set" to "https://devdocs.io/python~3.12/library/stdtypes#set-types-set-frozenset",
                "collections" to "https://devdocs.io/python~3.12/library/collections",
                "itertools" to "https://devdocs.io/python~3.12/library/itertools",
                "heapq" to "https://devdocs.io/python~3.12/library/heapq",
                "bisect" to "https://devdocs.io/python~3.12/library/bisect",
                "math" to "https://devdocs.io/python~3.12/library/math",
                "functools" to "https://devdocs.io/python~3.12/library/functools"
            )
        ),
        LangRef(
            "Kotlin",
            "https://devdocs.io/kotlin/",
            listOf(
                "String" to "https://devdocs.io/kotlin/api/core/kotlin-stdlib/kotlin/-string/index",
                "List" to "https://devdocs.io/kotlin/api/core/kotlin-stdlib/kotlin.collections/-list/index",
                "Map" to "https://devdocs.io/kotlin/api/core/kotlin-stdlib/kotlin.collections/-map/index",
                "Set" to "https://devdocs.io/kotlin/api/core/kotlin-stdlib/kotlin.collections/-set/index",
                "Sequence" to "https://devdocs.io/kotlin/api/core/kotlin-stdlib/kotlin.sequences/-sequence/index",
                "Array" to "https://devdocs.io/kotlin/api/core/kotlin-stdlib/kotlin/-array/index",
                "Regex" to "https://devdocs.io/kotlin/api/core/kotlin-stdlib/kotlin.text/-regex/index",
                "Comparable" to "https://devdocs.io/kotlin/api/core/kotlin-stdlib/kotlin/-comparable/index",
                "collections" to "https://devdocs.io/kotlin/api/core/kotlin-stdlib/kotlin.collections/index",
                "Scope functions" to "https://devdocs.io/kotlin/docs/scope-functions"
            )
        ),
        LangRef(
            "JavaScript",
            "https://devdocs.io/javascript/",
            listOf(
                "String" to "https://devdocs.io/javascript/global_objects/string",
                "Array" to "https://devdocs.io/javascript/global_objects/array",
                "Map" to "https://devdocs.io/javascript/global_objects/map",
                "Set" to "https://devdocs.io/javascript/global_objects/set",
                "Object" to "https://devdocs.io/javascript/global_objects/object",
                "Math" to "https://devdocs.io/javascript/global_objects/math",
                "JSON" to "https://devdocs.io/javascript/global_objects/json",
                "RegExp" to "https://devdocs.io/javascript/global_objects/regexp",
                "Promise" to "https://devdocs.io/javascript/global_objects/promise",
                "Number" to "https://devdocs.io/javascript/global_objects/number"
            )
        )
    )

    private val langCombo = ComboBox(languages.map { it.label }.toTypedArray())
    // 자동 줄바꿈 FlowLayout
    private val shortcutPanel = JPanel(WrapLayout(FlowLayout.LEFT, JBUI.scale(3), JBUI.scale(3)))

    init {
        if (useCef) {
            initCefUI()
        } else {
            add(JLabel(I18n.t("JCEF 미지원 환경입니다. 브라우저에서 직접 참조하세요.",
                "JCEF not supported. Please use your browser.")), BorderLayout.CENTER)
        }
    }

    private fun initCefUI() {
        cefBrowser = JBCefBrowser(languages[0].homeUrl)

        // ── 상단 바: [언어 콤보] [홈] [◀] [▶]  |  바로가기 칩들 ──
        val toolbar = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(4, 6, 0, 6)
        }

        // 왼쪽: 언어 + 네비게이션
        val navPanel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(2), 0))

        langCombo.apply {
            font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(12f).toFloat())
        }
        navPanel.add(langCombo)
        navPanel.add(Box.createHorizontalStrut(JBUI.scale(4)))

        navPanel.add(makeIconButton(AllIcons.Actions.Back, I18n.t("뒤로", "Back")) {
            cefBrowser?.cefBrowser?.goBack()
        })
        navPanel.add(makeIconButton(AllIcons.Actions.Forward, I18n.t("앞으로", "Forward")) {
            cefBrowser?.cefBrowser?.goForward()
        })
        navPanel.add(makeIconButton(AllIcons.Actions.Refresh, I18n.t("홈", "Home")) {
            navigateHome()
        })

        toolbar.add(navPanel, BorderLayout.WEST)

        // 전체 상단 (toolbar + 바로가기)
        val topBox = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0)
        }
        toolbar.alignmentX = LEFT_ALIGNMENT
        topBox.add(toolbar)

        shortcutPanel.border = JBUI.Borders.empty(2, 6, 4, 6)
        shortcutPanel.alignmentX = LEFT_ALIGNMENT
        updateShortcuts(0)
        topBox.add(shortcutPanel)

        add(topBox, BorderLayout.NORTH)
        add(cefBrowser!!.component, BorderLayout.CENTER)

        // JCEF 리사이즈 동기화
        // 1) 직접 리사이즈 시
        addComponentListener(object : java.awt.event.ComponentAdapter() {
            override fun componentResized(e: java.awt.event.ComponentEvent) {
                SwingUtilities.invokeLater {
                    cefBrowser?.component?.revalidate()
                    cefBrowser?.component?.repaint()
                }
            }
        })
        // 2) 다른 탭에서 돌아왔을 때 (탭 숨김 중 리사이즈된 경우)
        addHierarchyListener { e ->
            if ((e.changeFlags and java.awt.event.HierarchyEvent.SHOWING_CHANGED.toLong()) != 0L && isShowing) {
                SwingUtilities.invokeLater {
                    cefBrowser?.component?.revalidate()
                    cefBrowser?.component?.repaint()
                }
            }
        }

        // 스크롤 성능 개선
        cefBrowser!!.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                if (frame?.isMain != true) return
                browser?.executeJavaScript("""
                    (function(){
                        var s=document.createElement('style');
                        s.textContent='html,body{scroll-behavior:smooth}*{-webkit-overflow-scrolling:touch}';
                        document.head.appendChild(s);
                    })();
                """.trimIndent(), browser.url, 0)
            }
        }, cefBrowser!!.cefBrowser)

        // 이벤트
        langCombo.addActionListener {
            val idx = langCombo.selectedIndex
            updateShortcuts(idx)
            navigateHome()
        }
    }

    private fun makeIconButton(icon: Icon, tooltip: String, action: () -> Unit): JButton {
        return JButton(icon).apply {
            toolTipText = tooltip
            isBorderPainted = false
            isContentAreaFilled = false
            isFocusPainted = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            preferredSize = Dimension(JBUI.scale(24), JBUI.scale(24))
            addActionListener { action() }
        }
    }

    private fun updateShortcuts(langIndex: Int) {
        shortcutPanel.removeAll()
        val lang = languages[langIndex]
        for ((label, url) in lang.shortcuts) {
            shortcutPanel.add(makeChipButton(label) { cefBrowser?.loadURL(url) })
        }
        shortcutPanel.revalidate()
        shortcutPanel.repaint()
    }

    private fun makeChipButton(text: String, action: () -> Unit): JButton {
        return object : JButton(text) {
            init {
                font = font.deriveFont(JBUI.scaleFontSize(11f).toFloat())
                foreground = JBColor(Color(60, 60, 60), Color(200, 200, 200))
                isContentAreaFilled = false
                isFocusPainted = false
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                border = BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(JBColor(Color(180, 180, 180), Color(80, 80, 80)), 1, true),
                    JBUI.Borders.empty(2, 8)
                )
                addActionListener { action() }
            }

            override fun paintComponent(g: Graphics) {
                val g2 = g as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                if (model.isRollover) {
                    g2.color = JBColor(Color(230, 240, 255), Color(60, 70, 85))
                    g2.fillRoundRect(0, 0, width, height, JBUI.scale(8), JBUI.scale(8))
                }
                super.paintComponent(g)
            }
        }
    }

    private fun navigateHome() {
        val lang = languages[langCombo.selectedIndex]
        cefBrowser?.loadURL(lang.homeUrl)
    }

    /**
     * FlowLayout that wraps to the next line when the container width is exceeded.
     */
    private class WrapLayout(align: Int, hgap: Int, vgap: Int) : FlowLayout(align, hgap, vgap) {
        override fun preferredLayoutSize(target: Container): Dimension {
            return layoutSize(target, true)
        }
        override fun minimumLayoutSize(target: Container): Dimension {
            return layoutSize(target, false)
        }
        private fun layoutSize(target: Container, preferred: Boolean): Dimension {
            synchronized(target.treeLock) {
                val targetWidth = if (target.size.width > 0) target.size.width else Int.MAX_VALUE
                val insets = target.insets
                val maxWidth = targetWidth - insets.left - insets.right - hgap * 2
                var x = 0
                var y = insets.top + vgap
                var rowHeight = 0
                for (comp in target.components) {
                    if (!comp.isVisible) continue
                    val d = if (preferred) comp.preferredSize else comp.minimumSize
                    if (x > 0 && x + d.width > maxWidth) {
                        x = 0
                        y += rowHeight + vgap
                        rowHeight = 0
                    }
                    x += d.width + hgap
                    rowHeight = maxOf(rowHeight, d.height)
                }
                y += rowHeight + vgap
                return Dimension(targetWidth, y + insets.bottom)
            }
        }
    }
}
