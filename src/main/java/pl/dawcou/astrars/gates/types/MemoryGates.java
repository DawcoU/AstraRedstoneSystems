package pl.dawcou.astrars.gates.types;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import pl.dawcou.astrars.AstraRS;
import pl.dawcou.astrars.file.GateDataManager;
import pl.dawcou.astrars.gates.data.GateContext;
import pl.dawcou.astrars.gates.data.GateContextFactory;
import pl.dawcou.astrars.gates.utils.GateUtils;
import pl.dawcou.astrars.gates.data.GateValidator;

import java.util.Map;
import java.util.Set;

public class MemoryGates {

    private final AstraRS plugin;
    private final GateValidator validator;

    private static final Set<String> OUT_GATES = Set.of("LATCH", "MEMORY_CELL", "TFF");
    private static final Set<String> SIDE_GATES = Set.of("LATCH", "MEMORY_CELL");
    private static final Set<String> BACK_GATES = Set.of("TFF", "MEMORY_CELL");

    public MemoryGates(AstraRS plugin, GateValidator validator) {
        this.plugin = plugin;
        this.validator = validator;
    }

    public void runMemoryGates() {
        GateDataManager manager = plugin.getGateDataManager();
        JsonObject gatesSection = manager.getGates();
        if (gatesSection == null) return;

        for (Map.Entry<String, JsonElement> entry : gatesSection.entrySet()) {
            GateContext ctx = GateContextFactory.create(entry, validator);
            if (ctx == null) continue;

            boolean oldState = ctx.currentState();
            boolean newState = oldState;

            // LOGIKA BRAMEK
            switch (ctx.type()) {
                case "LATCH" -> {
                    // RS Latch: right = Set, left = Reset
                    if (ctx.pRight()) newState = true;
                    else if (ctx.pLeft()) newState = false;
                }
                case "MEMORY_CELL" -> {
                    // Tył = Data, Prawo = Write, Lewo = Read
                    boolean memory = ctx.json().has("memory") && ctx.json().get("memory").getAsBoolean();

                    // ZAPIS
                    if (ctx.pRight()) {
                        memory = ctx.pBack();
                        ctx.json().addProperty("memory", memory);
                    }

                    // ODCZYT
                    newState = memory && ctx.pLeft();
                }
                case "TFF" -> {
                    // Toggle Flip-Flop: Zbocze narastające na tyłach
                    boolean lastIn = ctx.json().has("lastInput") && ctx.json().get("lastInput").getAsBoolean();
                    if (ctx.pBack() != lastIn) {
                        if (ctx.pBack()) { // Jeśli to zbocze narastające (wejście prądu)
                            newState = !oldState;
                        }
                        ctx.json().addProperty("lastInput", ctx.pBack());
                    }
                }
            }

            if (newState != oldState) {
                ctx.json().addProperty("state", newState);
                GateUtils.updateOutput(plugin, ctx.key(), ctx.dirs().targetBlock(), newState);
            }

            if (OUT_GATES.contains(ctx.type())) {
                GateUtils.spawnStatusParticle(ctx.gateBlock(), ctx.dirs().out(), newState);
            }

            if (SIDE_GATES.contains(ctx.type())) {
                GateUtils.spawnStatusParticle(ctx.gateBlock(), ctx.dirs().right(), ctx.pRight());
                GateUtils.spawnStatusParticle(ctx.gateBlock(), ctx.dirs().left(), ctx.pLeft());
            }

            if (BACK_GATES.contains(ctx.type())) {
                GateUtils.spawnStatusParticle(ctx.gateBlock(), ctx.dirs().back(), ctx.pBack());
            }
        }
    }
}