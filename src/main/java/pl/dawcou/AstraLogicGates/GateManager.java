package pl.dawcou.AstraLogicGates;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class GateManager {

    private final LogicGates plugin;
    private int globalTickCounter = 0;
    private final Random random = new Random();

    public static final List<Material> ALLOWED_BRAMKI = Arrays.asList(
            Material.RED_CONCRETE, Material.ORANGE_CONCRETE, Material.YELLOW_CONCRETE,
            Material.GRAY_CONCRETE, Material.LIGHT_BLUE_CONCRETE, Material.PURPLE_CONCRETE,
            Material.MAGENTA_CONCRETE, Material.WHITE_CONCRETE, Material.PINK_CONCRETE,
            Material.BLUE_CONCRETE, Material.GREEN_CONCRETE, Material.LIME_CONCRETE,
            Material.CYAN_CONCRETE, Material.BROWN_CONCRETE, Material.IRON_BLOCK,
            Material.GOLD_BLOCK, Material.EMERALD_BLOCK, Material.LAPIS_BLOCK
    );

    public GateManager(LogicGates plugin) { this.plugin = plugin; }

    public void checkGates() {
        globalTickCounter++;
        boolean isActionTick = (globalTickCounter % 2 == 0);
        FileConfiguration config = plugin.getGatesConfig();
        if (config.getConfigurationSection("gates") == null) return;

        for (String key : config.getConfigurationSection("gates").getKeys(false)) {
            String path = "gates." + key;
            Location loc = strToLoc(key);

            if (loc == null || !ALLOWED_BRAMKI.contains(loc.getBlock().getType())) continue;

            Block gate = loc.getBlock();
            String type = config.getString(path + ".type");

            // --- BLOKADA BEZPIECZEŃSTWA (Walidacja Configu) ---
            if (type.equals("COUNTER")) {
                int limit = config.getInt(path + ".score_limit");
                if (limit < 1 || limit > 100) continue; // Błąd w configu? Bramka stoi, ale nie działa.
            }
            else if (type.equals("SENSOR")) {
                int radius = config.getInt(path + ".interval");
                if (radius < 1 || radius > 15) continue;
            }
            else if (type.matches("CLOCK|CLOCK_GATE|REPEATER")) {
                int interval = config.getInt(path + ".interval");
                // 5t (min) do 200t (10s max)
                if (interval < 5 || interval > 200) continue;
            }

            BlockFace out = BlockFace.valueOf(config.getString(path + ".out"));
            Block target = gate.getRelative(out);
            BlockFace back = out.getOppositeFace();
            BlockFace s1 = rotate90(out);
            BlockFace s2 = s1.getOppositeFace();

            // 1. POBIERZ AKTUALNY STAN (Podstawa, żeby nic nie migało)
            boolean currentState = config.getBoolean(path + ".state", false);

            // --- RENDEROWANIE CZĄSTECZEK (Co tick - stabilnie) ---
            // Jeśli typ NIE JEST synchronizerem, to spawnuj cząsteczki
            if (!type.equals("SYNCHRONIZER")) {
                spawnStatusParticle(gate, out, currentState);
            }

            if (type.equals("COUNTER")) {
                BlockFace left = rotate90(rotate90(rotate90(out)));
                BlockFace right = rotate90(out);
                spawnStatusParticle(gate, back, getPowerAt(gate.getRelative(back)) > 0);
                spawnStatusParticle(gate, left, getPowerAt(gate.getRelative(left)) > 0);
                spawnStatusParticle(gate, right, getPowerAt(gate.getRelative(right)) > 0);
            } else {
                if (type.matches("AND|NAND|XOR|XNOR|LATCH|NIMPLY")) {
                    spawnStatusParticle(gate, s1, getPowerAt(gate.getRelative(s1)) > 0);
                    spawnStatusParticle(gate, s2, getPowerAt(gate.getRelative(s2)) > 0);
                }
                if (type.matches("NOT|OR|NOR|CLOCK_GATE|REPEATER|BOOSTER|TFF|RANDOM|DLATCH|NIMPLY|SENDER")) {
                    spawnStatusParticle(gate, back, getPowerAt(gate.getRelative(back)) > 0);
                }
            }

            // 2. LOGIKA NATYCHMIASTOWA (1 tick - Booster, Wireless, Clocks)
            if (type.equals("BOOSTER")) {
                boolean p = getPowerAt(gate.getRelative(back)) > 0;
                if (p != currentState) {
                    updateOutput(path, target, p);
                    config.set(path + ".state", p);
                }
                continue;
            }

            if (type.equals("SENDER")) {
                String rawChannels = config.getString(path + ".channel", "default");
                boolean hasPower = getPowerAt(gate.getRelative(back)) > 0;

                String[] splitChannels = rawChannels.split(",");
                boolean hasListener = false;

                for (String chan : splitChannels) {
                    String trimmed = chan.trim();
                    config.set("channels." + trimmed, hasPower);

                    // Sprawdzamy, czy jakikolwiek odbiornik słucha tego kanału
                    if (config.getBoolean("active_channels." + trimmed, false)) {
                        hasListener = true;
                    }

                    // CZYŚCIMY status słuchacza po sprawdzeniu (żeby w następnym ticku musiał się odświeżyć)
                    config.set("active_channels." + trimmed, null);
                }

                // Dymek z przodu świeci TYLKO gdy: jest prąd I jest przynajmniej jeden odbiornik
                boolean isTransmitting = hasPower && hasListener;

                spawnStatusParticle(gate, out, isTransmitting); // Przód (Antena)
                spawnStatusParticle(gate, back, hasPower);      // Tył (Zasilanie)

                config.set(path + ".state", false); // Fizyczne wyjście OFF
                continue;
            }

            if (type.equals("RECEIVER")) {
                // Pobieramy kanał i od razu wywalamy przecinki oraz spacje (na wypadek gdyby gracz coś namieszał)
                String channel = config.getString(path + ".channel", "default").replace(",", "").replace(" ", "");

                config.set("active_channels." + channel, true);

                // Sprawdzamy stan tego konkretnego kanału w chmurze
                boolean remotePower = config.getBoolean("channels." + channel, false);

                if (remotePower != currentState) {
                    updateOutput(path, target, remotePower);
                    config.set(path + ".state", remotePower);
                }
                continue;
            }

            if (type.equals("SENSOR")) {
                // Pobieramy promień z configu (domyślnie 5 bloków)
                int radius = config.getInt(path + ".interval", 5);
                boolean found = false;

                // Szukamy graczy w sześcianie wokół bramki
                for (org.bukkit.entity.Entity entity : gate.getWorld().getNearbyEntities(gate.getLocation(), radius, radius, radius)) {
                    if (entity instanceof org.bukkit.entity.Player) {
                        found = true;
                        break;
                    }
                }

                // Aktualizacja stanu tylko gdy ktoś wejdzie lub wyjdzie z zasięgu
                if (found != currentState) {
                    updateOutput(path, target, found);
                    config.set(path + ".state", found);
                }

                continue;
            }

            // SEKCJA 1-TICKOWA (Dla bramek wymagających natychmiastowej reakcji)
            if (type.equals("SYNCHRONIZER")) {
                BlockFace left = rotate90(rotate90(rotate90(out)));
                BlockFace right = rotate90(out);

                // 1. Sprawdzamy wejścia (za blokami bocznymi)
                boolean pA = getPowerAt(gate.getRelative(left).getRelative(back)) > 0;
                boolean pB = getPowerAt(gate.getRelative(right).getRelative(back)) > 0;

                // 2. Logika SYNC
                boolean ready = pA && pB;

                // 3. Aktualizacja dwóch wyjść (tylko jeśli stan się zmienił!)

                if (ready != currentState) {
                    updateOutput(path + "_L", gate.getRelative(left).getRelative(out), ready);
                    updateOutput(path + "_R", gate.getRelative(right).getRelative(out), ready);
                    config.set(path + ".state", ready);
                }

                // 4. Dymki (TYLKO na bocznych modułach)

                // Tyły (Wejścia) - informują czy sygnał A i B dotarł
                spawnStatusParticle(gate.getRelative(left), back, pA);
                spawnStatusParticle(gate.getRelative(right), back, pB);

                // Przody (Wyjścia) - informują czy brama puściła sygnały dalej
                spawnStatusParticle(gate.getRelative(left), out, ready);
                spawnStatusParticle(gate.getRelative(right), out, ready);

                continue; // Przejdź do następnej bramki
            }

            if (type.matches("CLOCK|CLOCK_GATE|REPEATER")) {
                int interval = config.getInt(path + ".interval", 20);
                boolean hasPower = getPowerAt(gate.getRelative(back)) > 0;

                if (type.equals("REPEATER")) {
                    int timer = config.getInt(path + ".next_tick", 0);
                    if (hasPower) {
                        if (!currentState) {
                            timer++;
                            if (timer >= interval) { updateOutput(path, target, true); config.set(path + ".state", true); timer = 0; }
                            config.set(path + ".next_tick", timer);
                        }
                    } else {
                        if (currentState) { updateOutput(path, target, false); config.set(path + ".state", false); }
                        config.set(path + ".next_tick", 0);
                    }
                } else {
                    boolean enabled = !type.equals("CLOCK_GATE") || hasPower;
                    if (!enabled) {
                        if (currentState) { updateOutput(path, target, false); config.set(path + ".state", false); }
                        config.set(path + ".next_tick", 0);
                    } else {
                        int nt = config.getInt(path + ".next_tick") + 1;
                        if (nt >= interval) {
                            boolean newState = !currentState;
                            updateOutput(path, target, newState);
                            config.set(path + ".state", newState);
                            nt = 0;
                        }
                        config.set(path + ".next_tick", nt);
                    }
                }
                continue;
            }

            if (type.equals("COUNTER")) {
                BlockFace right = rotate90(out);
                BlockFace left = rotate90(rotate90(rotate90(out)));
                boolean pB = getPowerAt(gate.getRelative(back)) > 0, pL = getPowerAt(gate.getRelative(left)) > 0, pR = getPowerAt(gate.getRelative(right)) > 0;
                int count = config.getInt(path + ".count"), limit = config.getInt(path + ".score_limit");
                boolean lB = config.getBoolean(path + ".last_back"), lL = config.getBoolean(path + ".last_left"), lR = config.getBoolean(path + ".last_right");
                boolean changed = false;

                if (pB && !lB && count < limit) { count++; changed = true; }
                if (pL && !lL && count > 0) { count--; changed = true; }
                if (pR && !lR) { count = 0; changed = true; }

                config.set(path + ".last_back", pB); config.set(path + ".last_left", pL); config.set(path + ".last_right", pR);
                if (changed) {
                    config.set(path + ".count", count);
                    boolean finalState = (count >= limit);
                    updateOutput(path, target, finalState);
                    config.set(path + ".state", finalState);
                }
                continue;
            }

            // 3. LOGIKA ZWYKŁA (Obliczana co 2 ticki, ale stan trzymany co tick)
            if (isActionTick) {
                boolean nextState = currentState;

                if (type.matches("NOT|OR|NOR")) {
                    boolean p = isInputPowered(gate, out);
                    // NOT odwraca (nie p), OR podaje dalej (p), NOR odwraca sumę (nie p)
                    nextState = type.equals("NOT") ? !p : (type.equals("OR") ? p : !p);
                } else if (type.matches("AND|NAND|XOR|XNOR")) {
                    boolean p1 = getPowerAt(gate.getRelative(s1)) > 0;
                    boolean p2 = getPowerAt(gate.getRelative(s2)) > 0;

                    if (type.equals("AND")) nextState = (p1 && p2);
                    else if (type.equals("NAND")) nextState = !(p1 && p2);
                    else if (type.equals("XOR")) nextState = (p1 ^ p2);
                    else if (type.equals("XNOR")) nextState = (p1 == p2);
                } else if (type.equals("LATCH")) {
                    boolean s = getPowerAt(gate.getRelative(s1)) > 0, r = getPowerAt(gate.getRelative(s2)) > 0;
                    if (s) nextState = true; else if (r) nextState = false;
                } else if (type.equals("TFF") || type.equals("RANDOM")) {
                    boolean in = getPowerAt(gate.getRelative(back)) > 0;
                    if (in && !config.getBoolean(path + ".lastInput")) {
                        nextState = type.equals("TFF") ? !currentState : random.nextBoolean();
                    }
                    config.set(path + ".lastInput", in);
                } else if (type.equals("NIMPLY")) {
                    nextState = getPowerAt(gate.getRelative(back)) > 0 && !(getPowerAt(gate.getRelative(s1)) > 0 || getPowerAt(gate.getRelative(s2)) > 0);
                }

                // AKTUALIZACJA TYLKO PRZY ZMIANIE (Klucz do braku migania)
                if (nextState != currentState) {
                    updateOutput(path, target, nextState);
                    config.set(path + ".state", nextState);
                }
            }
        }
        plugin.saveGates();
    }

    public void updateOutput(String path, Block target, boolean p) {
        FileConfiguration c = plugin.getGatesConfig();
        // Celujemy w blok bezpośrednio pod wyjściem (target)
        Block powerBlock = target.getRelative(BlockFace.DOWN);

        if (p) {
            // Stawiamy blok redstone pod spodem, jeśli go tam jeszcze nie ma
            if (powerBlock.getType() != Material.REDSTONE_BLOCK) {
                c.set(path + ".oldMat", powerBlock.getType().name());
                c.set(path + ".oldData", powerBlock.getBlockData().getAsString());

                powerBlock.setType(Material.REDSTONE_BLOCK);
                plugin.saveGates();
            }
        } else if (powerBlock.getType() == Material.REDSTONE_BLOCK) {
            // Przywracamy poprzedni materiał bloku pod spodem
            String matName = c.getString(path + ".oldMat", "AIR");
            String d = c.getString(path + ".oldData");

            try {
                powerBlock.setType(Material.valueOf(matName));
                if (d != null) powerBlock.setBlockData(Bukkit.createBlockData(d));
            } catch (Exception e) {
                powerBlock.setType(Material.AIR);
            }

            c.set(path + ".oldMat", null);
            c.set(path + ".oldData", null);
            plugin.saveGates();
        }
    }

    public int getPowerAt(Block b) {
        if (b == null || b.getType() == Material.AIR || b.getType() == Material.CAVE_AIR || b.getType() == Material.VOID_AIR) {
            return 0;
        }

        Material type = b.getType();

        // 1. Kable (Redstone Wire) - pobieramy realną moc (0-15)
        if (type == Material.REDSTONE_WIRE) {
            return ((org.bukkit.block.data.type.RedstoneWire) b.getBlockData()).getPower();
        }

        // 2. Blok redstone - zawsze max power
        if (type == Material.REDSTONE_BLOCK) {
            return 15;
        }

        // 3. Pochodnie (ścienne i zwykłe) - sprawdzamy czy się świecą
        if (type == Material.REDSTONE_TORCH || type == Material.REDSTONE_WALL_TORCH) {
            if (b.getBlockData() instanceof org.bukkit.block.data.Lightable torch) {
                return torch.isLit() ? 15 : 0;
            }
        }

        // 4. Reszta (Dźwignie, guziki, repeatery, komparatory itp.)
        return b.getBlockPower();
    }

    private boolean isInputPowered(Block g, BlockFace out) {
        // Sprawdzamy tylko boki, tył i górę.
        // Wywalamy DOWN (żeby nie brało prądu z ziemi) i out (żeby nie brało z własnego wyjścia).
        for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP}) {

            // Jeśli ta strona to nasze wyjście, ignorujemy ją całkowicie!
            if (f == out) continue;

            // Sprawdzamy sygnał na pozostałych ścianach
            if (getPowerAt(g.getRelative(f)) > 0) return true;
        }
        return false;
    }

    public BlockFace rotate90(BlockFace f) {
        return switch (f) { case NORTH -> BlockFace.EAST; case EAST -> BlockFace.SOUTH; case SOUTH -> BlockFace.WEST; case WEST -> BlockFace.NORTH; default -> BlockFace.EAST; };
    }

    public BlockFace getDirection(Player p) {
        float y = p.getLocation().getYaw();
        if (y < 0) y += 360;
        if (y >= 315 || y < 45) return BlockFace.SOUTH;
        if (y >= 45 && y < 135) return BlockFace.WEST;
        if (y >= 135 && y < 225) return BlockFace.NORTH;
        return BlockFace.EAST;
    }

    public String locToStr(Location l) { return l.getWorld().getName() + "," + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ(); }
    public Location strToLoc(String s) {
        try {
            String[] p = s.split(",");
            return new Location(Bukkit.getWorld(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
        } catch (Exception e) { return null; }
    }

    private void spawnStatusParticle(Block gate, BlockFace face, boolean active) {
        Location loc = gate.getLocation().add(0.5, 0.5, 0.5);
        loc.add(face.getDirection().multiply(0.51));

        // Zmieniamy rozmiar z 1.0F na 1.2F (delikatnie większe)
        org.bukkit.Particle.DustOptions dust = active ?
                new org.bukkit.Particle.DustOptions(org.bukkit.Color.LIME, 1.3F) :
                new org.bukkit.Particle.DustOptions(org.bukkit.Color.RED, 1.3F);

        gate.getWorld().spawnParticle(org.bukkit.Particle.DUST, loc, 1, 0, 0, 0, 0, dust);
    }
}