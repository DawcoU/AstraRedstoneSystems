package pl.dawcou.astrars.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import pl.dawcou.astrars.AstraRS;
import pl.dawcou.astrars.selection.SelectionManager;
import pl.dawcou.astrars.system.SoundManager;

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
        Player p = (sender instanceof Player) ? (Player) sender : null;

        if (label.equalsIgnoreCase("astraredstonesystems") || label.equalsIgnoreCase("ars")) {

            // Brak argumentów lub komenda /ars help /ars pomoc
            if (args.length == 0 || (args.length == 1 && (args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("pomoc")))) {
                plugin.getNoticeManager().sendHelp(sender);
                if (p != null) {
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.SUCCESS);
                }
                return true;
            }

            // --- RELOAD ---
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                if (p != null && !p.hasPermission("astrars.reload")) {
                    p.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                    return true;
                }

                plugin.reload();

                sender.sendMessage(plugin.getLanguageManager().getWithPrefix("general.reload-success"));
                if (p != null) {
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.SUCCESS);
                }
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
                if (p != null) {
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.SUCCESS);
                }
                return true;
            }

            // --- UPDATE ---
            if (args.length == 1 && args[0].equalsIgnoreCase("update")) {
                if (p != null && !p.hasPermission("astrars.update")) {
                    p.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                    return true;
                }

                plugin.getAutoUpdater().checkAndDownload(sender);
                if (p != null) {
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.SUCCESS);
                }
                return true;
            }

            // --- BLOKADA DLA KONSOLI (Dalej wymagany jest gracz do edycji świata) ---
            if (p == null) {
                sender.sendMessage("§cTa komenda może być wykonana tylko w grze!");
                return true;
            }

            // --- SELECTOR ---
            if (args.length == 1 && args[0].equalsIgnoreCase("selector")) {
                if (!p.hasPermission("astrars.admin")) {
                    p.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                    return true;
                }
                selectionManager.giveSelector(p);
                plugin.getSoundManager().playSound(p, SoundManager.SoundType.SUCCESS);
                return true;
            }

            // --- CUT ---
            if (args.length == 1 && args[0].equalsIgnoreCase("cut")) {
                if (!p.hasPermission("astrars.admin")) {
                    p.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                    return true;
                }
                selectionManager.cutSelection(p);
                plugin.getSoundManager().playSound(p, SoundManager.SoundType.SUCCESS);
                return true;
            }

            // --- COPY ---
            if (args.length == 1 && args[0].equalsIgnoreCase("copy")) {
                if (!p.hasPermission("astrars.admin")) {
                    p.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                    return true;
                }
                selectionManager.copySelection(p);
                plugin.getSoundManager().playSound(p, SoundManager.SoundType.SUCCESS);
                return true;
            }

            // --- PASTE ---
            if (args.length == 1 && args[0].equalsIgnoreCase("paste")) {
                if (!p.hasPermission("astrars.admin")) {
                    p.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                    return true;
                }
                selectionManager.pasteSelection(p);
                plugin.getSoundManager().playSound(p, SoundManager.SoundType.SUCCESS);
                return true;
            }

            // --- UNDO ---
            if (args[0].equalsIgnoreCase("undo")) {
                if (!p.hasPermission("astrars.admin")) {
                    p.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                    return true;
                }
                selectionManager.undoPaste(p);
                plugin.getSoundManager().playSound(p, SoundManager.SoundType.SUCCESS);
                return true;
            }

            // --- REDO ---
            if (args[0].equalsIgnoreCase("redo")) {
                if (!p.hasPermission("astrars.admin")) {
                    p.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                    return true;
                }
                selectionManager.redoPaste(p);
                plugin.getSoundManager().playSound(p, SoundManager.SoundType.SUCCESS);
                return true;
            }

            // --- ROTATE ---
            if (args[0].equalsIgnoreCase("rotate")) {
                if (!p.hasPermission("astrars.admin")) {
                    p.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                    return true;
                }

                if (args.length < 2) {
                    p.sendMessage(plugin.getLanguageManager().getWithPrefix("clipboard.invalid-rotate-angle"));
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                    return true;
                }

                try {
                    int degrees = Integer.parseInt(args[1]);
                    if (degrees != 90 && degrees != -90 && degrees != 180) {
                        p.sendMessage(plugin.getLanguageManager().getWithPrefix("clipboard.invalid-rotate-angle"));
                        plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                        return true;
                    }
                    selectionManager.rotateSelection(p, degrees);
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.SUCCESS);
                } catch (NumberFormatException e) {
                    p.sendMessage(plugin.getLanguageManager().getWithPrefix("clipboard.invalid-rotate-angle"));
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                }
                return true;
            }

            // --- SCHEMATIC ---
            if (args[0].equalsIgnoreCase("schematic") || args[0].equalsIgnoreCase("schematics")) {
                if (!p.hasPermission("astrars.admin")) {
                    p.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                    return true;
                }

                if (args.length < 2) {
                    String msg = plugin.getLanguageManager().getWithPrefix("schema.specify-action");
                    if (msg != null) p.sendMessage(msg);
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                    return true;
                }

                if (args[1].equalsIgnoreCase("save")) {
                    if (args.length < 3) {
                        String msg = plugin.getLanguageManager().getWithPrefix("schema.specify-name");
                        if (msg != null) p.sendMessage(msg);
                        plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                        return true;
                    }

                    String schemaName = args[2];
                    if (!schemaName.matches("[a-zA-Z0-9_#-]+")) {
                        String invalidNameMsg = plugin.getLanguageManager().getWithPrefix("schema.invalid-name");
                        if (invalidNameMsg != null) p.sendMessage(invalidNameMsg);
                        plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                        return true;
                    }

                    selectionManager.saveClipboardToFile(p, schemaName);
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.SUCCESS);
                    return true;
                }

                if (args[1].equalsIgnoreCase("load")) {
                    if (args.length < 3) {
                        String msg = plugin.getLanguageManager().getWithPrefix("schema.specify-name");
                        if (msg != null) p.sendMessage(msg);
                        plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                        return true;
                    }

                    String schemaName = args[2];
                    selectionManager.loadClipboardFromFile(p, schemaName);
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.SUCCESS);
                    return true;
                }

                if (args[1].equalsIgnoreCase("delete")) {
                    if (args.length < 3) {
                        String msg = plugin.getLanguageManager().getWithPrefix("schema.specify-name");
                        if (msg != null) p.sendMessage(msg);
                        plugin.getSoundManager().playSound(p, SoundManager.SoundType.FAIL);
                        return true;
                    }

                    String schemaName = args[2];
                    selectionManager.deleteClipboardFile(p, schemaName);
                    plugin.getSoundManager().playSound(p, SoundManager.SoundType.SUCCESS);
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
            // Sprawdzenie uprawnień podstawowych dla podpowiedzi
            if (sender instanceof Player player) {
                boolean hasAdmin = player.hasPermission("astrars.admin");
                boolean hasReload = player.hasPermission("astrars.reload");
                boolean hasUpdate = player.hasPermission("astrars.update");

                if (!hasAdmin && !hasReload && !hasUpdate) {
                    return hints; // Zwykły gracz bez uprawnień administracyjnych nie widzi podpowiedzi /ars
                }

                if (args.length == 1) {
                    List<String> availableCommands = new ArrayList<>();
                    availableCommands.add("help");
                    availableCommands.add("pomoc");
                    if (hasReload) availableCommands.add("reload");
                    availableCommands.add("info");
                    if (hasUpdate) availableCommands.add("update");
                    if (hasAdmin) {
                        availableCommands.addAll(Arrays.asList("selector", "cut", "paste", "copy", "rotate", "undo", "redo", "schematic"));
                    }

                    availableCommands.forEach(a -> {
                        if (a.toLowerCase().startsWith(args[0].toLowerCase())) hints.add(a);
                    });

                } else if (args.length == 2) {
                    List<String> subArgs = switch (args[0].toLowerCase()) {
                        case "rotate" -> hasAdmin ? Arrays.asList("90", "-90", "180") : Collections.emptyList();
                        case "schematic", "schematics" -> hasAdmin ? Arrays.asList("save", "load", "delete") : Collections.emptyList();
                        default -> Collections.emptyList();
                    };

                    subArgs.forEach(t -> {
                        if (t.toLowerCase().startsWith(args[1].toLowerCase())) hints.add(t);
                    });

                } else if (args.length == 3 && hasAdmin) {
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
        }
        return hints;
    }
}