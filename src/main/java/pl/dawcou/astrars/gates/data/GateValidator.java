package pl.dawcou.astrars.gates.data;

import com.google.gson.JsonObject;
import org.bukkit.block.BlockFace;

public class GateValidator {

    public boolean isValid(JsonObject gate) {
        if (gate == null) return false;

        String type = gate.has("type") ? gate.get("type").getAsString().toUpperCase() : "";

        // 1. Podstawowa walidacja istnienia typu
        if (type.isEmpty()) return false;

        // Jeśli gracz ma błąd w pliku, wyłapujemy to tutaj i bezpiecznie odrzucamy bramkę
        String outName = gate.has("out") ? gate.get("out").getAsString().toUpperCase() : "NORTH";
        try {
            BlockFace.valueOf(outName);
        } catch (IllegalArgumentException e) {
            return false;
        }

        // 3. Szczegółowa walidacja parametrów dla konkretnych typów bramek
        switch (type) {
            // ==========================================
            // BRAMKI LICZBOWE (NumberGates)
            // ==========================================
            case "COUNTER" -> {
                int limit = gate.has("score_limit") ? gate.get("score_limit").getAsInt() : 15;
                if (limit < 1 || limit > 1000) return false;
            }
            case "NUMBER_GATE", "DECODER" -> {
                if (!gate.has("value")) return false;
            }
            case "MATH" -> {
                // Sprawdzamy czy tryb działania jest prawidłowy
                String mode = gate.has("mode") ? gate.get("mode").getAsString().toUpperCase() : "ADD";
                if (!mode.matches("ADD|SUB|-|MUL|\\*|DIV|/|POW|\\^")) return false;
            }
            case "COMPARATOR" -> {
                String op = gate.has("mode") ? gate.get("mode").getAsString() : "==";
                if (!op.matches(">|<|==|!=|>=|<=")) return false;
            }
            case "RANDOM_NUMBER" -> {
                if (!gate.has("min") || !gate.has("max")) return false;
                int min = gate.get("min").getAsInt();
                int max = gate.get("max").getAsInt();
                if (min > max) return false;
            }

            // ==========================================
            // BRAMKI TEKSTOWE (StringGates)
            // ==========================================
            case "STRING_GATE", "STRING_DECODER" -> {
                if (!gate.has("value")) return false;
            }
            case "STRING_COMPARATOR" -> {
                String mode = gate.has("mode") ? gate.get("mode").getAsString().toUpperCase() : "EQUALS";
                if (!mode.matches("EQUALS|EQUALS_IGNORE_CASE|CONTAINS|STARTS_WITH|ENDS_WITH|EMPTY")) return false;
            }

            // ==========================================
            // POZOSTAŁE BRAMKI SYSTEMOWE
            // ==========================================
            case "SENSOR" -> {
                int radius = gate.has("radius") ? gate.get("radius").getAsInt() : 0;
                if (radius < 1 || radius > 15) return false;
            }
            case "REPEATER", "CLOCK", "CLOCK_GATE" -> {
                int interval = gate.has("interval") ? gate.get("interval").getAsInt() : 0;
                if (interval < 1 || interval > 200) return false;
            }
            case "SENDER", "RECEIVER" -> {
                String channel = gate.has("channel") ? gate.get("channel").getAsString() : "default";
                if (channel.isEmpty()) return false;
            }
        }

        return true;
    }
}