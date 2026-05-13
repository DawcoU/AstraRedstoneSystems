package pl.dawcou.AstraRedstoneSystems.gates;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import pl.dawcou.AstraRedstoneSystems.AstraRS;
import pl.dawcou.AstraRedstoneSystems.GateValidator;
import pl.dawcou.AstraRedstoneSystems.GateUtils;

import java.util.concurrent.ThreadLocalRandom;

public class NumberGates {
    private final AstraRS plugin;
    private final GateValidator validator;

    public NumberGates(AstraRS plugin, GateValidator validator) {
        this.plugin = plugin;
        this.validator = validator;
    }

    public void runNumberGates() {
        FileConfiguration config = plugin.getGatesConfig();
        ConfigurationSection gatesSection = config.getConfigurationSection("gates");
        if (gatesSection == null) return;
        // Pobieramy flagę debugowania raz dla całego cyklu przetwarzania
        boolean debug = plugin.getConfig().getBoolean("debug-mode", false);

        for (String key : gatesSection.getKeys(false)) {
            String path = "gates." + key;
            if (!validator.isValid(path, config)) continue;

            Location loc = GateUtils.strToLoc(key);
            if (loc == null) continue;

            Block gate = loc.getBlock();
            String type = config.getString(path + ".type", "").toUpperCase();

            // --- DEKLARUJEMY ZMIENNE JAKO NULL ---
            BlockFace out = null;
            BlockFace back = null;
            BlockFace s1 = null;
            BlockFace s2 = null;
            Block target = null;

            // --- INICJALIZUJEMY JE TYLKO JEŚLI TO NIE KABEL ---
            if (!type.equals("CABLE_DATA")) {
                String outName = config.getString(path + ".out", "NORTH");
                out = BlockFace.valueOf(outName.toUpperCase());
                back = out.getOppositeFace();
                s1 = GateUtils.rotate90(out);
                s2 = s1.getOppositeFace();
                target = gate.getRelative(out);
            }

            boolean currentState = config.getBoolean(path + ".state", false);

            // --- EFEKTY WIZUALNE STATUSU ---

            // 1. EFEKT WYJŚCIA (Wszystkie bramki logiczne oprócz kabla)
            if (type.matches("NUMBER_GATE|VARIABLE_GATE|COUNTER|BOOLEAN_GATE|MATH|COMPARATOR|LINKER|DECODER|RANDOM_BOOLEAN|RANDOM_NUMBER")) {
                GateUtils.spawnStatusParticle(gate, out, currentState);
            }

            // 2. EFEKT WEJŚCIA Z TYŁU (Główne wejście danych/zasilania)
            if (type.matches("NUMBER_GATE|VARIABLE_GATE|BOOLEAN_GATE|DECODER|RANDOM_BOOLEAN|RANDOM_NUMBER|LINKER|DISPLAY|COUNTER")) {
                boolean pBack = GateUtils.getPowerAt(gate.getRelative(back)) > 0;
                // Specjalny check dla Linkera i Displaya - one czytają Twoje "current_out"
                if (type.equals("LINKER") || type.equals("DISPLAY") || type.equals("COUNTER")) {
                    // Pobieramy konkretną liczbę z bloku z tyłu
                    int vBack = GateUtils.getDataFrom(gate.getRelative(back), back.getOppositeFace(), plugin);

                    // Jeśli to DISPLAY, to odświeżamy napis nad bramką
                    if (type.equals("DISPLAY")) {
                        String uuidStr = config.getString(path + ".displayUUID");
                        GateUtils.updateDisplayNumber(plugin, uuidStr, vBack);
                    }

                    // Particle świecą, jeśli jest jakakolwiek wartość lub prąd vanilla
                    GateUtils.spawnStatusParticle(gate, back, vBack > 0 || pBack);
                } else {
                    GateUtils.spawnStatusParticle(gate, back, pBack);
                }
            }

            // 3. EFEKT WEJŚĆ BOCZNYCH
            if (type.matches("COUNTER|MATH|COMPARATOR|LINKER")) {
                // Blokujemy nazwy kierunków dla czytelności
                BlockFace faceL = s2;
                BlockFace faceR = s1;

                // LEWO (Dla Countera to wejście odejmujące --)
                int vL;
                if (type.equals("COUNTER") || type.equals("MATH")) {
                    // Pobieramy pełne dane (np. z innej bramki)
                    vL = GateUtils.getDataFrom(gate.getRelative(faceL), faceL.getOppositeFace(), plugin);
                    // Jeśli nie ma danych z bramki, sprawdźmy zwykły redstone
                    if (vL == 0) vL = GateUtils.getPowerAt(gate.getRelative(faceL));
                } else {
                    vL = GateUtils.getCustomOrRedstonePower(plugin, gate.getRelative(faceL));
                }
                GateUtils.spawnStatusParticle(gate, faceL, vL > 0);

                // PRAWO (Dla Countera to zazwyczaj RESET)
                int vR;
                if (type.equals("COUNTER") || type.equals("MATH")) {
                    vR = GateUtils.getDataFrom(gate.getRelative(faceR), faceR.getOppositeFace(), plugin);
                    if (vR == 0) vR = GateUtils.getPowerAt(gate.getRelative(faceR));
                } else {
                    vR = GateUtils.getCustomOrRedstonePower(plugin, gate.getRelative(faceR));
                }
                GateUtils.spawnStatusParticle(gate, faceR, vR > 0);
            }

            // --- LOGIKA BRAMEK ---
            switch (type) {
                case "NUMBER_GATE" -> {
                    boolean pBack = GateUtils.getPowerAt(gate.getRelative(back)) > 0;
                    int storedValue = config.getInt(path + ".value", 0);
                    int finalValue = pBack ? storedValue : 0;
                    int lastOut = config.getInt(path + ".current_out", -1);

                    if (finalValue != lastOut) {
                        if (debug) plugin.getLogger().info("[AstraDebug] NUMBER_GATE na " + key + " stan: " + finalValue);
                        config.set(path + ".current_out", finalValue);
                        config.set(path + ".state", pBack);
                        GateUtils.updateOutput(plugin, path, target, pBack);
                    }
                }

                case "VARIABLE_GATE" -> {
                    // 1. Sprawdzamy boki (S1 i S2) pod kątem sygnału Reset
                    boolean reset = GateUtils.getPowerAt(gate.getRelative(s1)) > 0 || GateUtils.getPowerAt(gate.getRelative(s2)) > 0;

                    if (reset) {
                        // Siłowe czyszczenie wszystkiego
                        config.set(path + ".value", 0);
                        config.set(path + ".current_out", 0);
                        if (config.getBoolean(path + ".state", false)) {
                            config.set(path + ".state", false);
                            GateUtils.updateOutput(plugin, path, target, false);
                        }
                        if (debug) plugin.getLogger().info("[AstraDebug] VARIABLE " + key + " został ZRESETOWANY boczny sygnałem.");
                        return; // Przerywamy, nie sprawdzamy wejścia z tyłu w tym cyklu
                    }

                    // 2. Pobieramy dane z wejścia (tył)
                    int incoming = GateUtils.getDataFrom(gate.getRelative(back), back.getOppositeFace(), plugin);
                    int currentStored = config.getInt(path + ".value", 0);

                    // 3. Logika "Zatrzymania" (Latch)
                    // Zapisujemy nową wartość TYLKO jeśli jest większa od 0
                    if (incoming > 0) {
                        if (incoming != currentStored) {
                            config.set(path + ".value", incoming);
                            currentStored = incoming;
                            if (debug) plugin.getLogger().info("[AstraDebug] VARIABLE " + key + " zapisała nową wartość: " + incoming);
                        }
                    }

                    // 4. Logika wyjścia - zawsze wypuszczaj to, co masz "wgrane"
                    boolean hasState = currentStored > 0;
                    boolean previousState = config.getBoolean(path + ".state", false);

                    if (hasState != previousState) {
                        config.set(path + ".state", hasState);
                        GateUtils.updateOutput(plugin, path, target, hasState);
                    }

                    // Synchronizacja dla innych bramek (np. Display czy Math)
                    if (currentStored != config.getInt(path + ".current_out", -1)) {
                        config.set(path + ".current_out", currentStored);
                    }
                }

                case "CABLE_DATA" -> {
                    int bestValue = 0;
                    int maxPower = 0;
                    int myCurrentPower = config.getInt(path + ".power", 0);

                    for (BlockFace face : BlockFace.values()) {
                        if (!face.isCartesian()) continue;
                        Block neighbor = gate.getRelative(face);
                        String neighborPath = "gates." + GateUtils.locToStr(neighbor.getLocation());

                        if (!config.contains(neighborPath)) continue;
                        String nType = config.getString(neighborPath + ".type", "");
                        int incoming = GateUtils.getDataFrom(neighbor, face.getOppositeFace(), plugin);

                        int neighborPower;
                        if (!nType.equals("CABLE_DATA")) {
                            // 1. Jeśli to BRAMKA - ona ma zawsze moc 100
                            String nOutStr = config.getString(neighborPath + ".out", "NORTH");
                            BlockFace nOut = BlockFace.valueOf(nOutStr.toUpperCase());
                            if (neighbor.getRelative(nOut).getLocation().equals(gate.getLocation())) {
                                neighborPower = 100;
                            } else {
                                neighborPower = 0;
                            }
                        } else {
                            // 2. Jeśli to KABEL - pobieramy jego aktualną moc
                            neighborPower = config.getInt(neighborPath + ".power", 0);
                        }

                        // BIERZEMY SYGNAŁ TYLKO OD SILNIEJSZEGO SĄSIADA
                        // To klucz do braku migania i braku pętli!
                        if (neighborPower > maxPower) {
                            maxPower = neighborPower;
                            bestValue = incoming;
                        }
                    }

                    // Obliczamy naszą nową moc (zawsze o 1 mniej niż źródło)
                    int newPower = Math.max(0, maxPower - 1);

                    // Aktualizujemy tylko jeśli coś się zmieniło, żeby nie marnować procesora
                    if (bestValue != config.getInt(path + ".current_out", 0) || newPower != myCurrentPower) {
                        config.set(path + ".current_out", bestValue);
                        config.set(path + ".power", newPower); // Zapisujemy moc do stabilizacji
                    }

                    // Efekty cząsteczkowe
                    if (bestValue > 0) {
                        Location pLoc = gate.getLocation().clone().add(0.5, 1.1, 0.5);
                        gate.getWorld().spawnParticle(Particle.REDSTONE, pLoc, 1, 0.1, 0, 0.1, 0, new Particle.DustOptions(Color.AQUA, 1.0F));
                    }
                }

                case "COUNTER" -> {
                    BlockFace right = GateUtils.rotate90(out);
                    BlockFace left = right.getOppositeFace();

                    // --- POBIERANIE DANYCH ---
                    int dataBack = GateUtils.getDataFrom(gate.getRelative(back), back.getOppositeFace(), plugin);
                    int dataLeft = GateUtils.getDataFrom(gate.getRelative(left), left.getOppositeFace(), plugin);

                    boolean pB = GateUtils.getPowerAt(gate.getRelative(back)) > 0;
                    boolean pL = GateUtils.getPowerAt(gate.getRelative(left)) > 0;
                    boolean pR = GateUtils.getPowerAt(gate.getRelative(right)) > 0;

                    // --- POBIERANIE POPRZEDNICH STANÓW (Z CONFIGU) ---
                    int lastDataBack = config.getInt(path + ".last_data_back", 0);
                    int lastDataLeft = config.getInt(path + ".last_data_left", 0);
                    boolean lB = config.getBoolean(path + ".last_back", false);
                    boolean lL = config.getBoolean(path + ".last_left", false);
                    boolean lR = config.getBoolean(path + ".last_right", false);

                    int count = config.getInt(path + ".count", 0);
                    int limit = config.getInt(path + ".score_limit", 15);
                    boolean changed = false;

                    // --- LOGIKA: DODAWANIE (TYŁ) ---
                    // A) Przez wysłanie liczby (dodaje różnicę)
                    if (dataBack > lastDataBack) {
                        count = Math.min(limit, count + (dataBack - lastDataBack));
                        changed = true;
                    }
                    // B) Przez impuls Redstone (+1)
                    else if (pB && !lB && count < limit) {
                        count++;
                        changed = true;
                    }

                    // --- LOGIKA: ODEJMOWANIE (LEWO) ---
                    // A) Przez wysłanie liczby (odejmuje różnicę)
                    if (dataLeft > lastDataLeft) {
                        count = Math.max(0, count - (dataLeft - lastDataLeft));
                        changed = true;
                    }
                    // B) Przez impuls Redstone (-1)
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
                        config.set(path + ".count", count);

                        boolean finalState = (count > 0);
                        boolean previousState = config.getBoolean(path + ".state", false);

                        if (finalState != previousState) {
                            config.set(path + ".state", finalState);
                            GateUtils.updateOutput(plugin, path, target, finalState);
                        }
                    }

                    // Zapisujemy stany do następnego sprawdzenia
                    config.set(path + ".current_out", count); // Dla innych bramek
                    config.set(path + ".last_data_back", dataBack);
                    config.set(path + ".last_data_left", dataLeft);
                    config.set(path + ".last_back", pB);
                    config.set(path + ".last_left", pL);
                    config.set(path + ".last_right", pR);
                }

                case "BOOLEAN_GATE" -> {
                    boolean hasPower = GateUtils.getPowerAt(gate.getRelative(back)) > 0;
                    int valueToSend = hasPower ? 1 : 0;
                    int lastOut = config.getInt(path + ".current_out", -1);

                    if (valueToSend != lastOut) {
                        if (debug) plugin.getLogger().info("[AstraDebug] BOOLEAN " + key + " przesyła: " + valueToSend);
                        config.set(path + ".current_out", valueToSend);
                        config.set(path + ".state", hasPower);
                        GateUtils.updateOutput(plugin, path, target, hasPower);
                    }
                }

                case "MATH" -> {
                    BlockFace right = GateUtils.rotate90(out);
                    BlockFace left = right.getOppositeFace();

                    int vL = GateUtils.getDataFrom(gate.getRelative(left), left.getOppositeFace(), plugin);
                    int vR = GateUtils.getDataFrom(gate.getRelative(right), right.getOppositeFace(), plugin);
                    String m = config.getString(path + ".mode", "ADD").toUpperCase();

                    int result = switch (m) {
                        case "SUB", "-" -> Math.max(0, vL - vR);
                        case "MUL", "*" -> vL * vR;
                        case "DIV", "/" -> (vR != 0) ? (vL / vR) : 0;
                        case "POW", "^" -> (int) Math.pow(vL, vR);
                        default -> vL + vR;
                    };

                    int lastRes = config.getInt(path + ".current_out", -1);
                    if (result != lastRes) {
                        if (debug) plugin.getLogger().info("[AstraDebug] MATH " + key + " wynik (" + m + "): " + result);
                        config.set(path + ".current_out", result);
                        config.set(path + ".state", result > 0);
                        GateUtils.updateOutput(plugin, path, target, result > 0);
                    }
                }

                case "COMPARATOR" -> {
                    BlockFace right = GateUtils.rotate90(out);
                    BlockFace left = right.getOppositeFace();

                    int vL = GateUtils.getDataFrom(gate.getRelative(left), left.getOppositeFace(), plugin);
                    int vR = GateUtils.getDataFrom(gate.getRelative(right), right.getOppositeFace(), plugin);
                    String m = config.getString(path + ".mode", "==");

                    boolean result = switch (m) {
                        case ">" -> vL > vR;
                        case "<" -> vL < vR;
                        case "==" -> vL == vR;
                        case ">=" -> vL >= vR;
                        case "<=" -> vL <= vR;
                        case "!=" -> vL != vR;
                        default -> false;
                    };

                    int finalVal = result ? 1 : 0;
                    int lastOut = config.getInt(path + ".current_out", -1);
                    if (finalVal != lastOut) {
                        if (debug) plugin.getLogger().info("[AstraDebug] COMP " + key + " wynik: " + result);
                        config.set(path + ".current_out", finalVal);
                        config.set(path + ".state", result);
                        GateUtils.updateOutput(plugin, path, target, result);
                    }
                }

                case "LINKER" -> {
                    // Dane płyną z TYŁU
                    int incomingValue = GateUtils.getDataFrom(gate.getRelative(back), back.getOppositeFace(), plugin);

                    // Blokada z BOKÓW (lewy lub prawy)
                    int blockL = GateUtils.getCustomOrRedstonePower(plugin, gate.getRelative(s2)); // Lewo
                    int blockR = GateUtils.getCustomOrRedstonePower(plugin, gate.getRelative(s1)); // Prawo

                    // Jeśli którykolwiek bok ma prąd, wynik to 0. Jeśli nie - puszczamy tył.
                    int result = (blockL > 0 || blockR > 0) ? 0 : incomingValue;

                    int lastOut = config.getInt(path + ".current_out", -1);
                    if (result != lastOut) {
                        if (debug) plugin.getLogger().info("[AstraDebug] LINKER " + key + " (Input: " + incomingValue + ") Blokada: " + (result == 0 && incomingValue > 0));
                        config.set(path + ".current_out", result);
                        config.set(path + ".state", result > 0);
                        GateUtils.updateOutput(plugin, path, target, result > 0);
                    }
                }

                case "DECODER" -> {
                    int incoming = GateUtils.getDataFrom(gate.getRelative(back), back.getOppositeFace(), plugin);
                    int targetValue = config.getInt(path + ".value", 0);
                    boolean isMatch = (incoming == targetValue && incoming != 0);
                    int finalVal = isMatch ? 1 : 0;

                    int lastOut = config.getInt(path + ".current_out", -1);
                    if (finalVal != lastOut) {
                        if (debug) plugin.getLogger().info("[AstraDebug] DECODER " + key + " (szuka: " + targetValue + ") otrzymał: " + incoming);
                        config.set(path + ".current_out", finalVal);
                        config.set(path + ".state", isMatch);
                        GateUtils.updateOutput(plugin, path, target, isMatch);
                    }
                }

                case "RANDOM_BOOLEAN", "RANDOM_NUMBER" -> {
                    boolean in = GateUtils.getPowerAt(gate.getRelative(back)) > 0;
                    boolean lastIn = config.getBoolean(path + ".lastInput", false);

                    if (in && !lastIn) {
                        int result = type.equals("RANDOM_BOOLEAN")
                                ? (ThreadLocalRandom.current().nextBoolean() ? 1 : 0)
                                : ThreadLocalRandom.current().nextInt(config.getInt(path + ".min", 0), config.getInt(path + ".max", 15) + 1);

                        if (debug) plugin.getLogger().info("[AstraDebug] RANDOM " + key + " wylosował: " + result);
                        config.set(path + ".current_out", result);
                        config.set(path + ".state", result > 0);
                        GateUtils.updateOutput(plugin, path, target, result > 0);
                    }
                    config.set(path + ".lastInput", in);
                }

                case "DISPLAY" -> {
                    // 1. Odbieramy liczbę z tyłu Twoją metodą
                    int val = GateUtils.getDataFrom(gate.getRelative(back), back.getOppositeFace(), plugin);

                    // 2. Wyciągamy UUID wyświetlacza z configu
                    String uuidStr = config.getString(path + ".displayUUID");

                    // 3. Aktualizujemy napis (używamy Twojej nowej metody w GateUtils)
                    GateUtils.updateDisplayNumber(plugin, uuidStr, val);

                    // 4. Zapisujemy tę wartość jako wyjście bramki, żeby można było łączyć DISPLAY szeregowo
                    config.set(path + ".current_out", val);
                }
            }
        }
    }
}