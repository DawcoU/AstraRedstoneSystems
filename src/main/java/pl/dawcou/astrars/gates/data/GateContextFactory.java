package pl.dawcou.astrars.gates.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import pl.dawcou.astrars.AstraRS;
import pl.dawcou.astrars.gates.utils.GateUtils;

import java.util.Map;
import java.util.function.Consumer;

public class GateContextFactory {

    private static GateContext create(Map.Entry<String, JsonElement> entry, GateValidator validator, AstraRS plugin) {
        String key = entry.getKey();
        if (!entry.getValue().isJsonObject()) return null;

        JsonObject gateObj = entry.getValue().getAsJsonObject();
        if (!validator.isValid(gateObj)) return null;

        Location loc = GateUtils.strToLoc(key);
        if (loc == null) return null;

        Block gate = loc.getBlock();
        String type = gateObj.has("type") ? gateObj.get("type").getAsString().toUpperCase() : "";

        // Parsowanie kierunków
        String outStr = gateObj.has("out") ? gateObj.get("out").getAsString().toUpperCase() : "NORTH";
        BlockFace out = BlockFace.valueOf(outStr);
        BlockFace back = out.getOppositeFace();
        BlockFace right = GateUtils.rotate90(out);
        BlockFace left = right.getOppositeFace();

        // Bloki sąsiadujące
        Block targetBlock = gate.getRelative(out);
        Block backBlock = gate.getRelative(back);
        Block rightBlock = gate.getRelative(right);
        Block leftBlock = gate.getRelative(left);

        GateDirections dirs = new GateDirections(
                out, back, right, left,
                targetBlock, backBlock, rightBlock, leftBlock
        );

        // Sygnały wejściowe: dane logiczne z kabla mają pierwszeństwo
        boolean pBack = getInputState(backBlock, plugin);
        boolean pRight = getInputState(rightBlock, plugin);
        boolean pLeft = getInputState(leftBlock, plugin);

        boolean currentState = gateObj.has("state") && gateObj.get("state").getAsBoolean();
        int inputs = gateObj.has("inputs") ? gateObj.get("inputs").getAsInt() : 2;

        return new GateContext(
                key, gateObj, loc, gate, type, currentState, inputs,
                dirs, pBack, pRight, pLeft
        );
    }

    public static void createAndRun(Map.Entry<String, JsonElement> entry, GateValidator validator, AstraRS plugin, Consumer<GateContext> action) {
        String key = entry.getKey();
        Location loc = GateUtils.strToLoc(key);
        if (loc == null) return;

        // Przenosimy wykonanie na wątek regionu zanim dotkniemy świata w create()
        plugin.getSchedulerManager().executeAt(loc, () -> {
            GateContext ctx = create(entry, validator, plugin);
            if (ctx != null) {
                action.accept(ctx);
            }
        });
    }

    private static boolean getInputState(Block block, AstraRS plugin) {
        Boolean dataState = GateUtils.getBooleanFrom(block, plugin);
        return dataState != null
                ? dataState
                : GateUtils.getPowerAt(block) > 0;
    }
}