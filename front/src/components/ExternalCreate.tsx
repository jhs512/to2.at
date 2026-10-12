import { useEffect, useState } from 'react'
import { ApiError, api, type Link } from '../lib/api'
import { completeExternalCreate, copyAddress, createAndCopy, type ExternalCreate } from '../lib/external-create'

function clipboardWriter() {
  return navigator.clipboard?.writeText.bind(navigator.clipboard)
}

export function ExternalCreateCard({ request, onCreated }: { request: ExternalCreate; onCreated: () => void }) {
  const [result, setResult] = useState<Link | null>(null)
  const [copied, setCopied] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    let active = true
    createAndCopy(request, api.createLink, clipboardWriter())
      .then(({ link, copied }) => {
        if (!active) return
        setResult(link)
        setCopied(copied)
        completeExternalCreate(request, sessionStorage)
        onCreated()
      })
      .catch((err: unknown) => {
        if (!active) return
        setError(err instanceof ApiError && err.status === 409
          ? '기존 주소의 대상이 변경되었습니다. 필요하면 아래에서 새 주소를 만들어주세요.'
          : err instanceof ApiError ? err.message : '주소를 만들지 못했습니다. 잠시 후 다시 시도해주세요.')
      })
    return () => { active = false }
  }, [request, attempt, onCreated])

  async function copy() {
    if (result) setCopied(await copyAddress(result.shortUrl, clipboardWriter()))
  }

  const active = result?.state === 'ACTIVE'
  return (
    <section className="mt-8 rounded-xl border border-zinc-300 p-5 dark:border-zinc-700" aria-live="polite">
      <h2 className="font-semibold">공유 주소를 짧게 만들기</h2>
      <p className="mt-2 truncate text-sm text-zinc-500" title={request.target}>{request.target}</p>
      {!result && !error && <p className="mt-4 text-sm">주소를 만들고 있습니다…</p>}
      {error && <>
        <p className="mt-4 text-sm text-red-600">{error}</p>
        <button type="button" onClick={() => { setError(null); setAttempt((n) => n + 1) }} className="mt-3 rounded-lg border px-4 py-2 text-sm">다시 시도</button>
      </>}
      {result && <>
        <label className="mt-4 block text-sm" htmlFor="external-short-url">단축 주소</label>
        <input id="external-short-url" readOnly value={result.shortUrl} onFocus={(event) => event.currentTarget.select()} className="mt-2 w-full rounded-lg border border-zinc-300 bg-transparent px-3 py-2 dark:border-zinc-700" />
        {active ? <>
          <p className="mt-3 text-sm text-zinc-500">{copied ? '단축 주소를 복사했습니다.' : '복사 버튼을 눌러주세요. 복사가 안 되면 위 주소를 선택해 복사할 수 있습니다.'}</p>
          <button type="button" onClick={copy} className="mt-3 rounded-lg bg-zinc-900 px-4 py-2 text-sm font-semibold text-white dark:bg-zinc-100 dark:text-zinc-900">{copied ? '다시 복사' : '단축 주소 복사'}</button>
        </> : <p className="mt-3 text-sm text-red-600">{result.state === 'SUSPENDED' ? '이 주소는 중지되었습니다.' : '이 주소는 삭제되었습니다.'} 새 주소를 자동으로 만들지 않았습니다.</p>}
      </>}
    </section>
  )
}
