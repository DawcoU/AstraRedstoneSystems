package pl.dawcou.astrars.selection;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.dawcou.astrars.AstraRS;
import pl.dawcou.astrars.file.GateDataManager;
import pl.dawcou.astrars.gates.utils.GateUtils;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class SelectionManager implements Listener {

    private final AstraRS plugin;
    private final GateDataManager dataManager;
    private final Map<UUID, Location[]> selections = new HashMap<>();
    private final Map<UUID, Map<org.bukkit.util.Vector, JsonObject>> clipboard = new HashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // ----------------------------------------------------
    // Klasa pomocnicza do trzymania stanu przed zmianami
    // ----------------------------------------------------
    private static class BlockStateBackup {
        private final Location location;
        private final BlockData blockData;
        private final String[] frontSignLines;
        private final String[] backSignLines;
        private final JsonObject gateDataSnapshot;

        public BlockStateBackup(Location location, BlockData blockData, String[] frontSignLines, String[] backSignLines, JsonObject gateDataSnapshot) {
            this.location = location;
            this.blockData = blockData.clone();
            this.frontSignLines = frontSignLines;
            this.backSignLines = backSignLines;
            this.gateDataSnapshot = gateDataSnapshot;
        }
    }

    private final Map<UUID, LinkedList<List<BlockStateBackup>>> pasteHistory = new HashMap<>();
    private final Map<UUID, LinkedList<List<BlockStateBackup>>> redoHistory = new HashMap<>();
    private static final int MAX_HISTORY_SIZE = 10;

    public SelectionManager(AstraRS plugin, GateDataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;

        // Automatyczne tworzenie folderu schematics
        File schematicsDir = new File(plugin.getDataFolder(), "schematics");
        if (!schematicsDir.exists()) {
            schematicsDir.mkdirs();
        }
    }

    private void serializeBlockState(Block block, JsonObject target) {
        target.addProperty("block_data_str", block.getBlockData().getAsString());

        if (block.getState() instanceof Sign sign) {
            JsonArray front = new JsonArray();
            JsonArray back = new JsonArray();
            for (int i = 0; i < 4; i++) {
                front.add(sign.getSide(Side.FRONT).getLine(i));
                back.add(sign.getSide(Side.BACK).getLine(i));
            }
            target.add("sign_front", front);
            target.add("sign_back", back);
        }
    }

    private void deserializeBlockState(Location loc, JsonObject source) {
        if (!source.has("block_data_str")) {
            String legacyMat = source.has("block_type") ? source.get("block_type").getAsString() : "AIR";
            Material mat = Material.matchMaterial(legacyMat);
            loc.getBlock().setType(mat != null ? mat : Material.AIR, true);
            return;
        }

        try {
            String dataStr = source.get("block_data_str").getAsString();
            BlockData data = Bukkit.createBlockData(dataStr);
            loc.getBlock().setBlockData(data, true);

            if (loc.getBlock().getState() instanceof Sign sign) {
                if (source.has("sign_front") && source.get("sign_front").isJsonArray()) {
                    JsonArray front = source.getAsJsonArray("sign_front");
                    for (int i = 0; i < Math.min(4, front.size()); i++) {
                        sign.getSide(Side.FRONT).setLine(i, front.get(i).getAsString());
                    }
                }
                if (source.has("sign_back") && source.get("sign_back").isJsonArray()) {
                    JsonArray back = source.getAsJsonArray("sign_back");
                    for (int i = 0; i < Math.min(4, back.size()); i++) {
                        sign.getSide(Side.BACK).setLine(i, back.get(i).getAsString());
                    }
                }
                sign.update(true, true);
            }
        } catch (IllegalArgumentException e) {
            loc.getBlock().setType(Material.AIR, true);
        }
    }

    private BlockStateBackup createBackup(Location loc) {
        Block block = loc.getBlock();
        BlockData data = block.getBlockData();
        String[] front = null;
        String[] back = null;

        if (block.getState() instanceof Sign sign) {
            front = new String[4];
            back = new String[4];
            for (int i = 0; i < 4; i++) {
                front[i] = sign.getSide(Side.FRONT).getLine(i);
                back[i] = sign.getSide(Side.BACK).getLine(i);
            }
        }

        String locKey = GateUtils.locToStr(loc);
        JsonObject originalGate = dataManager.getGate(locKey);
        JsonObject snapshot = (originalGate != null) ? originalGate.deepCopy() : null;

        return new BlockStateBackup(loc, data, front, back, snapshot);
    }

    private void restoreBackup(BlockStateBackup backup) {
        Location loc = backup.location;
        loc.getBlock().setBlockData(backup.blockData, true);

        if (loc.getBlock().getState() instanceof Sign sign && backup.frontSignLines != null) {
            for (int i = 0; i < 4; i++) {
                sign.getSide(Side.FRONT).setLine(i, backup.frontSignLines[i]);
                sign.getSide(Side.BACK).setLine(i, backup.backSignLines[i]);
            }
            sign.update(true, true);
        }

        String locKey = GateUtils.locToStr(loc);
        JsonObject gates = dataManager.getGates();
        if (backup.gateDataSnapshot != null) {
            gates.add(locKey, backup.gateDataSnapshot.deepCopy());
        } else {
            gates.remove(locKey);
        }
    }

    public void giveSelector(Player player) {
        ItemStack stick = new ItemStack(Material.STICK);
        ItemMeta meta = stick.getItemMeta();

        if (meta != null) {
            String langName = plugin.getLanguageManager().getMessage("selector.item-name");
            if (langName == null || langName.isEmpty()) {
                langName = "&dSelektor Bramek";
            }

            meta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', langName));

            org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "item_type");
            meta.getPersistentDataContainer().set(key, org.bukkit.persistence.PersistentDataType.STRING, "gate_selector");

            meta.addEnchant(Enchantment.LUCK, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            stick.setItemMeta(meta);
        }

        player.getInventory().addItem(stick);
        player.sendMessage(plugin.getLanguageManager().getWithPrefix("selector.received"));
    }

    public void cutSelection(Player player) {
        UUID uuid = player.getUniqueId();
        if (!selections.containsKey(uuid) || selections.get(uuid)[0] == null || selections.get(uuid)[1] == null) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("selector.both-required"));
            return;
        }

        Location loc1 = selections.get(uuid)[0];
        Location loc2 = selections.get(uuid)[1];
        World world = loc1.getWorld();

        int minX = Math.min(loc1.getBlockX(), loc2.getBlockX());
        int maxX = Math.max(loc1.getBlockX(), loc2.getBlockX());
        int minY = Math.min(loc1.getBlockY(), loc2.getBlockY());
        int maxY = Math.max(loc1.getBlockY(), loc2.getBlockY());
        int minZ = Math.min(loc1.getBlockZ(), loc2.getBlockZ());
        int maxZ = Math.max(loc1.getBlockZ(), loc2.getBlockZ());

        JsonObject gates = dataManager.getGates();
        int removedGates = 0;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Location target = new Location(world, x, y, z);
                    String key = GateUtils.locToStr(target);

                    if (gates.has(key)) {
                        JsonObject data = gates.getAsJsonObject(key);
                        if (data.has("type") && "DISPLAY".equals(data.get("type").getAsString())) {
                            if (data.has("displayUUID")) {
                                try {
                                    Entity entity = Bukkit.getEntity(UUID.fromString(data.get("displayUUID").getAsString()));
                                    if (entity != null) entity.remove();
                                } catch (Exception ignored) {}
                            }
                        }
                        gates.remove(key);
                        removedGates++;
                    }
                    world.getBlockAt(x, y, z).setType(Material.AIR);
                }
            }
        }
        dataManager.save();

        String msg = plugin.getLanguageManager().getWithPrefix("selector.cut");
        player.sendMessage(msg.replace("%count%", String.valueOf(removedGates)));
    }

    public void copySelection(Player player) {
        UUID uuid = player.getUniqueId();
        if (selections.get(uuid) == null || selections.get(uuid)[0] == null || selections.get(uuid)[1] == null) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("selector.both-required"));
            return;
        }

        Location loc1 = selections.get(uuid)[0];
        Location loc2 = selections.get(uuid)[1];
        Location playerLoc = player.getLocation().getBlock().getLocation();
        World world = loc1.getWorld();

        int minX = Math.min(loc1.getBlockX(), loc2.getBlockX());
        int maxX = Math.max(loc1.getBlockX(), loc2.getBlockX());
        int minY = Math.min(loc1.getBlockY(), loc2.getBlockY());
        int maxY = Math.max(loc1.getBlockY(), loc2.getBlockY());
        int minZ = Math.min(loc1.getBlockZ(), loc2.getBlockZ());
        int maxZ = Math.max(loc1.getBlockZ(), loc2.getBlockZ());

        Map<org.bukkit.util.Vector, JsonObject> playerClipboard = new HashMap<>();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Location currentLoc = new Location(world, x, y, z);
                    Block block = currentLoc.getBlock();
                    String locStr = GateUtils.locToStr(currentLoc);
                    Material actualMaterial = block.getType();

                    JsonObject originalGate = dataManager.getGate(locStr);
                    boolean isGate = (originalGate != null);

                    if (actualMaterial == Material.AIR && !isGate) {
                        continue;
                    }

                    JsonObject copyOfData = new JsonObject();
                    serializeBlockState(block, copyOfData);

                    if (isGate) {
                        JsonObject gateCopy = originalGate.deepCopy();
                        gateCopy.remove("displayUUID");

                        for (Map.Entry<String, JsonElement> entry : gateCopy.entrySet()) {
                            copyOfData.add(entry.getKey(), entry.getValue());
                        }

                        copyOfData.addProperty("state", false);
                        copyOfData.addProperty("current_out", 0);
                        copyOfData.addProperty("power", 0);
                        copyOfData.addProperty("last_decay", 0);
                        copyOfData.addProperty("last_charge_tick", 0);
                    }

                    copyOfData.addProperty("is_gate_logic", isGate);
                    org.bukkit.util.Vector offset = new org.bukkit.util.Vector(x, y, z).subtract(playerLoc.toVector());
                    playerClipboard.put(offset, copyOfData);
                }
            }
        }

        clipboard.put(uuid, playerClipboard);

        long actualGatesCount = playerClipboard.values().stream()
                .filter(json -> json.has("is_gate_logic") && json.get("is_gate_logic").getAsBoolean())
                .count();

        String msg = plugin.getLanguageManager().getWithPrefix("clipboard.copy-success");
        player.sendMessage(msg.replace("%count%", String.valueOf(actualGatesCount)));
    }

    public void pasteSelection(Player player) {
        UUID uuid = player.getUniqueId();
        if (!clipboard.containsKey(uuid) || clipboard.get(uuid).isEmpty()) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("clipboard.empty"));
            return;
        }

        Location playerLoc = player.getLocation().getBlock().getLocation();
        JsonObject gates = dataManager.getGates();
        List<BlockStateBackup> currentOperationBackup = new ArrayList<>();

        int pastedGatesCount = 0;

        List<Map.Entry<org.bukkit.util.Vector, JsonObject>> entries = new ArrayList<>(clipboard.get(uuid).entrySet());
        entries.sort(Comparator.comparingInt(e -> {
            String dataStr = e.getValue().has("block_data_str") ? e.getValue().get("block_data_str").getAsString() : "";
            if (dataStr.contains("sign") || dataStr.contains("button") || dataStr.contains("lever")) return 1;
            return 0;
        }));

        for (Map.Entry<org.bukkit.util.Vector, JsonObject> entry : entries) {
            Location newLoc = playerLoc.clone().add(entry.getKey());
            JsonObject gateData = entry.getValue();

            currentOperationBackup.add(createBackup(newLoc));

            boolean isGate = (gateData.has("is_gate_logic") && gateData.get("is_gate_logic").getAsBoolean()) || gateData.has("type");
            deserializeBlockState(newLoc, gateData);

            String locKey = GateUtils.locToStr(newLoc);
            if (isGate) {
                JsonObject saveData = gateData.deepCopy();
                saveData.remove("block_data_str");
                saveData.remove("sign_front");
                saveData.remove("sign_back");
                saveData.remove("is_gate_logic");

                gates.add(locKey, saveData);
                pastedGatesCount++;
            } else {
                gates.remove(locKey);
            }
        }

        pasteHistory.computeIfAbsent(uuid, k -> new LinkedList<>()).addFirst(currentOperationBackup);
        if (pasteHistory.get(uuid).size() > MAX_HISTORY_SIZE) {
            pasteHistory.get(uuid).removeLast();
        }
        redoHistory.remove(uuid);

        dataManager.save();

        String msg = plugin.getLanguageManager().getWithPrefix("clipboard.paste-success");
        player.sendMessage(msg.replace("%count%", String.valueOf(pastedGatesCount)));
    }

    public void undoPaste(Player player) {
        UUID uuid = player.getUniqueId();
        if (!pasteHistory.containsKey(uuid) || pasteHistory.get(uuid).isEmpty()) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("clipboard.nothing-to-undo"));
            return;
        }

        List<BlockStateBackup> lastOperation = pasteHistory.get(uuid).removeFirst();
        List<BlockStateBackup> redoBackup = new ArrayList<>();

        for (BlockStateBackup blockBackup : lastOperation) {
            redoBackup.add(createBackup(blockBackup.location));
        }

        for (int i = lastOperation.size() - 1; i >= 0; i--) {
            restoreBackup(lastOperation.get(i));
        }

        redoHistory.computeIfAbsent(uuid, k -> new LinkedList<>()).addFirst(redoBackup);
        if (redoHistory.get(uuid).size() > MAX_HISTORY_SIZE) {
            redoHistory.get(uuid).removeLast();
        }

        dataManager.save();
        player.sendMessage(plugin.getLanguageManager().getWithPrefix("clipboard.undo-success")
                .replace("%count%", String.valueOf(lastOperation.size())));
    }

    public void redoPaste(Player player) {
        UUID uuid = player.getUniqueId();
        if (!redoHistory.containsKey(uuid) || redoHistory.get(uuid).isEmpty()) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("clipboard.nothing-to-redo"));
            return;
        }

        List<BlockStateBackup> redoOperation = redoHistory.get(uuid).removeFirst();
        List<BlockStateBackup> undoBackup = new ArrayList<>();

        for (BlockStateBackup blockBackup : redoOperation) {
            undoBackup.add(createBackup(blockBackup.location));
        }

        for (BlockStateBackup blockBackup : redoOperation) {
            restoreBackup(blockBackup);
        }

        pasteHistory.computeIfAbsent(uuid, k -> new LinkedList<>()).addFirst(undoBackup);

        dataManager.save();
        player.sendMessage(plugin.getLanguageManager().getWithPrefix("clipboard.redo-success")
                .replace("%count%", String.valueOf(redoOperation.size())));
    }

    public void rotateSelection(Player player, int degrees) {
        UUID uuid = player.getUniqueId();
        if (!clipboard.containsKey(uuid) || clipboard.get(uuid).isEmpty()) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("clipboard.empty"));
            return;
        }

        int angle = (degrees % 360 + 360) % 360;
        if (angle == 0) return;

        Map<org.bukkit.util.Vector, JsonObject> oldClipboard = clipboard.get(uuid);
        Map<org.bukkit.util.Vector, JsonObject> rotatedClipboard = new HashMap<>();

        for (Map.Entry<org.bukkit.util.Vector, JsonObject> entry : oldClipboard.entrySet()) {
            org.bukkit.util.Vector vec = entry.getKey();
            JsonObject data = entry.getValue();

            double x = vec.getX();
            double z = vec.getZ();
            double radians = Math.toRadians(angle);
            double newX = x * Math.cos(radians) - z * Math.sin(radians);
            double newZ = x * Math.sin(radians) + z * Math.cos(radians);

            org.bukkit.util.Vector rotatedVec = new org.bukkit.util.Vector(Math.round(newX), vec.getY(), Math.round(newZ));

            if (data.has("out")) {
                String currentOutStr = data.get("out").getAsString().toUpperCase();
                try {
                    BlockFace currentOut = BlockFace.valueOf(currentOutStr);
                    data.addProperty("out", rotateFace(currentOut, angle).name());
                } catch (IllegalArgumentException ignored) {}
            }

            if (data.has("block_data_str")) {
                String dataStr = data.get("block_data_str").getAsString();
                data.addProperty("block_data_str", rotateBlockDataString(dataStr, angle));
            }

            rotatedClipboard.put(rotatedVec, data);
        }

        clipboard.put(uuid, rotatedClipboard);
        String msg = plugin.getLanguageManager().getWithPrefix("clipboard.rotate-success");
        player.sendMessage(msg.replace("%degree%", String.valueOf(degrees)));
    }

    private BlockFace rotateFace(BlockFace face, int degrees) {
        int steps = (degrees / 90) % 4;
        if (steps < 0) steps += 4;
        BlockFace current = face;
        for (int i = 0; i < steps; i++) {
            current = switch (current) {
                case NORTH -> BlockFace.EAST;
                case EAST -> BlockFace.SOUTH;
                case SOUTH -> BlockFace.WEST;
                case WEST -> BlockFace.NORTH;
                default -> current;
            };
        }
        return current;
    }

    private String rotateBlockDataString(String dataStr, int degrees) {
        if (!dataStr.contains("facing=")) return dataStr;
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
            if (dataStr.contains("facing=" + face.name().toLowerCase())) {
                BlockFace rotated = rotateFace(face, degrees);
                return dataStr.replace("facing=" + face.name().toLowerCase(), "facing=" + rotated.name().toLowerCase());
            }
        }
        return dataStr;
    }

    public void saveClipboardToFile(Player player, String schemaName) {
        File schematicsDir = new File(plugin.getDataFolder(), "schematics");
        File schemaFile = new File(schematicsDir, schemaName.toLowerCase() + ".json");
        UUID uuid = player.getUniqueId();

        if (!clipboard.containsKey(uuid) || clipboard.get(uuid).isEmpty()) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("clipboard.empty"));
            return;
        }

        if (schemaFile.exists()) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("schema.file-exists").replace("%name%", schemaName));
            return;
        }

        Map<org.bukkit.util.Vector, JsonObject> playerClipboard = clipboard.get(uuid);
        long gatesCount = playerClipboard.values().stream()
                .filter(json -> (json.has("is_gate_logic") && json.get("is_gate_logic").getAsBoolean()) || json.has("type"))
                .count();

        plugin.getSchedulerManager().runAsync(() -> {
            if (!schematicsDir.exists()) schematicsDir.mkdirs();

            JsonObject rootJson = new JsonObject();
            JsonObject schematicGatesJson = new JsonObject();

            for (Map.Entry<org.bukkit.util.Vector, JsonObject> entry : playerClipboard.entrySet()) {
                String vecKey = entry.getKey().getBlockX() + "_" + entry.getKey().getBlockY() + "_" + entry.getKey().getBlockZ();
                JsonObject gateObj = entry.getValue().deepCopy();
                gateObj.remove("displayUUID");

                schematicGatesJson.add(vecKey, gateObj);
            }

            rootJson.add("schematic_gates", schematicGatesJson);

            try (FileWriter writer = new FileWriter(schemaFile)) {
                gson.toJson(rootJson, writer);
                plugin.getSchedulerManager().runSync(() -> {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("schema.save-success").replace("%name%", schemaName).replace("%count%", String.valueOf(gatesCount)));
                });
            } catch (IOException e) {
                plugin.getSchedulerManager().runSync(() -> {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("schema.save-error"));
                });
                e.printStackTrace();
            }
        });
    }

    public void loadClipboardFromFile(Player player, String schemaName) {
        File schematicsDir = new File(plugin.getDataFolder(), "schematics");
        if (!schematicsDir.exists()) {
            schematicsDir.mkdirs();
        }

        File schemaFile = new File(schematicsDir, schemaName.toLowerCase() + ".json");

        if (!schemaFile.exists()) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("schema.not-found").replace("%name%", schemaName));
            return;
        }

        plugin.getSchedulerManager().runAsync(() -> {
            try (FileReader reader = new FileReader(schemaFile)) {
                JsonObject rootJson = JsonParser.parseReader(reader).getAsJsonObject();

                if (!rootJson.has("schematic_gates") || !rootJson.get("schematic_gates").isJsonObject()) {
                    plugin.getSchedulerManager().runSync(() -> player.sendMessage(plugin.getLanguageManager().getWithPrefix("schema.corrupted")));
                    return;
                }

                JsonObject schematicGates = rootJson.getAsJsonObject("schematic_gates");
                if (schematicGates.keySet().isEmpty()) {
                    plugin.getSchedulerManager().runSync(() -> player.sendMessage(plugin.getLanguageManager().getWithPrefix("schema.corrupted")));
                    return;
                }

                Map<org.bukkit.util.Vector, JsonObject> loadedClipboard = new HashMap<>();
                int loadedGatesCount = 0;

                for (String key : schematicGates.keySet()) {
                    String[] parts = key.split("_");
                    if (parts.length != 3) continue;

                    try {
                        org.bukkit.util.Vector vec = new org.bukkit.util.Vector(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                        JsonObject gateDataJson = schematicGates.getAsJsonObject(key);

                        if ((gateDataJson.has("is_gate_logic") && gateDataJson.get("is_gate_logic").getAsBoolean()) || gateDataJson.has("type")) {
                            loadedGatesCount++;
                        }
                        loadedClipboard.put(vec, gateDataJson);
                    } catch (Exception ignored) {}
                }

                final int finalGatesCount = loadedGatesCount;
                plugin.getSchedulerManager().runSync(() -> {
                    clipboard.put(player.getUniqueId(), loadedClipboard);
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("schema.load-success").replace("%name%", schemaName).replace("%count%", String.valueOf(finalGatesCount)));
                });

            } catch (Exception e) {
                plugin.getSchedulerManager().runSync(() -> player.sendMessage(plugin.getLanguageManager().getWithPrefix("schema.corrupted")));
                e.printStackTrace();
            }
        });
    }

    public void deleteClipboardFile(Player player, String schemaName) {
        plugin.getSchedulerManager().runAsync(() -> {
            File schematicsDir = new File(plugin.getDataFolder(), "schematics");
            File schemaFile = new File(schematicsDir, schemaName.toLowerCase() + ".json");

            if (!schemaFile.exists()) {
                plugin.getSchedulerManager().runSync(() -> {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("schema.not-found").replace("%name%", schemaName));
                });
                return;
            }

            if (schemaFile.delete()) {
                plugin.getSchedulerManager().runSync(() -> {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("schema.deleted").replace("%name%", schemaName));
                });
            } else {
                plugin.getSchedulerManager().runSync(() -> {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("schema.delete-error").replace("%name%", schemaName));
                });
            }
        });
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || !item.hasItemMeta()) return;

        ItemMeta currentMeta = item.getItemMeta();
        if (currentMeta == null) return;

        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "item_type");
        if (!currentMeta.getPersistentDataContainer().has(key, org.bukkit.persistence.PersistentDataType.STRING)) return;

        String itemType = currentMeta.getPersistentDataContainer().get(key, org.bukkit.persistence.PersistentDataType.STRING);
        if (!"gate_selector".equals(itemType)) return;

        if (!player.hasPermission("astrars.admin")) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
            return;
        }

        if (event.getClickedBlock() == null) return;

        event.setCancelled(true);

        UUID uuid = player.getUniqueId();
        Location clickedLoc = event.getClickedBlock().getLocation();

        if (!selections.containsKey(uuid)) selections.put(uuid, new Location[2]);
        Location[] playerSelections = selections.get(uuid);

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (playerSelections[0] == null || !playerSelections[0].equals(clickedLoc)) {
                playerSelections[0] = clickedLoc;
                player.sendMessage(plugin.getLanguageManager().getWithPrefix("selector.pos-1"));
            }
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (playerSelections[1] == null || !playerSelections[1].equals(clickedLoc)) {
                playerSelections[1] = clickedLoc;
                player.sendMessage(plugin.getLanguageManager().getWithPrefix("selector.pos-2"));
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        selections.remove(uuid);
        clipboard.remove(uuid);
    }
}