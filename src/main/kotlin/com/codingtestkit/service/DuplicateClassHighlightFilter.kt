package com.codingtestkit.service

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiFile

/**
 * problems/ 폴더와 src/ 간의 중복 클래스 오류를 숨기는 필터.
 *
 * IntelliJ의 "duplicate class found in file" 오류는 inspection이 아닌
 * 컴파일러 수준 오류라 설정에서 끌 수 없고, HighlightInfoFilter로만 제어 가능.
 * (Lombok 플러그인도 동일한 방식으로 false-positive 오류를 숨김)
 */
class DuplicateClassHighlightFilter : HighlightInfoFilter {
    override fun accept(info: HighlightInfo, file: PsiFile?): Boolean {
        if (file == null) return true
        if (info.severity != HighlightSeverity.ERROR) return true
        val desc = info.description ?: return true
        if (file.virtualFile == null) return true

        // 중복 클래스 오류 메시지가 problems/ 내 파일을 가리키면 숨김
        // 예: "파일 '/path/problems/.../Main.java'에서 중복된 클래스가 발견되었습니다"
        // Windows는 메시지의 경로가 \ 구분자로 나오므로 통일 후 비교
        if (desc.replace('\\', '/').contains("/problems/")) return false

        return true
    }
}
