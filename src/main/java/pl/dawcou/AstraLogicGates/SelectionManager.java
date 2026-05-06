package pl.dawcou.AstraLogicGates;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Vector;

public class SelectionManager implements Listener {

    private final AstraLogicGates plugin; // Nazwa Twojej głównej klasy
    private final Map<UUID, Location[]> selections = new HashMap<>(); //
    private final Map<UUID, Map<org.bukkit.util.Vector, ConfigurationSection>> clipboard = new HashMap<>();

    public SelectionManager(AstraLogicGates plugin) {
        this.plugin = plugin;
    }

    // Metoda do dawania świecącego patyka
    public void giveSelector(Player player) {
        ItemStack stick = new ItemStack(Material.STICK);
        ItemMeta meta = stick.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§eSelektor Bramek");

            // Używamy Enchantment.LUCK (standardowa nazwa w nowszych wersjach)
            // Jeśli nadal sypie błędem, sprawdź podpowiedzi IDE, bo nazwy różnią się między wersjami silnika
            meta.addEnchant(Enchantment.LUCK, 1, true);

            // Ta flaga sprawia, że napis "Luck I" nie pojawia się w opisie przedmiotu
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            stick.setItemMeta(meta);
        }

        player.getInventory().addItem(stick);
        player.sendMessage(plugin.getLanguageManager().getWithPrefix("receive-selector"));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || !item.hasItemMeta() || !item.getItemMeta().getDisplayName().equals("§eSelektor Bramek")) return;
        if (event.getClickedBlock() == null) return;

        event.setCancelled(true);
        UUID uuid = player.getUniqueId();
        Location clickedLoc = event.getClickedBlock().getLocation();

        if (!selections.containsKey(uuid)) selections.put(uuid, new Location[2]);
        Location[] playerSelections = selections.get(uuid);

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            // Sprawdzamy, czy pozycja 1 jest inna niż kliknięta
            if (playerSelections[0] == null || !playerSelections[0].equals(clickedLoc)) {
                playerSelections[0] = clickedLoc;
                player.sendMessage(plugin.getLanguageManager().getWithPrefix("position-1-selected"));
            }
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // Sprawdzamy, czy pozycja 2 jest inna niż kliknięta
            if (playerSelections[1] == null || !playerSelections[1].equals(clickedLoc)) {
                playerSelections[1] = clickedLoc;
                player.sendMessage(plugin.getLanguageManager().getWithPrefix("position-2-selected"));
            }
        }
    }

    public void cutSelection(Player player) {
        UUID uuid = player.getUniqueId();
        if (!selections.containsKey(uuid) || selections.get(uuid)[0] == null || selections.get(uuid)[1] == null) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("both-positions-required"));
            return;
        }

        Location loc1 = selections.get(uuid)[0];
        Location loc2 = selections.get(uuid)[1];
        World world = loc1.getWorld();

        int minX = Math.min(loc1.getBlockX(), loc2.getBlockX());
        int maxX = Math.max(loc1.getBlockX(), loc2.getBlockX());
        int minY = Math.min(loc1.getBlockY(), loc2.getBlockY());
        int maxY = Math.max(loc1.getBlockY(), loc2.getBlockY());
        int minZ = Math.min(loc1.getBlockZ(), loc2.getBlockZ());
        int maxZ = Math.max(loc1.getBlockZ(), loc2.getBlockZ());

        // 1. USUWANIE WSZYSTKICH BLOKÓW W OBSZARZE
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR);
                }
            }
        }

        // 2. USUWANIE LOGIKI BRAMEK Z CONFIGU
        FileConfiguration config = plugin.getGatesConfig();
        ConfigurationSection gates = config.getConfigurationSection("gates");
        int removedGates = 0;

        if (gates != null) {
            for (String key : gates.getKeys(false)) {
                String[] parts = key.split(",");
                int gx = Integer.parseInt(parts[1]);
                int gy = Integer.parseInt(parts[2]);
                int gz = Integer.parseInt(parts[3]);

                if (gx >= minX && gx <= maxX && gy >= minY && gy <= maxY && gz >= minZ && gz <= maxZ) {
                    config.set("gates." + key, null);
                    removedGates++;
                }
            }
            plugin.saveGates();
        }

        String msg = plugin.getLanguageManager().getWithPrefix("cut-out-area");
        player.sendMessage(msg.replace("{COUNT}", String.valueOf(removedGates)));
    }

    // COPY
    public void copySelection(Player player) {
        UUID uuid = player.getUniqueId();
        if (selections.get(uuid) == null || selections.get(uuid)[0] == null || selections.get(uuid)[1] == null) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("both-positions-required"));
            return;
        }

        Location loc1 = selections.get(uuid)[0];
        Location loc2 = selections.get(uuid)[1];
        Location playerLoc = player.getLocation().getBlock().getLocation();

        int minX = Math.min(loc1.getBlockX(), loc2.getBlockX());
        int maxX = Math.max(loc1.getBlockX(), loc2.getBlockX());
        int minY = Math.min(loc1.getBlockY(), loc2.getBlockY());
        int maxY = Math.max(loc1.getBlockY(), loc2.getBlockY());
        int minZ = Math.min(loc1.getBlockZ(), loc2.getBlockZ());
        int maxZ = Math.max(loc1.getBlockZ(), loc2.getBlockZ());

        ConfigurationSection gates = plugin.getGatesConfig().getConfigurationSection("gates");
        Map<org.bukkit.util.Vector, ConfigurationSection> playerClipboard = new HashMap<>();

        if (gates != null) {
            for (String key : gates.getKeys(false)) {
                String[] parts = key.split(",");
                int gx = Integer.parseInt(parts[1]);
                int gy = Integer.parseInt(parts[2]);
                int gz = Integer.parseInt(parts[3]);

                if (gx >= minX && gx <= maxX && gy >= minY && gy <= maxY && gz >= minZ && gz <= maxZ) {
                    org.bukkit.util.Vector offset = new org.bukkit.util.Vector(gx, gy, gz).subtract(playerLoc.toVector());
                    playerClipboard.put(offset, gates.getConfigurationSection(key));
                }
            }
        }

        clipboard.put(uuid, playerClipboard);

        String msg = plugin.getLanguageManager().getWithPrefix("copy-success");
        player.sendMessage(msg.replace("{COUNT}", String.valueOf(playerClipboard.size())));
    }

    // PASTE
    public void pasteSelection(Player player) {
        UUID uuid = player.getUniqueId();
        if (!clipboard.containsKey(uuid) || clipboard.get(uuid).isEmpty()) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("clipboard-empty"));
            return;
        }

        Location playerLoc = player.getLocation().getBlock().getLocation();
        FileConfiguration config = plugin.getGatesConfig();

        int pastedCount = 0;
        for (Map.Entry<org.bukkit.util.Vector, ConfigurationSection> entry : clipboard.get(uuid).entrySet()) {
            Location newLoc = playerLoc.clone().add(entry.getKey());
            ConfigurationSection gateData = entry.getValue();

            // 1. POBIERAMY MATERIAŁ Z DANYCH BRAMKI (jeśli go tam zapisujesz)
            // Jeśli nie masz zapisanego materiału, użyjemy domyślnego bloku Twoich bramek
            String materialName = gateData.getString("block_type", "RED_CONCRETE"); // Domyślnie np. Smooth Stone
            Material gateMaterial = Material.matchMaterial(materialName);
            if (gateMaterial == null) gateMaterial = Material.IRON_BLOCK; // Failsafe

            // 2. STAWIAMY WŁAŚCIWY BLOK
            newLoc.getBlock().setType(gateMaterial);

            // 3. ZAPISUJEMY LOGIKĘ DO PLIKU (Przesunięte kordy)
            config.set("gates." + GateUtils.locToStr(newLoc), gateData);

            pastedCount++;
        }

        plugin.saveGates();

        String msg = plugin.getLanguageManager().getWithPrefix("paste-success");
        player.sendMessage(msg.replace("{COUNT}", String.valueOf(pastedCount)));
    }
}