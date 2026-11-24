package pl.dawcou.astrars.gates.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

class BooleanUtils {
    static boolean readBoolean(JsonObject json, String key, boolean fallback) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) return fallback;
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        return primitive.isBoolean() ? primitive.getAsBoolean() : fallback;
    }

    static void setBooleanIfChanged(JsonObject json, String key, boolean value) {
        JsonElement previous = json.get(key);
        if (previous == null || !previous.isJsonPrimitive() || !previous.getAsJsonPrimitive().isBoolean() || previous.getAsBoolean() != value) {
            json.addProperty(key, value);
        }
    }
}