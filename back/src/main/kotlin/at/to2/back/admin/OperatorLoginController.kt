package at.to2.back.admin

import org.springframework.http.MediaType
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody

/**
 * The Operator console is served by the backend on its own host rather than by
 * the SPA. It is not linked from the public site, has no cross-origin dance, and
 * keeps the administrative surface off the site Visitors use.
 */
@Controller
class OperatorLoginController {

    @GetMapping("/admin/login", produces = [MediaType.TEXT_HTML_VALUE])
    @ResponseBody
    fun loginPage(@RequestParam(required = false) error: String?, csrf: CsrfToken?): String =
        adminPage(
            "로그인",
            """
            ${if (error != null) """<p class="error">아이디 또는 비밀번호가 올바르지 않습니다. 반복 실패 시 계정이 잠깁니다.</p>""" else ""}
            <form method="post" action="/admin/login">
              ${csrfInput(csrf)}
              <label>아이디<input name="username" autocomplete="username" autofocus></label>
              <label>비밀번호<input name="password" type="password" autocomplete="current-password"></label>
              <button type="submit">로그인</button>
            </form>
            """,
        )
}

/**
 * The CSRF hidden input. Hand-written forms have to carry this themselves —
 * without it every POST on this chain is rejected, and the form looks fine right
 * up until someone actually tries to use it.
 */
internal fun csrfInput(csrf: CsrfToken?): String =
    csrf?.let { """<input type="hidden" name="${it.parameterName}" value="${it.token}">""" } ?: ""

internal fun adminPage(title: String, body: String): String =
    """<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex, nofollow">
<title>$title · to2.at</title>
<style>
  :root { color-scheme: light dark; }
  body { font-family: system-ui, -apple-system, "Malgun Gothic", sans-serif; margin: 0 auto;
         max-width: 720px; padding: 40px 20px; line-height: 1.5; }
  h1 { font-size: 20px; margin: 0 0 24px; }
  form { display: grid; gap: 12px; max-width: 320px; }
  label { display: grid; gap: 4px; font-size: 13px; }
  input { font: inherit; padding: 8px 10px; border: 1px solid #999; border-radius: 6px; }
  button { font: inherit; padding: 8px 14px; border: 0; border-radius: 6px;
           background: #18181b; color: #fff; cursor: pointer; }
  .error { color: #dc2626; font-size: 13px; }
  table { width: 100%; border-collapse: collapse; font-size: 14px; }
  th, td { text-align: left; padding: 8px 6px; border-bottom: 1px solid #ddd; vertical-align: top; }
  td.target { word-break: break-all; color: #666; }
  .pill { font-size: 12px; padding: 1px 6px; border-radius: 4px; background: #eee; }
  .pill.suspended { background: #fee; color: #b91c1c; }
  .row-actions { display: flex; gap: 6px; }
  .row-actions button { padding: 4px 8px; font-size: 12px; }
  .muted { color: #888; font-size: 13px; }
</style>
</head>
<body>
<h1>to2.at 운영</h1>
$body
</body>
</html>"""
