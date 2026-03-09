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
            "https://docs.oracle.com/en/java/javase/21/docs/api/java.base/module-summary.html",
            listOf(
                "String" to "https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/String.html",
                "Arrays" to "https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Arrays.html",
                "ArrayList" to "https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ArrayList.html",
                "HashMap" to "https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/HashMap.html",
                "Collections" to "https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Collections.html",
                "PriorityQueue" to "https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/PriorityQueue.html",
                "Math" to "https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Math.html",
                "Scanner" to "https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Scanner.html",
                "BufferedReader" to "https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/BufferedReader.html",
                "Stream" to "https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Stream.html"
            )
        ),
        LangRef(
            "C++",
            "https://en.cppreference.com/w/cpp",
            listOf(
                "string" to "https://en.cppreference.com/w/cpp/string/basic_string",
                "vector" to "https://en.cppreference.com/w/cpp/container/vector",
                "map" to "https://en.cppreference.com/w/cpp/container/map",
                "set" to "https://en.cppreference.com/w/cpp/container/set",
                "queue" to "https://en.cppreference.com/w/cpp/container/queue",
                "stack" to "https://en.cppreference.com/w/cpp/container/stack",
                "algorithm" to "https://en.cppreference.com/w/cpp/algorithm",
                "priority_queue" to "https://en.cppreference.com/w/cpp/container/priority_queue",
                "unordered_map" to "https://en.cppreference.com/w/cpp/container/unordered_map",
                "deque" to "https://en.cppreference.com/w/cpp/container/deque"
            )
        ),
        LangRef(
            "Python",
            "https://docs.python.org/3/library/index.html",
            listOf(
                "str" to "https://docs.python.org/3/library/stdtypes.html#text-sequence-type-str",
                "list" to "https://docs.python.org/3/library/stdtypes.html#lists",
                "dict" to "https://docs.python.org/3/library/stdtypes.html#mapping-types-dict",
                "set" to "https://docs.python.org/3/library/stdtypes.html#set-types-set-frozenset",
                "collections" to "https://docs.python.org/3/library/collections.html",
                "itertools" to "https://docs.python.org/3/library/itertools.html",
                "heapq" to "https://docs.python.org/3/library/heapq.html",
                "bisect" to "https://docs.python.org/3/library/bisect.html",
                "math" to "https://docs.python.org/3/library/math.html",
                "functools" to "https://docs.python.org/3/library/functools.html"
            )
        ),
        LangRef(
            "Kotlin",
            "https://kotlinlang.org/api/core/kotlin-stdlib/",
            listOf(
                "String" to "https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-string/",
                "List" to "https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-list/",
                "Map" to "https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-map/",
                "Set" to "https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-set/",
                "Sequence" to "https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.sequences/-sequence/",
                "Array" to "https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-array/",
                "Regex" to "https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.text/-regex/",
                "Comparable" to "https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-comparable/",
                "collections" to "https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/",
                "Scope functions" to "https://kotlinlang.org/docs/scope-functions.html"
            )
        ),
        LangRef(
            "JavaScript",
            "https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference",
            listOf(
                "String" to "https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String",
                "Array" to "https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array",
                "Map" to "https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Map",
                "Set" to "https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Set",
                "Object" to "https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Object",
                "Math" to "https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Math",
                "JSON" to "https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/JSON",
                "RegExp" to "https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/RegExp",
                "Promise" to "https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Promise",
                "Number" to "https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Number"
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
