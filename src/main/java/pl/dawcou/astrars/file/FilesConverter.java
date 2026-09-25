package pl.dawcou.astrars.file;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.dawcou.astrars.AstraRS;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class FilesConverter {

    // ----------------------------------------------------------------------------------------------------
    // CONSTANTS & FIELDS
    // ----------------------------------------------------------------------------------------------------
    private final AstraRS plugin;
    private final Gson gson;

    // ----------------------------------------------------------------------------------------------------
    // CONSTRUCTOR
    // ----------------------------------------------------------------------------------------------------
    public FilesConverter(AstraRS plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    // ----------------------------------------------------------------------------------------------------
    // MAIN MIGRATION CONTROL
    // ----------------------------------------------------------------------------------------------------
    public void runAllMigrations() {
        migrateYamlToJson();
    }

    // ----------------------------------------------------------------------------------------------------
    // MIGRATION LOGIC (YAML -> JSON)
    // ----------------------------------------------------------------------------------------------------
    private void migrateYamlToJson() {
        File oldYamlFile = new File(plugin.getDataFolder(), "logic_gates.yml");
        File newJsonFile = new File(plugin.getDataFolder(), "gates.json");

        // Jeśli stary YAML istnieje, a nowy JSON jeszcze nie powstał - wykonujemy migrację
        if (oldYamlFile.exists() && !newJsonFile.exists()) {
            plugin.getNoticeManager().sendMigrationNotice("logic_gates.yml", "gates.json");

            try {
                FileConfiguration yamlConfig = YamlConfiguration.loadConfiguration(oldYamlFile);
                JsonObject rootJson = new JsonObject();

                // Przepisujemy całą strukturę YAMLa do JSON
                for (String key : yamlConfig.getKeys(false)) {
                    if (yamlConfig.isConfigurationSection(key)) {
                        ConfigurationSection section = yamlConfig.getConfigurationSection(key);
                        if (section != null) {
                            rootJson.add(key, convertSectionToJson(section));
                        }
                    } else {
                        addPropertyToJson(rootJson, key, yamlConfig.get(key));
                    }
                }

                // Zapisujemy wygenerowany plik gates.json
                try (FileWriter writer = new FileWriter(newJsonFile)) {
                    gson.toJson(rootJson, writer);
                }

                // Robimy bezpieczną kopię logic_gates.yml.old, a oryginał zostawiamy/zmieniamy rozszerzenie
                File backupFile = new File(plugin.getDataFolder(), "logic_gates.yml.old");
                if (oldYamlFile.renameTo(backupFile)) {
                    plugin.getNoticeManager().sendSuccessNotice("logic_gates.yml -> gates.json");
                }

            } catch (IOException e) {
                plugin.getNoticeManager().sendErrorNotice("logic_gates.yml");
                e.printStackTrace();
            }
        }
    }

    // ----------------------------------------------------------------------------------------------------
    // HELPER CONVERSION METHODS
    // ----------------------------------------------------------------------------------------------------
    private JsonObject convertSectionToJson(ConfigurationSection section) {
        JsonObject jsonObject = new JsonObject();

        for (String key : section.getKeys(false)) {
            if (section.isConfigurationSection(key)) {
                ConfigurationSection subSection = section.getConfigurationSection(key);
                if (subSection != null) {
                    jsonObject.add(key, convertSectionToJson(subSection));
                }
            } else {
                Object value = section.get(key);

                // Automatyczna zamiana VARIABLE_GATE -> DISK_GATE
                if ("type".equals(key) && "VARIABLE_GATE".equals(value)) {
                    value = "DISK_GATE";
                }

                addPropertyToJson(jsonObject, key, value);
            }
        }

        return jsonObject;
    }

    @SuppressWarnings("unchecked")
    private void addPropertyToJson(JsonObject jsonObject, String key, Object value) {
        if (value == null) return;

        if (value instanceof Number) {
            jsonObject.addProperty(key, (Number) value);
        } else if (value instanceof Boolean) {
            jsonObject.addProperty(key, (Boolean) value);
        } else if (value instanceof String) {
            jsonObject.addProperty(key, (String) value);
        } else if (value instanceof List) {
            JsonArray jsonArray = new JsonArray();
            List<?> list = (List<?>) value;
            for (Object item : list) {
                if (item instanceof Map) {
                    // W razie skomplikowanych list obiektów w YAML
                    JsonObject mapObj = new JsonObject();
                    Map<String, Object> map = (Map<String, Object>) item;
                    map.forEach((k, v) -> addPropertyToJson(mapObj, k, v));
                    jsonArray.add(mapObj);
                } else if (item instanceof Number) {
                    jsonArray.add((Number) item);
                } else if (item instanceof Boolean) {
                    jsonArray.add((Boolean) item);
                } else {
                    jsonArray.add(String.valueOf(item));
                }
            }
            jsonObject.add(key, jsonArray);
        } else {
            jsonObject.addProperty(key, value.toString());
        }
    }
}