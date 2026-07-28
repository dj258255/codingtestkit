package com.codingtestkit.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** 트리·그래프·anti-hash 구조 생성기 검증 (이슈 #36) */
class GenStructuresTest {

    private val rng = Random(7)

    /** 간선 목록이 n개 정점의 유효한 트리인지 — 간선 n-1개 + 연결됨(사이클 없음) */
    private fun assertIsTree(n: Int, edges: List<GenStructures.Edge>) {
        assertEquals(n - 1, edges.size, "트리 간선은 n-1개")
        val parent = IntArray(n + 1) { it }
        fun find(x: Int): Int { var r = x; while (parent[r] != r) r = parent[r]; return r }
        for (e in edges) {
            assertTrue(e.u in 1..n && e.v in 1..n, "정점 번호는 1..n: $e")
            assertNotEquals(e.u, e.v, "자기 루프 없음")
            val a = find(e.u); val b = find(e.v)
            assertNotEquals(a, b, "사이클이 생기면 트리가 아님: $e")
            parent[a] = b
        }
        val root = find(1)
        for (v in 2..n) assertEquals(root, find(v), "정점 $v 가 연결되지 않음")
    }

    @Test
    fun `all tree types produce valid trees`() {
        for (type in listOf("RANDOM", "STAR", "PATH", "BAMBOO", "CATERPILLAR", "BINARY")) {
            for (n in listOf(1, 2, 5, 50)) {
                val edges = GenStructures.tree(n, type, rng)
                assertIsTree(n, edges)
            }
        }
    }

    @Test
    fun `tree shapes match their names`() {
        // STAR: 모든 간선이 1번에서
        assertTrue(GenStructures.tree(6, "STAR", rng).all { it.u == 1 })
        // PATH: i-1 - i
        assertEquals(listOf(1 to 2, 2 to 3, 3 to 4), GenStructures.tree(4, "PATH", rng).map { it.u to it.v })
        // BINARY: 부모는 i/2
        assertEquals(listOf(1 to 2, 1 to 3, 2 to 4, 2 to 5), GenStructures.tree(5, "BINARY", rng).map { it.u to it.v })
    }

    @Test
    fun `unknown tree type is rejected`() {
        val e = assertThrows(IllegalArgumentException::class.java) { GenStructures.tree(5, "NOPE", rng) }
        assertTrue(e.message!!.contains("unknown tree type"))
    }

    @Test
    fun `simple graph has no self loops or duplicates`() {
        val edges = GenStructures.graph(20, 60, "SIMPLE", directed = false, rng = rng)
        assertEquals(60, edges.size)
        val seen = HashSet<Pair<Int, Int>>()
        for (e in edges) {
            assertNotEquals(e.u, e.v, "자기 루프")
            val key = minOf(e.u, e.v) to maxOf(e.u, e.v)
            assertTrue(seen.add(key), "중복 간선 $key")
        }
    }

    @Test
    fun `connected graph is actually connected`() {
        val n = 30
        val edges = GenStructures.graph(n, 40, "CONNECTED", directed = false, rng = rng)
        assertEquals(40, edges.size)
        val parent = IntArray(n + 1) { it }
        fun find(x: Int): Int { var r = x; while (parent[r] != r) r = parent[r]; return r }
        for (e in edges) parent[find(e.u)] = find(e.v)
        val root = find(1)
        for (v in 2..n) assertEquals(root, find(v), "정점 $v 가 연결되지 않음")
    }

    @Test
    fun `connected rejects m below n minus 1`() {
        val e = assertThrows(IllegalArgumentException::class.java) {
            GenStructures.graph(10, 5, "CONNECTED", directed = false, rng = rng)
        }
        assertTrue(e.message!!.contains("m >= n-1"), e.message)
    }

    @Test
    fun `dag edges always go low to high`() {
        val edges = GenStructures.graph(15, 30, "DAG", directed = true, rng = rng)
        assertEquals(30, edges.size)
        for (e in edges) assertTrue(e.u < e.v, "DAG 간선이 역방향: $e")
    }

    @Test
    fun `too many edges for simple graph is rejected`() {
        // n=5 무향 단순 그래프의 최대 간선은 10
        val e = assertThrows(IllegalArgumentException::class.java) {
            GenStructures.graph(5, 11, "SIMPLE", directed = false, rng = rng)
        }
        assertTrue(e.message!!.contains("exceeds"), e.message)
        assertEquals(10, GenStructures.graph(5, 10, "SIMPLE", directed = false, rng = rng).size)
    }

    @Test
    fun `weighted graph emits weights in range`() {
        val edges = GenStructures.graph(10, 20, "SIMPLE", directed = false, rng = rng,
            weighted = true, wmin = 5, wmax = 9)
        for (e in edges) {
            assertNotNull(e.weight)
            assertTrue(e.weight!! in 5..9, "가중치 ${e.weight} 범위 밖")
            assertTrue(e.render().split(" ").size == 3, "가중치 있는 간선은 3개 필드")
        }
    }

    @Test
    fun `bipartite graph keeps sides separate`() {
        val n1 = 6; val n2 = 8
        val edges = GenStructures.bipartiteGraph(n1, n2, 20, directed = false, rng = rng)
        assertEquals(20, edges.size)
        for (e in edges) {
            val (l, r) = if (e.u <= n1) e.u to e.v else e.v to e.u
            assertTrue(l in 1..n1, "왼쪽 정점이 아님: $e")
            assertTrue(r in n1 + 1..n1 + n2, "오른쪽 정점이 아님: $e")
        }
    }

    @Test
    fun `anti hash ints all land in one bucket`() {
        val n = 1000
        val prime = GenStructures.bucketPrimeFor(n)
        assertTrue(prime >= n, "버킷 소수는 n 이상이어야 함: $prime")
        val values = GenStructures.antiHashInts(n, prime)
        assertEquals(n, values.size)
        // libstdc++의 std::hash<int>는 항등 → 버킷 = value % prime. 전부 0이어야 한다.
        assertTrue(values.all { it % prime == 0L }, "모든 값이 버킷 0에 몰려야 한다")
        assertEquals(n, values.distinct().size, "값은 서로 달라야 한다")
    }

    @Test
    fun `bucket prime table is sorted and covers common sizes`() {
        assertEquals(107897L, GenStructures.bucketPrimeFor(100_000))
        assertEquals(1031L, GenStructures.bucketPrimeFor(1000))
        // 표를 넘어가는 크기는 마지막 소수로 포화
        assertTrue(GenStructures.bucketPrimeFor(Int.MAX_VALUE) > 10_000_000L)
    }
}
