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
 * **이미 어두운 페이지는 건너뛴다** (LeetCode 다크 계정·프로그래머스 문제 페이지처럼
 * 원래 어두운 페이지를 다시 반전해 밝게 만드는 사고 방지 — 화면 5개 지점에서
 * 실제 보이는 배경 휘도를 샘플링해 과반 다수결로 판단).
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
    // 부착된 브라우저들 — 설정이나 IDE 테마가 바뀌면 이미 열려 있는 페이지도
    // 다시 칠해야 한다. 다이얼로그가 닫혀도 참조가 남지 않도록 약한 참조로 들고,
    // 죽은 참조는 순회할 때 정리한다 (이슈 #34).
    private val attached = java.util.Collections.synchronizedList(
        mutableListOf<java.lang.ref.WeakReference<JBCefBrowser>>()
    )
    private var themeHooksInstalled = false

    fun attach(browser: JBCefBrowser) {
        installThemeHooks()
        attached.add(java.lang.ref.WeakReference(browser))
        browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(cef: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                if (!frame.isMain) return
                cef.executeJavaScript(if (isDarkEnabled()) INJECT_JS else REMOVE_JS, cef.url, 0)
            }
        }, browser.cefBrowser)
    }

    /**
     * 설정 변경과 IDE 테마 변경을 한 번만 구독한다.
     *
     * 주입 시점이 onLoadEnd뿐이면 설정을 바꿔도 이미 열려 있는 페이지는 그대로다 —
     * Dark→Light로 바꿔도 반전이 남고, Light→Dark로 바꿔도 밝은 채로 있다.
     * FOLLOW_IDE는 JBColor.isBright()를 주입 시점에만 읽으므로 IDE 테마를 런타임에
     * 바꾸면 따라오지 않는다. 둘 다 여기서 잡는다.
     */
    private fun installThemeHooks() {
        if (themeHooksInstalled) return
        themeHooksInstalled = true
        PluginSettingsService.getInstance().addEmbedThemeListener { reapplyAll() }
        com.intellij.openapi.application.ApplicationManager.getApplication().messageBus
            .connect()
            .subscribe(
                com.intellij.ide.ui.LafManagerListener.TOPIC,
                com.intellij.ide.ui.LafManagerListener { reapplyAll() }
            )
    }

    /** 살아 있는 모든 임베드 브라우저에 현재 설정을 다시 적용 */
    private fun reapplyAll() {
        val script = if (isDarkEnabled()) INJECT_JS else REMOVE_JS
        synchronized(attached) {
            val it = attached.iterator()
            while (it.hasNext()) {
                val browser = it.next().get()
                if (browser == null) { it.remove(); continue }
                runCatching { browser.cefBrowser.executeJavaScript(script, browser.cefBrowser.url, 0) }
            }
        }
    }

    /** 주입한 다크 스타일을 걷어내고 재주입 가드를 푼다 */
    private val REMOVE_JS = """
        (function() {
          var s = document.getElementById('ctk-dark-style');
          if (s) s.remove();
          window.__ctkDark = false;
        })();
    """.trimIndent()

    // 밝은 페이지(body 휘도 > 128)에만 필터를 씌운다. 이미지·비디오·캔버스는
    // 이중 반전으로 원색 유지. 중복 주입은 id로 방지.
    //
    // html 배경을 흰색으로 강제하는 이유: 루트 요소의 filter는 자기 배경
    // (캔버스로 전파되는 부분 포함)까지 반전시키므로, 어두운 색을 직접 지정하면
    // 오히려 밝게 뒤집혀 문서가 뷰포트보다 짧을 때 하단에 밝은 띠가 생긴다
    // (이슈 #34 재보고). 흰색이 invert(0.92)를 거치면 ≈ #141414 다크가 된다.
    //
    // 코드포스 헤더 로고는 '불투명한 흰 판' 이미지다 (투명 픽셀 0%). 원색 보존
    // 대상에 두면 이중 반전으로 다크 배경 위에 흰 사각형이 남는다 — 제외해서 루트
    // 반전만 받게 해야 로고가 어두워진다.
    //
    // 셀렉터를 하나로 좁히지 않는 이유: 클래식 레이아웃은 div#header 안의
    // codeforces-*.png 이지만, 새 프론트엔드는 <header>에 id가 없고 로고도
    // /assets/img/logo.png 다. 한쪽만 겨누면 다른 쪽에서 조용히 아무것도 매칭되지
    // 않아 제보자가 본 흰 판이 그대로 돌아온다. 국기 아이콘 등 다른 헤더 이미지는
    // 원색 유지가 맞으므로 로고로 보이는 것만 고른다.
    private val INJECT_JS = """
        (function() {
          if (window.__ctkDark) return; // 같은 문서에 중복 부착 방지 (내비게이션 시 초기화됨)
          window.__ctkDark = true;

          // 유효 배경 휘도 판정: body 배경색만 보면 오판한다 —
          //  · body는 투명, 실제 배경은 래퍼 div (프로그래머스 로그인)
          //  · body는 흰색인데 다크 래퍼가 화면 전체를 덮음 (프로그래머스 문제 페이지)
          // 그래서 화면 5개 지점에서 실제로 '보이는' 배경을 샘플링한다:
          // 각 지점의 최상단 요소에서 조상으로 올라가며 첫 불투명 배경의 휘도를
          // 재고, 과반이 어두우면 이미 다크 페이지로 판정한다.
          // (computed style은 우리 invert 필터의 영향을 받지 않으므로
          //  주입 이후에 다시 호출해도 판정이 오염되지 않는다)
          function pageIsDark() {
            try {
              var w = innerWidth, h = innerHeight;
              var pts = [[w*0.5,h*0.1],[w*0.1,h*0.5],[w*0.5,h*0.5],[w*0.9,h*0.5],[w*0.5,h*0.9]];
              var dark = 0;
              for (var p = 0; p < pts.length; p++) {
                var el = document.elementFromPoint(pts[p][0], pts[p][1]);
                var lum = 255; // 최상위까지 전부 투명이면 흰 배경 취급
                while (el) {
                  var m = (getComputedStyle(el).backgroundColor || '').match(/[\d.]+/g);
                  if (m && m.length >= 3 && !(m.length >= 4 && parseFloat(m[3]) === 0)) {
                    lum = 0.299 * m[0] + 0.587 * m[1] + 0.114 * m[2];
                    break;
                  }
                  el = el.parentElement;
                }
                if (lum < 128) dark++;
              }
              return dark * 2 > pts.length;
            } catch (e) { return false; } // 판정 불가면 밝은 페이지 취급 (기존 동작)
          }

          function apply() {
            var existing = document.getElementById('ctk-dark-style');
            if (pageIsDark()) {
              if (existing) existing.remove(); // 늦게 어두워진 SPA — 잘못 입힌 반전 철회
              return;
            }
            if (existing) return;
            var s = document.createElement('style');
            s.id = 'ctk-dark-style';
            s.textContent =
              'html{filter:invert(0.92) hue-rotate(180deg)!important;background:#fff!important;}' +
              'img,video,canvas,iframe,embed,svg image,[style*="background-image"]' +
              '{filter:invert(1) hue-rotate(180deg)!important;}';
            if (/(^|\.)codeforces\.(com|org)${'$'}/.test(location.hostname))
              s.textContent +=
                '#header img[src*="codeforces-"],' +
                'header img[src*="logo"],' +
                '#header img[src*="logo"],' +
                'img[alt*="Codeforces" i]' +
                '{filter:none!important;}';
            document.documentElement.appendChild(s);
          }

          apply();
          // SPA는 로드 완료 뒤에야 실제 화면을 칠하는 경우가 있어 늦게 재판정 —
          // 흰 셸을 보고 잘못 입혔으면 걷어내고, 늦게 밝아진 페이지는 그때 입힌다.
          setTimeout(apply, 1500);
          setTimeout(apply, 4000);
        })();
    """.trimIndent()
}
