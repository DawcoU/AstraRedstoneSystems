package pl.dawcou.astrars.gates.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import pl.dawcou.astrars.gates.utils.GateUtils;

import java.util.Map;

public class GateContextFactory {

    public static GateContext create(Map.Entry<String, JsonElement> entry, GateValidator validator) {
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

        // Sygnały wejściowe
        boolean pBack = GateUtils.getPowerAt(backBlock) > 0;
        boolean pRight = GateUtils.getPowerAt(rightBlock) > 0;
        boolean pLeft = GateUtils.getPowerAt(leftBlock) > 0;

        boolean currentState = gateObj.has("state") && gateObj.get("state").getAsBoolean();
        int inputs = gateObj.has("inputs") ? gateObj.get("inputs").getAsInt() : 2;

        return new GateContext(
                key, gateObj, loc, gate, type, currentState, inputs,
                dirs, pBack, pRight, pLeft
        );
    }
}