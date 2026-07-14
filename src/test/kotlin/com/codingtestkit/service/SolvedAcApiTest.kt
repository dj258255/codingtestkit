package com.codingtestkit.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SolvedAcApiTest {

    // ── levelToString ──

    @Test
    fun `level 0 is Unrated`() {
        assertEquals("Unrated", SolvedAcApi.levelToString(0))
    }

    @Test
    fun `Bronze tier levels 1-5`() {
        assertEquals("Bronze V", SolvedAcApi.levelToString(1))
        assertEquals("Bronze IV", SolvedAcApi.levelToString(2))
        assertEquals("Bronze III", SolvedAcApi.levelToString(3))
        assertEquals("Bronze II", SolvedAcApi.levelToString(4))
        assertEquals("Bronze I", SolvedAcApi.levelToString(5))
    }

    @Test
    fun `Silver tier levels 6-10`() {
        assertEquals("Silver V", SolvedAcApi.levelToString(6))
        assertEquals("Silver IV", SolvedAcApi.levelToString(7))
        assertEquals("Silver III", SolvedAcApi.levelToString(8))
        assertEquals("Silver II", SolvedAcApi.levelToString(9))
        assertEquals("Silver I", SolvedAcApi.levelToString(10))
    }

    @Test
    fun `Gold tier levels 11-15`() {
        assertEquals("Gold V", SolvedAcApi.levelToString(11))
        assertEquals("Gold IV", SolvedAcApi.levelToString(12))
        assertEquals("Gold III", SolvedAcApi.levelToString(13))
        assertEquals("Gold II", SolvedAcApi.levelToString(14))
        assertEquals("Gold I", SolvedAcApi.levelToString(15))
    }

    @Test
    fun `Platinum tier levels 16-20`() {
        assertEquals("Platinum V", SolvedAcApi.levelToString(16))
        assertEquals("Platinum I", SolvedAcApi.levelToString(20))
    }

    @Test
    fun `Diamond tier levels 21-25`() {
        assertEquals("Diamond V", SolvedAcApi.levelToString(21))
        assertEquals("Diamond I", SolvedAcApi.levelToString(25))
    }

    @Test
    fun `Ruby tier levels 26-30`() {
        assertEquals("Ruby V", SolvedAcApi.levelToString(26))
        assertEquals("Ruby I", SolvedAcApi.levelToString(30))
    }

    @Test
    fun `out of range level returns Unrated`() {
        assertEquals("Unrated", SolvedAcApi.levelToString(31))
        assertEquals("Unrated", SolvedAcApi.levelToString(100))
        assertEquals("Unrated", SolvedAcApi.levelToString(-1))
    }
}
