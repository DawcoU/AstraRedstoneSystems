package pl.dawcou.AstraLogicGates;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class AstraLogicGates extends JavaPlugin implements CommandExecutor, TabCompleter {

    public static final String PREFIX = ChatColor.of("#FF0000") + "[" + ChatColor.of("#00D5FF") + "Astra" + ChatColor.of("#FFFFFF") + "Logic" + ChatColor.of("#FF0000") + "]";
    public static final String PREFIX2 = "§c[§bAstra§fLogic§c]";

    private File gatesFile;
    private FileConfiguration gatesConfig;
    private final Map<UUID, Location> linkingSession = new HashMap<>();

    private GateManager gateManager;
    private LanguageManager languageManager;
    private NoticeManager noticeManager;

    public void setLanguageManager(LanguageManager languageManager) {
        this.languageManager = languageManager;
    }

    public GateManager getGateManager() {
        return gateManager;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public NoticeManager getNoticeManager() {
        return noticeManager;
    }

    @Override
    public void onEnable() {
        this.noticeManager = new NoticeManager(this);
        this.languageManager = new LanguageManager(this);
        this.gateManager = new GateManager(this);
        GateConverter converter = new GateConverter(this);
        converter.runAllMigrations();

        saveDefaultConfig();
        createGatesConfig();

        FilesUpdater updater = new FilesUpdater(this);
        updater.check();

        getServer().getPluginManager().registerEvents(new GateListener(this, gateManager), this);

        var cmdBramka = getCommand("bramka");
        if (cmdBramka != null) {
            cmdBramka.setExecutor(this);
            cmdBramka.setTabCompleter(this);
        }

        var cmdAlg = getCommand("astralogicgates");
        if (cmdAlg != null) {
            cmdAlg.setExecutor(this);
            cmdAlg.setTabCompleter(this);
        }

        Bukkit.getScheduler().runTaskTimer(this, gateManager::checkGates, 1L, 1L);

        noticeManager.sendStartupLogo();

        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            if (getConfig().getBoolean("check-updates", true)) {
                new UpdateChecker(this).getVersion(version -> {
                    String currentVersion = this.getPluginMeta().getVersion();
                    if (currentVersion.equals(version)) {
                        noticeManager.sendVersionOk(version);
                    } else if (currentVersion.compareTo(version) > 0) {
                        noticeManager.sendDevNotice(currentVersion, version);
                    } else {
                        noticeManager.sendUpdateNotice(Bukkit.getConsoleSender(), version);
                    }
                });
            }
        });
    }

    @Override
    public void onDisable() {
        noticeManager.sendShutdownLogo();
    }

    private void createGatesConfig() {
        gatesFile = new File(getDataFolder(), "logic_gates.yml");
        if (!gatesFile.exists()) {
            if (gatesFile.getParentFile().mkdirs()) {
                try {
                    gatesFile.createNewFile();
                } catch (IOException ignored) {}
            }
        }
        gatesConfig = YamlConfiguration.loadConfiguration(gatesFile);
    }

    public FileConfiguration getGatesConfig() {
        return gatesConfig;
    }

    public void saveGates() {
        try {
            gatesConfig.save(gatesFile);
        } catch (IOException ignored) {}
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (command.getName().equalsIgnoreCase("astralogicgates") || command.getName().equalsIgnoreCase("alg")) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("astralogicgates.reload")) {
                    sender.sendMessage(this.getLanguageManager().getWithPrefix("no-permission"));
                    return true;
                }
                this.reloadConfig();
                this.languageManager = new LanguageManager(this);
                sender.sendMessage(this.getLanguageManager().getWithPrefix("reload-success"));
                return true;
            }

            if (args.length == 1 && args[0].equalsIgnoreCase("info")) {
                sender.sendMessage("§7------------ " + PREFIX + " §7----------");
                sender.sendMessage("§aPlugin created by: §eDawcoU");
                sender.sendMessage("§aPlugin version: §ev" + getPluginMeta().getVersion());
                sender.sendMessage("");
                sender.sendMessage("§6Copyright © 2026 DawcoU All rights reserved");
                sender.sendMessage("§7-----------------------");
                return true;
            }

            if (args.length >= 1 && args[0].equalsIgnoreCase("link")) {
                if (!sender.hasPermission("astralogicgates.admin")) {
                    sender.sendMessage(this.getLanguageManager().getWithPrefix("no-permission"));
                    return true;
                }

                Block b = player.getTargetBlockExact(5);
                if (b == null || b.getType() == Material.AIR) {
                    player.sendMessage(languageManager.getWithPrefix("link-no-block"));
                    return true;
                }
                Location loc = b.getLocation();
                String path = "gates." + GateUtils.locToStr(loc);
                if (!getGatesConfig().contains(path)) {
                    player.sendMessage(languageManager.getWithPrefix("link-not-gate"));
                    return true;
                }
                UUID uuid = player.getUniqueId();
                if (!linkingSession.containsKey(uuid)) {
                    linkingSession.put(uuid, loc);
                    player.sendMessage(languageManager.getWithPrefix("link-step-1"));
                } else {
                    Location origin = linkingSession.get(uuid);
                    getGatesConfig().set("gates." + GateUtils.locToStr(origin) + ".target_link", GateUtils.locToStr(loc));
                    saveGates();
                    player.sendMessage(languageManager.getWithPrefix("link-step-2"));
                    linkingSession.remove(uuid);
                }
                return true;
            }
        }

        if (label.equalsIgnoreCase("bramka")) {
            if (!player.hasPermission("astralogicgates.gates")) {
                player.sendMessage(this.getLanguageManager().getWithPrefix("no-permission"));
                return true;
            }
            if (args.length < 2) {
                player.sendMessage(this.getLanguageManager().getWithPrefix("usage-gate-command"));
                return true;
            }

            String category = args[0].toLowerCase();
            String type = args[1].toUpperCase();
            Material mat = switch (category) {
                case "logic" -> switch (type) {
                    case "NOT" -> Material.RED_CONCRETE;
                    case "AND" -> Material.ORANGE_CONCRETE;
                    case "OR" -> Material.YELLOW_CONCRETE;
                    case "NOR" -> Material.GRAY_CONCRETE;
                    case "NAND" -> Material.PINK_CONCRETE;
                    case "XOR" -> Material.PURPLE_CONCRETE;
                    case "XNOR" -> Material.MAGENTA_CONCRETE;
                    case "NIMPLY" -> Material.BROWN_CONCRETE;
                    case "RANDOM" -> Material.CYAN_CONCRETE;
                    case "BOOSTER" -> Material.ORANGE_CONCRETE;
                    default -> null;
                };
                case "memory" -> switch (type) {
                    case "LATCH" -> Material.WHITE_CONCRETE;
                    case "TFF" -> Material.LIGHT_BLUE_CONCRETE;
                    case "MEMORY_CELL", "MEMORY_READ" -> Material.BLUE_CONCRETE;
                    default -> null;
                };
                case "data" -> switch (type) {
                    case "COUNTER" -> Material.IRON_BLOCK;
                    case "NUMBER_GATE" -> Material.YELLOW_CONCRETE;
                    case "BOOLEAN_GATE" -> Material.ORANGE_CONCRETE;
                    case "VARIABLE_GATE" -> Material.RED_CONCRETE;
                    case "MATH" -> Material.BLUE_CONCRETE;
                    case "COMPARATOR", "DECODER" -> Material.GRAY_CONCRETE;
                    case "LINKER" -> Material.CYAN_CONCRETE;
                    default -> null;
                };
                case "space" -> switch (type) {
                    case "SENDER" -> Material.GOLD_BLOCK;
                    case "RECEIVER" -> Material.EMERALD_BLOCK;
                    case "SENSOR" -> Material.LAPIS_BLOCK;
                    default -> null;
                };
                case "time" -> switch (type) {
                    case "CLOCK" -> Material.GREEN_CONCRETE;
                    case "CLOCK_GATE" -> Material.LIME_CONCRETE;
                    case "REPEATER" -> Material.YELLOW_CONCRETE;
                    case "SYNCHRONIZER" -> Material.RED_CONCRETE;
                    default -> null;
                };
                default -> null;
            };

            if (mat == null) {
                player.sendMessage(this.getLanguageManager().getWithPrefix("unknown-type-gate"));
                return true;
            }

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return true;

            meta.setDisplayName("§eBramka: §6" + type);
            List<String> lore = new ArrayList<>();

            if (type.equals("SENDER") || type.equals("RECEIVER")) {
                if (args.length < 3) {
                    player.sendMessage(this.getLanguageManager().getWithPrefix("need-channel"));
                    return true;
                }
                lore.add("§7Kanał: §f" + args[2].replace(" ", ""));

            } else if (type.equals("NUMBER_GATE") || type.equals("DECODER")) {
                if (args.length < 3) {
                    player.sendMessage(this.getLanguageManager().getWithPrefix("provide-value"));
                    return true;
                }

                if (!args[2].matches("-?\\d+")) {
                    player.sendMessage(this.getLanguageManager().getWithPrefix("not-a-number"));
                    return true;
                }

                lore.add("§7Wartość: §f" + args[2]);

            } else if (type.equals("MATH")) {
                if (args.length < 3) {
                    player.sendMessage(this.getLanguageManager().getWithPrefix("provide-mode"));
                    return true;
                }

                String modeName = (args[2].equalsIgnoreCase("sub") || args[2].equalsIgnoreCase("subtract"))
                        ? "Subtract"
                        : "Add";

                lore.add("§7Tryb: §f" + modeName);

            } else if (type.equals("COMPARATOR")) {
                if (args.length < 3) {
                    player.sendMessage(this.getLanguageManager().getWithPrefix("provide-sign"));
                    return true;
                }

                String sign = args[2];

                if (!sign.matches(">|<|==|!=|>=|<=")) {
                    player.sendMessage(this.getLanguageManager().getWithPrefix("invalid-sign"));
                    return true;
                }

                lore.add("§7Tryb: §f" + sign);

            } else if (type.equals("VARIABLE_GATE")) {
                lore.add("§8(Przyjmuje sygnały liczbowe i binarne)");

            } else if (type.equals("BOOLEAN_GATE")) {
                lore.add("§8(Wysyła 1 gdy włączona, 0 gdy wyłączona)");

            } else if (type.equals("COUNTER")) {
                if (args.length < 3) {
                    player.sendMessage(this.getLanguageManager().getWithPrefix("provide-limit"));
                    return true;
                }

                if (!args[2].matches("-?\\d+")) {
                    player.sendMessage(this.getLanguageManager().getWithPrefix("not-a-number"));
                    return true;
                }

                int val = Integer.parseInt(args[2]);

                if (val < 1 || val > 100) {
                    player.sendMessage(this.getLanguageManager().getWithPrefix("limit-range"));
                    return true;
                }

                lore.add("§7Limit: §f" + args[2]);

            } else if (type.equals("SENSOR")) {
                if (args.length < 3) {
                    player.sendMessage(this.getLanguageManager().getWithPrefix("provide-range"));
                    return true;
                }

                if (!args[2].matches("-?\\d+")) {
                    player.sendMessage(this.getLanguageManager().getWithPrefix("not-a-number"));
                    return true;
                }

                int val = Integer.parseInt(args[2]);

                if (val < 1 || val > 15) {
                    player.sendMessage(this.getLanguageManager().getWithPrefix("range-out-of-bounds"));
                    return true;
                }

                lore.add("§7Zasięg: §f" + args[2]);

            } else if (type.matches("CLOCK|CLOCK_GATE|REPEATER")) {
                if (args.length < 3) {
                    player.sendMessage(this.getLanguageManager().getWithPrefix("provide-delay"));
                    return true;
                }

                String input = args[2].toLowerCase();

                if (!input.endsWith("t") && !input.endsWith("s")) {
                    player.sendMessage(this.getLanguageManager().getWithPrefix("provide-unit"));
                    return true;
                }

                String numStr = input.substring(0, input.length() - 1);

                if (!numStr.matches("-?\\d+")) {
                    player.sendMessage(this.getLanguageManager().getWithPrefix("invalid-time-format"));
                    return true;
                }

                int val = Integer.parseInt(numStr);

                if (input.endsWith("t")) {
                    if (val < 5 || val > 200) {
                        player.sendMessage(this.getLanguageManager().getWithPrefix("ticks-range-reapeter"));
                        return true;
                    }
                } else if (input.endsWith("s")) {
                    if (val < 1 || val > 10) {
                        player.sendMessage(this.getLanguageManager().getWithPrefix("seconds-range-reapeter"));
                        return true;
                    }
                }

                lore.add("§7Czas: §f" + input);
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
            player.getInventory().addItem(item);
            player.sendMessage(this.getLanguageManager().getWithPrefix("gate-received", "{TYPE}", type));
            return true;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> hints = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("bramka")) {
            if (args.length == 1) {
                Arrays.asList("logic", "memory", "data", "space", "time").forEach(c -> { if (c.startsWith(args[0].toLowerCase())) hints.add(c); });
            } else if (args.length == 2) {
                List<String> types = switch (args[0].toLowerCase()) {
                    case "logic" -> Arrays.asList("NOT", "AND", "OR", "NOR", "NAND", "XOR", "XNOR", "NIMPLY", "RANDOM", "BOOSTER");
                    case "memory" -> Arrays.asList("LATCH", "TFF", "MEMORY_CELL", "MEMORY_READ");
                    case "data" -> Arrays.asList("COUNTER", "NUMBER_GATE", "BOOLEAN_GATE", "VARIABLE_GATE", "MATH", "COMPARATOR", "DECODER", "LINKER");
                    case "space" -> Arrays.asList("SENDER", "RECEIVER", "SENSOR");
                    case "time" -> Arrays.asList("CLOCK", "CLOCK_GATE", "REPEATER", "SYNCHRONIZER");
                    default -> Collections.emptyList();
                };
                types.forEach(t -> { if (t.startsWith(args[1].toUpperCase())) hints.add(t); });
            } else if (args.length == 3) {
                String type = args[1].toUpperCase();
                if (type.matches("CLOCK|CLOCK_GATE|REPEATER")) hints.addAll(Arrays.asList("10t", "1s"));
                else if (type.equals("MATH")) hints.addAll(Arrays.asList("ADD", "SUBTRACT"));
                else if (type.equals("COMPARATOR")) hints.addAll(Arrays.asList(">", "<", "==", "!=", ">=", "<="));
                else if (type.equals("SENSOR")) hints.add("5");
                else if (type.equals("COUNTER")) hints.add("10");
            }
        } else if (command.getName().equalsIgnoreCase("astralogicgates") || command.getName().equalsIgnoreCase("alg")) {
            if (args.length == 1) {
                Arrays.asList("info", "link", "reload").forEach(a -> { if (a.startsWith(args[0].toLowerCase())) hints.add(a); });
            }
        }
        return hints;
    }
}