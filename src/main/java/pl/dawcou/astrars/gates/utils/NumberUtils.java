package pl.dawcou.astrars.gates.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;

class NumberUtils {

    private static final double ULP_TOLERANCE = 4.0;

    static Long parseLong(JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isNumber()) {
            return null;
        }
        try {
            return new BigDecimal(primitive.getAsString()).longValueExact();
        } catch (NumberFormatException | ArithmeticException ignored) {
            return null;
        }
    }

    static Long readLongOrNull(JsonObject json, String key) {
        return parseLong(json.get(key));
    }

    static long readLong(JsonObject json, String key, long fallback) {
        Long value = readLongOrNull(json, key);
        return value != null ? value : fallback;
    }

    static Double parseDouble(JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isNumber()) {
            return null;
        }
        try {
            double value = primitive.getAsDouble();
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static Double readDoubleOrNull(JsonObject json, String key) {
        return parseDouble(json.get(key));
    }

    static double readDouble(JsonObject json, String key, double fallback) {
        Double value = readDoubleOrNull(json, key);
        return value != null ? value : fallback;
    }

    static String readString(JsonObject json, String key, String fallback) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return fallback;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        return primitive.isString() ? primitive.getAsString() : fallback;
    }

    static void setLongIfChanged(JsonObject json, String key, long value) {
        Long previous = readLongOrNull(json, key);
        if (previous == null || previous.longValue() != value) {
            json.addProperty(key, value);
        }
    }

    static void setNullIfChanged(JsonObject json, String key) {
        JsonElement previous = json.get(key);
        if (previous == null || !previous.isJsonNull()) {
            json.add(key, JsonNull.INSTANCE);
        }
    }

    static long positiveDifference(long current, long previous) {
        try {
            return Math.subtractExact(current, previous);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    static double randomDouble(double min, double max) {
        if (min > max) {
            double temp = min;
            min = max;
            max = temp;
        }
        if (min == max) {
            return min;
        }
        double random = ThreadLocalRandom.current().nextDouble();
        double result = min * (1.0 - random) + max * random;
        if (Double.isNaN(result) || result < min) return min;
        if (result > max) return max;
        return result;
    }

    static int compareDoubles(double left, double right) {
        if (left == right) {
            return 0;
        }
        double difference = Math.abs(left - right);
        double tolerance = ULP_TOLERANCE * Math.max(Math.ulp(left), Math.ulp(right));
        if (difference <= tolerance) {
            return 0;
        }
        return left < right ? -1 : 1;
    }
}