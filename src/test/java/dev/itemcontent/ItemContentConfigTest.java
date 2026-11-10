package dev.itemcontent;

import dev.itemcontent.config.ItemContentConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

final class ItemContentConfigTest {
    @Test void parsesShortIdsAndFields() throws Exception {
        var items = ItemContentConfig.parse("""
                {"version":1,"items":[{"id":"plate","name":"Plate","custom_model":true,"fields":["mass",{"id":"hardness","default":25.00}]}]}
                """);
        assertEquals(1, items.size());
        assertEquals("itemcontent:plate", items.get(0).id().toString());
        assertEquals("itemcontent:mass", items.get(0).fields().get(0).id());
        assertTrue(items.get(0).customModel());
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

    @Test void rejectsDuplicateAndInvalidParameterIdsAndMisspelledKeys() {
        for (String fields : new String[]{"[\"mass\",\"itemcontent:mass\"]", "[\"bad field\"]",
                "[{\"id\":\"mass\",\"defualt\":5}]", "[{\"id\":\"mass\",\"default\":\"5\"}]"})
            assertThrows(IOException.class, () -> ItemContentConfig.parse(
                    "{\"version\":1,\"items\":[{\"id\":\"plate\",\"name\":\"x\",\"fields\":" + fields + "}]}"), fields);
    }

    @Test void preservesBothInt32BoundariesExactly() throws Exception {
        var fields = ItemContentConfig.parse("""
                {"version":1,"items":[{"id":"plate","name":"x","fields":[
                {"id":"low","default":-21474836.48}, {"id":"high","default":21474836.47}]}]}
                """).get(0).fields();
        assertEquals(Integer.MIN_VALUE, fields.get(0).defaultValue().movePointRight(2).intValueExact());
        assertEquals(Integer.MAX_VALUE, fields.get(1).defaultValue().movePointRight(2).intValueExact());
    }
}
