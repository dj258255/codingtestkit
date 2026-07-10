package com.codingtestkit.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * 적대적 생성기가 실제로 저격하는지 검증 (이슈 #36).
 * "anti-hash라고 부르지만 실제론 충돌 안 함" 같은 거짓을 막기 위한 실증 테스트.
 */
class TestCaseGeneratorsTest {

    // ─── anti-hash: 두 문자열이 정말 mod 2^64 다항 해시에서 충돌하는가 ───

    @Test
    fun `anti-hash collides for common odd bases`() {
        val (a, b) = TestCaseGenerators.thueMorseAntiHash(4096)
        assertEquals(a.length, b.length, "두 문자열 길이는 같아야 한다")
        // 실전 해시 base들 + 최악 케이스(mod 8 == 3,5) + 랜덤 홀수
        val bases = longArrayOf(31, 131, 1_000_000_007, 3, 5, 37, 53, 9973, 999_999_937)
        for (base in bases) {
            val ha = TestCaseGenerators.polyHashMod2p64(a, base)
            val hb = TestCaseGenerators.polyHashMod2p64(b, base)
            assertEquals(ha, hb, "base=$base 에서 anti-hash 두 문자열이 mod 2^64 충돌해야 한다")
        }
    }

    @Test
    fun `anti-hash strings are actually different`() {
        val (a, b) = TestCaseGenerators.thueMorseAntiHash(4096)
        assertTrue(a != b, "충돌은 하되 두 문자열은 서로 달라야 의미가 있다")
    }

    @Test
    fun `anti-hash collides for many random odd bases`() {
        val (a, b) = TestCaseGenerators.thueMorseAntiHash(4096)
        val rng = Random(12345)
        repeat(50) {
            val base = (rng.nextLong(2, 2_000_000_000) or 1L) // 홀수 강제
            assertEquals(
                TestCaseGenerators.polyHashMod2p64(a, base),
                TestCaseGenerators.polyHashMod2p64(b, base),
                "랜덤 홀수 base=$base 에서 충돌해야 한다"
            )
        }
    }

    // ─── 퀵정렬 킬러: 정말 비교 횟수가 O(n²)로 폭발하는가 ───

    /** 마지막 원소 피벗 Lomuto 퀵정렬의 비교 횟수 */
    private fun countComparisons(a: IntArray): Long {
        var count = 0L
        fun sort(arr: IntArray, lo: Int, hi: Int) {
            if (lo >= hi) return
            val pivot = arr[hi]; var i = lo
            for (j in lo until hi) { count++; if (arr[j] < pivot) { val t = arr[i]; arr[i] = arr[j]; arr[j] = t; i++ } }
            val t = arr[i]; arr[i] = arr[hi]; arr[hi] = t
            sort(arr, lo, i - 1); sort(arr, i + 1, hi)
        }
        sort(a.copyOf(), 0, a.size - 1)
        return count
    }

    @Test
    fun `quicksort killer forces quadratic comparisons`() {
        val n = 2000
        val killer = TestCaseGenerators.quicksortKiller(n)
        val killerCmp = countComparisons(killer)
        val rng = Random(7)
        val randomCmp = countComparisons(IntArray(n) { rng.nextInt() })
        // 킬러는 n(n-1)/2 ≈ 2e6 규모, 랜덤은 ~n log n ≈ 2e4 규모.
        // 최소한 킬러가 랜덤의 20배 이상이어야 "저격 성공"으로 본다.
        assertTrue(
            killerCmp > randomCmp * 20,
            "킬러 비교=$killerCmp, 랜덤 비교=$randomCmp — 킬러가 훨씬 많아야 한다"
        )
        // 실제로 거의 최악(n(n-1)/2)에 근접하는지도 확인
        val worst = n.toLong() * (n - 1) / 2
        assertTrue(killerCmp >= worst / 2, "킬러 비교=$killerCmp 가 최악치($worst)의 절반 이상이어야 한다")
    }

    @Test
    fun `quicksort killer is a valid permutation`() {
        val killer = TestCaseGenerators.quicksortKiller(500)
        assertEquals((0 until 500).toSet(), killer.toSet(), "0..n-1 순열이어야 한다")
    }

    // ─── 2D 배열 · TreeNode 형식 ───

    @Test
    fun `2d array has valid nested literal shape`() {
        var c = 0L
        val out = TestCaseGenerators.int2dArray(100) { (c++ % 10) }
        assertTrue(out.startsWith("[[") && out.endsWith("]]"), "2D 배열 리터럴 형태여야: $out")
        // 대략 정사각형: 100 → 10x10
        assertEquals(10, out.count { it == '[' } - 1, "행 수(내부 [ 개수)가 대략 sqrt(n)이어야")
    }

    @Test
    fun `tree serialization is a flat bracketed list`() {
        var c = 0L
        val out = TestCaseGenerators.completeTree(7) { c++ }
        assertEquals("[0,1,2,3,4,5,6]", out, "완전 이진 트리 레벨 순서 직렬화")
    }
}
