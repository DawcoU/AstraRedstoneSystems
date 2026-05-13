package pl.dawcou.AstraRedstoneSystems;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.util.List;
import java.util.UUID;

public class GateUtils {

    public static int getPowerAt(Block b) {
        if (b == null || b.getType() == Material.AIR || b.getType() == Material.CAVE_AIR || b.getType() == Material.VOID_AIR) {
            return 0;
        }
        Material type = b.getType();
        if (type == Material.REDSTONE_WIRE) {
            return ((org.bukkit.block.data.type.RedstoneWire) b.getBlockData()).getPower();
        }
        if (type == Material.REDSTONE_BLOCK) {
            return 15;
        }
        if (type == Material.REDSTONE_TORCH || type == Material.REDSTONE_WALL_TORCH) {
            if (b.getBlockData() instanceof org.bukkit.block.data.Lightable torch) {
                return torch.isLit() ? 15 : 0;
            }
        }
        return b.getBlockPower();
    }

    public static int getDataFrom(Block block, BlockFace fromFace, AstraRS plugin) {
        String key = locToStr(block.getLocation());
        FileConfiguration config = plugin.getGatesConfig();

        // Pobieramy sygnał fizyczny (z bloku)
        int physical = config.getInt("gates." + key + ".current_out", 0);
        // Pobieramy sygnał "z powietrza" (z linku)
        int wireless = config.getInt("gates." + key + ".link_input", 0);

        // Zwracamy mocniejszy sygnał
        return Math.max(physical, wireless);
    }

    public static void syncLinks(AstraRS plugin) {
        FileConfiguration config = plugin.getGatesConfig();
        ConfigurationSection gates = config.getConfigurationSection("gates");
        if (gates == null) return;

        // KROK 1: Czyścimy link_input wszystkich bramek, żeby nie było starych danych
        for (String key : gates.getKeys(false)) {
            config.set("gates." + key + ".link_input", 0);
        }

        // KROK 2: Rozsyłamy aktualne wartości z nadajników
        for (String key : gates.getKeys(false)) {
            String path = "gates." + key;
            List<String> targets = config.getStringList(path + ".target_links");

            if (!targets.isEmpty()) {
                int currentVal = config.getInt(path + ".current_out", 0);
                for (String targetLoc : targets) {
                    config.set("gates." + targetLoc + ".link_input", currentVal);
                }
            }
        }
    }

    public static int getCustomOrRedstonePower(AstraRS plugin, Block block) {
        // 1. Najpierw sprawdzamy, czy ten blok to jedna z TWOICH bramek (cyfrowe dane)
        String path = "gates." + locToStr(block.getLocation());
        if (plugin.getGatesConfig().contains(path)) {
            // Jeśli tak, wyciągamy zapisaną tam wartość liczbową
            return plugin.getGatesConfig().getInt(path + ".current_out", 0);
        }

        // 2. Jeśli to nie jest Twoja bramka, sprawdzamy zwykły Minecraftowy Redstone
        // (Dźwignie, przyciski, czerwony proszek itp.)
        return block.getBlockPower();
    }

    public static BlockFace rotate90(BlockFace f) {
        return switch (f) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            default -> BlockFace.EAST;
        };
    }

    public static BlockFace getDirection(Player p) {
        float y = p.getLocation().getYaw();
        if (y < 0) y += 360;
        if (y >= 315 || y < 45) return BlockFace.SOUTH;
        if (y >= 45 && y < 135) return BlockFace.WEST;
        if (y >= 135 && y < 225) return BlockFace.NORTH;
        return BlockFace.EAST;
    }

    public static String locToStr(Location l) {
        return l.getWorld().getName() + "," + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }

    public static Location strToLoc(String s) {
        try {
            String[] p = s.split(",");
            return new Location(Bukkit.getWorld(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
        } catch (Exception e) {
            return null;
        }
    }

    public static void spawnStatusParticle(Block gate, BlockFace face, boolean active) {
        Location loc = gate.getLocation().add(0.5, 0.5, 0.5);
        loc.add(face.getDirection().multiply(0.51));
        org.bukkit.Particle.DustOptions dust = active ?
                new org.bukkit.Particle.DustOptions(org.bukkit.Color.LIME, 1.3F) :
                new org.bukkit.Particle.DustOptions(org.bukkit.Color.RED, 1.3F);
        gate.getWorld().spawnParticle(org.bukkit.Particle.REDSTONE, loc, 1, 0, 0, 0, 0, dust);
    }

    public static void updateDisplayNumber(AstraRS plugin, String uuidStr, int value) {
        if (uuidStr == null || uuidStr.isEmpty()) return;

        try {
            UUID uuid = UUID.fromString(uuidStr);
            Entity entity = Bukkit.getEntity(uuid);

            if (entity instanceof TextDisplay display) {
                // Możesz tu poszaleć z kolorami, np. złoty dla liczb > 0
                String color = (value > 0) ? "§f" : "§7";
                display.setText(color + value);
            }
        } catch (Exception e) {
            // Ciche ignorowanie, jeśli UUID jest trefne
        }
    }

    public static void updateOutput(AstraRS plugin, String path, Block target, boolean p) {
        FileConfiguration config = plugin.getGatesConfig();

        // 1. POBIERAMY TYP ZAPISANY W CONFIGU DLA TEJ ŚCIEŻKI
        String type = config.getString(path + ".type", "");

        // 2. DEBUG (teraz zobaczysz co to za typ)
        //Bukkit.broadcastMessage("§cDEBUG: " + type + " na " + path + " -> target: " + target.getX() + "," + target.getZ());

        // 3. BLOKADA - jeśli to kabel, wychodzimy ZANIM cokolwiek zmienimy
        if (type.equalsIgnoreCase("CABLE_DATA")) {
            return;
        }

        // Celujemy w blok POD tym, co podaliśmy jako target
        Block powerBlock = target.getRelative(BlockFace.DOWN);

        if (p) {
            // Zapisujemy co tam jest TERAZ (np. Grass), zanim damy redstone
            if (powerBlock.getType() != Material.REDSTONE_BLOCK) {
                config.set(path + ".oldBlock", powerBlock.getType().name());
                plugin.saveGates();
            }
            powerBlock.setType(Material.REDSTONE_BLOCK);
        } else {
            // Przywracamy z pamięci
            String matName = config.getString(path + ".oldBlock", "AIR");
            powerBlock.setType(Material.valueOf(matName));
        }
    }
}