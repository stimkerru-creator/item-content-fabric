package dev.itemcontent.editor;

import net.minecraft.client.render.model.json.JsonUnbakedModel;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Compare editor acceptance with the actual 1.20.1 deserializer, not a mock parser. */
class NativeModelValidationTest {
    private static String resource(String name) throws Exception {
        try (var input = NativeModelValidationTest.class.getResourceAsStream("/model-validation/" + name)) {
            assertNotNull(input, name); return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    @Test void syntaxAndValidEdgeCasesAgreeWithMinecraft() throws Exception {
        int checked = 0;
        for (String line : resource("cases.txt").lines().toList()) {
            String[] parts = line.split(" "); String name = parts[0], text = resource(name + ".json");
            boolean nativeAccepts = parts[1].equals("accept");
            if (nativeAccepts) assertDoesNotThrow(() -> JsonUnbakedModel.deserialize(text), name);
            else assertThrows(RuntimeException.class, () -> JsonUnbakedModel.deserialize(text), name);
            boolean supported = nativeAccepts && !Set.of("empty-object", "parent-empty").contains(name);
            MinecraftModel.Source source = new MinecraftModel.Source() {
                public List<String> models() { return List.of("audit:root"); }
                public byte[] read(String path) { return path.equals("assets/audit/models/root.json") ? text.getBytes(StandardCharsets.UTF_8) : null; }
            };
            if (supported) assertNotNull(MinecraftModel.load(source, "audit:root"), name);
            else assertThrows(IOException.class, () -> MinecraftModel.load(source, "audit:root"), name);
            checked++;
        }
        assertEquals(48, checked);
    }
}
