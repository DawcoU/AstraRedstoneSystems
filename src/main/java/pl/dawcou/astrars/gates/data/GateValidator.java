
package pl.dawcou.astrars.gates.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.bukkit.block.BlockFace;

import java.util.Locale;
import java.util.Set;

public class GateValidator {

    public static final Set<String> MATH_MODES = Set.of(
            "ADD", "SUB", "MUL", "DIV", "POW", "MOD",
            "MIN", "MAX", "SQRT", "ROOT", "AVG", "ABS", "HYPOT");

    private static final Set<String> COMPARATOR_MODES =
            Set.of(">", "<", "==", "!=", ">=", "<=");

    private static final Set<String> STRING_COMPARATOR_MODES =
            Set.of("EQUALS", "EQUALS_IGNORE_CASE", "CONTAINS",
                    "STARTS_WITH", "ENDS_WITH", "EMPTY");

    public boolean isValid(JsonObject gate) {
        if (gate == null) return false;

        try {
            String type = gate.has("type")
                    ? gate.get("type").getAsString().toUpperCase(Locale.ROOT)
                    : "";

            if (type.isEmpty()) return false;

            String outName = gate.has("out")
                    ? gate.get("out").getAsString().toUpperCase(Locale.ROOT)
                    : "NORTH";

            try {
                BlockFace.valueOf(outName);
            } catch (IllegalArgumentException e) {
                return false;
            }

            switch (type) {
                case "COUNTER" -> {
                    int limit = gate.has("score_limit")
                            ? gate.get("score_limit").getAsInt() : 15;

                    if (limit < 1 || limit > 1000) return false;
                }

                case "NUMBER_SENDER", "DECODER" -> {
                    if (!hasFiniteNumber(gate, "value")) return false;
                }

                case "MATH" -> {
                    String mode = gate.has("mode")
                            ? gate.get("mode").getAsString().toUpperCase(Locale.ROOT)
                            : "ADD";

                    if (!MATH_MODES.contains(mode)) return false;
                }

                case "COMPARATOR" -> {
                    String mode = gate.has("mode")
                            ? gate.get("mode").getAsString()
                            : "==";

                    if (!COMPARATOR_MODES.contains(mode)) return false;
                }

                case "RANDOM_NUMBER" -> {
                    if (!hasFiniteNumber(gate, "min")
                            || !hasFiniteNumber(gate, "max")) {
                        return false;
                    }
                }

                case "STRING_SENDER", "STRING_DECODER" -> {
                    if (!gate.has("value")
                            || gate.get("value").isJsonNull()) {
                        return false;
                    }
                }

                case "STRING_COMPARATOR" -> {
                    String mode = gate.has("mode")
                            ? gate.get("mode").getAsString().toUpperCase(Locale.ROOT)
                            : "EQUALS";

                    if (!STRING_COMPARATOR_MODES.contains(mode)) return false;
                }

                case "SENSOR" -> {
                    int radius = gate.has("radius")
                            ? gate.get("radius").getAsInt() : 0;

                    if (radius < 1 || radius > 15) return false;
                }

                case "REPEATER", "CLOCK" -> {
                    int interval = gate.has("interval")
                            ? gate.get("interval").getAsInt() : 0;

                    if (interval < 1 || interval > 200) return false;
                }

                case "SENDER", "RECEIVER" -> {
                    String channel = gate.has("channel")
                            ? gate.get("channel").getAsString() : "default";

                    if (channel.isEmpty()) return false;
                }
            }

            return true;
        } catch (RuntimeException e) {
            // Uszkodzone dane JSON nie mogą przerwać ładowania bramek.
            return false;
        }
    }

    private boolean hasFiniteNumber(JsonObject gate, String key) {
        if (!gate.has(key)) return false;

        JsonElement element = gate.get(key);

        if (element == null || element.isJsonNull()
                || !element.isJsonPrimitive()) {
            return false;
        }

        JsonPrimitive primitive = element.getAsJsonPrimitive();

        if (!primitive.isNumber()) return false;

        double value = primitive.getAsDouble();
        return Double.isFinite(value);
    }
}
