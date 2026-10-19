SHELL := /bin/bash

APP_ID    := com.todi.appp
APP_NAME  := Todi
ACTIVITY  := com.todi.appp/.presentation.MainActivity

GRADLE       := ./gradlew
KEYTOOL      ?= keytool
AVD          ?= Pixel_8
EMULATOR_BIN ?= emulator

DEBUG_APK    := app/build/outputs/apk/debug/app-debug.apk
RELEASE_APK  := app/build/outputs/apk/release/app-release.apk
AAB          := app/build/outputs/bundle/release/app-release.aab

SIZE_BUDGET_MB ?= 50

DEBUG_KEYSTORE   := $(HOME)/.android/debug.keystore
KEYSTORE_PROPS   := keystore.properties
RELEASE_KEYSTORE := release.keystore

ANDROID_HOME ?= $(shell sed -n 's/^sdk.dir=//p' local.properties 2>/dev/null)
ifeq ($(ANDROID_HOME),)
ANDROID_HOME := $(HOME)/Library/Android/sdk
endif
export PATH := $(PATH):$(ANDROID_HOME)/platform-tools:$(ANDROID_HOME)/emulator

DEVICE ?=

VERSION_NAME := $(shell grep -oE 'versionName = "[^"]+"' app/build.gradle.kts | sed -E 's/.*"([^"]+)".*/\1/')
VERSION_CODE := $(shell grep -oE 'versionCode = [0-9]+' app/build.gradle.kts | grep -oE '[0-9]+')

define SERIAL_EMU
$$(adb devices | awk '$$2=="device" && $$1 ~ /emulator/ {print $$1; exit}')
endef
define SERIAL_PHONE
$$(adb devices | awk '$$2=="device" && $$1 !~ /emulator/ {print $$1; exit}')
endef
define SHOW_DEVICES
echo "  Устройства (adb devices):"; \
adb devices | sed 's/^/    /'; \
if [ -z "$1" ]; then echo "  Выбрано: НЕТ (ни одно устройство не доступно)"; \
else echo "  Выбрано: $1"; fi
endef

.PHONY: help
help:
	@echo ""
	@echo "  $(APP_NAME) ($(APP_ID)) — быстрый список дел по датам"
	@echo "  Версия: $(VERSION_NAME) (versionCode $(VERSION_CODE))"
	@echo ""
	@echo "  ── Цели ───────────────────────────────────────────────────────────"
	@echo "  make doctor              — проверить окружение (JDK, SDK, adb, ключи)"
	@echo "  make build-debug         — debug-APK и отпечатки debug-подписи"
	@echo "  make build-release       — подписанный release-APK (minify + shrink)"
	@echo "  make bundle              — AAB для Google Play"
	@echo "  make size                — размер release-APK против бюджета $(SIZE_BUDGET_MB) МБ"
	@echo "  make test                — unit-тесты"
	@echo "  make lint                — Android lint (0 ошибок)"
	@echo "  make ktlint              — проверка стиля Kotlin"
	@echo "  make ktlint-format       — автоправка стиля Kotlin"
	@echo "  make verify              — unit-тесты + lint + ktlint"
	@echo "  make build-check         — собрать main, unit-тесты и androidTest без устройства"
	@echo "  make ui-test             — UI-тесты на эмуляторе (AVD $(AVD))"
	@echo "  make emulator-boot       — запустить эмулятор, если он ещё не запущен"
	@echo "  make emulator            — собрать и открыть приложение на эмуляторе"
	@echo "  make phone               — собрать и открыть приложение на телефоне"
	@echo "  make install             — установить debug-APK"
	@echo "  make uninstall           — удалить приложение с устройства"
	@echo "  make logs                — logcat приложения"
	@echo "  make devices             — список подключённых устройств"
	@echo "  make init-keystore       — создать release-keystore и keystore.properties"
	@echo "  make bump-version        — поднять versionCode (+1); NEW_VERSION=x.y.z"
	@echo "  make clean               — очистить результат сборки"
	@echo "  make clean-cache         — очистить build, app/build и .gradle"
	@echo "  make clean-global        — снять демоны и глобальный кэш Gradle"
	@echo ""
	@echo "  ── Окружение ──────────────────────────────────────────────────────"
	@echo "  OS             macOS / Linux (bash, make)"
	@echo "  JDK            21 LTS"
	@echo "  Android SDK    platform android-36.1, build-tools 36.x, platform-tools"
	@echo "                 эмулятор и AVD '$(AVD)' — для make emulator и make ui-test"
	@echo "                 путь: ANDROID_HOME или local.properties (sdk.dir)"
	@echo "  Gradle         не нужен: ./gradlew скачает Gradle 9.4.1"
	@echo "  Kotlin         не нужен: AGP 9 со встроенным Kotlin"
	@echo "  keytool        из JDK — для отпечатков подписи и init-keystore"
	@echo "  RAM            3+ ГБ под Gradle daemon"
	@echo ""
	@echo "  Примеры:"
	@echo "    make doctor"
	@echo "    make build-debug"
	@echo "    make bundle"
	@echo "    make emulator"
	@echo "    make phone"
	@echo "    make bump-version NEW_VERSION=1.1"
	@echo ""

.PHONY: doctor
doctor:
	@echo ""
	@echo "  === Диагностика окружения $(APP_NAME) ==="
	@echo ""
	@echo -n "  make              "; command -v make >/dev/null && echo "OK  $(shell command -v make)" || echo "НЕТ"
	@JAVA_VER=$$(java -version 2>&1 | awk -F'"' '/version/ {print $$2}'); \
	case "$$JAVA_VER" in 1.*) JAVA_MAJOR=$$(echo "$$JAVA_VER" | cut -d. -f2);; *) JAVA_MAJOR=$$(echo "$$JAVA_VER" | cut -d. -f1);; esac; \
	if [ -z "$$JAVA_VER" ]; then echo "  java              НЕТ (нужен JDK 17+)"; \
	elif [ "$$JAVA_MAJOR" -ge 17 ]; then echo "  java              OK  $$JAVA_VER (нужно 17+)"; \
	else echo "  java              FAIL $$JAVA_VER (нужно 17+)"; fi
	@echo -n "  gradlew           "; [ -x gradlew ] && echo "OK" || echo "НЕТ"
	@echo -n "  ANDROID_HOME      "; [ -d "$(ANDROID_HOME)" ] && echo "OK  $(ANDROID_HOME)" || echo "НЕТ: задайте ANDROID_HOME или local.properties"
	@echo -n "  platform 36.1     "; [ -d "$(ANDROID_HOME)/platforms/android-36.1" ] && echo "OK" || echo "НЕТ (SDK Manager → SDK Platform android-36.1)"
	@echo -n "  platform-tools    "; [ -x "$(ANDROID_HOME)/platform-tools/adb" ] && echo "OK" || echo "НЕТ (SDK Manager → Android SDK Platform-Tools)"
	@echo -n "  keytool           "; command -v $(KEYTOOL) >/dev/null && echo "OK  $(shell command -v $(KEYTOOL))" || echo "НЕТ (нужен JDK)"
	@echo -n "  debug-keystore    "; [ -f "$(DEBUG_KEYSTORE)" ] && echo "OK  $(DEBUG_KEYSTORE)" || echo "НЕТ"
	@echo -n "  release-подпись   "; [ -f "$(KEYSTORE_PROPS)" ] && echo "OK  $(KEYSTORE_PROPS)" || echo "НЕТ (для релиза: make init-keystore)"
	@echo -n "  emulator (bin)    "; command -v $(EMULATOR_BIN) >/dev/null && echo "OK" || echo "НЕТ"
	@echo -n "  AVD $(AVD)     "; $(EMULATOR_BIN) -list-avds 2>/dev/null | grep -qx "$(AVD)" && echo "OK" || echo "НЕТ (создайте AVD '$(AVD)')"
	@echo "  Устройства:"
	@adb devices 2>/dev/null | sed 's/^/    /'
	@echo ""

.PHONY: build-debug
build-debug:
	$(GRADLE) :app:assembleDebug
	@echo ""
	@echo "  === $(APP_NAME) — debug-сборка ==="
	@echo "  applicationId : $(APP_ID)"
	@echo "  versionName   : $(VERSION_NAME)"
	@echo "  versionCode   : $(VERSION_CODE)"
	@echo "  APK           : $(DEBUG_APK) ($$(ls -lh $(DEBUG_APK) | awk '{print $$5}'))"
	@echo "  SHA-256 APK   : $$(shasum -a 256 $(DEBUG_APK) | cut -d' ' -f1)"
	@echo "  --- Отпечатки debug-подписи (Google Cloud Console / Firebase) ---"
	@$(KEYTOOL) -list -v -keystore "$(DEBUG_KEYSTORE)" -alias androiddebugkey -storepass android 2>/dev/null | grep -E "SHA1:|SHA256:" | sed 's/^/  /' || echo "  (debug.keystore не найден)"

.PHONY: build-release
build-release: require-keystore
	$(GRADLE) :app:assembleRelease
	@echo ""
	@echo "  === $(APP_NAME) — release-сборка ==="
	@echo "  applicationId : $(APP_ID)"
	@echo "  versionName   : $(VERSION_NAME)"
	@echo "  versionCode   : $(VERSION_CODE)"
	@echo "  APK           : $(RELEASE_APK) ($$(ls -lh $(RELEASE_APK) | awk '{print $$5}'))"
	@echo "  SHA-256 APK   : $$(shasum -a 256 $(RELEASE_APK) | cut -d' ' -f1)"
	@echo "  --- Отпечатки release-подписи (Google Cloud Console / Play App Signing) ---"
	@$(MAKE) --no-print-directory fingerprints-release

.PHONY: bundle
bundle: require-keystore
	$(GRADLE) :app:bundleRelease
	@echo ""
	@echo "  === $(APP_NAME) — AAB для Play Market ==="
	@echo "  applicationId : $(APP_ID)"
	@echo "  versionName   : $(VERSION_NAME)"
	@echo "  versionCode   : $(VERSION_CODE)"
	@echo "  AAB           : $(AAB) ($$(ls -lh $(AAB) | awk '{print $$5}'))"
	@echo "  SHA-256 AAB   : $$(shasum -a 256 $(AAB) | cut -d' ' -f1)"
	@echo "  Загрузка: Play Console → Выпуск → Тестирование → Производственная версия"
	@echo "  App signing: ключ загрузки = release.keystore; ключ подписи сгенерирует Google"
	@echo "  (отпечаток загрузки — в 'make fingerprints-release'; SHA-1 для Google APIs — там же)"

.PHONY: require-keystore
require-keystore:
	@if [ ! -f "$(KEYSTORE_PROPS)" ]; then \
		echo "Ошибка: нет $(KEYSTORE_PROPS) — release не подпишется."; \
		echo "Создайте ключ:  make init-keystore"; \
		echo "(или: make build-debug — для локальной проверки)"; \
		exit 1; \
	fi

.PHONY: fingerprints
fingerprints:
	@echo "  --- Debug-подпись ($(DEBUG_KEYSTORE)) ---"
	@$(KEYTOOL) -list -v -keystore "$(DEBUG_KEYSTORE)" -alias androiddebugkey -storepass android 2>/dev/null | grep -E "SHA1:|SHA256:" | sed 's/^/  /' || echo "  (debug.keystore не найден)"
	@$(MAKE) --no-print-directory fingerprints-release

.PHONY: fingerprints-release
fingerprints-release:
	@if [ ! -f "$(KEYSTORE_PROPS)" ]; then echo "  (release-подписи нет — make init-keystore)"; exit 0; fi; \
	STORE_FILE=$$(sed -n 's/^storeFile=//p' $(KEYSTORE_PROPS)); \
	KEY_ALIAS=$$(sed -n 's/^keyAlias=//p' $(KEYSTORE_PROPS)); \
	STORE_PASS=$$(sed -n 's/^storePassword=//p' $(KEYSTORE_PROPS)); \
	echo "  --- Release-подпись ($$STORE_FILE, alias $$KEY_ALIAS) ---"; \
	$(KEYTOOL) -list -v -keystore "$$STORE_FILE" -alias "$$KEY_ALIAS" -storepass "$$STORE_PASS" 2>/dev/null | grep -E "SHA1:|SHA256:" | sed 's/^/  /' || echo "  (не удалось прочитать keystore)"

.PHONY: test
test:
	$(GRADLE) :app:testDebugUnitTest

.PHONY: lint
lint:
	$(GRADLE) :app:lintDebug

.PHONY: ktlint
ktlint:
	$(GRADLE) :app:ktlintCheck

.PHONY: ktlint-format
ktlint-format:
	$(GRADLE) :app:ktlintFormat

.PHONY: verify
verify: test lint ktlint

.PHONY: build-check
build-check:
	$(GRADLE) :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest

.PHONY: size
size:
	$(GRADLE) :app:assembleRelease
	@SIZE=$$(stat -f%z $(RELEASE_APK) 2>/dev/null || stat -c%s $(RELEASE_APK)); \
	MB=$$(echo "$$SIZE" | awk '{printf "%.1f", $$1/1048576}'); \
	echo ""; \
	echo "  release-APK: $$MB МБ (бюджет $(SIZE_BUDGET_MB) МБ)"; \
	if [ "$$SIZE" -gt $$(( $(SIZE_BUDGET_MB) * 1048576 )) ]; then \
		echo "  ПРЕВЫШЕН БЮДЖЕТ: APK больше $(SIZE_BUDGET_MB) МБ"; exit 1; \
	fi; \
	echo "  OK"

.PHONY: ui-test
ui-test: emulator-boot
	@SER=$(SERIAL_EMU); \
	if [ -z "$$SER" ]; then echo "Эмулятор не найден в adb devices."; exit 1; fi; \
	adb -s $$SER shell svc power stayon true; \
	adb -s $$SER uninstall $(APP_ID) >/dev/null 2>&1; \
	adb -s $$SER uninstall $(APP_ID).test >/dev/null 2>&1; \
	ANDROID_SERIAL=$$SER $(GRADLE) :app:connectedDebugAndroidTest; \
	EXIT=$$?; adb -s $$SER shell svc power stayon false; exit $$EXIT

.PHONY: emulator-boot
emulator-boot:
	@SER=$(SERIAL_EMU); \
	if [ -n "$$SER" ]; then \
		echo "Эмулятор уже запущен: $$SER"; \
	else \
		echo "Запускаю эмулятор $(AVD)…"; \
		($(EMULATOR_BIN) -avd $(AVD) -no-snapshot-load -no-audio > /tmp/todi-emulator.log 2>&1 &); \
		adb wait-for-device; \
		until [ "$$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done; \
		SER=$(SERIAL_EMU); \
		echo "Эмулятор запущен."; \
	fi; \
	adb -s $$SER shell input keyevent KEYCODE_WAKEUP; \
	adb -s $$SER shell wm dismiss-keyguard

.PHONY: emulator
emulator: emulator-boot build-debug
	@SER=$(SERIAL_EMU); \
	$(call SHOW_DEVICES,$$SER); \
	if [ -z "$$SER" ]; then echo "Эмулятор не найден в adb devices."; exit 1; fi; \
	adb -s $$SER install -r $(DEBUG_APK); \
	adb -s $$SER shell am start -n $(ACTIVITY); \
	echo "Приложение запущено на эмуляторе $$SER."

.PHONY: phone
phone: build-debug
	@if [ -n "$(DEVICE)" ]; then SER="$(DEVICE)"; else SER=$(SERIAL_PHONE); fi; \
	$(call SHOW_DEVICES,$$SER); \
	if [ -z "$$SER" ]; then \
		echo "Телефон не найден (adb devices пуст). Подключите по USB с отладкой или задайте: make phone DEVICE=<serial>"; \
		exit 1; \
	fi; \
	adb -s $$SER install -r $(DEBUG_APK); \
	adb -s $$SER shell am start -n $(ACTIVITY); \
	echo "Приложение запущено на телефоне $$SER."

.PHONY: install
install: build-debug
	@if [ -n "$(DEVICE)" ]; then SER="$(DEVICE)"; else SER=$(SERIAL_EMU); [ -z "$$SER" ] && SER=$(SERIAL_PHONE); fi; \
	$(call SHOW_DEVICES,$$SER); \
	if [ -z "$$SER" ]; then echo "Нет подключённых устройств (adb devices)."; exit 1; fi; \
	adb -s $$SER install -r $(DEBUG_APK); \
	echo "Установлено на $$SER."

.PHONY: uninstall
uninstall:
	@if [ -n "$(DEVICE)" ]; then SER="$(DEVICE)"; else SER=$(SERIAL_EMU); [ -z "$$SER" ] && SER=$(SERIAL_PHONE); fi; \
	$(call SHOW_DEVICES,$$SER); \
	if [ -z "$$SER" ]; then echo "Нет подключённых устройств (adb devices)."; exit 1; fi; \
	adb -s $$SER uninstall $(APP_ID)

.PHONY: logs
logs:
	@if [ -n "$(DEVICE)" ]; then SER="$(DEVICE)"; else SER=$(SERIAL_EMU); [ -z "$$SER" ] && SER=$(SERIAL_PHONE); fi; \
	$(call SHOW_DEVICES,$$SER); \
	if [ -z "$$SER" ]; then echo "Нет подключённых устройств (adb devices)."; exit 1; fi; \
	PID=$$(adb -s $$SER shell pidof $(APP_ID) | tr -d '\r'); \
	if [ -n "$$PID" ]; then adb -s $$SER logcat --pid=$$PID; \
	else adb -s $$SER logcat -d | grep -E "$(APP_ID)|AndroidRuntime"; fi

.PHONY: devices
devices:
	@SERS=$$(adb devices | awk '$$2=="device" {print $$1}'); \
	if [ -z "$$SERS" ]; then echo "Нет подключённых устройств."; exit 0; fi; \
	echo "Подключённые устройства:"; \
	for SER in $$SERS; do \
		MODEL=$$(adb -s $$SER shell getprop ro.product.model 2>/dev/null | tr -d '\r'); \
		ANDROID=$$(adb -s $$SER shell getprop ro.build.version.release 2>/dev/null | tr -d '\r'); \
		ROLE="телефон"; echo "$$SER" | grep -q emulator && ROLE="эмулятор"; \
		printf "    %-22s %-10s %s (Android %s)\n" "$$SER" "$$ROLE" "$$MODEL" "$$ANDROID"; \
	done; \
	echo ""; \
	echo "Авто-выбор: эмулятор = $(SERIAL_EMU), телефон = $(SERIAL_PHONE)"; \
	echo "Приоритет: DEVICE=<serial> (явный) > эмулятор > телефон"

.PHONY: init-keystore
init-keystore:
	@if [ -f "$(KEYSTORE_PROPS)" ]; then \
		echo "Ошибка: $(KEYSTORE_PROPS) уже существует — удалите его вручную, если хотите пересоздать."; \
		exit 1; \
	fi
	@STORE_PASS=$${STORE_PASS:-$$(openssl rand -base64 18 2>/dev/null || date +%s | shasum -a 256 | cut -c1-24)}; \
	KEY_PASS=$${KEY_PASS:-$$STORE_PASS}; \
	ALIAS=$${ALIAS:-todi-release}; \
	DNAME=$${DNAME:-"CN=Todi, OU=Mobile, O=Todi, C=RU"}; \
	$(KEYTOOL) -genkeypair -v -keystore $(RELEASE_KEYSTORE) -alias "$$ALIAS" \
		-keyalg RSA -keysize 2048 -validity 10000 \
		-storepass "$$STORE_PASS" -keypass "$$KEY_PASS" -dname "$$DNAME" 2>&1 | tail -3; \
	printf 'storeFile=%s\nstorePassword=%s\nkeyAlias=%s\nkeyPassword=%s\n' \
		"$(RELEASE_KEYSTORE)" "$$STORE_PASS" "$$ALIAS" "$$KEY_PASS" > $(KEYSTORE_PROPS); \
	chmod 600 $(KEYSTORE_PROPS) $(RELEASE_KEYSTORE); \
	echo ""; \
	echo "Готово: $(RELEASE_KEYSTORE) + $(KEYSTORE_PROPS) (права 600, в .gitignore)."; \
	echo "Пароль хранилища: $$STORE_PASS"; \
	echo "ВАЖНО: сохраните $(RELEASE_KEYSTORE) и пароль в надёжном месте —"; \
	echo "без них нельзя будет обновлять приложение в Play Market!"

.PHONY: bump-version
bump-version:
	@OLD_CODE=$(VERSION_CODE); \
	NEW_CODE=$$((OLD_CODE + 1)); \
	sed -i.bak -E "s/versionCode = [0-9]+/versionCode = $$NEW_CODE/" app/build.gradle.kts; \
	rm -f app/build.gradle.kts.bak; \
	if [ -n "$(NEW_VERSION)" ]; then \
		sed -i.bak -E "s/versionName = \"[^\"]+\"/versionName = \"$(NEW_VERSION)\"/" app/build.gradle.kts; \
		rm -f app/build.gradle.kts.bak; \
		echo "versionName: $(VERSION_NAME) -> $(NEW_VERSION)"; \
	fi; \
	echo "versionCode: $$OLD_CODE -> $$NEW_CODE"

.PHONY: clean
clean:
	$(GRADLE) clean

.PHONY: clean-cache
clean-cache: clean
	rm -rf app/build .gradle build
	@echo "Локальные артефакты удалены. Глобальный кэш Gradle: make clean-global"

.PHONY: clean-global
clean-global:
	@echo "  Останавливаю демоны Gradle (своей версии и чужих)..."
	-@$(GRADLE) --stop
	-@pkill -f 'org.gradle.launcher.daemon.bootstrap.GradleDaemon'
	@echo "  Удаляю глобальный кэш $(HOME)/.gradle/caches..."
	@rm -rf $(HOME)/.gradle/caches
	@echo "  Удаляю локальные артефакты app/build, .gradle, build..."
	@rm -rf app/build .gradle build
	@echo "Глобальный и локальный кэши удалены, демонов нет. Следующая сборка перекачает зависимости."
