<p align="center">
  <img src="docs/screenshots/todi-logo.png" width="96" height="96" alt="Todi">
</p>

<h1 align="center">Todi</h1>

<p align="center">
  Список дел по датам, который всегда виден на экране блокировки.
</p>

<p align="center">
  <a href="https://github.com/veterAN911/todi/actions/workflows/ci.yml"><img src="https://github.com/veterAN911/todi/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-blue.svg" alt="MIT"></a>
  <img src="https://img.shields.io/badge/platform-Android%2026%2B-3DDC84" alt="Android 26+">
  <img src="https://img.shields.io/badge/Kotlin-2.2-7F52FF" alt="Kotlin 2.2">
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4" alt="Jetpack Compose">
  <img src="https://img.shields.io/badge/version-1.0.1-informational" alt="1.0.1">
</p>

<p align="center">
  <a href="README.en.md">English</a>
</p>

<p align="center">
  <a href="https://groups.google.com/g/-todi">Группа тестировщиков</a> ·
  <a href="https://play.google.com/store/apps/details?id=com.todi.appp">Google Play</a> ·
  <a href="https://github.com/veterAN911/todi/releases/latest">APK</a>
</p>

## О приложении

Todi — локальный планировщик для Android. Дела живут на своих датах, а главная
особенность — постоянное системное уведомление со списком активных дел на сегодня.
Оно висит в шторке и на экране блокировки, обновляется само и не требует
открывать приложение, чтобы вспомнить, что запланировано.

Приложение не требует аккаунта, не содержит рекламы и аналитики, а содержимое
дел не покидает телефон. Исключение — необязательная резервная копия на сервер,
который выбирает сам владелец сборки.

## Скачать

**Google Play, закрытое тестирование.** Приложение доступно в закрытом
тестировании, поэтому сначала нужно попасть в список тестировщиков:

1. Вступите в группу: [groups.google.com/g/-todi](https://groups.google.com/g/-todi)
   (вступить может каждый) или по адресу `-todi@googlegroups.com`.
2. После вступления откройте страницу приложения в Google Play:
   [play.google.com/store/apps/details?id=com.todi.appp](https://play.google.com/store/apps/details?id=com.todi.appp).

Ссылка в Google Play работает только для участников группы: без вступления
магазин покажет, что приложение недоступно.

**APK напрямую.** Подписанный APK приложен к
[Releases](https://github.com/veterAN911/todi/releases/latest). Скачайте файл
`todi-1.0.1.apk` и установите его на телефон (Android 8.0 и новее). При
установке из файла потребуется разрешить установку из этого источника.

## Возможности

- **Сегодня, Завтра и Календарь** — один и тот же список дня, сетка месяца на
  шесть недель, точки по дням с делами.
- **Дела без даты** — отдельный список для того, что не привязано к дню и не
  должно переноситься.
- **Автоперенос** — незакрытые дела прошлого дня утром переезжают на сегодня и
  помечаются красным «с 29 авг.»; исходная дата сохраняется.
- **Уведомление на экране блокировки** — список на сегодня, цвета разделов,
  настраиваемый состав: только сегодня, сегодня и просроченные, плюс дела без
  даты. Отдельный режим «только счётчик» не показывает названия даже в развёрнутой
  карточке.
- **Цвета разделов** — палитра из 16 оттенков, каждый проверен на контраст и на
  светлом, и на тёмном фоне уведомления; можно выбрать свой цвет.
- **Напоминание о делах без даты** — отдельное уведомление с настраиваемым
  интервалом и часом тишины.
- **Голосовой ввод** — удержание кнопки «+» и протяжка вверх; распознавание идёт
  офлайн на телефоне через Vosk, без отправки звука в сеть. Надиктованная фраза
  разбирается на дела, даты и время.
- **Поиск** по названию и заметке сразу по всем делам, с подсветкой совпадений.
- **Архив** выполненных дел по дате закрытия, список или сетка месяца, возврат
  дела в работу на выбранный день.
- **Календарь телефона** — необязательная выгрузка дел с датой в основной
  календарь устройства.
- **Резервная копия** — необязательная отправка снапшота на собственный сервер,
  не чаще раза в 12 часов и по кнопке; сервер хранит 10 последних копий.
- **Два языка** — русский и английский, светлая и тёмная темы.
- **Нижняя панель на выбор** — на телефоне и планшете показываются только те
  разделы, которые выбраны в настройках; все разделы всегда доступны из меню.

## Скриншоты

<table>
  <tr>
    <td><img src="docs/screenshots/1-segodnya.png" width="220" alt="Сегодня"></td>
    <td><img src="docs/screenshots/2-lock-screen.png" width="220" alt="Экран блокировки"></td>
    <td><img src="docs/screenshots/3-notification.png" width="220" alt="Уведомление"></td>
    <td><img src="docs/screenshots/4-kalendar.png" width="220" alt="Календарь"></td>
  </tr>
  <tr>
    <td align="center">Сегодня</td>
    <td align="center">Экран блокировки</td>
    <td align="center">Шторка</td>
    <td align="center">Календарь</td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/5-bez-daty.png" width="220" alt="Без даты"></td>
    <td><img src="docs/screenshots/6-arhiv.png" width="220" alt="Архив"></td>
    <td><img src="docs/screenshots/7-poisk.png" width="220" alt="Поиск"></td>
    <td><img src="docs/screenshots/8-tsveta.png" width="220" alt="Цвета"></td>
  </tr>
  <tr>
    <td align="center">Без даты</td>
    <td align="center">Архив</td>
    <td align="center">Поиск</td>
    <td align="center">Цвета разделов</td>
  </tr>
</table>

## Стек

| Слой | Технологии |
|---|---|
| Язык и UI | Kotlin 2.2, Jetpack Compose, Material 3 |
| Архитектура | MVVM, Clean Architecture, Coroutines и Flow |
| Хранение | Room, DataStore Preferences |
| DI | Hilt |
| Фон | WorkManager |
| Сеть | OkHttp |
| Голос | Vosk Android, офлайн-модель русского языка |
| Календарь | CalendarContract (системный провайдер) |
| Сборка | AGP 9.2, Gradle 9.4.1, KSP, R8 |

Минимальная версия — Android 8.0 (API 26), целевая — Android 16 (API 36).

## Сборка

Нужны JDK 21 и Android SDK с платформой `android-36.1`, build-tools 36.x и
platform-tools. Gradle и Kotlin устанавливать не нужно: первый скачает wrapper,
второй встроен в AGP 9.

```bash
git clone https://github.com/<owner>/todi.git
cd todi
./gradlew :app:assembleDebug
```

Если SDK не находится автоматически, создайте `local.properties`:

```properties
sdk.dir=/path/to/Android/sdk
```

Основные команды:

```bash
make doctor                  # проверить окружение
make test                    # unit-тесты
make lint                    # Android lint
make ktlint                  # стиль Kotlin
make verify                  # test + lint + ktlint
make build-debug             # debug-APK
make bundle                  # AAB для Google Play
make size                    # размер release-APK против бюджета 50 МБ
make ui-test                 # инструментальные тесты на эмуляторе Pixel_8
```

Полный список целей — `make help`.

## Настройка

Адрес сервера резервных копий вшивается в сборку через `gradle.properties`:

```properties
todi.backupServerUrl=https://example.com
```

Пустое значение означает, что адреса по умолчанию нет: приложение будет честно
сообщать, что резервная копия недоступна, вместо того чтобы стучаться в
неизвестный хост. Протокол сервера описан в [docs/BACKUP.md](docs/BACKUP.md);
сам сервер в этот репозиторий не входит.

Release-подпись читается из `keystore.properties` в корне. Файл и сам ключ
исключены из репозитория; создать их можно командой `make init-keystore`.

## Структура проекта

```
app/src/main/java/com/todi/app/
├── backup/          резервные копии и транспорт к своему серверу
├── calendar/        синхронизация с календарём телефона
├── data/            Room, DataStore, репозитории
├── di/              Hilt-модули
├── domain/          модели, use cases, чистые утилиты и парсеры
├── notification/    уведомления, каналы, воркеры и receivers
└── presentation/    Compose-экраны, ViewModel, навигация, голосовой ввод
```

Подробное описание слоёв, потоков данных и решений — в
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Документация

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — устройство приложения и поток данных
- [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) — окружение, сборка, тесты, отладка
- [docs/BACKUP.md](docs/BACKUP.md) — формат снапшота и API сервера копий
- [docs/RELEASE.md](docs/RELEASE.md) — версии, подпись, публикация в Google Play
- [docs/PRIVACY.md](docs/PRIVACY.md) — какие данные и куда уходят
- [CHANGELOG.md](CHANGELOG.md) — история версий
- [CONTRIBUTING.md](CONTRIBUTING.md) — как предложить изменение

## Идентификатор приложения

`namespace` исходников — `com.todi.app`, а `applicationId` — `com.todi.appp`.
Второе имя историческое: оно уже опубликовано, а менять `applicationId` после
первой публикации нельзя. Переименование `namespace` не требуется и не влияет на
идентификатор установленного приложения.

## Лицензия

[MIT](LICENSE). Сторонние библиотеки распространяются на своих условиях;
голосовая модель Vosk скачивается отдельно и в репозиторий не входит.
