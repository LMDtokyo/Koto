# forks/

Внешние проекты, которые мы форкаем как стартовую точку для платформенных
клиентов и компонентов Koto. Каждая поддиректория — отдельный fork
upstream-репозитория с сохранением их лицензии и copyright headers.

## Содержимое

### session/ — fork приватного мессенджера Session

| Подкаталог | Upstream | Стек | Лицензия |
|-----------|----------|------|----------|
| [`session/session-android/`](session/session-android/) | [session-foundation/session-android](https://github.com/session-foundation/session-android) | Kotlin/Java | GPL-3.0 |
| [`session/session-ios/`](session/session-ios/) | [session-foundation/session-ios](https://github.com/session-foundation/session-ios) | Swift + Objective-C legacy | GPL-3.0 |
| [`session/session-desktop/`](session/session-desktop/) | [session-foundation/session-desktop](https://github.com/session-foundation/session-desktop) | Electron + TypeScript | GPL-3.0 |
| [`session/libsession-util/`](session/libsession-util/) | [session-foundation/libsession-util](https://github.com/session-foundation/libsession-util) | C++ (общая библиотека) | GPL-3.0 |
| [`session/oxen-storage-server/`](session/oxen-storage-server/) | [oxen-io/oxen-storage-server](https://github.com/oxen-io/oxen-storage-server) | C++ (Service Node storage) | GPL-3.0 |

Все клонированы как **shallow** (`--depth=1`), чтобы не тащить полную git-историю
(~1.5M коммитов суммарно, экономит ~5 ГБ диска). Если понадобится полная
история — `git fetch --unshallow` в конкретной поддиректории.

## Что мы делаем с этими форками

**План адаптации (см. [KOTO_PROTOCOL.md](../KOTO_PROTOCOL.md)):**

1. **Удалить Lokinet/onion routing** — заменить на наш REST/WebSocket gateway. Это означает удалить:
   - `OnionRequestAPI` / `SnodeAPI` / `PathBuilder` / `Swarm`
   - `Storage Server protocol` (заменим на наш `/v1/conversations/...`)
   - Зависимости от `oxen-storage-server` целиком — он нам не нужен (есть свой `services/chat`)

2. **Заменить Session ID на Koto ID** — Session ID имеет префикс `05` (Loki blockchain). У нас identity-public-key без префикса.

3. **Перенаправить network на naш Koto-server** — все REST-вызовы должны идти на `koto.run` API, а не на Service Node swarm.

4. **Убрать Loki/Oxen blockchain интеграции** — ссылки на on-chain registration, fee burning, и т.п.

5. **Обновить branding** — Session → Koto, иконки, splash, copy.

6. **Перезаписать crypto** — Session использует свой fork Signal-Protocol. Нам нужно либо:
   - Адаптировать Session-крипто к работе с нашим X3DH+PQXDH bundle-форматом, ИЛИ
   - Заменить целиком на наш `crypto/` Rust-крейт через FFI/JNI.

Это **большой рефакторинг** на каждой платформе (iOS/Android/Desktop). Реальные оценки:
- Android: 2–3 месяца full-time
- iOS: 2–3 месяца full-time (legacy ObjC + Swift mix)
- Desktop: 1–2 месяца (Electron, проще структура)

## Юридическая сторона (GPL-3.0)

Session **под GPL-3.0**. Это значит:

- ✅ Можно форкать, модифицировать, публиковать.
- ✅ Можно интегрировать в Koto.
- ⚠️ **Любой код, тесно интегрированный с этими форками, тоже становится GPL-3.0.** Это значит наш Tauri-клиент или будущий iOS-клиент **должны быть опубликованы под GPL-3.0** (если используют форкнутый код).
- ⚠️ **Сохранить все copyright headers** в файлах. Нельзя удалять `Copyright (c) 2018-2026 Session Foundation`.
- ⚠️ **Сохранить LICENSE-файл** в каждой поддиректории (он там уже есть — не трогать).
- ⚠️ **Опубликовать source code форков** при дистрибуции. Если мы выпускаем APK/IPA на основе форка — обязаны выложить source.

**Важно**: GPL-3.0 на форкнутых клиентах **не делает GPL-3.0 наш Go-сервер и Rust-крейт**, потому что они не «derived works» — связь через сетевой протокол, а не через linking. Это стандартный паттерн «GPL client + Apache server» (тот же, что у Matrix: Element AGPL, но Synapse/Dendrite Apache).

Однако если мы хотим избежать GPL-заразы — лучше **не интегрировать Session-код в свой Tauri-клиент**, а оставить эти форки изолированными платформенными приложениями.

## Альтернативы, которые стоит рассмотреть параллельно

Перед тем как погружаться в форк Session, посмотри также:

- [`element-x-ios`](https://github.com/element-hq/element-x-ios) — iOS-клиент Matrix, **Apache-2.0** (без заразы). Современный SwiftUI, использует matrix-rust-sdk.
- [`mattermost-mobile`](https://github.com/mattermost/mattermost-mobile) — Apache-2.0, React Native, кросс-платформа.
- **Compose Multiplatform** для iOS из нашего существующего Android-кода (см. [MVP_CHECKLIST.md](../MVP_CHECKLIST.md)).

Эти варианты дают нам Apache-2.0 / MIT лицензии без цепной реакции GPL.

## Как обновлять upstream

Каждые 1–2 месяца:

```bash
cd forks/session/session-android && git fetch --depth=1 origin && git reset --hard origin/master
# (или master/main, проверь branch у каждого)
```

Шallow-clone каждый раз заменяет содержимое — это нормально, мы храним свои
изменения **отдельно** в нашем форке (он будет жить в отдельных ветках или
вообще отдельной директорией `koto-android/`, унаследованной от Session).

## Не коммитить пока

Эти 5 репозиториев занимают **~225 МБ**, и каждый — со своим `.git`. Если
коммитить как submodules — нужно делать `git submodule add` правильно. Если
коммитить как обычные файлы — `.git` нужно убрать, и все 225 МБ попадут в наш
основной репо.

**Текущая рекомендация**: добавить `forks/` в `.gitignore` корневого Koto-репо
(чтобы не раздувать наш repo), а с этими клонами работать локально. Когда
будем готовы превратить один из них в полноценный Koto-клиент — выделим в
отдельный репозиторий `koto-android/`, `koto-ios/`, `koto-desktop-electron/`.

См. [`../.gitignore`](../.gitignore) — туда добавится `forks/` после твоего
ОК.
