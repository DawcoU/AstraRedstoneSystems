package pl.dawcou.AstraRedstoneSystems.gates;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import pl.dawcou.AstraRedstoneSystems.AstraRS;
import pl.dawcou.AstraRedstoneSystems.GateValidator;
import pl.dawcou.AstraRedstoneSystems.GateUtils;

public class MemoryGates {

    private final AstraRS plugin;
    private final GateValidator validator;

    public MemoryGates(AstraRS plugin, GateValidator validator) {
        this.plugin = plugin;
        this.validator = validator;
    }

    public void runMemoryGates() {
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

            // UJEDNOLICONE ZMIENNE KIERUNKOWE
            BlockFace out = BlockFace.valueOf(config.getString(path + ".out", "NORTH").toUpperCase());
            BlockFace back = out.getOppositeFace();
            BlockFace s1 = GateUtils.rotate90(out);
            BlockFace s2 = s1.getOppositeFace();

            Block target = gate.getRelative(out);

            // POBIERANIE SYGNAŁÓW WEJŚCIOWYCH
            boolean pBack = GateUtils.getPowerAt(gate.getRelative(back)) > 0;
            boolean pS1 = GateUtils.getPowerAt(gate.getRelative(s1)) > 0;
            boolean pS2 = GateUtils.getPowerAt(gate.getRelative(s2)) > 0;

            boolean oldState = config.getBoolean(path + ".state", false);
            boolean newState = oldState;

            // --- PARTICLE STATUSU ---
            if (type.matches("LATCH|MEMORY_CELL|MEMORY_READ|TFF|TFF")) {
                GateUtils.spawnStatusParticle(gate, out, oldState);
            }

            // Boki (S1, S2) dla Latchy i komórek pamięci
            if (type.matches("LATCH|MEMORY_CELL|MEMORY_READ")) {
                GateUtils.spawnStatusParticle(gate, s1, pS1);
                GateUtils.spawnStatusParticle(gate, s2, pS2);
            }

            // Tył (BACK) dla wejść danych lub TFF
            if (type.matches("TFF|MEMORY_CELL|MEMORY_READ")) {
                GateUtils.spawnStatusParticle(gate, back, pBack);
            }

            // LOGIKA BRAMEK
            switch (type) {
                case "LATCH" -> {
                    // RS Latch: s1 = Set, s2 = Reset
                    if (pS1) newState = true;
                    else if (pS2) newState = false;
                }
                case "MEMORY_CELL" -> {
                    // D-Latch: Tył = Data, s2 = Write, s1 = Reset
                    if (pS2) newState = pBack;
                    else if (pS1) newState = false;
                }
                case "MEMORY_READ" -> {
                    // Odczyt: pBack (Dane) && (pS1 || pS2) (Sygnał odczytu)
                    newState = (pBack && (pS1 || pS2));
                }
                case "TFF" -> {
                    // Toggle Flip-Flop: Zbocze narastające na tyłach
                    boolean lastIn = config.getBoolean(path + ".lastInput", false);
                    if (pBack && !lastIn) {
                        newState = !oldState;
                    }
                    config.set(path + ".lastInput", pBack);
                }
            }

            if (newState != oldState) {
                config.set(path + ".state", newState);
                GateUtils.updateOutput(plugin, path, target, newState);
            }
        }
    }
}