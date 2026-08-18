package com.codingtestkit.service

import com.codingtestkit.model.CodeTemplate
import com.codingtestkit.model.Language
import com.codingtestkit.model.ProblemSource
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * 플랫폼 기본 템플릿 불변식 검증 (이슈 #35).
 *
 * 이 규칙들은 지금까지 테스트가 하나도 없었고, 그 사이 "한 템플릿이 두 플랫폼의
 * 기본이 될 수 없다"는 결함이 조용히 살아 있었다.
 */
class TemplateServiceTest {

    private fun service() = TemplateService()

    private fun template(name: String, language: Language = Language.JAVA, vararg platforms: ProblemSource) =
        CodeTemplate(name = name, language = language.displayName, code = "// $name")
            .withDefaultPlatforms(platforms.map { it.name }.toSet())

    @Test
    fun `one template can be the default for several platforms`() {
        val s = service()
        s.saveTemplate(template("base", Language.JAVA, ProblemSource.CODEFORCES, ProblemSource.LEETCODE))

        assertEquals("base", s.findPlatformDefault(ProblemSource.CODEFORCES, Language.JAVA)?.name)
        assertEquals("base", s.findPlatformDefault(ProblemSource.LEETCODE, Language.JAVA)?.name)
    }

    @Test
    fun `claiming a platform only releases that platform from the previous holder`() {
        val s = service()
        s.saveTemplate(template("old", Language.JAVA, ProblemSource.CODEFORCES, ProblemSource.LEETCODE))
        s.saveTemplate(template("new", Language.JAVA, ProblemSource.LEETCODE))

        // leetcode만 넘어가고 codeforces 지정은 old에 남아야 한다
        assertEquals("new", s.findPlatformDefault(ProblemSource.LEETCODE, Language.JAVA)?.name)
        assertEquals("old", s.findPlatformDefault(ProblemSource.CODEFORCES, Language.JAVA)?.name)
    }

    @Test
    fun `defaults are scoped per language`() {
        val s = service()
        s.saveTemplate(template("java-one", Language.JAVA, ProblemSource.CODEFORCES))
        s.saveTemplate(template("py-one", Language.PYTHON, ProblemSource.CODEFORCES))

        assertEquals("java-one", s.findPlatformDefault(ProblemSource.CODEFORCES, Language.JAVA)?.name)
        assertEquals("py-one", s.findPlatformDefault(ProblemSource.CODEFORCES, Language.PYTHON)?.name)
    }

    @Test
    fun `unsetting the last platform clears the default`() {
        val s = service()
        s.saveTemplate(template("base", Language.JAVA, ProblemSource.CODEFORCES))
        s.saveTemplate(template("base", Language.JAVA))   // 지정 해제

        assertNull(s.findPlatformDefault(ProblemSource.CODEFORCES, Language.JAVA))
        assertEquals(1, s.getTemplates().size, "해제가 템플릿을 지워서는 안 된다")
    }

    @Test
    fun `v1_7_0 records with only the singular field still resolve`() {
        val s = service()
        // 예전 형식: defaultForPlatforms 없이 defaultForPlatform만 있는 레코드
        s.saveTemplate(CodeTemplate(
            name = "legacy", language = Language.JAVA.displayName, code = "// legacy",
            defaultForPlatform = ProblemSource.CODEFORCES.name
        ))
        assertEquals("legacy", s.findPlatformDefault(ProblemSource.CODEFORCES, Language.JAVA)?.name)
    }

    @Test
    fun `saving keeps both fields in sync for older versions`() {
        val s = service()
        s.saveTemplate(template("base", Language.JAVA, ProblemSource.LEETCODE, ProblemSource.CODEFORCES))
        val saved = s.getTemplates().single()

        assertNotNull(saved.defaultForPlatform, "구버전이 읽을 단수 필드도 채워져야 한다")
        assertTrue(saved.defaultForPlatform in saved.defaultPlatforms())
        assertEquals(2, saved.defaultPlatforms().size)
    }

    @Test
    fun `saving the same name overwrites instead of duplicating`() {
        val s = service()
        s.saveTemplate(template("base"))
        s.saveTemplate(CodeTemplate(name = "base", language = Language.JAVA.displayName, code = "// changed"))

        assertEquals(1, s.getTemplates().size)
        assertEquals("// changed", s.getTemplates().single().code)
    }
}
