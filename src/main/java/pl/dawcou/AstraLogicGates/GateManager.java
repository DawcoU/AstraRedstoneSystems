package pl.dawcou.AstraLogicGates;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class GateManager {

    private final AstraLogicGates plugin;
    private int globalTickCounter = 0;
    private final Random random = new Random();
    private final Set<String> dataProcessedInThisTick = new HashSet<>();
    private final Set<String> sendGuard = new HashSet<>();
    private final Map<String, BukkitTask> repeaterTasks = new HashMap<>();

    public static final List<Material> ALLOWED_BRAMKI = Arrays.asList(
            Material.RED_CONCRETE, Material.ORANGE_CONCRETE, Material.YELLOW_CONCRETE,
            Material.GRAY_CONCRETE, Material.LIGHT_BLUE_CONCRETE, Material.PURPLE_CONCRETE,
            Material.MAGENTA_CONCRETE, Material.WHITE_CONCRETE, Material.PINK_CONCRETE,
            Material.BLUE_CONCRETE, Material.GREEN_CONCRETE, Material.LIME_CONCRETE,
            Material.CYAN_CONCRETE, Material.BROWN_CONCRETE, Material.IRON_BLOCK,
            Material.GOLD_BLOCK, Material.EMERALD_BLOCK, Material.LAPIS_BLOCK
    );

    public GateManager(AstraLogicGates plugin) { this.plugin = plugin; }

    public void checkGates() {
        dataProcessedInThisTick.clear();
        globalTickCounter++;
        boolean isActionTick = (globalTickCounter % 2 == 0);
        FileConfiguration config = plugin.getGatesConfig();

        ConfigurationSection gatesSection = config.getConfigurationSection("gates");
        if (gatesSection == null) return;

        for (String key : gatesSection.getKeys(false)) {
            String path = "gates." + key;
            Location loc = GateUtils.strToLoc(key);

            if (loc == null || !ALLOWED_BRAMKI.contains(loc.getBlock().getType())) continue;

            Block gate = loc.getBlock();
            String type = config.getString(path + ".type", "");
            String op = config.getString(path + ".mode", "");

            // ... (walidacja typów: COUNTER, NUMBER_GATE, COMPARATOR, SENSOR, CLOCK...)
            if ("COUNTER".equals(type)) {
                int limit = config.getInt(path + ".score_limit");

                if (limit < 1 || limit > 100) continue;

            } else if ("NUMBER_GATE".equals(type)) {
                int value = config.getInt(path + ".value");

                if (value < 0) continue;

            } else if ("COMPARATOR".equals(type)) {
                if (op == null || op.isEmpty()) continue;

                if (!op.equals(">")
                        && !op.equals("<")
                        && !op.equals("==")
                        && !op.equals("!=")
                        && !op.equals(">=")
                        && !op.equals("<=")) continue;

            } else if ("SENSOR".equals(type)) {
                int radius = config.getInt(path + ".interval");

                if (radius < 1 || radius > 15) continue;

            } else if ("CLOCK".equals(type) || "CLOCK_GATE".equals(type)) {
                int interval = config.getInt(path + ".interval");

                if (interval < 5 || interval > 200) continue;

            } else if ("REPEATER".equals(type)) {
                int interval = config.getInt(path + ".interval");

                if (interval < 1 || interval > 200) continue;

            } else if ("SENDER".equals(type) || "RECEIVER".equals(type)) {
                String channel = config.getString(path + ".channel");

                if (channel == null || channel.isEmpty()) continue;
            }

            String outRaw = config.getString(path + ".out");
            if (outRaw == null) continue;
            BlockFace out = BlockFace.valueOf(outRaw);
            Block target = gate.getRelative(out);
            BlockFace back = out.getOppositeFace();
            BlockFace s1 = GateUtils.rotate90(out);
            BlockFace s2 = s1.getOppositeFace();

            boolean currentState = config.getBoolean(path + ".state", false);

            if (!("SYNCHRONIZER".equals(type) || "SENDER".equals(type))) {
                GateUtils.spawnStatusParticle(gate, out, currentState);
            }

            // ... (Particle i logika BOOSTER, SENDER, RECEIVER, SENSOR, SYNCHRONIZER, CLOCK/REPEATER, COUNTER, NUMBER_GATE, BOOLEAN_GATE - bez zmian)
            if (type.matches("OR|NOR|AND|NAND|XOR|XNOR|LATCH|NIMPLY|COUNTER|MEMORY_CELL|MEMORY_READ|COMPARATOR|MATH|LINKER")) {
                GateUtils.spawnStatusParticle(gate, s1, GateUtils.getPowerAt(gate.getRelative(s1)) > 0);
                GateUtils.spawnStatusParticle(gate, s2, GateUtils.getPowerAt(gate.getRelative(s2)) > 0);
            }
            if (type.matches("NOT|OR|NOR|CLOCK_GATE|REPEATER|BOOSTER|TFF|RANDOM|NIMPLY|COUNTER|SENDER|MEMORY_CELL|MEMORY_READ|NUMBER_GATE|BOOLEAN_GATE|VARIABLE_GATE")) {
                GateUtils.spawnStatusParticle(gate, back, GateUtils.getPowerAt(gate.getRelative(back)) > 0);
            }
            if ("BOOSTER".equals(type)) {
                boolean p = GateUtils.getPowerAt(gate.getRelative(back)) > 0;
                if (p != currentState) {
                    GateUtils.updateOutput(plugin, path, target, p);
                    config.set(path + ".state", p);
                }
                continue;
            }
            if ("SENDER".equals(type)) {
                String rawChannels = config.getString(path + ".channel", "default");
                boolean hasPower = GateUtils.getPowerAt(gate.getRelative(back)) > 0;
                String[] splitChannels = rawChannels.split(",");
                boolean hasListener = false;
                for (String chan : splitChannels) {
                    String trimmed = chan.trim();
                    config.set("channels." + trimmed, hasPower);
                    if (config.getBoolean("active_channels." + trimmed, false)) hasListener = true;
                    config.set("active_channels." + trimmed, null);
                }
                boolean isTransmitting = hasPower && hasListener;
                GateUtils.spawnStatusParticle(gate, out, isTransmitting);
                GateUtils.spawnStatusParticle(gate, back, hasPower);
                config.set(path + ".state", false);
                continue;
            }
            if ("RECEIVER".equals(type)) {
                String channel = config.getString(path + ".channel", "default").replace(",", "").replace(" ", "");
                config.set("active_channels." + channel, true);
                boolean remotePower = config.getBoolean("channels." + channel, false);
                if (remotePower != currentState) {
                    GateUtils.updateOutput(plugin, path, target, remotePower);
                    config.set(path + ".state", remotePower);
                }
                continue;
            }
            if ("SENSOR".equals(type)) {
                int radius = config.getInt(path + ".interval", 5);
                boolean found = false;
                for (Entity entity : gate.getWorld().getNearbyEntities(gate.getLocation(), radius, radius, radius)) {
                    if (entity instanceof Player) { found = true; break; }
                }
                if (found != currentState) {
                    GateUtils.updateOutput(plugin, path, target, found);
                    config.set(path + ".state", found);
                }
                continue;
            }
            if ("SYNCHRONIZER".equals(type)) {
                BlockFace left = GateUtils.rotate90(GateUtils.rotate90(GateUtils.rotate90(out)));
                BlockFace right = GateUtils.rotate90(out);
                boolean pA = GateUtils.getPowerAt(gate.getRelative(left).getRelative(back)) > 0;
                boolean pB = GateUtils.getPowerAt(gate.getRelative(right).getRelative(back)) > 0;
                boolean ready = pA && pB;
                if (ready != currentState) {
                    GateUtils.updateOutput(plugin, path + "_L", gate.getRelative(left).getRelative(out), ready);
                    GateUtils.updateOutput(plugin, path + "_R", gate.getRelative(right).getRelative(out), ready);
                    config.set(path + ".state", ready);
                }
                GateUtils.spawnStatusParticle(gate.getRelative(left), back, pA);
                GateUtils.spawnStatusParticle(gate.getRelative(right), back, pB);
                GateUtils.spawnStatusParticle(gate.getRelative(left), out, ready);
                GateUtils.spawnStatusParticle(gate.getRelative(right), out, ready);
                continue;
            }
            if (type.matches("CLOCK|CLOCK_GATE|REPEATER")) {
                int interval = config.getInt(path + ".interval", 20);
                boolean hasPower = GateUtils.getPowerAt(gate.getRelative(back)) > 0;
                if ("REPEATER".equals(type)) {
                    boolean in = GateUtils.getPowerAt(gate.getRelative(back)) > 0;
                    if (in != config.getBoolean(path + ".last_in", false)) {
                        config.set(path + ".last_in", in);
                        BukkitTask oldTask = repeaterTasks.get(path);
                        if (oldTask != null) { oldTask.cancel(); repeaterTasks.remove(path); }
                        int delay = config.getInt(path + ".interval", 20);
                        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            config.set(path + ".state", in);
                            GateUtils.updateOutput(plugin, path, target, in);
                            repeaterTasks.remove(path);
                        }, delay);
                        repeaterTasks.put(path, task);
                    }
                    continue;
                }
                boolean enabled = !"CLOCK_GATE".equals(type) || hasPower;
                if (!enabled) {
                    if (currentState) { GateUtils.updateOutput(plugin, path, target, false); config.set(path + ".state", false); }
                    config.set(path + ".next_tick", 0);
                } else {
                    int nt = config.getInt(path + ".next_tick") + 1;
                    if (nt >= interval) {
                        boolean newState = !currentState;
                        GateUtils.updateOutput(plugin, path, target, newState);
                        config.set(path + ".state", newState);
                        nt = 0;
                    }
                    config.set(path + ".next_tick", nt);
                }
                continue;
            }
            if ("COUNTER".equals(type)) {
                BlockFace right = GateUtils.rotate90(out);
                BlockFace left = GateUtils.rotate90(GateUtils.rotate90(GateUtils.rotate90(out)));
                int incomingVal = config.getInt(path + ".val_left", 0);
                int lastVal = config.getInt(path + ".last_val_left", 0);
                int count = config.getInt(path + ".count");
                int limit = config.getInt(path + ".score_limit");
                boolean changed = false;
                if (incomingVal > lastVal) {
                    int diff = incomingVal - lastVal;
                    count = Math.min(limit, count + diff);
                    changed = true;
                }
                config.set(path + ".last_val_left", incomingVal);
                if (dataProcessedInThisTick.contains(path)) {
                    config.set(path + ".last_back", GateUtils.getPowerAt(gate.getRelative(back)) > 0);
                    config.set(path + ".last_left", GateUtils.getPowerAt(gate.getRelative(left)) > 0);
                    config.set(path + ".last_right", GateUtils.getPowerAt(gate.getRelative(right)) > 0);
                    continue;
                }
                boolean pB = GateUtils.getPowerAt(gate.getRelative(back)) > 0, pL = GateUtils.getPowerAt(gate.getRelative(left)) > 0, pR = GateUtils.getPowerAt(gate.getRelative(right)) > 0;
                boolean lB = config.getBoolean(path + ".last_back"), lL = config.getBoolean(path + ".last_left"), lR = config.getBoolean(path + ".last_right");
                if (pB && !lB && count < limit) { count++; changed = true; }
                if (pL && !lL && count > 0) { count--; changed = true; }
                if (pR && !lR) { count = 0; changed = true; }
                config.set(path + ".last_back", pB); config.set(path + ".last_left", pL); config.set(path + ".last_right", pR);
                if (changed) {
                    config.set(path + ".count", count);
                    boolean finalState = (count >= limit);
                    GateUtils.updateOutput(plugin, path, target, finalState);
                    config.set(path + ".state", finalState);
                }
                continue;
            }
            if ("NUMBER_GATE".equals(type)) {
                boolean pB = GateUtils.getPowerAt(gate.getRelative(back)) > 0;
                boolean lB = config.getBoolean(path + ".last_back");
                if (config.getBoolean(path + ".state") != pB) {
                    GateUtils.updateOutput(plugin, path, target, pB);
                    config.set(path + ".state", pB);
                }
                if (pB != lB) {
                    int valueToSend = pB ? config.getInt(path + ".value") : 0;
                    String linkStr = config.getString(path + ".target_link");
                    if (linkStr != null) {
                        Location targetLoc = GateUtils.strToLoc(linkStr);
                        if (targetLoc != null) sendDataToGate(targetLoc.getBlock(), valueToSend, gate);
                    } else {
                        sendDataToGate(gate.getRelative(out), valueToSend, gate);
                    }
                }
                config.set(path + ".last_back", pB);
                continue;
            }
            if ("BOOLEAN_GATE".equals(type)) {
                boolean pB = GateUtils.getPowerAt(gate.getRelative(back)) > 0;
                boolean lB = config.getBoolean(path + ".last_back");
                if (pB != lB) {
                    String link = config.getString(path + ".target_link");
                    if (link != null) {
                        Location targetLoc = GateUtils.strToLoc(link);
                        if (targetLoc != null) sendDataToGate(targetLoc.getBlock(), pB ? 1 : 0, gate);
                    }
                    GateUtils.updateOutput(plugin, path, target, pB);
                    config.set(path + ".state", pB);
                    config.set(path + ".last_back", pB);
                }
                continue;
            }

            // 🔥 FIX: VARIABLE_GATE LOGIC
            if ("VARIABLE_GATE".equals(type)) {
                // Synchronizacja bufora wejściowego
                if (config.contains(path + ".val_left")) {
                    int incoming = config.getInt(path + ".val_left");
                    config.set(path + ".value", incoming);
                    config.set(path + ".val_left", null);
                }

                int storedValue = config.getInt(path + ".value", 0);
                boolean shouldBeOn = (storedValue > 0);

                // Aktualizacja bloku, jeśli stan się nie zgadza
                if (config.getBoolean(path + ".state") != shouldBeOn) {
                    GateUtils.updateOutput(plugin, path, target, shouldBeOn);
                    config.set(path + ".state", shouldBeOn);

                    // Jeśli wartość się zmieniła w tym ticku, wysyłamy ją dalej (zabezpieczenie)
                    if (!dataProcessedInThisTick.contains(path)) {
                        dataProcessedInThisTick.add(path);
                        sendDataToGate(target, storedValue, gate);

                        String link = config.getString(path + ".target_link");
                        if (link != null) {
                            Location targetLoc = GateUtils.strToLoc(link);
                            if (targetLoc != null) sendDataToGate(targetLoc.getBlock(), storedValue, gate);
                        }
                    }
                }

                // Zapisujemy tylko ostatni stan wejścia back dla cząsteczek, ale nie blokujemy nim wysyłki
                config.set(path + ".last_back", GateUtils.getPowerAt(gate.getRelative(back)) > 0);
                continue;
            }

            // ... (Reszta typów MATH, COMPARATOR, LINKER, etc. - bez zmian)
            if ("MATH".equals(type)) {
                int vL = config.getInt(path + ".val_left", 0);
                int vR = config.getInt(path + ".val_right", 0);
                String m = config.getString(path + ".mode", "ADD");

                int result = "SUB".equals(m) ? Math.max(0, vL - vR) : vL + vR;
                int lastResult = config.getInt(path + ".value", -1);
                if (result != lastResult) {
                    config.set(path + ".value", result);
                    GateUtils.updateOutput(plugin, path, target, result > 0);
                    String tLink = config.getString(path + ".target_link");
                    if (tLink != null && !tLink.isEmpty()) {
                        Location linkLoc = GateUtils.strToLoc(tLink);
                        if (linkLoc != null) sendDataToGate(linkLoc.getBlock(), result, gate);
                    }
                    sendDataToGate(target, result, gate);
                }
                continue;
            }
            if ("COMPARATOR".equals(type)) {
                int vL = config.getInt(path + ".val_left", 0);
                int vR = config.getInt(path + ".val_right", 0);
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
                boolean lastState = config.getBoolean(path + ".state", false);
                if (result != lastState) {
                    config.set(path + ".state", result);
                    GateUtils.updateOutput(plugin, path, target, result);
                    sendDataToGate(target, result ? 1 : 0, gate);
                    String tLink = config.getString(path + ".target_link");
                    if (tLink != null && !tLink.isEmpty()) {
                        Location linkLoc = GateUtils.strToLoc(tLink);
                        if (linkLoc != null) sendDataToGate(linkLoc.getBlock(), result ? 1 : 0, gate);
                    }
                }
                continue;
            }
            if ("DECODER".equals(type)) {
                boolean state = config.getBoolean(path + ".state", false);
                GateUtils.updateOutput(plugin, path, gate.getRelative(out), state);
                continue;
            }
            if ("LINKER".equals(type)) {
                Block sideR = gate.getRelative(s1);
                int vL = config.getInt(path + ".val_left", 0);
                int vR = GateUtils.getPowerAt(sideR);
                int result = (vR > 0) ? 0 : vL;
                int lastResult = config.getInt(path + ".value", -1);
                if (result != lastResult) {
                    config.set(path + ".value", result);
                    config.set(path + ".state", result > 0);
                    GateUtils.updateOutput(plugin, path, target, result > 0);
                    sendDataToGate(target, result, gate);
                    String tLink = config.getString(path + ".target_link");
                    if (tLink != null && !tLink.isEmpty()) {
                        Location linkLoc = GateUtils.strToLoc(tLink);
                        if (linkLoc != null) sendDataToGate(linkLoc.getBlock(), result, gate);
                    }
                }
                continue;
            }

            if (isActionTick) {
                boolean nextState = currentState;
                if (type.matches("NOT|OR|NOR")) {
                    boolean p = GateUtils.isInputPowered(gate, out);
                    nextState = "NOT".equals(type) ? !p : ("OR".equals(type) == p);
                } else if (type.matches("AND|NAND|XOR|XNOR")) {
                    boolean p1 = GateUtils.getPowerAt(gate.getRelative(s1)) > 0;
                    boolean p2 = GateUtils.getPowerAt(gate.getRelative(s2)) > 0;
                    if ("AND".equals(type)) nextState = (p1 && p2);
                    else if ("NAND".equals(type)) nextState = !(p1 && p2);
                    else if ("XOR".equals(type)) nextState = (p1 ^ p2);
                    else if ("XNOR".equals(type)) nextState = (p1 == p2);
                } else if ("LATCH".equals(type)) {
                    boolean s = GateUtils.getPowerAt(gate.getRelative(s1)) > 0, r = GateUtils.getPowerAt(gate.getRelative(s2)) > 0;
                    if (s) nextState = true; else if (r) nextState = false;
                } else if ("MEMORY_CELL".equals(type)) {
                    BlockFace right = GateUtils.rotate90(out);
                    BlockFace left = GateUtils.rotate90(GateUtils.rotate90(GateUtils.rotate90(out)));
                    boolean dataIn = GateUtils.getPowerAt(gate.getRelative(back)) > 0;
                    boolean saveIn = GateUtils.getPowerAt(gate.getRelative(right)) > 0;
                    boolean resetIn = GateUtils.getPowerAt(gate.getRelative(left)) > 0;
                    boolean stored = config.getBoolean(path + ".stored_state", false);
                    if (saveIn && dataIn) { stored = true; config.set(path + ".stored_state", true); }
                    if (resetIn) { stored = false; config.set(path + ".stored_state", false); }
                    nextState = stored;
                } else if ("MEMORY_READ".equals(type)) {
                    BlockFace right = GateUtils.rotate90(out);
                    BlockFace left = GateUtils.rotate90(GateUtils.rotate90(GateUtils.rotate90(out)));
                    boolean signalFromBack = GateUtils.getPowerAt(gate.getRelative(back)) > 0;
                    boolean readIn = GateUtils.getPowerAt(gate.getRelative(right)) > 0 || GateUtils.getPowerAt(gate.getRelative(left)) > 0;
                    nextState = (signalFromBack && readIn);
                } else if ("TFF".equals(type) || "RANDOM".equals(type)) {
                    boolean in = GateUtils.getPowerAt(gate.getRelative(back)) > 0;
                    if (in && !config.getBoolean(path + ".lastInput")) {
                        nextState = "TFF".equals(type) ? !currentState : random.nextBoolean();
                    }
                    config.set(path + ".lastInput", in);
                } else if ("NIMPLY".equals(type)) {
                    nextState = GateUtils.getPowerAt(gate.getRelative(back)) > 0 && !(GateUtils.getPowerAt(gate.getRelative(s1)) > 0 || GateUtils.getPowerAt(gate.getRelative(s2)) > 0);
                }
                if (nextState != currentState) {
                    GateUtils.updateOutput(plugin, path, target, nextState);
                    config.set(path + ".state", nextState);
                }
            }
        }
        plugin.saveGates();
    }

    public void sendDataToGate(Block target, int value, Block from) {
        FileConfiguration cfg = plugin.getGatesConfig();
        if (target == null || from == null || target.equals(from)) return;

        String path = "gates." + GateUtils.locToStr(target.getLocation());
        if (!cfg.contains(path)) return;

        String targetType = cfg.getString(path + ".type");
        if (targetType == null) return;
        if (!sendGuard.add(path)) return;

        try {
            String outStr = cfg.getString(path + ".out");
            if (outStr == null) return;
            BlockFace targetOut = BlockFace.valueOf(outStr);
            Block finalOutputBlock = target.getRelative(targetOut);

            // ... (Logika MATH, COUNTER - bez zmian)
            if ("MATH".equals(targetType)) {
                BlockFace left = GateUtils.rotate90(GateUtils.rotate90(GateUtils.rotate90(targetOut)));
                Block leftBlock = target.getRelative(left);
                Block rightBlock = target.getRelative(GateUtils.rotate90(targetOut));
                if (from.equals(leftBlock)) cfg.set(path + ".val_left", value);
                else if (from.equals(rightBlock)) cfg.set(path + ".val_right", value);
            }
            if ("COUNTER".equals(targetType)) {
                int currentCount = cfg.getInt(path + ".count");
                int limit = cfg.getInt(path + ".score_limit");
                BlockFace left = GateUtils.rotate90(GateUtils.rotate90(GateUtils.rotate90(targetOut)));
                int newCount = from.getLocation().equals(target.getRelative(left).getLocation()) ? Math.max(0, currentCount - value) : Math.min(limit, currentCount + value);
                cfg.set(path + ".count", newCount);
                boolean finalState = (newCount >= limit);
                GateUtils.updateOutput(plugin, path, finalOutputBlock, finalState);
                cfg.set(path + ".state", finalState);
                sendDataToGate(finalOutputBlock, newCount, target);
            }

            // 🔥 FIX: VARIABLE_GATE SEND DATA
            if ("VARIABLE_GATE".equals(targetType)) {
                // 1. Natychmiast zapisujemy wartość i stan
                cfg.set(path + ".value", value);
                boolean finalState = (value > 0);
                cfg.set(path + ".state", finalState);
                GateUtils.updateOutput(plugin, path, finalOutputBlock, finalState);

                // 2. NATYCHMIASTOWA WYSYŁKA DALEJ (bez czekania na cokolwiek)
                // Fizycznie przed siebie:
                sendDataToGate(finalOutputBlock, value, target);

                // I przez link bezprzewodowy:
                String link = cfg.getString(path + ".target_link");
                if (link != null && !link.isEmpty()) {
                    Location targetLoc = GateUtils.strToLoc(link);
                    if (targetLoc != null) {
                        sendDataToGate(targetLoc.getBlock(), value, target);
                    }
                }
            }

            // ... (Reszta COMPARATOR, DECODER, LINKER - bez zmian)
            if ("COMPARATOR".equals(targetType)) {
                BlockFace left = GateUtils.rotate90(GateUtils.rotate90(GateUtils.rotate90(targetOut)));
                if (from.getLocation().equals(target.getRelative(left).getLocation())) cfg.set(path + ".val_left", value);
                else cfg.set(path + ".val_right", value);
            }
            if ("DECODER".equals(targetType)) {
                int targetDigit = cfg.getInt(path + ".target", 0);
                boolean result = (value == targetDigit);
                cfg.set(path + ".state", result);
                GateUtils.updateOutput(plugin, path, finalOutputBlock, result);
                String linkLocStr = cfg.getString(path + ".target_link");
                if (linkLocStr != null && !linkLocStr.isEmpty()) {
                    Location linkLoc = GateUtils.strToLoc(linkLocStr);
                    if (linkLoc != null) sendDataToGate(linkLoc.getBlock(), result ? 1 : 0, target);
                }
                sendDataToGate(finalOutputBlock, result ? 1 : 0, target);
            }
            if ("LINKER".equals(targetType)) {
                BlockFace outFace = BlockFace.valueOf(outStr);
                BlockFace leftFace = GateUtils.rotate90(GateUtils.rotate90(GateUtils.rotate90(outFace)));
                Block sideL = target.getRelative(leftFace);
                boolean isWirelessTarget = false;
                String currentLocStr = GateUtils.locToStr(target.getLocation());
                String senderPath = "gates." + GateUtils.locToStr(from.getLocation());
                if (cfg.contains(senderPath + ".target_link") && currentLocStr.equals(cfg.getString(senderPath + ".target_link"))) isWirelessTarget = true;
                if ((from.getX() == sideL.getX() && from.getZ() == sideL.getZ()) || isWirelessTarget) cfg.set(path + ".val_left", value);
                int vL = cfg.getInt(path + ".val_left", 0);
                int vR = GateUtils.getPowerAt(target.getRelative(GateUtils.rotate90(outFace)));
                int result = (vR > 0) ? 0 : vL;
                cfg.set(path + ".value", result);
                cfg.set(path + ".state", result > 0);
                GateUtils.updateOutput(plugin, path, finalOutputBlock, result > 0);
                sendDataToGate(finalOutputBlock, result, target);
            }
        } finally {
            sendGuard.remove(path);
        }
    }
}