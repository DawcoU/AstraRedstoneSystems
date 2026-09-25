package pl.dawcou.astrars.gates.gui;

import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.dawcou.astrars.AstraRS;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// ----------------------------------------------------------------------------------------------------
// Klasa odpowiedzialna za wyświetlanie i obsługę GUI edycji bramek logicznych
// ----------------------------------------------------------------------------------------------------
public class GateGUI implements Listener {

    private final AstraRS plugin;
    private final Map<UUID, GateSession> activeSessions = new HashMap<>();

    public GateGUI(AstraRS plugin) {
        this.plugin = plugin;
    }

    // Dedykowany holder dla naszego GUI
    public static class GateHolder implements InventoryHolder {
        private GateSession session;

        public void setSession(GateSession session) {
            this.session = session;
        }

        public GateSession getSession() {
            return session;
        }

        @Override
        public Inventory getInventory() {
            return session != null ? session.inventory : null;
        }
    }

    private static class GateSession {
        final String locKey;
        final String type;
        final JsonObject gateObj;
        final Inventory inventory;

        int value;
        int interval;
        int scoreLimit;
        int radius;
        int min;
        int max;

        int chargeSeconds;
        int decaySeconds;

        int inputs;

        String mode;

        GateSession(String locKey, String type, JsonObject gateObj, Inventory inventory) {
            this.locKey = locKey;
            this.type = type;
            this.gateObj = gateObj;
            this.inventory = inventory;

            this.value = gateObj.has("value") ? gateObj.get("value").getAsInt() : 0;
            this.interval = gateObj.has("interval") ? gateObj.get("interval").getAsInt() : 20;
            this.scoreLimit = gateObj.has("score_limit") ? gateObj.get("score_limit").getAsInt() : 0;
            this.radius = gateObj.has("radius") ? gateObj.get("radius").getAsInt() : 1;
            this.min = gateObj.has("min") ? gateObj.get("min").getAsInt() : 0;
            this.max = gateObj.has("max") ? gateObj.get("max").getAsInt() : 100;

            this.chargeSeconds = Math.min(30, Math.max(10, gateObj.has("charge_seconds") ? gateObj.get("charge_seconds").getAsInt() : 10));
            this.decaySeconds = Math.min(150, Math.max(60, gateObj.has("decay_seconds") ? gateObj.get("decay_seconds").getAsInt() : 120));

            this.inputs = gateObj.has("inputs") ? gateObj.get("inputs").getAsInt() : 2;

            this.mode = gateObj.has("mode") ? gateObj.get("mode").getAsString() : "ADD";
        }
    }

    public void openGUI(Player player, String locKey, JsonObject gateObj, String type) {
        GateHolder holder = new GateHolder();
        String title = plugin.getLanguageManager().getMessage("gui.title").replace("%type%", type);
        Inventory inv = Bukkit.createInventory(holder, 27, ChatColor.translateAlternateColorCodes('&', title));

        GateSession session = new GateSession(locKey, type, gateObj, inv);
        holder.setSession(session);
        activeSessions.put(player.getUniqueId(), session);

        refreshGUI(session);
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.0f);
    }

    private void refreshGUI(GateSession session) {
        session.inventory.clear();

        ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < session.inventory.getSize(); i++) {
            session.inventory.setItem(i, filler);
        }

        switch (session.type) {
            case "CLOCK", "CLOCK_GATE", "REPEATER" -> buildTimeGUI(session);
            case "BATTERY" -> buildBatteryGUI(session);
            case "NUMBER_GATE", "DECODER" -> buildValueGUI(session);
            case "COUNTER" -> buildLimitGUI(session);
            case "SENSOR" -> buildRadiusGUI(session);
            case "RANDOM_NUMBER" -> buildMinMaxGUI(session);
            case "MATH" -> buildMathGUI(session);
            case "COMPARATOR" -> buildComparatorGUI(session);

            case "OR", "NOR", "AND", "NAND", "XOR", "XNOR" -> buildBasicGateGUI(session);
        }
    }

    private void buildBasicGateGUI(GateSession session) {
        Material mat = (session.inputs == 3) ? Material.REPEATER : Material.REDSTONE_TORCH;
        String title = plugin.getLanguageManager().getMessage("gui.status-inputs")
                .replace("%val%", String.valueOf(session.inputs));
        session.inventory.setItem(13, createItem(mat, title));
    }

    private void buildTimeGUI(GateSession session) {
        session.inventory.setItem(4, createItem(Material.CLOCK, plugin.getLanguageManager().getMessage("gui.status-time").replace("%val%", String.valueOf(session.interval))));
        session.inventory.setItem(10, createItem(Material.RED_WOOL, "&c-10 Ticks (-0.5s)"));
        session.inventory.setItem(11, createItem(Material.ORANGE_WOOL, "&6-1 Tick (-0.05s)"));
        session.inventory.setItem(15, createItem(Material.LIME_WOOL, "&a+1 Tick (+0.05s)"));
        session.inventory.setItem(16, createItem(Material.GREEN_WOOL, "&2+10 Ticks (+0.5s)"));
    }

    private void buildValueGUI(GateSession session) {
        session.inventory.setItem(4, createItem(Material.PAPER, plugin.getLanguageManager().getMessage("gui.status-value").replace("%val%", String.valueOf(session.value))));
        session.inventory.setItem(10, createItem(Material.RED_WOOL, "&c-10"));
        session.inventory.setItem(11, createItem(Material.ORANGE_WOOL, "&6-1"));
        session.inventory.setItem(15, createItem(Material.LIME_WOOL, "&a+1"));
        session.inventory.setItem(16, createItem(Material.GREEN_WOOL, "&2+10"));
    }

    private void buildLimitGUI(GateSession session) {
        session.inventory.setItem(4, createItem(Material.DISPENSER, plugin.getLanguageManager().getMessage("gui.status-limit").replace("%val%", String.valueOf(session.scoreLimit))));
        session.inventory.setItem(10, createItem(Material.RED_WOOL, "&c-10"));
        session.inventory.setItem(11, createItem(Material.ORANGE_WOOL, "&6-1"));
        session.inventory.setItem(15, createItem(Material.LIME_WOOL, "&a+1"));
        session.inventory.setItem(16, createItem(Material.GREEN_WOOL, "&2+10"));
    }

    private void buildRadiusGUI(GateSession session) {
        session.inventory.setItem(4, createItem(Material.COMPASS, plugin.getLanguageManager().getMessage("gui.status-range").replace("%val%", String.valueOf(session.radius))));
        session.inventory.setItem(11, createItem(Material.ORANGE_WOOL, "&6-1"));
        session.inventory.setItem(15, createItem(Material.LIME_WOOL, "&a+1"));
    }

    private void buildMinMaxGUI(GateSession session) {
        session.inventory.setItem(4, createItem(Material.STRUCTURE_VOID, plugin.getLanguageManager().getMessage("gui.status-random")
                .replace("%min%", String.valueOf(session.min))
                .replace("%max%", String.valueOf(session.max))));

        session.inventory.setItem(10, createItem(Material.RED_WOOL, "&cMIN -1"));
        session.inventory.setItem(11, createItem(Material.LIME_WOOL, "&aMIN +1"));
        session.inventory.setItem(15, createItem(Material.ORANGE_WOOL, "&6MAX -1"));
        session.inventory.setItem(16, createItem(Material.GREEN_WOOL, "&2MAX +1"));
    }

    private void buildBatteryGUI(GateSession session) {
        // Górny rząd: Informacje i opcje ŁADOWANIA (10s - 30s)
        session.inventory.setItem(2, createItem(Material.REDSTONE_TORCH, "&aŁadowanie: &f" + session.chargeSeconds + "s / 1%"));
        session.inventory.setItem(10, createItem(Material.RED_WOOL, "&cŁadowanie -5s"));
        session.inventory.setItem(11, createItem(Material.ORANGE_WOOL, "&6Ładowanie -1s"));
        session.inventory.setItem(12, createItem(Material.LIME_WOOL, "&aŁadowanie +1s"));
        session.inventory.setItem(13, createItem(Material.GREEN_WOOL, "&2Ładowanie +5s"));

        // Dolny rząd: Informacje i opcje ROZŁADOWYWANIA (60s - 150s)
        session.inventory.setItem(6, createItem(Material.LEVER, "&cRozładowanie: &f" + session.decaySeconds + "s / 1%"));
        session.inventory.setItem(14, createItem(Material.RED_WOOL, "&cRozładowanie -10s"));
        session.inventory.setItem(15, createItem(Material.ORANGE_WOOL, "&6Rozładowanie -1s"));
        session.inventory.setItem(16, createItem(Material.LIME_WOOL, "&aRozładowanie +1s"));
        session.inventory.setItem(17, createItem(Material.GREEN_WOOL, "&2Rozładowanie +10s"));
    }

    private void buildMathGUI(GateSession session) {
        session.inventory.setItem(4, createItem(Material.BOOK, plugin.getLanguageManager().getMessage("gui.status-mode").replace("%mode%", session.mode)));
        session.inventory.setItem(11, createItem(Material.PAPER, "&eADD (+)"));
        session.inventory.setItem(12, createItem(Material.PAPER, "&eSUB (-)"));
        session.inventory.setItem(13, createItem(Material.PAPER, "&eMUL (*)"));
        session.inventory.setItem(14, createItem(Material.PAPER, "&eDIV (/)"));
        session.inventory.setItem(15, createItem(Material.PAPER, "&ePOW (^)"));
    }

    private void buildComparatorGUI(GateSession session) {
        session.inventory.setItem(4, createItem(Material.COMPARATOR, plugin.getLanguageManager().getMessage("gui.status-mode").replace("%mode%", session.mode)));
        session.inventory.setItem(10, createItem(Material.PAPER, "&e=="));
        session.inventory.setItem(11, createItem(Material.PAPER, "&e!="));
        session.inventory.setItem(12, createItem(Material.PAPER, "&e>"));
        session.inventory.setItem(13, createItem(Material.PAPER, "&e<"));
        session.inventory.setItem(14, createItem(Material.PAPER, "&e>="));
        session.inventory.setItem(15, createItem(Material.PAPER, "&e<="));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        // 1. Sprawdzamy holdera i natychmiast anulujemy interakcję
        if (!(e.getInventory().getHolder() instanceof GateHolder holder)) {
            return;
        }
        e.setCancelled(true);

        if (!(e.getWhoClicked() instanceof Player player)) return;

        // 2. Ignorujemy kliknięcia poza okno lub w dolny ekwipunek gracza
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(e.getInventory())) return;

        // 3. Pobieramy sesję z holdera
        GateSession session = holder.getSession();
        if (session == null) return;

        int slot = e.getSlot();
        boolean updated = false;

        // 4. Obsługa kliknięć w sloty
        switch (session.type) {
            case "OR", "NOR", "AND", "NAND", "XOR", "XNOR" -> {
                if (slot == 13) {
                    session.inputs = (session.inputs == 2) ? 3 : 2;
                    updated = true;
                }
            }

            case "CLOCK", "CLOCK_GATE", "REPEATER" -> {
                if (slot == 10) { session.interval = Math.max(1, session.interval - 10); updated = true; }
                else if (slot == 11) { session.interval = Math.max(1, session.interval - 1); updated = true; }
                else if (slot == 15) { session.interval += 1; updated = true; }
                else if (slot == 16) { session.interval += 10; updated = true; }
            }
            case "NUMBER_GATE", "DECODER" -> {
                if (slot == 10) { session.value -= 10; updated = true; }
                else if (slot == 11) { session.value -= 1; updated = true; }
                else if (slot == 15) { session.value += 1; updated = true; }
                else if (slot == 16) { session.value += 10; updated = true; }
            }
            case "COUNTER" -> {
                if (slot == 10) { session.scoreLimit = Math.max(0, session.scoreLimit - 10); updated = true; }
                else if (slot == 11) { session.scoreLimit = Math.max(0, session.scoreLimit - 1); updated = true; }
                else if (slot == 15) { session.scoreLimit += 1; updated = true; }
                else if (slot == 16) { session.scoreLimit += 10; updated = true; }
            }
            case "SENSOR" -> {
                if (slot == 11) { session.radius = Math.max(1, session.radius - 1); updated = true; }
                else if (slot == 15) { session.radius += 1; updated = true; }
            }
            case "RANDOM_NUMBER" -> {
                if (slot == 10) { session.min -= 1; updated = true; }
                else if (slot == 11) { session.min += 1; updated = true; }
                else if (slot == 15) { session.max -= 1; updated = true; }
                else if (slot == 16) { session.max += 1; updated = true; }
            }
            case "MATH" -> {
                if (slot == 11) { session.mode = "ADD"; updated = true; }
                else if (slot == 12) { session.mode = "SUB"; updated = true; }
                else if (slot == 13) { session.mode = "MUL"; updated = true; }
                else if (slot == 14) { session.mode = "DIV"; updated = true; }
                else if (slot == 15) { session.mode = "POW"; updated = true; }
            }
            case "COMPARATOR" -> {
                if (slot == 10) { session.mode = "EQUALS"; updated = true; }
                else if (slot == 11) { session.mode = "NOT_EQUALS"; updated = true; }
                else if (slot == 12) { session.mode = "GREATER"; updated = true; }
                else if (slot == 13) { session.mode = "LESS"; updated = true; }
                else if (slot == 14) { session.mode = "GREATER_OR_EQUAL"; updated = true; }
                else if (slot == 15) { session.mode = "LESS_OR_EQUAL"; updated = true; }
            }
            case "BATTERY" -> {
                // Ładowanie (sloty 10-13, limit: 10s - 30s)
                if (slot == 10) { session.chargeSeconds = Math.max(10, session.chargeSeconds - 5); updated = true; }
                else if (slot == 11) { session.chargeSeconds = Math.max(10, session.chargeSeconds - 1); updated = true; }
                else if (slot == 12) { session.chargeSeconds = Math.min(30, session.chargeSeconds + 1); updated = true; }
                else if (slot == 13) { session.chargeSeconds = Math.min(30, session.chargeSeconds + 5); updated = true; }

                // Rozładowywanie (sloty 14-17, limit: 60s - 150s)
                else if (slot == 14) { session.decaySeconds = Math.max(60, session.decaySeconds - 10); updated = true; }
                else if (slot == 15) { session.decaySeconds = Math.max(60, session.decaySeconds - 1); updated = true; }
                else if (slot == 16) { session.decaySeconds = Math.min(150, session.decaySeconds + 1); updated = true; }
                else if (slot == 17) { session.decaySeconds = Math.min(150, session.decaySeconds + 10); updated = true; }
            }
        }

        // 5. Jeśli wartość się zmieniła, zapisujemy do JSON i odświeżamy
        if (updated) {
            switch (session.type) {
                case "OR", "NOR", "AND", "NAND", "XOR", "XNOR" -> session.gateObj.addProperty("inputs", session.inputs);

                case "CLOCK", "CLOCK_GATE", "REPEATER" -> session.gateObj.addProperty("interval", session.interval);
                case "NUMBER_GATE", "DECODER" -> session.gateObj.addProperty("value", session.value);
                case "COUNTER" -> session.gateObj.addProperty("score_limit", session.scoreLimit);
                case "SENSOR" -> session.gateObj.addProperty("radius", session.radius);
                case "RANDOM_NUMBER" -> {
                    session.gateObj.addProperty("min", session.min);
                    session.gateObj.addProperty("max", session.max);
                }
                case "MATH", "COMPARATOR" -> session.gateObj.addProperty("mode", session.mode);
                case "BATTERY" -> {
                    session.gateObj.addProperty("charge_seconds", session.chargeSeconds);
                    session.gateObj.addProperty("decay_seconds", session.decaySeconds);
                }
            }

            plugin.saveGates();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);

            String valStr = switch (session.type) {
                case "OR", "NOR", "AND", "NAND", "XOR", "XNOR" -> session.inputs + " Inputs";

                case "CLOCK", "CLOCK_GATE", "REPEATER" -> session.interval + " Ticks";
                case "NUMBER_GATE", "DECODER" -> String.valueOf(session.value);
                case "COUNTER" -> String.valueOf(session.scoreLimit);
                case "SENSOR" -> String.valueOf(session.radius);
                case "RANDOM_NUMBER" -> session.min + " - " + session.max;
                case "MATH", "COMPARATOR" -> session.mode;
                case "BATTERY" -> "Ładowanie: " + session.chargeSeconds + "s, Rozładowanie: " + session.decaySeconds + "s";
                default -> "";
            };

            player.sendMessage(plugin.getLanguageManager().getWithPrefix("gui.value-changed")
                    .replace("%type%", session.type)
                    .replace("%val%", valStr));

            refreshGUI(session);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player player)) return;

        if (e.getInventory().getHolder() instanceof GateHolder) {
            GateSession session = activeSessions.remove(player.getUniqueId());
            if (session != null) {
                player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.5f, 0.8f);
                player.sendMessage(plugin.getLanguageManager().getWithPrefix("gui.saved"));
            }
        }
    }

    private ItemStack createItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            item.setItemMeta(meta);
        }
        return item;
    }
}