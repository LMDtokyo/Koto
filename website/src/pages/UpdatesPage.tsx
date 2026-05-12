import { Sparkles } from "lucide-react";

type Release = {
  version: string;
  date: string;
  tag: "beta" | "alpha" | "stable";
  highlights: string[];
};

const RELEASES: Release[] = [
  {
    version: "0.1.0",
    date: "2026-05-09",
    tag: "beta",
    highlights: [
      "Первая публичная сборка Android-клиента",
      "End-to-end шифрование Signal Protocol + PQXDH",
      "WebSocket-доставка сообщений в реальном времени",
      "Премиальный UI/UX с анимациями 120fps",
    ],
  },
  {
    version: "0.0.9",
    date: "2026-04-22",
    tag: "alpha",
    highlights: [
      "Обкатка X3DH key distribution",
      "Стабилизация шлюза и chat-сервиса",
    ],
  },
];

const TAG_STYLES: Record<Release["tag"], string> = {
  beta: "border-[var(--color-accent)]/40 bg-[var(--color-accent)]/10 text-[var(--color-text)]",
  alpha:
    "border-[var(--color-accent-2)]/40 bg-[var(--color-accent-2)]/10 text-[var(--color-text)]",
  stable:
    "border-[var(--color-success)]/40 bg-[var(--color-success)]/10 text-[var(--color-text)]",
};

export function UpdatesPage() {
  return (
    <section className="mx-auto max-w-3xl px-6 py-16">
      <div className="flex items-center gap-2 text-sm text-[var(--color-text-mute)]">
        <Sparkles className="h-4 w-4" />
        Обновления
      </div>
      <h1 className="mt-2 text-4xl font-semibold tracking-tight md:text-5xl">
        Журнал релизов
      </h1>
      <p className="mt-3 max-w-2xl text-[var(--color-text-dim)]">
        Что нового в каждом релизе. Подписывайтесь на обновления через GitHub
        Releases.
      </p>

      <ol className="mt-12 space-y-6">
        {RELEASES.map((r) => (
          <li
            key={r.version}
            className="rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface)]/60 p-6"
          >
            <div className="flex items-baseline gap-3">
              <h2 className="text-2xl font-semibold tracking-tight">
                v{r.version}
              </h2>
              <span
                className={`rounded-full border px-2 py-0.5 text-xs ${TAG_STYLES[r.tag]}`}
              >
                {r.tag}
              </span>
              <span className="ml-auto text-sm text-[var(--color-text-mute)]">
                {r.date}
              </span>
            </div>

            <ul className="mt-4 space-y-2 text-sm text-[var(--color-text-dim)]">
              {r.highlights.map((h) => (
                <li key={h} className="flex gap-2">
                  <span
                    aria-hidden
                    className="mt-2 inline-block h-1 w-1 flex-none rounded-full bg-[var(--color-accent-2)]"
                  />
                  <span>{h}</span>
                </li>
              ))}
            </ul>
          </li>
        ))}
      </ol>
    </section>
  );
}
