package pl.dawcou.astrars.listeners;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import pl.dawcou.astrars.AstraRS;
import pl.dawcou.astrars.selection.SelectionManager;

public class SelectionListeners implements Listener {

    private final AstraRS plugin;
    private final SelectionManager selectionManager;

    public SelectionListeners(AstraRS plugin, SelectionManager selectionManager) {
        this.plugin = plugin;
        this.selectionManager = selectionManager;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || !item.hasItemMeta()) return;

        ItemMeta currentMeta = item.getItemMeta();
        if (currentMeta == null) return;

        NamespacedKey key = new NamespacedKey(plugin, "item_type");
        if (!currentMeta.getPersistentDataContainer().has(key, PersistentDataType.STRING)) return;

        String itemType = currentMeta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (!"gate_selector".equals(itemType)) return;

        if (!player.hasPermission("astrars.admin")) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("general.no-permission"));
            return;
        }

        if (event.getClickedBlock() == null) return;

        event.setCancelled(true);
        Location clickedLoc = event.getClickedBlock().getLocation();

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            selectionManager.setPos1(player, clickedLoc);
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            selectionManager.setPos2(player, clickedLoc);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        selectionManager.clearPlayer(event.getPlayer().getUniqueId());
    }
}