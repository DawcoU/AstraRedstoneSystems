package pl.dawcou.AstraLogicGates.gates;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import pl.dawcou.AstraLogicGates.AstraLogicGates;
import pl.dawcou.AstraLogicGates.GateValidator;
import pl.dawcou.AstraLogicGates.GateUtils;

public class BasicGates {
    private final AstraLogicGates plugin;
    private final GateValidator validator;

    public BasicGates(AstraLogicGates plugin, GateValidator validator) {
        this.plugin = plugin;
        this.validator = validator;
    }

    public void runBasicGates() {
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

            // POBIERANIE SYGNAŁÓW (pS1 i pS2 to Twoje s1 i s2 w particle)
            boolean pBack = GateUtils.getPowerAt(gate.getRelative(back)) > 0;
            boolean pS1 = GateUtils.getPowerAt(gate.getRelative(s1)) > 0;
            boolean pS2 = GateUtils.getPowerAt(gate.getRelative(s2)) > 0;

            // --- PARTICLE STATUSU ---
            // --- PARTICLE STATUSU ---
            if (type.matches("OR|NOR|AND|NAND|XOR|XNOR|NOT|BUFFER|IMPLY|NIMPLY")) {
                GateUtils.spawnStatusParticle(gate, out, currentState);
            }

            if (type.matches("OR|NOR|AND|NAND|XOR|XNOR|NIMPLY|IMPLY")) {
                GateUtils.spawnStatusParticle(gate, s1, pS1);
                GateUtils.spawnStatusParticle(gate, s2, pS2);
            }
            if (type.matches("NOT|BUFFER|NIMPLY|IMPLY")) {
                GateUtils.spawnStatusParticle(gate, back, pBack);
            }

            // LOGIKA
            boolean newState = false;
            boolean isBasic = true;

            switch (type) {
                case "NOT" -> newState = !pBack;
                case "OR" -> newState = (pS1 || pS2 || pBack);
                case "NOR" -> newState = !(pS1 || pS2 || pBack);
                case "AND" -> newState = (pS1 && pS2);
                case "NAND" -> newState = !(pS1 && pS2);
                case "XOR" -> newState = (pS1 ^ pS2);
                case "XNOR" -> newState = (pS1 == pS2);
                case "IMPLY" -> newState = !pBack || (pS1 || pS2);
                case "NIMPLY" -> newState = pBack && !(pS1 || pS2);
                case "BUFFER" -> newState = pBack;
                default -> isBasic = false;
            }

            if (isBasic && newState != currentState) {
                config.set(path + ".state", newState);
                GateUtils.updateOutput(plugin, path, target, newState);
            }
        }
    }
}