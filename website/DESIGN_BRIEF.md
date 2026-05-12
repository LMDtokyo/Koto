# Koto Website — Промпты для Claude Designer

> Каждый блок ниже — один **готовый к копипасту** промпт для Claude Designer.
> Порядок: сначала **Prompt 0** (загрузить как context — система/бренд),
> потом по очереди **Prompt 1…N** для каждого экрана.
>
> Кладёшь Prompt 0 в `Pasted text` (контекст), а каждый следующий — в `Describe what you want to create`.

---

## Prompt 0 — Контекст и дизайн-система (вставь один раз как context)

```
Project: Koto — приватный мессенджер с E2E шифрованием (Signal + Kyber). Нет телефонов, нет рекламы, нет телеметрии. Идентичность — 12-словная BIP39 фраза. Open source. Аудитория: люди, которые осознанно выбирают приватность.

Сайт — статическая визитка: главная, скачать, о продукте, новости, документация. Никакого web-чата.

Brand:
- Name: Koto. Mark: лисичка-маскот (cute fox), как в Discord/Linear-духе.
- Accent: #5865F2 (Koto-blurple), gradient #7289DA → #4752C4.
- Воронка ценности в одну фразу: «Сообщения, которые читаете только вы и собеседник».

Design language (humanist tech, 2026):
- Тёмная тема по умолчанию. Background #0a0b0e, surface #14151a, elevated #1c1d24, line rgba(255,255,255,0.08).
- Текст: #f5f6f8 / #a1a4ad / #6c6f78.
- Big bold display-typography: hero 72–96px (Inter Display / Mona Sans), fluid clamp().
- Asymmetric organic layouts. НЕ жёсткая 12-column сетка везде.
- Glassmorphism только на nav и CTA-карточках (backdrop-filter blur 14–20px).
- Lucide icons, stroke 1.75, hairline. Те же иконки, что в десктоп-клиенте — единая система.
- Радиусы: 8 / 14 / 22 / 32px.
- Motion: скромно. Hover scale 1.02 + glow. Transitions 200–240ms cubic-bezier(0.22,1,0.36,1). Никаких параллаксов и splash-screens.

Tone of voice: спокойный, технически уверенный, без маркетингового жаргона. Не «революционный», не «безупречный». Лучше «открытый код на GitHub», «12 слов вместо номера», «без рекламы и без планов её добавить». Русский — основной язык, английский — вторичный.

Жёсткие НЕТ:
- Generic «laptop mockup на градиенте + кнопка Download».
- Bento-grid из 12 одинаковых карточек.
- «Trusted by Forbes / TechCrunch».
- Live-chat виджет «Hi, can I help you?».
- Stock-фото людей с MacBook'ами.
- Cookie-баннер с 47 чекбоксами.

Референсы по уровню вкуса: Linear, Vercel, Arc, Resend, Threema, Signal — но БЕЗ слепого копирования. Узнаваемая идентичность Koto, не клон.

Каждый экран должен:
- Работать в dark + light mode.
- Иметь читаемую иерархию: одно главное действие на экран.
- Уважать prefers-reduced-motion.
- Быть accessible (контраст ≥ 4.5:1, focus-states).
```

---

## Prompt 1 — Home / Hero

```
Hero первой страницы koto.run. Полноэкранная секция (на 1440×900 не должно быть скролла, чтобы понять, что это).

Композиция:
- Слева — большой H1 в 2–3 строки: «Сообщения, которые читаете только вы и собеседник.» Bold display-фонт 84px, fluid. Под ним — saber-line (короткая строка subhead) 18–20px secondary-color: «Сквозное шифрование. Без телефонов, без рекламы, без следов».
- Под текстом — primary CTA «Скачать Koto» (accent fill, glow при hover) + secondary ghost «Открытый код» с GitHub-иконкой.
- Справа — НЕ скриншот приложения, а абстрактная визуализация: парящий 3D-объект из мягких полупрозрачных карточек/панелей, имитирующих интерфейс мессенджера, но без читаемого текста. Парят с лёгким drift-motion (3–4 сек цикл). Подсвечены blurple-gradient'ом снизу.
- Сверху — sticky nav (glass): mark + название слева, ссылки по центру (Скачать, О Koto, Новости, Документация), GitHub-icon справа.

Background: тёмный #0a0b0e с очень тонким noise-grain (3% opacity) и одним гигантским мягким радиальным glow в нижнем-правом углу (Koto-blurple, opacity 12%).

Никаких больших цветных блоков. Всё дышит. Тон: уверенный, тихий, дорогой.
```

---

## Prompt 2 — Home / Value props (3 секции after hero)

```
Три секции value-props под hero. Каждая занимает full-width, разделена 120–160px вертикального воздуха.

Секция 1 — «Только вы и собеседник»:
- Большой H2 слева (48px), 2-строчный объяснительный параграф справа.
- Под ними — широкая карточка (border 1px line, radius 22px, surface bg) с упрощённой схемой: два аватара по краям, между ними «зашифрованная капсула» с lock-иконкой. Анимация: бегущие dot'ы между двумя точками, имитирующие пакет.
- Фон секции: тёмный, с тонкой decorative grid-линией внизу (line color, 1px, opacity 30%).

Секция 2 — «12 слов вместо номера»:
- Перевернуть композицию: H2 справа, изображение слева.
- Изображение: 12 карточек-слов BIP39 в неровной сетке 4×3 (как магниты на холодильнике, чуть наклонённые), с реальными словами «velvet», «orbit», «paper», ... Одна карточка подсвечена accent-glow.
- Под ней — мелкий шрифт «Эта фраза — единственный способ восстановить аккаунт. Сервер её никогда не видел.»

Секция 3 — «Открытый код, открытый протокол»:
- H2 по центру, под ним 3 чипа с лого: Signal Protocol, Kyber-1024 (PQ), libsignal Rust. Чипы — ghost style, glassmorphism.
- Большая ссылка-CTA «Изучить на GitHub →» с hover-glow.

Без иконок-эмодзи. Никаких feature-карточек 3-в-ряд из bento.
```

---

## Prompt 3 — Home / Социальное доказательство + footer-CTA

```
Двухчастная нижняя секция Home:

Часть A — «Не верь нам на слово»:
- H2 «Проверьте сами», под ним 2 живые цитаты из security audits / reviews. НЕ выдумывать имена компаний; использовать общие источники: «Security review by independent auditor», «Open source contributors say...».
- Цитаты в широких card'ах (50/50 layout), surface bg, цитата-кавычка большая accent-color сверху.

Часть B — финальный CTA:
- Полноширинная card с soft-glow и градиентом по диагонали (accent → background).
- Внутри: H1 «Готовы попробовать?», под ним строка «Десктоп · Android · iOS (скоро)».
- Большая primary-кнопка «Скачать Koto».
- Под кнопкой мелким — три пиктограммы платформ (Linux, macOS, Windows) с проверкой текущей ОС → её иконка ярче.

Footer:
- Тёмный, минимум контента: логотип-mark, копирайт, строка ссылок (Документация · Новости · GitHub · Безопасность · Контакты).
- Без social-icons-rainbow, без newsletter-signup'а.
- Высота ~140px.
```

---

## Prompt 4 — Download page

```
Страница /download. ОДНА цель — пользователь скачивает клиент за 5 секунд.

Composition:
- Hero компактнее, чем на главной: H1 «Скачать Koto», sub «Один клиент на все ваши устройства. Все версии — open source.»
- Сразу под — большая «smart download» карточка: автоматически определяет ОС (Linux/macOS/Windows) по navigator.userAgent. Текущая ОС — primary CTA с большой иконкой и текстом «Скачать для macOS · 24 МБ». Остальные ОС — три ghost-карточки в строку под ней.
- Каждая ghost-карточка: иконка ОС, имя, размер, версия (v0.1.0), маленький monospace SHA256-checksum (truncate до 8 символов с tooltip-полным).
- Между Linux/Mac/Win и mobile — горизонтальный divider с подписью «Мобильные».
- Mobile карточки (Android, iOS): Android — primary, iOS — disabled с label «Скоро». QR-коды справа на каждой.

Below the fold:
- Section «Что внутри сборки» — 3 буллета: «Подписан Ed25519», «Воспроизводимая сборка», «Без телеметрии и автообновлений без вашего согласия».
- Section «Альтернативные источники»: F-Droid, Flathub, Homebrew, AUR — ghost-чипы с command-snippets для геков (`brew install koto`).
- Section «Старые версии» — collapsed details с табличкой релизов.

Тон страницы — деловой, тихий. Никаких «БЕЗОПАСНО! ПРИВАТНО! 100%!». Используй тонкий иконографический язык lucide.
```

---

## Prompt 5 — About page

```
Страница /about. Не «О компании» с фотографиями команды, а **манифест продукта**.

Layout — длинная одноколоночная страница (max-width 720px по центру), как editorial-статья. Никаких 3-column feature blocks.

Структура:
1. **Заголовок**: «Что такое Koto» — H1 96px, fluid.
2. **Lead-параграф** (24px, accent-soft color): 3–4 предложения, что мы строим и для кого. Без слов «революционный».
3. **Discount-block** «Чего у нас нет» — большой list с pull-quote-style: «нет рекламы», «нет аналитики», «нет привязки к телефону», «нет AI-обучения на ваших переписках». Каждый пункт — отдельная строка, font-weight 400, 28px, line-height 1.4.
4. **Раздел «Как устроена приватность»** — H2, под ним 3 sub-секции:
   - «Сквозное шифрование» с одной technical-схемой (X3DH+PQXDH), без кода.
   - «Без идентификаторов» — про seed-фразу.
   - «Без серверной слежки» — про что хранит сервер (только зашифрованные блобы).
   В каждой — 1 диаграмма уровня «коробочка → стрелка → коробочка», абстрактная, accent-line.
5. **Раздел «Команда и финансирование»** — H2, под ним абзац: open-source, без VC, без donations-вытягивания. Если есть Patreon/OpenCollective — ссылка одной строкой.
6. **Финальный CTA**: «Скачать Koto» — кнопка по центру, под ней строка «или прочитайте документацию →».

Типографика — максимально editorial, как Stripe Press или Vercel essays. Большие отступы между блоками (96–128px), generous line-height. Тонкие decorative-линии между разделами.
```

---

## Prompt 6 — News index

```
Страница /news — лента релизов и постов. Не блог-стартапа с фотками, а **changelog meets editorial**.

Layout: max-width 880px по центру.

Top:
- H1 «Новости» (96px), под ним sub «Релизы, безопасность, архитектура».
- Под ним — горизонтальный фильтр-rail: «Всё · Релизы · Безопасность · Под капотом» (pill-tabs, активная — accent fill).

Список постов:
- Каждый пост — большая card во всю ширину, padding 28px, border-radius 22px, hover slightly elevates surface bg.
- Внутри card: дата (mono, secondary), tag-pill (Релиз / Безопасность / Под капотом), H2 заголовок (32px, можно две строки), 1–2 предложения lead, ссылка «Читать →».
- Чередование plain card и highlighted card (с soft accent-glow по краю) — для визуального ритма.
- Если есть свежий security-advisory — sticky banner вверху над списком: warning-color border, текст «Обновитесь до 0.1.2 — устранена уязвимость в …», ссылка «Подробнее».

Pagination — простая «Older →» снизу, без infinite scroll.

Никаких авторских аватарок, дат вида «15 минут назад», emoji-reactions.
```

---

## Prompt 7 — News article (single post)

```
Страница /news/[slug] — одна статья.

Header:
- Breadcrumb «Новости / Релизы» — мелкий tertiary шрифт.
- H1 заголовок (64–80px, bold, fluid).
- Meta-row: дата · автор (если есть) · время чтения · tag-pill.

Body:
- Markdown-article max-width 720px по центру.
- Типографика: 18px body, line-height 1.7, subheadings 32/24px, code-blocks с syntax-highlight на тёмной поверхности (#0a0b0e, accent на keywords).
- Inline-code: фон elevated, padding 2×6, mono.
- Изображения: max-width 100%, radius 14, тонкая 1px line-граница.
- Cite-блок (для security advisories): warning-color left-border 3px, fill warning-tinted background.
- Внизу — divider, потом блок «Связанные посты» (3 карточки в ряд, упрощённые).
- В конце — большой anchor «Помог пост — поддержите проект» со ссылкой на GitHub stars/Patreon. БЕЗ навязчивости.

Sticky на десктопе слева — table-of-contents (auto-generated из h2/h3), активная подсвечивается scroll-spy'ем. Mobile — без TOC.

Без author-bio-блоков с социалками и крупных фото-портретов.
```

---

## Prompt 8 — Docs index

```
Страница /docs — документация для пользователей и контрибьюторов.

Three-pane layout (desktop):
1. **Sidebar (260px)** — sticky слева. Структура: «Начало работы», «Концепции», «Платформы» (под-аккордеон Desktop/Android/iOS), «Безопасность», «Для разработчиков». Активная — accent-fill subtle, без stripe.
2. **Main column** — список карточек категорий или markdown-контент (если открыт раздел).
3. **TOC (220px)** — sticky справа, table of contents текущей статьи.

Top шапки:
- Над всеми панелями — search-bar (kbd-стиль, ⌘K), полнотекстовый поиск по docs (Fuse.js).
- В sidebar — small switcher версии (v0.1.0 / latest).

Index-mode (когда нет выбранной статьи) — показываем 6 категорий как карточки 2×3, каждая с lucide-иконкой, заголовком, sub-text «4 статьи».

Стиль docs — справочник, не маркетинг. Никаких скевоморфных «book pages», никаких 3D-стрелок.
```

---

## Prompt 9 — Docs article

```
Страница /docs/[slug] — одна статья документации.

Layout — тот же 3-pane, что в Prompt 8 (sidebar + main + toc).

Main column (max-width 760px):
- Breadcrumb сверху.
- H1 (48px), sub-line «Last updated · 5 min read».
- Markdown body, такой же типографики, как в news article, но плотнее: 17px body, line-height 1.65.
- Code-блоки с copy-button в правом верхнем углу при hover.
- Callout-блоки 4 типов: «info» (accent-tint), «warn» (warning), «danger» (error), «tip» (success). Каждый — left-border 3px + tinted fill.
- Inline anchor-links на h2/h3 (появляются # при hover на заголовок).
- В конце статьи — feedback-row: «Эта страница помогла?» — 👍 / 👎 (mailto: или GitHub-issue link). Только когда нажали 👎 — расширяется textarea «что не так».

Footer статьи: «Edit on GitHub →» link. Никакой комментариев под статьями.
```

---

## Prompt 10 — Navigation + Footer (design system)

```
Универсальные nav и footer, переиспользуются на всех страницах.

Nav (sticky top, 64px height):
- Glass-effect: backdrop-filter blur(20px), background rgba(10,11,14,0.7).
- Тонкая 1px line-граница снизу.
- Слева: Koto-mark (32px) + название «Koto» 17px bold.
- Центр: ссылки 14px, gap 32px — Скачать, О Koto, Новости, Документация. Активная — accent-fill underline.
- Справа: 2 ghost-icon-button — search (⌘K opens overlay), GitHub. Mobile — burger.
- При scroll вниз — nav сжимается до 56px, граница более выраженная.

Footer:
- Полностью полная ширина, dark surface bg, padding 80px вертикально.
- Layout: 4 колонки на desktop (mark+slogan / Продукт / Сообщество / Юридическое), на mobile — стек.
- Под колонками — divider, под ним: copyright «© 2026 Koto» слева, версия сайта мелким моно справа, по центру — set из 3 ghost-link'ов (status.koto.run, security.txt, transparency).
- Никаких social-icons rainbow. GitHub — единственная иконка, и она уже в nav.

Тон: тихий, business-class, как footer Linear или Vercel.
```

---

## Prompt 11 — 404 page

```
Страница 404 — фирменная, не дефолтная.

Полноэкранная композиция, по центру:
- Большой H1 «Здесь ничего нет» (96px, bold), под ним sub-line «Эта страница исчезла, как и положено сообщениям» (18px secondary).
- Под текстом — иллюстрация: лисичка-маскот Koto с грустной мордочкой, рядом «empty bubble» (пустое сообщение) с пульсирующей точкой. Иллюстрация плоская, vector, в брендовом стиле.
- Под иллюстрацией — 2 кнопки в строку: «На главную» (primary) и «Документация» (ghost).
- Внизу мелким — «Если вы думаете, что страница должна была здесь быть — напишите нам в issue».

Без больших цветных блоков. Тёмный фон, акцент только в иллюстрации.
Mobile: иллюстрация уменьшается до 240px, остальное стекается.
```

---

## Как пользоваться

1. **Загрузи Prompt 0 как context** (вставь в `Pasted text` или прикрепи как `Design System` через кнопку «+»).
2. **Потом по одному Prompt 1…11** в `Describe what you want to create` — Claude Designer выдаёт hi-fi makeup для каждого экрана.
3. **Итерации:** если результат «дешёвый», добавляй в промпт строку «Пере-сделай в стиле Linear, без bento-grid и без ярких градиентов».
4. **Финальная стадия:** когда есть mockups — попроси Claude Designer экспортировать их как React + Tailwind компоненты и переноси в `website/src/`.

Не пиши промпты типа «сделай красиво». Пиши композицию, иерархию, что НЕ должно быть. Это даёт результат уровня Linear, а не уровня Wix.
