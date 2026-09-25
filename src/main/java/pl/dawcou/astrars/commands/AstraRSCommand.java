package pl.dawcou.astrars.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import pl.dawcou.astrars.AstraRS;
import pl.dawcou.astrars.selection.SelectionManager;

import java.io.File;
import java.util.*;

public class AstraRSCommand implements CommandExecutor, TabCompleter {

    private final AstraRS plugin;
    private final SelectionManager selectionManager;

    public AstraRSCommand(AstraRS plugin, SelectionManager selectionManager) {
        this.plugin = plugin;
        this.selectionManager = selectionManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (label.equalsIgnoreCase("astraredstonesystems") || label.equalsIgnoreCase("ars")) {

            // Brak argumentów lub komenda /ars help /ars pomoc
            if (args.length == 0 || (args.length == 1 && (args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("pomoc")))) {
                plugin.getNoticeManager().sendHelp(sender);
                return true;
            }

            // --- RELOAD ---
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                // Sprawdzamy permisję tylko jeśli to gracz, żeby konsola mogła zawsze przeładować
                if (sender instanceof Player) {
                    Player p = (Player) sender;
                    if (!p.hasPermission("astrars.reload")) {
                        p.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
                        return true;
                    }
                }

                plugin.getGateDataManager().load();

                plugin.reloadConfig();

                plugin.debugMode = plugin.getConfig().getBoolean("settings.debug.enabled", false);

                plugin.getLanguageManager().reload();

                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("general.reload-success"));
                return true;
            }

            // --- INFO ---
            if (args.length == 1 && args[0].equalsIgnoreCase("info")) {
                String prefix = (sender instanceof org.bukkit.command.ConsoleCommandSender) ? AstraRS.PREFIX2 : AstraRS.PREFIX;

                sender.sendMessage(plugin.getLanguageManager().parseToLegacy("<gray>------------ " + prefix + " <gray>----------"));
                sender.sendMessage("§aPlugin created by: §e" + plugin.getAuthor());
                sender.sendMessage("§aPlugin version: §ev" + plugin.getDescription().getVersion());
                sender.sendMessage("");
                sender.sendMessage("§6Copyright © 2026 " + plugin.getAuthor());
                sender.sendMessage("§7Licensed under the MIT License");
                sender.sendMessage("§7-----------------------");
                return true;
            }

            // --- BLOKADA DLA KONSOLI ---
            // Wszystko powyżej działa dla konsoli i graczy, wszystko poniżej tylko dla graczy
            if (!(sender instanceof Player)) {
                return true;
            }

            Player player = (Player) sender;

            // --- SELECTOR ---
            if (args.length == 1 && args[0].equalsIgnoreCase("selector")) {
                if (!player.hasPermission("astrars.admin")) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
                    return true;
                }
                selectionManager.giveSelector(player);
                return true;
            }

            // --- CUT ---
            if (args.length == 1 && args[0].equalsIgnoreCase("cut")) {
                if (!player.hasPermission("astrars.admin")) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
                    return true;
                }
                selectionManager.cutSelection(player);
                return true;
            }

            // --- COPY ---
            if (args.length == 1 && args[0].equalsIgnoreCase("copy")) {
                if (!player.hasPermission("astrars.admin")) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
                    return true;
                }
                selectionManager.copySelection(player);
                return true;
            }

            // --- PASTE ---
            if (args.length == 1 && args[0].equalsIgnoreCase("paste")) {
                if (!player.hasPermission("astrars.admin")) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
                    return true;
                }
                selectionManager.pasteSelection(player);
                return true;
            }

            // --- UNDO ---
            if (args[0].equalsIgnoreCase("undo")) {
                if (!player.hasPermission("astrars.admin")) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
                    return true;
                }

                // Wywołujemy logikę cofania
                selectionManager.undoPaste(player);
                return true;
            }

            // --- REDO ---
            if (args[0].equalsIgnoreCase("redo")) {
                if (!player.hasPermission("astrars.admin")) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
                    return true;
                }

                // Wywołujemy logikę cofania
                selectionManager.redoPaste(player);
                return true;
            }

            // --- ROTATE ---
            if (args[0].equalsIgnoreCase("rotate")) {
                if (!player.hasPermission("astrars.admin")) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
                    return true;
                }

                // Sprawdzamy, czy gracz podał stopnie (np. /ars rotate 90)
                if (args.length < 2) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("clipboard.invalid-rotate-angle"));
                    return true;
                }

                try {
                    int degrees = Integer.parseInt(args[1]);

                    // Walidacja: tylko 90, -90, 180 są sensowne dla bramek
                    if (degrees != 90 && degrees != -90 && degrees != 180) {
                        player.sendMessage(plugin.getLanguageManager().getWithPrefix("clipboard.invalid-rotate-angle"));
                        return true;
                    }

                    // WYWOŁUJEMY ROTACJĘ
                    selectionManager.rotateSelection(player, degrees);

                } catch (NumberFormatException e) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("clipboard.invalid-rotate-angle"));
                }
                return true;
            }

            if (args[0].equalsIgnoreCase("schematic") || args[0].equalsIgnoreCase("schematics")) {
                if (!player.hasPermission("astrars.admin")) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
                    return true;
                }

                // 1. Sprawdzamy, czy gracz w ogóle podał drugi argument (save lub load)
                if (args.length < 2) {
                    String msg = plugin.getLanguageManager().getWithPrefix("schema.specify-action");
                    if (msg != null) {
                        player.sendMessage(msg);
                    }
                    return true;
                }

                // --- PODKOMENDA: SAVE (/ars schematic save <nazwa>) ---
                if (args[1].equalsIgnoreCase("save")) {
                    // Sprawdzamy, czy podano nazwę (musi być args[2])
                    if (args.length < 3) {
                        String msg = plugin.getLanguageManager().getWithPrefix("schema.specify-name");
                        if (msg != null) {
                            player.sendMessage(msg);
                        }
                        return true;
                    }

                    String schemaName = args[2];

                    // Bezpieczna weryfikacja nazwy pliku
                    if (!schemaName.matches("[a-zA-Z0-9_#-]+")) {
                        String invalidNameMsg = plugin.getLanguageManager().getWithPrefix("schema.invalid-name");
                        if (invalidNameMsg != null) {
                            player.sendMessage(invalidNameMsg);
                        }
                        return true;
                    }

                    selectionManager.saveClipboardToFile(player, schemaName);
                    return true;
                }

                // --- PODKOMENDA: LOAD (/ars schematic load <nazwa>) ---
                if (args[1].equalsIgnoreCase("load")) {
                    // Sprawdzamy, czy podano nazwę (musi być args[2])
                    if (args.length < 3) {
                        String msg = plugin.getLanguageManager().getWithPrefix("schema.specify-name");
                        if (msg != null) {
                            player.sendMessage(msg);
                        }
                        return true;
                    }

                    String schemaName = args[2];
                    selectionManager.loadClipboardFromFile(player, schemaName);
                    return true;
                }

                // --- PODKOMENDA: DELETE (/ars schematic delete <nazwa>) ---
                if (args[1].equalsIgnoreCase("delete")) {
                    // 1. Sprawdzamy, czy podano nazwę (musi być args[2])
                    if (args.length < 3) {
                        String msg = plugin.getLanguageManager().getWithPrefix("schema.specify-name");
                        if (msg != null) {
                            player.sendMessage(msg);
                        }
                        return true;
                    }

                    String schemaName = args[2];

                    // 3. Wywołanie ukrytej logiki z menedżera
                    selectionManager.deleteClipboardFile(player, schemaName);
                    return true;
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> hints = new ArrayList<>();
        String cmd = command.getName();

        if (cmd.equalsIgnoreCase("astraredstonesystems") || cmd.equalsIgnoreCase("ars")) {
            if (args.length == 1) {
                // Podpowiedzi dla głównej komendy (args[0])
                Arrays.asList("info", "help", "reload", "selector", "cut", "paste", "copy", "rotate", "undo", "redo", "schematic")
                        .forEach(a -> {
                            if (a.startsWith(args[0].toLowerCase())) hints.add(a);
                        });

            } else if (args.length == 2) {
                // Podpowiedzi dla drugiego argumentu (args[1])
                List<String> subArgs = switch (args[0].toLowerCase()) {
                    case "rotate" -> Arrays.asList("90", "-90", "180");
                    case "schematic", "schematics" -> Arrays.asList("save", "load", "delete");
                    default -> Collections.emptyList();
                };

                subArgs.forEach(t -> {
                    if (t.toLowerCase().startsWith(args[1].toLowerCase())) hints.add(t);
                });

            } else if (args.length == 3) {
                // Podpowiedzi dla trzeciego argumentu (args[2])
                if (args[0].equalsIgnoreCase("schematic") || args[0].equalsIgnoreCase("schematics")) {

                    // Jeśli gracz wpisał /ars schematic load -> podpowiadamy ISTNIEJĄCE pliki z dysku!
                    if (args[1].equalsIgnoreCase("load") || args[1].equalsIgnoreCase("delete")) {
                        File schematicsDir = new File(plugin.getDataFolder(), "schematics");
                        if (schematicsDir.exists() && schematicsDir.isDirectory()) {
                            File[] files = schematicsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
                            if (files != null) {
                                for (File file : files) {
                                    String fileName = file.getName().substring(0, file.getName().length() - 5);
                                    if (fileName.toLowerCase().startsWith(args[2].toLowerCase())) {
                                        hints.add(fileName);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return hints;
    }
}