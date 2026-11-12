package dev.itemcontent;
import dev.itemcontent.config.*;
import dev.itemcontent.api.*;
import dev.itemcontent.definition.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class ExtendedConfigTest {
    @Test void typedDefinitionsLoadDefaultsAndRetainVersionOneSupport() throws Exception {
        for (var type : BuiltinTypes.all()) {
            var item = ItemContentConfig.parse("{\"version\":2,\"items\":[{\"id\":\"typed\",\"name\":\"Typed\",\"type\":\""+type.id()+"\"}]}").get(0);
            assertEquals(type.id(),item.type()); assertEquals(type.properties().size(),item.properties().size());
            if(item.properties().has("durability") && item.integer("durability")>0) assertEquals(1,item.maxCount());
        }
        assertEquals("itemcontent:item",ItemContentConfig.parse("{\"version\":1,\"items\":[{\"id\":\"old\",\"name\":\"Old\"}]}").get(0).type());
    }
    @Test void existingOverrideIsExplicitAndRejectsUnsupportedFields() throws Exception {
        String prefix="{\"version\":2,\"items\":[{\"id\":\"minecraft:iron_sword\",\"name\":\"Sword\",\"type\":\"itemcontent:sword\",\"existing\":true,\"max_count\":1,\"properties\":";
        assertEquals(500,ItemContentConfig.parse(prefix+"{\"durability\":500}}]}").get(0).integer("durability"));
        assertThrows(java.io.IOException.class,()->ItemContentConfig.parse(prefix+"{\"attack_damage\":500}}]}"));
        assertThrows(java.io.IOException.class,()->ItemContentConfig.parse("{\"version\":2,\"groups\":[{\"id\":\"itemcontent:keep\",\"name\":\"Reserved\"}],\"items\":[]}"));
    }
    @Test void optionalProviderCanRegisterAndValidateItsOwnSettings() throws Exception {
        var types=ContentTypeRegistry.getInstance();
        var definition=new TypeDefinition("test:provider", "Provider", "Test", "minecraft:combat", "handheld", false,
            List.of(PropertyDefinition.number("fire_rate","Fire rate",5,1,100,true,"")), "");
        ContentFactory provider=(spec,settings)->{throw new AssertionError("Parsing must not instantiate game content");};
        types.register(definition,provider); assertSame(provider,types.factory(definition.id()));
        var item=ItemContentConfig.parse("{\"version\":2,\"items\":[{\"id\":\"custom\",\"name\":\"Custom\",\"type\":\"test:provider\",\"properties\":{\"fire_rate\":12.5}}]}").get(0);
        assertEquals(12.5,item.properties().get("fire_rate").getAsDouble());
        assertThrows(IllegalArgumentException.class,()->types.register(definition,provider));
        assertThrows(java.io.IOException.class,()->ItemContentConfig.parse("{\"version\":2,\"items\":[{\"id\":\"custom\",\"name\":\"Custom\",\"type\":\"missing:provider\"}]}"));
    }
}
