package com.codingtestkit.ui

/**
 * 적대적(adversarial) 테스트 케이스 생성 코어 (이슈 #36).
 *
 * UI에서 분리한 순수 함수 모음 — 유닛 테스트로 "실제로 저격이 되는지"를 검증한다.
 * (퀵정렬 킬러는 비교 횟수가 O(n²)로 폭발하는지, anti-hash는 두 문자열이 실제로
 *  mod 2^64 다항 해시에서 충돌하는지 테스트에서 확인)
 */
object TestCaseGenerators {

    // ─── 퀵정렬 킬러 (McIlroy antiquicksort 적대자) ───

    /**
     * 고전 단일 피벗 퀵정렬(마지막 원소 피벗, Lomuto)을 O(n²)로 모는 킬러 배열.
     *
     * McIlroy 적대자 방식: 정렬을 "지연 비교자"로 구동하면서 아직 값이 안 정해진
     * 원소들을 비교 시점에 나쁜 순서로 확정한다. 결과 배열을 실제 같은 퀵정렬에
     * 넣으면 매번 최악의 피벗이 골라져 비교가 n²/2 규모로 폭발한다.
     *
     * 주의: introsort(C++ std::sort)·TimSort(Java 객체 정렬)에는 무효 —
     * 이들은 O(n log n)을 보장한다. 순진한 손수 퀵정렬·마지막/중앙 피벗 저격용.
     */
    fun quicksortKiller(n: Int): IntArray {
        if (n <= 1) return IntArray(n)
        val GAS = -1
        val value = IntArray(n) { GAS }   // value[i] = 아직 안 정해졌으면 GAS
        var nsolid = 0                     // 지금까지 확정된 값의 개수
        var candidate = 0                  // 다음 피벗 후보 인덱스

        // value 기준 비교자. 두 미확정 원소를 비교하면 후보가 아닌 쪽을 즉시 확정(작게).
        val cmp = Comparator<Int> { x, y ->
            if (value[x] == GAS && value[y] == GAS) {
                if (x == candidate) value[x] = nsolid++ else value[y] = nsolid++
            }
            when {
                value[x] == GAS -> { candidate = x; 1 }   // 미확정은 가장 큰 값처럼
                value[y] == GAS -> { candidate = y; -1 }
                else -> value[x] - value[y]
            }
        }

        // 인덱스 배열을 고전 퀵정렬(마지막 피벗)로 정렬 — 비교자가 값을 확정시킨다
        val perm = IntArray(n) { it }
        lomutoQuicksort(perm, 0, n - 1, cmp)

        // 남은 미확정 원소들에 나머지 값 부여
        for (i in 0 until n) if (value[i] == GAS) value[i] = nsolid++
        return value
    }

    /** 마지막 원소 피벗 Lomuto 퀵정렬 (킬러 생성 구동용) */
    private fun lomutoQuicksort(a: IntArray, lo: Int, hi: Int, cmp: Comparator<Int>) {
        if (lo >= hi) return
        val pivot = a[hi]
        var i = lo
        for (j in lo until hi) {
            if (cmp.compare(a[j], pivot) < 0) { val t = a[i]; a[i] = a[j]; a[j] = t; i++ }
        }
        val t = a[i]; a[i] = a[hi]; a[hi] = t
        lomutoQuicksort(a, lo, i - 1, cmp)
        lomutoQuicksort(a, i + 1, hi, cmp)
    }

    // ─── anti-hash (Thue–Morse, mod 2^64 다항 해시 저격) ───

    /**
     * mod 2^64(unsigned overflow) 다항 해시를 충돌시키는 두 문자열을 만든다.
     *
     * 원리: Thue–Morse ±1 수열의 생성함수가 ∏(1 − x^(2^j))이라, base가 홀수면
     * 각 인수 (1 − base^(2^j))의 2-adic 값이 누적돼 길이 2^k가 충분히 크면
     * H(A) ≡ H(B) (mod 2^64)가 성립한다 (A=Thue–Morse, B=여집합). 실전 해시 base는
     * 전부 홀수(31, 131, 1e9+7…)라 base를 몰라도 결정적으로 충돌한다.
     *
     * length는 2의 거듭제곱으로 올림(최소 4096 — 모든 홀수 base 충돌 보장).
     * @return (A, B) 두 문자열. 같은 base·mod 2^64 다항 해시에서 같은 값.
     */
    /**
     * Thue–Morse 충돌 문자열 쌍.
     *
     * 주의: [length]는 '최소 길이'다. 충돌 성질이 2의 거듭제곱 길이에서만 성립하므로
     * 실제 길이는 length 이상의 2의 거듭제곱(최소 4096)으로 올림된다 — 요청한 길이가
     * 그대로 나오지 않는다는 뜻이라 문법 문서에도 같은 설명을 적어 두었다 (이슈 #36).
     */
    fun thueMorseAntiHash(length: Int): Pair<String, String> {
        // 2^10=1024면 최악 base도 충돌하지만, 여유를 두어 최소 4096
        var len = 1
        while (len < length || len < 4096) len = len shl 1
        val a = StringBuilder(len)
        val b = StringBuilder(len)
        for (i in 0 until len) {
            val bit = Integer.bitCount(i) and 1   // Thue–Morse: i의 1비트 수 패리티
            a.append(if (bit == 0) 'a' else 'b')
            b.append(if (bit == 0) 'b' else 'a')  // 여집합
        }
        return a.toString() to b.toString()
    }

    /** mod 2^64 다항 해시 (Long 오버플로 = mod 2^64). 테스트·검증용 공개 함수. */
    fun polyHashMod2p64(s: String, base: Long): Long {
        var h = 0L
        for (c in s) h = h * base + c.code.toLong()   // Long 오버플로 = 자동 mod 2^64
        return h
    }

    // ─── 2D 배열 · TreeNode (LeetCode 파라미터 타입) ───

    /** n개 원소를 담는 대략 정사각형 2D 정수 배열 리터럴 [[..],[..]] */
    fun int2dArray(n: Int, valueOf: () -> Long): String {
        val rows = Math.max(1, Math.ceil(Math.sqrt(n.toDouble())).toInt())
        val cols = Math.max(1, Math.ceil(n.toDouble() / rows).toInt())
        return buildString(n * 8) {
            append('[')
            for (r in 0 until rows) {
                if (r > 0) append(',')
                append('[')
                for (c in 0 until cols) { if (c > 0) append(','); append(valueOf()) }
                append(']')
            }
            append(']')
        }
    }

    /**
     * n개 노드의 완전 이진 트리를 LeetCode 레벨 순서 직렬화 [v0,v1,...]로 만든다.
     * 완전 트리라 null 없이도 유효한 직렬화 (node i의 자식 = 2i+1, 2i+2).
     */
    fun completeTree(n: Int, valueOf: () -> Long): String =
        (0 until n).joinToString(",", "[", "]") { valueOf().toString() }
}
