import type { Link } from './api'

export interface ExternalCreate {
  target: string
  requestId: string
}

const PREFIX = 'to2:external-create:'
const PENDING = 'to2:external-create-pending'
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/

function validateTarget(target: string) {
  let url: URL
  try { url = new URL(target) } catch { throw new Error('올바른 공유 주소를 전달해주세요.') }
  if (!['http:', 'https:'].includes(url.protocol) || url.hostname === 'to2.at' || url.hostname.endsWith('.to2.at')) {
    throw new Error('http 또는 https 공유 주소를 전달해주세요.')
  }
}

/** Stable across refreshes, tabs and repeated opens, scoped by Owner in the API. */
async function requestIdFor(target: string): Promise<string> {
  const digest = new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(`to2.at:create-and-copy:v1\n${target}`)))
  const bytes = digest.slice(0, 16)
  bytes[6] = (bytes[6] & 0x0f) | 0x80 // UUID version 8 (application-defined).
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

/** Capture before OAuth. The fragment wins; URLSearchParams decodes exactly once. */
export async function captureExternalCreate(href: string, local: Pick<Storage, 'getItem' | 'setItem'>, session: Pick<Storage, 'getItem' | 'setItem'>): Promise<ExternalCreate | null> {
  const url = new URL(href)
  const explicit = url.searchParams.get('action') === 'create-and-copy'
  if (url.searchParams.has('action') && !explicit) return null
  const target = explicit ? new URLSearchParams(url.hash.slice(1)).get('target') ?? url.searchParams.get('target') : null
  let request: ExternalCreate
  if (target !== null) {
    validateTarget(target)
    request = { target, requestId: await requestIdFor(target) }
    // Persist before touching the address or navigating: failure must be visible.
    local.setItem(PREFIX + request.requestId, JSON.stringify(request))
  } else {
    const id = explicit ? url.searchParams.get('request') : session.getItem(PENDING)
    if (!id) {
      if (explicit) throw new Error('공유 주소가 없습니다. 원래 화면에서 다시 열어주세요.')
      return null
    }
    if (!UUID.test(id)) throw new Error('공유 요청을 읽을 수 없습니다. 원래 화면에서 다시 열어주세요.')
    const stored = local.getItem(PREFIX + id)
    if (!stored) throw new Error('공유 주소가 저장되어 있지 않습니다. 원래 화면에서 다시 열어주세요.')
    const parsed: unknown = JSON.parse(stored)
    if (typeof parsed !== 'object' || parsed === null || !('target' in parsed) || !('requestId' in parsed) || typeof parsed.target !== 'string' || parsed.requestId !== id) {
      throw new Error('공유 요청을 읽을 수 없습니다. 원래 화면에서 다시 열어주세요.')
    }
    validateTarget(parsed.target)
    request = { target: parsed.target, requestId: id }
  }
  session.setItem(PENDING, request.requestId)
  return request
}

export function externalCreatePath(request: ExternalCreate): string {
  return `/?action=create-and-copy&request=${request.requestId}`
}

export function completeExternalCreate(request: ExternalCreate, session: Pick<Storage, 'getItem' | 'removeItem'>) {
  if (session.getItem(PENDING) === request.requestId) session.removeItem(PENDING)
}

/** A rejected/missing clipboard is a normal fallback, never another creation. */
export async function copyAddress(shortUrl: string, writeText?: (text: string) => Promise<void>): Promise<boolean> {
  let timer: ReturnType<typeof setTimeout> | undefined
  try {
    if (!writeText) return false
    // Some browsers leave the permission promise pending rather than reject it.
    return await Promise.race([
      writeText(shortUrl).then(() => true),
      new Promise<boolean>((resolve) => { timer = setTimeout(() => resolve(false), 1500) }),
    ])
  } catch {
    return false
  } finally {
    clearTimeout(timer)
  }
}

export async function createAndCopy(request: ExternalCreate, create: (target: string, requestId: string) => Promise<Link>, writeText?: (text: string) => Promise<void>) {
  const link = await create(request.target, request.requestId)
  const copied = link.state === 'ACTIVE' && await copyAddress(link.shortUrl, writeText)
  return { link, copied }
}
