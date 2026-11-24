package pl.dawcou.astrars.commands;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import pl.dawcou.astrars.AstraRS;
import pl.dawcou.astrars.system.SoundManager;

import java.util.*;

public class GateCommand implements CommandExecutor, TabCompleter {

    private final AstraRS plugin;

    public GateCommand(AstraRS plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (label.equalsIgnoreCase("bramka") || label.equalsIgnoreCase("gate")) {
            if (!player.hasPermission("astrars.gates")) {
                player.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
                plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
                return true;
            }
            if (args.length < 2) {
                player.sendMessage(plugin.getLanguageManager().getWithPrefix("gate-cmd.usage"));
                plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
                return true;
            }

            String category = args[0].toLowerCase();
            String type = args[1].toUpperCase();

            // --- DYNAMICZNE SPRAWDZENIE PERMISJI ---
            String permission = "astrars.gates." + category;
            if (!player.hasPermission(permission)) {
                player.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
                plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
                return true;
            }

            Material mat = switch (category) {
                case "logic" -> switch (type) {
                    case "NOT", "NOR" -> Material.RED_CONCRETE;
                    case "AND", "OR", "BUFFER" -> Material.YELLOW_CONCRETE;
                    case "NAND", "XNOR", "NIMPLY" -> Material.ORANGE_CONCRETE;
                    case "XOR", "IMPLY", "MUX" -> Material.LIME_CONCRETE;
                    default -> null;
                };
                case "memory" -> switch (type) {
                    case "LATCH" -> Material.CYAN_CONCRETE;
                    case "TFF" -> Material.LIGHT_BLUE_CONCRETE;
                    case "MEMORY_CELL" -> Material.BLUE_CONCRETE;
                    default -> null;
                };
                case "number" -> switch (type) {
                    case "MATH", "DECIMAL_ACCUMULATOR" -> Material.BLUE_CONCRETE;
                    case "COUNTER" -> Material.LIGHT_GRAY_CONCRETE;
                    case "COMPARATOR", "DECODER" -> Material.GRAY_CONCRETE;
                    case "RANDOM_NUMBER" -> Material.CYAN_CONCRETE;
                    case "NUMBER_SENDER" -> Material.BROWN_CONCRETE;
                    default -> null;
                };
                case "boolean" -> switch (type) {
                    case "RANDOM_BOOLEAN" -> Material.CYAN_CONCRETE;
                    case "BOOLEAN_SENDER" -> Material.BROWN_CONCRETE;
                    default -> null;
                };
                case "string" -> switch (type) {
                    case "STRING_COMPARATOR", "STRING_DECODER" -> Material.GRAY_CONCRETE;
                    case "STRING_SENDER" -> Material.BROWN_CONCRETE;
                    default -> null;
                };
                case "data" -> switch (type) {
                    case "CABLE_DATA" -> Material.BLACK_CONCRETE;
                    case "DISPLAY" -> Material.WHITE_CONCRETE;
                    case "TRANSISTOR" -> Material.RED_CONCRETE;
                    case "DISK" -> Material.LIGHT_BLUE_CONCRETE;
                    case "RAM" -> Material.GREEN_CONCRETE;
                    case "BATTERY" -> Material.ORANGE_CONCRETE;
                    case "DATA_DETECTOR" -> Material.LIME_CONCRETE;
                    default -> null;
                };
                case "space" -> switch (type) {
                    case "SENDER" -> Material.MAGENTA_CONCRETE;
                    case "RECEIVER" -> Material.PURPLE_CONCRETE;
                    case "SENSOR" -> Material.PINK_CONCRETE;
                    default -> null;
                };
                case "time" -> switch (type) {
                    case "CLOCK" -> Material.GREEN_CONCRETE;
                    case "REPEATER", "PULSER" -> Material.YELLOW_CONCRETE;
                    default -> null;
                };
                default -> null;
            };

            if (mat == null) {
                player.sendMessage(plugin.getLanguageManager().getWithPrefix("gate.unknown-type"));
                plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
                return true;
            }

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return true;

            String langPrefix = plugin.getLanguageManager().getMessage("gate.prefix-item");
            if (langPrefix == null || langPrefix.isEmpty()) {
                langPrefix = "&4Bramka: &c";
            }

            // Składamy tekst i zamieniamy kolory z & na §
            String fullText = langPrefix + type.toUpperCase();
            String coloredText = org.bukkit.ChatColor.translateAlternateColorCodes('&', fullText);

            // Usuwamy domyślne pochylenie (italic) w Minecraft za pomocą prefixu §r (reset formatting)
            meta.setDisplayName("§r" + coloredText);
            item.setItemMeta(meta);

            // Ustawiamy puste lore, żebym meta nie różniła się strukturą od dropu bazowego
            List<String> lore = new ArrayList<>();

            // --- ZASZYWANIE DANYCH LOGICZNYCH W PRZEDMIOCIE (PDC) ---
            org.bukkit.NamespacedKey typeKey = new org.bukkit.NamespacedKey(plugin, "gate_type");

            meta.getPersistentDataContainer().set(typeKey, org.bukkit.persistence.PersistentDataType.STRING, type.toUpperCase());

            if (type.equals("SENDER") || type.equals("RECEIVER")) {
                if (args.length < 3) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("gate-cmd.need-channel"));
                    plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
                    return true;
                }
                lore.add("§7Channel: §f" + args[2].replace(" ", ""));

            } else if (type.equals("NUMBER_SENDER") || type.equals("DECODER")) {
                if (args.length < 3) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("gate-cmd.provide-value"));
                    plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
                    return true;
                }

                try {
                    double value = Double.parseDouble(args[2]);

                    if (!Double.isFinite(value)) {
                        throw new NumberFormatException();
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("gate-cmd.not-a-number"));
                    plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
                    return true;
                }

                double value = Double.parseDouble(args[2]);
                lore.add("§7Value: §f" + value);

            } else if (type.equals("RANDOM_NUMBER")) {
                if (args.length < 3) {
                    player.sendMessage(plugin.getLanguageManager()
                            .getWithPrefix("gate-cmd.provide-range"));
                    plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
                    return true;
                }

                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                        "^\\s*(-?\\d+(?:\\.\\d+)?)-(-?\\d+(?:\\.\\d+)?)\\s*$"
                ).matcher(args[2]);

                if (!matcher.matches()) {
                    player.sendMessage(plugin.getLanguageManager()
                            .getWithPrefix("gate-cmd.wrong-format"));
                    plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
                    return true;
                }

                try {
                    double min = Double.parseDouble(matcher.group(1));
                    double max = Double.parseDouble(matcher.group(2));

                    if (!Double.isFinite(min) || !Double.isFinite(max)) {
                        throw new NumberFormatException();
                    }

                    if (min > max) {
                        player.sendMessage(plugin.getLanguageManager()
                                .getWithPrefix("gate-cmd.min-greater-than-max"));
                        plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
                        return true;
                    }

                    lore.add("§7min: §f" + min);
                    lore.add("§7max: §f" + max);

                } catch (NumberFormatException e) {
                    player.sendMessage(plugin.getLanguageManager()
                            .getWithPrefix("gate-cmd.not-a-number"));
                    plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
                    return true;
                }

            } else if (type.equals("MATH")) {
                if (args.length < 3) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("gate-cmd.provide-mode"));
                    plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
                    return true;
                }

                String modeName;
                switch (args[2].toUpperCase(Locale.ROOT)) {
                    case "SUB", "-" -> modeName = "SUB";
                    case "MUL", "*", "X" -> modeName = "MUL";
                    case "DIV", "/" -> modeName = "DIV";
                    case "POW", "^" -> modeName = "POW";
                    case "MOD", "%" -> modeName = "MOD";
                    case "MIN" -> modeName = "MIN";
                    case "MAX" -> modeName = "MAX";
                    case "SQRT" -> modeName = "SQRT";
                    case "ROOT", "NTH_ROOT" -> modeName = "ROOT";
                    case "AVG" -> modeName = "AVG";
                    case "ABS" -> modeName = "ABS";
                    case "HYPOT" -> modeName = "HYPOT";
                    default -> modeName = "ADD";
                }

                lore.add("§7Mode: §f" + modeName);

            } else if (type.equals("COMPARATOR")) {
                if (args.length < 3) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("gate-cmd.provide-sign"));
                    plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
                    return true;
                }

                String sign = args[2];

                if (!sign.matches(">|<|==|!=|>=|<=")) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("gate-cmd.invalid-sign"));
                    plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
                    return true;
                }

                lore.add("§7Mode: §f" + sign);

            } else if (type.equals("COUNTER")) {
                if (args.length < 3) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("gate-cmd.provide-limit"));
                    plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
                    return true;
                }

                if (!args[2].matches("-?\\d+")) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("gate-cmd.not-a-number"));
                    plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
                    return true;
                }

                int val = Integer.parseInt(args[2]);

                if (val < 1 || val > 100) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("gate-cmd.limit-range"));
                    plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
                    return true;
                }

                lore.add("§7Limit: §f" + args[2]);

            } else if (type.equals("SENSOR")) {
                if (args.length < 3) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("gate-cmd.provide-range"));
                    plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
                    return true;
                }

                if (!args[2].matches("-?\\d+")) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("gate-cmd.not-a-number"));
                    plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
                    return true;
                }

                int val = Integer.parseInt(args[2]);

                if (val < 1 || val > 15) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("gate-cmd.range-bounds"));
                    plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
                    return true;
                }

                lore.add("§7Range: §f" + args[2]);

            } else if (type.matches("CLOCK|REPEATER")) {
                boolean isClock = type.contains("CLOCK");

                if (args.length < 3) {
                    String msgKey = isClock ? "gate-cmd.provide-frequency" : "gate-cmd.provide-delay";
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix(msgKey));
                    plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
                    return true;
                }

                String input = args[2].toLowerCase();

                if (!input.endsWith("t") && !input.endsWith("s")) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("gate-cmd.provide-unit"));
                    plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
                    return true;
                }

                String numStr = input.substring(0, input.length() - 1);

                if (!numStr.matches("-?\\d+")) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("gate-cmd.invalid-time"));
                    plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
                    return true;
                }

                int val = Integer.parseInt(numStr);

                if (input.endsWith("t")) {
                    // Zegar i repeater mają ten sam limit od 1 ticka
                    if (val < 1 || val > 200) {
                        String msgKey = isClock ? "gate-cmd.clock-ticks-range" : "gate-cmd.repeater-ticks-range";
                        player.sendMessage(plugin.getLanguageManager().getWithPrefix(msgKey));
                        plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
                        return true;
                    }
                } else if (input.endsWith("s")) {
                    if (val < 1 || val > 10) {
                        String msgKey = isClock ? "gate-cmd.clock-seconds-range" : "gate-cmd.repeater-seconds-range";
                        player.sendMessage(plugin.getLanguageManager().getWithPrefix(msgKey));
                        plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
                        return true;
                    }
                }

                lore.add("§7Time: §f" + input);

            } else if (type.equals("STRING_SENDER") || type.equals("STRING_DECODER")) {
                if (args.length < 3) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("gate-cmd.provide-text"));
                    plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
                    return true;
                }

                // Łączymy wszystkie argumenty od args[2] wzwyż
                StringBuilder sb = new StringBuilder();
                for (int i = 2; i < args.length; i++) {
                    sb.append(args[i]).append(" ");
                }
                String textValue = sb.toString().trim();

                lore.add("§7Text: §f" + textValue);

            } else if (type.equals("STRING_COMPARATOR")) {
                if (args.length < 3) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("gate-cmd.provide-string-mode"));
                    plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
                    return true;
                }

                String mode = args[2].toUpperCase();

                if (!mode.matches("==|EQUALS|EQUALS_IGNORE_CASE|=I|CONTAINS|STARTS_WITH|ENDS_WITH|EMPTY")) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("gate-cmd.invalid-string-sign"));
                    plugin.getSoundManager().playSound(player, SoundManager.SoundType.FAIL);
                    return true;
                }

                lore.add("§7Mode: §f" + mode);
            }

            List<String> finalFormattedLore = new ArrayList<>();
            for (String line : lore) {
                String formattedLine = org.bukkit.ChatColor.translateAlternateColorCodes('&', line);
                if (!formattedLine.startsWith("§r")) {
                    formattedLine = "§r" + formattedLine;
                }
                finalFormattedLore.add(formattedLine);
            }

            meta.setLore(finalFormattedLore);
            item.setItemMeta(meta);
            player.getInventory().addItem(item);

            player.sendMessage(plugin.getLanguageManager().getWithPrefix("gate.received")
                    .replace("%type%", type));
            plugin.getSoundManager().playSound(player, SoundManager.SoundType.SUCCESS);

            return true;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> hints = new ArrayList<>();
        if (sender instanceof Player player) {
            if (command.getName().equalsIgnoreCase("bramka") || command.getName().equalsIgnoreCase("gate")) {
                if (!player.hasPermission("astrars.gates")) {
                    return hints; // Zwykły gracz bez uprawnień do bramek nie widzi podpowiedzi
                }

                if (args.length == 1) {
                    Arrays.asList("logic", "memory", "number", "boolean", "string", "data", "space", "time").forEach(c -> {
                        if (c.startsWith(args[0].toLowerCase()) && player.hasPermission("astrars.gates." + c.toLowerCase())) {
                            hints.add(c);
                        }
                    });
                } else if (args.length == 2) {
                    String category = args[0].toLowerCase();
                    if (!player.hasPermission("astrars.gates." + category)) {
                        return hints;
                    }

                    List<String> types = switch (category) {
                        case "logic" ->
                                Arrays.asList("NOT", "AND", "OR", "NOR", "NAND", "XOR", "XNOR", "NIMPLY", "IMPLY", "BUFFER", "MUX");
                        case "memory" -> Arrays.asList("LATCH", "TFF", "MEMORY_CELL");
                        case "number" ->
                                Arrays.asList("COUNTER", "RANDOM_NUMBER", "NUMBER_SENDER", "MATH", "DECIMAL_ACCUMULATOR", "COMPARATOR", "DECODER");
                        case "boolean" ->
                                Arrays.asList("RANDOM_BOOLEAN", "BOOLEAN_SENDER");
                        case "string" ->
                                Arrays.asList("STRING_SENDER", "STRING_COMPARATOR", "STRING_DECODER");
                        case "data" ->
                                Arrays.asList("CABLE_DATA", "DISPLAY", "TRANSISTOR", "DISK", "RAM", "BATTERY", "DATA_DETECTOR");
                        case "space" -> Arrays.asList("SENDER", "RECEIVER", "SENSOR");
                        case "time" -> Arrays.asList("CLOCK", "REPEATER", "PULSER");
                        default -> Collections.emptyList();
                    };
                    types.forEach(t -> {
                        if (t.startsWith(args[1].toUpperCase())) hints.add(t);
                    });
                } else if (args.length == 3) {
                    String category = args[0].toLowerCase();
                    if (!player.hasPermission("astrars.gates." + category)) {
                        return hints;
                    }

                    String type = args[1].toUpperCase();
                    if (type.matches("CLOCK|REPEATER")) hints.addAll(Arrays.asList("10t", "1s"));
                    else if (type.equals("MATH")) {
                        hints.addAll(Arrays.asList(
                                "ADD", "SUB", "-", "MUL", "*", "DIV", "/", "POW", "^",
                                "MOD", "%", "MIN", "MAX", "SQRT", "ROOT", "AVG", "ABS", "HYPOT"
                        ));
                    }
                    else if (type.equals("COMPARATOR")) hints.addAll(Arrays.asList(">", "<", "==", "!=", ">=", "<="));
                    else if (type.equals("STRING_COMPARATOR")) hints.addAll(Arrays.asList("EQUALS", "EQUALS_IGNORE_CASE", "CONTAINS", "STARTS_WITH", "ENDS_WITH", "EMPTY"));
                    else if (type.equals("SENSOR")) hints.add("5");
                    else if (type.equals("COUNTER")) hints.add("10");
                    else if (type.equals("NUMBER_SENDER")) hints.add("1");
                    else if (type.equals("RANDOM_NUMBER")) hints.addAll(Arrays.asList("0-5", "0-10"));
                }
            }
        }
        return hints;
    }
}