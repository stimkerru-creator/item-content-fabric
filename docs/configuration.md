# Item Content Fabric

Регистрирует реальные типы предметов из `config/item-content/items.json` на старте Minecraft. Работает рядом с `item-parameters` и не использует чужое пространство имён `minecraft`.

```json
{
  "version": 1,
  "items": [
    {
      "id": "steel_plate",
      "name": "Стальная пластина",
      "appearance": "minecraft:iron_ingot",
      "custom_model": false,
      "max_count": 64,
      "fireproof": false,
      "fields": [
        { "id": "mass", "default": 1.00, "min": 0, "max": 21474836.47 },
        { "id": "hardness", "default": 25.00 }
      ]
    }
  ]
}
```

Если `id` указан без namespace, создаётся `itemcontent:steel_plate`. Идентификатор регистрируется один раз до заморозки реестров; после изменения файла нужен перезапуск. Уже занятый ID отклоняется с понятной ошибкой.

Так как это настоящий Minecraft registry item, файл `items.json` должен быть одинаковым на сервере и на каждом клиенте до подключения. Это ограничение реестра Minecraft: предмет нельзя безопасно создать только сервером после начала игры.

После регистрации этот предмет появляется во вкладке творческого режима «Ингредиенты» и его можно указать в основном моде параметров как `itemcontent:steel_plate`. Клиентский entrypoint переиспользует модель из `appearance`, поэтому предмет виден в инвентаре без заглушечного JSON-моделя. Если в конфигурации указаны `fields`, мод автоматически создаёт `config/itemparams/items/itemcontent_generated.json`; основной мод подхватит его на серверном старте. Короткое имя поля автоматически получает namespace `itemcontent:`.

## Своя картинка предмета

Есть два режима:

1. `custom_model: false` — самый простой режим. Мод берёт готовую модель из `appearance`, например `minecraft:iron_ingot`. PNG добавлять не нужно.
2. `custom_model: true` — мод не подменяет модель. Minecraft загрузит модель из resource pack по ID предмета. Это нужно для своей картинки.

Для `itemcontent:steel_plate` создайте resource pack со структурой:

```text
steel-pack/
├─ pack.mcmeta
└─ assets/
   └─ itemcontent/
      ├─ models/item/steel_plate.json
      └─ textures/item/steel_plate.png
```

`pack.mcmeta` для Minecraft 1.20.1:

```json
{
  "pack": {
    "pack_format": 15,
    "description": "Item Content models"
  }
}
```

`assets/itemcontent/models/item/steel_plate.json`:

```json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "itemcontent:item/steel_plate"
  }
}
```

`steel_plate.png` — обычная PNG-текстура предмета, обычно 16×16, 32×32 или 64×64 пикселя. Положите папку `steel-pack` в `.minecraft/resourcepacks`, включите её в настройках ресурспаков и перезапустите клиент или выполните `F3+T`. На сервере и клиенте должен быть одинаковый `items.json`; resource pack нужен каждому клиенту.

Для другого предмета замените `steel_plate` во всех трёх местах на его ID без namespace. Если ресурс отсутствует или путь написан неправильно, предмет зарегистрируется, но Minecraft покажет missing-model.

## Сборка и проверки

Нужен JDK 17. Запустите `./gradlew build`; задача выполняет unit-тесты загрузчика, проверяет Fabric-манифест и собирает клиентский/серверный JAR. GitHub Actions повторяет ту же проверку на Temurin 17. Список сторонних библиотек и лицензий находится в `THIRD-PARTY-NOTICES.txt`.


## Версия формата

`version: 1` обозначает версию структуры файла настроек. Это не версия Minecraft,
мода или предмета. В выпуске мода 1.0.3 формат остаётся версии 1.

Числа записываются как числа JSON, а флаги как `true`/`false`, без кавычек.
Размер стака — целое число; повторяющиеся ID полей, неизвестные ключи и неверные типы
отклоняются при запуске. Для обычной работы используйте [редактор](editor.md):
он создаёт этот файл автоматически.
