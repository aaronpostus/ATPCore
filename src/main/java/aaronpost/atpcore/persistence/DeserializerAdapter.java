package aaronpost.atpcore.persistence;

import com.google.gson.*;

import java.lang.reflect.Type;

/**
 * Polymorphic Gson adapter using a {type, properties} envelope. The concrete
 * class is resolved as {@code path + type + typeExtension} via Class.forName,
 * so the consuming plugin passes its own package prefix.
 */
public class DeserializerAdapter<T> implements JsonSerializer<T>, JsonDeserializer<T> {
    private final String path, typeExtension;

    public DeserializerAdapter(String path) {
        this.path = path;
        this.typeExtension = "";
    }

    public DeserializerAdapter(String path, String typeExtension) {
        this.path = path;
        this.typeExtension = typeExtension;
    }

    @Override
    public JsonElement serialize(T src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject result = new JsonObject();
        String name = src.getClass().getSimpleName();
        if (!typeExtension.isEmpty() && name.endsWith(typeExtension)) {
            name = name.substring(0, name.length() - typeExtension.length());
        }
        result.add("type", new JsonPrimitive(name));
        result.add("properties", context.serialize(src, src.getClass()));

        return result;
    }

    @Override
    public T deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        String type = jsonObject.get("type").getAsString() + typeExtension;
        JsonElement element = jsonObject.get("properties");

        try {
            return context.deserialize(element, Class.forName(path + type));
        } catch (ClassNotFoundException cnfe) {
            throw new JsonParseException("Unknown element type: " + type, cnfe);
        }
    }
}
