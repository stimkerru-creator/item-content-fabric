package dev.itemcontent.definition;

import java.util.*;
import static dev.itemcontent.definition.PropertyDefinition.*;

public final class BuiltinTypes {
    private BuiltinTypes() {}
    public static final Map<String, String> GROUPS;
    static {
        Map<String, String> groups = new LinkedHashMap<>();
        groups.put("minecraft:ingredients", "Ингредиенты"); groups.put("minecraft:building_blocks", "Строительные блоки");
        groups.put("minecraft:colored_blocks", "Цветные блоки"); groups.put("minecraft:natural_blocks", "Природные блоки");
        groups.put("minecraft:functional_blocks", "Функциональные блоки"); groups.put("minecraft:redstone_blocks", "Механизмы");
        groups.put("minecraft:tools_and_utilities", "Инструменты"); groups.put("minecraft:combat", "Оружие и броня");
        groups.put("minecraft:food_and_drinks", "Еда и напитки"); groups.put("minecraft:spawn_eggs", "Яйца призыва");
        GROUPS = Collections.unmodifiableMap(groups);
    }
    private static final PropertyDefinition DURABILITY = integer("durability", "Прочность (0 — без износа)", 0, 0, 10000000, true, "Для изнашиваемого предмета размер стака должен быть 1.");
    public static List<TypeDefinition> all() {
        List<TypeDefinition> result = new ArrayList<>();
        result.add(type("item", "Обычный предмет", "Предметы", "ingredients", "generated", false, List.of(DURABILITY), "Слитки, пластины, компоненты и другие предметы без специального поведения."));
        result.add(type("food", "Еда", "Еда", "food_and_drinks", "generated", false, List.of(
                integer("hunger", "Восстановление голода", 4, 0, 100, true, "Одна иконка голода соответствует двум единицам."),
                number("saturation", "Коэффициент насыщения", 0.3, 0, 100, true, "Штатный коэффициент насыщения Minecraft."),
                flag("always_edible", "Можно есть сытым", false, true, ""), flag("meat", "Подходит для кормления волков", false, true, ""),
                flag("snack", "Быстро съедается", false, true, "")), "Используется стандартная механика еды Minecraft."));
        for (String id : List.of("sword", "pickaxe", "axe", "shovel", "hoe")) {
            String label = switch (id) { case "sword" -> "Меч"; case "pickaxe" -> "Кирка"; case "axe" -> "Топор"; case "shovel" -> "Лопата"; default -> "Мотыга"; };
            List<PropertyDefinition> p = new ArrayList<>(List.of(
                    integer("durability", "Прочность", 250, 1, 10000000, true, ""),
                    number("attack_damage", "Урон", 6, 1, 100000, false, "Общий урон обычного игрока до зачарований и других модификаторов."),
                    number("attack_speed", "Атак в секунду", id.equals("sword") ? 1.6 : 1.0, 0.05, 100, false, "Итоговая скорость для игрока с обычными базовыми атрибутами."),
                    integer("enchantability", "Зачаровываемость", 14, 0, 100, false, ""),
                    identifier("repair_item", "Предмет для ремонта", "minecraft:iron_ingot", false, "Можно указать ID ванильного или модового предмета.")));
            if (!id.equals("sword")) p.addAll(List.of(number("mining_speed", "Скорость добычи", 6, 0.1, 100000, false, ""),
                    integer("mining_level", "Уровень добычи: 0 дерево, 1 камень, 2 железо, 3 алмаз, 4 незерит", 2, 0, 4, false, "Применяется вместе с тегами добываемых блоков.")));
            result.add(type(id, label, id.equals("sword") ? "Оружие" : "Инструменты", id.equals("sword") ? "combat" : "tools_and_utilities", "handheld", false, p, "Штатный класс Minecraft; поддерживает обычные действия, износ и зачарования."));
        }
        result.add(type("bow", "Лук", "Оружие", "combat", "bow", false, List.of(
                integer("durability", "Прочность", 384, 1, 10000000, true, ""),
                integer("draw_ticks", "Время полного натяжения, тики", 20, 1, 1200, false, "20 тиков = 1 секунда. Используются обычные стрелы и зачарования.")), "Стандартный лук Minecraft с настраиваемым временем натяжения."));
        result.add(type("crossbow", "Арбалет", "Оружие", "combat", "crossbow", false, List.of(integer("durability", "Прочность", 465, 1, 10000000, true, "")), "Стандартный арбалет Minecraft; скорость зарядки определяется его обычной механикой и зачарованиями."));
        result.add(type("armor", "Броня", "Броня", "combat", "generated", false, List.of(
                choice("slot", "Часть комплекта", "chestplate", List.of("helmet", "chestplate", "leggings", "boots"), false, "helmet — шлем; chestplate — нагрудник; leggings — поножи; boots — ботинки."),
                integer("durability", "Прочность", 240, 1, 10000000, true, ""), integer("protection", "Защита", 6, 0, 100, false, ""),
                number("toughness", "Твёрдость брони", 0, 0, 100, false, ""), number("knockback_resistance", "Сопротивление отбрасыванию", 0, 0, 1, false, ""),
                integer("enchantability", "Зачаровываемость", 9, 0, 100, false, ""),
                choice("armor_texture", "Текстура надетой брони", "iron", List.of("leather", "chainmail", "iron", "gold", "diamond", "netherite", "custom"), false, "Для custom выберите обе PNG-слоя брони во внешнем виде."),
                identifier("repair_item", "Предмет для ремонта", "minecraft:iron_ingot", false, "")), "PNG иконки и изображение надетой брони задаются отдельно."));
        var blockProperties = List.of(number("hardness", "Твёрдость блока", 1.5, 0, 100000, false, "Влияет на время разрушения."),
                number("resistance", "Взрывоустойчивость", 6, 0, 3600000, false, ""),
                integer("light", "Излучаемый свет", 0, 0, 15, false, ""),
                choice("sound", "Звук блока", "stone", List.of("stone", "wood", "metal", "glass", "gravel", "wool"), false, ""),
                choice("tool", "Предпочтительный инструмент", "pickaxe", List.of("none", "pickaxe", "axe", "shovel", "hoe"), false, ""),
                integer("mining_level", "Требуемый уровень: 0 любой, 1 камень, 2 железо, 3 алмаз", 0, 0, 3, false, "При уровне 0 блок выпадает даже без подходящего инструмента."));
        result.add(type("block", "Обычный блок", "Блоки", "building_blocks", "cube", true, blockProperties, "Полноразмерный устанавливаемый блок: булыжник, руда, металл и т. п."));
        result.add(type("decorative_chest", "Декоративный сундук", "Блоки", "functional_blocks", "chest", true, blockProperties, "Ставится в мире и имеет форму сундука. Инвентаря и открытия нет."));
        result.add(type("decorative_furnace", "Декоративная печь", "Блоки", "functional_blocks", "furnace", true, blockProperties, "Ставится с ориентацией передней стороны. Плавки и инвентаря нет."));
        return List.copyOf(result);
    }
    private static TypeDefinition type(String id, String name, String category, String group, String model, boolean block, List<PropertyDefinition> p, String help) {
        return new TypeDefinition("itemcontent:" + id, name, category, "minecraft:" + group, model, block, p, help);
    }
    public static Map<String, TypeDefinition> index() {
        Map<String, TypeDefinition> types = new LinkedHashMap<>(); for (var type : all()) types.put(type.id(), type); return types;
    }
    public static String classify(String subtype) { return "itemcontent:" + subtype; }
}
