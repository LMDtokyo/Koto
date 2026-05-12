import { Link } from "react-router";

export function Footer() {
  const year = new Date().getFullYear();
  return (
    <footer className="border-t border-[var(--color-border)]/60 bg-[var(--color-bg)]">
      <div className="mx-auto grid max-w-6xl gap-8 px-6 py-12 sm:grid-cols-2 md:grid-cols-4">
        <div>
          <div className="flex items-center gap-2 font-semibold">
            <span className="grid h-7 w-7 place-items-center rounded-md bg-gradient-to-br from-[var(--color-accent)] to-[var(--color-accent-2)] text-xs font-bold text-white">
              K
            </span>
            Koto
          </div>
          <p className="mt-3 text-sm text-[var(--color-text-mute)]">
            Приватный мессенджер с end-to-end шифрованием.
          </p>
        </div>

        <FooterCol
          title="Продукт"
          items={[
            { label: "Скачать", to: "/download" },
            { label: "Обновления", to: "/updates" },
          ]}
        />
        <FooterCol
          title="Разработчикам"
          items={[
            { label: "Документация", to: "/docs" },
            { label: "API", to: "/docs#api" },
          ]}
        />
        <FooterCol
          title="Связь"
          items={[
            { label: "GitHub", href: "https://github.com/" },
            { label: "Поддержка", href: "mailto:hello@koto.run" },
          ]}
        />
      </div>
      <div className="border-t border-[var(--color-border)]/60">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-4 text-xs text-[var(--color-text-mute)]">
          <span>© {year} Koto Messenger</span>
          <span>Сделано с заботой о приватности</span>
        </div>
      </div>
    </footer>
  );
}

type FooterItem =
  | { label: string; to: string; href?: never }
  | { label: string; href: string; to?: never };

function FooterCol({ title, items }: { title: string; items: FooterItem[] }) {
  return (
    <div>
      <div className="mb-3 text-xs font-medium tracking-wide text-[var(--color-text-mute)] uppercase">
        {title}
      </div>
      <ul className="space-y-2 text-sm">
        {items.map((item) =>
          "to" in item && item.to ? (
            <li key={item.label}>
              <Link
                to={item.to}
                className="text-[var(--color-text-dim)] hover:text-[var(--color-text)]"
              >
                {item.label}
              </Link>
            </li>
          ) : (
            <li key={item.label}>
              <a
                href={item.href}
                className="text-[var(--color-text-dim)] hover:text-[var(--color-text)]"
                target="_blank"
                rel="noreferrer noopener"
              >
                {item.label}
              </a>
            </li>
          ),
        )}
      </ul>
    </div>
  );
}
