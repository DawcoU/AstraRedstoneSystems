package pl.dawcou.astrars.file;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.stream.MalformedJsonException;
import pl.dawcou.astrars.AstraRS;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GateDataManager {

    // ----------------------------------------------------------------------------------------------------
    // CONSTANTS & FIELDS
    // ----------------------------------------------------------------------------------------------------
    private static final Pattern LINE_COL_PATTERN = Pattern.compile("line (\\d+) column (\\d+)");

    private final AstraRS plugin;
    private final File file;
    private final Gson gson;
    private JsonObject root;
    private boolean loadedSuccessfully = false;

    public GateDataManager(AstraRS plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "gates.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.root = new JsonObject();
    }

    // ----------------------------------------------------------------------------------------------------
    // INITIALIZATION & I/O
    // ----------------------------------------------------------------------------------------------------
    public void load() {
        if (!file.exists()) {
            this.root = new JsonObject();
            this.loadedSuccessfully = true;
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            save(); // Tworzy świeży plik gates.json na dysku z pustą strukturą {}
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (parsed != null && parsed.isJsonObject()) {
                this.root = parsed.getAsJsonObject();
                this.loadedSuccessfully = true;
            } else {
                plugin.getLogger().warning("[GateDataManager] The file 'gates.json' does not contain a valid JSON Object. Resetting structure.");
                this.root = new JsonObject();
                this.loadedSuccessfully = false;
            }
        } catch (JsonParseException e) {
            this.root = new JsonObject();
            this.loadedSuccessfully = false; // Block save() from overwriting disk content
            logFormattedJsonError(e);
        } catch (IOException e) {
            plugin.getLogger().severe("[GateDataManager] Could not read 'gates.json' due to I/O error: " + e.getMessage());
            this.root = new JsonObject();
            this.loadedSuccessfully = false;
        }
    }

    public void save() {
        // Safe check: do NOT overwrite gates.json if the file was corrupted during load()
        if (!loadedSuccessfully) {
            plugin.getLogger().warning("[GateDataManager] Save skipped! The 'gates.json' file is corrupted or failed to load.");
            return;
        }

        synchronized (this) {
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(root, writer);
            } catch (IOException e) {
                plugin.getLogger().severe("[GateDataManager] Could not save 'gates.json': " + e.getMessage());
            }
        }
    }

    // ----------------------------------------------------------------------------------------------------
    // ERROR LOGGING HELPERS
    // ----------------------------------------------------------------------------------------------------
    private void logFormattedJsonError(JsonParseException e) {
        String message = e.getMessage();
        int line = -1;
        int column = -1;

        if (message != null) {
            Matcher matcher = LINE_COL_PATTERN.matcher(message);
            if (matcher.find()) {
                line = Integer.parseInt(matcher.group(1));
                column = Integer.parseInt(matcher.group(2));
            }
        }

        Throwable cause = e.getCause();
        if ((line == -1 || column == -1) && cause instanceof MalformedJsonException) {
            String causeMsg = cause.getMessage();
            if (causeMsg != null) {
                Matcher matcher = LINE_COL_PATTERN.matcher(causeMsg);
                if (matcher.find()) {
                    line = Integer.parseInt(matcher.group(1));
                    column = Integer.parseInt(matcher.group(2));
                }
            }
        }

        String details = extractErrorDetails(cause != null ? cause.getMessage() : message);

        plugin.getLogger().severe("==================================================");
        plugin.getLogger().severe("Failed to parse 'gates.json'!");
        if (line != -1 && column != -1) {
            plugin.getLogger().severe("  -> Syntax error at Line: " + line + ", Column: " + column);
        } else {
            plugin.getLogger().severe("  -> Syntax error location could not be calculated precisely.");
        }
        plugin.getLogger().severe("  -> Issue details: " + details);
        plugin.getLogger().severe("  -> File saving HAS BEEN BLOCKED to prevent wiping data!");
        plugin.getLogger().severe("==================================================");
    }

    private String extractErrorDetails(String rawMessage) {
        if (rawMessage == null || rawMessage.isEmpty()) {
            return "Malformed JSON syntax or invalid structure.";
        }

        if (rawMessage.contains("setStrictness") || rawMessage.contains("LENIENT")) {
            return "Invalid JSON header structure. Missing opening '{' or malformed root key.";
        } else if (rawMessage.contains("Expected name")) {
            return "Missing key or quote in JSON object structure.";
        } else if (rawMessage.contains("Unterminated object")) {
            return "Missing closing curly bracket '}' at end of object.";
        } else if (rawMessage.contains("Unterminated array")) {
            return "Missing closing square bracket ']' at end of array.";
        } else if (rawMessage.contains("Expected ':'")) {
            return "Missing colon ':' between key and value.";
        } else if (rawMessage.contains("Unterminated string")) {
            return "String quote '\"' was left unclosed.";
        } else if (rawMessage.contains("Expected value")) {
            return "Missing value or trailing comma ',' found.";
        }

        int atIndex = rawMessage.indexOf(" at line");
        if (atIndex != -1) {
            return rawMessage.substring(0, atIndex).trim();
        }

        return rawMessage;
    }

    // ----------------------------------------------------------------------------------------------------
    // GETTERS & SETTERS FOR JSON OBJECTS
    // ----------------------------------------------------------------------------------------------------
    public JsonObject getRoot() {
        return root;
    }

    public JsonObject getGates() {
        if (!root.has("gates") || !root.get("gates").isJsonObject()) {
            root.add("gates", new JsonObject());
        }
        return root.getAsJsonObject("gates");
    }

    public JsonObject getGate(String key) {
        JsonObject gates = getGates();
        if (gates.has(key) && gates.get(key).isJsonObject()) {
            return gates.getAsJsonObject(key);
        }
        return null;
    }

    public String getString(String gateKey, String path, String def) {
        JsonObject gate = getGate(gateKey);
        if (gate != null && gate.has(path) && gate.get(path).isJsonPrimitive()) {
            return gate.get(path).getAsString();
        }
        return def;
    }

    public boolean getBoolean(String gateKey, String path, boolean def) {
        JsonObject gate = getGate(gateKey);
        if (gate != null && gate.has(path) && gate.get(path).isJsonPrimitive()) {
            return gate.get(path).getAsBoolean();
        }
        return def;
    }

    public void setBoolean(String gateKey, String path, boolean value) {
        JsonObject gate = getGate(gateKey);
        if (gate != null) {
            gate.addProperty(path, value);
        }
    }

    public boolean isLoadedSuccessfully() {
        return loadedSuccessfully;
    }
}