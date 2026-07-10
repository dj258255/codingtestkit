package com.codingtestkit.ui

import com.codingtestkit.service.PluginSettingsService
import com.intellij.ui.JBColor
import com.intellij.ui.jcef.JBCefBrowser
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter

/**
 * 임베드 웹페이지 다크 모드 (이슈 #34).
 *
 * 코드포스처럼 다크 테마가 없는 사이트를 플러그인 안(JCEF)에서 열면 밝은 화면이
 * 그대로 노출된다 — 브라우저 확장(Dark Reader 등)도 못 쓰는 환경이라 플러그인이
 * 직접 어둡게 만든다. 페이지 로드 완료 시 invert+hue-rotate 필터 CSS를 주입하되,
 * **이미 어두운 페이지는 건너뛴다** (LeetCode 다크 계정처럼 원래 어두운 페이지를
 * 다시 반전해 밝게 만드는 사고 방지 — body 배경 휘도를 보고 판단).
 *
 * 설정(FOLLOW_IDE/LIGHT/DARK)은 PluginSettingsService.embedTheme에 저장된다.
 */
object CefDarkMode {

    /** 현재 설정 기준으로 임베드 페이지를 어둡게 할지 */
    fun isDarkEnabled(): Boolean = when (PluginSettingsService.getInstance().embedTheme) {
        PluginSettingsService.EmbedTheme.DARK -> true
        PluginSettingsService.EmbedTheme.LIGHT -> false
        PluginSettingsService.EmbedTheme.FOLLOW_IDE -> !JBColor.isBright()
    }

    /**
     * 브라우저에 다크 모드 주입기를 부착한다. 이후 모든 페이지 이동에서
     * 로드 완료 시점에 설정을 읽어 적용 — 다이얼로그가 떠 있는 동안
     * 페이지를 오가도 계속 어둡게 유지된다.
     */
    fun attach(browser: JBCefBrowser) {
        browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(cef: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                if (!frame.isMain) return
                if (!isDarkEnabled()) return
                cef.executeJavaScript(INJECT_JS, cef.url, 0)
            }
        }, browser.cefBrowser)
    }

    // 밝은 페이지(body 휘도 > 128)에만 필터를 씌운다. 이미지·비디오·캔버스는
    // 이중 반전으로 원색 유지. 중복 주입은 id로 방지.
    private val INJECT_JS = """
        (function() {
          if (document.getElementById('ctk-dark-style')) return;
          try {
            var bg = getComputedStyle(document.body).backgroundColor || '';
            var m = bg.match(/\d+/g);
            if (m && m.length >= 3) {
              var lum = 0.299 * m[0] + 0.587 * m[1] + 0.114 * m[2];
              if (m.length >= 4 && parseFloat(m[3]) === 0) lum = 255; // 투명 = 흰 배경 취급
              if (lum < 128) return; // 이미 어두운 페이지 — 건드리지 않음
            }
          } catch (e) {}
          var s = document.createElement('style');
          s.id = 'ctk-dark-style';
          s.textContent =
            'html{filter:invert(0.92) hue-rotate(180deg)!important;background:#151617!important;}' +
            'img,video,canvas,iframe,embed,svg image,[style*="background-image"]' +
            '{filter:invert(1) hue-rotate(180deg)!important;}';
          document.documentElement.appendChild(s);
        })();
    """.trimIndent()
}
