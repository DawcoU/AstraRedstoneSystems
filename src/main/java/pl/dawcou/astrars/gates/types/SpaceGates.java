package pl.dawcou.astrars.gates.types;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;

import pl.dawcou.astrars.AstraRS;
import pl.dawcou.astrars.file.GateDataManager;
import pl.dawcou.astrars.gates.data.GateContext;
import pl.dawcou.astrars.gates.data.GateContextFactory;
import pl.dawcou.astrars.gates.data.GateValidator;
import pl.dawcou.astrars.gates.utils.GateUtils;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SpaceGates {
    private final AstraRS plugin;
    private final GateValidator validator;
    private long lastCleanup = 0;

    public SpaceGates(AstraRS plugin, GateValidator validator) {
        this.plugin = plugin;
        this.validator = validator;
    }

    public void runSpaceGates() {
        GateDataManager manager = plugin.getGateDataManager();
        JsonObject gatesSection = manager.getGates();
        if (gatesSection == null) return;

        long currentTime = System.currentTimeMillis();
        boolean shouldClean = (currentTime - lastCleanup > 300000);
        Set<String> usedChannels = shouldClean ? new HashSet<>() : null;

        JsonObject root = manager.getRoot();
        JsonObject channelsSection = root.has("channels") && root.get("channels").isJsonObject() ? root.getAsJsonObject("channels") : null;
        JsonObject activeChannelsSection = root.has("active_channels") && root.get("active_channels").isJsonObject() ? root.getAsJsonObject("active_channels") : null;

        for (Map.Entry<String, JsonElement> entry : gatesSection.entrySet()) {
            GateContext ctx = GateContextFactory.create(entry, validator);
            if (ctx == null) continue;

            JsonObject gateObj = ctx.json();
            String key = ctx.key();
            String type = ctx.type();

            if (shouldClean && gateObj.has("channel")) {
                for (String split : gateObj.get("channel").getAsString().split(",")) {
                    usedChannels.add(split.trim());
                }
            }

            switch (type) {
                case "SENDER" -> {
                    String rawChannels = gateObj.has("channel") ? gateObj.get("channel").getAsString() : "default";
                    String[] splitChannels = rawChannels.split(",");

                    String incomingData = GateUtils.getStringFrom(ctx.dirs().backBlock(), plugin);
                    int traditionalPower = GateUtils.getPowerAt(ctx.dirs().backBlock());

                    String signalToBroadcast = null;
                    if (!incomingData.isEmpty() && !incomingData.equals("-2147483648") && !incomingData.equals("-9223372036854775808")) {
                        signalToBroadcast = incomingData;
                    } else if (traditionalPower > 0) {
                        signalToBroadcast = "_REDSTONE_";
                    }

                    String lastTransmitted = gateObj.has("current_out") ? gateObj.get("current_out").getAsString() : "";
                    String currentVal = (signalToBroadcast != null) ? signalToBroadcast : "";

                    if (!currentVal.equals(lastTransmitted)) {
                        gateObj.addProperty("current_out", currentVal);
                        gateObj.addProperty("state", signalToBroadcast != null);

                        if (channelsSection == null) {
                            channelsSection = new JsonObject();
                            root.add("channels", channelsSection);
                        }
                        if (activeChannelsSection == null) {
                            activeChannelsSection = new JsonObject();
                            root.add("active_channels", activeChannelsSection);
                        }

                        for (String chan : splitChannels) {
                            String trimmed = chan.trim();
                            if (signalToBroadcast != null) {
                                channelsSection.addProperty(trimmed, signalToBroadcast);
                            } else {
                                channelsSection.remove(trimmed);
                            }
                            activeChannelsSection.addProperty(trimmed, signalToBroadcast != null);
                        }
                    }

                    boolean hasListener = false;
                    if (activeChannelsSection != null) {
                        for (String chan : splitChannels) {
                            String trimmed = chan.trim();
                            if (activeChannelsSection.has(trimmed) && activeChannelsSection.get(trimmed).getAsBoolean()) {
                                hasListener = true;
                                break;
                            }
                        }
                    }

                    boolean isTransmitting = (signalToBroadcast != null) && hasListener;
                    GateUtils.spawnStatusParticle(ctx.gateBlock(), ctx.dirs().out(), isTransmitting);
                    GateUtils.spawnStatusParticle(ctx.gateBlock(), ctx.dirs().back(), signalToBroadcast != null);
                }

                case "RECEIVER" -> {
                    String channel = gateObj.has("channel") ? gateObj.get("channel").getAsString().trim() : "default";
                    String payload = (channelsSection != null && channelsSection.has(channel)) ? channelsSection.get(channel).getAsString() : "";

                    boolean shouldOutput = false;
                    String dataToPass = "";

                    if (payload.equals("_REDSTONE_")) {
                        shouldOutput = true;
                    } else if (!payload.isEmpty()) {
                        shouldOutput = true;
                        dataToPass = payload;
                    }

                    gateObj.addProperty("current_out", dataToPass);
                    gateObj.addProperty("state", shouldOutput);

                    GateUtils.updateOutput(plugin, key, ctx.dirs().targetBlock(), shouldOutput);
                    GateUtils.spawnStatusParticle(ctx.gateBlock(), ctx.dirs().out(), shouldOutput);
                }

                case "SENSOR" -> {
                    double radius = gateObj.has("radius") ? gateObj.get("radius").getAsDouble() : 5.0;
                    double radiusSq = radius * radius;

                    var center = ctx.gateBlock().getLocation().add(0.5, 0.5, 0.5);

                    int count = ctx.gateBlock().getWorld().getNearbyEntities(
                            center, radius, radius, radius,
                            entity -> entity instanceof LivingEntity
                                    && !(entity instanceof ArmorStand)
                                    && entity.getLocation().distanceSquared(center) <= radiusSq
                    ).size();

                    boolean isActive = (count > 0);
                    boolean lastState = ctx.currentState();

                    if (isActive != lastState) {
                        gateObj.addProperty("state", isActive);
                        GateUtils.updateOutput(plugin, key, ctx.dirs().targetBlock(), isActive);
                    }

                    String lastRes = gateObj.has("current_out") ? gateObj.get("current_out").getAsString() : "";
                    String currentResStr = String.valueOf(count);

                    if (!currentResStr.equals(lastRes)) {
                        if (plugin.isDebugMode()) {
                            Bukkit.getConsoleSender().sendMessage(
                                    AstraRS.DEBUG_PREFIX + "§b§lSENSOR §e" + key + " §7detected entities: §a§l" + count
                            );
                        }
                        gateObj.addProperty("current_out", currentResStr);
                    }

                    GateUtils.spawnStatusParticle(ctx.gateBlock(), ctx.dirs().out(), isActive);
                }
            }
        }

        if (shouldClean) {
            cleanDeadChannels(channelsSection, activeChannelsSection, usedChannels);
            lastCleanup = currentTime;
        }
    }

    private void cleanDeadChannels(JsonObject channels, JsonObject activeChannels, Set<String> usedChannels) {
        if (channels != null) {
            new HashSet<>(channels.keySet()).forEach(chan -> {
                if (!usedChannels.contains(chan)) channels.remove(chan);
            });
        }
        if (activeChannels != null) {
            new HashSet<>(activeChannels.keySet()).forEach(chan -> {
                if (!usedChannels.contains(chan)) activeChannels.remove(chan);
            });
        }
    }
}