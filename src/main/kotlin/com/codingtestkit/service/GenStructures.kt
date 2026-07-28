package com.codingtestkit.service

import kotlin.random.Random

/**
 * 생성기 DSL의 구조 생성기 — 트리·그래프·이분 그래프·수치 anti-hash (이슈 #36).
 *
 * 정점 번호는 CP 관행대로 1-based, 간선은 "u v"(가중치 있으면 "u v w") 한 줄씩.
 * 인터프리터와 분리된 순수 함수라 단독으로 검증할 수 있다.
 */
object GenStructures {

    /** 간선 하나 (가중치 없으면 weight = null) */
    data class Edge(val u: Int, val v: Int, val weight: Long? = null) {
        fun render(): String = if (weight == null) "$u $v" else "$u $v $weight"
    }

    // ─────────────────────────── 트리 ───────────────────────────

    /**
     * n개 정점의 트리 (간선 n-1개).
     * RANDOM      — 새 정점마다 앞선 정점 중 임의의 부모 (균일 랜덤 트리)
     * STAR        — 모두 1번에 연결 (깊이 1, 차수 n-1)
     * PATH        — 1-2-3-…-n 일자
     * BAMBOO      — 일자이되 번호를 무작위로 섞음 (인접 번호 가정을 깨는 용도)
     * CATERPILLAR — 척추 경로에 잎을 하나씩 매단 형태
     * BINARY      — 완전 이진 트리 (부모 = i/2)
     */
    fun tree(n: Int, type: String, rng: Random): List<Edge> {
        require(n >= 1) { "tree needs n >= 1" }
        if (n == 1) return emptyList()
        return when (type) {
            "RANDOM" -> (2..n).map { Edge(rng.nextInt(1, it), it) }
            "STAR" -> (2..n).map { Edge(1, it) }
            "PATH" -> (2..n).map { Edge(it - 1, it) }
            "BAMBOO" -> {
                val label = (1..n).toMutableList().also { it.shuffle(rng) }
                (1 until n).map { Edge(label[it - 1], label[it]) }
            }
            "CATERPILLAR" -> {
                // 앞쪽 절반은 척추(경로), 나머지는 척추의 임의 정점에 붙는 잎
                val spine = (n + 1) / 2
                val edges = mutableListOf<Edge>()
                for (i in 2..spine) edges.add(Edge(i - 1, i))
                for (i in spine + 1..n) edges.add(Edge(rng.nextInt(1, spine + 1), i))
                edges
            }
            "BINARY" -> (2..n).map { Edge(it / 2, it) }
            else -> throw IllegalArgumentException(
                "unknown tree type '$type' (RANDOM, STAR, PATH, BAMBOO, CATERPILLAR, BINARY)")
        }
    }

    // ─────────────────────────── 그래프 ───────────────────────────

    /**
     * n개 정점·m개 간선 그래프.
     * SIMPLE    — 자기 루프·중복 간선 없음
     * CONNECTED — 신장 트리를 먼저 깔고 남은 간선을 추가 (연결 보장)
     * DAG       — directed에서만: 항상 낮은 번호 → 높은 번호
     * MULTI     — 자기 루프·중복 허용
     * TREE      — tree(n)과 같되 가중치를 붙일 수 있음 (m은 무시, n-1개)
     */
    fun graph(
        n: Int, m: Int, type: String, directed: Boolean, rng: Random,
        weighted: Boolean = false, wmin: Long = 1, wmax: Long = 1_000_000_000
    ): List<Edge> {
        require(n >= 1) { "graph needs n >= 1" }
        require(m >= 0) { "graph needs m >= 0" }
        fun w(): Long? = if (weighted) (if (wmin == wmax) wmin else rng.nextLong(wmin, wmax + 1)) else null

        if (type == "TREE") return tree(n, "RANDOM", rng).map { it.copy(weight = w()) }

        val maxSimple = maxSimpleEdges(n, directed, type == "DAG")
        if (type != "MULTI" && m > maxSimple) throw IllegalArgumentException(
            "m=$m exceeds the maximum $maxSimple edges for n=$n with type=$type")

        val edges = mutableListOf<Edge>()
        val seen = HashSet<Long>()
        fun key(u: Int, v: Int): Long {
            // 무향은 (작은 쪽, 큰 쪽)으로 정규화해 u-v와 v-u를 같은 간선으로 본다
            val a = if (directed) u else minOf(u, v)
            val b = if (directed) v else maxOf(u, v)
            return a.toLong() * (n + 1) + b
        }

        if (type == "CONNECTED") {
            if (m < n - 1) throw IllegalArgumentException("CONNECTED needs m >= n-1 (n=$n, m=$m)")
            for (e in tree(n, "RANDOM", rng)) {
                edges.add(e.copy(weight = w()))
                seen.add(key(e.u, e.v))
            }
        }

        var guard = 0L
        val guardLimit = (m.toLong() + 16) * 200   // 후보가 고갈될 때의 무한 루프 방지
        while (edges.size < m) {
            if (guard++ > guardLimit) throw IllegalArgumentException(
                "could not place $m distinct edges for n=$n (too dense)")
            var u = rng.nextInt(1, n + 1)
            var v = rng.nextInt(1, n + 1)
            when (type) {
                "MULTI" -> {}                                   // 무엇이든 허용
                "DAG" -> {
                    if (u == v) continue
                    if (u > v) { val t = u; u = v; v = t }       // 항상 낮은→높은
                    if (!seen.add(key(u, v))) continue
                }
                else -> {                                        // SIMPLE / CONNECTED
                    if (u == v) continue
                    if (!seen.add(key(u, v))) continue
                }
            }
            edges.add(Edge(u, v, w()))
        }
        return edges
    }

    private fun maxSimpleEdges(n: Int, directed: Boolean, dag: Boolean): Long {
        val pairs = n.toLong() * (n - 1) / 2
        return if (directed && !dag) pairs * 2 else pairs
    }

    /** n1 + n2 정점의 이분 그래프 — 왼쪽 1..n1, 오른쪽 n1+1..n1+n2 */
    fun bipartiteGraph(n1: Int, n2: Int, m: Int, directed: Boolean, rng: Random): List<Edge> {
        require(n1 >= 1 && n2 >= 1) { "bipartite_graph needs n1, n2 >= 1" }
        val maxEdges = n1.toLong() * n2
        if (m > maxEdges) throw IllegalArgumentException(
            "m=$m exceeds the maximum $maxEdges edges for n1=$n1, n2=$n2")
        val edges = mutableListOf<Edge>()
        val seen = HashSet<Long>()
        var guard = 0L
        val guardLimit = (m.toLong() + 16) * 200
        while (edges.size < m) {
            if (guard++ > guardLimit) throw IllegalArgumentException(
                "could not place $m distinct edges (too dense)")
            val l = rng.nextInt(1, n1 + 1)
            val r = n1 + rng.nextInt(1, n2 + 1)
            if (!seen.add(l.toLong() * (n1 + n2 + 1) + r)) continue
            edges.add(if (directed && rng.nextBoolean()) Edge(r, l) else Edge(l, r))
        }
        return edges
    }

    // ──────────────────── 수치 anti-hash (GCC libstdc++) ────────────────────

    /**
     * 현대 libstdc++(_Prime_rehash_policy)가 버킷 수로 고르는 소수들.
     * 이슈어가 인용한 __stl_prime_list는 옛 SGI 계열이라 최신 GCC에서는
     * 저격이 빗나간다 — 그래서 최신 표를 쓰고, 소수를 인자로 덮어쓸 수도 있게 한다.
     */
    private val LIBSTDCXX_PRIMES = longArrayOf(
        1031, 1109, 1193, 1289, 1381, 1493, 1613, 1741, 1879, 2029, 2179, 2357, 2549, 2753,
        2971, 3209, 3469, 3739, 4027, 4349, 4703, 5087, 5503, 5953, 6427, 6949, 7517, 8123,
        8783, 9497, 10273, 11113, 12011, 12983, 14033, 15173, 16411, 17749, 19183, 20753,
        22447, 24281, 26267, 28411, 30727, 33223, 35933, 38873, 42043, 45481, 49201, 53201,
        57557, 62233, 67307, 72817, 78779, 85229, 92203, 99733, 107897, 116731, 126271,
        136607, 147793, 159871, 172933, 187091, 202409, 218971, 236897, 256279, 277261,
        299951, 324503, 351061, 379787, 410857, 444487, 480881, 520241, 562841, 608903,
        658753, 712697, 771049, 834181, 902483, 976369, 1056323, 1142821, 1236397, 1337629,
        1447153, 1565659, 1693859, 1832561, 1982627, 2144977, 2320627, 2510653, 2716249,
        2938679, 3179303, 3439651, 3721303, 4026031, 4355707, 4712381, 5098259, 5515729,
        5967347, 6456007, 6984629, 7556579, 8175383, 8844859, 9569143, 10352717
    )

    /** n개 원소를 담을 때 libstdc++가 최종적으로 쓰게 될 버킷 소수 */
    fun bucketPrimeFor(n: Int): Long =
        LIBSTDCXX_PRIMES.firstOrNull { it >= n } ?: LIBSTDCXX_PRIMES.last()

    /**
     * std::unordered_map/set을 O(N)으로 떨어뜨리는 정수 수열 (이슈 #36).
     *
     * libstdc++의 std::hash<int>는 항등 함수이고 버킷 인덱스는 hash % bucket_count다.
     * 버킷 수가 소수 p일 때 p의 배수만 넣으면 전부 버킷 0에 몰려, 분리 연결 리스트가
     * 길이 N의 리스트 하나가 된다. 대상은 GCC libstdc++ — libc++/MSVC는 정책이 다르고
     * Java 8+는 버킷을 트리화해 O(log n)으로 막는다.
     */
    fun antiHashInts(n: Int, prime: Long): List<Long> {
        require(n >= 0) { "anti_hash_int needs n >= 0" }
        require(prime > 0) { "anti_hash_int needs a positive prime" }
        return (1..n).map { prime * it }
    }
}
