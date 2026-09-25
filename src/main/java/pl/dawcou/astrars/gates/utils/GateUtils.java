package pl.dawcou.astrars.gates.utils;

import com.google.gson.JsonObject;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import pl.dawcou.astrars.AstraRS;

import java.util.Map;
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

    public static int getPowerAt(Block b) {
        if (b == null || b.getType().isAir()) {
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

        // Levers, buttons, pressure plates, repeaters, comparators
        if (b.getBlockData() instanceof org.bukkit.block.data.Powerable powerable) {
            return powerable.isPowered() ? 15 : 0;
        }

        // Return current power directly from wire/analogue states without triggering world block power recalculation
        if (b.getBlockData() instanceof org.bukkit.block.data.AnaloguePowerable analogue) {
            return analogue.getPower();
        }

        return 0;
    }

    public static String getStringFrom(Block block, AstraRS plugin) {
        String key = locToStr(block.getLocation());
        JsonObject gate = plugin.getGateDataManager().getGate(key);

        if (gate == null) return "";

        // 1. Sprawdzamy link (bezprzewodowy)
        if (gate.has("link_input")) {
            long wireless = gate.get("link_input").getAsLong();
            if (wireless != Long.MIN_VALUE) {
                return String.valueOf(wireless);
            }
        }

        // 2. Sprawdzamy fizyczny (lokalny)
        if (!gate.has("current_out")) return "";
        String physical = gate.get("current_out").getAsString();

        if (physical.isEmpty()) return "";

        // Sprawdzamy, czy sygnał to nie jest flaga braku sygnału (Long.MIN_VALUE)
        try {
            String cleanVal = physical.replaceAll("[^0-9\\-]", "");

            if (!cleanVal.isEmpty() && !cleanVal.equals("-")) {
                long parsed = Long.parseLong(cleanVal);
                if (parsed == Long.MIN_VALUE) {
                    return ""; // Tylko MIN_VALUE to brak sygnału
                }
            }
        } catch (NumberFormatException e) {
            // Jeśli sygnał jest tekstem (np. dla STRING_GATE "hello"), zwracamy go po prostu
            return physical;
        }

        return physical; // Zwraca "0" bez przeszkód
    }

    public static long getNumberFrom(Block block, AstraRS plugin) {
        String val = getStringFrom(block, plugin);

        if (val.isEmpty()) return Long.MIN_VALUE;

        try {
            String cleanVal = val.replaceAll("[^0-9\\-]", "");

            if (cleanVal.isEmpty() || cleanVal.equals("-")) {
                return Long.MIN_VALUE;
            }

            return Long.parseLong(cleanVal);
        } catch (NumberFormatException e) {
            return Long.MIN_VALUE;
        }
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

    public static void spawnStatusParticle(Block gate, BlockFace face, Boolean active) {
        try {
            Location loc = gate.getLocation().add(0.5, 0.5, 0.5);
            loc.add(face.getDirection().multiply(0.51));

            Color color = active ? Color.LIME : Color.RED;
            Particle.DustOptions dust = new Particle.DustOptions(color, 1.4F);

            gate.getWorld().spawnParticle(DUST_PARTICLE, loc, 3, 0, 0, 0, 0, dust);
        } catch (Exception ignored) {}
    }

    public static String createDisplay(Location blockLoc, String outName) {
        World world = blockLoc.getWorld();
        if (world == null) return "";

        Location displayLoc = blockLoc.clone().add(0.5, 2.0, 0.5);

        switch (outName.toUpperCase()) {
            case "NORTH" -> displayLoc.add(0, 0, -2.0);
            case "SOUTH" -> displayLoc.add(0, 0, 2.0);
            case "EAST"  -> displayLoc.add(2.0, 0, 0);
            case "WEST"  -> displayLoc.add(-2.0, 0, 0);
        }

        TextDisplay textDisplay = world.spawn(displayLoc, TextDisplay.class);

        textDisplay.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        textDisplay.setShadowed(false);
        textDisplay.setAlignment(TextDisplay.TextAlignment.CENTER);
        textDisplay.setBillboard(TextDisplay.Billboard.CENTER);

        Transformation transformation = textDisplay.getTransformation();
        transformation.getScale().set(4f, 4f, 4f);
        textDisplay.setTransformation(transformation);

        return textDisplay.getUniqueId().toString();
    }

    public static String validateDisplay(JsonObject gate, Location gateLoc) {
        if (gate == null) return "";

        String uuidStr = gate.has("displayUUID") ? gate.get("displayUUID").getAsString() : null;
        String outName = gate.has("out") ? gate.get("out").getAsString() : "NORTH";

        // Obliczamy lokalizację do szybkiego sprawdzenia chunku
        Location displayLoc = gateLoc.clone().add(0.5, 2.0, 0.5);
        switch (outName.toUpperCase()) {
            case "NORTH" -> displayLoc.add(0, 0, -2.0);
            case "SOUTH" -> displayLoc.add(0, 0, 2.0);
            case "EAST"  -> displayLoc.add(2.0, 0, 0);
            case "WEST"  -> displayLoc.add(-2.0, 0, 0);
        }

        // Blokada anty-lagowa: jeśli chunk jest niezaładowany, nie dotykamy encji
        if (!displayLoc.getChunk().isLoaded()) {
            return uuidStr != null ? uuidStr : "";
        }

        // Sprawdzamy czy istnieje
        try {
            if (uuidStr != null && !uuidStr.isEmpty()) {
                Entity entity = Bukkit.getEntity(UUID.fromString(uuidStr));

                if (entity instanceof TextDisplay) {
                    return uuidStr;
                }
            }
        } catch (Exception ignored) {}

        // Jeśli chunk jest załadowany, ale nie ma hologramu -> tworzymy od nowa
        return createDisplay(gateLoc, outName);
    }

    public static void updateDisplayNumber(String uuidStr, String value) {
        if (uuidStr == null || uuidStr.isEmpty()) return;

        try {
            UUID uuid = UUID.fromString(uuidStr);
            Entity entity = Bukkit.getEntity(uuid);

            if (entity instanceof TextDisplay display) {
                display.setText((value));
            }
        } catch (Exception ignored) {}
    }

    public static void removeGate(AstraRS plugin, Location loc) {
        removeGate(plugin, loc, true);
    }

    // Główna logika z przełącznikiem modifyBlock
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

        // Cleanup dla hologramów DISPLAY
        if ("DISPLAY".equals(type)) {
            String uuidStr = gateObj.has("displayUUID") ? gateObj.get("displayUUID").getAsString() : null;
            if (uuidStr != null && !uuidStr.isEmpty()) {
                try {
                    org.bukkit.entity.Entity entity = org.bukkit.Bukkit.getEntity(java.util.UUID.fromString(uuidStr));
                    if (entity != null) entity.remove();
                } catch (Exception ignored) {}
            }
        }

        // Zmieniamy blok w AIR tylko wtedy, gdy NIE wywołujemy tego z eventu niszczenia!
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

        for (Map.Entry<String, com.google.gson.JsonElement> entry : gates.entrySet()) {
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
                // Jeśli sam blok bramki zniknął z świata (jest AIR), to NIE chronimy pochodni
                if (gateBlock.getType().isAir()) {
                    return false;
                }
                return true;
            }
        }
        return false;
    }

    // ----------------------------------------------------
    // Update gate output state using a Wall Torch (LIT / UNLIT)
    // ----------------------------------------------------
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

        // 1. Sprawdzamy aktualny stan świecenia pochodni
        boolean currentLit = false;
        if (target.getType() == Material.REDSTONE_WALL_TORCH && target.getBlockData() instanceof org.bukkit.block.data.type.RedstoneWallTorch torch) {
            currentLit = torch.isLit();
        }

        // 2. Nie zmieniamy gotowej pochodni ani nie nadpisujemy innych bloków
        if (target.getType() == Material.REDSTONE_WALL_TORCH) {
            if (currentLit == p) return;
        } else if (!target.getType().isAir()) {
            return;
        }

        // 3. Tworzymy całkowicie nowy BlockData z wymaganym stanem (LIT = p)
        String blockDataString = "minecraft:redstone_wall_torch[facing=" + outFace.name().toLowerCase() + ",lit=" + p + "]";
        org.bukkit.block.data.BlockData newData = org.bukkit.Bukkit.createBlockData(blockDataString);

        // 4. Ustawiamy nowy BlockData z flagą applyPhysics = true
        target.setBlockData(newData, true);
    }
}