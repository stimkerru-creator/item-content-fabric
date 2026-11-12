package dev.itemcontent.api;

import dev.itemcontent.definition.*;
import dev.itemcontent.item.BuiltinFactory;
import java.util.*;

/** One immutable-after-startup registry; external behavior is an optional provider. */
public final class ContentTypeRegistry {
    private static final ContentTypeRegistry INSTANCE = new ContentTypeRegistry();
    private final Map<String, TypeDefinition> definitions = BuiltinTypes.index();
    private final Map<String, ContentFactory> factories = new LinkedHashMap<>();
    private boolean frozen;
    private ContentTypeRegistry() { for (var type : definitions.values()) factories.put(type.id(), new BuiltinFactory(type)); }
    public static ContentTypeRegistry getInstance() { return INSTANCE; }
    public void register(TypeDefinition definition, ContentFactory factory) {
        if (frozen) throw new IllegalStateException("Content types are already frozen");
        Objects.requireNonNull(definition); Objects.requireNonNull(factory);
        if (definition.id().startsWith("itemcontent:") || definitions.containsKey(definition.id()))
            throw new IllegalArgumentException("Content type already reserved: " + definition.id());
        definitions.put(definition.id(), definition); factories.put(definition.id(), factory);
    }
    public void freeze() { frozen = true; }
    public Map<String, TypeDefinition> definitions() { return Collections.unmodifiableMap(definitions); }
    public TypeDefinition definition(String id) {
        var type = definitions.get(id);
        if (type == null) throw new IllegalArgumentException("Неизвестный тип " + id + ". Установите предоставляющее его расширение.");
        return type;
    }
    public ContentFactory factory(String id) { definition(id); return factories.get(id); }
}
