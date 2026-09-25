package pl.dawcou.astrars.gates.data;

import com.google.gson.JsonObject;
import org.bukkit.Location;
import org.bukkit.block.Block;

public record GateContext(
        String key,
        JsonObject json,
        Location location,
        Block gateBlock,
        String type,
        boolean currentState,
        int inputs,
        GateDirections dirs,
        boolean pBack,
        boolean pRight,
        boolean pLeft
) {}