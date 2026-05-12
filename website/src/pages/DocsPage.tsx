import { useState } from "react";
import { BookOpen, ChevronRight } from "lucide-react";

type Section = {
  id: string;
  title: string;
  body: string;
};

const SECTIONS: Section[] = [
  {
    id: "intro",
    title: "Начало",
    body: "Koto — это приватный мессенджер с end-to-end шифрованием. Документация описывает архитектуру клиента, протокол и API шлюза.",
  },
  {
    id: "install",
    title: "Установка",
    body: "Скачайте APK с раздела «Скачать», либо соберите проект из исходников: ./gradlew assembleDebug в директории android/.",
  },
  {
    id: "crypto",
    title: "Шифрование",
    body: "Используется Signal Protocol с PQXDH. Сервер видит только ciphertext. Ключи генерируются и хранятся на устройстве.",
  },
  {
    id: "api",
    title: "API",
    body: "Шлюз предоставляет REST на :8080 и WebSocket на :9080. Авторизация по JWT (Ed25519). Подробности — в репозитории services/gateway.",
  },
  {
    id: "selfhost",
    title: "Self-hosting",
    body: "Полный стек собирается через docker-compose.yml. Команда make stack поднимает 7 микросервисов и инфраструктуру.",
  },
];

export function DocsPage() {
  const [activeId, setActiveId] = useState(SECTIONS[0]!.id);

  return (
    <section className="mx-auto max-w-6xl px-6 py-16">
      <div className="flex items-center gap-2 text-sm text-[var(--color-text-mute)]">
        <BookOpen className="h-4 w-4" />
        Документация
      </div>
      <h1 className="mt-2 text-4xl font-semibold tracking-tight md:text-5xl">
        Как устроен Koto
      </h1>
      <p className="mt-3 max-w-2xl text-[var(--color-text-dim)]">
        Краткий обзор архитектуры, протокола и API. Полная документация
        дополняется по мере выхода обновлений.
      </p>

      <div className="mt-10 grid gap-8 md:grid-cols-[220px_1fr]">
        <aside className="md:sticky md:top-20 md:self-start">
          <nav className="flex flex-col gap-1">
            {SECTIONS.map((s) => (
              <button
                key={s.id}
                type="button"
                onClick={() => {
                  setActiveId(s.id);
                  document
                    .getElementById(s.id)
                    ?.scrollIntoView({ behavior: "smooth", block: "start" });
                }}
                className={[
                  "inline-flex items-center justify-between rounded-lg px-3 py-2 text-left text-sm transition-colors",
                  activeId === s.id
                    ? "bg-[var(--color-surface)] text-[var(--color-text)]"
                    : "text-[var(--color-text-dim)] hover:bg-[var(--color-surface)]/60 hover:text-[var(--color-text)]",
                ].join(" ")}
              >
                {s.title}
                <ChevronRight className="h-4 w-4 opacity-60" />
              </button>
            ))}
          </nav>
        </aside>

        <div className="space-y-6">
          {SECTIONS.map((s) => (
            <article
              key={s.id}
              id={s.id}
              className="scroll-mt-24 rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface)]/60 p-6"
            >
              <h2 className="text-xl font-semibold tracking-tight">
                {s.title}
              </h2>
              <p className="mt-2 text-[var(--color-text-dim)]">{s.body}</p>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}
