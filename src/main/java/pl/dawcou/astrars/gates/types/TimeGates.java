package pl.dawcou.astrars.gates.types;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import pl.dawcou.astrars.AstraRS;
import pl.dawcou.astrars.file.GateDataManager;
import pl.dawcou.astrars.gates.data.GateContextFactory;
import pl.dawcou.astrars.gates.data.GateValidator;
import pl.dawcou.astrars.gates.utils.GateUtils;
import pl.dawcou.astrars.system.SchedulerManager;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TimeGates {

    private final AstraRS plugin;
    private final GateValidator validator;

    private final Map<String, SchedulerManager.Task> repeaterTasks = new ConcurrentHashMap<>();

    private static final Set<String> HANDLED_TYPES = Set.of(
            "CLOCK", "REPEATER", "PULSER"
    );

    private static final Set<String> OUT_GATES = Set.of(
            "CLOCK", "REPEATER", "PULSER"
    );

    private static final Set<String> BACK_GATES = Set.of(
            "CLOCK", "REPEATER", "PULSER"
    );

    public TimeGates(AstraRS plugin, GateValidator validator) {
        this.plugin = plugin;
        this.validator = validator;
    }

    // --------------------------------------------------
    // GŁÓWNA PĘTLA
    // --------------------------------------------------

    public void runTimeGates() {
        GateDataManager manager = plugin.getGateDataManager();
        JsonObject gatesSection = manager.getGates();
        if (gatesSection == null) return;

        for (Map.Entry<String, JsonElement> entry : gatesSection.entrySet()) {
            GateContextFactory.createAndRun(entry, validator, plugin, ctx -> {
                if (ctx == null) return;

                if (!HANDLED_TYPES.contains(ctx.type())) return;

                JsonObject gateObj = ctx.json();
                String key = ctx.key();
                String type = ctx.type();

                // Odczyt wejść
                boolean enabled = ctx.pBack();

                // ----------------------------------------------------
                // CZĄSTECZKI STATUSU
                // ----------------------------------------------------

                if (OUT_GATES.contains(type)) {
                    GateUtils.spawnStatusParticle(
                            ctx.gateBlock(), ctx.dirs().out(), ctx.currentState()
                    );
                }

                if (BACK_GATES.contains(type)) {
                    GateUtils.spawnStatusParticle(
                            ctx.gateBlock(), ctx.dirs().back(), enabled
                    );
                }

                // ----------------------------------------------------
                // LOGIKA BRAMEK CZASOWYCH
                // ----------------------------------------------------

                switch (type) {
                    case "REPEATER" -> {
                        boolean lastIn = gateObj.has("last_in")
                                && gateObj.get("last_in").getAsBoolean();

                        if (enabled != lastIn) {
                            gateObj.addProperty("last_in", enabled);

                            if (repeaterTasks.containsKey(key)) {
                                repeaterTasks.get(key).cancel();
                            }

                            int delay = gateObj.has("interval")
                                    ? gateObj.get("interval").getAsInt()
                                    : 20;

                            final boolean finalPower = enabled;

                            SchedulerManager.Task task =
                                    plugin.getSchedulerManager().runSyncLater(() -> {
                                        GateUtils.syncOutput(
                                                plugin,
                                                gateObj,
                                                ctx,
                                                key,
                                                finalPower,
                                                false
                                        );
                                        repeaterTasks.remove(key);
                                    }, delay);

                            repeaterTasks.put(key, task);
                        }
                    }

                    case "PULSER" -> {
                        boolean lastIn = gateObj.has("lastInput")
                                && gateObj.get("lastInput").getAsBoolean();

                        boolean result = enabled && !lastIn;

                        gateObj.addProperty("lastInput", enabled);

                        GateUtils.syncOutput(
                                plugin,
                                gateObj,
                                ctx,
                                key,
                                result,
                                false
                        );
                    }

                    case "CLOCK" -> {
                        int interval = gateObj.has("interval")
                                ? gateObj.get("interval").getAsInt()
                                : 20;

                        // Zegar działa tylko wtedy, gdy z tyłu jest dostarczany sygnał (pBack)

                        if (!enabled) {
                            if (ctx.currentState()) {
                                GateUtils.syncOutput(
                                        plugin,
                                        gateObj,
                                        ctx,
                                        key,
                                        false,
                                        false
                                );
                            }
                            gateObj.addProperty("next_tick", 0);
                        } else {
                            int nt = (gateObj.has("next_tick")
                                    ? gateObj.get("next_tick").getAsInt()
                                    : 0) + 1;

                            if (nt >= interval) {
                                boolean newState = !ctx.currentState();
                                GateUtils.syncOutput(
                                        plugin,
                                        gateObj,
                                        ctx,
                                        key,
                                        newState,
                                        false
                                );
                                nt = 0;
                            }

                            gateObj.addProperty("next_tick", nt);
                        }
                    }
                }
            });
        }
    }
}