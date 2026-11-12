# Сборка и подготовка публикации

Исходники: JDK 17+, Gradle Wrapper включён. Для первой сборки нужен доступ к репозиториям зависимостей.
Готовое desktop-приложение требует Java 17+, без JDK и Gradle.

## Проверки

Из корня проекта в PowerShell:

```powershell
.\gradlew.bat build --no-daemon
.\gradlew.bat -p editor build --no-daemon
python tools/package-release.py
```

Linux/macOS: замените `.\gradlew.bat` на `./gradlew`, `python` на `python3`.
Python 3 используется только для упаковки, игра и редактор от него не зависят.

`build` проверяет runtime-парсер и совместимость с экспортом desktop-приложения.
`-p editor build` отдельно проверяет модель проекта и действия Swing без Minecraft.
`package-release.py` проверяет Java 17 bytecode, версии, лицензии и границы JAR,
затем создаёт ZIP и контрольные суммы SHA-256 в `build/distributions`.

ZIP содержит готовый редактор в `lib`, мод в `mods`, пример, инструкции и лицензии.
В Windows запускайте `run-editor.bat` после распаковки всей папки. На Linux/macOS:

```text
java -jar lib/item-content-editor-1.2.0.jar
```

GitHub Actions повторяет сборку на Java 17 и прикладывает артефакты к запуску workflow.
Это конфигурация CI; успешность удалённого запуска проверяется отдельно от локальных тестов.

## Структура

| Каталог | Назначение |
|---|---|
| `src/main` | Fabric-мод и загрузка описаний предметов |
| `src/test` | Проверки runtime и совместимости формата экспорта |
| `editor/src/main` | Независимый Swing-редактор и экспорт |
| `editor/src/test` | Тесты сохранения, экспорта, сохранности старых данных и действий интерфейса |
| `examples` | Редактируемые учебные проекты |
| `docs` | Пользовательские инструкции и результаты проверок |
| `tools` | Запуск редактора и упаковка выпуска |

`.gradle`, `.gradle-home`, `build`, `editor/build` и `run` исключены из Git.
Наличие этих каталогов на диске не означает, что они опубликованы.

## Совместимость и расширения

Форматы списка и экспорта повышены до 2; версии 1 по-прежнему читаются.
Общие неизменяемые описания находятся в `common`; игровые фабрики — в `src/main`;
редактор строит формы по описаниям и не зависит от классов Minecraft.
[API типов](extensions.md) отделяет специфическую игровую механику от формата/интерфейса.
Swing/FlatLaf не входят в Minecraft JAR; тестовые поставщики также исключены из выпуска.

## Настоящий сервер и клиент

`./gradlew runIntegrationServer --no-daemon` создаёт **build/integration-server**,
локальный сервер на `127.0.0.1:25576` и тестовый мир. Используется уже принятое EULA из `run/eula.txt`;
для нового окружения после ознакомления и согласия с EULA укажите `-PacceptMinecraftEula=true`.
Пользовательские миры не читаются и не изменяются. Сервер сам останавливается после проверок;
при сбое Gradle завершает задачу ошибкой. Результат: `integration-result.txt` в этой папке.

Проверяются нативные классы, атрибуты, настройки существующих предметов, блоки в мире,
теги инструментов, категории и фабрика/инспектор пробного внешнего типа.
`integration-test` — тестовая модификация для dev-окружения, не часть выпуска.

`runIntegrationClient` сначала выполняет серверную проверку и подготавливает отдельный dev-клиент в
`build/integration-client` с копией тестового мира `saves/testworld`, конфигурацией и ресурсами.
Он открывает этот мир через quick play, проверяет реальный каталог и модели, затем закрывается.
Тестовый клиент требует обычных игровых assets и рабочего графического окружения;
он не запускается в безэкранном CI. Результат: `client-integration-result.txt`.

Для снимков редактора:

```text
java -jar editor/build/libs/item-content-editor-1.2.0.jar --screenshot build/editor.png 1280 680 --open examples/content-types.icproject --select itemcontent:bronze_sword --behavior
```

UI-тесты проходят при масштабах 1, 1.25 и 1.5. Они проверяют видимость действий и высоту полей,
но не заменяют визуальную проверку нативного окна.

## Running the editor while rebuilding

Use `run-editor.bat` or `tools/run-editor.ps1`. The launcher copies the chosen editor JAR to
`.editor-runtime/<build digest>/` inside the application folder and starts that immutable copy.
Rebuilding the distributable JAR cannot change the classes/resources used by an already open window.
Each editor build reuses its own snapshot; the folder is ignored by Git and is not included in releases.
Old snapshots can be removed manually after all editor instances using them have closed.
Do not rebuild a JAR used by a directly launched `java -jar` process.

Native Windows regression check (opens temporary test windows):
`powershell -File tools/check-editor-close.ps1 -JavaHome <JDK path>` after building the editor.
