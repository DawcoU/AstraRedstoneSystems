package pl.dawcou.astrars.gates.listener;

import com.google.gson.JsonObject;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.dawcou.astrars.AstraRS;
import pl.dawcou.astrars.file.GateDataManager;
import pl.dawcou.astrars.gates.utils.GateUtils;

import java.util.*;

public class GateListeners implements Listener {

    private final AstraRS plugin;

    public GateListeners(AstraRS plugin) {
        this.plugin = plugin;
    }

    // ----------------------------------------------------
    // Handles player breaking the gate block directly
    // ----------------------------------------------------
    @EventHandler
    public void onGateBlockBreak(BlockBreakEvent e) {
        Block block = e.getBlock();
        Location loc = block.getLocation();

        JsonObject gatesSection = plugin.getGateDataManager().getGates();
        String locStr = GateUtils.locToStr(loc);

        if (gatesSection != null && gatesSection.has(locStr)) {
            e.setCancelled(true);
            GateUtils.removeGate(plugin, loc);
        }
    }

    // ----------------------------------------------------
    // Prevents players from breaking the output torch manually
    // ----------------------------------------------------
    @EventHandler
    public void onPowerBlockBreak(BlockBreakEvent e) {
        Material type = e.getBlock().getType();
        if (type != Material.REDSTONE_WALL_TORCH && type != Material.REDSTONE_TORCH) return;

        if (GateUtils.isGateOutputBlock(plugin, e.getBlock().getLocation())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(plugin.getLanguageManager().getWithPrefix("gate.power-break-deny"));
        }
    }

    // ----------------------------------------------------
    // Prevents game engine physics from extinguishing or detaching the output torch
    // ----------------------------------------------------
    @EventHandler
    public void onGateOutputPhysics(BlockPhysicsEvent e) {
        Block block = e.getBlock();
        Material type = block.getType();

        if (type == Material.REDSTONE_WALL_TORCH || type == Material.REDSTONE_TORCH) {
            if (GateUtils.isGateOutputBlock(plugin, block.getLocation())) {
                e.setCancelled(true);
            }
        }
    }

    // ----------------------------------------------------
    // Metoda pomocnicza do generowania lore z danych JSON
    // ----------------------------------------------------
    private List<String> buildGateLore(String type, JsonObject gateObj) {
        List<String> lore = new ArrayList<>();

        switch (type.toUpperCase()) {
            case "OR", "NOR", "AND", "NAND", "XOR", "XNOR" -> {
                int inputs = gateObj.has("inputs") ? gateObj.get("inputs").getAsInt() : 2;
                lore.add("§7Inputs: §f" + inputs);
            }

            case "SENDER", "RECEIVER" -> {
                String channel = gateObj.has("channel") ? gateObj.get("channel").getAsString() : "default";
                lore.add("§7Channel: §f" + channel);
            }
            case "NUMBER_GATE", "DECODER" -> {
                long val = gateObj.has("value") ? gateObj.get("value").getAsLong() : 0;
                lore.add("§7Value: §f" + val);
            }
            case "RANDOM_NUMBER" -> {
                long min = gateObj.has("min") ? gateObj.get("min").getAsLong() : 0;
                long max = gateObj.has("max") ? gateObj.get("max").getAsLong() : 10;
                lore.add("§7min: §f" + min);
                lore.add("§7max: §f" + max);
            }
            case "MATH" -> {
                String mode = gateObj.has("mode") ? gateObj.get("mode").getAsString() : "ADD";
                String modeName = switch (mode.toUpperCase()) {
                    case "SUB", "SUBTRACT", "-" -> "Subtract";
                    case "MUL", "MULTIPLY", "*" -> "Multiply";
                    case "DIV", "DIVIDE", "/" -> "Divide";
                    case "POW", "POWER", "^" -> "Power";
                    default -> "Add";
                };
                lore.add("§7Mode: §f" + modeName);
            }
            case "BATTERY" -> {
                long chargeTime = gateObj.has("charge_seconds") ? gateObj.get("charge_seconds").getAsLong() : 10;
                long decayTime = gateObj.has("decay_seconds") ? gateObj.get("decay_seconds").getAsLong() : 60;
                lore.add("§7Charge speed: §f" + chargeTime + "s");
                lore.add("§7Decay speed: §f" + decayTime + "s");
            }
            case "COMPARATOR" -> {
                String mode = gateObj.has("mode") ? gateObj.get("mode").getAsString() : "==";
                lore.add("§7Mode: §f" + mode);
            }
            case "COUNTER" -> {
                long limit = gateObj.has("score_limit") ? gateObj.get("score_limit").getAsLong() : 10;
                lore.add("§7Limit: §f" + limit);
            }
            case "SENSOR" -> {
                int radius = gateObj.has("radius") ? gateObj.get("radius").getAsInt() : 5;
                lore.add("§7Range: §f" + radius);
            }
            case "CLOCK", "CLOCK_GATE", "REPEATER" -> {
                int interval = gateObj.has("interval") ? gateObj.get("interval").getAsInt() : 20;
                lore.add("§7Time: §f" + interval + "t");
            }
            case "STRING_GATE", "STRING_DECODER" -> {
                String text = gateObj.has("value") ? gateObj.get("value").getAsString() : "";
                lore.add("§7Text: §f" + text);
            }
            case "STRING_COMPARATOR" -> {
                String mode = gateObj.has("mode") ? gateObj.get("mode").getAsString() : "EQUALS";
                lore.add("§7Mode: §f" + mode);
            }
        }

        return lore;
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        ItemStack item = e.getItemInHand();
        if (!item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        // 1. Sprawdzanie klucza NBT/PDC bramki
        org.bukkit.NamespacedKey typeKey = new org.bukkit.NamespacedKey(plugin, "gate_type");
        if (!meta.getPersistentDataContainer().has(typeKey, org.bukkit.persistence.PersistentDataType.STRING)) return;

        String type = meta.getPersistentDataContainer().get(typeKey, org.bukkit.persistence.PersistentDataType.STRING);
        if (type == null || type.isEmpty()) return;

        if ("DISPLAY".equalsIgnoreCase(type) && !plugin.isTextDisplaySupported()) {
            e.getPlayer().sendMessage(plugin.getLanguageManager().getWithPrefix("gate.unsupported")
                    .replace("%type%", type));
            e.setCancelled(true);
            return;
        }

        // 2. Pobieramy DOKŁADNĄ pozycję stawiającego się bloku
        Block block = e.getBlockPlaced();
        String locKey = GateUtils.locToStr(block.getLocation());

        GateDataManager manager = plugin.getGateDataManager();
        JsonObject gatesSection = manager.getGates();
        if (gatesSection == null) return;

        JsonObject gateObj = new JsonObject();

        BlockFace outFace = GateUtils.getDirection(e.getPlayer());
        gateObj.addProperty("type", type.toUpperCase());

        // 3. Obsługa kierunków i bezpieczne pobieranie starego bloku pod spodem
        if (type.equals("CABLE_DATA")) {
            gateObj.addProperty("current_out", String.valueOf(Long.MIN_VALUE));
            gateObj.addProperty("power", 0);
        } else {
            gateObj.addProperty("out", outFace.name());
            gateObj.addProperty("state", false);
            gateObj.addProperty("current_out", "");

            Block target = block.getRelative(outFace);
            Block outputBlock = target.getRelative(BlockFace.DOWN);
            gateObj.addProperty("oldBlock", outputBlock.getType().name());
        }

        if ("DISPLAY".equals(type)) {
            String outName = gateObj.has("out") ? gateObj.get("out").getAsString() : "NORTH";
            String uuid = GateUtils.createDisplay(block.getLocation(), outName);
            gateObj.addProperty("displayUUID", uuid);
        }

        // 4. Wartości domyślne
        if (type.matches("CLOCK|CLOCK_GATE|REPEATER")) {
            gateObj.addProperty("interval", 0);
            gateObj.addProperty("next_tick", 0);
        } else if (type.matches("SENSOR")) {
            gateObj.addProperty("radius", 5);
        } else if ("COUNTER".equals(type)) {
            gateObj.addProperty("score_limit", 10);
            gateObj.addProperty("count", 0);
        } else if ("RANDOM_NUMBER".equals(type)) {
            gateObj.addProperty("min", 0);
            gateObj.addProperty("max", 10);
        } else if (type.matches("NUMBER_GATE|CABLE_DATA|DECODER")) {
            gateObj.addProperty("value", 0);
        } else if (type.matches("DISK_GATE|RAM_GATE")) {
            gateObj.addProperty("value", "");
        }

        // 5. Bezpieczny odczyt z Lore przedmiotu
        List<String> rawLore = meta.hasLore() && meta.getLore() != null ? meta.getLore() : new ArrayList<>();
        if (!rawLore.isEmpty()) {
            for (String line : rawLore) {
                try {
                    String cleanLine = ChatColor.stripColor(line);

                    if (cleanLine.contains("Channel: ") || cleanLine.contains("Kanał: ")) {
                        String channelVal = cleanLine.contains("Channel: ") ? cleanLine.replace("Channel: ", "") : cleanLine.replace("Kanał: ", "");
                        gateObj.addProperty("channel", channelVal.trim());
                    }
                    else if (cleanLine.contains("min: ")) {
                        String digits = cleanLine.replaceAll("[^0-9\\-]", "");
                        if (!digits.isEmpty()) gateObj.addProperty("min", Long.parseLong(digits));
                    }
                    else if (cleanLine.contains("max: ")) {
                        String digits = cleanLine.replaceAll("[^0-9\\-]", "");
                        if (!digits.isEmpty()) gateObj.addProperty("max", Long.parseLong(digits));
                    }
                    else if (cleanLine.contains("Value: ") || cleanLine.contains("Wartość: ")) {
                        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("-?\\d+");
                        java.util.regex.Matcher matcher = pattern.matcher(cleanLine);
                        if (matcher.find()) {
                            long val = Long.parseLong(matcher.group());
                            if (type.equals("NUMBER_GATE") || type.equals("DECODER")) {
                                gateObj.addProperty("value", val);
                            }
                        }
                    }
                    else if (cleanLine.contains("Range: ") || cleanLine.contains("Zasięg: ")) {
                        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\d+");
                        java.util.regex.Matcher matcher = pattern.matcher(cleanLine);
                        if (matcher.find()) {
                            int val = Integer.parseInt(matcher.group());
                            gateObj.addProperty("radius", val);
                        }
                    }
                    else if (cleanLine.contains("Text: ") || cleanLine.contains("Tekst: ")) {
                        String textVal = cleanLine.contains("Text: ") ? cleanLine.replace("Text: ", "") : cleanLine.replace("Tekst: ", "");
                        if (type.equals("STRING_GATE") || type.equals("STRING_DECODER")) {
                            gateObj.addProperty("value", textVal.trim());
                        }
                    }
                    else if (cleanLine.contains("Mode: ") || cleanLine.contains("Tryb: ")) {
                        String rawMode = cleanLine.contains("Mode: ") ? cleanLine.replace("Mode: ", "").trim() : cleanLine.replace("Tryb: ", "").trim();

                        switch (type) {
                            case "MATH" -> {
                                String modeCode = switch (rawMode) {
                                    case "Subtract" -> "SUB";
                                    case "Multiply" -> "MUL";
                                    case "Divide"   -> "DIV";
                                    case "Power"    -> "POW";
                                    default         -> "ADD";
                                };
                                gateObj.addProperty("mode", modeCode);
                            }
                            case "STRING_COMPARATOR" -> gateObj.addProperty("mode", rawMode.toUpperCase());
                            default -> gateObj.addProperty("mode", rawMode);
                        }
                    }
                    else if (cleanLine.contains("Limit: ")) {
                        String digits = cleanLine.replaceAll("\\D+", "");
                        if (!digits.isEmpty()) {
                            gateObj.addProperty("score_limit", Long.parseLong(digits));
                            gateObj.addProperty("count", 0L);
                        }
                    }
                    else if (cleanLine.contains("Time: ") || cleanLine.contains("Czas: ")) {
                        String timeStr = cleanLine.contains("Time: ") ? cleanLine.replace("Time: ", "").trim() : cleanLine.replace("Czas: ", "").trim();
                        int ticks = 20;

                        if (timeStr.endsWith("s")) {
                            String cleanNum = timeStr.replace("s", "").replaceAll("[^0-9.]", "");
                            if (!cleanNum.isEmpty()) {
                                ticks = (int) (Double.parseDouble(cleanNum) * 20);
                            }
                        } else {
                            String cleanNum = timeStr.replace("t", "").replaceAll("\\D+", "");
                            if (!cleanNum.isEmpty()) {
                                ticks = Integer.parseInt(cleanNum);
                            }
                        }
                        gateObj.addProperty("interval", ticks);
                    }
                } catch (Exception ignored) {}
            }
        }

        // 6. Dopisujemy do sekcji i ZAPISUJEMY plik
        gatesSection.add(locKey, gateObj);
        plugin.saveGates();

        e.getPlayer().sendMessage(plugin.getLanguageManager().getWithPrefix("gate.placed")
                .replace("%type%", type)
                .replace("%out%", outFace.name()));
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Block brokenBlock = e.getBlock();
        String locStr = GateUtils.locToStr(brokenBlock.getLocation());

        GateDataManager manager = plugin.getGateDataManager();
        JsonObject gatesSection = manager.getGates();

        // Jeśli blok nie jest bramką - nic nie robimy, Minecraft sam wydropi zwykły blok
        if (gatesSection == null || !gatesSection.has(locStr)) return;

        JsonObject gateObj = gatesSection.getAsJsonObject(locStr);

        // Blokujemy domyślny drop z Minecrafta, bo to nasza customowa bramka
        e.setDropItems(false);

        String type = gateObj.has("type") ? gateObj.get("type").getAsString() : "UNKNOWN";

        // Dropierz przedmiot tylko na survivalu / adventure
        if (e.getPlayer().getGameMode() != GameMode.CREATIVE) {
            ItemStack item = new ItemStack(brokenBlock.getType());

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                String langPrefix = plugin.getLanguageManager().getMessage("gate.prefix-item");
                if (langPrefix == null || langPrefix.isEmpty()) {
                    langPrefix = "&4Bramka: &c";
                }

                String displayName = ChatColor.translateAlternateColorCodes('&', langPrefix + type.toUpperCase());
                meta.setDisplayName("§r" + displayName);

                List<String> rawGeneratedLore = buildGateLore(type, gateObj);
                List<String> formattedLore = new ArrayList<>();
                for (String line : rawGeneratedLore) {
                    String formattedLine = ChatColor.translateAlternateColorCodes('&', line);
                    if (!formattedLine.startsWith("§r")) {
                        formattedLine = "§r" + formattedLine;
                    }
                    formattedLore.add(formattedLine);
                }
                meta.setLore(formattedLore);

                org.bukkit.NamespacedKey typeKey = new org.bukkit.NamespacedKey(plugin, "gate_type");
                meta.getPersistentDataContainer().set(typeKey, org.bukkit.persistence.PersistentDataType.STRING, type.toUpperCase());

                item.setItemMeta(meta);
            }

            // Używamy bezpośredniej lokalizacji bloku bez sztucznych offsetów, co zapobiega znikaniu przedmiotu w ścianach
            brokenBlock.getWorld().dropItemNaturally(brokenBlock.getLocation(), item);
        }

        String finalMsg = plugin.getLanguageManager().getWithPrefix("gate.removed").replace("%type%", type);
        e.getPlayer().sendMessage(finalMsg);

        // Usuwamy dane bramki z pliku JSON i czyścimy ew. wyjścia/hologramy
        GateUtils.removeGate(plugin, brokenBlock.getLocation(), false);
    }

    @EventHandler
    public void onGateInteract(PlayerInteractEvent e) {
        if (e.getHand() == EquipmentSlot.OFF_HAND || e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = e.getClickedBlock();
        if (block == null) return;

        String locKey = GateUtils.locToStr(block.getLocation());
        GateDataManager manager = plugin.getGateDataManager();
        JsonObject gatesSection = manager.getGates();
        if (gatesSection == null || !gatesSection.has(locKey)) return;

        JsonObject gateObj = gatesSection.getAsJsonObject(locKey);
        String type = gateObj.has("type") ? gateObj.get("type").getAsString() : "";
        Player p = e.getPlayer();

        if (type.matches("OR|NOR|AND|NAND|XOR|XNOR|COUNTER|CLOCK|CLOCK_GATE|REPEATER|SENSOR|NUMBER_GATE|DECODER|MATH|COMPARATOR|RANDOM_NUMBER|BATTERY")) {
            e.setCancelled(true);
            plugin.getGateGUI().openGUI(p, locKey, gateObj, type);
        }
    }
}