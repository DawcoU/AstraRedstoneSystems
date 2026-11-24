package pl.dawcou.astrars.gates.types;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import pl.dawcou.astrars.AstraRS;
import pl.dawcou.astrars.file.GateDataManager;
import pl.dawcou.astrars.gates.data.GateContextFactory;
import pl.dawcou.astrars.gates.data.GateValidator;
import pl.dawcou.astrars.gates.utils.GateUtils;

import java.util.Map;
import java.util.Set;

public class MemoryGates {

    private final AstraRS plugin;
    private final GateValidator validator;

    private static final Set<String> HANDLED_TYPES =
            Set.of("LATCH", "MEMORY_CELL", "TFF");

    private static final Set<String> OUT_GATES =
            Set.of("LATCH", "MEMORY_CELL", "TFF");

    private static final Set<String> SIDE_GATES =
            Set.of("LATCH", "MEMORY_CELL");

    private static final Set<String> BACK_GATES =
            Set.of("TFF", "MEMORY_CELL");

    public MemoryGates(AstraRS plugin, GateValidator validator) {
        this.plugin = plugin;
        this.validator = validator;
    }

    // --------------------------------------------------
    // GŁÓWNA PĘTLA
    // --------------------------------------------------

    public void runMemoryGates() {
        GateDataManager manager = plugin.getGateDataManager();
        JsonObject gatesSection = manager.getGates();
        if (gatesSection == null) return;

        for (Map.Entry<String, JsonElement> entry : gatesSection.entrySet()) {
            GateContextFactory.createAndRun(entry, validator, plugin, ctx -> {
                if (ctx == null) return;

                if (!HANDLED_TYPES.contains(ctx.type())) return;

                boolean oldState = ctx.currentState();
                boolean newState = oldState;

                // Odczyt wejść
                boolean pBack = ctx.pBack();
                boolean pLeft = ctx.pLeft();
                boolean pRight = ctx.pRight();

                // ----------------------------------------------------
                // LOGIKA BRAMEK
                // ----------------------------------------------------

                switch (ctx.type()) {
                    case "LATCH" -> {
                        // RS Latch: right = Set, left = Reset.
                        if (pRight) newState = true;
                        else if (pLeft) newState = false;
                    }

                    case "MEMORY_CELL" -> {
                        // Tył = Data, prawo = Write, lewo = Read.
                        boolean memory = ctx.json().has("memory")
                                && ctx.json().get("memory").getAsBoolean();

                        // ZAPIS
                        if (pRight) {
                            memory = pBack;
                            ctx.json().addProperty("memory", memory);
                        }

                        // ODCZYT
                        newState = memory && pLeft;
                    }

                    case "TFF" -> {
                        // Toggle Flip-Flop: zbocze narastające na wejściu tylnym.
                        boolean lastIn = ctx.json().has("lastInput")
                                && ctx.json().get("lastInput").getAsBoolean();

                        if (pBack != lastIn) {
                            if (pBack) {
                                newState = !oldState;
                            }
                            ctx.json().addProperty("lastInput", pBack);
                        }
                    }
                }

                // ----------------------------------------------------
                // AKTUALIZACJA WYJŚCIA
                // ----------------------------------------------------

                JsonElement lastOut = ctx.json().get("current_out");
                boolean outputChanged = lastOut == null
                        || !lastOut.isJsonPrimitive()
                        || !lastOut.getAsJsonPrimitive().isBoolean()
                        || lastOut.getAsBoolean() != newState;

                // Zapisujemy wynik logiczny w polu current_out.
                if (outputChanged) {
                    ctx.json().addProperty("current_out", newState);
                }

                GateUtils.syncOutput(
                        plugin,
                        ctx.json(),
                        ctx,
                        ctx.key(),
                        newState,
                        outputChanged
                );

                // ----------------------------------------------------
                // CZĄSTECZKI STATUSU
                // ----------------------------------------------------

                if (OUT_GATES.contains(ctx.type())) {
                    GateUtils.spawnStatusParticle(
                            ctx.gateBlock(), ctx.dirs().out(), newState
                    );
                }

                if (SIDE_GATES.contains(ctx.type())) {
                    GateUtils.spawnStatusParticle(
                            ctx.gateBlock(), ctx.dirs().right(), pRight
                    );
                    GateUtils.spawnStatusParticle(
                            ctx.gateBlock(), ctx.dirs().left(), pLeft
                    );
                }

                if (BACK_GATES.contains(ctx.type())) {
                    GateUtils.spawnStatusParticle(
                            ctx.gateBlock(), ctx.dirs().back(), pBack
                    );
                }
            });
        }
    }
}