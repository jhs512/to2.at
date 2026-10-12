import { afterEach, describe, expect, it, vi } from 'vitest'
import worker, { isCrawler, matchCode, type Env } from '../worker/index'

/** Stands in for the backend. Seam B stops at the fetch boundary. */
function stubApi(handler: (code: string) => Response) {
  vi.stubGlobal('fetch', (input: RequestInfo | URL) => {
    const url = new URL(typeof input === 'string' ? input : input.toString())
    const code = url.pathname.replace('/to/', '')
    return Promise.resolve(handler(code))
  })
}

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

const env: Env = {
  API_ORIGIN: 'https://api.example.test',
  ASSETS: {
    fetch: () => Promise.resolve(new Response('SPA SHELL', { status: 200 })),
  } as unknown as Fetcher,
}

const get = (path: string, headers: Record<string, string> = {}) =>
  worker.fetch(new Request(`https://to2.at${path}`, { headers }), env)

afterEach(() => vi.unstubAllGlobals())

describe('Code matching', () => {
  it('accepts only the canonical spelling of a Code', () => {
    expect(matchCode('/1')).toBe('1')
    expect(matchCode('/4096')).toBe('4096')

    // One Link, one address.
    expect(matchCode('/01')).toBeNull()
    expect(matchCode('/0')).toBeNull()
    expect(matchCode('/0000001')).toBeNull()
  })

  it('leaves app routes alone', () => {
    // Digit-only Codes are what let app routes share the root with no
    // reserved-word list.
    expect(matchCode('/')).toBeNull()
    expect(matchCode('/dashboard')).toBeNull()
    expect(matchCode('/links/1')).toBeNull()
  })
})

describe('a Visitor opening a Code', () => {
  it('keeps the entire large share fragment in the browser navigation Target', async () => {
    const target = 'https://java-viz.oa.gg/web/app/#payload=' + 'x'.repeat(131072) + '%2B%26%3D&mode=java'
    stubApi(() => json({ code: '1', state: 'ACTIVE', target }))
    const response = await get('/1')
    expect(response.status).toBe(200)
    expect(await response.text()).toContain(`var target = ${JSON.stringify(target)};`)
  })
  it('sees the destination host before being moved', async () => {
    stubApi(() => json({ code: '1', state: 'ACTIVE', target: 'https://example.com/a/b?c=d' }))

    const res = await get('/1')
    const body = await res.text()

    expect(res.status).toBe(200)
    expect(res.headers.get('content-type')).toContain('text/html')
    expect(body).toContain('example.com')
    expect(body).toContain('/a/b?c=d')
  })

  it('is moved with a history-replacing navigation', async () => {
    stubApi(() => json({ code: '1', state: 'ACTIVE', target: 'https://example.com/' }))

    const body = await (await get('/1')).text()

    // Assigning to location instead would leave the Interstitial in history,
    // so Back from the destination would bounce straight back here.
    expect(body).toContain('location.replace')
    expect(body).not.toMatch(/location\.href\s*=/)
  })

  it('still offers the Target when scripting is off', async () => {
    stubApi(() => json({ code: '1', state: 'ACTIVE', target: 'https://example.com/deep' }))

    const body = await (await get('/1')).text()

    expect(body).toContain('<noscript>')
    expect(body).toContain('https://example.com/deep')
  })

  it('does not claim the Target was checked for safety', async () => {
    stubApi(() => json({ code: '1', state: 'ACTIVE', target: 'https://example.com/' }))

    const body = await (await get('/1')).text()

    // Nothing is scanned. Copy that implies otherwise would be a safety claim
    // the service does not honour.
    expect(body).not.toContain('검사')
    expect(body).not.toContain('안전')
  })

  it('waits before moving', async () => {
    stubApi(() => json({ code: '1', state: 'ACTIVE', target: 'https://example.com/' }))

    const body = await (await get('/1')).text()

    expect(body).toMatch(/setTimeout/)
    expect(body).toContain('1000')
  })
})

describe('Codes that do not resolve', () => {
  it('shows a not-found page for an unknown Code', async () => {
    stubApi(() => new Response('', { status: 404 }))

    const res = await get('/999')

    expect(res.status).toBe(404)
    expect(await res.text()).toContain('없는 주소')
  })

  it('shows a not-found page for a deleted Code', async () => {
    stubApi(() => json({ code: '1', state: 'DELETED' }))

    const res = await get('/1')

    expect(res.status).toBe(404)
  })

  it('never discloses the Target of a suspended Code', async () => {
    stubApi(() => json({ code: '1', state: 'SUSPENDED' }))

    const res = await get('/1')
    const body = await res.text()

    expect(res.status).toBe(410)
    expect(body).toContain('중지')
    // The block page must not advertise the destination it exists to stop.
    expect(body).not.toContain('example.com')
  })

  it('degrades to an error page when the backend is unreachable', async () => {
    vi.stubGlobal('fetch', () => Promise.reject(new Error('down')))

    expect((await get('/1')).status).toBe(502)
  })
})

describe('everything that is not a Code', () => {
  it('falls through to the static assets', async () => {
    const res = await get('/dashboard')

    expect(await res.text()).toBe('SPA SHELL')
  })
})

describe('caching', () => {
  it('never caches a resolution', async () => {
    stubApi(() => json({ code: '1', state: 'ACTIVE', target: 'https://example.com/' }))

    // Resolution state changes when a Link is deleted or suspended; a cached
    // Interstitial would keep sending Visitors to a stopped Target.
    expect((await get('/1')).headers.get('cache-control')).toBe('no-store')
  })
})

describe('reporting from the Interstitial', () => {
  it('offers a report button and cancels the move when it is used', async () => {
    stubApi(() => json({ code: '1', state: 'ACTIVE', target: 'https://example.com/' }))

    const body = await (await get('/1')).text()

    expect(body).toContain('id="report"')
    expect(body).toContain('/reports')
    // Reporting means the Visitor does not want to go there; leaving the timer
    // running would send them to the Target anyway.
    expect(body).toContain('clearTimeout')
  })
})

describe('link preview crawlers', () => {
  const KAKAO_UA = 'facebookexternalhit/1.1;kakaotalk-scrap/1.0'

  it('sends a crawler to the Target so the preview card is the Target’s own', async () => {
    stubApi(() => json({ code: '1', state: 'ACTIVE', target: 'https://example.com/article' }))

    const res = await get('/1', { 'user-agent': KAKAO_UA })

    // Kakao follows the redirect and scrapes where it lands, so this one branch
    // buys a correct preview without ever fetching the Target's tags ourselves.
    expect(res.status).toBe(302)
    expect(res.headers.get('location')).toBe('https://example.com/article')
  })

  it('never redirects a crawler to a suspended Target', async () => {
    stubApi(() => json({ code: '1', state: 'SUSPENDED' }))

    const res = await get('/1', { 'user-agent': KAKAO_UA })
    const body = await res.text()

    // Otherwise the Link is blocked for people while still advertising the
    // destination in every chat it was ever pasted into.
    expect(res.status).not.toBe(302)
    expect(res.headers.get('location')).toBeNull()
    expect(body).not.toContain('example.com')
  })

  it('never redirects a crawler for an unknown Code', async () => {
    stubApi(() => new Response('', { status: 404 }))

    expect((await get('/999', { 'user-agent': KAKAO_UA })).status).toBe(404)
  })

  it('still shows people the Interstitial', async () => {
    stubApi(() => json({ code: '1', state: 'ACTIVE', target: 'https://example.com/article' }))

    const res = await get('/1', { 'user-agent': 'Mozilla/5.0 (Macintosh) Safari/605' })

    expect(res.status).toBe(200)
    expect(await res.text()).toContain('example.com')
  })

  it('treats every preview crawler the same, and nothing else', async () => {
    const ua = (value: string) =>
      isCrawler(new Request('https://to2.at/1', { headers: { 'user-agent': value } }))

    // Kakao is the one that matters, but a short address gets pasted into more
    // than one app and the same 302 gives each of them a correct card.
    expect(ua(KAKAO_UA)).toBe(true)
    expect(ua('facebookexternalhit/1.1')).toBe(true)
    expect(ua('facebookexternalhit/1.1;line-poker/1.0')).toBe(true)
    expect(ua('Slackbot-LinkExpanding 1.0')).toBe(true)

    // People still get the Interstitial.
    expect(ua('Mozilla/5.0 (Macintosh) Safari/605')).toBe(false)
    expect(ua('')).toBe(false)
  })

  it('does not put a canonical Open Graph URL on the Interstitial', async () => {
    stubApi(() => json({ code: '1', state: 'ACTIVE', target: 'https://example.com/' }))

    const body = await (await get('/1')).text()

    // An og:url different from the requested address sends Kakao off to scrape
    // that other address instead.
    expect(body).not.toContain('og:url')
  })
})
