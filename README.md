# Mini Browser — Android

Минималистичный полноэкранный браузер для Android 7+.

## Возможности

- При первом запуске запрашивает стартовую страницу (по умолчанию Google)
- Сохраняет URL между запусками
- Полноэкранный режим (статус-бар и навигация скрыты)
- Свайп от края экрана временно показывает системные панели
- Постоянное уведомление в шторке:
  - **🔗 Изменить URL** — сменить стартовую страницу на лету
  - **✖ Kill** — полностью закрыть приложение

## Как собрать

### Android Studio (рекомендуется)

1. Открой Android Studio → **File → Open** → выбери папку `MiniBrowser`
2. Android Studio автоматически скачает Gradle и зависимости
3. Нажми **Build → Build Bundle(s) / APK(s) → Build APK(s)**
4. APK будет в `app/build/outputs/apk/debug/app-debug.apk`

### Командная строка (нужен Android SDK + Gradle 8.4)

```bash
cd MiniBrowser
gradle wrapper          # создаст gradlew
./gradlew assembleDebug
```

## Иконка

Иконка находится в `app/src/main/res/mipmap-*/ic_launcher.png`.
Для разных разрешений использована одна картинка — при желании замени на правильно масштабированные версии (mdpi 48px, hdpi 72px, xhdpi 96px, xxhdpi 144px, xxxhdpi 192px).

## Минимальная версия Android

`minSdk 24` (Android 7.0 Nougat) — работает на Android 7 и выше, включая Android 16.
