# Разработка

## Требования

- JDK 21.
- Android SDK с платформой `android-36.1` и build-tools 36.x.
- platform-tools и эмулятор или устройство с Android 8.0+.
- GNU Make — необязательно, но все команды ниже есть в `Makefile`.

Gradle устанавливать не нужно: используется wrapper. Kotlin встроен в AGP 9.

Проверка окружения:

```bash
make doctor
```

Если SDK не найден, создайте `local.properties` в корне:

```properties
sdk.dir=/path/to/Android/sdk
```

Файл исключён из репозитория.

## Сборка

```bash
./gradlew :app:assembleDebug
make build-debug
make build-release
make bundle
make size
```

`make build-release` требует `keystore.properties` и release-ключ. Создать их
можно командой `make init-keystore`. Подробности — в [RELEASE.md](RELEASE.md).

`make size` собирает release-APK и сравнивает его с бюджетом 50 МБ.

## Тесты и проверки

```bash
make test
make lint
make ktlint
make verify
make ui-test
```

- `make test` — юнит-тесты (`:app:testDebugUnitTest`).
- `make lint` — `:app:lintDebug`, должно быть 0 ошибок.
- `make ktlint` — проверка стиля, 0 нарушений.
- `make verify` — тесты, lint и ktlint вместе.
- `make ui-test` — инструментальные тесты на эмуляторе `Pixel_8`.

Перед `make ui-test` эмулятор должен быть запущен (`make emulator`) или
подключён телефон (`make phone`). Цель `ui-test` сама будит экран и сносит
установленные сборки перед прогоном.

Запуск отдельного класса:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.todi.app.NotificationViewsTest
```

## Стиль

Стиль задаёт `.editorconfig`:

- `ktlint_code_style = ktlint_official`;
- `max_line_length = 140`;
- функции с аннотацией `@Composable` могут начинаться с заглавной буквы.

Набор правил используется целиком. Версия ktlint закреплена в
`app/build.gradle.kts` отдельно от версии плагина: новый ktlint приносит новые
правила и меняет стиль молча. Обновлять её нужно отдельным шагом вместе с
`ktlintFormat`.

`make ktlint-format` исправляет то, что чинится автоматически. Если команда
падает, она печатает список того, что нужно поправить руками: имена, имена
файлов, KDoc.

`SCREAMING_CASE` у `private val` допустим только вне тела класса — в
`companion object` или на верхнем уровне. В теле класса константы именуются в
camelCase.

## Неочевидные настройки сборки

- **AGP 9 built-in Kotlin.** Плагин `kotlin-android` не применяется, он
  конфликтует со встроенным. KSP генерирует исходники через `kotlin.sourceSets`,
  поэтому в `gradle.properties` стоит `android.disallowKotlinSourceSets=false`.
- **compileSdk 36.1.** Стабильный канал SDK не содержит android-37, а свежие
  AndroidX-библиотеки уже требуют его. Версии закреплены на последних
  совместимых с 36: core 1.18.0, lifecycle 2.10.0, navigation 2.9.8,
  hilt-navigation-compose 1.3.0.
- **Локали отфильтрованы до `ru` и `en`** в `androidResources.localeFilters`.
  Без фильтра библиотеки приносят десятки локалей, и AAB объявляет их своими.
- **kotlinx-coroutines-test 1.9.0** совпадает с версией coroutines в приложении.
  Рассинхрон ломает androidTest ошибкой `NoSuchMethodError`.
- **Vosk в release только под две ABI**: `arm64-v8a` и `armeabi-v7a`. Debug
  остаётся универсальным, иначе тесты на x86_64-эмуляторе не запустятся.
- **R8** требует `keep` для `com.sun.jna.**` и `org.vosk.**`, а также
  `-dontwarn java.awt.**`.

## Известные особенности инструментальных тестов

### Снос сборок перед прогоном

`make ui-test` вызывает `adb uninstall`. Без этого `install -r` отправляет
`MY_PACKAGE_REPLACED`, `BootTimeReceiver` пытается работать до создания
Hilt-компонента, процесс падает, и Gradle сообщает «Starting 0 tests» и
«Instrumentation run failed due to Process crashed».

### TIME_SET от эмулятора

Эмулятор сверяет часы с хостом раз в 10–18 минут и рассылает
`android.intent.action.TIME_SET`. Receiver вызывает
`MidnightWorkScheduler.schedule()`, а в тестовом процессе WorkManager не
инициализирован, поэтому процесс падает. В отчёте это видно по стеку через
`BootTimeReceiver` и сообщению `WorkManager is not initialized properly`.
Проверить рассылки можно так:

```bash
adb logcat -b events | grep TIME_SET
```

Повторный запуск упавшего класса обычно проходит.

### Единичный сбой при уничтожении activity

Иногда прогон обрывается на первом классе с `ArrayIndexOutOfBoundsException` в
`SlotWriter` при уничтожении activity. Это не дефект теста: отдельный прогон
класса проходит. Признак — в отчёте один тест вместо полного списка и нет
проваленных assertion'ов.

### Микрофон и аудио эмулятора

Эмулятор запускается с `-no-audio`: при обращении к микрофону QEMU на macOS
может зависнуть целиком. Разрешение `RECORD_AUDIO` выдаётся в `@Before` через
`pm grant`, потому что системный диалог принадлежит другому activity и ломает
поиск Compose-иерархии.

### Остановленные часы

Бесконечные анимации (шар голосового ввода, подсказка) не дают Compose-тесту
дойти до idle. В таких тестах до `setContent` выставляется
`mainClock.autoAdvance = false`, а кадры двигаются вручную.

## Отладка

Логи:

```bash
make logs
adb logcat -s CalendarSync
```

Содержимое дел, адреса почты и токены не должны попадать в логи. В сообщения
пишется только класс исключения.

Диагностика календаря видна в настройках приложения в поле последней ошибки
синхронизации. На EMUI системные логи приложений могут быть недоступны.

## Переменные сборки

| Свойство | Значение по умолчанию | Назначение |
|---|---|---|
| `todi.backupServerUrl` | `https://app-todi.ru` | адрес сервера копий, попадает в `BuildConfig` |

Пустое значение означает отсутствие сервера по умолчанию: приложение сообщает,
что резервная копия недоступна.
