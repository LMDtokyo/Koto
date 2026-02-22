# Git Workflow

## Ветвление

```
main                    ← продакшен, всегда стабильный
├── develop             ← основная ветка разработки
│   ├── feat/auth       ← новая фича
│   ├── feat/messages   ← новая фича
│   ├── fix/ws-reconnect← баг-фикс
│   └── refactor/db     ← рефакторинг
└── release/1.0.0       ← подготовка релиза
```

### Правила

- **main** — только через PR из `develop` или `release/*`
- **develop** — только через PR из feature/fix веток
- Прямые пуши в `main` и `develop` **ЗАПРЕЩЕНЫ**
- Каждая задача = отдельная ветка

### Именование веток

```
feat/<service>/<short-description>    → feat/messages/cursor-pagination
fix/<service>/<short-description>     → fix/gateway/heartbeat-timeout
refactor/<service>/<short-description>→ refactor/auth/argon2-migration
test/<service>/<short-description>    → test/users/friend-request
chore/<description>                   → chore/update-dependencies
hotfix/<description>                  → hotfix/critical-auth-bypass
```

## Коммиты

### Формат (Conventional Commits)

```
<type>(<scope>): <описание>

[тело — опционально]

[footer — опционально]
```

### Типы

| Тип | Когда использовать |
|-----|-------------------|
| `feat` | Новая фича |
| `fix` | Баг-фикс |
| `refactor` | Рефакторинг (не меняет поведение) |
| `perf` | Оптимизация производительности |
| `test` | Добавление/исправление тестов |
| `docs` | Документация |
| `chore` | Обновление зависимостей, CI, конфиги |
| `style` | Форматирование (не CSS!) |

### Scope — имя сервиса или пакета

```
feat(auth): add TOTP 2FA support
fix(gateway): handle WebSocket reconnect on timeout
refactor(messages): switch to cursor-based pagination
perf(db): add index on messages.channel_id
test(guilds): add role permission tests
chore(deps): update axum to 0.8
docs(api): add OpenAPI spec for auth endpoints
```

### Правила

- Первая буква описания — строчная
- Без точки в конце
- Императив: "add", "fix", "remove" (не "added", "fixes")
- Максимум 72 символа в первой строке
- Тело — через пустую строку, объяснение ПОЧЕМУ

## Pull Requests

### Правила

- PR в `develop` — ревью минимум 1 человека
- PR в `main` — ревью минимум 2 человек
- CI должен пройти (lint, tests, build)
- Название PR = формат коммита: `feat(auth): add OAuth2 Google login`
- Описание: что сделано, почему, как тестировать

### Шаблон PR

```markdown
## Что сделано
- Краткое описание изменений

## Почему
- Причина / задача / проблема

## Как тестировать
- [ ] Шаги для проверки

## Скриншоты (если UI)
```

### Merge стратегия

- `develop` ← feature: **Squash merge** (чистая история)
- `main` ← develop/release: **Merge commit** (сохраняем историю)

## .gitignore

Обязательно игнорировать:
```
# Rust
target/

# Frontend
node_modules/
dist/

# Env
.env
.env.*
!.env.example

# Logs & OS
*.log
.DS_Store

# Tools
coverage/
.turbo/
```

## Теги и релизы

```
v1.0.0    — major (breaking changes)
v1.1.0    — minor (новые фичи)
v1.1.1    — patch (баг-фиксы)
```

Semantic Versioning (semver).
