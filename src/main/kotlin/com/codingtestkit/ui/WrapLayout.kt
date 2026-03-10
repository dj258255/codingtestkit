package com.codingtestkit.ui

import java.awt.Container
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JPanel

/**
 * FlowLayout 확장 — 컴포넌트가 줄바꿈될 때 컨테이너 높이를 올바르게 계산.
 * 표준 FlowLayout은 한 줄 높이만 반환해서 줄바꿈 시 잘림.
 */
class WrapLayout(align: Int = LEFT, hgap: Int = 0, vgap: Int = 0) : FlowLayout(align, hgap, vgap) {

    override fun preferredLayoutSize(target: Container): Dimension {
        return layoutSize(target, true)
    }

    override fun minimumLayoutSize(target: Container): Dimension {
        return layoutSize(target, false)
    }

    private fun layoutSize(target: Container, preferred: Boolean): Dimension {
        synchronized(target.treeLock) {
            val targetWidth = if (target.size.width > 0) target.size.width else target.parent?.width ?: Int.MAX_VALUE
            val insets = target.insets
            val maxWidth = targetWidth - insets.left - insets.right - hgap * 2
            var rowWidth = 0
            var rowHeight = 0
            var totalHeight = insets.top + insets.bottom + vgap * 2
            var maxRowWidth = 0

            for (comp in target.components) {
                if (!comp.isVisible) continue
                val d = if (preferred) comp.preferredSize else comp.minimumSize
                if (rowWidth + d.width > maxWidth && rowWidth > 0) {
                    maxRowWidth = maxOf(maxRowWidth, rowWidth)
                    totalHeight += rowHeight + vgap
                    rowWidth = 0
                    rowHeight = 0
                }
                rowWidth += d.width + hgap
                rowHeight = maxOf(rowHeight, d.height)
            }
            maxRowWidth = maxOf(maxRowWidth, rowWidth)
            totalHeight += rowHeight

            return Dimension(maxRowWidth + insets.left + insets.right, totalHeight)
        }
    }
}

/**
 * WrapLayout + BoxLayout 호환 패널.
 * BoxLayout은 maximumSize로 세로 확장을 결정하므로,
 * preferredSize 높이를 maximumSize로 반환하여 불필요한 여백 방지.
 */
class WrapPanel(align: Int = FlowLayout.LEFT, hgap: Int = 0, vgap: Int = 0) : JPanel(WrapLayout(align, hgap, vgap)) {
    override fun getMaximumSize(): Dimension {
        return Dimension(Int.MAX_VALUE, preferredSize.height)
    }
}
