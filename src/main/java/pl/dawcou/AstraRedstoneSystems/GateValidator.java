package pl.dawcou.AstraRedstoneSystems;

import org.bukkit.configuration.file.FileConfiguration;

public class GateValidator {

    private final AstraRS plugin;

    public GateValidator(AstraRS plugin) {
        this.plugin = plugin;
    }

    public static boolean isValid(String path, FileConfiguration config) {
        String type = config.getString(path + ".type", "").toUpperCase();

        // Podstawowa walidacja typu
        if (type.isEmpty()) return false;

        switch (type) {
            case "COUNTER" -> {
                int limit = config.getInt(path + ".score_limit");
                if (limit < 1 || limit > 100) return false;
            }
            case "NUMBER_GATE" -> {
                int value = config.getInt(path + ".value", -1);
                if (value < 0) return false;
            }
            case "RANDOM_NUMBER" -> {
                if (!config.contains(path + ".min") || !config.contains(path + ".max")) return false;
                int min = config.getInt(path + ".min");
                int max = config.getInt(path + ".max");
                if (min < 0 || max < 0 || min > max) return false;
            }
            case "COMPARATOR" -> {
                String op = config.getString(path + ".operator", "");
                if (!op.matches(">|<|==|!=|>=|<=")) return false;
            }
            case "SENSOR" -> {
                int radius = config.getInt(path + ".interval");
                if (radius < 1 || radius > 15) return false;
            }
            case "CLOCK", "CLOCK_GATE" -> {
                int interval = config.getInt(path + ".interval");
                if (interval < 5 || interval > 200) return false;
            }
            case "REPEATER" -> {
                int interval = config.getInt(path + ".interval");
                if (interval < 1 || interval > 200) return false;
            }
            case "SENDER", "RECEIVER" -> {
                String channel = config.getString(path + ".channel");
                if (channel == null || channel.isEmpty()) return false;
            }
        }

        return true; // Jeśli bramka przeszła testy lub nie wymaga specyficznej walidacji
    }
}