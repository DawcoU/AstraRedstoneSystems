package pl.dawcou.astrars.gates.types;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import pl.dawcou.astrars.AstraRS;
import pl.dawcou.astrars.file.GateDataManager;
import pl.dawcou.astrars.gates.data.GateContext;
import pl.dawcou.astrars.gates.data.GateContextFactory;
import pl.dawcou.astrars.gates.data.GateValidator;
import pl.dawcou.astrars.gates.utils.GateUtils;

import java.util.Map;
import java.util.Set;

public class DataGates {
    private final AstraRS plugin;
    private final GateValidator validator;

    private static final Set<String> BACK_GATES = Set.of("DISPLAY", "TRANSISTOR", "DISK_GATE", "RAM_GATE", "BATTERY", "DATA_DETECTOR");
    private static final Set<String> OUT_GATES = Set.of("TRANSISTOR", "DISK_GATE", "RAM_GATE", "BATTERY", "DATA_DETECTOR");
    private static final Set<String> SIDE_GATES = Set.of("TRANSISTOR", "DISK_GATE", "RAM_GATE");

    public DataGates(AstraRS plugin, GateValidator validator) {
        this.plugin = plugin;
        this.validator = validator;
    }

    public void runDataGates() {
        GateDataManager manager = plugin.getGateDataManager();
        JsonObject gatesSection = manager.getGates();
        if (gatesSection == null) return;

        for (Map.Entry<String, JsonElement> entry : gatesSection.entrySet()) {
            GateContext ctx = GateContextFactory.create(entry, validator);
            if (ctx == null) continue;

            JsonObject gateObj = ctx.json();
            Block gate = ctx.gateBlock();
            String key = ctx.key();
            String type = ctx.type();

            // --- EFEKTY WIZUALNE STATUSU ---
            if (BACK_GATES.contains(type)) {
                boolean currentState = ctx.currentState();

                // 1. EFEKT WYJŚCIA (Włączony dla Transistora, Variable i teraz dla sprawnych Baterii)
                if (OUT_GATES.contains(type)) {
                    GateUtils.spawnStatusParticle(gate, ctx.dirs().out(), currentState);
                }

                // 2. EFEKT WEJŚCIA Z TYŁU (Zawsze dla wszystkich w tym bloku, w tym dla ładującej się baterii)
                String vBack = GateUtils.getStringFrom(ctx.dirs().backBlock(), plugin);
                boolean isActive = !vBack.isEmpty();

                GateUtils.spawnStatusParticle(gate, ctx.dirs().back(), isActive || ctx.pBack());

                // 3. EFEKT BLOKADY Z BOKÓW (Tylko dla tych, które mają logikę blokowania)
                if (SIDE_GATES.contains(type)) {
                    GateUtils.spawnStatusParticle(gate, ctx.dirs().left(), ctx.pLeft());  // Lewy bok
                    GateUtils.spawnStatusParticle(gate, ctx.dirs().right(), ctx.pRight()); // Prawy bok
                }
            }

            // --- LOGIKA BRAMEK ---
            switch (type) {
                case "CABLE_DATA" -> {
                    String bestValue = String.valueOf(Long.MIN_VALUE);
                    long maxPower = 0;
                    long myCurrentPower = gateObj.has("power") ? gateObj.get("power").getAsLong() : 0;
                    String currentOut = gateObj.has("current_out") ? gateObj.get("current_out").getAsString() : String.valueOf(Long.MIN_VALUE);

                    for (BlockFace face : BlockFace.values()) {
                        if (!face.isCartesian()) continue;
                        Block neighbor = gate.getRelative(face);
                        String neighborKey = GateUtils.locToStr(neighbor.getLocation());
                        JsonObject neighborObj = manager.getGate(neighborKey);

                        if (neighborObj == null) continue;

                        String nType = neighborObj.has("type") ? neighborObj.get("type").getAsString() : "";

                        if (!nType.equals("CABLE_DATA")) {
                            boolean isActive = neighborObj.has("state") && neighborObj.get("state").getAsBoolean();
                            if (!isActive) continue; // Ignorujemy tylko wyłączone bramki
                        }

                        String incoming = GateUtils.getStringFrom(neighbor, plugin);

                        // ZABEZPIECZENIE: Ignorujemy puste dane oraz flagę braku sygnału (Long.MIN_VALUE)
                        if (incoming == null || incoming.isEmpty() || incoming.equals(String.valueOf(Long.MIN_VALUE))) {
                            continue;
                        }

                        long neighborPower;
                        if (!nType.equals("CABLE_DATA")) {
                            String nOutStr = neighborObj.has("out") ? neighborObj.get("out").getAsString() : "NORTH";
                            BlockFace nOut = BlockFace.valueOf(nOutStr.toUpperCase());
                            if (neighbor.getRelative(nOut).getLocation().equals(gate.getLocation())) {
                                neighborPower = 300;
                            } else {
                                neighborPower = 0;
                            }
                        } else {
                            neighborPower = neighborObj.has("power") ? neighborObj.get("power").getAsLong() : 0;
                        }

                        // BLOKADA ANTY-ECHO / ANTY-SPAM:
                        // Jeśli sąsiad to kabel, ma taki sam tekst jak nasz obecny i jego moc jest MNIEJSZA lub równa naszej,
                        // to oznacza, że on żywi się NASZYM sygnałem! Ignorujemy go, żeby nie stworzyć pętli zwrotnej.
                        if (nType.equals("CABLE_DATA") && incoming.equals(currentOut) && neighborPower <= myCurrentPower) {
                            continue;
                        }

                        if (neighborPower > maxPower) {
                            maxPower = neighborPower;
                            bestValue = incoming;
                        }
                    }

                    long newPower = Math.max(0, maxPower - 1);

                    // Logika aktualizacji
                    if (!bestValue.equals(currentOut) || newPower != myCurrentPower) {
                        gateObj.addProperty("current_out", bestValue);
                        gateObj.addProperty("power", newPower);

                        if (plugin.isDebugMode()) {
                            Bukkit.getConsoleSender().sendMessage(
                                    AstraRS.DEBUG_PREFIX + "§9CABLE_DATA §7at §e" + key +
                                            " §7changed state: §b\"" + (bestValue.isEmpty() ? "EMPTY" : bestValue) + "\" §7(Power: §3" + newPower + "§7)"
                            );
                        }
                    }

                    // Efekt wizualny
                    if (!bestValue.isEmpty() && newPower > 0) {
                        Location pLoc = gate.getLocation().clone().add(0.5, 1.1, 0.5);
                        gate.getWorld().spawnParticle(Particle.REDSTONE, pLoc, 2, 0, 0, 0, 0, new Particle.DustOptions(Color.AQUA, 1.0F));
                    }
                }

                case "DISPLAY" -> {
                    if (!plugin.isTextDisplaySupported()) {
                        continue;
                    }

                    Location gateLoc = gate.getLocation();

                    // Jeśli chunk z bramką jest odładowany, nie robimy absolutnie nic
                    if (!gateLoc.getChunk().isLoaded()) {
                        continue;
                    }

                    String rawData = GateUtils.getStringFrom(ctx.dirs().backBlock(), plugin);

                    // Pobieramy stary UUID do porównania
                    String oldUuidStr = gateObj.has("displayUUID") ? gateObj.get("displayUUID").getAsString() : "";
                    String uuidStr = GateUtils.validateDisplay(gateObj, gateLoc);

                    gateObj.addProperty("displayUUID", uuidStr);

                    String lastOut = gateObj.has("current_out") ? gateObj.get("current_out").getAsString() : "";
                    boolean isNewHologram = !uuidStr.equals(oldUuidStr);

                    // Aktualizacja tekstu tylko przy zmianie danych LUB nowym hologramie (w załadowanym chunku!)
                    if (!rawData.equals(lastOut) || isNewHologram) {

                        // Jeśli z jakiegoś powodu uuidStr jest pusty (bo chunk był odładowany), pomijamy update tekstu
                        if (uuidStr.isEmpty()) {
                            continue;
                        }

                        // Zawsze biały kolor tekstu
                        String formattedData = rawData.isEmpty() ? "" : "§f" + rawData;

                        GateUtils.updateDisplayNumber(uuidStr, formattedData);
                        gateObj.addProperty("current_out", rawData);

                        if (plugin.isDebugMode()) {
                            Bukkit.getConsoleSender().sendMessage(
                                    AstraRS.DEBUG_PREFIX + "§fDISPLAY §7at §e" + key +
                                            " §7" + (isNewHologram ? "recreated hologram and set" : "refreshed") +
                                            " text to: " + formattedData
                            );
                        }
                    }
                }

                case "TRANSISTOR" -> {
                    // Próbujemy pobrać tekst z kabla z tyłu
                    String incomingString = GateUtils.getStringFrom(ctx.dirs().backBlock(), plugin);

                    // Wybieramy co faktycznie płynie z tyłu
                    boolean incomingHasPower = !incomingString.isEmpty();

                    // Blokada z BOKÓW (lewy lub prawy) - używamy metody do zwykłego prądu!
                    long blockL = GateUtils.getPowerAt(ctx.dirs().leftBlock()); // Lewo
                    long blockR = GateUtils.getPowerAt(ctx.dirs().rightBlock()); // Prawo
                    boolean isBlocked = (blockL > 0 || blockR > 0);

                    // SYSTEMOWA POPRAWKA: Jeśli jest blokada lub brak sygnału z tyłu, wyjściem jest pustka ""
                    String result = (isBlocked || !incomingHasPower) ? "" : incomingString;
                    boolean hasPower = !result.isEmpty();

                    String lastOut = gateObj.has("current_out") ? gateObj.get("current_out").getAsString() : "";
                    boolean lastState = ctx.currentState(); // <--- POBIERAMY STARY STAN FIZYCZNY

                    // 1. Zapis tekstowy w configu i logi robimy, jeśli zmieniła się treść danych
                    if (!result.equals(lastOut)) {
                        if (plugin.isDebugMode()) {
                            String statusColor = isBlocked ? "§c[BLOKADA]" : "§a[PRZEPŁYW]";
                            Bukkit.getConsoleSender().sendMessage(
                                    AstraRS.DEBUG_PREFIX + "§6TRANSISTOR §7at §e" + key +
                                            " " + statusColor + " §7Back signal: §b\"" + (incomingString.isEmpty() ? "NONE" : incomingString) + "\"" +
                                            " §7-> Output: §d\"" + (result.isEmpty() ? "EMPTY" : result) + "\""
                            );
                        }
                        gateObj.addProperty("current_out", result);
                    }

                    // 2. FIZYCZNĄ AKTUALIZACJĘ ŚWIATA ROBIMY TYLKO WTEDY, GDY ZMIENIŁO SIĘ STAN WŁĄCZONY/WYŁĄCZONY!
                    if (hasPower != lastState) {
                        gateObj.addProperty("state", hasPower);
                        GateUtils.updateOutput(plugin, key, ctx.dirs().targetBlock(), hasPower); // <--- ŚWIAT AKTUALIZUJE SIĘ TYLKO PRZY REALNEJ ZMIANIE SZYNY!
                    }
                }

                case "DISK_GATE" -> {
                    // 1. Sprawdzamy boki (right i left) pod kątem sygnału Reset
                    boolean reset = ctx.pRight() || ctx.pLeft();

                    String currentStored = gateObj.has("value") ? gateObj.get("value").getAsString() : "";
                    boolean previousState = ctx.currentState();

                    if (reset) {
                        // Odpalamy logikę resetu TYLKO jeśli bramka faktycznie NIE JEST jeszcze pusta
                        if (!currentStored.isEmpty() || previousState) {
                            gateObj.addProperty("value", "");
                            gateObj.addProperty("current_out", "");
                            gateObj.addProperty("state", false);

                            GateUtils.updateOutput(plugin, key, ctx.dirs().targetBlock(), false);

                            if (plugin.isDebugMode()) {
                                Bukkit.getConsoleSender().sendMessage(AstraRS.DEBUG_PREFIX + "§dDISK_GATE §e" + key + " §cRESET BY side signal!");
                            }

                            // Wyczyszczone lokalnie, żeby sekcja poniżej zapisała czysty stan do pliku
                            currentStored = "";
                            gateObj.addProperty("value", "");
                        }
                    } else {
                        // AKCJA: Dane z tyłu zbieramy i zapisujemy TYLKO wtedy, gdy NIE MA RESETU!
                        // 2. Pobieramy dane z wejścia (tył) jako uniwersalny tekst/liczba
                        String incoming = GateUtils.getStringFrom(ctx.dirs().backBlock(), plugin);

                        // 3. Logika "Zatrzymania" (Latch)
                        if (!incoming.isEmpty()) {
                            if (!incoming.equals(currentStored)) {
                                gateObj.addProperty("value", incoming);
                                currentStored = incoming;
                                if (plugin.isDebugMode()) {
                                    Bukkit.getConsoleSender().sendMessage(AstraRS.DEBUG_PREFIX + "§dDISK_GATE §e" + key + " §7saved new value: §b\"" + incoming + "\"");
                                }
                            }
                        }
                    }

                    // 4. Logika wyjścia - Zawsze sprawdzana, gwarantuje, że KAŻDA bramka zresetuje się równo!
                    boolean hasState = !currentStored.isEmpty();
                    previousState = gateObj.has("state") && gateObj.get("state").getAsBoolean(); // Pobieramy stan na nowo po if(reset)

                    if (hasState != previousState) {
                        gateObj.addProperty("state", hasState);
                        GateUtils.updateOutput(plugin, key, ctx.dirs().targetBlock(), hasState);
                    }

                    // Synchronizacja uniwersalnego wyjścia tekstowego (To czyści current_out do spodu!)
                    String currentOut = gateObj.has("current_out") ? gateObj.get("current_out").getAsString() : "";
                    if (!currentStored.equals(currentOut)) {
                        gateObj.addProperty("current_out", currentStored);
                    }
                }

                case "RAM_GATE" -> {
                    // 1. Sprawdzamy boki (right i left) pod kątem sygnału Reset i zasilania
                    boolean reset = ctx.pLeft();
                    boolean power = ctx.pRight();

                    String currentStored = gateObj.has("value") ? gateObj.get("value").getAsString() : "";
                    boolean previousState = ctx.currentState();

                    if (reset || !power) {
                        // Odpalamy logikę resetu TYLKO jeśli bramka faktycznie NIE JEST jeszcze pusta
                        if (!currentStored.isEmpty() || previousState) {
                            GateUtils.updateOutput(plugin, key, ctx.dirs().targetBlock(), false);

                            if (plugin.isDebugMode()) {
                                Bukkit.getConsoleSender().sendMessage(AstraRS.DEBUG_PREFIX + "§dRAM_GATE §e" + key + " §cRESET BY side signal!");
                            }

                            // Wyczyszczone lokalnie, żeby sekcja poniżej zapisała czysty stan do pliku
                            currentStored = "";
                            gateObj.addProperty("value", "");
                        }
                    } else {
                        // AKCJA: Dane z tyłu zbieramy i zapisujemy TYLKO wtedy, gdy NIE MA RESETU!
                        // 2. Pobieramy dane z wejścia (tył) jako uniwersalny tekst/liczba
                        String incoming = GateUtils.getStringFrom(ctx.dirs().backBlock(), plugin);

                        // 3. Logika "Zatrzymania" (Latch)
                        if (!incoming.isEmpty()) {
                            if (!incoming.equals(currentStored)) {
                                gateObj.addProperty("value", incoming);
                                currentStored = incoming;
                                if (plugin.isDebugMode()) {
                                    Bukkit.getConsoleSender().sendMessage(AstraRS.DEBUG_PREFIX + "§dRAM_GATE §e" + key + " §7saved new value: §b\"" + incoming + "\"");
                                }
                            }
                        }
                    }

                    // 4. Logika wyjścia - Zawsze sprawdzana, gwarantuje, że KAŻDA bramka zresetuje się równo!
                    boolean hasState = !currentStored.isEmpty();
                    previousState = gateObj.has("state") && gateObj.get("state").getAsBoolean(); // Pobieramy stan na nowo po if(reset)

                    if (hasState != previousState) {
                        gateObj.addProperty("state", hasState);
                        GateUtils.updateOutput(plugin, key, ctx.dirs().targetBlock(), hasState);
                    }

                    // Synchronizacja uniwersalnego wyjścia tekstowego (To czyści current_out do spodu!)
                    String currentOut = gateObj.has("current_out") ? gateObj.get("current_out").getAsString() : "";
                    if (!currentStored.equals(currentOut)) {
                        gateObj.addProperty("current_out", currentStored);
                    }
                }

                case "BATTERY" -> {
                    // --- ODCZYT I INICJALIZACJA DANYCH ---
                    long charge = gateObj.has("charge") ? gateObj.get("charge").getAsLong() : 0L;
                    long lastChargeTick = gateObj.has("last_charge_tick") ? gateObj.get("last_charge_tick").getAsLong() : 0L;
                    long lastDecay = gateObj.has("last_decay") ? gateObj.get("last_decay").getAsLong() : 0L;
                    long currentTime = System.currentTimeMillis();

                    // Czas rozładowywania i ładowania pobierany bezpośrednio w sekundach
                    long decaySeconds = gateObj.has("decay_seconds") ? gateObj.get("decay_seconds").getAsLong() : 60L; // min 60s, max 150s
                    long decayInterval = decaySeconds * 1000L;

                    long chargeSeconds = gateObj.has("charge_seconds") ? gateObj.get("charge_seconds").getAsLong() : 10L; // min 10s, max 30s
                    long chargeInterval = chargeSeconds * 1000L;

                    // Jeśli bateria jest tworzona po raz pierwszy, ustawiamy czas decay na aktualny
                    if (lastDecay == 0L) {
                        gateObj.addProperty("last_decay", currentTime);
                        lastDecay = currentTime;
                    }

                    // --- SPRAWDZENIE CZY ŁADOWARKA JEST PODPIĘTA (TYŁ) ---
                    String vBack = GateUtils.getStringFrom(ctx.dirs().backBlock(), plugin);
                    boolean isInputPowered = ctx.pBack() || !vBack.isEmpty();

                    // --- 1. MECHANIZM AUTOMATYCZNEGO ROZŁADOWYWANIA ---
                    if (currentTime - lastDecay >= decayInterval) {
                        if (isInputPowered) {
                            gateObj.addProperty("last_decay", currentTime);
                            if (plugin.isDebugMode()) {
                                Bukkit.getConsoleSender().sendMessage(
                                        AstraRS.DEBUG_PREFIX + "§eBATTERY §7at §e" + key + " §bEnergy drop blocked - charging in progress."
                                );
                            }
                        } else if (charge > 0L) {
                            charge = Math.max(0L, charge - 1L);
                            gateObj.addProperty("charge", charge);
                            gateObj.addProperty("last_decay", currentTime);

                            if (plugin.isDebugMode()) {
                                Bukkit.getConsoleSender().sendMessage(
                                        AstraRS.DEBUG_PREFIX + "§eBATTERY §7at §e" + key + " §cLost 1% energy (No power). Level: §b" + charge + "%"
                                );
                            }
                        } else {
                            gateObj.addProperty("last_decay", lastDecay + decayInterval);
                        }
                    }

                    // --- 2. MECHANIZM STAŁEGO ŁADOWANIA ---
                    if (isInputPowered && (currentTime - lastChargeTick >= chargeInterval)) {
                        if (charge < 100L) {
                            charge = Math.min(100L, charge + 1L);
                            gateObj.addProperty("charge", charge);
                            gateObj.addProperty("last_charge_tick", currentTime);

                            if (plugin.isDebugMode()) {
                                Bukkit.getConsoleSender().sendMessage(
                                        AstraRS.DEBUG_PREFIX + "§eBATTERY §7at §e" + key + " §aGrid charging... Level: §b" + charge + "%"
                                );
                            }
                        }
                    }

                    // --- 3. LOGIKA WYJŚCIA ---
                    boolean hasPower = charge > 0L;
                    boolean previousState = ctx.currentState();

                    if (hasPower != previousState) {
                        gateObj.addProperty("state", hasPower);
                        GateUtils.updateOutput(plugin, key, ctx.dirs().targetBlock(), hasPower);

                        if (plugin.isDebugMode()) {
                            String powerStatus = hasPower ? "§aENABLED" : "§cDISABLED (0%)";
                            Bukkit.getConsoleSender().sendMessage(
                                    AstraRS.DEBUG_PREFIX + "§eBATTERY §7at §e" + key + " §aGrid charging... Level: §b" + charge + "% §7(" + powerStatus + "§7)"
                            );
                        }
                    }

                    String chargeString = charge + "%";
                    String currentOut = gateObj.has("current_out") ? gateObj.get("current_out").getAsString() : "";
                    if (!chargeString.equals(currentOut)) {
                        gateObj.addProperty("current_out", chargeString);
                    }
                }

                case "DATA_DETECTOR" -> {
                    String rawData = GateUtils.getStringFrom(ctx.dirs().backBlock(), plugin);

                    // Bramka wykrywa sygnał jeśli String nie jest pusty i nie jest reprezentacją MIN_VALUE
                    boolean hasData = !rawData.isEmpty() && !rawData.equals(String.valueOf(Long.MIN_VALUE));

                    // Jeśli jest sygnał -> wysyłamy 1, w przeciwnym razie MIN_VALUE (brak sygnału w sieci danych)
                    String currentResStr = hasData ? "1" : String.valueOf(Long.MIN_VALUE);
                    String lastOutStr = gateObj.has("current_out") ? gateObj.get("current_out").getAsString() : "";

                    if (!currentResStr.equals(lastOutStr)) {
                        if (plugin.isDebugMode()) {
                            Bukkit.getConsoleSender().sendMessage(
                                    AstraRS.DEBUG_PREFIX + "§bDATA_DETECTOR §7at §e" + key + " §7-> Detected: " + (hasData ? "§aYES (§f" + rawData + "§a)" : "§cNO")
                            );
                        }

                        gateObj.addProperty("current_out", currentResStr);
                        gateObj.addProperty("state", hasData);

                        GateUtils.updateOutput(plugin, key, ctx.dirs().targetBlock(), hasData);
                    }
                }
            }
        }
    }
}