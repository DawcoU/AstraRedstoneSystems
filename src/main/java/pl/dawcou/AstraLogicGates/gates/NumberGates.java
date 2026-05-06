package pl.dawcou.AstraLogicGates.gates;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import pl.dawcou.AstraLogicGates.AstraLogicGates;
import pl.dawcou.AstraLogicGates.GateValidator;
import pl.dawcou.AstraLogicGates.GateUtils;

public class NumberGates {
    private final AstraLogicGates plugin;
    private final GateValidator validator;

    public NumberGates(AstraLogicGates plugin, GateValidator validator) {
        this.plugin = plugin;
        this.validator = validator;
    }

    public void runNumberGates() {
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

            // --- UJEDNOLICONE ZMIENNE KIERUNKOWE ---
            BlockFace out = BlockFace.valueOf(config.getString(path + ".out", "NORTH").toUpperCase());
            BlockFace back = out.getOppositeFace();
            // s1 i s2 zostawiamy, bo mogą być potrzebne do GateUtils lub przyszłych testów
            BlockFace s1 = GateUtils.rotate90(out);
            BlockFace s2 = s1.getOppositeFace();

            boolean currentState = config.getBoolean(path + ".state", false);
            Block target = gate.getRelative(out);

            // --- TWOJE EFEKTY STATUSU (TYLKO DLA TEJ TRÓJKI) ---
            if (type.matches("NUMBER_GATE|VARIABLE_GATE")) {
                GateUtils.spawnStatusParticle(gate, out, currentState);
            }

            if (type.matches("NUMBER_GATE|VARIABLE_GATE")) {
                // pBack to moc na wejściu z tyłu
                boolean pBack = GateUtils.getPowerAt(gate.getRelative(back)) > 0;
                GateUtils.spawnStatusParticle(gate, back, pBack);
            }

            // --- LOGIKA BRAMEK ---
            switch (type) {
                case "NUMBER_GATE" -> {
                    boolean pBack = GateUtils.getPowerAt(gate.getRelative(back)) > 0;
                    int storedValue = config.getInt(path + ".value", 0);
                    int finalValue = pBack ? storedValue : 0;
                    int lastOut = config.getInt(path + ".current_out", -1);

                    if (finalValue != lastOut) {
                        plugin.getLogger().info("[AstraDebug] NUMBER_GATE na " + key + " stan: " + finalValue);
                        config.set(path + ".current_out", finalValue);
                        config.set(path + ".state", pBack);
                        GateUtils.updateOutput(plugin, path, target, pBack);
                    }
                }

                case "VARIABLE_GATE" -> {
                    // 1. Pobieramy dane z wejścia
                    int incoming = GateUtils.getDataFrom(gate.getRelative(back), out, plugin);
                    int currentStored = config.getInt(path + ".value", 0);

                    // 2. REAKCJA NA ZMIANĘ (Obsługuje zapis nowej liczby ORAZ reset do 0)
                    if (incoming != currentStored) {
                        config.set(path + ".value", incoming);
                        currentStored = incoming;
                        plugin.getLogger().info("[AstraDebug] VARIABLE " + key + " zmienił wartość na: " + incoming);
                    }

                    // 3. Aktualizacja stanu fizycznego (wyjście redstone)
                    boolean hasState = currentStored > 0;
                    if (hasState != currentState) {
                        config.set(path + ".state", hasState);
                        // To wyłączy pochodnię/blok, jeśli hasState spadnie do false
                        GateUtils.updateOutput(plugin, path, target, hasState);
                    }

                    // 4. Aktualizacja wyjścia danych (dla kabli)
                    if (currentStored != config.getInt(path + ".current_out", -1)) {
                        config.set(path + ".current_out", currentStored);
                    }
                }

                case "CABLE_DATA" -> {
                    int maxIn = 0;
                    String sourcePos = "NONE";

                    for (BlockFace face : BlockFace.values()) {
                        if (!face.isCartesian()) continue;

                        Block neighbor = gate.getRelative(face);
                        String neighborPath = "gates." + GateUtils.locToStr(neighbor.getLocation());

                        // Blokada pętli zwrotnej dla bramek logicznych
                        String nType = config.getString(neighborPath + ".type", "");
                        if (nType.matches("VARIABLE_GATE|NUMBER_GATE")) {
                            String nOutStr = config.getString(neighborPath + ".out", "NORTH");
                            BlockFace nOut = BlockFace.valueOf(nOutStr);
                            if (!neighbor.getRelative(nOut).getLocation().equals(gate.getLocation())) {
                                continue;
                            }
                        }

                        // PRAWIDŁOWE pobieranie danych do maxIn
                        int incoming = GateUtils.getDataFrom(neighbor, face.getOppositeFace(), plugin);
                        if (incoming > maxIn) {
                            maxIn = incoming;
                            sourcePos = face.name();
                        }
                    }

                    // Aktualizacja kabla tylko przy zmianie
                    int lastOut = config.getInt(path + ".current_out", 0);
                    if (maxIn != lastOut) {
                        config.set(path + ".current_out", maxIn);
                        config.set(path + ".state", maxIn > 0);
                        // DEBUG: Widzisz w konsoli czy kabel w ogóle żyje
                        plugin.getLogger().info("[AstraDebug] KABEL " + key + " ma teraz: " + maxIn + " z " + sourcePos);
                    }

                    // Morskie cząsteczki płynących danych
                    if (maxIn > 0) {
                        Location pLoc = gate.getLocation().clone().add(0.5, 1.1, 0.5);
                        gate.getWorld().spawnParticle(Particle.REDSTONE, pLoc, 1, 0.1, 0, 0.1, 0, new Particle.DustOptions(Color.AQUA, 1.0F));
                    }
                }
            }
        }
    }
}