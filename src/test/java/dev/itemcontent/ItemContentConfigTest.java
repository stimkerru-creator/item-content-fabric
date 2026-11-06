package dev.itemcontent;

import dev.itemcontent.config.ItemContentConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

final class ItemContentConfigTest {
    @Test void parsesShortIdsAndFields() throws Exception {
        var items = ItemContentConfig.parse("""
                {"version":1,"items":[{"id":"plate","name":"Plate","fields":["mass",{"id":"hardness","default":25.00}]}]}
                """);
        assertEquals(1, items.size());
        assertEquals("itemcontent:plate", items.get(0).id().toString());
        assertEquals("itemcontent:mass", items.get(0).fields().get(0).id());
        assertEquals(25.00, items.get(0).fields().get(1).defaultValue().doubleValue());
    }

    @Test void rejectsUnknownNamespaceAndDuplicateIds() {
        assertThrows(IOException.class, () -> ItemContentConfig.parse("{\"version\":1,\"items\":[{\"id\":\"other:plate\",\"name\":\"x\"}]}"));
        assertThrows(IOException.class, () -> ItemContentConfig.parse("{\"version\":1,\"items\":[{\"id\":\"plate\",\"name\":\"x\"},{\"id\":\"plate\",\"name\":\"y\"}]}"));
    }

    @Test void rejectsUnsupportedPrecisionAndUnknownKeys() {
        assertThrows(IOException.class, () -> ItemContentConfig.parse("{\"version\":1,\"items\":[{\"id\":\"plate\",\"name\":\"x\",\"fields\":[{\"id\":\"mass\",\"default\":1.001}]}]}"));
        assertThrows(IOException.class, () -> ItemContentConfig.parse("{\"version\":1,\"items\":[{\"id\":\"plate\",\"name\":\"x\",\"unexpected\":true}]}"));
        assertThrows(IOException.class, () -> ItemContentConfig.parse("{\"version\":1,\"items\":[{\"id\":\"plate\",\"name\":\"x\",\"max_count\":0}]}"));
    }
}
