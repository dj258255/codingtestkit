package com.codingtestkit.service

import com.codingtestkit.model.TestCase
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.zip.ZipInputStream

/**
 * SWEA 테스트 데이터를 Kotlin에서 직접 다운로드하여
 * zip이면 메모리 내 추출, plain text이면 그대로 사용하여
 * TestCase 리스트로 반환한다.
 */
object SweaTestDataDownloader {

    data class DownloadResult(
        val testCases: List<TestCase>,
        val rawInput: String,
        val rawOutput: String
    )

    private val DOWNLOAD_URL_PATTERNS = listOf(
        "/main/common/contestProb/contestProbDown.do?downType=in&contestProbId=%s" to
                "/main/common/contestProb/contestProbDown.do?downType=out&contestProbId=%s",
        "/main/code/problem/problemSampleDown.do?contestProbId=%s&type=input" to
                "/main/code/problem/problemSampleDown.do?contestProbId=%s&type=output",
        "/main/code/problem/problemSampleDownload.do?contestProbId=%s&type=input" to
                "/main/code/problem/problemSampleDownload.do?contestProbId=%s&type=output"
    )

    private const val BASE_URL = "https://swexpertacademy.com"
    private const val MAX_SIZE = 10 * 1024 * 1024 // 10MB

    /**
     * contestProbId를 이용해 테스트 데이터를 다운로드하고 TestCase 리스트로 반환.
     * 여러 URL 패턴을 순차 시도하여 유효한 데이터를 찾으면 반환.
     */
    fun download(contestProbId: String, cookies: String): DownloadResult? {
        for ((inputPattern, outputPattern) in DOWNLOAD_URL_PATTERNS) {
            val inputUrl = BASE_URL + inputPattern.format(contestProbId)
            val outputUrl = BASE_URL + outputPattern.format(contestProbId)

            try {
                val inputContent = fetchContent(inputUrl, cookies) ?: continue
                val outputContent = fetchContent(outputUrl, cookies) ?: continue

                if (inputContent.isBlank() || outputContent.isBlank()) continue
                if (isHtml(inputContent) || isHtml(outputContent)) continue

                return DownloadResult(
                    testCases = listOf(TestCase(input = inputContent.trim(), expectedOutput = outputContent.trim())),
                    rawInput = inputContent.trim(),
                    rawOutput = outputContent.trim()
                )
            } catch (e: Exception) {
                println("[CodingTestKit] Download attempt failed for pattern: ${e.message}")
            }
        }
        return null
    }

    /**
     * URL에서 콘텐츠를 가져온다.
     * 응답이 zip이면 메모리 내 추출하여 텍스트를 반환한다.
     */
    private fun fetchContent(url: String, cookies: String): String? {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Cookie", cookies)
        conn.setRequestProperty("Referer", "$BASE_URL/")
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.instanceFollowRedirects = true

        val status = conn.responseCode
        if (status != 200) return null

        val contentType = conn.contentType ?: ""
        val bytes = conn.inputStream.use { input ->
            ByteArrayOutputStream().also { output ->
                val buf = ByteArray(8192)
                var total = 0
                var n: Int
                while (input.read(buf).also { n = it } != -1) {
                    total += n
                    if (total > MAX_SIZE) return null
                    output.write(buf, 0, n)
                }
            }.toByteArray()
        }

        if (bytes.isEmpty()) return null

        // zip 판별: Content-Type 또는 매직넘버(PK)
        if (isZip(contentType, bytes)) {
            return extractTextFromZip(bytes)
        }

        // plain text
        val text = String(bytes, Charsets.UTF_8)
        return if (isHtml(text)) null else text
    }

    /**
     * zip 여부 판별 (Content-Type 또는 매직넘버 PK 0x504B)
     */
    private fun isZip(contentType: String, bytes: ByteArray): Boolean {
        if (contentType.contains("zip", ignoreCase = true) ||
            contentType.contains("octet-stream", ignoreCase = true)
        ) return bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()
        // Content-Type이 text라도 매직넘버가 PK이면 zip
        return bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()
    }

    /**
     * zip 바이트 배열에서 첫 번째 텍스트 파일 내용을 추출
     */
    private fun extractTextFromZip(bytes: ByteArray): String? {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val content = zis.readBytes()
                    zis.closeEntry()
                    val text = String(content, Charsets.UTF_8)
                    if (text.isNotBlank() && !isHtml(text)) {
                        return text
                    }
                }
                entry = zis.nextEntry
            }
        }
        return null
    }

    private fun isHtml(text: String): Boolean {
        val trimmed = text.trimStart()
        return trimmed.startsWith("<!") ||
                trimmed.startsWith("<html", ignoreCase = true) ||
                trimmed.startsWith("<HTML") ||
                (trimmed.contains("<head>") && trimmed.contains("<body>")) ||
                trimmed.contains("<!DOCTYPE") ||
                (trimmed.contains("<link") && trimmed.contains("<script"))
    }
}
