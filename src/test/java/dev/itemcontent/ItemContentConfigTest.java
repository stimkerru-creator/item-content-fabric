package dev.itemcontent;

import dev.itemcontent.config.ItemContentConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

final class ItemContentConfigTest {
    @Test void parsesStandaloneItemWithOnlyVanillaProperties() throws Exception {
        var items = ItemContentConfig.parse("""
                {"version":1,"items":[{"id":"plate","name":"Plate","custom_model":true,"max_count":16,"fireproof":true}]}
                """);
        assertEquals(1, items.size()); assertEquals("itemcontent:plate", items.get(0).id().toString());
        assertTrue(items.get(0).customModel()); assertTrue(items.get(0).fireproof()); assertEquals(16, items.get(0).maxCount());
    }
    @Test void readsLegacyExportsWithoutInterpretingTheirNumericData() throws Exception {
        for (String legacy : new String[]{"[]", "[\"mass\"]", "[{\"default\":1.001}]"}) {
            var items = ItemContentConfig.parse("{\"version\":1,\"items\":[{\"id\":\"plate\",\"name\":\"Plate\",\"fields\":" + legacy + "}]}");
            assertEquals("itemcontent:plate", items.get(0).id().toString());
        }
    }

    @Test void rejectsUnknownNamespaceAndDuplicateIds() {
        assertThrows(IOException.class, () -> ItemContentConfig.parse("{\"version\":1,\"items\":[{\"id\":\"other:plate\",\"name\":\"x\"}]}"));
        assertThrows(IOException.class, () -> ItemContentConfig.parse("{\"version\":1,\"items\":[{\"id\":\"plate\",\"name\":\"x\"},{\"id\":\"plate\",\"name\":\"y\"}]}"));
    }

    @Test void rejectsInvalidItemPropertiesAndUnknownKeys() {
        assertThrows(IOException.class, () -> ItemContentConfig.parse("{\"version\":1,\"items\":[{\"id\":\"plate\",\"name\":\"x\",\"unexpected\":true}]}"));
        assertThrows(IOException.class, () -> ItemContentConfig.parse("{\"version\":1,\"items\":[{\"id\":\"plate\",\"name\":\"x\",\"max_count\":0}]}"));
    }
    @Test void rejectsMalformedTypesWithoutLeakingRuntimeExceptions() {
        for (String json : new String[]{"null", "[]", "1", "{\"version\":1,\"items\":null}",
                "{\"version\":1,\"items\":[null]}",
                "{\"version\":1,\"items\":[{\"id\":\"plate\",\"name\":\"x\",\"fields\":null}]}"})
            assertThrows(IOException.class, () -> ItemContentConfig.parse(json), json);
    }

    @Test void rejectsTruncatedIntegersAndCoercedBooleanValues() {
        for (String property : new String[]{"\"max_count\":16.5", "\"max_count\":4294967297",
                "\"max_count\":\"16\"", "\"fireproof\":\"true\"", "\"custom_model\":1"})
            assertThrows(IOException.class, () -> ItemContentConfig.parse(
                    "{\"version\":1,\"items\":[{\"id\":\"plate\",\"name\":\"x\"," + property + "}]}"), property);
        assertThrows(IOException.class, () -> ItemContentConfig.parse("{\"version\":1.5,\"items\":[]}"));
    }

}
