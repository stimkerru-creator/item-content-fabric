package dev.itemcontent.api;

/** Register under the custom Fabric entrypoint "itemcontent:extensions". */
public interface ItemContentExtension {
    void register(ContentTypeRegistry registry);
}
