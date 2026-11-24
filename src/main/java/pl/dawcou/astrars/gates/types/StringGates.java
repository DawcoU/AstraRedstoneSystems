package pl.dawcou.astrars.gates.types;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.bukkit.Bukkit;

import pl.dawcou.astrars.AstraRS;
import pl.dawcou.astrars.file.GateDataManager;
import pl.dawcou.astrars.gates.data.GateContextFactory;
import pl.dawcou.astrars.gates.data.GateValidator;
import pl.dawcou.astrars.gates.utils.GateUtils;

import java.util.Map;
import java.util.Set;

public class StringGates {
    private final AstraRS plugin;
    private final GateValidator validator;

    private static final Set<String> HANDLED_TYPES = Set.of(
            "STRING_SENDER", "STRING_COMPARATOR", "STRING_DECODER"
    );

    private static final Set<String> OUT_GATES = Set.of(
            "STRING_SENDER", "STRING_COMPARATOR", "STRING_DECODER"
    );

    private static final Set<String> BACK_GATES = Set.of(
            "STRING_SENDER", "STRING_DECODER"
    );

    public StringGates(AstraRS plugin, GateValidator validator) {
        this.plugin = plugin;
        this.validator = validator;
    }

    // --------------------------------------------------
    // GŁÓWNA PĘTLA
    // --------------------------------------------------

    public void runStringGates() {
        GateDataManager manager = plugin.getGateDataManager();
        JsonObject gatesSection = manager.getGates();

        if (gatesSection == null) return;

        for (Map.Entry<String, JsonElement> entry : gatesSection.entrySet()) {
            GateContextFactory.createAndRun(entry, validator, plugin, ctx -> {
                if (ctx == null) return;

                if (!HANDLED_TYPES.contains(ctx.type())) return;

                JsonObject gateObj = ctx.json();
                String type = ctx.type();
                String key = ctx.key();

                String sL = null;
                String sR = null;
                String sBack = null;

                // --- EFEKTY WIZUALNE ---

                // 1. EFEKT WYJŚCIA
                if (OUT_GATES.contains(type)) {
                    GateUtils.spawnStatusParticle(
                            ctx.gateBlock(),
                            ctx.dirs().out(),
                            ctx.currentState()
                    );
                }

                // 2. EFEKT WEJŚCIA Z TYŁU
                if (BACK_GATES.contains(type)) {
                    sBack = GateUtils.getStringFrom(
                            ctx.dirs().backBlock(), plugin
                    );

                    // Pusty tekst jest prawidłowym sygnałem danych.
                    GateUtils.spawnStatusParticle(
                            ctx.gateBlock(),
                            ctx.dirs().back(),
                            ctx.pBack() || sBack != null
                    );
                }

                // 3. EFEKTY WEJŚĆ BOCZNYCH
                if (type.equals("STRING_COMPARATOR")) {
                    sL = GateUtils.getStringFrom(
                            ctx.dirs().leftBlock(), plugin
                    );

                    sR = GateUtils.getStringFrom(
                            ctx.dirs().rightBlock(), plugin
                    );

                    GateUtils.spawnStatusParticle(
                            ctx.gateBlock(),
                            ctx.dirs().left(),
                            sL != null
                    );

                    GateUtils.spawnStatusParticle(
                            ctx.gateBlock(),
                            ctx.dirs().right(),
                            sR != null
                    );
                }

                // --- LOGIKA BRAMEK ---
                switch (type) {
                    case "STRING_SENDER" -> {
                        String storedText = gateObj.has("value")
                                ? gateObj.get("value").getAsString()
                                : "";

                        boolean enabled = ctx.pBack();

                        JsonElement result = enabled
                                ? new JsonPrimitive(storedText)
                                : JsonNull.INSTANCE;

                        boolean outputChanged = GateUtils.setOutputIfChanged(
                                gateObj, result
                        );

                        if (outputChanged && plugin.isDebugEnabled()) {
                            Bukkit.getConsoleSender().sendMessage(
                                    AstraRS.DEBUG_PREFIX
                                            + "§dSTRING_SENDER §7at §e" + key
                                            + " §7emitted text: "
                                            + (enabled
                                            ? "§5\"" + storedText + "\""
                                            : "§7NO DATA")
                            );
                        }

                        GateUtils.syncOutput(
                                plugin,
                                gateObj,
                                ctx,
                                key,
                                enabled,
                                outputChanged
                        );
                    }

                    case "STRING_COMPARATOR" -> {
                        String mode = gateObj.has("mode")
                                ? gateObj.get("mode").getAsString().toUpperCase()
                                : "EQUALS";

                        // Brak któregokolwiek wejścia oznacza brak wyniku.
                        JsonElement resultElement = JsonNull.INSTANCE;
                        boolean result = false;

                        if (sL != null && sR != null) {
                            result = switch (mode) {
                                case "EQUALS" ->
                                        sL.equals(sR);

                                case "EQUALS_IGNORE_CASE" ->
                                        sL.equalsIgnoreCase(sR);

                                case "CONTAINS" ->
                                        sL.contains(sR);

                                case "STARTS_WITH" ->
                                        sL.startsWith(sR);

                                case "ENDS_WITH" ->
                                        sL.endsWith(sR);

                                default -> false;
                            };

                            resultElement = new JsonPrimitive(result);
                        }

                        boolean outputChanged = GateUtils.setOutputIfChanged(
                                gateObj, resultElement
                        );

                        if (outputChanged && plugin.isDebugEnabled()) {
                            String status = resultElement.isJsonNull()
                                    ? "§7NO DATA"
                                    : result ? "§aTRUE" : "§cFALSE";

                            Bukkit.getConsoleSender().sendMessage(
                                    AstraRS.DEBUG_PREFIX
                                            + "§dSTRING_COMP §7at §e" + key
                                            + " §7result (§6" + mode + "§7): "
                                            + status
                            );
                        }

                        GateUtils.syncOutput(
                                plugin,
                                gateObj,
                                ctx,
                                key,
                                result,
                                outputChanged
                        );
                    }

                    case "STRING_DECODER" -> {
                        String targetValue = gateObj.has("value")
                                ? gateObj.get("value").getAsString()
                                : "";

                        boolean isMatch = sBack != null
                                && sBack.equals(targetValue);

                        JsonElement resultElement = sBack == null
                                ? JsonNull.INSTANCE
                                : new JsonPrimitive(isMatch);

                        boolean outputChanged = GateUtils.setOutputIfChanged(
                                gateObj, resultElement
                        );

                        if (outputChanged && plugin.isDebugEnabled()) {
                            String status = sBack == null
                                    ? "§7NO DATA"
                                    : isMatch ? "§aMATCH" : "§cNO MATCH";

                            Bukkit.getConsoleSender().sendMessage(
                                    AstraRS.DEBUG_PREFIX
                                            + "§dSTRING_DECODER §7at §e" + key
                                            + " §7(target: §5\"" + targetValue
                                            + "\"§7, received: "
                                            + (sBack == null
                                            ? "§7NO DATA"
                                            : "§5\"" + sBack + "\"")
                                            + "§7) -> " + status
                            );
                        }

                        GateUtils.syncOutput(
                                plugin,
                                gateObj,
                                ctx,
                                key,
                                isMatch,
                                outputChanged
                        );
                    }
                }
            });
        }
    }
}