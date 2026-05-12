import { Link } from "react-router";
import {
  Shield,
  Zap,
  Smartphone,
  Lock,
  ArrowRight,
  Sparkles,
} from "lucide-react";

const FEATURES = [
  {
    icon: Shield,
    title: "End-to-end шифрование",
    text: "Signal Protocol + PQXDH. Сервер хранит только зашифрованные блобы.",
  },
  {
    icon: Zap,
    title: "120fps на Android",
    text: "Кастомные анимации, отзывчивость <100ms, плавность как на iOS.",
  },
  {
    icon: Smartphone,
    title: "Multi-device",
    text: "Один аккаунт — все устройства. Сообщения доставляются мгновенно.",
  },
  {
    icon: Lock,
    title: "Анонимность по умолчанию",
    text: "Регистрация по ключу. Имя, аватар и био — опциональны.",
  },
];

export function HomePage() {
  return (
    <>
      <Hero />
      <Features />
      <Cta />
    </>
  );
}

function Hero() {
  return (
    <section className="relative overflow-hidden">
      <div className="koto-hero-glow pointer-events-none absolute inset-0" />
      <div className="relative mx-auto max-w-6xl px-6 py-24 md:py-32">
        <div className="inline-flex items-center gap-2 rounded-full border border-[var(--color-border)] bg-[var(--color-surface)]/60 px-3 py-1 text-xs text-[var(--color-text-dim)]">
          <Sparkles className="h-3.5 w-3.5 text-[var(--color-accent-2)]" />
          Приватность, которую видно
        </div>

        <h1 className="mt-6 max-w-3xl text-5xl leading-[1.05] font-semibold tracking-tight md:text-7xl">
          Мессенджер,
          <br />
          в котором сообщения{" "}
          <span className="bg-gradient-to-br from-[var(--color-accent)] to-[var(--color-accent-2)] bg-clip-text text-transparent">
            принадлежат вам
          </span>
          .
        </h1>

        <p className="mt-6 max-w-2xl text-lg text-[var(--color-text-dim)]">
          Koto — это end-to-end шифрование Signal Protocol, премиальный UI и
          плавность 120fps. Без рекламы, без трекинга, без компромиссов.
        </p>

        <div className="mt-8 flex flex-wrap items-center gap-3">
          <Link
            to="/download"
            className="inline-flex items-center gap-2 rounded-xl bg-gradient-to-br from-[var(--color-accent)] to-[var(--color-accent-2)] px-5 py-3 font-medium text-white shadow-xl shadow-[var(--color-accent)]/25 hover:opacity-95"
          >
            Скачать Koto
            <ArrowRight className="h-4 w-4" />
          </Link>
          <Link
            to="/docs"
            className="inline-flex items-center gap-2 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)]/40 px-5 py-3 font-medium text-[var(--color-text)] hover:bg-[var(--color-surface)]"
          >
            Документация
          </Link>
        </div>
      </div>
    </section>
  );
}

function Features() {
  return (
    <section className="border-t border-[var(--color-border)]/60 bg-[var(--color-bg-elev)]">
      <div className="mx-auto max-w-6xl px-6 py-20">
        <h2 className="text-3xl font-semibold tracking-tight md:text-4xl">
          Создан для тех, кто ценит детали
        </h2>
        <p className="mt-3 max-w-2xl text-[var(--color-text-dim)]">
          Каждая анимация, каждый кадр и каждый байт — продуманы так, чтобы
          мессенджер ощущался лучше, чем привычные.
        </p>

        <div className="mt-12 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {FEATURES.map(({ icon: Icon, title, text }) => (
            <div
              key={title}
              className="rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface)]/60 p-5 transition hover:border-[var(--color-accent)]/40 hover:bg-[var(--color-surface)]"
            >
              <div className="grid h-10 w-10 place-items-center rounded-xl bg-[var(--color-bg)] ring-1 ring-[var(--color-border)]">
                <Icon className="h-5 w-5 text-[var(--color-accent-2)]" />
              </div>
              <div className="mt-4 font-medium">{title}</div>
              <div className="mt-1.5 text-sm text-[var(--color-text-dim)]">
                {text}
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

function Cta() {
  return (
    <section className="border-t border-[var(--color-border)]/60">
      <div className="mx-auto max-w-6xl px-6 py-20">
        <div className="relative overflow-hidden rounded-3xl border border-[var(--color-border)] bg-[var(--color-surface)] p-10 md:p-14">
          <div
            aria-hidden
            className="pointer-events-none absolute -top-32 -right-32 h-72 w-72 rounded-full bg-gradient-to-br from-[var(--color-accent)] to-[var(--color-accent-2)] opacity-20 blur-3xl"
          />
          <h3 className="text-3xl font-semibold tracking-tight md:text-4xl">
            Готовы попробовать?
          </h3>
          <p className="mt-3 max-w-xl text-[var(--color-text-dim)]">
            Установите бета-сборку для Android, или ознакомьтесь с журналом
            обновлений и документацией.
          </p>
          <div className="mt-6 flex flex-wrap gap-3">
            <Link
              to="/download"
              className="inline-flex items-center gap-2 rounded-xl bg-gradient-to-br from-[var(--color-accent)] to-[var(--color-accent-2)] px-5 py-3 font-medium text-white shadow-lg shadow-[var(--color-accent)]/25 hover:opacity-95"
            >
              Скачать APK
              <ArrowRight className="h-4 w-4" />
            </Link>
            <Link
              to="/updates"
              className="inline-flex items-center gap-2 rounded-xl border border-[var(--color-border)] px-5 py-3 font-medium text-[var(--color-text)] hover:bg-[var(--color-bg)]"
            >
              Что нового
            </Link>
          </div>
        </div>
      </div>
    </section>
  );
}
