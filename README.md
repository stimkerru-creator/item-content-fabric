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

После регистрации этот предмет можно указать в основном моде параметров как `itemcontent:steel_plate`. Если в конфигурации указаны `fields`, мод автоматически создаёт `config/itemparams/items/itemcontent_generated.json`; основной мод подхватит его на серверном старте. Короткое имя поля автоматически получает namespace `itemcontent:`.

Сборка: `./gradlew build`.
