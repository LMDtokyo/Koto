import { NavLink, Link } from "react-router";
import { Download, BookOpen, Sparkles, Github } from "lucide-react";

const NAV_LINKS = [
  { to: "/download", label: "Скачать", icon: Download },
  { to: "/docs", label: "Документация", icon: BookOpen },
  { to: "/updates", label: "Обновления", icon: Sparkles },
];

export function Navbar() {
  return (
    <header className="sticky top-0 z-30 border-b border-[var(--color-border)]/60 bg-[var(--color-bg)]/80 backdrop-blur-xl">
      <div className="mx-auto flex h-16 max-w-6xl items-center gap-6 px-6">
        <Link to="/" className="flex items-center gap-2 font-semibold">
          <span className="grid h-8 w-8 place-items-center rounded-lg bg-gradient-to-br from-[var(--color-accent)] to-[var(--color-accent-2)] text-sm font-bold text-white">
            K
          </span>
          <span className="text-[15px] tracking-tight">Koto</span>
        </Link>

        <nav className="ml-2 hidden items-center gap-1 md:flex">
          {NAV_LINKS.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                [
                  "inline-flex items-center gap-2 rounded-lg px-3 py-1.5 text-sm transition-colors",
                  isActive
                    ? "bg-[var(--color-surface)] text-[var(--color-text)]"
                    : "text-[var(--color-text-dim)] hover:bg-[var(--color-surface)]/60 hover:text-[var(--color-text)]",
                ].join(" ")
              }
            >
              <Icon className="h-4 w-4" />
              {label}
            </NavLink>
          ))}
        </nav>

        <div className="ml-auto flex items-center gap-2">
          <a
            href="https://github.com/"
            target="_blank"
            rel="noreferrer noopener"
            className="hidden items-center gap-2 rounded-lg border border-[var(--color-border)] px-3 py-1.5 text-sm text-[var(--color-text-dim)] hover:bg-[var(--color-surface)] hover:text-[var(--color-text)] sm:inline-flex"
          >
            <Github className="h-4 w-4" />
            GitHub
          </a>
          <Link
            to="/download"
            className="inline-flex items-center gap-2 rounded-lg bg-gradient-to-br from-[var(--color-accent)] to-[var(--color-accent-2)] px-3 py-1.5 text-sm font-medium text-white shadow-lg shadow-[var(--color-accent)]/25 hover:opacity-95"
          >
            <Download className="h-4 w-4" />
            Скачать
          </Link>
        </div>
      </div>
    </header>
  );
}
