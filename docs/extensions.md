# Расширения типов: API 1.2

Item Content не зависит от конкретного оружейного, технического или магического мода.
Другой мод либо отдельный аддон регистрирует **описание типа и фабрику поведения**.
Пользователь получает тип и понятные поля через каталог игры и работает в редакторе без Java/JSON.
Одного имени чужого Java-класса недостаточно: создание предмета нередко требует материалов,
реестров, боеприпасов, сетевых обработчиков, ресурсов и других зависимостей этого мода.

## Жизненный цикл

1. Fabric вызывает пользовательскую точку входа `itemcontent:extensions` в начале инициализации Item Content.
2. Поставщик регистрирует `TypeDefinition` и `ContentFactory`. ID типа — в пространстве поставщика.
3. Реестр типов замораживается. Загружается конфигурация; неизвестные поля/типы и неверные значения отклоняются.
4. Фабрики создают новые Item/Block до заморозки реестров Minecraft.
5. После инициализации модов применяются явно выбранные настройки существующих предметов.
6. Клиент выгружает описания типов и распознанные свойства в каталог редактора.

**Не регистрируйте тип в обычном main-entrypoint:** его порядок относительно Item Content не гарантирован.
Вызов `register` после заморозки или повторное использование ID завершается ошибкой.

## Метаданные Fabric аддона

Аддон указывает свою зависимость от Item Content и того мода, с которым он работает.
Базовый Item Content при этом не получает обязательной зависимости от этого мода.

```json
{
  "schemaVersion": 1,
  "id": "example_content_bridge",
  "version": "1.0.0",
  "environment": "*",
  "entrypoints": {
    "itemcontent:extensions": ["example.bridge.ContentTypes"]
  },
  "depends": {
    "fabricloader": ">=0.15.11",
    "minecraft": "1.20.1",
    "itemcontent": ">=1.2.0"
  }
}
```

## Пример собственного типа

Ниже самостоятельный минимальный пример. Класс `LauncherItem` иллюстрирует место подключения
реального класса мода; сам по себе он не стреляет. В рабочем мосте фабрика вызывает API нужного мода.

```java
package example.bridge;

import com.google.gson.JsonObject;
import dev.itemcontent.api.*;
import dev.itemcontent.config.ItemSpec;
import dev.itemcontent.definition.*;
import net.minecraft.item.Item;
import java.util.List;
import java.util.Optional;

public final class ContentTypes implements ItemContentExtension {
    public static final class LauncherItem extends Item {
        private float fireRate;
        public LauncherItem(Settings settings, float fireRate) {
            super(settings);
            this.fireRate = fireRate;
        }
    }

    @Override public void register(ContentTypeRegistry registry) {
        var type = new TypeDefinition(
            "example:launcher", "Автомат", "Оружие расширения",
            "minecraft:combat", "handheld", false,
            List.of(PropertyDefinition.number(
                "fire_rate", "Выстрелов в секунду", 5, 0.1, 100,
                true, "Настройка поведения этого типа.")),
            "Механика предоставляется установленным расширением.");

        registry.register(type, new ContentFactory() {
            @Override public CreatedContent create(ItemSpec spec, Item.Settings settings) {
                return new CreatedContent(new LauncherItem(settings, spec.number("fire_rate")));
            }

            @Override public Optional<JsonObject> inspectExisting(Item item) {
                if (!(item instanceof LauncherItem launcher)) return Optional.empty();
                var values = new JsonObject();
                values.addProperty("fire_rate", launcher.fireRate);
                return Optional.of(values);
            }

            @Override public void applyExisting(Item item, JsonObject values) {
                if (!(item instanceof LauncherItem launcher))
                    throw new IllegalArgumentException("Не тот игровой класс");
                if (values.has("fire_rate"))
                    launcher.fireRate = values.get("fire_rate").getAsFloat();
            }
        });
    }
}
```

`editExisting=true` разрешает редактирование поля исходного предмета. Возвращайте `false`,
если значение можно задать только при создании. По умолчанию `applyExisting` отклоняет непустые
настройки: поддержка редактирования должна быть реализована явно. `inspectExisting` распознаёт
поддерживаемые предметы; пустой Optional означает «этот поставщик не обслуживает данный предмет».
Первый распознавший поставщик имеет приоритет; избегайте слишком общего `instanceof Item`.

Новые настройки передаются уже проверенными и дополненными значениями по умолчанию.
Для исходного предмета передаются только явно выбранные разрешённые поля — поставщик сохраняет остальные.
Фабрика должна соблюдать переданные `Item.Settings` и создавать согласованную пару Item/Block.
Если `TypeDefinition.block=true`, возвращайте `new CreatedContent(blockItem, block)`;
обе регистрации выполняет Item Content. Не регистрируйте возвращаемые объекты повторно самостоятельно.

## Описание полей и моделей

Поддерживаются `number`, `integer` (int32), `boolean`, `text`, `choice`, `identifier`.
Числа проверяются по диапазону, идентификаторы — по формату, варианты выбора — по списку.
Лимит — 128 полей на тип. Это свойства определения предмета, не индивидуальное NBT-хранилище.

Шаблоны внешнего вида: `generated`, `handheld`, `bow`, `crossbow`, `cube`, `chest`, `furnace`.
Собственный рендерер, анимации, зарядка, боеприпасы и сеть принадлежат поставщику поведения.
Для сложного модового ресурса он регистрирует необходимую клиентскую часть и предоставляет ресурсы;
редактор не восстанавливает их из Java-класса. Обычные изображения/модели используют стандартный resource pack.

Клиент и сервер должны иметь совместимые версии поставщика и одинаковые определения.
Каталог содержит **только данные схемы**: редактор не загружает и не исполняет классы модов.
Импортированная схема помогает редактировать список, но не заменяет установленный поставщик в игре.

## Подключение к сборке

Опубликованного Maven-репозитория пока нет. Подключите собранный Fabric JAR как локальную mod-зависимость
в проекте Fabric Loom с Java 17 и теми же mappings, например:

```groovy
dependencies {
    modImplementation files('libs/item-content-fabric-1.2.0.jar')
}
```

Расширение — отдельный обычный Fabric-мод. Его автор поддерживает API целевого мода и права на используемые ресурсы.
Рабочий тест поставщика находится в `integration-test/src/main/java/dev/itemcontent/integration/IntegrationMod.java`.
Он проверяется в настоящем сервере, но не включается в распространяемый JAR.

Используемый механизм: [Fabric custom entrypoints](https://wiki.fabricmc.net/documentation:entrypoint).
Точки входа получает [FabricLoader.getEntrypoints](https://maven.fabricmc.net/docs/fabric-loader-0.15.11/net/fabricmc/loader/api/FabricLoader.html).
