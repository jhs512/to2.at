import { createFileRoute } from '@tanstack/react-router'
import { useCallback, useEffect, useState } from 'react'

import { ApiError, api, logout, startKakaoLogin, type Link, type Me } from '../lib/api'
import { ApiKeys } from '../components/ApiKeys'

export const Route = createFileRoute('/')({ component: Home })

function Home() {
  const [me, setMe] = useState<Me | null>(null)
  const [checked, setChecked] = useState(false)

  useEffect(() => {
    api
      .me()
      .then(setMe)
      .catch(() => setMe(null))
      .finally(() => setChecked(true))
  }, [])

  if (!checked) return null
  return me ? <Dashboard me={me} /> : <Landing />
}

/**
 * The public landing page. Deliberately a page rather than an immediate bounce
 * to Kakao: a Visitor who lands here by accident needs somewhere to stand.
 */
function Landing() {
  return (
    <main className="mx-auto flex min-h-dvh max-w-md flex-col items-center justify-center px-6 text-center">
      <h1 className="text-4xl font-bold tracking-tight">to2.at</h1>
      <p className="mt-3 text-sm text-zinc-500">카카오 계정으로 만드는 짧은 주소</p>
      <button
        type="button"
        onClick={startKakaoLogin}
        className="mt-10 w-full rounded-lg bg-[#FEE500] px-5 py-3 text-sm font-semibold text-[#191600] transition hover:brightness-95"
      >
        카카오로 시작하기
      </button>
    </main>
  )
}

function Dashboard({ me }: { me: Me }) {
  const [links, setLinks] = useState<Link[]>([])
  const [target, setTarget] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const reload = useCallback(() => {
    api.listLinks().then(setLinks).catch(console.error)
  }, [])

  useEffect(reload, [reload])

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await api.createLink(target)
      setTarget('')
      reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '잠시 후 다시 시도해주세요.')
    } finally {
      setBusy(false)
    }
  }

  async function remove(code: string) {
    await api.deleteLink(code)
    reload()
  }

  return (
    <main className="mx-auto w-full max-w-2xl px-6 py-12">
      <header className="flex items-baseline justify-between">
        <h1 className="text-2xl font-bold tracking-tight">to2.at</h1>
        <div className="text-sm text-zinc-500">
          {me.nickname}님
          <button type="button" onClick={logout} className="ml-3 underline underline-offset-4">
            로그아웃
          </button>
        </div>
      </header>

      <ApiKeys />

      <form onSubmit={submit} className="mt-8 flex gap-2">
        <input
          value={target}
          onChange={(e) => setTarget(e.target.value)}
          placeholder="https://..."
          className="flex-1 rounded-lg border border-zinc-300 px-4 py-2.5 text-sm outline-none focus:border-zinc-900 dark:border-zinc-700 dark:bg-zinc-900 dark:focus:border-zinc-300"
        />
        <button
          type="submit"
          disabled={busy}
          className="rounded-lg bg-zinc-900 px-5 py-2.5 text-sm font-semibold text-white disabled:opacity-50 dark:bg-zinc-100 dark:text-zinc-900"
        >
          줄이기
        </button>
      </form>
      {error && <p className="mt-2 text-sm text-red-600">{error}</p>}

      <ul className="mt-8 divide-y divide-zinc-200 dark:divide-zinc-800">
        {links.map((link) => (
          <LinkRow key={link.code} link={link} onDelete={() => remove(link.code)} />
        ))}
      </ul>
      {links.length === 0 && (
        <p className="mt-8 text-center text-sm text-zinc-500">아직 만든 주소가 없습니다.</p>
      )}
    </main>
  )
}

function LinkRow({ link, onDelete }: { link: Link; onDelete: () => void }) {
  const [copied, setCopied] = useState(false)

  function copy() {
    navigator.clipboard.writeText(link.shortUrl).then(() => {
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    })
  }

  return (
    <li className="flex items-center gap-3 py-3">
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <span className="font-semibold">{link.shortUrl.replace('https://', '')}</span>
          {link.state === 'SUSPENDED' && (
            <span className="rounded bg-red-100 px-1.5 py-0.5 text-xs font-medium text-red-700 dark:bg-red-950 dark:text-red-300">
              중지됨
            </span>
          )}
        </div>
        <p className="truncate text-sm text-zinc-500">{link.target}</p>
        <p className="text-xs text-zinc-400">{new Date(link.createdAt).toLocaleString('ko-KR')}</p>
      </div>
      <button
        type="button"
        onClick={copy}
        className="shrink-0 text-sm text-zinc-500 underline underline-offset-4"
      >
        {copied ? '복사됨' : '복사'}
      </button>
      <button
        type="button"
        onClick={onDelete}
        className="shrink-0 text-sm text-zinc-400 underline underline-offset-4 hover:text-red-600"
      >
        삭제
      </button>
    </li>
  )
}
