package pl.dawcou.AstraRedstoneSystems.gates;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;

import pl.dawcou.AstraRedstoneSystems.AstraRS;
import pl.dawcou.AstraRedstoneSystems.GateValidator;
import pl.dawcou.AstraRedstoneSystems.GateUtils;

public class TimeGates {

    private final AstraRS plugin;
    private final GateValidator validator;
    private final Map<String, BukkitTask> repeaterTasks = new HashMap<>();

    public TimeGates(AstraRS plugin, GateValidator validator) {
        this.plugin = plugin;
        this.validator = validator;
    }

    public void runTimeGates() {
        FileConfiguration config = plugin.getGatesConfig();
        ConfigurationSection gatesSection = config.getConfigurationSection("gates");
        if (gatesSection == null) return;

        for (String key : gatesSection.getKeys(false)) {
            String path = "gates." + key;
            if (!validator.isValid(path, config)) continue;

            Location loc = GateUtils.strToLoc(key);
            if (loc == null) continue;

            Block gate = loc.getBlock();
            String type = config.getString(path + ".type", "").toUpperCase();

            // UJEDNOLICONE ZMIENNE
            BlockFace out = BlockFace.valueOf(config.getString(path + ".out", "NORTH").toUpperCase());
            BlockFace back = out.getOppositeFace();
            BlockFace s1 = GateUtils.rotate90(out);
            BlockFace s2 = s1.getOppositeFace();

            boolean currentState = config.getBoolean(path + ".state", false);
            Block target = gate.getRelative(out);

            // --- PARTICLE STATUSU ---
            if (type.matches("CLOCK_GATE|REPEATER")) {

                // Wyjście główne (tylko jeśli to nie synchronizer, choć matches i tak go tu nie wpuści)
                GateUtils.spawnStatusParticle(gate, out, currentState);
            }

            if (type.matches("CLOCK_GATE|REPEATER")) {
                boolean pBack = GateUtils.getPowerAt(gate.getRelative(back)) > 0;
                GateUtils.spawnStatusParticle(gate, back, pBack);
            }

            // --- LOGIKA ---
            switch (type) {
                case "SYNCHRONIZER" -> {
                    boolean pS1 = GateUtils.getPowerAt(gate.getRelative(s1).getRelative(back)) > 0;
                    boolean pS2 = GateUtils.getPowerAt(gate.getRelative(s2).getRelative(back)) > 0;
                    boolean ready = pS1 && pS2;

                    if (ready != currentState) {
                        config.set(path + ".state", ready);
                        GateUtils.updateOutput(plugin, path + "_L", gate.getRelative(s1).getRelative(out), ready);
                        GateUtils.updateOutput(plugin, path + "_R", gate.getRelative(s2).getRelative(out), ready);
                    }
                    GateUtils.spawnStatusParticle(gate.getRelative(s1), back, pS1);
                    GateUtils.spawnStatusParticle(gate.getRelative(s2), back, pS2);
                }

                case "REPEATER" -> {
                    boolean in = GateUtils.getPowerAt(gate.getRelative(back)) > 0;
                    if (in != config.getBoolean(path + ".last_in", false)) {
                        config.set(path + ".last_in", in);

                        if (repeaterTasks.containsKey(path)) {
                            repeaterTasks.get(path).cancel();
                        }

                        int delay = config.getInt(path + ".interval", 20);
                        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            config.set(path + ".state", in);
                            GateUtils.updateOutput(plugin, path, target, in);
                            repeaterTasks.remove(path);
                        }, (long) delay);
                        repeaterTasks.put(path, task);
                    }
                }

                case "CLOCK", "CLOCK_GATE" -> {
                    int interval = config.getInt(path + ".interval", 20);
                    boolean hasPower = GateUtils.getPowerAt(gate.getRelative(back)) > 0;
                    boolean enabled = "CLOCK".equals(type) || hasPower;

                    if (!enabled) {
                        if (currentState) {
                            GateUtils.updateOutput(plugin, path, target, false);
                            config.set(path + ".state", false);
                        }
                        config.set(path + ".next_tick", 0);
                    } else {
                        int nt = config.getInt(path + ".next_tick", 0) + 1;
                        if (nt >= interval) {
                            boolean newState = !currentState;
                            config.set(path + ".state", newState);
                            GateUtils.updateOutput(plugin, path, target, newState);
                            nt = 0;
                        }
                        config.set(path + ".next_tick", nt);
                    }
                }
            }
        }
    }
}