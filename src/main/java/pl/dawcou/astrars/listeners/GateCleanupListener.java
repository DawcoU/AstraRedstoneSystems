package pl.dawcou.astrars.listeners;

import com.google.gson.JsonObject;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import pl.dawcou.astrars.AstraRS;
import pl.dawcou.astrars.file.GateDataManager;
import pl.dawcou.astrars.gates.utils.GateUtils;

import java.util.List;

public class GateCleanupListener implements Listener {

    private final AstraRS plugin;

    public GateCleanupListener(AstraRS plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        handleBlockList(e.blockList());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        handleBlockList(e.blockList());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        for (Block block : e.getBlocks()) {
            if (isGateBlock(block)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        for (Block block : e.getBlocks()) {
            if (isGateBlock(block)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent e) {
        removeGateIfExists(e.getBlock());
    }

    private boolean isGateBlock(Block block) {
        GateDataManager manager = plugin.getGateDataManager();
        JsonObject gatesSection = manager.getGates();
        if (gatesSection == null) return false;
        return gatesSection.has(GateUtils.locToStr(block.getLocation()));
    }

    private void handleBlockList(List<Block> blocks) {
        for (Block block : blocks) {
            removeGateIfExists(block);
        }
    }

    private void removeGateIfExists(Block block) {
        String locStr = GateUtils.locToStr(block.getLocation());
        GateDataManager manager = plugin.getGateDataManager();
        JsonObject gatesSection = manager.getGates();

        if (gatesSection != null && gatesSection.has(locStr)) {
            GateUtils.removeGate(plugin, block.getLocation());
        }
    }
}