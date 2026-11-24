package pl.dawcou.astrars.gates.types;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.bukkit.Bukkit;

import pl.dawcou.astrars.AstraRS;
import pl.dawcou.astrars.file.GateDataManager;
import pl.dawcou.astrars.gates.data.GateContextFactory;
import pl.dawcou.astrars.gates.data.GateValidator;
import pl.dawcou.astrars.gates.utils.GateUtils;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class BooleanGates {

    private final AstraRS plugin;
    private final GateValidator validator;

    private static final Set<String> HANDLED_TYPES = Set.of(
            "RANDOM_BOOLEAN", "BOOLEAN_SENDER"
    );

    private static final Set<String> OUT_GATES = Set.of(
            "RANDOM_BOOLEAN"
    );

    private static final Set<String> BACK_GATES = Set.of(
            "BOOLEAN_SENDER", "RANDOM_BOOLEAN"
    );

    public BooleanGates(AstraRS plugin, GateValidator validator) {
        this.plugin = plugin;
        this.validator = validator;
    }

    // --------------------------------------------------
    // GŁÓWNA PĘTLA
    // --------------------------------------------------

    public void runBooleanGates() {
        GateDataManager manager = plugin.getGateDataManager();
        JsonObject gatesSection = manager.getGates();

        if (gatesSection == null) return;

        for (Map.Entry<String, JsonElement> entry : gatesSection.entrySet()) {
            GateContextFactory.createAndRun(entry, validator, plugin, ctx -> {
                if (ctx == null) return;

                if (!HANDLED_TYPES.contains(ctx.type())) return;

                JsonObject gateObj = ctx.json();
                String type = ctx.type();
                String key = ctx.key();

                // --------------------------------------------------
                // EFEKTY WIZUALNE STATUSU
                // --------------------------------------------------

                // Wyjście.
                if (OUT_GATES.contains(type)) {
                    GateUtils.spawnStatusParticle(
                            ctx.gateBlock(),
                            ctx.dirs().out(),
                            ctx.currentState()
                    );
                }

                // Wejście z tyłu.
                if (BACK_GATES.contains(type)) {
                    boolean active;

                    active = ctx.pBack();

                    GateUtils.spawnStatusParticle(
                            ctx.gateBlock(),
                            ctx.dirs().back(),
                            active
                    );
                }

                // --------------------------------------------------
                // LOGIKA BRAMEK
                // --------------------------------------------------

                switch (type) {
                    // ==============================================
                    // BOOLEAN_SENDER — wyjście logiczne
                    // ==============================================

                    case "BOOLEAN_SENDER" -> {
                        boolean hasPower = ctx.pBack();

                        JsonElement result = new JsonPrimitive(hasPower);

                        boolean outputChanged = GateUtils.setOutputIfChanged(
                                gateObj,
                                result
                        );

                        if (outputChanged
                                && plugin.isDebugEnabled()) {
                            Bukkit.getConsoleSender().sendMessage(
                                    AstraRS.DEBUG_PREFIX
                                            + "§3BOOLEAN_SENDER §7at §e"
                                            + key + " §7-> State: "
                                            + (hasPower ? "§aTRUE" : "§cFALSE")
                            );
                        }

                        GateUtils.syncOutput(
                                plugin,
                                gateObj,
                                ctx,
                                key,
                                hasPower,
                                outputChanged
                        );
                    }

                    case "RANDOM_BOOLEAN" -> {
                        boolean input = ctx.pBack();

                        boolean lastInput = GateUtils.readBoolean(
                                gateObj,
                                "lastInput",
                                false
                        );

                        // Losowanie wyłącznie na zboczu narastającym.
                        if (input && !lastInput) {
                            boolean value = ThreadLocalRandom.current()
                                    .nextBoolean();

                            JsonElement resultElement = new JsonPrimitive(value);

                            boolean outputChanged = GateUtils.setOutputIfChanged(
                                    gateObj,
                                    resultElement
                            );

                            GateUtils.syncOutput(
                                    plugin,
                                    gateObj,
                                    ctx,
                                    key,
                                    value,
                                    outputChanged
                            );

                            if (plugin.isDebugEnabled()) {
                                Bukkit.getConsoleSender().sendMessage(
                                        AstraRS.DEBUG_PREFIX
                                                + "§a§lRANDOM §7[" + type + "] at §e" + key
                                                + " §7rolled value: §b§l" + resultElement
                                );
                            }
                        }

                        GateUtils.setBooleanIfChanged(
                                gateObj,
                                "lastInput",
                                input
                        );
                    }
                }
            });
        }
    }
}