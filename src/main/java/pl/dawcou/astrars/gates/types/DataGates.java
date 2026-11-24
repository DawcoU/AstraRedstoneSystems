package pl.dawcou.astrars.gates.types;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import pl.dawcou.astrars.AstraRS;
import pl.dawcou.astrars.file.GateDataManager;
import pl.dawcou.astrars.gates.data.GateContextFactory;
import pl.dawcou.astrars.gates.data.GateValidator;
import pl.dawcou.astrars.gates.utils.GateUtils;

import java.util.Map;
import java.util.Set;

public class DataGates {
    private final AstraRS plugin;
    private final GateValidator validator;

    private static final Set<String> HANDLED_TYPES = Set.of(
            "CABLE_DATA", "DISPLAY", "TRANSISTOR", "DISK",
            "RAM", "BATTERY", "DATA_DETECTOR"
    );

    private static final Set<String> BACK_GATES = Set.of("DISPLAY", "TRANSISTOR", "DISK", "RAM", "BATTERY", "DATA_DETECTOR");
    private static final Set<String> OUT_GATES = Set.of("TRANSISTOR", "DISK", "RAM", "BATTERY", "DATA_DETECTOR");
    private static final Set<String> SIDE_GATES = Set.of("TRANSISTOR", "DISK", "RAM");

    public DataGates(AstraRS plugin, GateValidator validator) {
        this.plugin = plugin;
        this.validator = validator;
    }

    // --------------------------------------------------
    // GŁÓWNA PĘTLA
    // --------------------------------------------------

    public void runDataGates() {
        GateDataManager manager = plugin.getGateDataManager();
        JsonObject gatesSection = manager.getGates();
        if (gatesSection == null) return;

        for (Map.Entry<String, JsonElement> entry : gatesSection.entrySet()) {
            GateContextFactory.createAndRun(entry, validator, plugin, ctx -> {
                if (ctx == null) return;

                if (!HANDLED_TYPES.contains(ctx.type())) return;

                JsonObject gateObj = ctx.json();
                Block gate = ctx.gateBlock();
                String key = ctx.key();
                String type = ctx.type();

                if (BACK_GATES.contains(type)) {
                    boolean currentState = ctx.currentState();

                    if (OUT_GATES.contains(type)) {
                        GateUtils.spawnStatusParticle(gate, ctx.dirs().out(), currentState);
                    }

                    JsonElement vBack = GateUtils.getDataFrom(ctx.dirs().backBlock(), plugin);
                    boolean isActive = GateUtils.isDataSignalActive(vBack);

                    GateUtils.spawnStatusParticle(gate, ctx.dirs().back(), isActive || ctx.pBack());

                    if (SIDE_GATES.contains(type)) {
                        GateUtils.spawnStatusParticle(gate, ctx.dirs().left(), ctx.pLeft());
                        GateUtils.spawnStatusParticle(gate, ctx.dirs().right(), ctx.pRight());
                    }
                }

                switch (type) {
                    case "CABLE_DATA" -> {
                        JsonElement bestValue = null;
                        long maxPower = 0;
                        long myCurrentPower = gateObj.has("power") ? gateObj.get("power").getAsLong() : 0L;

                        JsonElement currentOut = gateObj.has("current_out") ? gateObj.get("current_out") : null;
                        if (currentOut != null && currentOut.isJsonNull()) currentOut = null;

                        for (BlockFace face : BlockFace.values()) {
                            if (!face.isCartesian()) continue;
                            Block neighbor = gate.getRelative(face);
                            String neighborKey = GateUtils.locToStr(neighbor.getLocation());
                            JsonObject neighborObj = manager.getGate(neighborKey);

                            if (neighborObj == null) continue;

                            String nType = neighborObj.has("type") ? neighborObj.get("type").getAsString() : "";

                            if (!nType.equals("CABLE_DATA")) {
                                boolean isActive = neighborObj.has("state") && neighborObj.get("state").getAsBoolean();
                                if (!isActive) continue;
                            }

                            JsonElement incoming = GateUtils.getDataFrom(neighbor, plugin);
                            if (incoming == null || incoming.isJsonNull()) continue;

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
                                neighborPower = neighborObj.has("power") ? neighborObj.get("power").getAsLong() : 0L;
                            }

                            if (GateUtils.areJsonElementsEqual(incoming, currentOut) && neighborPower <= myCurrentPower) {
                                continue;
                            }

                            if (neighborPower > maxPower) {
                                maxPower = neighborPower;
                                bestValue = incoming.deepCopy();
                            }
                        }

                        long newPower = Math.max(0, maxPower - 1);

                        boolean valueChanged = (currentOut == null && bestValue != null) ||
                                (currentOut != null && !GateUtils.areJsonElementsEqual(currentOut, bestValue));
                        boolean powerChanged = (newPower != myCurrentPower);

                        if (valueChanged || powerChanged) {
                            if (bestValue == null) {
                                gateObj.remove("current_out");
                            } else {
                                gateObj.add("current_out", bestValue.deepCopy());
                            }
                            gateObj.addProperty("power", newPower);

                            if (plugin.isDebugEnabled() && valueChanged) {
                                String newValStr = (bestValue == null) ? "NONE" : bestValue.toString();
                                Bukkit.getConsoleSender().sendMessage(
                                        AstraRS.DEBUG_PREFIX + "§9CABLE_DATA §7at §e" + key +
                                                " §7changed signal: §b" + newValStr + " §7(Power: §3" + newPower + "§7)"
                                );
                            }
                        }

                        if (bestValue != null && newPower > 0) {
                            Location pLoc = gate.getLocation().clone().add(0.5, 1.1, 0.5);
                            gate.getWorld().spawnParticle(Particle.REDSTONE, pLoc, 2, 0, 0, 0, 0, new Particle.DustOptions(Color.AQUA, 1.0F));
                        }
                    }

                    case "DISPLAY" -> {
                        if (!plugin.isTextDisplaySupported()) return;

                        Location gateLoc = gate.getLocation();
                        if (!gateLoc.getChunk().isLoaded()) return;

                        JsonElement incoming = GateUtils.getDataFrom(ctx.dirs().backBlock(), plugin);
                        String rawData = (incoming == null || incoming.isJsonNull()) ? "" : incoming.getAsString();

                        String oldUuidStr = gateObj.has("displayUUID") ? gateObj.get("displayUUID").getAsString() : "";
                        String uuidStr = GateUtils.validateDisplay(gateObj, gateLoc);
                        gateObj.addProperty("displayUUID", uuidStr);

                        JsonElement lastOut = gateObj.has("current_out") ? gateObj.get("current_out") : null;
                        boolean isNewHologram = !uuidStr.equals(oldUuidStr);
                        boolean dataChanged = !GateUtils.areJsonElementsEqual(incoming, lastOut);

                        if (dataChanged || isNewHologram) {
                            if (uuidStr.isEmpty()) return;

                            // Sprawdzamy null jako jedyny wyznacznik braku danych
                            String formattedData;
                            if (incoming == null || incoming.isJsonNull()) {
                                formattedData = ""; // Brak danych (czyścimy)
                            } else {
                                formattedData = rawData.isEmpty() ? "" : "§f" + rawData; // Pusty string to też dane, ale wyświetlamy czysto
                            }

                            GateUtils.updateDisplayNumber(uuidStr, formattedData);

                            gateObj.add("current_out", incoming == null ? JsonNull.INSTANCE : incoming.deepCopy());

                            if (plugin.isDebugEnabled()) {
                                Bukkit.getConsoleSender().sendMessage(
                                        AstraRS.DEBUG_PREFIX + "§fDISPLAY §7at §e" + key +
                                                " §7" + (isNewHologram ? "recreated hologram and set" : "refreshed") +
                                                " text to: " + formattedData
                                );
                            }
                        }
                    }

                    case "TRANSISTOR" -> {
                        JsonElement incoming = GateUtils.getDataFrom(ctx.dirs().backBlock(), plugin);
                        boolean isBlocked = ctx.pLeft() || ctx.pRight();

                        JsonElement result = (isBlocked || incoming == null || incoming.isJsonNull()) ? null : incoming.deepCopy();
                        boolean hasPower = GateUtils.isDataSignalActive(result);

                        JsonElement lastOut = gateObj.has("current_out") ? gateObj.get("current_out") : null;
                        boolean lastState = ctx.currentState();

                        if (!GateUtils.areJsonElementsEqual(result, lastOut)) {
                            if (plugin.isDebugEnabled()) {
                                String statusColor = isBlocked ? "§c[BLOCKED]" : "§a[FLOW]";
                                Bukkit.getConsoleSender().sendMessage(
                                        AstraRS.DEBUG_PREFIX + "§6TRANSISTOR §7at §e" + key +
                                                " " + statusColor + " §7Back: " + (incoming == null ? "NONE" : incoming) +
                                                " §7-> Output: " + (result == null ? "NONE" : result)
                                );
                            }
                            gateObj.add("current_out", result == null ? JsonNull.INSTANCE : result.deepCopy());
                        }

                        if (hasPower != lastState) {
                            gateObj.addProperty("state", hasPower);
                            GateUtils.updateOutput(plugin, key, ctx.dirs().targetBlock(), hasPower);
                        }
                    }

                    case "DISK" -> {
                        boolean reset = ctx.pRight() || ctx.pLeft();
                        JsonElement currentStored = gateObj.has("value") ? gateObj.get("value") : null;
                        if (currentStored != null && currentStored.isJsonNull()) currentStored = null;
                        boolean previousState = ctx.currentState();

                        if (reset) {
                            if (currentStored != null || previousState) {
                                gateObj.add("value", JsonNull.INSTANCE);
                                gateObj.add("current_out", JsonNull.INSTANCE);
                                gateObj.addProperty("state", false);
                                GateUtils.updateOutput(plugin, key, ctx.dirs().targetBlock(), false);

                                if (plugin.isDebugEnabled()) {
                                    Bukkit.getConsoleSender().sendMessage(AstraRS.DEBUG_PREFIX + "§dDISK §e" + key + " §cRESET BY side signal!");
                                }
                                currentStored = null;
                            }
                        } else {
                            JsonElement incoming = GateUtils.getDataFrom(ctx.dirs().backBlock(), plugin);
                            if (incoming != null && !incoming.isJsonNull()) {
                                if (!GateUtils.areJsonElementsEqual(incoming, currentStored)) {
                                    currentStored = incoming.deepCopy();
                                    gateObj.add("value", currentStored.deepCopy());
                                    if (plugin.isDebugEnabled()) {
                                        Bukkit.getConsoleSender().sendMessage(AstraRS.DEBUG_PREFIX + "§dDISK §e" + key + " §7saved new value: §b" + currentStored);
                                    }
                                }
                            }
                        }

                        boolean hasState = GateUtils.isDataSignalActive(currentStored);
                        previousState = gateObj.has("state") && gateObj.get("state").getAsBoolean();

                        if (hasState != previousState) {
                            gateObj.addProperty("state", hasState);
                            GateUtils.updateOutput(plugin, key, ctx.dirs().targetBlock(), hasState);
                        }

                        JsonElement expectedOut = (currentStored == null) ? JsonNull.INSTANCE : currentStored;
                        JsonElement currentOut = gateObj.has("current_out") ? gateObj.get("current_out") : null;

                        if (!GateUtils.areJsonElementsEqual(expectedOut, currentOut)) {
                            gateObj.add("current_out", expectedOut.deepCopy());
                        }
                    }

                    case "RAM" -> {
                        boolean reset = ctx.pLeft();
                        boolean power = ctx.pRight();

                        JsonElement currentStored = gateObj.has("value") ? gateObj.get("value") : null;
                        if (currentStored != null && currentStored.isJsonNull()) currentStored = null;
                        boolean previousState = ctx.currentState();

                        if (reset || !power) {
                            if (currentStored != null || previousState) {
                                GateUtils.updateOutput(plugin, key, ctx.dirs().targetBlock(), false);

                                if (plugin.isDebugEnabled()) {
                                    Bukkit.getConsoleSender().sendMessage(AstraRS.DEBUG_PREFIX + "§dRAM §e" + key + " §cRESET BY side signal!");
                                }
                                currentStored = null;
                                gateObj.add("value", JsonNull.INSTANCE);
                            }
                        } else {
                            JsonElement incoming = GateUtils.getDataFrom(ctx.dirs().backBlock(), plugin);
                            if (incoming != null && !incoming.isJsonNull()) {
                                if (!GateUtils.areJsonElementsEqual(incoming, currentStored)) {
                                    currentStored = incoming.deepCopy();
                                    gateObj.add("value", currentStored.deepCopy());
                                    if (plugin.isDebugEnabled()) {
                                        Bukkit.getConsoleSender().sendMessage(AstraRS.DEBUG_PREFIX + "§dRAM §e" + key + " §7saved new value: §b" + currentStored);
                                    }
                                }
                            }
                        }

                        boolean hasState = GateUtils.isDataSignalActive(currentStored);
                        previousState = gateObj.has("state") && gateObj.get("state").getAsBoolean();

                        if (hasState != previousState) {
                            gateObj.addProperty("state", hasState);
                            GateUtils.updateOutput(plugin, key, ctx.dirs().targetBlock(), hasState);
                        }

                        JsonElement expectedOut = (currentStored == null) ? JsonNull.INSTANCE : currentStored;
                        JsonElement currentOut = gateObj.has("current_out") ? gateObj.get("current_out") : null;

                        if (!GateUtils.areJsonElementsEqual(expectedOut, currentOut)) {
                            gateObj.add("current_out", expectedOut.deepCopy());
                        }
                    }

                    case "BATTERY" -> {
                        long charge = gateObj.has("charge") ? gateObj.get("charge").getAsLong() : 0L;
                        long lastChargeTick = gateObj.has("last_charge_tick") ? gateObj.get("last_charge_tick").getAsLong() : 0L;
                        long lastDecay = gateObj.has("last_decay") ? gateObj.get("last_decay").getAsLong() : 0L;
                        long currentTime = System.currentTimeMillis();

                        long decaySeconds = gateObj.has("decay_seconds") ? gateObj.get("decay_seconds").getAsLong() : 60L;
                        long decayInterval = decaySeconds * 1000L;

                        long chargeSeconds = gateObj.has("charge_seconds") ? gateObj.get("charge_seconds").getAsLong() : 10L;
                        long chargeInterval = chargeSeconds * 1000L;

                        if (lastDecay == 0L) {
                            gateObj.addProperty("last_decay", currentTime);
                            lastDecay = currentTime;
                        }

                        JsonElement vBack = GateUtils.getDataFrom(ctx.dirs().backBlock(), plugin);
                        boolean isInputPowered = ctx.pBack() || GateUtils.isDataSignalActive(vBack);

                        if (currentTime - lastDecay >= decayInterval) {
                            if (isInputPowered) {
                                gateObj.addProperty("last_decay", currentTime);
                            } else if (charge > 0L) {
                                charge = Math.max(0L, charge - 1L);
                                gateObj.addProperty("charge", charge);
                                gateObj.addProperty("last_decay", currentTime);
                            } else {
                                gateObj.addProperty("last_decay", lastDecay + decayInterval);
                            }
                        }

                        if (isInputPowered && (currentTime - lastChargeTick >= chargeInterval)) {
                            if (charge < 100L) {
                                charge = Math.min(100L, charge + 1L);
                                gateObj.addProperty("charge", charge);
                                gateObj.addProperty("last_charge_tick", currentTime);
                            }
                        }

                        boolean hasPower = charge > 0L;
                        boolean previousState = ctx.currentState();

                        if (hasPower != previousState) {
                            gateObj.addProperty("state", hasPower);
                            GateUtils.updateOutput(plugin, key, ctx.dirs().targetBlock(), hasPower);
                        }

                        String chargeString = charge + "%";
                        JsonElement currentOut = gateObj.has("current_out") ? gateObj.get("current_out") : null;
                        if (currentOut == null || !currentOut.isJsonPrimitive() || !currentOut.getAsString().equals(chargeString)) {
                            gateObj.addProperty("current_out", chargeString);
                        }
                    }

                    case "DATA_DETECTOR" -> {
                        JsonElement rawData = GateUtils.getDataFrom(ctx.dirs().backBlock(), plugin);
                        boolean hasData = (rawData != null && !rawData.isJsonNull());

                        JsonElement currentOut = gateObj.has("current_out") ? gateObj.get("current_out") : null;
                        boolean lastHasData = currentOut != null && currentOut.isJsonPrimitive() && currentOut.getAsBoolean();

                        if (hasData != lastHasData) {
                            if (plugin.isDebugEnabled()) {
                                Bukkit.getConsoleSender().sendMessage(
                                        AstraRS.DEBUG_PREFIX + "§bDATA_DETECTOR §7at §e" + key + " §7-> Detected: " + (hasData ? "§aYES" : "§cNO")
                                );
                            }

                            gateObj.addProperty("current_out", hasData);
                            gateObj.addProperty("state", hasData);
                            GateUtils.updateOutput(plugin, key, ctx.dirs().targetBlock(), hasData);
                        }
                    }
                }
            });
        }
    }
}