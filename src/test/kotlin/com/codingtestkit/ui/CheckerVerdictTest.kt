package com.codingtestkit.ui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * 커스텀 체커 판정 3상태 검증 (이슈 #36).
 *
 * 체커의 주 용도는 '기대 출력이 없는 생성 케이스'를 대신 판정하는 것이다.
 * 판정할 수 없는 경우를 FAIL로 접으면 사용자가 자기 풀이를 의심하게 되므로,
 * 보류(null → 중립)가 반드시 표현 가능해야 한다.
 */
class CheckerVerdictTest {

    private fun verdict(out: String) = TestPanel.parseCheckerVerdict(out)

    @Test
    fun `OK and AC pass`() {
        assertEquals(true, verdict("OK"))
        assertEquals(true, verdict("AC"))
        assertEquals(true, verdict("ok"))
        assertEquals(true, verdict("OK 판정 근거 어쩌고"))
        assertEquals(true, verdict("OK\n추가 설명"))
    }

    @Test
    fun `CHECK UNKNOWN SKIP hold the verdict`() {
        assertNull(verdict("CHECK"))
        assertNull(verdict("UNKNOWN"))
        assertNull(verdict("SKIP"))
        assertNull(verdict("check 기대 출력이 없음"))
    }

    @Test
    fun `a checker that prints nothing is not a failure`() {
        assertNull(verdict(""))
        assertNull(verdict("   \n  "))
    }

    @Test
    fun `anything else fails`() {
        assertEquals(false, verdict("WRONG"))
        assertEquals(false, verdict("WA"))
        assertEquals(false, verdict("틀렸습니다"))
    }
}
