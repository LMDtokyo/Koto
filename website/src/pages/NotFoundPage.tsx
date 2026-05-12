import { Link } from "react-router";

export function NotFoundPage() {
  return (
    <section className="mx-auto flex max-w-2xl flex-col items-center px-6 py-32 text-center">
      <div className="text-7xl font-semibold tracking-tight">404</div>
      <p className="mt-3 text-[var(--color-text-dim)]">
        Страница не найдена. Возможно, она была перемещена.
      </p>
      <Link
        to="/"
        className="mt-8 inline-flex items-center gap-2 rounded-xl bg-gradient-to-br from-[var(--color-accent)] to-[var(--color-accent-2)] px-5 py-3 font-medium text-white shadow-lg shadow-[var(--color-accent)]/25 hover:opacity-95"
      >
        На главную
      </Link>
    </section>
  );
}
