package com.codingtestkit.service

import com.google.gson.JsonParser
import org.jsoup.Jsoup
import java.net.URLEncoder

object TranslateService {

    /**
     * Google Translate 비공식 API (translate.googleapis.com)
     * IntelliJ TranslationPlugin(YiiGuxing) 등에서도 사용하는 방식
     */
    fun translate(text: String, sourceLang: String = "auto", targetLang: String = "ko"): String {
        if (text.isBlank()) return text

        // 긴 텍스트는 청크로 나눠서 번역 (API 제한 ~5000자)
        val maxLen = 4500
        if (text.length <= maxLen) {
            return translateChunk(text, sourceLang, targetLang)
        }

        // HTML 태그 경계를 기준으로 분할, 청크 간 딜레이로 rate limit 방지
        val chunks = splitHtml(text, maxLen)
        return buildString {
            for ((i, chunk) in chunks.withIndex()) {
                if (i > 0) Thread.sleep(300)
                append(translateChunk(chunk, sourceLang, targetLang))
            }
        }
    }

    private fun translateChunk(text: String, sourceLang: String, targetLang: String): String {
        val encoded = URLEncoder.encode(text, "UTF-8")
        val url = "https://translate.googleapis.com/translate_a/single" +
                "?client=gtx&sl=$sourceLang&tl=$targetLang&dt=t&dj=1&ie=UTF-8&oe=UTF-8" +
                "&q=$encoded"

        // 재시도 로직 (429/5xx 에러 시 exponential backoff)
        var lastException: Exception? = null
        for (attempt in 0..2) {
            if (attempt > 0) Thread.sleep(1000L * attempt)
            try {
                val conn = Jsoup.connect(url)
                    .header("Accept", "application/json")
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                    .ignoreContentType(true)
                    .timeout(10000)
                    .execute()

                if (conn.statusCode() == 429) {
                    lastException = RuntimeException(I18n.t(
                        "번역 요청이 너무 많습니다. 잠시 후 다시 시도하세요.",
                        "Too many translation requests. Please try again later."
                    ))
                    continue
                }

                val json = JsonParser.parseString(conn.body()).asJsonObject
                val sentences = json.getAsJsonArray("sentences") ?: return text
                return buildString {
                    for (s in sentences) {
                        val trans = s.asJsonObject.get("trans")?.asString
                        if (trans != null) append(trans)
                    }
                }
            } catch (e: org.jsoup.HttpStatusException) {
                lastException = e
                if (e.statusCode in listOf(429, 500, 502, 503)) continue
                throw e
            } catch (e: Exception) {
                throw e
            }
        }
        throw lastException ?: RuntimeException("Translation failed")
    }

    private fun splitHtml(html: String, maxLen: Int): List<String> {
        val chunks = mutableListOf<String>()
        var remaining = html
        while (remaining.length > maxLen) {
            // 태그 경계에서 분할 시도
            var splitIdx = remaining.lastIndexOf(">", maxLen)
            if (splitIdx <= 0) splitIdx = remaining.lastIndexOf(" ", maxLen)
            if (splitIdx <= 0) splitIdx = maxLen
            else splitIdx += 1
            chunks.add(remaining.substring(0, splitIdx))
            remaining = remaining.substring(splitIdx)
        }
        if (remaining.isNotEmpty()) chunks.add(remaining)
        return chunks
    }

    /**
     * 텍스트의 언어를 감지 (간단한 휴리스틱)
     * 한글이 포함되어 있으면 "ko", 아니면 "en"으로 판단
     */
    fun detectLanguage(text: String): String {
        val koreanCount = text.count { it in '\uAC00'..'\uD7A3' || it in '\u3131'..'\u318E' }
        return if (koreanCount > text.length * 0.1) "ko" else "en"
    }
}
