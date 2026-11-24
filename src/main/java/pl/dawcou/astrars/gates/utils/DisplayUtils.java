package pl.dawcou.astrars.gates.utils;

import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;

import java.util.UUID;

class DisplayUtils {

   static String createDisplay(Location blockLoc, String outName) {
        World world = blockLoc.getWorld();
        if (world == null) return "";

        Location displayLoc = blockLoc.clone().add(0.5, 2.0, 0.5);

        switch (outName.toUpperCase()) {
            case "NORTH" -> displayLoc.add(0, 0, -2.0);
            case "SOUTH" -> displayLoc.add(0, 0, 2.0);
            case "EAST"  -> displayLoc.add(2.0, 0, 0);
            case "WEST"  -> displayLoc.add(-2.0, 0, 0);
        }

        TextDisplay textDisplay = world.spawn(displayLoc, TextDisplay.class);

        textDisplay.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        textDisplay.setShadowed(false);
        textDisplay.setAlignment(TextDisplay.TextAlignment.CENTER);
        textDisplay.setBillboard(TextDisplay.Billboard.CENTER);

        Transformation transformation = textDisplay.getTransformation();
        transformation.getScale().set(4f, 4f, 4f);
        textDisplay.setTransformation(transformation);

        return textDisplay.getUniqueId().toString();
    }

    static String validateDisplay(JsonObject gate, Location gateLoc) {
        if (gate == null) return "";

        String uuidStr = gate.has("displayUUID") ? gate.get("displayUUID").getAsString() : null;
        String outName = gate.has("out") ? gate.get("out").getAsString() : "NORTH";

        Location displayLoc = gateLoc.clone().add(0.5, 2.0, 0.5);
        switch (outName.toUpperCase()) {
            case "NORTH" -> displayLoc.add(0, 0, -2.0);
            case "SOUTH" -> displayLoc.add(0, 0, 2.0);
            case "EAST"  -> displayLoc.add(2.0, 0, 0);
            case "WEST"  -> displayLoc.add(-2.0, 0, 0);
        }

        if (!displayLoc.getChunk().isLoaded()) {
            return uuidStr != null ? uuidStr : "";
        }

        try {
            if (uuidStr != null && !uuidStr.isEmpty()) {
                Entity entity = Bukkit.getEntity(UUID.fromString(uuidStr));
                if (entity instanceof TextDisplay) {
                    return uuidStr;
                }
            }
        } catch (Exception ignored) {}

        return createDisplay(gateLoc, outName);
    }

    static void updateDisplayNumber(String uuidStr, String value) {
        if (uuidStr == null || uuidStr.isEmpty()) return;

        try {
            UUID uuid = UUID.fromString(uuidStr);
            Entity entity = Bukkit.getEntity(uuid);

            if (entity instanceof TextDisplay display) {
                display.setText(value);
            }
        } catch (Exception ignored) {}
    }
}