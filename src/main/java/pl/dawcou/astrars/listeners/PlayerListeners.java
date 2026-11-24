package pl.dawcou.astrars.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import pl.dawcou.astrars.AstraRS;

public class PlayerListeners implements Listener {

    private final AstraRS plugin;

    public PlayerListeners(AstraRS plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();

        if (plugin.getConfig().getBoolean("settings.update.check-updates", true)
                && p.hasPermission("astrars.update")) {

            plugin.getUpdateChecker().checkForUpdates(p);
        }
    }
}