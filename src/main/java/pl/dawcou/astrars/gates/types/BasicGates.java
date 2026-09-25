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

public class BasicGates {
    private final AstraRS plugin;
    private final GateValidator validator;

    // Statyczne zbiory do błyskawicznego sprawdzenia wywoływanego w O(1)
    private static final Set<String> OUT_GATES = Set.of(
            "OR", "NOR", "AND", "NAND", "XOR", "XNOR", "NOT", "BUFFER", "IMPLY", "NIMPLY", "MUX", "PULSER"
    );

    private static final Set<String> BACK_GATES = Set.of(
            "NOT", "BUFFER", "IMPLY", "NIMPLY", "MUX", "PULSER"
    );

    private static final Set<String> SIDE_GATES = Set.of(
            "OR", "NOR", "AND", "NAND", "XOR", "XNOR", "IMPLY", "NIMPLY", "MUX"
    );

    public BasicGates(AstraRS plugin, GateValidator validator) {
        this.plugin = plugin;
        this.validator = validator;
    }

    public void runBasicGates() {
        GateDataManager manager = plugin.getGateDataManager();
        JsonObject gatesSection = manager.getGates();
        if (gatesSection == null) return;

        for (Map.Entry<String, JsonElement> entry : gatesSection.entrySet()) {
            GateContext ctx = GateContextFactory.create(entry, validator);
            if (ctx == null) continue;

            // --- PARTICLE STATUSU (WEJŚCIA) ---
            // 1. Wejścia boczne (lewo / prawo)
            if (SIDE_GATES.contains(ctx.type())) {
                GateUtils.spawnStatusParticle(ctx.gateBlock(), ctx.dirs().left(), ctx.pLeft());
                GateUtils.spawnStatusParticle(ctx.gateBlock(), ctx.dirs().right(), ctx.pRight());
            }

            // 2. Wejście z tyłu (dedykowane bramki LUB zwykłe bramki w trybie 3-wejściowym)
            if (BACK_GATES.contains(ctx.type()) || (ctx.inputs() == 3)) {
                GateUtils.spawnStatusParticle(ctx.gateBlock(), ctx.dirs().back(), ctx.pBack());
            }

            // LOGIKA
            boolean newState = switch (ctx.type()) {
                case "NOT" -> !ctx.pBack();
                case "OR" -> (ctx.inputs() == 3) ? (ctx.pRight() || ctx.pLeft() || ctx.pBack()) : (ctx.pRight() || ctx.pLeft());
                case "NOR" -> (ctx.inputs() == 3) ? !(ctx.pRight() || ctx.pLeft() || ctx.pBack()) : !(ctx.pRight() || ctx.pLeft());
                case "AND" -> (ctx.inputs() == 3) ? (ctx.pRight() && ctx.pLeft() && ctx.pBack()) : (ctx.pRight() && ctx.pLeft());
                case "NAND" -> (ctx.inputs() == 3) ? !(ctx.pRight() && ctx.pLeft() && ctx.pBack()) : !(ctx.pRight() && ctx.pLeft());
                case "XOR" -> (ctx.inputs() == 3) ? (ctx.pRight() ^ ctx.pLeft() ^ ctx.pBack()) : (ctx.pRight() ^ ctx.pLeft());
                case "XNOR" -> (ctx.inputs() == 3) ? (ctx.pRight() ^ ctx.pLeft()) == ctx.pBack() : (ctx.pRight() == ctx.pLeft());
                case "IMPLY" -> !ctx.pBack() || (ctx.pRight() || ctx.pLeft());
                case "NIMPLY" -> ctx.pBack() && !(ctx.pRight() || ctx.pLeft());
                case "BUFFER" -> ctx.pBack();
                case "MUX" -> ctx.pBack() ? ctx.pRight() : ctx.pLeft();

                default -> ctx.currentState();
            };

            // --- AKTUALIZACJA ---
            if (newState != ctx.currentState()) {
                GateUtils.updateOutput(plugin, ctx.key(), ctx.dirs().targetBlock(), newState);
                ctx.json().addProperty("state", newState);
            }

            // 3. JEDYNE I SŁUSZNE MIEJSCE NA DYMEK Z PRZODU NA BAZIE AKTUALNEGO newState
            if (OUT_GATES.contains(ctx.type())) {
                GateUtils.spawnStatusParticle(ctx.gateBlock(), ctx.dirs().out(), newState);
            }
        }
    }
}