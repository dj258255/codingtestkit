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

        // 보호 구간(<pre>, 수식)을 플레이스홀더로 치환해 한 번에 번역한 뒤 복원.
        // 구간별로 API를 호출하면 수식이 많은 문제에서 호출이 수십 회로 폭증함 (이슈 #25).
        val protectedContents = mutableListOf<String>()
        val masked = buildString {
            for ((content, isProtected) in splitProtected(text)) {
                if (isProtected) {
                    append(" __CTK${protectedContents.size}__ ")
                    protectedContents.add(content)
                } else {
                    append(content)
                }
            }
        }

        val translated = translateText(masked, sourceLang, targetLang)

        // 번역기가 플레이스홀더 주변에 공백을 끼워넣거나 대소문자를 바꿀 수 있어 유연하게 복원
        return Regex("__\\s*CTK\\s*(\\d+)\\s*__", RegexOption.IGNORE_CASE).replace(translated) { m ->
            protectedContents.getOrNull(m.groupValues[1].toIntOrNull() ?: -1) ?: m.value
        }
    }

    /**
     * 번역에서 보호할 구간을 분리해 (내용, 보호 여부) 리스트로 반환.
     * - <pre> 블록: 테스트 케이스 입출력이 번역되며 깨지는 것 방지
     * - 수식 구간 ($$...$$, $...$): KaTeX 소스가 번역되며 깨지는 것 방지 (이슈 #25)
     */
    internal fun splitProtected(text: String): List<Pair<String, Boolean>> {
        val protectedPattern = Regex(
            "<pre[^>]*>[\\s\\S]*?</pre>" +      // <pre> 블록
                "|\\$\\$[^$]+\\$\\$" +             // display 수식 $$...$$
                "|\\$[^$\\n]+\\$",                  // inline 수식 $...$
            RegexOption.IGNORE_CASE
        )
        val parts = mutableListOf<Pair<String, Boolean>>() // (content, isProtected)
        var lastEnd = 0
        for (match in protectedPattern.findAll(text)) {
            if (match.range.first > lastEnd) {
                parts.add(Pair(text.substring(lastEnd, match.range.first), false))
            }
            parts.add(Pair(match.value, true))
            lastEnd = match.range.last + 1
        }
        if (lastEnd < text.length) {
            parts.add(Pair(text.substring(lastEnd), false))
        }
        return parts
    }

    private fun translateText(text: String, sourceLang: String, targetLang: String): String {
        if (text.isBlank()) return text
        val maxLen = 4500
        if (text.length <= maxLen) {
            return translateChunk(text, sourceLang, targetLang)
        }
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
