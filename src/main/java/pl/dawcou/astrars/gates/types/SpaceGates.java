package pl.dawcou.astrars.gates.types;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;

import pl.dawcou.astrars.AstraRS;
import pl.dawcou.astrars.file.GateDataManager;
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

    private static final Set<String> HANDLED_TYPES = Set.of(
            "SENDER", "RECEIVER", "SENSOR"
    );

    // Oznaczenie sygnału fizycznego redstone (nowy, jedyny format).
    private static final String REDSTONE_MARKER = "__astrars_redstone_signal__";

    // ============================================================
    // KONSTRUKTOR
    // ============================================================

    public SpaceGates(AstraRS plugin, GateValidator validator) {
        this.plugin = plugin;
        this.validator = validator;
    }

    // ============================================================
    // ROZPOZNAWANIE SYGNAŁU REDSTONE (Wyczyszczone ze starego _REDSTONE_)
    // ============================================================

    private boolean isRedstoneSignal(JsonElement payload) {
        if (payload == null || payload.isJsonNull()) {
            return false;
        }

        // Sprawdzamy wyłącznie nowoczesny, obiektowy znacznik redstone.
        if (payload.isJsonObject()) {
            JsonObject object = payload.getAsJsonObject();
            return object.has(REDSTONE_MARKER)
                    && object.get(REDSTONE_MARKER).isJsonPrimitive()
                    && object.get(REDSTONE_MARKER).getAsJsonPrimitive().isBoolean()
                    && object.get(REDSTONE_MARKER).getAsBoolean();
        }

        return false;
    }

    private JsonObject createRedstoneSignal() {
        JsonObject marker = new JsonObject();
        marker.addProperty(REDSTONE_MARKER, true);
        return marker;
    }

    // --------------------------------------------------
    // GŁÓWNA PĘTLA
    // --------------------------------------------------

    public void runSpaceGates() {
        GateDataManager manager = plugin.getGateDataManager();
        JsonObject gatesSection = manager.getGates();
        if (gatesSection == null) return;

        long currentTime = System.currentTimeMillis();
        boolean shouldClean = (currentTime - lastCleanup > 300000);
        Set<String> usedChannels = shouldClean ? new HashSet<>() : null;

        JsonObject root = manager.getRoot();

        var ref = new Object() {
            JsonObject channelsSection =
                    root.has("channels") && root.get("channels").isJsonObject()
                            ? root.getAsJsonObject("channels")
                            : null;
            JsonObject activeChannelsSection =
                    root.has("active_channels") && root.get("active_channels").isJsonObject()
                            ? root.getAsJsonObject("active_channels")
                            : null;
        };


        // --------------------------------------------------------
        // PRZETWARZANIE WSZYSTKICH BRAMEK
        // --------------------------------------------------------

        for (Map.Entry<String, JsonElement> entry : gatesSection.entrySet()) {
            GateContextFactory.createAndRun(entry, validator, plugin, ctx -> {
                if (ctx == null) return;

                if (!HANDLED_TYPES.contains(ctx.type())) return;

                JsonObject gateObj = ctx.json();
                String key = ctx.key();
                String type = ctx.type();

                if (shouldClean && gateObj.has("channel")) {
                    for (String split : gateObj.get("channel").getAsString().split(",")) {
                        usedChannels.add(split.trim());
                    }
                }

                // ====================================================
                // SENDER — WYSYŁANIE DANYCH
                // ====================================================

                switch (type) {
                    case "SENDER" -> {
                        String rawChannels = gateObj.has("channel")
                                ? gateObj.get("channel").getAsString()
                                : "default";

                        String[] splitChannels = rawChannels.split(",");

                        JsonElement incomingData = GateUtils.getDataFrom(
                                ctx.dirs().backBlock(), plugin
                        );

                        int traditionalPower = GateUtils.getPowerAt(
                                ctx.dirs().backBlock()
                        );

                        JsonElement signalToBroadcast = incomingData;

                        if (signalToBroadcast == null && traditionalPower > 0) {
                            signalToBroadcast = createRedstoneSignal();
                        }

                        boolean outputChanged = GateUtils.setOutputIfChanged(
                                gateObj, signalToBroadcast
                        );

                        if (outputChanged) {
                            gateObj.addProperty("state", signalToBroadcast != null);

                            if (ref.channelsSection == null) {
                                ref.channelsSection = new JsonObject();
                                root.add("channels", ref.channelsSection);
                            }

                            if (ref.activeChannelsSection == null) {
                                ref.activeChannelsSection = new JsonObject();
                                root.add("active_channels", ref.activeChannelsSection);
                            }

                            for (String chan : splitChannels) {
                                String trimmed = chan.trim();

                                if (signalToBroadcast != null) {
                                    ref.channelsSection.add(
                                            trimmed,
                                            signalToBroadcast.deepCopy()
                                    );
                                } else {
                                    ref.channelsSection.remove(trimmed);
                                }

                                ref.activeChannelsSection.addProperty(
                                        trimmed,
                                        signalToBroadcast != null
                                );
                            }
                        }

                        boolean hasListener = false;

                        if (ref.activeChannelsSection != null) {
                            for (String chan : splitChannels) {
                                String trimmed = chan.trim();

                                if (ref.activeChannelsSection.has(trimmed)
                                        && ref.activeChannelsSection.get(trimmed).getAsBoolean()) {
                                    hasListener = true;
                                    break;
                                }
                            }
                        }

                        boolean isTransmitting =
                                signalToBroadcast != null && hasListener;

                        GateUtils.spawnStatusParticle(
                                ctx.gateBlock(),
                                ctx.dirs().out(),
                                isTransmitting
                        );

                        GateUtils.spawnStatusParticle(
                                ctx.gateBlock(),
                                ctx.dirs().back(),
                                signalToBroadcast != null
                        );
                    }


                    // =================================================
                    // RECEIVER — ODBIERANIE DANYCH
                    // =================================================

                    case "RECEIVER" -> {
                        String channel = gateObj.has("channel")
                                ? gateObj.get("channel").getAsString().trim()
                                : "default";

                        JsonElement payload = null;

                        boolean channelActive = ref.activeChannelsSection != null
                                && ref.activeChannelsSection.has(channel)
                                && ref.activeChannelsSection.get(channel).getAsBoolean();

                        if (channelActive
                                && ref.channelsSection != null
                                && ref.channelsSection.has(channel)) {
                            payload = ref.channelsSection.get(channel);
                        }

                        boolean shouldOutput = false;
                        JsonElement dataToPass = JsonNull.INSTANCE;

                        if (payload != null && !payload.isJsonNull()) {
                            if (isRedstoneSignal(payload)) {
                                shouldOutput = true;
                            } else {
                                dataToPass = payload.deepCopy();

                                if (payload.isJsonPrimitive()
                                        && payload.getAsJsonPrimitive().isBoolean()) {
                                    shouldOutput = payload.getAsBoolean();
                                } else {
                                    shouldOutput = true;
                                }
                            }
                        }

                        GateUtils.setOutputIfChanged(gateObj, dataToPass);
                        gateObj.addProperty("state", shouldOutput);

                        GateUtils.updateOutput(
                                plugin, key,
                                ctx.dirs().targetBlock(),
                                shouldOutput
                        );

                        GateUtils.spawnStatusParticle(
                                ctx.gateBlock(),
                                ctx.dirs().out(),
                                shouldOutput
                        );
                    }


                    // =================================================
                    // SENSOR — WYKRYWANIE ENCJI
                    // =================================================

                    case "SENSOR" -> {
                        double radius = gateObj.has("radius")
                                ? gateObj.get("radius").getAsDouble()
                                : 5.0;

                        double radiusSq = radius * radius;

                        var center = ctx.gateBlock()
                                .getLocation()
                                .add(0.5, 0.5, 0.5);

                        int count = ctx.gateBlock().getWorld().getNearbyEntities(
                                center, radius, radius, radius,
                                entity -> entity instanceof LivingEntity
                                        && !(entity instanceof ArmorStand)
                                        && entity.getLocation().distanceSquared(center) <= radiusSq
                        ).size();

                        boolean isActive = count > 0;
                        boolean lastState = ctx.currentState();

                        if (isActive != lastState) {
                            gateObj.addProperty("state", isActive);

                            GateUtils.updateOutput(
                                    plugin, key,
                                    ctx.dirs().targetBlock(),
                                    isActive
                            );
                        }

                        String lastRes = gateObj.has("current_out")
                                && !gateObj.get("current_out").isJsonNull()
                                ? gateObj.get("current_out").getAsString()
                                : "";

                        String currentResStr = String.valueOf(count);

                        if (!currentResStr.equals(lastRes)) {
                            if (plugin.isDebugEnabled()) {
                                Bukkit.getConsoleSender().sendMessage(
                                        AstraRS.DEBUG_PREFIX
                                                + "§b§lSENSOR §e" + key
                                                + " §7detected entities: §a§l" + count
                                );
                            }

                            gateObj.addProperty("current_out", currentResStr);
                        }

                        GateUtils.spawnStatusParticle(
                                ctx.gateBlock(),
                                ctx.dirs().out(),
                                isActive
                        );
                    }
                }
            });
        }

        // --------------------------------------------------------
        // OKRESOWE CZYSZCZENIE NIEUŻYWANYCH KANAŁÓW
        // --------------------------------------------------------

        if (shouldClean) {
            cleanDeadChannels(ref.channelsSection, ref.activeChannelsSection, usedChannels);
            lastCleanup = currentTime;
        }
    }


    // ============================================================
    // CZYSZCZENIE NIEUŻYWANYCH KANAŁÓW
    // ============================================================

    private void cleanDeadChannels(
            JsonObject channels,
            JsonObject activeChannels,
            Set<String> usedChannels
    ) {
        if (channels != null) {
            new HashSet<>(channels.keySet()).forEach(chan -> {
                if (!usedChannels.contains(chan)) {
                    channels.remove(chan);
                }
            });
        }

        if (activeChannels != null) {
            new HashSet<>(activeChannels.keySet()).forEach(chan -> {
                if (!usedChannels.contains(chan)) {
                    activeChannels.remove(chan);
                }
            });
        }
    }
}