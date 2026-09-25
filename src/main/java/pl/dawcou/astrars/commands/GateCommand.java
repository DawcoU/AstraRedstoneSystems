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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
                return true;
            }
            if (args.length < 2) {
                player.sendMessage(plugin.getLanguageManager().getWithPrefix("gate-cmd.usage"));
                return true;
            }

            String category = args[0].toLowerCase();
            String type = args[1].toUpperCase();

            // --- DYNAMICZNE SPRAWDZENIE PERMISJI ---
            String permission = "astrars.gates." + category;
            if (!player.hasPermission(permission)) {
                player.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
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
                    case "RANDOM_BOOLEAN", "RANDOM_NUMBER" -> Material.CYAN_CONCRETE;
                    case "NUMBER_GATE", "BOOLEAN_GATE" -> Material.BROWN_CONCRETE;
                    default -> null;
                };
                case "string" -> switch (type) {
                    case "STRING_COMPARATOR", "STRING_DECODER" -> Material.GRAY_CONCRETE;
                    case "STRING_GATE" -> Material.BROWN_CONCRETE;
                    default -> null;
                };
                case "data" -> switch (type) {
                    case "CABLE_DATA" -> Material.BLACK_CONCRETE;
                    case "DISPLAY" -> Material.WHITE_CONCRETE;
                    case "TRANSISTOR" -> Material.RED_CONCRETE;
                    case "DISK_GATE" -> Material.LIGHT_BLUE_CONCRETE;
                    case "RAM_GATE" -> Material.GREEN_CONCRETE;
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
                    case "CLOCK_GATE" -> Material.LIME_CONCRETE;
                    case "REPEATER", "PULSER" -> Material.YELLOW_CONCRETE;
                    default -> null;
                };
                default -> null;
            };

            if (mat == null) {
                player.sendMessage(plugin.getLanguageManager().getWithPrefix("gate.unknown-type"));
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
                    return true;
                }
                lore.add("§7Channel: §f" + args[2].replace(" ", ""));

            } else if (type.equals("NUMBER_GATE") || type.equals("DECODER")) {
                if (args.length < 3) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("gate-cmd.provide-value"));
                    return true;
                }

                if (!args[2].matches("-?\\d+")) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("gate-cmd.not-a-number"));
                    return true;
                }

                lore.add("§7Value: §f" + args[2]);

            } else if (type.equals("RANDOM_NUMBER")) {
                if (args.length < 3) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("gate-cmd.provide-range"));
                    return true;
                }

                String val = args[2];

                if (val.contains("-")) {
                    String[] parts = val.split("-");

                    if (parts.length == 2) {
                        try {
                            int min = Integer.parseInt(parts[0]);
                            int max = Integer.parseInt(parts[1]);

                            // BLOKADA UJEMNYCH I BŁĘDNYCH ZAKRESÓW
                            if (min < 0 || max < 0) {
                                player.sendMessage(plugin.getLanguageManager().getWithPrefix("gate-cmd.negative-number"));
                                return true;
                            }

                            if (min > max) {
                                player.sendMessage(plugin.getLanguageManager().getWithPrefix("gate-cmd.min-greater-than-max"));
                                return true;
                            }

                            lore.add("§7min: §f" + min);
                            lore.add("§7max: §f" + max);

                        } catch (NumberFormatException e) {
                            player.sendMessage(plugin.getLanguageManager().getWithPrefix("gate-cmd.not-a-number"));
                            return true;
                        }
                    } else {
                        player.sendMessage(plugin.getLanguageManager().getWithPrefix("gate-cmd.wrong-format"));
                        return true;
                    }
                } else {
                    return true;
                }

            } else if (type.equals("MATH")) {
                if (args.length < 3) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("gate-cmd.provide-mode"));
                    return true;
                }

                String modeName;
                switch (args[2]) {
                    case "+":
                    case "add":
                        modeName = "Add";
                        break;
                    case "-":
                    case "sub":
                        modeName = "Subtract";
                        break;
                    case "*":
                    case "x":
                    case "mul":
                        modeName = "Multiply";
                        break;
                    case "/":
                    case "div":
                        modeName = "Divide";
                        break;
                    case "^":
                    case "pow":
                        modeName = "Power";
                        break;
                    default:
                        modeName = "Add";
                        break;
                }

                lore.add("§7Mode: §f" + modeName);

            } else if (type.equals("COMPARATOR")) {
                if (args.length < 3) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("gate-cmd.provide-sign"));
                    return true;
                }

                String sign = args[2];

                if (!sign.matches(">|<|==|!=|>=|<=")) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("gate-cmd.invalid-sign"));
                    return true;
                }

                lore.add("§7Mode: §f" + sign);

            } else if (type.equals("COUNTER")) {
                if (args.length < 3) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("gate-cmd.provide-limit"));
                    return true;
                }

                if (!args[2].matches("-?\\d+")) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("gate-cmd.not-a-number"));
                    return true;
                }

                int val = Integer.parseInt(args[2]);

                if (val < 1 || val > 100) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("gate-cmd.limit-range"));
                    return true;
                }

                lore.add("§7Limit: §f" + args[2]);

            } else if (type.equals("SENSOR")) {
                if (args.length < 3) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("gate-cmd.provide-range"));
                    return true;
                }

                if (!args[2].matches("-?\\d+")) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("gate-cmd.not-a-number"));
                    return true;
                }

                int val = Integer.parseInt(args[2]);

                if (val < 1 || val > 15) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("gate-cmd.range-bounds"));
                    return true;
                }

                lore.add("§7Range: §f" + args[2]);

            } else if (type.matches("CLOCK|CLOCK_GATE|REPEATER")) {
                boolean isClock = type.contains("CLOCK");

                if (args.length < 3) {
                    String msgKey = isClock ? "gate-cmd.provide-frequency" : "gate-cmd.provide-delay";
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix(msgKey));
                    return true;
                }

                String input = args[2].toLowerCase();

                if (!input.endsWith("t") && !input.endsWith("s")) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("gate-cmd.provide-unit"));
                    return true;
                }

                String numStr = input.substring(0, input.length() - 1);

                if (!numStr.matches("-?\\d+")) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("gate-cmd.invalid-time"));
                    return true;
                }

                int val = Integer.parseInt(numStr);

                if (input.endsWith("t")) {
                    // Zegar i repeater mają ten sam limit od 1 ticka
                    if (val < 1 || val > 200) {
                        String msgKey = isClock ? "gate-cmd.clock-ticks-range" : "gate-cmd.repeater-ticks-range";
                        player.sendMessage(plugin.getLanguageManager().getWithPrefix(msgKey));
                        return true;
                    }
                } else if (input.endsWith("s")) {
                    if (val < 1 || val > 10) {
                        String msgKey = isClock ? "gate-cmd.clock-seconds-range" : "gate-cmd.repeater-seconds-range";
                        player.sendMessage(plugin.getLanguageManager().getWithPrefix(msgKey));
                        return true;
                    }
                }

                lore.add("§7Time: §f" + input);

            } else if (type.equals("STRING_GATE") || type.equals("STRING_DECODER")) {
                if (args.length < 3) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("gate-cmd.provide-text"));
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
                    return true;
                }

                String mode = args[2].toUpperCase();

                if (!mode.matches("==|EQUALS|EQUALS_IGNORE_CASE|=I|CONTAINS|STARTS_WITH|ENDS_WITH|EMPTY")) {
                    player.sendMessage(plugin.getLanguageManager().getWithPrefix("gate-cmd.invalid-string-sign"));
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

            return true;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> hints = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("bramka")) {
            if (args.length == 1) {
                Arrays.asList("logic", "memory", "number", "string", "data", "space", "time").forEach(c -> {
                    if (c.startsWith(args[0].toLowerCase())) hints.add(c);
                });
            } else if (args.length == 2) {
                List<String> types = switch (args[0].toLowerCase()) {
                    case "logic" ->
                            Arrays.asList("NOT", "AND", "OR", "NOR", "NAND", "XOR", "XNOR", "NIMPLY", "IMPLY", "BUFFER", "MUX");
                    case "memory" -> Arrays.asList("LATCH", "TFF", "MEMORY_CELL");
                    case "number" ->
                            Arrays.asList("COUNTER", "RANDOM_BOOLEAN", "RANDOM_NUMBER", "NUMBER_GATE", "BOOLEAN_GATE", "MATH", "DECIMAL_ACCUMULATOR", "COMPARATOR", "DECODER");
                    case "string" ->
                            Arrays.asList("STRING_GATE", "STRING_COMPARATOR", "STRING_DECODER");
                    case "data" ->
                            Arrays.asList("CABLE_DATA", "DISPLAY", "TRANSISTOR", "DISK_GATE", "RAM_GATE", "BATTERY", "DATA_DETECTOR");
                    case "space" -> Arrays.asList("SENDER", "RECEIVER", "SENSOR");
                    case "time" -> Arrays.asList("CLOCK", "CLOCK_GATE", "REPEATER", "PULSER");
                    default -> Collections.emptyList();
                };
                types.forEach(t -> {
                    if (t.startsWith(args[1].toUpperCase())) hints.add(t);
                });
            } else if (args.length == 3) {
                String type = args[1].toUpperCase();
                if (type.matches("CLOCK|CLOCK_GATE|REPEATER")) hints.addAll(Arrays.asList("10t", "1s"));
                else if (type.equals("MATH")) hints.addAll(Arrays.asList("+", "-", "x", "/", "^"));
                else if (type.equals("COMPARATOR")) hints.addAll(Arrays.asList(">", "<", "==", "!=", ">=", "<="));
                else if (type.equals("STRING_COMPARATOR")) hints.addAll(Arrays.asList("EQUALS", "EQUALS_IGNORE_CASE", "CONTAINS", "STARTS_WITH", "ENDS_WITH", "EMPTY"));
                else if (type.equals("SENSOR")) hints.add("5");
                else if (type.equals("COUNTER")) hints.add("10");
                else if (type.equals("NUMBER_GATE")) hints.add("1");
                else if (type.equals("RANDOM_NUMBER")) hints.addAll(Arrays.asList("0-5", "0-10"));
            }
        }
        return hints;
    }
}