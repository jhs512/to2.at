import { errorPage, interstitialPage, notFoundPage, suspendedPage } from './pages'

export interface Env {
  ASSETS: Fetcher
  /** Origin of the backend, e.g. https://api.to2.at */
  API_ORIGIN: string
}

type LinkState = 'ACTIVE' | 'DELETED' | 'SUSPENDED'

interface ResolvedCode {
  code: string
  state: LinkState
  target?: string
}

/**
 * A Code is digits only, in canonical form. Keeping the alphabet to digits is
 * what lets app routes (`/dashboard`) and Codes (`/12`) share the root without
 * a reserved-word list (ADR 0002).
 */
export function matchCode(pathname: string): string | null {
  const m = /^\/(\d+)$/.exec(pathname)
  if (!m) return null
  const code = m[1]
  // `/01` and `/0000001` are not spellings of `/1`.
  if (code.length > 1 && code.startsWith('0')) return null
  if (code === '0') return null
  return code
}

/**
 * Link preview crawlers, which do not run JavaScript and therefore only ever see
 * what the server returns.
 *
 * Kakao's own token comes first because it is the one that matters here, but the
 * list is deliberately wider: Kakao's User-Agent also carries the
 * `facebookexternalhit` prefix that Meta and LINE share, and every one of these
 * is a preview crawler that benefits from the same 302. Matching them all means
 * a short address previews correctly wherever it is pasted, not only in Kakao.
 *
 * Widening this is a deliberate departure from ADR 0004, which named only
 * `kakaotalk-scrap`; the ADR records the change.
 */
const CRAWLER_UA = /kakaotalk-scrap|facebookexternalhit|line-poker|Twitterbot|Slackbot|Discordbot|WhatsApp|TelegramBot|Applebot/i

export function isCrawler(request: Request): boolean {
  return CRAWLER_UA.test(request.headers.get('user-agent') ?? '')
}

async function handleCode(code: string, request: Request, env: Env): Promise<Response> {
  let resolved: ResolvedCode
  try {
    const res = await fetch(`${env.API_ORIGIN}/to/${code}`, {
      headers: { accept: 'application/json' },
    })
    if (res.status === 404) return notFoundPage()
    if (!res.ok) return errorPage()
    resolved = (await res.json()) as ResolvedCode
  } catch {
    return errorPage()
  }

  switch (resolved.state) {
    case 'ACTIVE':
      if (!resolved.target) return errorPage()
      // Kakao follows redirects and scrapes where it lands, so handing a
      // crawler a 302 gets the Target's own preview card for free — no need to
      // fetch or store its Open Graph tags anywhere.
      if (isCrawler(request)) return Response.redirect(resolved.target, 302)
      return interstitialPage(code, resolved.target, env.API_ORIGIN)
    case 'SUSPENDED':
      // Deliberately NOT a redirect, for crawlers either: a suspended Link that
      // still produces the Target's card is suspended in name only (ADR 0004).
      return suspendedPage()
    case 'DELETED':
    default:
      return notFoundPage()
  }
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url)

    const code = matchCode(url.pathname)
    if (code) return handleCode(code, request, env)

    // Everything else is the SPA. Note this Worker runs ahead of asset serving
    // for all non-asset paths: with the default SPA fallback a browser-typed
    // `/12` is answered with the shell and never reaches this code at all.
    return env.ASSETS.fetch(request)
  },
} satisfies ExportedHandler<Env>
