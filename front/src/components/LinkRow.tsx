import { useState } from 'react'
import { ApiError, api, type Link } from '../lib/api'

export function LinkRow({ link, onDelete, onUpdated }: { link: Link; onDelete: () => void; onUpdated: (link: Link) => void }) {
  const [copied, setCopied] = useState(false)
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState(link.target)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  function copy() {
    navigator.clipboard.writeText(link.shortUrl).then(() => {
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    })
  }

  function edit() {
    setDraft(link.target)
    setError(null)
    setSaved(false)
    setEditing(true)
  }

  async function save(event: React.FormEvent) {
    event.preventDefault()
    if (busy) return
    setBusy(true)
    setError(null)
    try {
      const updated = await api.updateLink(link.code, draft)
      onUpdated(updated)
      setEditing(false)
      setSaved(true)
    } catch (err) {
      setError(err instanceof ApiError && err.status === 404 ? '이 주소를 수정할 수 없습니다. 목록을 새로고침해주세요.'
        : err instanceof ApiError ? err.message : '저장하지 못했습니다. 잠시 후 다시 시도해주세요.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <li className="flex flex-wrap items-center gap-3 py-3">
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <span className="font-semibold">{link.shortUrl.replace('https://', '')}</span>
          {link.state === 'SUSPENDED' && <span className="rounded bg-red-100 px-1.5 py-0.5 text-xs font-medium text-red-700 dark:bg-red-950 dark:text-red-300">중지됨</span>}
        </div>
        <p className="truncate text-sm text-zinc-500" title={link.target}>{link.target}</p>
        <p className="text-xs text-zinc-400">{new Date(link.createdAt).toLocaleString('ko-KR')}</p>
        {saved && <p className="mt-1 text-xs text-zinc-500" role="status">대상 URL을 저장했습니다.</p>}
      </div>
      <button type="button" onClick={edit} disabled={busy} className="shrink-0 text-sm text-zinc-500 underline underline-offset-4 disabled:opacity-50">수정</button>
      <button type="button" onClick={copy} className="shrink-0 text-sm text-zinc-500 underline underline-offset-4">{copied ? '복사됨' : '복사'}</button>
      <button type="button" onClick={onDelete} disabled={busy} className="shrink-0 text-sm text-zinc-400 underline underline-offset-4 hover:text-red-600 disabled:opacity-50">삭제</button>
      {editing && <form onSubmit={save} className="w-full">
        <label htmlFor={`link-target-${link.code}`} className="text-sm">대상 URL</label>
        <div className="mt-2 flex flex-wrap gap-2">
          <input id={`link-target-${link.code}`} type="url" required value={draft} onChange={(event) => setDraft(event.target.value)} disabled={busy} autoFocus className="min-w-0 flex-1 rounded-lg border border-zinc-300 bg-transparent px-3 py-2 text-sm dark:border-zinc-700" />
          <button type="submit" disabled={busy} className="rounded-lg bg-zinc-900 px-4 py-2 text-sm text-white disabled:opacity-50 dark:bg-zinc-100 dark:text-zinc-900">{busy ? '저장 중…' : '저장'}</button>
          <button type="button" onClick={() => { setEditing(false); setError(null) }} disabled={busy} className="rounded-lg border px-4 py-2 text-sm disabled:opacity-50">취소</button>
        </div>
        {error && <p className="mt-2 text-sm text-red-600" role="alert">{error}</p>}
      </form>}
    </li>
  )
}
