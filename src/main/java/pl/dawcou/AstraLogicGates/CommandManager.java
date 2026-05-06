package pl.dawcou.AstraLogicGates;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class CommandManager implements org.bukkit.command.CommandExecutor, org.bukkit.command.TabCompleter {

    private final AstraLogicGates plugin;
    private final SelectionManager selectionManager;

    public CommandManager(AstraLogicGates plugin, SelectionManager selectionManager) {
        this.plugin = plugin;
        this.selectionManager = selectionManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (label.equalsIgnoreCase("astralogicgates") || label.equalsIgnoreCase("alg")) {

            // --- RELOAD (Twoje wiadomości i logika) ---
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                if (!player.hasPermission("astralogicgates.reload")) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("no-permission"));
                    return true;
                }
                plugin.reloadConfig();
                plugin.setLanguageManager(new LanguageManager(plugin));
                player.sendMessage(plugin.getLanguageManager().getWithPrefix("reload-success"));
                return true;
            }

            // --- INFO (Twoje wiadomości) ---
            if (args.length == 1 && args[0].equalsIgnoreCase("info")) {
                player.sendMessage("§7------------ §6AstraLogicGates §7----------");
                player.sendMessage("§aPlugin created by: §eDawcoU");
                player.sendMessage("§aPlugin version: §ev" + plugin.getPluginMeta().getVersion());
                player.sendMessage("");
                player.sendMessage("§6Copyright © 2026 DawcoU All rights reserved");
                player.sendMessage("§7-----------------------");
                return true;
            }

            // --- SELECTOR ---
            if (args.length == 1 && args[0].equalsIgnoreCase("selector")) {
                if (!player.hasPermission("astralogicgates.admin")) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("no-permission"));
                    return true;
                }
                selectionManager.giveSelector(player);
                return true;
            }

            // --- CUT ---
            if (args.length == 1 && args[0].equalsIgnoreCase("cut")) {
                if (!player.hasPermission("astralogicgates.admin")) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("no-permission"));
                    return true;
                }
                selectionManager.cutSelection(player);
                return true;
            }

            // --- COPY ---
            if (args.length == 1 && args[0].equalsIgnoreCase("copy")) {
                if (!player.hasPermission("astralogicgates.admin")) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("no-permission"));
                    return true;
                }
                selectionManager.copySelection(player);
                return true;
            }

            // --- PASTE ---
            if (args.length == 1 && args[0].equalsIgnoreCase("paste")) {
                if (!player.hasPermission("astralogicgates.admin")) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("no-permission"));
                    return true;
                }
                selectionManager.pasteSelection(player);
                return true;
            }

            // --- LINK ---
            // --- LINK ---
            if (args[0].equalsIgnoreCase("link")) {
                if (!player.hasPermission("astralogicgates.admin")) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("no-permission"));
                    return true;
                }

                Block b = player.getTargetBlockExact(8);
                if (b == null || b.getType() == Material.AIR) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("link-no-block"));
                    return true;
                }

                Location loc = b.getLocation();
                String path = "gates." + GateUtils.locToStr(loc);
                if (!plugin.getGatesConfig().contains(path)) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("link-not-gate"));
                    return true;
                }

                UUID uuid = player.getUniqueId();
                if (!plugin.getLinkingSession().containsKey(uuid)) {
                    plugin.getLinkingSession().put(uuid, loc);
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("link-step-1"));
                } else {
                    Location origin = plugin.getLinkingSession().get(uuid);
                    String originPath = "gates." + GateUtils.locToStr(origin);
                    String targetStr = GateUtils.locToStr(loc);

                    List<String> links = plugin.getGatesConfig().getStringList(originPath + ".target_links");

                    if (!links.contains(targetStr)) {
                        links.add(targetStr);
                        plugin.getGatesConfig().set(originPath + ".target_links", links);
                        plugin.getGatesConfig().set(originPath + ".target_link", null);

                        plugin.saveGates();
                        player.sendMessage(plugin.getLanguageManager().getWithPrefix("link-step-2"));
                    } else {
                        // Dodaj ten klucz do swojego messages_pl.yml / messages_en.yml
                        player.sendMessage(plugin.getLanguageManager().getWithPrefix("link-already-exists"));
                    }
                    plugin.getLinkingSession().remove(uuid);
                }
                return true;
            }

            // --- UNLINK ---
            if (args[0].equalsIgnoreCase("unlink")) {
                if (!player.hasPermission("astralogicgates.admin")) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("no-permission"));
                    return true;
                }

                Block b = player.getTargetBlockExact(8);
                if (b == null || b.getType() == Material.AIR) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("link-no-block"));
                    return true;
                }

                Location loc = b.getLocation();
                String path = "gates." + GateUtils.locToStr(loc);

                if (!plugin.getGatesConfig().contains(path)) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("link-not-gate"));
                    return true;
                }

                plugin.getGatesConfig().set(path + ".target_links", null);
                plugin.getGatesConfig().set(path + ".target_link", null);

                plugin.saveGates();
                // Dodaj ten klucz do plików językowych
                player.sendMessage(plugin.getLanguageManager().getWithPrefix("unlink-success"));
                return true;
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> hints = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("astralogicgates") || command.getName().equalsIgnoreCase("alg")) {
            if (args.length == 1) {
                Arrays.asList("info", "link", "unlink", "reload", "selector", "cut", "paste", "copy").forEach(a -> { if (a.startsWith(args[0].toLowerCase())) hints.add(a); });

            }
        }
        return hints;
    }
}