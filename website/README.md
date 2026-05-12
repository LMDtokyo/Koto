# Koto Website

Публичный сайт мессенджера Koto: лендинг, страница загрузки, документация и
журнал обновлений.

## Стек

- Vite 6 + React 19 + TypeScript 5.7
- React Router v7 (declarative mode)
- Tailwind CSS v4 (через `@tailwindcss/vite`)
- lucide-react — иконки

## Запуск

```bash
cd website
npm install
npm run dev      # http://localhost:5180
npm run build    # production-сборка в dist/
npm run preview  # локальный просмотр сборки
npm run typecheck
```

## Структура

```
website/
├── public/                # статические ассеты, отдаются как есть
├── src/
│   ├── components/        # переиспользуемые компоненты (Navbar, Footer, layout)
│   ├── pages/             # страницы маршрутов
│   ├── lib/               # утилиты
│   ├── App.tsx            # роуты
│   ├── main.tsx           # точка входа, BrowserRouter
│   └── index.css          # Tailwind + глобальные токены темы
├── index.html
├── tsconfig*.json
├── vite.config.ts
└── package.json
```

## Маршруты

| Путь         | Назначение                                  |
| ------------ | ------------------------------------------- |
| `/`          | Лендинг с описанием продукта                |
| `/download`  | Страница загрузки клиентов и контрольных сумм |
| `/docs`      | Документация: установка, протокол, API      |
| `/updates`   | Журнал релизов / changelog                  |

## Соглашения

- Тема и токены цветов задаются через CSS-переменные в `src/index.css`
  (блок `@theme`). Это даёт Tailwind v4 утилиты вида
  `text-[var(--color-text)]`, не привязывая дизайн к жёстким палитрам.
- Компоненты страниц — именованные экспорты, layout с `<Outlet />` живёт
  в `components/SiteLayout.tsx`.
- Иконки — только `lucide-react`, чтобы не плодить графические зависимости.
