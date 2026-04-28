package pl.dawcou.AstraLogicGates;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GateListener implements Listener {

    private final AstraLogicGates plugin;
    private final GateManager manager;
    private final Map<UUID, String> editingPlayers = new HashMap<>();

    public GateListener(AstraLogicGates plugin, GateManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Block brokenBlock = e.getBlock();
        String locStr = GateUtils.locToStr(e.getBlock().getLocation());
        String path = "gates." + locStr;
        FileConfiguration cfg = plugin.getGatesConfig();

        if (cfg.contains(path)) {
            String type = cfg.getString(path + ".type", "UNKNOWN");
            String outStr = cfg.getString(path + ".out");
            if (outStr == null) return;

            // 1. CZYSZCZENIE LINKU BEZPRZEWODOWEGO
            String targetStr = cfg.getString(path + ".target_link");
            if (targetStr != null && !targetStr.isEmpty()) {
                Location targetLoc = GateUtils.strToLoc(targetStr);
                if (targetLoc != null) {
                    // Wysyłamy 0 do zlinkowanej bramki, żeby ją "odświeżyć"
                    plugin.getGateManager().sendDataToGate(targetLoc.getBlock(), 0, brokenBlock);
                }
            }

            BlockFace out = BlockFace.valueOf(outStr);
            Block target = e.getBlock().getRelative(out);

            if ("SYNCHRONIZER".equals(type)) {
                String sL = cfg.getString(path + ".sideL");
                String sR = cfg.getString(path + ".sideR");
                if (sL != null) {
                    Location lLoc = GateUtils.strToLoc(sL);
                    if (lLoc != null) {
                        lLoc.getBlock().setType(Material.AIR);
                        lLoc.getBlock().getRelative(out).setType(Material.AIR);
                    }
                }
                if (sR != null) {
                    Location rLoc = GateUtils.strToLoc(sR);
                    if (rLoc != null) {
                        rLoc.getBlock().setType(Material.AIR);
                        rLoc.getBlock().getRelative(out).setType(Material.AIR);
                    }
                }
            }

            ConfigurationSection gatesSection = cfg.getConfigurationSection("gates");
            if (gatesSection != null) {
                for (String key : gatesSection.getKeys(false)) {
                    String gatePath = "gates." + key;
                    String link = cfg.getString(gatePath + ".target_link");
                    if (locStr.equals(link)) {
                        cfg.set(gatePath + ".target_link", null);
                    }
                }
            }

            GateUtils.updateOutput(plugin, path, target, false);

            ItemStack item = new ItemStack(e.getBlock().getType());
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§eBramka: §6" + type.toUpperCase());
                item.setItemMeta(meta);
            }

            e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation(), item);
            cfg.set(path, null);
            plugin.saveGates();

            e.setDropItems(false);
            e.getPlayer().sendMessage(plugin.getLanguageManager().getWithPrefix("gate-removed", "{TYPE}", type));
        }
    }

    @EventHandler
    public void onPowerBlockBreak(BlockBreakEvent e) {
        if (e.getBlock().getType() == Material.REDSTONE_BLOCK) {
            ConfigurationSection gates = plugin.getGatesConfig().getConfigurationSection("gates");
            if (gates == null) return;

            for (String key : gates.getKeys(false)) {
                Location gateLoc = GateUtils.strToLoc(key);
                if (gateLoc == null) continue;

                String path = "gates." + key;
                String outStr = plugin.getGatesConfig().getString(path + ".out");
                if (outStr == null) continue;

                BlockFace out = BlockFace.valueOf(outStr);
                Block powerBlock = gateLoc.getBlock().getRelative(out).getRelative(BlockFace.DOWN);

                Location blockLoc = e.getBlock().getLocation();
                Location powerLoc = powerBlock.getLocation();

                if (blockLoc.getBlockX() == powerLoc.getBlockX() &&
                        blockLoc.getBlockY() == powerLoc.getBlockY() &&
                        blockLoc.getBlockZ() == powerLoc.getBlockZ() &&
                        blockLoc.getWorld() != null && blockLoc.getWorld().equals(powerLoc.getWorld())) {

                    e.setCancelled(true);
                    e.getPlayer().sendMessage(plugin.getLanguageManager().getWithPrefix("power-block-break-deny"));
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        ItemStack item = e.getItemInHand();
        if (!item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;

        if (!GateManager.ALLOWED_BRAMKI.contains(e.getBlock().getType())) return;

        String name = meta.getDisplayName();
        if (name.startsWith("§eBramka: §6")) {
            String type = name.replace("§eBramka: §6", "").toUpperCase();
            Block block = e.getBlock();
            String path = "gates." + GateUtils.locToStr(block.getLocation());
            BlockFace outFace = GateUtils.getDirection(e.getPlayer());

            FileConfiguration cfg = plugin.getGatesConfig();

            cfg.set(path + ".type", type);
            cfg.set(path + ".out", outFace.name());
            cfg.set(path + ".state", false);

            if ("SYNCHRONIZER".equals(type)) {
                BlockFace left = GateUtils.rotate90(GateUtils.rotate90(GateUtils.rotate90(outFace)));
                BlockFace right = GateUtils.rotate90(outFace);

                Block leftSide = block.getRelative(left);
                Block rightSide = block.getRelative(right);

                leftSide.setType(block.getType());
                rightSide.setType(block.getType());

                cfg.set(path + ".sideL", GateUtils.locToStr(leftSide.getLocation()));
                cfg.set(path + ".sideR", GateUtils.locToStr(rightSide.getLocation()));
                cfg.set(path + ".direction", outFace.name());
            }

            if ("VARIABLE_GATE".equals(type)) {
                cfg.set(path + ".value", 0);
                cfg.set(path + ".last_back", false);
            } else if ("BOOLEAN_GATE".equals(type)) {
                cfg.set(path + ".last_back", false);
            } else if ("MATH".equals(type)) {
                cfg.set(path + ".value", 0);
                cfg.set(path + ".val_left", 0);
                cfg.set(path + ".val_right", 0);
                cfg.set(path + ".target_link", null);
            }

            if (meta.hasLore() && meta.getLore() != null) {
                for (String line : meta.getLore()) {
                    if (line.contains("Kanał: ")) {
                        cfg.set(path + ".channel", line.replace("§7Kanał: §f", "").trim());
                    }
                    else if (line.contains("Wartość: ")) {
                        int val = Integer.parseInt(line.replace("§7Wartość: §f", "").trim());
                        if ("NUMBER_GATE".equals(type)) cfg.set(path + ".value", val);
                        else if ("DECODER".equals(type)) cfg.set(path + ".target", val);
                    }
                    else if (line.contains("Tryb: ")) {
                        String rawMode = line.replace("§7Tryb: §f", "").trim();
                        if ("MATH".equals(type)) cfg.set(path + ".mode", rawMode.equalsIgnoreCase("Subtract") ? "SUB" : "ADD");
                        else if ("COMPARATOR".equals(type)) cfg.set(path + ".mode", rawMode);
                    }
                    else if (line.contains("Limit: ")) {
                        cfg.set(path + ".score_limit", Integer.parseInt(line.replace("§7Limit: §f", "")));
                        cfg.set(path + ".count", 0);
                    }
                    else if (line.contains("Zasięg: ")) {
                        cfg.set(path + ".interval", Integer.parseInt(line.replace("§7Zasięg: §f", "")));
                    }
                    else if (line.contains("Czas: ")) {
                        String timeStr = line.replace("§7Czas: §f", "");
                        int ticks = timeStr.endsWith("s") ? (int)(Double.parseDouble(timeStr.replace("s", "")) * 20) : Integer.parseInt(timeStr.replace("t", ""));
                        cfg.set(path + ".interval", ticks);
                        cfg.set(path + ".next_tick", 0);
                    }
                }
            } else {
                if (type.matches("CLOCK|CLOCK_GATE|REPEATER")) {
                    cfg.set(path + ".interval", 20);
                    cfg.set(path + ".next_tick", 0);
                } else if ("COUNTER".equals(type)) {
                    cfg.set(path + ".score_limit", 10);
                    cfg.set(path + ".count", 0);
                } else if ("SENSOR".equals(type)) {
                    cfg.set(path + ".interval", 5);
                }
            }

            plugin.saveGates();
            e.getPlayer().sendMessage(plugin.getLanguageManager().getWithPrefix("gate-placed", "{TYPE}", type).replace("{OUT}", outFace.name()));
        }
    }

    @EventHandler
    public void onGateInteract(PlayerInteractEvent e) {
        if (e.getHand() == EquipmentSlot.OFF_HAND || e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = e.getClickedBlock();
        if (block == null) return;

        String path = "gates." + GateUtils.locToStr(block.getLocation());
        if (!plugin.getGatesConfig().contains(path)) return;

        String type = plugin.getGatesConfig().getString(path + ".type", "");
        Player p = e.getPlayer();

        if (type.matches("COUNTER|CLOCK|CLOCK_GATE|REPEATER|SENSOR")) {
            editingPlayers.put(p.getUniqueId(), path);
            p.sendMessage("");
            p.sendMessage(plugin.getLanguageManager().getWithPrefix("edit-mode-start", "{TYPE}", type));

            if ("COUNTER".equals(type)) {
                p.sendMessage(plugin.getLanguageManager().getMessage("edit-limit-info").replace("{CURRENT}", String.valueOf(plugin.getGatesConfig().getInt(path + ".score_limit"))));
            } else if ("SENSOR".equals(type)) {
                p.sendMessage(plugin.getLanguageManager().getMessage("edit-range-info").replace("{CURRENT}", String.valueOf(plugin.getGatesConfig().getInt(path + ".interval"))));
            } else {
                p.sendMessage(plugin.getLanguageManager().getMessage("edit-time-info"));
            }
            p.sendMessage(plugin.getLanguageManager().getMessage("edit-cancel-info"));
            p.sendMessage("");
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (!editingPlayers.containsKey(p.getUniqueId())) return;

        String path = editingPlayers.get(p.getUniqueId());
        String msg = e.getMessage().toLowerCase();
        e.setCancelled(true);

        if ("anuluj".equals(msg)) {
            editingPlayers.remove(p.getUniqueId());
            p.sendMessage(plugin.getLanguageManager().getWithPrefix("edit-cancelled"));
            return;
        }

        try {
            if (msg.startsWith("limit: ")) {
                int val = Integer.parseInt(msg.replace("limit: ", "").trim());
                if (val >= 1 && val <= 100) {
                    plugin.getGatesConfig().set(path + ".score_limit", val);
                    p.sendMessage(plugin.getLanguageManager().getWithPrefix("limit-set", "{VAL}", String.valueOf(val)));
                    editingPlayers.remove(p.getUniqueId());
                } else p.sendMessage(plugin.getLanguageManager().getWithPrefix("limit-range-error"));
            } else if (msg.startsWith("zasięg: ")) {
                int val = Integer.parseInt(msg.replace("zasięg: ", "").trim());
                if (val >= 1 && val <= 15) {
                    plugin.getGatesConfig().set(path + ".interval", val);
                    p.sendMessage(plugin.getLanguageManager().getWithPrefix("range-set", "{VAL}", String.valueOf(val)));
                    editingPlayers.remove(p.getUniqueId());
                } else p.sendMessage(plugin.getLanguageManager().getWithPrefix("range-range-error"));
            } else if (msg.startsWith("czas: ")) {
                String valStr = msg.replace("czas: ", "").trim();
                int interval;
                if (valStr.endsWith("s")) interval = (int) (Double.parseDouble(valStr.replace("s", "")) * 20);
                else if (valStr.endsWith("t")) interval = Integer.parseInt(valStr.replace("t", ""));
                else { p.sendMessage(plugin.getLanguageManager().getWithPrefix("time-unit-error")); return; }

                plugin.getGatesConfig().set(path + ".interval", interval);
                p.sendMessage(plugin.getLanguageManager().getWithPrefix("time-set", "{VAL}", String.valueOf(interval)));
                editingPlayers.remove(p.getUniqueId());
            }
            plugin.saveGates();
        } catch (NumberFormatException ex) {
            p.sendMessage(plugin.getLanguageManager().getWithPrefix("limit-format-error"));
        }
    }
}