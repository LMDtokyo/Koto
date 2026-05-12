import {
  Smartphone,
  Monitor,
  Apple,
  Globe,
  ArrowDown,
  Lock,
} from "lucide-react";

type Platform = {
  icon: typeof Smartphone;
  title: string;
  status: string;
  description: string;
  primary?: { label: string; href: string };
  hint?: string;
};

const PLATFORMS: Platform[] = [
  {
    icon: Smartphone,
    title: "Android",
    status: "Бета",
    description: "APK для Android 8.0+ (arm64 / x86_64). Минимум 60 МБ.",
    primary: { label: "Скачать APK", href: "#" },
    hint: "Подпись релизных сборок проверяйте через SHA-256 ниже.",
  },
  {
    icon: Monitor,
    title: "Desktop",
    status: "Альфа",
    description: "Tauri-сборка для Linux, Windows и macOS.",
    primary: { label: "Скачать (скоро)", href: "#" },
  },
  {
    icon: Apple,
    title: "iOS",
    status: "В разработке",
    description: "Поддержка iPhone и iPad запланирована.",
  },
  {
    icon: Globe,
    title: "Web",
    status: "В разработке",
    description: "Веб-клиент с тем же протоколом E2EE.",
  },
];

export function DownloadPage() {
  return (
    <section className="mx-auto max-w-5xl px-6 py-16">
      <div className="flex items-center gap-2 text-sm text-[var(--color-text-mute)]">
        <ArrowDown className="h-4 w-4" />
        Загрузка
      </div>
      <h1 className="mt-2 text-4xl font-semibold tracking-tight md:text-5xl">
        Установите Koto
      </h1>
      <p className="mt-3 max-w-2xl text-[var(--color-text-dim)]">
        Скачайте клиент для своей платформы. Все сборки распространяются с
        открытыми контрольными суммами.
      </p>

      <div className="mt-10 grid gap-4 md:grid-cols-2">
        {PLATFORMS.map((p) => (
          <PlatformCard key={p.title} platform={p} />
        ))}
      </div>

      <div className="mt-10 rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface)]/60 p-6">
        <div className="flex items-center gap-2 text-sm font-medium">
          <Lock className="h-4 w-4 text-[var(--color-accent-2)]" />
          Контрольные суммы
        </div>
        <p className="mt-2 text-sm text-[var(--color-text-dim)]">
          Перед установкой сверьте SHA-256 загруженного файла с публикуемым ниже
          значением. Это защищает от подмены сборки на пути к вам.
        </p>
        <pre className="mt-4 overflow-x-auto rounded-lg bg-[var(--color-bg)] p-4 font-mono text-xs text-[var(--color-text-dim)]">
          {`koto-android-0.1.0.apk  sha256: <будет опубликовано с релизом>`}
        </pre>
      </div>
    </section>
  );
}

function PlatformCard({ platform }: { platform: Platform }) {
  const { icon: Icon, title, status, description, primary, hint } = platform;
  const isAvailable = !!primary;

  return (
    <div className="group relative overflow-hidden rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface)]/60 p-6 transition hover:border-[var(--color-accent)]/40">
      <div className="flex items-start justify-between gap-4">
        <div className="grid h-11 w-11 place-items-center rounded-xl bg-[var(--color-bg)] ring-1 ring-[var(--color-border)]">
          <Icon className="h-5 w-5 text-[var(--color-accent-2)]" />
        </div>
        <span
          className={[
            "rounded-full border px-2 py-0.5 text-xs",
            isAvailable
              ? "border-[var(--color-accent)]/40 bg-[var(--color-accent)]/10 text-[var(--color-text)]"
              : "border-[var(--color-border)] text-[var(--color-text-mute)]",
          ].join(" ")}
        >
          {status}
        </span>
      </div>
      <div className="mt-4 text-lg font-medium">{title}</div>
      <p className="mt-1 text-sm text-[var(--color-text-dim)]">{description}</p>

      {primary ? (
        <a
          href={primary.href}
          className="mt-5 inline-flex items-center gap-2 rounded-xl bg-gradient-to-br from-[var(--color-accent)] to-[var(--color-accent-2)] px-4 py-2 text-sm font-medium text-white shadow-lg shadow-[var(--color-accent)]/20 hover:opacity-95"
        >
          {primary.label}
        </a>
      ) : (
        <div className="mt-5 inline-flex items-center gap-2 rounded-xl border border-dashed border-[var(--color-border)] px-4 py-2 text-sm text-[var(--color-text-mute)]">
          Скоро
        </div>
      )}

      {hint ? (
        <p className="mt-3 text-xs text-[var(--color-text-mute)]">{hint}</p>
      ) : null}
    </div>
  );
}
