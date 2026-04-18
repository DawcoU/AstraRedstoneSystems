package pl.dawcou.AstraLogicGates;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public class GateListener implements Listener {

    private final LogicGates plugin;
    private final GateManager manager;
    private final java.util.Map<java.util.UUID, String> editingPlayers = new java.util.HashMap<>();

    public GateListener(LogicGates plugin, GateManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        String path = "gates." + manager.locToStr(e.getBlock().getLocation());
        FileConfiguration cfg = plugin.getGatesConfig();

        if (cfg.contains(path)) {
            String type = cfg.getString(path + ".type");
            BlockFace out = BlockFace.valueOf(cfg.getString(path + ".out"));
            Block target = e.getBlock().getRelative(out);

            // Sprzątanie boków synchronizatora
            if (type.equals("SYNCHRONIZER")) {
                String sL = cfg.getString(path + ".sideL");
                String sR = cfg.getString(path + ".sideR");
                if (sL != null) {
                    manager.strToLoc(sL).getBlock().setType(Material.AIR);
                    manager.strToLoc(sL).getBlock().getRelative(out).setType(Material.AIR);
                }
                if (sR != null) {
                    manager.strToLoc(sR).getBlock().setType(Material.AIR);
                    manager.strToLoc(sR).getBlock().getRelative(out).setType(Material.AIR);
                }
            }

            manager.updateOutput(path, target, false);

            // Drop przedmiotu
            org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(e.getBlock().getType());
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§eBramka: §6" + type.toUpperCase());
                item.setItemMeta(meta);
            }

            e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation(), item);
            cfg.set(path, null);
            plugin.saveGates();

            e.setDropItems(false);
            e.getPlayer().sendMessage(LogicGates.PREFIX + " §cBramka §6" + type + " §czostała usunięta!");
        }
    }

    @EventHandler
    public void onPowerBlockBreak(BlockBreakEvent e) {
        // Jeśli gracz niszczy blok redstone
        if (e.getBlock().getType() == Material.REDSTONE_BLOCK) {
            // Sprawdzamy wszystkie bramki w configu, czy któraś z nich nie używa tego bloku pod swoim wyjściem
            org.bukkit.configuration.ConfigurationSection gates = plugin.getGatesConfig().getConfigurationSection("gates");
            if (gates == null) return;

            for (String key : gates.getKeys(false)) {
                Location gateLoc = manager.strToLoc(key);
                if (gateLoc == null) continue;

                String path = "gates." + key;
                BlockFace out = BlockFace.valueOf(plugin.getGatesConfig().getString(path + ".out"));
                // Wyjście (target) to tam gdzie jest dymek, a blok zasilający jest POD nim
                Block powerBlock = gateLoc.getBlock().getRelative(out).getRelative(BlockFace.DOWN);

                if (e.getBlock().getLocation().equals(powerBlock.getLocation())) {
                    e.setCancelled(true);
                    e.getPlayer().sendMessage(LogicGates.PREFIX + " §cTo jest blok zasilający wyjście bramki, nie możesz go zniszczyć!");
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onPlace(org.bukkit.event.block.BlockPlaceEvent e) {
        org.bukkit.inventory.ItemStack item = e.getItemInHand();
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return;

        if (!GateManager.ALLOWED_BRAMKI.contains(e.getBlock().getType())) return;

        String name = item.getItemMeta().getDisplayName();
        if (name.startsWith("§eBramka: §6")) {
            String type = name.replace("§eBramka: §6", "").toUpperCase();
            Block block = e.getBlock();
            String path = "gates." + manager.locToStr(block.getLocation());
            BlockFace outFace = manager.getDirection(e.getPlayer());

            org.bukkit.configuration.file.FileConfiguration cfg = plugin.getGatesConfig();

            cfg.set(path + ".type", type);
            cfg.set(path + ".out", outFace.name());
            cfg.set(path + ".state", false);

            // --- LOGIKA DLA SYNCHRONIZATORA (Fizyczne boki) ---
            if (type.equals("SYNCHRONIZER")) {
                BlockFace left = manager.rotate90(manager.rotate90(manager.rotate90(outFace)));
                BlockFace right = manager.rotate90(outFace);

                Block leftSide = block.getRelative(left);
                Block rightSide = block.getRelative(right);

                leftSide.setType(block.getType());
                rightSide.setType(block.getType());

                cfg.set(path + ".sideL", manager.locToStr(leftSide.getLocation()));
                cfg.set(path + ".sideR", manager.locToStr(rightSide.getLocation()));
                cfg.set(path + ".direction", outFace.name());
            }

            // --- CZYTANIE LORE (Parametry) ---
            if (item.getItemMeta().hasLore()) {
                for (String line : item.getItemMeta().getLore()) {
                    if (line.contains("Kanał: ")) {
                        String chan = line.replace("§7Kanał: §f", "").trim();
                        cfg.set(path + ".channel", chan);
                    }
                    else if (line.contains("Limit: ")) {
                        int limit = Integer.parseInt(line.replace("§7Limit: §f", ""));
                        cfg.set(path + ".score_limit", limit);
                        cfg.set(path + ".count", 0);
                    }
                    else if (line.contains("Zasięg: ")) {
                        cfg.set(path + ".interval", Integer.parseInt(line.replace("§7Zasięg: §f", "")));
                    }
                    else if (line.contains("Czas: ")) {
                        String timeStr = line.replace("§7Czas: §f", "");
                        int ticks = timeStr.endsWith("s") ?
                                (int)(Double.parseDouble(timeStr.replace("s", "")) * 20) :
                                Integer.parseInt(timeStr.replace("t", ""));
                        cfg.set(path + ".interval", ticks);
                        cfg.set(path + ".next_tick", 0);
                    }
                }
            } else {
                // DOMYŚLNE WARTOŚCI
                if (type.matches("CLOCK|CLOCK_GATE|REPEATER")) {
                    cfg.set(path + ".interval", 20);
                    cfg.set(path + ".next_tick", 0);
                }
                if (type.equals("COUNTER")) {
                    cfg.set(path + ".score_limit", 10);
                    cfg.set(path + ".count", 0);
                }
                if (type.equals("SENSOR")) {
                    cfg.set(path + ".interval", 5);
                }
            }

            plugin.saveGates();
            e.getPlayer().sendMessage(LogicGates.PREFIX + " §aPostawiono bramkę §e" + type + " §6Wyjście: §e" + outFace);
        }
    }

    @EventHandler
    public void onGateInteract(PlayerInteractEvent e) {
        if (e.getHand() == EquipmentSlot.OFF_HAND || e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = e.getClickedBlock();
        if (block == null) return;

        String path = "gates." + manager.locToStr(block.getLocation());
        if (!plugin.getGatesConfig().contains(path)) return;

        String type = plugin.getGatesConfig().getString(path + ".type");
        Player p = e.getPlayer();

        if (type.matches("COUNTER|CLOCK|CLOCK_GATE|REPEATER")) {
            editingPlayers.put(p.getUniqueId(), path);

            p.sendMessage("");
            p.sendMessage(LogicGates.PREFIX + " §aWszedłeś w tryb edycji bramki §e" + type);
            if (type.equals("COUNTER")) {
                int current = plugin.getGatesConfig().getInt(path + ".score_limit");
                p.sendMessage("§7Wpisz na czacie: §6limit: <liczba 1-100> §7(Obecnie: " + current + ")");
            }
            else if (type.equals("SENSOR")) {
                int current = plugin.getGatesConfig().getInt(path + ".interval");
                p.sendMessage("§7Wpisz na czacie: §6zasięg: <liczba 1-15> §7(Obecnie: " + current + ")");
            } else {
                int current = plugin.getGatesConfig().getInt(path + ".interval");
                p.sendMessage("§7Wpisz na czacie: §6czas: <liczba>s §7lub §6<liczba>t");
            }
            p.sendMessage("§7Wpisz §6anuluj§7, aby wyjść bez zmian.");
            p.sendMessage("");
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerChat(org.bukkit.event.player.AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (!editingPlayers.containsKey(p.getUniqueId())) return;

        String path = editingPlayers.get(p.getUniqueId());
        String msg = e.getMessage().toLowerCase();

        // Wyjście z trybu edycji
        if (msg.equalsIgnoreCase("anuluj")) {
            editingPlayers.remove(p.getUniqueId());
            p.sendMessage(LogicGates.PREFIX + " §cEdycja anulowana.");
            e.setCancelled(true);
            return;
        }

        // Edycja LICZNIKA
        if (msg.startsWith("limit: ")) {
            try {
                int val = Integer.parseInt(msg.replace("limit: ", "").trim());
                if (val >= 1 && val <= 100) {
                    plugin.getGatesConfig().set(path + ".score_limit", val);
                    plugin.saveGates();
                    p.sendMessage(LogicGates.PREFIX + " §aUstawiono limit na: §e" + val);
                    editingPlayers.remove(p.getUniqueId());
                } else {
                    p.sendMessage(LogicGates.PREFIX + " §cLimit musi być w przedziale 1-100!");
                }
            } catch (NumberFormatException ex) {
                p.sendMessage(LogicGates.PREFIX + " §cBłędny format! Użyj: limit: <liczba>");
            }
            e.setCancelled(true);
        }

        // Edycja ZASIĘGU (Sensor)
        if (msg.startsWith("zasięg: ")) {
            String valStr = msg.replace("zasięg: ", "").trim();
            // Sprawdzamy czy to liczba bez try-catch
            if (valStr.matches("-?\\d+")) {
                int val = Integer.parseInt(valStr);
                if (val >= 1 && val <= 15) {
                    plugin.getGatesConfig().set(path + ".interval", val);
                    plugin.saveGates();
                    p.sendMessage(LogicGates.PREFIX + " §aUstawiono zasięg na: §e" + val + " bloków");
                    editingPlayers.remove(p.getUniqueId());
                } else {
                    p.sendMessage(LogicGates.PREFIX + " §cZasięg musi być w przedziale 1-15!");
                }
            } else {
                p.sendMessage(LogicGates.PREFIX + " §cBłędny format! Użyj: zasięg: <liczba>");
            }
            e.setCancelled(true);
        }

        // Edycja CZASU
        else if (msg.startsWith("czas: ")) {
            String valStr = msg.replace("czas: ", "").trim();
            int interval = 20;
            try {
                if (valStr.endsWith("s")) {
                    interval = (int) (Double.parseDouble(valStr.replace("s", "")) * 20);
                } else if (valStr.endsWith("t")) {
                    interval = Integer.parseInt(valStr.replace("t", ""));
                } else {
                    p.sendMessage(LogicGates.PREFIX + " §cDodaj 's' (sekundy) lub 't' (ticki)!");
                    e.setCancelled(true);
                    return;
                }

                plugin.getGatesConfig().set(path + ".interval", interval);
                plugin.saveGates();
                p.sendMessage(LogicGates.PREFIX + " §aUstawiono czas na: §e" + interval + "t");
                editingPlayers.remove(p.getUniqueId());
            } catch (NumberFormatException ex) {
                p.sendMessage(LogicGates.PREFIX + " §cBłędny format czasu!");
            }
            e.setCancelled(true);
        }
    }
}