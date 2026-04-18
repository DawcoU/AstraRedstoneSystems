package pl.dawcou.AstraLogicGates;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class LogicGates extends JavaPlugin implements CommandExecutor, TabCompleter {

    private File gatesFile;
    private FileConfiguration gatesConfig;
    private GateManager gateManager;

    public static final String PREFIX = "§e[§bAstraLogicGates§e]";

    @Override
    public void onEnable() {
        createGatesConfig();
        this.gateManager = new GateManager(this);

        getServer().getPluginManager().registerEvents(new GateListener(this, gateManager), this);
        getCommand("bramka").setExecutor(this);
        getCommand("bramka").setTabCompleter(this);

        // Pobieranie wersji automatycznie z plugin.yml
        String v = getDescription().getVersion();

        String statusLabel = "§eStatus: ";
        String status = "§aWłączony";
        String authorLabel = "§eAutor: §6";

        Bukkit.getConsoleSender().sendMessage("");
        Bukkit.getConsoleSender().sendMessage("§7------------ " + PREFIX + " §7------------");
        Bukkit.getConsoleSender().sendMessage("§eVersion: §f" + v);
        Bukkit.getConsoleSender().sendMessage(statusLabel + status);
        Bukkit.getConsoleSender().sendMessage(authorLabel + "DawcoU");
        Bukkit.getConsoleSender().sendMessage("§7----------------------------------------------");
        Bukkit.getConsoleSender().sendMessage("");

        Bukkit.getScheduler().runTaskTimer(this, gateManager::checkGates, 1L, 1L);
    }

    @Override
    public void onDisable() {
        Bukkit.getConsoleSender().sendMessage("");
        Bukkit.getConsoleSender().sendMessage("§7------------ " + PREFIX + " §7------------");
        Bukkit.getConsoleSender().sendMessage("§6   Status: §cWyłączony §7- §eDo zobaczenia! :D");
        Bukkit.getConsoleSender().sendMessage("§7----------------------------------------------");
        Bukkit.getConsoleSender().sendMessage("");
    }

    private void createGatesConfig() {
        gatesFile = new File(getDataFolder(), "gates.yml");
        if (!gatesFile.exists()) {
            gatesFile.getParentFile().mkdirs();
            try { gatesFile.createNewFile(); } catch (IOException ignored) {}
        }
        gatesConfig = YamlConfiguration.loadConfiguration(gatesFile);
    }

    public FileConfiguration getGatesConfig() { return gatesConfig; }

    public void saveGates() {
        try { gatesConfig.save(gatesFile); } catch (IOException ignored) {}
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (!player.hasPermission("AstraLogicGates.bramki")) {
            player.sendMessage(PREFIX + " §cNie masz uprawnień!");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(PREFIX + " §cUżycie: §6/bramka <TYP> [parametr]");
            return true;
        }

        String type = args[0].toUpperCase();
        Material mat;

        // Przypisanie materiału do typu
        switch (type) {
            case "NOT": mat = Material.RED_CONCRETE; break;
            case "AND": mat = Material.ORANGE_CONCRETE; break;
            case "OR": mat = Material.YELLOW_CONCRETE; break;
            case "NOR": mat = Material.GRAY_CONCRETE; break;
            case "NAND": mat = Material.PINK_CONCRETE; break;
            case "XOR": mat = Material.PURPLE_CONCRETE; break;
            case "XNOR": mat = Material.MAGENTA_CONCRETE; break;
            case "LATCH": mat = Material.WHITE_CONCRETE; break;
            case "TFF": mat = Material.LIGHT_BLUE_CONCRETE; break;
            case "DLATCH": mat = Material.BLUE_CONCRETE; break;
            case "CLOCK": mat = Material.GREEN_CONCRETE; break;
            case "CLOCK_GATE": mat = Material.LIME_CONCRETE; break;
            case "NIMPLY": mat = Material.BROWN_CONCRETE; break;
            case "RANDOM": mat = Material.CYAN_CONCRETE; break;
            case "REPEATER": mat = Material.YELLOW_CONCRETE; break;
            case "BOOSTER": mat = Material.ORANGE_CONCRETE; break;
            case "COUNTER": mat = Material.IRON_BLOCK; break;
            case "SENDER": mat = Material.GOLD_BLOCK; break;
            case "RECEIVER": mat = Material.EMERALD_BLOCK; break;
            case "SENSOR": mat = Material.LAPIS_BLOCK; break;
            case "SYNCHRONIZER": mat = Material.RED_CONCRETE; break;
            default:
                player.sendMessage(PREFIX + " §cNieznany typ bramki!");
                return true;
        }

        // Tworzenie przedmiotu
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§eBramka: §6" + type);
        List<String> lore = new ArrayList<>();

        // Obsługa parametrów (Kanały, Limity, Czas, Zasięg)
        if (type.equals("SENDER") || type.equals("RECEIVER")) {
            if (args.length < 2) {
                player.sendMessage(PREFIX + " §cMusisz podać kanał! §6/bramka " + type + " <kanał1,kanał2...>");
                return true;
            }

            // Wywalamy spacji, żeby "dom , ogrod" zamieniło się w "dom,ogrod" (mniej błędów w configu)
            String channels = args[1].replace(" ", "");

            // Dodajemy do opisu (Lore)
            lore.add("§7Kanały: §f" + channels);

        } else if (type.equals("COUNTER")) {
            if (args.length < 2) {
                player.sendMessage(PREFIX + " §cPodaj limit (1-100)! §6/bramka COUNTER <limit>");
                return true;
            }
            // Sprawdzamy czy to liczba
            if (!args[1].matches("-?\\d+")) {
                player.sendMessage(PREFIX + " §cTo nie jest liczba!");
                return true;
            }
            int val = Integer.parseInt(args[1]);
            if (val < 1 || val > 100) {
                player.sendMessage(PREFIX + " §cLimit musi być w zakresie 1-100!");
                return true;
            }
            lore.add("§7Limit: §f" + val);

        } else if (type.equals("SENSOR")) {
            if (args.length < 2) {
                player.sendMessage(PREFIX + " §cPodaj zasięg (1-15)! §6/bramka SENSOR <zasięg>");
                return true;
            }
            if (!args[1].matches("-?\\d+")) {
                player.sendMessage(PREFIX + " §cTo nie jest liczba!");
                return true;
            }
            int val = Integer.parseInt(args[1]);
            if (val < 1 || val > 15) {
                player.sendMessage(PREFIX + " §cZasięg musi być w zakresie 1-15!");
                return true;
            }
            lore.add("§7Zasięg: §f" + val);

        } else if (type.matches("CLOCK|CLOCK_GATE|REPEATER")) {
            if (args.length < 2) {
                player.sendMessage(PREFIX + " §cPodaj czas (5t-10s)! §6/bramka " + type + " <czas>");
                return true;
            }
            String input = args[1].toLowerCase();

            // Sprawdzenie jednostki
            if (!input.endsWith("t") && !input.endsWith("s")) {
                player.sendMessage(PREFIX + " §cPodaj jednostkę: §e't' §7(ticki) lub §e's' §7(sekundy)!");
                return true;
            }

            // Wyciągamy samą liczbę przed literką
            String numStr = input.substring(0, input.length() - 1);
            if (!numStr.matches("-?\\d+")) {
                player.sendMessage(PREFIX + " §cPodaj poprawny czas (np. 10s)!");
                return true;
            }

            int val = Integer.parseInt(numStr);

            // Walidacja zakresu (5t do 10s czyli 200t)
            if (input.endsWith("t")) {
                if (val < 5 || val > 200) {
                    player.sendMessage(PREFIX + " §cCzas w tickach musi być od 5t do 200t (10s)!");
                    return true;
                }
            } else if (input.endsWith("s")) {
                if (val < 1 || val > 10) {
                    player.sendMessage(PREFIX + " §cCzas w sekundach musi być od 1s do 10s!");
                    return true;
                }
            }

            lore.add("§7Czas: §f" + input);
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        player.getInventory().addItem(item);
        player.sendMessage(PREFIX + " §aOtrzymałeś blok bramki: §e" + type);

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("NOT", "AND", "OR", "NOR", "NAND", "XOR", "XNOR", "LATCH", "TFF", "DLATCH", "CLOCK", "CLOCK_GATE", "NIMPLY", "RANDOM", "REPEATER", "BOOSTER", "COUNTER", "SENDER", "RECEIVER", "SENSOR", "SYNCHRONIZER")
                    .stream().filter(t -> t.startsWith(args[0].toUpperCase())).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}