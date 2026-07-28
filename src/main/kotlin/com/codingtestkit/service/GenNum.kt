package com.codingtestkit.service

import java.math.BigInteger
import kotlin.random.Random

/**
 * 생성기 DSL의 수 모델 (이슈 #36).
 *
 * 64비트 고속 경로로 계산하다가 범위를 넘으면 BigInteger로 승격한다.
 * CP 입력은 대부분 Long 안에 들어가므로(5백만 요소 배열도 흔하다) 전부
 * BigInteger로 돌리면 할당 비용이 그대로 생성 시간이 된다. 반대로 Long만
 * 쓰면 2^64를 넘는 경계값을 표현할 수 없다 — 그래서 둘을 섞는다.
 *
 * 수 표현은 파서·평가기·생성기 전부가 딛는 기반이라 DSL 1단계에서 확정한다
 * (나중에 바꾸면 전면 재작업).
 */
class GenNum private constructor(
    private val small: Long,
    /** null이면 small이 유효값 (고속 경로) */
    private val big: BigInteger?
) : Comparable<GenNum> {

    companion object {
        val ZERO = of(0L)
        val ONE = of(1L)

        fun of(v: Long): GenNum = GenNum(v, null)

        /** 64비트에 들어가면 고속 경로로 되돌린다 — 승격은 필요한 동안만 유지 */
        fun of(v: BigInteger): GenNum =
            if (v.bitLength() < 64) GenNum(v.toLong(), null) else GenNum(0, v)

        fun parse(text: String): GenNum? {
            text.toLongOrNull()?.let { return of(it) }
            return try { of(BigInteger(text)) } catch (_: NumberFormatException) { null }
        }
    }

    val isBig: Boolean get() = big != null

    fun toBigInteger(): BigInteger = big ?: BigInteger.valueOf(small)

    /** Long 범위를 넘으면 null — 배열 길이·반복 횟수처럼 Int/Long이어야 하는 자리에서 검사용 */
    fun toLongOrNull(): Long? = if (big == null) small else null

    fun toIntOrNull(): Int? = toLongOrNull()?.takeIf { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }?.toInt()

    operator fun plus(o: GenNum): GenNum =
        if (big == null && o.big == null) {
            try { of(Math.addExact(small, o.small)) }
            catch (_: ArithmeticException) { of(toBigInteger().add(o.toBigInteger())) }
        } else of(toBigInteger().add(o.toBigInteger()))

    operator fun minus(o: GenNum): GenNum =
        if (big == null && o.big == null) {
            try { of(Math.subtractExact(small, o.small)) }
            catch (_: ArithmeticException) { of(toBigInteger().subtract(o.toBigInteger())) }
        } else of(toBigInteger().subtract(o.toBigInteger()))

    operator fun times(o: GenNum): GenNum =
        if (big == null && o.big == null) {
            try { of(Math.multiplyExact(small, o.small)) }
            catch (_: ArithmeticException) { of(toBigInteger().multiply(o.toBigInteger())) }
        } else of(toBigInteger().multiply(o.toBigInteger()))

    /** 내림 나눗셈 (음수에서도 바닥 방향 — DSL 명세의 floor divide) */
    fun floorDiv(o: GenNum): GenNum {
        if (o.isZero()) throw ArithmeticException("division by zero")
        return if (big == null && o.big == null) of(Math.floorDiv(small, o.small))
        else {
            val (q, r) = toBigInteger().divideAndRemainder(o.toBigInteger())
            // BigInteger 나눗셈은 0 방향 절단이라 음수 나머지일 때 1을 빼 바닥으로 맞춘다
            of(if (r.signum() != 0 && r.signum() != o.toBigInteger().signum()) q.subtract(BigInteger.ONE) else q)
        }
    }

    fun isZero(): Boolean = big?.signum()?.equals(0) ?: (small == 0L)

    override fun compareTo(other: GenNum): Int =
        if (big == null && other.big == null) small.compareTo(other.small)
        else toBigInteger().compareTo(other.toBigInteger())

    override fun equals(other: Any?): Boolean = other is GenNum && compareTo(other) == 0
    override fun hashCode(): Int = toBigInteger().hashCode()
    override fun toString(): String = big?.toString() ?: small.toString()

    /**
     * [min, max] 균등 랜덤.
     * 고속 경로는 Random.nextLong, 큰 범위는 비트 길이만큼 뽑고 범위 밖이면
     * 다시 뽑는 기각 샘플링 — 모듈로 편향 없이 균등하다.
     */
    fun rangeTo(max: GenNum, rng: Random): GenNum {
        val min = this
        if (min == max) return min
        val lo = min.toLongOrNull()
        val hi = max.toLongOrNull()
        // hi+1이 오버플로하는 경계는 고속 경로에서 표현할 수 없어 큰 수 경로로 넘긴다
        if (lo != null && hi != null && hi != Long.MAX_VALUE) return of(rng.nextLong(lo, hi + 1))

        val span = max.toBigInteger().subtract(min.toBigInteger())  // >= 0
        val bits = span.bitLength()
        val bytes = ByteArray((bits + 7) / 8 + 1)
        while (true) {
            rng.nextBytes(bytes)
            bytes[0] = 0 // 최상위 바이트를 비워 부호 없는 값으로 만든다
            val candidate = BigInteger(bytes).shiftRight(bytes.size * 8 - 8 - bits)
            if (candidate <= span) return of(min.toBigInteger().add(candidate))
        }
    }
}
