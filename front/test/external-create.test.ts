import { describe, expect, it, vi } from 'vitest'
import { captureExternalCreate, completeExternalCreate, copyAddress, createAndCopy, externalCreatePath } from '../src/lib/external-create'
import type { Link } from '../src/lib/api'

function storage() {
  const values = new Map<string, string>()
  return {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => { values.set(key, value) },
    removeItem: (key: string) => { values.delete(key) },
  }
}

const target = 'https://java-viz.oa.gg/web/app/?lang=java#content=abc%2Bdef+ghi&mode=run'
const entry = (target: string) => `https://to2.at/?action=create-and-copy#target=${encodeURIComponent(target)}`
const link: Link = { code: '123', shortUrl: 'https://to2.at/123', target, name: null, state: 'ACTIVE', createdAt: '2026-10-13T00:00:00Z' }

describe('external create-and-copy workflow', () => {
  it('preserves a complete share URL and resumes it after Kakao returns to the home page', async () => {
    const local = storage(), session = storage()
    const request = await captureExternalCreate(entry(target), local, session)
    expect(request?.target).toBe(target)
    expect(await captureExternalCreate('https://to2.at/', local, session)).toEqual(request)
    expect(await captureExternalCreate(`https://to2.at${externalCreatePath(request!)}`, local, session)).toEqual(request)
    completeExternalCreate(request!, session)
    expect(await captureExternalCreate('https://to2.at/', local, session)).toBeNull()
    // Refreshes retain the explicit result path without resurrecting the flow on ordinary home visits.
    expect(await captureExternalCreate(`https://to2.at${externalCreatePath(request!)}`, local, session)).toEqual(request)
  })

  it('uses the same request identity across reopens tabs and the compatible query form', async () => {
    const first = await captureExternalCreate(entry(target), storage(), storage())
    const reopened = await captureExternalCreate(entry(target), storage(), storage())
    const query = await captureExternalCreate(`https://to2.at/?target=${encodeURIComponent(target)}&action=create-and-copy`, storage(), storage())
    expect(reopened).toEqual(first)
    expect(query).toEqual(first)
    const other = await captureExternalCreate(entry(target + 'changed'), storage(), storage())
    expect(other?.requestId).not.toBe(first?.requestId)
  })

  it('carries a 128 KiB fragment without placing it in an HTTP query', async () => {
    const longTarget = target + 'x'.repeat(131072)
    const href = entry(longTarget)
    expect(new URL(href).search).toBe('?action=create-and-copy')
    expect((await captureExternalCreate(href, storage(), storage()))?.target).toBe(longTarget)
  })

  it('uses the authenticated creation API then copies only the returned short address', async () => {
    const request = (await captureExternalCreate(entry(target), storage(), storage()))!
    const create = vi.fn().mockResolvedValue(link), write = vi.fn().mockResolvedValue(undefined)
    expect(await createAndCopy(request, create, write)).toEqual({ link, copied: true })
    expect(create).toHaveBeenCalledWith(target, request.requestId)
    expect(write).toHaveBeenCalledWith('https://to2.at/123')
  })

  it('keeps the result when automatic copying is denied and manual copying can succeed', async () => {
    const request = (await captureExternalCreate(entry(target), storage(), storage()))!
    const create = vi.fn().mockResolvedValue(link)
    const denied = vi.fn().mockRejectedValue(new Error('User gesture required'))
    expect(await createAndCopy(request, create, denied)).toEqual({ link, copied: false })
    expect(await copyAddress(link.shortUrl, vi.fn().mockResolvedValue(undefined))).toBe(true)
    expect(create).toHaveBeenCalledTimes(1)
    expect(await copyAddress(link.shortUrl)).toBe(false)
  })

  it('does not copy a deleted or suspended result', async () => {
    const request = (await captureExternalCreate(entry(target), storage(), storage()))!
    const write = vi.fn()
    for (const state of ['DELETED', 'SUSPENDED'] as const) {
      expect((await createAndCopy(request, async () => ({ ...link, state }), write)).copied).toBe(false)
    }
    expect(write).not.toHaveBeenCalled()
  })

  it('shows the copy fallback when the browser never settles its permission promise', async () => {
    vi.useFakeTimers()
    try {
      const copying = copyAddress(link.shortUrl, () => new Promise(() => {}))
      await vi.advanceTimersByTimeAsync(1500)
      expect(await copying).toBe(false)
    } finally {
      vi.useRealTimers()
    }
  })

  it('fails visibly without durable storage or valid input and does not navigate', async () => {
    await expect(captureExternalCreate(entry(target), { ...storage(), setItem: () => { throw new Error('Quota exceeded') } }, storage())).rejects.toThrow('Quota exceeded')
    await expect(captureExternalCreate(entry('javascript:alert(1)'), storage(), storage())).rejects.toThrow()
    await expect(captureExternalCreate('https://to2.at/?action=create-and-copy&request=bad', storage(), storage())).rejects.toThrow()
    await expect(captureExternalCreate('https://to2.at/?action=create-and-copy', storage(), storage())).rejects.toThrow()
    expect(await captureExternalCreate(`https://to2.at/?target=${encodeURIComponent(target)}`, storage(), storage())).toBeNull()
  })

  it('propagates creation failure without copying or automatic retries', async () => {
    const request = (await captureExternalCreate(entry(target), storage(), storage()))!
    const create = vi.fn().mockRejectedValue(new Error('Network unavailable')), write = vi.fn()
    await expect(createAndCopy(request, create, write)).rejects.toThrow('Network unavailable')
    expect(create).toHaveBeenCalledTimes(1)
    expect(write).not.toHaveBeenCalled()
  })
})
