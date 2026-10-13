import { afterEach, expect, it, vi } from 'vitest'
import { api, ApiError } from '../src/lib/api'

afterEach(() => vi.unstubAllGlobals())

it('updates only the existing Code Target with the Owner session and no browser cache', async () => {
  const updated = { code: '123', shortUrl: 'https://to2.at/123', target: 'https://example.com/new#hash', state: 'SUSPENDED', createdAt: '2026-10-13T00:00:00Z' }
  const fetchApi = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => Response.json(updated))
  vi.stubGlobal('fetch', fetchApi)
  expect(await api.updateLink('123', 'https://example.com/new#hash')).toEqual(updated)
  const [url, init] = fetchApi.mock.calls[0]
  expect(String(url)).toMatch(/\/links\/123$/)
  expect(init).toMatchObject({ method: 'PATCH', credentials: 'include', cache: 'no-store', body: JSON.stringify({ target: 'https://example.com/new#hash' }) })
  expect(init?.headers).not.toHaveProperty('Authorization')
})

it('surfaces Target validation and Owner refusal without pretending the save succeeded', async () => {
  vi.stubGlobal('fetch', async () => Response.json({ message: '주소 형식이 올바르지 않습니다.' }, { status: 400 }))
  await expect(api.updateLink('123', 'bad')).rejects.toMatchObject({ status: 400, message: '주소 형식이 올바르지 않습니다.' })
  vi.stubGlobal('fetch', async () => new Response('', { status: 404 }))
  await expect(api.updateLink('123', 'https://example.com/')).rejects.toBeInstanceOf(ApiError)
})

it('includes a supplied Name in an edit while preserving name omission for existing callers', async () => {
  const updated = { code: '123', shortUrl: 'https://to2.at/123', target: 'https://example.com/new', name: '수업 기억', state: 'ACTIVE', createdAt: '2026-10-13T00:00:00Z' }
  const fetchApi = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => Response.json(updated))
  vi.stubGlobal('fetch', fetchApi)
  expect(await api.updateLink('123', updated.target, '수업 기억')).toEqual(updated)
  expect(JSON.parse(fetchApi.mock.calls[0][1]!.body as string)).toEqual({ target: updated.target, name: '수업 기억' })
  await api.updateLink('123', updated.target, '')
  expect(JSON.parse(fetchApi.mock.calls[1][1]!.body as string)).toEqual({ target: updated.target, name: '' })
})
