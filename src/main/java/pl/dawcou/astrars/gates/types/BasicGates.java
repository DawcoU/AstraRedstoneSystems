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

public class BasicGates {

    private final AstraRS plugin;
    private final GateValidator validator;

    private static final Set<String> HANDLED_TYPES = Set.of(
            "OR", "NOR", "AND", "NAND", "XOR", "XNOR",
            "NOT", "BUFFER", "IMPLY", "NIMPLY", "MUX", "PULSER"
    );

    private static final Set<String> OUT_GATES = Set.of(
            "OR", "NOR", "AND", "NAND", "XOR", "XNOR",
            "NOT", "BUFFER", "IMPLY", "NIMPLY", "MUX", "PULSER"
    );

    private static final Set<String> BACK_GATES = Set.of(
            "NOT", "BUFFER", "IMPLY", "NIMPLY", "MUX", "PULSER"
    );

    private static final Set<String> SIDE_GATES = Set.of(
            "OR", "NOR", "AND", "NAND", "XOR", "XNOR",
            "IMPLY", "NIMPLY", "MUX"
    );

    public BasicGates(AstraRS plugin, GateValidator validator) {
        this.plugin = plugin;
        this.validator = validator;
    }

    // ============================================================
    // GŁÓWNA PĘTLA BRAMEK LOGICZNYCH
    // ============================================================

    public void runBasicGates() {
        GateDataManager manager = plugin.getGateDataManager();
        JsonObject gatesSection = manager.getGates();

        if (gatesSection == null) return;

        for (Map.Entry<String, JsonElement> entry : gatesSection.entrySet()) {
            GateContextFactory.createAndRun(entry, validator, plugin, ctx -> {
                if (ctx == null) return;

                // Jeśli typ bramki nie pasuje do naszej listy — wypad z pętli
                if (!HANDLED_TYPES.contains(ctx.type())) return;

                // ----------------------------------------------------
                // ODCZYT WEJŚĆ
                // ----------------------------------------------------

                boolean pBack = ctx.pBack();
                boolean pLeft = ctx.pLeft();
                boolean pRight = ctx.pRight();

                // ----------------------------------------------------
                // CZĄSTECZKI STATUSU WEJŚĆ
                // ----------------------------------------------------

                // Wejścia boczne: lewo i prawo.
                if (SIDE_GATES.contains(ctx.type())) {
                    GateUtils.spawnStatusParticle(
                            ctx.gateBlock(),
                            ctx.dirs().left(),
                            pLeft
                    );

                    GateUtils.spawnStatusParticle(
                            ctx.gateBlock(),
                            ctx.dirs().right(),
                            pRight
                    );
                }

                // Wejście tylne: dedykowane bramki lub tryb 3-wejściowy.
                if (BACK_GATES.contains(ctx.type()) || ctx.inputs() == 3) {
                    GateUtils.spawnStatusParticle(
                            ctx.gateBlock(),
                            ctx.dirs().back(),
                            pBack
                    );
                }

                // ----------------------------------------------------
                // LOGIKA BRAMEK
                // ----------------------------------------------------

                boolean newState = switch (ctx.type()) {
                    case "NOT" -> !pBack;

                    case "OR" -> (ctx.inputs() == 3)
                            ? (pRight || pLeft || pBack)
                            : (pRight || pLeft);

                    case "NOR" -> (ctx.inputs() == 3)
                            ? !(pRight || pLeft || pBack)
                            : !(pRight || pLeft);

                    case "AND" -> (ctx.inputs() == 3)
                            ? (pRight && pLeft && pBack)
                            : (pRight && pLeft);

                    case "NAND" -> (ctx.inputs() == 3)
                            ? !(pRight && pLeft && pBack)
                            : !(pRight && pLeft);

                    case "XOR" -> (ctx.inputs() == 3)
                            ? (pRight ^ pLeft ^ pBack)
                            : (pRight ^ pLeft);

                    case "XNOR" -> (ctx.inputs() == 3)
                            ? (pRight ^ pLeft) == pBack
                            : (pRight == pLeft);

                    case "IMPLY" -> !pBack || (pRight || pLeft);

                    case "NIMPLY" -> pBack && !(pRight || pLeft);

                    case "BUFFER" -> pBack;

                    case "MUX" -> pBack ? pRight : pLeft;

                    default -> ctx.currentState();
                };


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
                // CZĄSTECZKI STATUSU WYJŚCIA
                // ----------------------------------------------------

                if (OUT_GATES.contains(ctx.type())) {
                    GateUtils.spawnStatusParticle(
                            ctx.gateBlock(),
                            ctx.dirs().out(),
                            newState
                    );
                }
            });
        }
    }
}