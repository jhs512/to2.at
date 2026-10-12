import { useCallback, useEffect, useState } from 'react'
import { api, ApiError, type ApiKeyMetadata, type IssuedApiKey } from '../lib/api'

export function ApiKeys() {
  const [keys, setKeys] = useState<ApiKeyMetadata[]>([])
  const [name, setName] = useState('Codex')
  const [issued, setIssued] = useState<IssuedApiKey | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)

  const load = useCallback(async () => {
    setKeys(await api.listApiKeys())
  }, [])

  useEffect(() => {
    api.listApiKeys().then(setKeys).catch(() => setError('API 키 목록을 불러오지 못했습니다.'))
  }, [])

  async function create() {
    setBusy(true)
    setError(null)
    try {
      const key = await api.createApiKey(name)
      setIssued(key)
      setCopied(false)
      await load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '발급 결과를 확인하지 못했습니다. 목록을 확인한 뒤 다시 시도해주세요.')
    } finally {
      setBusy(false)
    }
  }

  async function revoke(id: number) {
    setBusy(true)
    setError(null)
    try {
      await api.revokeApiKey(id)
      if (issued?.id === id) setIssued(null)
      await load()
    } catch {
      setError('키를 폐기하지 못했습니다. 다시 시도해주세요.')
    } finally {
      setBusy(false)
    }
  }

  async function copy() {
    if (!issued) return
    try {
      await navigator.clipboard.writeText(issued.key)
      setCopied(true)
    } catch {
      setError('복사하지 못했습니다. 표시된 키를 직접 복사해주세요.')
    }
  }

  return (
    <details className="mt-8 rounded-lg border border-zinc-200 p-4 dark:border-zinc-700">
      <summary className="cursor-pointer font-semibold">API 키</summary>
      <p className="mt-3 text-sm text-zinc-500">
        스킬이나 외부 도구에서 내 주소를 관리할 때 사용합니다. 키를 가진 도구는 내 주소를 만들고 삭제할 수 있습니다.
      </p>
      <div className="mt-3 flex gap-2">
        <input
          aria-label="API 키 이름"
          value={name}
          onChange={(event) => setName(event.target.value)}
          maxLength={80}
          className="min-w-0 flex-1 rounded border border-zinc-300 px-3 py-2 dark:border-zinc-700"
        />
        <button type="button" disabled={busy || !name.trim() || issued !== null} onClick={create} className="rounded bg-zinc-900 px-3 py-2 text-sm text-white disabled:opacity-50 dark:bg-zinc-100 dark:text-zinc-900">
          새 키 발급
        </button>
      </div>
      {issued && (
        <div className="mt-4 rounded bg-zinc-100 p-3 dark:bg-zinc-800">
          <p className="text-sm">이 키는 지금 한 번만 표시됩니다. 안전한 곳에 저장하세요.</p>
          <code className="mt-2 block break-all select-all text-sm">{issued.key}</code>
          <div className="mt-2 flex gap-4 text-sm">
            <button type="button" onClick={copy} className="underline">{copied ? '복사됨' : '키 복사'}</button>
            <button type="button" onClick={() => setIssued(null)} className="underline">저장했어요 · 닫기</button>
          </div>
        </div>
      )}
      {error && <p role="alert" className="mt-3 text-sm text-red-600">{error}</p>}
      <ul className="mt-4 divide-y divide-zinc-200 dark:divide-zinc-700">
        {keys.map((key) => (
          <li key={key.id} className="flex items-center justify-between gap-3 py-2 text-sm">
            <span>{key.name} · {new Date(key.createdAt).toLocaleDateString('ko-KR')} {key.revokedAt ? '· 폐기됨' : ''}</span>
            {!key.revokedAt && <button type="button" disabled={busy} onClick={() => revoke(key.id)} className="text-red-600 underline disabled:opacity-50">폐기</button>}
          </li>
        ))}
      </ul>
    </details>
  )
}
