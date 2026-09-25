package pl.dawcou.astrars.gates.types;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.block.BlockFace;

import pl.dawcou.astrars.AstraRS;
import pl.dawcou.astrars.file.GateDataManager;
import pl.dawcou.astrars.gates.data.GateContext;
import pl.dawcou.astrars.gates.data.GateContextFactory;
import pl.dawcou.astrars.gates.data.GateValidator;
import pl.dawcou.astrars.gates.utils.GateUtils;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class NumberGates {
    private final AstraRS plugin;
    private final GateValidator validator;

    private static final Set<String> OUT_GATES = Set.of(
            "NUMBER_GATE", "COUNTER", "BOOLEAN_GATE", "MATH", "COMPARATOR",
            "DECODER", "RANDOM_BOOLEAN", "RANDOM_NUMBER", "DECIMAL_ACCUMULATOR", "DATA_LISTENER"
    );
    private static final Set<String> BACK_GATES = Set.of(
            "NUMBER_GATE", "BOOLEAN_GATE", "DECODER", "RANDOM_BOOLEAN",
            "RANDOM_NUMBER", "COUNTER", "DECIMAL_ACCUMULATOR", "DATA_LISTENER"
    );
    private static final Set<String> SIDE_GATES = Set.of("COUNTER", "MATH", "COMPARATOR", "DECIMAL_ACCUMULATOR");

    public NumberGates(AstraRS plugin, GateValidator validator) {
        this.plugin = plugin;
        this.validator = validator;
    }

    public void runNumberGates() {
        GateDataManager manager = plugin.getGateDataManager();
        JsonObject gatesSection = manager.getGates();
        if (gatesSection == null) return;

        for (Map.Entry<String, JsonElement> entry : gatesSection.entrySet()) {
            GateContext ctx = GateContextFactory.create(entry, validator);
            if (ctx == null) continue;

            JsonObject gateObj = ctx.json();
            String type = ctx.type();
            String key = ctx.key();

            // --- EFEKTY WIZUALNE STATUSU ---
            // 1. EFEKT WYJŚCIA (Wszystkie bramki logiczne oprócz kabla)
            if (OUT_GATES.contains(type)) {
                GateUtils.spawnStatusParticle(ctx.gateBlock(), ctx.dirs().out(), ctx.currentState());
            }

            // 2. EFEKT WEJŚCIA Z TYŁU
            if (BACK_GATES.contains(type)) {
                if (type.equals("COUNTER") || type.equals("DECIMAL_ACCUMULATOR")) {
                    long vBack = GateUtils.getNumberFrom(ctx.dirs().backBlock(), plugin);
                    // Świeci jeśli idzie liczba LUB prąd
                    GateUtils.spawnStatusParticle(ctx.gateBlock(), ctx.dirs().back(), vBack > 0 || ctx.pBack());
                } else {
                    GateUtils.spawnStatusParticle(ctx.gateBlock(), ctx.dirs().back(), ctx.pBack());
                }
            }

            // 3. EFEKT WEJŚĆ BOCZNYCH
            if (SIDE_GATES.contains(type)) {
                BlockFace faceL = ctx.dirs().left();
                BlockFace faceR = ctx.dirs().right();

                // LEWO
                long vL = GateUtils.getNumberFrom(ctx.dirs().leftBlock(), plugin);
                if (vL == 0 || vL == Long.MIN_VALUE) vL = GateUtils.getPowerAt(ctx.dirs().leftBlock());
                GateUtils.spawnStatusParticle(ctx.gateBlock(), faceL, vL > 0);

                // PRAWO
                long vR = GateUtils.getNumberFrom(ctx.dirs().rightBlock(), plugin);
                if (vR == 0 || vR == Long.MIN_VALUE) vR = GateUtils.getPowerAt(ctx.dirs().rightBlock());
                GateUtils.spawnStatusParticle(ctx.gateBlock(), faceR, vR > 0);
            }

            // --- LOGIKA BRAMEK ---
            switch (type) {
                case "NUMBER_GATE" -> {
                    long storedValue = gateObj.has("value") ? gateObj.get("value").getAsLong() : 0L;

                    // LOGIKA: Jeśli OFF -> Long.MIN_VALUE, Jeśli ON -> storedValue
                    long valToSend = ctx.pBack() ? storedValue : Long.MIN_VALUE;
                    String currentResStr = String.valueOf(valToSend);

                    String lastOutStr = gateObj.has("current_out") ? gateObj.get("current_out").getAsString() : String.valueOf(Long.MIN_VALUE);

                    if (!currentResStr.equals(lastOutStr)) {
                        gateObj.addProperty("current_out", currentResStr);
                        gateObj.addProperty("state", ctx.pBack());
                        // GateUtils musi umieć odebrać longa i przekazać go dalej
                        GateUtils.updateOutput(plugin, key, ctx.dirs().targetBlock(), ctx.pBack());
                    }
                }

                case "BOOLEAN_GATE" -> {
                    boolean hasPower = ctx.pBack();
                    long valueToSend = hasPower ? 1L : 0L;

                    // 1. Przygotowujemy uniwersalny String systemowy
                    String currentResStr = String.valueOf(valueToSend);

                    // 2. Pobieramy poprzednią wartość jako STRING (konsekwentnie!)
                    String lastOutStr = gateObj.has("current_out") ? gateObj.get("current_out").getAsString() : "";

                    // 3. Porównujemy String z Stringiem, żeby uniknąć wiecznej pętli
                    if (!currentResStr.equals(lastOutStr)) {

                        if (plugin.isDebugMode()) {
                            Bukkit.getConsoleSender().sendMessage(
                                    AstraRS.DEBUG_PREFIX + "§3BOOLEAN_GATE §7at §e" + key + " §7-> State: " + (hasPower ? "§aENABLED (1)" : "§cDISABLED (0)")
                            );
                        }

                        // Zapisujemy jako String – bezpiecznie dla reszty sieci kabli danych
                        gateObj.addProperty("current_out", currentResStr);
                        gateObj.addProperty("state", hasPower);

                        GateUtils.updateOutput(plugin, key, ctx.dirs().targetBlock(), hasPower);
                    }
                }

                case "COUNTER" -> {
                    // --- POBIERANIE DANYCH ---
                    long dataBack = GateUtils.getNumberFrom(ctx.dirs().backBlock(), plugin);
                    long dataLeft = GateUtils.getNumberFrom(ctx.dirs().leftBlock(), plugin);

                    // NORMALIZACJA:
                    if (dataBack == Long.MIN_VALUE) dataBack = 0;
                    if (dataLeft == Long.MIN_VALUE) dataLeft = 0;

                    boolean pB = ctx.pBack();
                    boolean pL = ctx.pLeft();
                    boolean pR = ctx.pRight();

                    // --- POBIERANIE POPRZEDNICH STANÓW (Z JSON) ---
                    long lastDataBack = gateObj.has("last_data_back") ? gateObj.get("last_data_back").getAsLong() : 0L;
                    long lastDataLeft = gateObj.has("last_data_left") ? gateObj.get("last_data_left").getAsLong() : 0L;
                    boolean lB = gateObj.has("last_back") && gateObj.get("last_back").getAsBoolean();
                    boolean lL = gateObj.has("last_left") && gateObj.get("last_left").getAsBoolean();
                    boolean lR = gateObj.has("last_right") && gateObj.get("last_right").getAsBoolean();

                    long count = gateObj.has("count") ? gateObj.get("count").getAsLong() : 0L;
                    long limit = gateObj.has("score_limit") ? gateObj.get("score_limit").getAsLong() : 15L;
                    boolean changed = false;

                    // --- LOGIKA: DODAWANIE (TYŁ) ---
                    if (dataBack > lastDataBack) {
                        count = Math.min(limit, count + (dataBack - lastDataBack));
                        changed = true;
                    }
                    else if (pB && !lB && count < limit) {
                        count++;
                        changed = true;
                    }

                    // --- LOGIKA: ODEJMOWANIE (LEWO) ---
                    if (dataLeft > lastDataLeft) {
                        count = Math.max(0, count - (dataLeft - lastDataLeft));
                        changed = true;
                    }
                    else if (pL && !lL && count > 0) {
                        count--;
                        changed = true;
                    }

                    // --- LOGIKA: RESET (PRAWO) ---
                    if (pR && !lR) {
                        count = 0;
                        changed = true;
                    }

                    // --- ZAPIS I AKTUALIZACJA ---
                    if (changed) {
                        gateObj.addProperty("count", count);

                        boolean finalState = (count > 0);
                        boolean previousState = ctx.currentState();

                        if (finalState != previousState) {
                            gateObj.addProperty("state", finalState);
                            GateUtils.updateOutput(plugin, key, ctx.dirs().targetBlock(), finalState);
                        }

                        if (plugin.isDebugMode()) {
                            Bukkit.getConsoleSender().sendMessage(
                                    AstraRS.DEBUG_PREFIX + "§5COUNTER §7at §e" + key + " §7-> New counter state: §d§l" + count + "§7/§5" + limit
                            );
                        }

                        String countStr = String.valueOf(count);
                        gateObj.addProperty("current_out", countStr);
                        gateObj.addProperty("last_data_back", dataBack);
                        gateObj.addProperty("last_data_left", dataLeft);
                        gateObj.addProperty("last_back", pB);
                        gateObj.addProperty("last_left", pL);
                        gateObj.addProperty("last_right", pR);
                    }
                }

                case "MATH" -> {
                    // 1. POBIERANIE DANYCH
                    long vL_raw = GateUtils.getNumberFrom(ctx.dirs().leftBlock(), plugin);
                    long vR_raw = GateUtils.getNumberFrom(ctx.dirs().rightBlock(), plugin);

                    // 2. NORMALIZACJA - Sprawdzamy Long.MIN_VALUE
                    long vL = (vL_raw == Long.MIN_VALUE) ? 0L : vL_raw;
                    long vR = (vR_raw == Long.MIN_VALUE) ? 0L : vR_raw;

                    String m = gateObj.has("mode") ? gateObj.get("mode").getAsString().toUpperCase() : "ADD";

                    // 3. OBLICZENIA - Czyste operacje na longach
                    long result = switch (m) {
                        case "SUB", "-" -> vL - vR;
                        case "MUL", "*" -> vL * vR;
                        case "DIV", "/" -> (vR != 0) ? vL / vR : 0L;
                        case "POW", "^" -> (long) Math.pow(vL, vR);
                        default -> vL + vR;
                    };

                    // 4. ZAPIS I AKTUALIZACJA
                    String lastRes = gateObj.has("current_out") ? gateObj.get("current_out").getAsString() : "";
                    String currentResStr = String.valueOf(result);

                    if (!currentResStr.equals(lastRes)) {
                        if (plugin.isDebugMode()) {
                            Bukkit.getConsoleSender().sendMessage(
                                    AstraRS.DEBUG_PREFIX + "§d§lMATH §e" + key + " §7result: §a§l" + result
                            );
                        }
                        gateObj.addProperty("current_out", currentResStr);

                        boolean isActive = (result != 0L);
                        gateObj.addProperty("state", isActive);
                        GateUtils.updateOutput(plugin, key, ctx.dirs().targetBlock(), isActive);
                    }
                }

                case "DECIMAL_ACCUMULATOR" -> {
                    boolean resetL = ctx.pLeft();
                    boolean resetR = ctx.pRight();

                    // Pobieramy wartość
                    long inputVal = GateUtils.getNumberFrom(ctx.dirs().backBlock(), plugin);

                    // 1. Reset
                    if (resetL || resetR) {
                        gateObj.addProperty("current_out", "0");
                        gateObj.addProperty("last_input", Long.MIN_VALUE);
                        gateObj.addProperty("state", true);
                        GateUtils.updateOutput(plugin, key, ctx.dirs().targetBlock(), true);
                    }

                    // 2. Logika APPEND
                    else {
                        // Pobieramy ostatni stan (domyślnie brak sygnału)
                        long lastInput = gateObj.has("last_input") ? gateObj.get("last_input").getAsLong() : Long.MIN_VALUE;

                        if (inputVal >= 0 && inputVal <= 9) {
                            if (lastInput == Long.MIN_VALUE) {

                                String lastRes = gateObj.has("current_out") ? gateObj.get("current_out").getAsString() : "0";
                                long currentVal;
                                try {
                                    currentVal = Long.parseLong(lastRes);
                                } catch (Exception e) {
                                    currentVal = 0;
                                }

                                long result = (currentVal * 10L) + inputVal;

                                gateObj.addProperty("current_out", String.valueOf(result));
                                gateObj.addProperty("state", true);
                                GateUtils.updateOutput(plugin, key, ctx.dirs().targetBlock(), true);

                                // Zapisujemy, że ten sygnał został już przetworzony
                                gateObj.addProperty("last_input", inputVal);
                            }
                        }
                        // Jeśli sygnał zniknął, resetujemy "last_input", żeby pozwolić na kolejny impuls
                        else if (inputVal == Long.MIN_VALUE) {
                            long storedLastInput = gateObj.has("last_input") ? gateObj.get("last_input").getAsLong() : Long.MIN_VALUE;
                            if (storedLastInput != Long.MIN_VALUE) {
                                gateObj.addProperty("last_input", Long.MIN_VALUE);
                            }
                        }
                    }
                }

                case "COMPARATOR" -> {
                    long vL = GateUtils.getNumberFrom(ctx.dirs().leftBlock(), plugin);
                    long vR = GateUtils.getNumberFrom(ctx.dirs().rightBlock(), plugin);

                    // Jeśli lewe wejście (dane) to MIN_VALUE, od razu gasimy wyjście
                    if (vL == Long.MIN_VALUE) {
                        String currentOut = gateObj.has("current_out") ? gateObj.get("current_out").getAsString() : "";
                        if (!"MIN_VALUE".equals(currentOut)) {
                            gateObj.addProperty("current_out", "MIN_VALUE");
                            gateObj.addProperty("state", false);
                            // Przekazujemy false, bo brak danych = brak sygnału
                            GateUtils.updateOutput(plugin, key, ctx.dirs().targetBlock(), false);
                        }
                        break;
                    }

                    long valR = (vR == Long.MIN_VALUE) ? 0 : vR;
                    String m = gateObj.has("mode") ? gateObj.get("mode").getAsString() : "==";

                    boolean result = switch (m) {
                        case ">"  -> vL > valR;
                        case "<"  -> vL < valR;
                        case "==" -> vL == valR;
                        case ">=" -> vL >= valR;
                        case "<=" -> vL <= valR;
                        case "!=" -> vL != valR;
                        default   -> false;
                    };

                    long finalVal = result ? vL : Long.MIN_VALUE;
                    String currentResStr = String.valueOf(finalVal);
                    String lastOutStr = gateObj.has("current_out") ? gateObj.get("current_out").getAsString() : "";

                    if (!currentResStr.equals(lastOutStr)) {
                        if (plugin.isDebugMode()) {
                            String compColor = result ? "§a" : "§c";
                            Bukkit.getConsoleSender().sendMessage(
                                    AstraRS.DEBUG_PREFIX + "§2COMPARATOR §7at §e" + key + " §7result (§6" + m + "§7): " + compColor + (result ? "TRUE" : "FALSE")
                            );
                        }
                        gateObj.addProperty("current_out", currentResStr);
                        gateObj.addProperty("state", result);

                        // POPRAWKA: Przekazujemy wynik logiczny (true/false) do metody Redstone'a
                        GateUtils.updateOutput(plugin, key, ctx.dirs().targetBlock(), result);
                    }
                }

                case "DECODER" -> {
                    long incoming = GateUtils.getNumberFrom(ctx.dirs().backBlock(), plugin);
                    long targetValue = gateObj.has("value") ? gateObj.get("value").getAsLong() : 0L;
                    boolean isMatch = (incoming == targetValue && incoming != 0);
                    long finalVal = isMatch ? 1L : 0L;

                    String currentResStr = String.valueOf(finalVal);
                    String lastOutStr = gateObj.has("current_out") ? gateObj.get("current_out").getAsString() : "";

                    if (!currentResStr.equals(lastOutStr)) {
                        if (plugin.isDebugMode()) {
                            String matchColor = isMatch ? "§aTRAFIONY" : "§cBRAK DOPASOWANIA";
                            Bukkit.getConsoleSender().sendMessage(
                                    AstraRS.DEBUG_PREFIX + "§eDECODER §7at §e" + key + " §7(Searched for: §6" + targetValue + "§7) -> Status: " + matchColor + " §7(Received: §b" + incoming + "§7)"
                            );
                        }
                        gateObj.addProperty("current_out", currentResStr);
                        gateObj.addProperty("state", isMatch);
                        GateUtils.updateOutput(plugin, key, ctx.dirs().targetBlock(), isMatch);
                    }
                }

                case "RANDOM_BOOLEAN", "RANDOM_NUMBER" -> {
                    boolean in = ctx.pBack();
                    boolean lastIn = gateObj.has("lastInput") && gateObj.get("lastInput").getAsBoolean();

                    if (in && !lastIn) {
                        long minVal = gateObj.has("min") ? gateObj.get("min").getAsLong() : 0L;
                        long maxVal = gateObj.has("max") ? gateObj.get("max").getAsLong() : 15L;

                        long result = type.equals("RANDOM_BOOLEAN")
                                ? (ThreadLocalRandom.current().nextBoolean() ? 1L : 0L)
                                : ThreadLocalRandom.current().nextLong(minVal, maxVal + 1);

                        // SYSTEMOWA POPRAWKA ZAPISU ORAZ STANU (Obsługuje liczby ujemne!)
                        String currentResStr = String.valueOf(result);
                        boolean isActive = (result != 0);

                        if (plugin.isDebugMode()) {
                            Bukkit.getConsoleSender().sendMessage(
                                    AstraRS.DEBUG_PREFIX + "§a§lRANDOM §7[" + type + "] at §e" + key + " §7rolled value: §b§l" + result
                            );
                        }

                        gateObj.addProperty("current_out", currentResStr);
                        gateObj.addProperty("state", isActive);
                        GateUtils.updateOutput(plugin, key, ctx.dirs().targetBlock(), isActive);
                    }

                    if (in != lastIn) {
                        gateObj.addProperty("lastInput", in);
                    }
                }
            }
        }
    }
}