package pl.dawcou.AstraLogicGates;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

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

    public static int getDataFrom(Block block, BlockFace fromFace, AstraLogicGates plugin) {
        String key = locToStr(block.getLocation());
        FileConfiguration config = plugin.getGatesConfig();
        // Pobiera wartość wysyłaną przez blok obok
        return config.getInt("gates." + key + ".current_out", 0);
    }

    public static int getCustomOrRedstonePower(Plugin plugin, Block block) {
        AstraLogicGates main = (AstraLogicGates) plugin;
        FileConfiguration config = main.getGatesConfig();

        String locStr = locToStr(block.getLocation());
        if (config.contains("gates." + locStr)) {
            return config.getInt("gates." + locStr + ".value", 0);
        }
        return getPowerAt(block);
    }

    public static boolean isInputPowered(Block g, BlockFace out) {
        for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            if (f == out) continue;
            if (getPowerAt(g.getRelative(f)) > 0) return true;
        }
        return false;
    }

    public static BlockFace rotate90(BlockFace f) {
        return switch (f) { case NORTH -> BlockFace.EAST; case EAST -> BlockFace.SOUTH; case SOUTH -> BlockFace.WEST; case WEST -> BlockFace.NORTH; default -> BlockFace.EAST; };
    }

    public static BlockFace getDirection(Player p) {
        float y = p.getLocation().getYaw();
        if (y < 0) y += 360;
        if (y >= 315 || y < 45) return BlockFace.SOUTH;
        if (y >= 45 && y < 135) return BlockFace.WEST;
        if (y >= 135 && y < 225) return BlockFace.NORTH;
        return BlockFace.EAST;
    }

    public static String locToStr(Location l) { return l.getWorld().getName() + "," + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ(); }

    public static Location strToLoc(String s) {
        try {
            String[] p = s.split(",");
            return new Location(Bukkit.getWorld(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
        } catch (Exception e) { return null; }
    }

    public static void spawnStatusParticle(Block gate, BlockFace face, boolean active) {
        Location loc = gate.getLocation().add(0.5, 0.5, 0.5);
        loc.add(face.getDirection().multiply(0.51));
        org.bukkit.Particle.DustOptions dust = active ?
                new org.bukkit.Particle.DustOptions(org.bukkit.Color.LIME, 1.3F) :
                new org.bukkit.Particle.DustOptions(org.bukkit.Color.RED, 1.3F);
        gate.getWorld().spawnParticle(org.bukkit.Particle.REDSTONE, loc, 1, 0, 0, 0, 0, dust);
    }

    // Tutaj dodałem TwojaGłównaKlasa plugin jako argument, żeby updateOutput miało dostęp do configu
    public static void updateOutput(AstraLogicGates plugin, String path, Block target, boolean p) {
        FileConfiguration c = plugin.getGatesConfig();
        // Upewnij się, że target to faktycznie blok, który ma zasilać,
        // ale usuwanie/stawianie bloku pod spodem musi być precyzyjne!
        Block powerBlock = target.getRelative(BlockFace.DOWN);

        // Zapobiegaj miganiu - sprawdź czy zmiana jest faktycznie potrzebna
        if (p && powerBlock.getType() != Material.REDSTONE_BLOCK) {
            c.set(path + ".oldMat", powerBlock.getType().name());
            c.set(path + ".oldData", powerBlock.getBlockData().getAsString());
            powerBlock.setType(Material.REDSTONE_BLOCK);
        } else if (!p && powerBlock.getType() == Material.REDSTONE_BLOCK) {
            String matName = c.getString(path + ".oldMat", "AIR");
            powerBlock.setType(Material.valueOf(matName));
            c.set(path + ".oldMat", null);
        }
    }
}