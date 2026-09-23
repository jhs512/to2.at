import { createRootRoute, Outlet } from '@tanstack/react-router'

export const Route = createRootRoute({
  component: () => (
    <div className="min-h-dvh bg-white text-zinc-900 dark:bg-zinc-950 dark:text-zinc-50">
      <Outlet />
    </div>
  ),
  notFoundComponent: () => (
    <main className="mx-auto flex min-h-dvh max-w-md flex-col items-center justify-center px-6 text-center">
      <p className="text-2xl font-bold">없는 페이지입니다</p>
      <a href="/" className="mt-4 text-sm text-zinc-500 underline underline-offset-4">
        처음으로
      </a>
    </main>
  ),
})
