const FONT_CSS =
  'https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/dist/web/variable/pretendardvariable-dynamic-subset.min.css'

const FONT_STACK =
  '"Pretendard Variable", Pretendard, -apple-system, BlinkMacSystemFont, ' +
  '"Apple SD Gothic Neo", "Segoe UI", "Malgun Gothic", sans-serif'

/**
 * Every page the Worker serves for a Code path is a single self-contained
 * document with inline styles. It must paint before any bundle loads — the
 * whole reason the Worker renders this instead of the SPA.
 */
function shell(title: string, body: string, head = ''): string {
  return `<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${escapeHtml(title)}</title>
<link rel="stylesheet" href="${FONT_CSS}" crossorigin>
${head}
<style>
  :root { color-scheme: light dark; --fg: #18181b; --muted: #71717a; --bg: #ffffff; --accent: #3b82f6; --danger: #dc2626; }
  @media (prefers-color-scheme: dark) {
    :root { --fg: #fafafa; --muted: #a1a1aa; --bg: #0a0a0a; }
  }
  * { box-sizing: border-box; }
  body {
    margin: 0; min-height: 100vh; display: flex; align-items: center; justify-content: center;
    padding: 24px; background: var(--bg); color: var(--fg); font-family: ${FONT_STACK};
    -webkit-font-smoothing: antialiased;
  }
  main { width: 100%; max-width: 480px; text-align: center; }
  .label { font-size: 14px; color: var(--muted); margin: 0 0 12px; }
  .host { font-size: 28px; font-weight: 700; word-break: break-all; margin: 0 0 8px; line-height: 1.25; }
  .path { font-size: 13px; color: var(--muted); word-break: break-all; margin: 0 0 28px; }
  .bar { height: 3px; border-radius: 3px; background: color-mix(in srgb, var(--muted) 25%, transparent); overflow: hidden; }
  .bar > i { display: block; height: 100%; width: 0; background: var(--accent); animation: fill 1s linear forwards; }
  @keyframes fill { to { width: 100%; } }
  .actions { margin-top: 28px; font-size: 13px; }
  .actions button, .actions a {
    font: inherit; font-size: 13px; color: var(--muted); background: none; border: 0;
    padding: 6px 10px; cursor: pointer; text-decoration: underline; text-underline-offset: 3px;
  }
  .note { font-size: 13px; color: var(--muted); margin-top: 20px; }
  .danger { color: var(--danger); font-weight: 600; }
  code { font-size: 13px; }
</style>
</head>
<body><main>${body}</main></body>
</html>`
}

export function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function splitTarget(target: string): { host: string; rest: string } {
  try {
    const url = new URL(target)
    return { host: url.host, rest: url.pathname + url.search + url.hash }
  } catch {
    return { host: target, rest: '' }
  }
}

/**
 * The Interstitial. Shows where the Visitor is about to go and gives them a
 * moment to look at it, which is the only safety this service actually offers —
 * nothing is scanned, so no copy here may suggest otherwise.
 */
export function interstitialPage(
  code: string,
  target: string,
  apiOrigin: string,
  minDwellMs = 1000,
): Response {
  const { host, rest } = splitTarget(target)
  const body = `
  <p class="label">이 주소로 이동합니다</p>
  <p class="host">${escapeHtml(host)}</p>
  <p class="path">${escapeHtml(rest)}</p>
  <div class="bar"><i></i></div>
  <div class="actions">
    <button type="button" id="report">🚩 신고</button>
  </div>
  <noscript>
    <p class="note"><a href="${escapeHtml(target)}" rel="noreferrer">${escapeHtml(host)}(으)로 이동</a></p>
  </noscript>
  <script>
    (function () {
      var target = ${JSON.stringify(target)};
      var startedAt = Date.now();
      // replace(), never assignment: otherwise this page stays in history and
      // Back from the destination bounces the Visitor straight back here.
      var timer = setTimeout(function () { location.replace(target) },
                 Math.max(0, ${minDwellMs} - (Date.now() - startedAt)));
      var button = document.getElementById('report');
      button.addEventListener('click', function () {
        // Reporting means the Visitor does NOT want to go there. Cancel the
        // move, or the report button would send them to the Target anyway.
        clearTimeout(timer);
        button.disabled = true;
        button.textContent = '신고 접수 중...';
        fetch(${JSON.stringify(apiOrigin)} + '/reports', {
          method: 'POST',
          headers: { 'content-type': 'application/json' },
          body: JSON.stringify({ code: ${JSON.stringify(code)} })
        }).then(function () {
          button.textContent = '신고가 접수되었습니다';
        }).catch(function () {
          button.textContent = '신고에 실패했습니다';
          button.disabled = false;
        });
      });
    })();
  </script>`
  return html(shell('이동 중', body))
}

export function notFoundPage(): Response {
  const body = `
  <p class="host">없는 주소입니다</p>
  <p class="note">이 단축 주소는 존재하지 않거나 삭제되었습니다.</p>`
  return html(shell('없는 주소', body), 404)
}

/**
 * The Target is deliberately absent. Showing it would advertise exactly the
 * destination the Suspension exists to stop (ADR 0004).
 */
export function suspendedPage(): Response {
  const body = `
  <p class="host danger">중지된 주소입니다</p>
  <p class="note">이 단축 주소의 연결이 중지되었습니다.</p>`
  return html(shell('중지된 주소', body), 410)
}

export function errorPage(): Response {
  const body = `
  <p class="host">잠시 후 다시 시도해주세요</p>
  <p class="note">주소를 확인하는 중 문제가 발생했습니다.</p>`
  return html(shell('일시적 오류', body), 502)
}

function html(markup: string, status = 200): Response {
  return new Response(markup, {
    status,
    headers: {
      'content-type': 'text/html; charset=utf-8',
      // Resolution state changes when a Link is deleted or suspended, so this
      // must never be cached at the edge or in the browser.
      'cache-control': 'no-store',
    },
  })
}
