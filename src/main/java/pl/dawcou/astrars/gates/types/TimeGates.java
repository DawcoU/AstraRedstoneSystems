package pl.dawcou.astrars.gates.types;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import pl.dawcou.astrars.AstraRS;
import pl.dawcou.astrars.file.GateDataManager;
import pl.dawcou.astrars.gates.data.GateContext;
import pl.dawcou.astrars.gates.data.GateContextFactory;
import pl.dawcou.astrars.gates.data.GateValidator;
import pl.dawcou.astrars.gates.utils.GateUtils;
import pl.dawcou.astrars.system.SchedulerManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class TimeGates {

    private final AstraRS plugin;
    private final GateValidator validator;

    private final Map<String, SchedulerManager.Task> repeaterTasks = new HashMap<>();

    private static final Set<String> OUT_GATES = Set.of("CLOCK_GATE", "CLOCK", "REPEATER", "PULSER");
    private static final Set<String> BACK_GATES = Set.of("CLOCK_GATE", "REPEATER", "PULSER");

    public TimeGates(AstraRS plugin, GateValidator validator) {
        this.plugin = plugin;
        this.validator = validator;
    }

    public void runTimeGates() {
        GateDataManager manager = plugin.getGateDataManager();
        JsonObject gatesSection = manager.getGates();
        if (gatesSection == null) return;

        for (Map.Entry<String, JsonElement> entry : gatesSection.entrySet()) {
            GateContext ctx = GateContextFactory.create(entry, validator);
            if (ctx == null) continue;

            JsonObject gateObj = ctx.json();
            String key = ctx.key();
            String type = ctx.type();

            if (OUT_GATES.contains(type)) {
                GateUtils.spawnStatusParticle(ctx.gateBlock(), ctx.dirs().out(), ctx.currentState());
            }

            if (BACK_GATES.contains(type)) {
                GateUtils.spawnStatusParticle(ctx.gateBlock(), ctx.dirs().back(), ctx.pBack());
            }

            switch (type) {
                case "REPEATER" -> {
                    boolean lastIn = gateObj.has("last_in") && gateObj.get("last_in").getAsBoolean();

                    if (ctx.pBack() != lastIn) {
                        gateObj.addProperty("last_in", ctx.pBack());

                        if (repeaterTasks.containsKey(key)) {
                            repeaterTasks.get(key).cancel();
                        }

                        int delay = gateObj.has("interval") ? gateObj.get("interval").getAsInt() : 20;
                        final boolean finalPower = ctx.pBack();

                        SchedulerManager.Task task = plugin.getSchedulerManager().runSyncLater(() -> {
                            gateObj.addProperty("state", finalPower);
                            GateUtils.updateOutput(plugin, key, ctx.dirs().targetBlock(), finalPower);
                            repeaterTasks.remove(key);
                        }, delay);

                        repeaterTasks.put(key, task);
                    }
                }

                case "PULSER" -> {
                    boolean lastIn = gateObj.has("lastInput") && gateObj.get("lastInput").getAsBoolean();
                    boolean result = ctx.pBack() && !lastIn;

                    gateObj.addProperty("lastInput", ctx.pBack());

                    if (result != ctx.currentState()) {
                        gateObj.addProperty("state", result);
                        GateUtils.updateOutput(plugin, key, ctx.dirs().targetBlock(), result);
                    }
                }

                case "CLOCK", "CLOCK_GATE" -> {
                    int interval = gateObj.has("interval") ? gateObj.get("interval").getAsInt() : 20;
                    boolean enabled = "CLOCK".equals(type) || ctx.pBack();

                    if (!enabled) {
                        if (ctx.currentState()) {
                            GateUtils.updateOutput(plugin, key, ctx.dirs().targetBlock(), false);
                            gateObj.addProperty("state", false);
                        }
                        gateObj.addProperty("next_tick", 0);
                    } else {
                        int nt = (gateObj.has("next_tick") ? gateObj.get("next_tick").getAsInt() : 0) + 1;
                        if (nt >= interval) {
                            boolean newState = !ctx.currentState();
                            gateObj.addProperty("state", newState);

                            GateUtils.updateOutput(plugin, key, ctx.dirs().targetBlock(), newState);
                            nt = 0;
                        }
                        gateObj.addProperty("next_tick", nt);
                    }
                }
            }
        }
    }
}