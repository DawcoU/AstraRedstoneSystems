package pl.dawcou.astrars.gates.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import pl.dawcou.astrars.AstraRS;
import pl.dawcou.astrars.gates.data.GateContext;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class GateUtils {

    private static final Particle DUST_PARTICLE;

    static {
        Particle particle;
        try {
            particle = Particle.valueOf("DUST");
        } catch (IllegalArgumentException e) {
            particle = Particle.valueOf("REDSTONE");
        }
        DUST_PARTICLE = particle;
    }

    // ============================================================
    // DELEGACJE NA SKRÓTY (ODŁĄCZONE DO OSOBNYCH KLAS)
    // ============================================================

    // NumberUtils delegaty
    public static Long parseLong(JsonElement el) { return NumberUtils.parseLong(el); }
    public static Long readLongOrNull(JsonObject j, String k) { return NumberUtils.readLongOrNull(j, k); }
    public static long readLong(JsonObject j, String k, long f) { return NumberUtils.readLong(j, k, f); }
    public static Double parseDouble(JsonElement el) { return NumberUtils.parseDouble(el); }
    public static Double readDoubleOrNull(JsonObject j, String k) { return NumberUtils.readDoubleOrNull(j, k); }
    public static double readDouble(JsonObject j, String k, double f) { return NumberUtils.readDouble(j, k, f); }
    public static String readString(JsonObject j, String k, String f) { return NumberUtils.readString(j, k, f); }
    public static void setLongIfChanged(JsonObject j, String k, long v) { NumberUtils.setLongIfChanged(j, k, v); }
    public static void setNullIfChanged(JsonObject j, String k) { NumberUtils.setNullIfChanged(j, k); }
    public static long positiveDifference(long c, long p) { return NumberUtils.positiveDifference(c, p); }
    public static double randomDouble(double min, double max) { return NumberUtils.randomDouble(min, max); }
    public static int compareDoubles(double l, double r) { return NumberUtils.compareDoubles(l, r); }

    // BooleanUtils delegaty
    public static boolean readBoolean(JsonObject j, String k, boolean f) { return BooleanUtils.readBoolean(j, k, f); }
    public static void setBooleanIfChanged(JsonObject j, String k, boolean v) { BooleanUtils.setBooleanIfChanged(j, k, v); }

    // DisplayUtils delegaty
    public static String createDisplay(Location loc, String out) { return DisplayUtils.createDisplay(loc, out); }
    public static String validateDisplay(JsonObject g, Location loc) { return DisplayUtils.validateDisplay(g, loc); }
    public static void updateDisplayNumber(String uuid, String val) { DisplayUtils.updateDisplayNumber(uuid, val); }


    // ============================================================
    // OGÓLNE NARZĘDZIA SIECI I ŚWIATA REDSTONE
    // ============================================================

    public static int getPowerAt(Block b) {
        if (b == null || b.getType().isAir()) return 0;
        Material type = b.getType();

        if (type == Material.REDSTONE_WIRE) {
            return ((org.bukkit.block.data.type.RedstoneWire) b.getBlockData()).getPower();
        }
        if (type == Material.REDSTONE_BLOCK) return 15;
        if (type == Material.REDSTONE_TORCH || type == Material.REDSTONE_WALL_TORCH) {
            if (b.getBlockData() instanceof org.bukkit.block.data.Lightable torch) {
                return torch.isLit() ? 15 : 0;
            }
        }
        if (b.getBlockData() instanceof org.bukkit.block.data.Powerable powerable) {
            return powerable.isPowered() ? 15 : 0;
        }
        if (b.getBlockData() instanceof org.bukkit.block.data.AnaloguePowerable analogue) {
            return analogue.getPower();
        }
        return 0;
    }

    public static String getStringFrom(Block block, AstraRS plugin) {
        String key = locToStr(block.getLocation());
        JsonObject gate = plugin.getGateDataManager().getGate(key);
        if (gate == null || !gate.has("current_out")) return null;
        JsonElement output = gate.get("current_out");
        if (output == null || output.isJsonNull() || !output.isJsonPrimitive()) return null;
        JsonPrimitive primitive = output.getAsJsonPrimitive();
        return primitive.isString() ? primitive.getAsString() : null;
    }

    public static Long getNumberFrom(Block block, AstraRS plugin) {
        String key = locToStr(block.getLocation());
        JsonObject gate = plugin.getGateDataManager().getGate(key);
        if (gate == null || !gate.has("current_out")) return null;
        JsonElement output = gate.get("current_out");
        if (output == null || output.isJsonNull() || !output.isJsonPrimitive()) return null;
        JsonPrimitive primitive = output.getAsJsonPrimitive();
        if (!primitive.isNumber()) return null;
        try {
            return new BigDecimal(primitive.getAsString()).longValueExact();
        } catch (NumberFormatException | ArithmeticException ignored) {
            return null;
        }
    }

    public static Double getDoubleFrom(Block block, AstraRS plugin) {
        String key = locToStr(block.getLocation());
        JsonObject gate = plugin.getGateDataManager().getGate(key);
        if (gate == null || !gate.has("current_out")) return null;
        JsonElement output = gate.get("current_out");
        if (output == null || output.isJsonNull() || !output.isJsonPrimitive()) return null;
        JsonPrimitive primitive = output.getAsJsonPrimitive();
        if (!primitive.isNumber()) return null;
        try {
            double value = primitive.getAsDouble();
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static Boolean getBooleanFrom(Block block, AstraRS plugin) {
        String key = locToStr(block.getLocation());
        JsonObject gate = plugin.getGateDataManager().getGate(key);
        if (gate == null || !gate.has("current_out")) return null;
        JsonElement output = gate.get("current_out");
        if (output == null || output.isJsonNull() || !output.isJsonPrimitive()) return null;
        JsonPrimitive primitive = output.getAsJsonPrimitive();
        return primitive.isBoolean() ? primitive.getAsBoolean() : null;
    }

    public static JsonElement getDataFrom(Block block, AstraRS plugin) {
        String key = locToStr(block.getLocation());
        JsonObject gate = plugin.getGateDataManager().getGate(key);
        if (gate == null || !gate.has("current_out")) return null;
        JsonElement output = gate.get("current_out");
        if (output == null || output.isJsonNull() || !output.isJsonPrimitive()) return null;
        return output.deepCopy();
    }

    public static boolean isDataSignalActive(JsonElement data) {
        if (data == null || data.isJsonNull()) {
            return false;
        }
        if (data.isJsonPrimitive() && data.getAsJsonPrimitive().isBoolean()) {
            return data.getAsBoolean();
        }
        return true;
    }

    public static JsonElement outputOrNull(JsonElement value) {
        return value == null ? JsonNull.INSTANCE : value.deepCopy();
    }

    public static boolean areJsonElementsEqual(JsonElement e1, JsonElement e2) {
        boolean n1 = (e1 == null || e1.isJsonNull());
        boolean n2 = (e2 == null || e2.isJsonNull());
        if (n1 || n2) return n1 && n2;

        if (!e1.isJsonPrimitive() || !e2.isJsonPrimitive()) {
            return e1.equals(e2);
        }

        JsonPrimitive p1 = e1.getAsJsonPrimitive();
        JsonPrimitive p2 = e2.getAsJsonPrimitive();

        if (p1.isBoolean() && p2.isBoolean()) return p1.getAsBoolean() == p2.getAsBoolean();
        if (p1.isString() && p2.isString()) return p1.getAsString().equals(p2.getAsString());

        if (p1.isNumber() && p2.isNumber()) {
            try {
                return new BigDecimal(p1.getAsString()).compareTo(new BigDecimal(p2.getAsString())) == 0;
            } catch (Exception e) {
                return p1.getAsString().equals(p2.getAsString());
            }
        }
        return p1.equals(p2);
    }

    public static boolean setOutputIfChanged(JsonObject gate, JsonElement value) {
        JsonElement previous = gate.get("current_out");
        JsonElement output = outputOrNull(value);
        if (Objects.equals(previous, output)) return false;
        gate.add("current_out", output);
        return true;
    }

    public static void syncOutput(AstraRS plugin, JsonObject gate, GateContext ctx, String key, boolean state, boolean outputChanged) {
        if (outputChanged || state != ctx.currentState()) {
            gate.addProperty("state", state);
            updateOutput(plugin, key, ctx.dirs().targetBlock(), state);
        }
    }

    public static BlockFace rotate90(BlockFace f) {
        return switch (f) {
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

    public static void spawnStatusParticle(Block gate, BlockFace face, Boolean active) {
        try {
            Location loc = gate.getLocation().add(0.5, 0.5, 0.5);
            loc.add(face.getDirection().multiply(0.51));
            Color color = active ? Color.LIME : Color.RED;
            Particle.DustOptions dust = new Particle.DustOptions(color, 1.4F);
            gate.getWorld().spawnParticle(DUST_PARTICLE, loc, 3, 0, 0, 0, 0, dust);
        } catch (Exception ignored) {}
    }

    public static void removeGate(AstraRS plugin, Location loc) {
        removeGate(plugin, loc, true);
    }

    public static void removeGate(AstraRS plugin, Location loc, boolean modifyBlock) {
        String locStr = locToStr(loc);
        JsonObject gatesSection = plugin.getGateDataManager().getGates();
        if (gatesSection == null || !gatesSection.has(locStr)) return;

        JsonObject gateObj = gatesSection.getAsJsonObject(locStr);
        String type = gateObj.has("type") ? gateObj.get("type").getAsString() : "UNKNOWN";
        String dirName = gateObj.has("out") ? gateObj.get("out").getAsString() : "NORTH";
        BlockFace faceOut = BlockFace.valueOf(dirName);

        if (!type.equalsIgnoreCase("CABLE_DATA")) {
            Block target = loc.getBlock().getRelative(faceOut);
            if (target.getType() == Material.REDSTONE_TORCH || target.getType() == Material.REDSTONE_WALL_TORCH) {
                target.setType(Material.AIR);
            }
        }

        if ("DISPLAY".equals(type)) {
            String uuidStr = gateObj.has("displayUUID") ? gateObj.get("displayUUID").getAsString() : null;
            if (uuidStr != null && !uuidStr.isEmpty()) {
                try {
                    Entity entity = Bukkit.getEntity(UUID.fromString(uuidStr));
                    if (entity != null) entity.remove();
                } catch (Exception ignored) {}
            }
        }

        if (modifyBlock) {
            loc.getBlock().setType(Material.AIR);
        }

        gatesSection.remove(locStr);
        plugin.saveGates();
    }

    public static boolean isGateOutputBlock(AstraRS plugin, Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        JsonObject gates = plugin.getGateDataManager().getGates();
        if (gates == null) return false;

        for (Map.Entry<String, JsonElement> entry : gates.entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject gateObj = entry.getValue().getAsJsonObject();

            Location gateLoc = strToLoc(entry.getKey());
            if (gateLoc == null || !gateLoc.getWorld().equals(loc.getWorld())) continue;

            String outStr = gateObj.has("out") ? gateObj.get("out").getAsString().toUpperCase() : "NORTH";
            BlockFace outFace;
            try {
                outFace = BlockFace.valueOf(outStr);
            } catch (IllegalArgumentException e) {
                outFace = BlockFace.NORTH;
            }

            Block gateBlock = gateLoc.getBlock();
            Block targetBlock = gateBlock.getRelative(outFace);

            if (targetBlock.getLocation().equals(loc)) {
                return !gateBlock.getType().isAir();
            }
        }
        return false;
    }

    public static void updateOutput(AstraRS plugin, String key, Block target, boolean p) {
        if (target == null) return;
        JsonObject gate = plugin.getGateDataManager().getGate(key);
        if (gate == null) return;

        String type = gate.has("type") ? gate.get("type").getAsString() : "";
        if (type.equalsIgnoreCase("CABLE_DATA")) return;

        String outStr = gate.has("out") ? gate.get("out").getAsString() : "NORTH";
        BlockFace outFace;
        try {
            outFace = BlockFace.valueOf(outStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            outFace = BlockFace.NORTH;
        }

        boolean currentLit = false;
        if (target.getType() == Material.REDSTONE_WALL_TORCH && target.getBlockData() instanceof org.bukkit.block.data.type.RedstoneWallTorch torch) {
            currentLit = torch.isLit();
        }

        if (target.getType() == Material.REDSTONE_WALL_TORCH) {
            if (currentLit == p) return;
        } else if (!target.getType().isAir()) {
            return;
        }

        String blockDataString = "minecraft:redstone_wall_torch[facing=" + outFace.name().toLowerCase() + ",lit=" + p + "]";
        org.bukkit.block.data.BlockData newData = org.bukkit.Bukkit.createBlockData(blockDataString);
        target.setBlockData(newData, true);
    }
}