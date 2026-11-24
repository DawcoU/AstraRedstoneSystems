package pl.dawcou.astrars.gates.types;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.bukkit.Bukkit;

import pl.dawcou.astrars.AstraRS;
import pl.dawcou.astrars.file.GateDataManager;
import pl.dawcou.astrars.gates.data.GateContextFactory;
import pl.dawcou.astrars.gates.data.GateValidator;
import pl.dawcou.astrars.gates.utils.GateUtils;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class NumberGates {

    private final AstraRS plugin;
    private final GateValidator validator;

    private static final Set<String> HANDLED_TYPES = Set.of(
            "NUMBER_SENDER", "COUNTER", "MATH", "COMPARATOR",
            "DECODER", "RANDOM_NUMBER", "DECIMAL_ACCUMULATOR", "DATA_LISTENER"
    );

    private static final Set<String> OUT_GATES = Set.of(
            "NUMBER_SENDER", "COUNTER", "MATH", "COMPARATOR",
            "DECODER", "RANDOM_NUMBER",
            "DECIMAL_ACCUMULATOR", "DATA_LISTENER"
    );

    private static final Set<String> SIDE_GATES = Set.of(
            "COUNTER", "MATH", "COMPARATOR", "DECIMAL_ACCUMULATOR"
    );

    private static final Set<String> BACK_GATES = Set.of(
            "NUMBER_SENDER", "DECODER", "RANDOM_NUMBER",
            "COUNTER", "DECIMAL_ACCUMULATOR", "DATA_LISTENER"
    );

    public NumberGates(AstraRS plugin, GateValidator validator) {
        this.plugin = plugin;
        this.validator = validator;
    }

    // --------------------------------------------------
    // GŁÓWNA PĘTLA
    // --------------------------------------------------

    public void runNumberGates() {
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

                    if (type.equals("COUNTER")
                            || type.equals("DECIMAL_ACCUMULATOR")) {

                        Long value = GateUtils.getNumberFrom(
                                ctx.dirs().backBlock(),
                                plugin
                        );

                        active = value != null
                                ? value > 0L
                                : ctx.pBack();

                    } else {
                        active = ctx.pBack();
                    }

                    GateUtils.spawnStatusParticle(
                            ctx.gateBlock(),
                            ctx.dirs().back(),
                            active
                    );
                }

                // Wejścia boczne.
                if (SIDE_GATES.contains(type)) {
                    boolean leftActive;
                    boolean rightActive;

                    switch (type) {
                        case "COUNTER" -> {
                            Long leftValue = GateUtils.getNumberFrom(
                                    ctx.dirs().leftBlock(),
                                    plugin
                            );

                            Long rightValue = GateUtils.getNumberFrom(
                                    ctx.dirs().rightBlock(),
                                    plugin
                            );

                            leftActive = leftValue != null
                                    ? leftValue > 0L
                                    : ctx.pLeft();

                            rightActive = rightValue != null
                                    ? rightValue > 0L
                                    : ctx.pRight();
                        }

                        case "MATH", "COMPARATOR" -> {
                            Double leftValue = GateUtils.getDoubleFrom(
                                    ctx.dirs().leftBlock(),
                                    plugin
                            );

                            Double rightValue = GateUtils.getDoubleFrom(
                                    ctx.dirs().rightBlock(),
                                    plugin
                            );

                            // Cząsteczki świecą na zielono tylko gdy są dane cyfrowe, ignorując zwykły redstone
                            leftActive = leftValue != null;
                            rightActive = rightValue != null;
                        }

                        // Boki akumulatora służą do resetowania.
                        case "DECIMAL_ACCUMULATOR" -> {
                            leftActive = ctx.pLeft();
                            rightActive = ctx.pRight();
                        }

                        default -> {
                            leftActive = ctx.pLeft();
                            rightActive = ctx.pRight();
                        }
                    }

                    GateUtils.spawnStatusParticle(
                            ctx.gateBlock(),
                            ctx.dirs().left(),
                            leftActive
                    );

                    GateUtils.spawnStatusParticle(
                            ctx.gateBlock(),
                            ctx.dirs().right(),
                            rightActive
                    );
                }

                // --------------------------------------------------
                // LOGIKA BRAMEK
                // --------------------------------------------------

                switch (type) {

                    // ==============================================
                    // NUMBER_SENDER — źródło liczb double
                    // ==============================================

                    case "NUMBER_SENDER" -> {
                        double storedValue = GateUtils.readDouble(
                                gateObj,
                                "value",
                                0.0
                        );

                        boolean enabled = ctx.pBack();

                        JsonElement result = enabled
                                ? new JsonPrimitive(storedValue)
                                : JsonNull.INSTANCE;

                        boolean outputChanged = GateUtils.setOutputIfChanged(
                                gateObj,
                                result
                        );

                        GateUtils.syncOutput(
                                plugin,
                                gateObj,
                                ctx,
                                key,
                                enabled,
                                outputChanged
                        );
                    }

                    // ==============================================
                    // COUNTER — liczby całkowite long
                    // ==============================================

                    case "COUNTER" -> {
                        Long inputBack = GateUtils.getNumberFrom(
                                ctx.dirs().backBlock(),
                                plugin
                        );

                        Long inputLeft = GateUtils.getNumberFrom(
                                ctx.dirs().leftBlock(),
                                plugin
                        );

                        boolean pB = ctx.pBack();
                        boolean pL = ctx.pLeft();
                        boolean pR = ctx.pRight();

                        long lastDataBack = GateUtils.readLong(
                                gateObj,
                                "last_data_back",
                                0L
                        );

                        long lastDataLeft = GateUtils.readLong(
                                gateObj,
                                "last_data_left",
                                0L
                        );

                        boolean lastBack = GateUtils.readBoolean(
                                gateObj,
                                "last_back",
                                false
                        );

                        boolean lastLeft = GateUtils.readBoolean(
                                gateObj,
                                "last_left",
                                false
                        );

                        boolean lastRight = GateUtils.readBoolean(
                                gateObj,
                                "last_right",
                                false
                        );

                        long count = GateUtils.readLong(
                                gateObj,
                                "count",
                                0L
                        );

                        long limit = GateUtils.readLong(
                                gateObj,
                                "score_limit",
                                15L
                        );

                        limit = Math.max(0L, limit);
                        count = Math.max(0L, Math.min(count, limit));

                        long previousCount = count;

                        // DODAWANIE:
                        // przyrost liczbowy albo impuls redstone.
                        if (inputBack != null) {
                            if (inputBack > lastDataBack) {
                                long delta = GateUtils.positiveDifference(
                                        inputBack,
                                        lastDataBack
                                );

                                long remaining = limit - count;

                                count = delta >= remaining
                                        ? limit
                                        : count + delta;

                            } else if (pB && !lastBack && count < limit) {
                                count++;
                            }

                        } else if (pB && !lastBack && count < limit) {
                            count++;
                        }

                        // ODEJMOWANIE:
                        // przyrost liczbowy albo impuls redstone.
                        if (inputLeft != null) {
                            if (inputLeft > lastDataLeft) {
                                long delta = GateUtils.positiveDifference(
                                        inputLeft,
                                        lastDataLeft
                                );

                                count = delta >= count
                                        ? 0L
                                        : count - delta;

                            } else if (pL && !lastLeft && count > 0L) {
                                count--;
                            }

                        } else if (pL && !lastLeft && count > 0L) {
                            count--;
                        }

                        // RESET.
                        if (pR && !lastRight) {
                            count = 0L;
                        }

                        boolean changed = count != previousCount;

                        if (changed && plugin.isDebugEnabled()) {
                            Bukkit.getConsoleSender().sendMessage(
                                    AstraRS.DEBUG_PREFIX
                                            + "§5COUNTER §7at §e"
                                            + key
                                            + " §7-> New counter state: §d§l"
                                            + count + "§7/§5" + limit
                            );
                        }

                        // Wynikiem licznika zawsze jest liczba long,
                        // także wtedy, gdy wynosi zero.
                        GateUtils.setLongIfChanged(gateObj, "count", count);

                        boolean outputChanged = GateUtils.setOutputIfChanged(
                                gateObj,
                                new JsonPrimitive(count)
                        );

                        boolean finalState = count > 0L;

                        GateUtils.syncOutput(
                                plugin,
                                gateObj,
                                ctx,
                                key,
                                finalState,
                                outputChanged
                        );

                        // Historia fizycznych wejść.
                        GateUtils.setBooleanIfChanged(gateObj, "last_back", pB);
                        GateUtils.setBooleanIfChanged(gateObj, "last_left", pL);
                        GateUtils.setBooleanIfChanged(gateObj, "last_right", pR);

                        // Historia danych aktualizuje się tylko wtedy,
                        // gdy wejście faktycznie dostarcza liczbę.
                        if (inputBack != null) {
                            GateUtils.setLongIfChanged(
                                    gateObj,
                                    "last_data_back",
                                    inputBack
                            );
                        }

                        if (inputLeft != null) {
                            GateUtils.setLongIfChanged(
                                    gateObj,
                                    "last_data_left",
                                    inputLeft
                            );
                        }
                    }

                    // ==============================================
                    // MATH — działania na liczbach double
                    // ==============================================

                    case "MATH" -> {
                        Double inputL = GateUtils.getDoubleFrom(
                                ctx.dirs().leftBlock(),
                                plugin
                        );

                        Double inputR = GateUtils.getDoubleFrom(
                                ctx.dirs().rightBlock(),
                                plugin
                        );

                        JsonElement resultElement = JsonNull.INSTANCE;

                        if (inputL != null && inputR != null) {
                            double vL = inputL;
                            double vR = inputR;

                            String mode = GateUtils.readString(
                                    gateObj,
                                    "mode",
                                    "ADD"
                            ).toUpperCase(Locale.ROOT);

                            double result = switch (mode) {
                                case "SUB" -> vL - vR;
                                case "MUL" -> vL * vR;
                                case "DIV" -> vR != 0.0 ? vL / vR : 0.0;
                                case "POW" -> Math.pow(vL, vR);
                                case "MOD" -> vR != 0.0 ? vL % vR : 0.0;
                                case "MIN" -> Math.min(vL, vR);
                                case "MAX" -> Math.max(vL, vR);
                                case "SQRT" -> Math.sqrt(vL);
                                case "ROOT" -> vR != 0.0 ? Math.pow(vL, 1.0 / vR) : 0.0;
                                case "AVG" -> (vL + vR) / 2.0;
                                case "ABS" -> Math.abs(vL);
                                case "HYPOT" -> Math.hypot(vL, vR);
                                default -> vL + vR; // ADD
                            };

                            // Dzielenie przez zero, przepełnienie oraz
                            // nieokreślone wyniki potęgowania nie są danymi.
                            if (Double.isFinite(result)) {
                                resultElement = new JsonPrimitive(result);
                            }
                        }

                        boolean outputChanged = GateUtils.setOutputIfChanged(
                                gateObj,
                                resultElement
                        );

                        boolean isActive = resultElement.isJsonPrimitive()
                                && resultElement.getAsJsonPrimitive().isNumber()
                                && resultElement.getAsDouble() != 0.0;

                        if (outputChanged && plugin.isDebugEnabled()) {
                            Bukkit.getConsoleSender().sendMessage(
                                    AstraRS.DEBUG_PREFIX
                                            + "§d§lMATH §e" + key
                                            + " §7result: §a§l"
                                            + resultElement
                            );
                        }

                        GateUtils.syncOutput(
                                plugin,
                                gateObj,
                                ctx,
                                key,
                                isActive,
                                outputChanged
                        );
                    }

                    // ==============================================
                    // DECIMAL_ACCUMULATOR — liczby całkowite long
                    // ==============================================

                    case "DECIMAL_ACCUMULATOR" -> {
                        boolean reset = ctx.pLeft() || ctx.pRight();

                        Long input = GateUtils.getNumberFrom(
                                ctx.dirs().backBlock(),
                                plugin
                        );

                        if (reset) {
                            boolean outputChanged = GateUtils.setOutputIfChanged(
                                    gateObj,
                                    new JsonPrimitive(0L)
                            );

                            GateUtils.setNullIfChanged(gateObj, "last_input");

                            GateUtils.syncOutput(
                                    plugin,
                                    gateObj,
                                    ctx,
                                    key,
                                    true,
                                    outputChanged
                            );

                        } else if (input == null
                                || input < 0L
                                || input > 9L) {

                            // Brak prawidłowej cyfry pozwala później
                            // ponownie przyjąć tę samą cyfrę.
                            GateUtils.setNullIfChanged(gateObj, "last_input");

                        } else {
                            Long lastInput = GateUtils.readLongOrNull(
                                    gateObj,
                                    "last_input"
                            );

                            // Jedno dopisanie na każdą zmianę cyfry.
                            // Stała wartość wejściowa nie jest dopisywana
                            // ponownie co tick.
                            if (lastInput == null || lastInput.longValue() != input) {
                                long currentValue = GateUtils.readLong(
                                        gateObj,
                                        "current_out",
                                        0L
                                );

                                try {
                                    long result = Math.addExact(
                                            Math.multiplyExact(
                                                    currentValue,
                                                    10L
                                            ),
                                            input
                                    );

                                    boolean outputChanged =
                                            GateUtils.setOutputIfChanged(
                                                    gateObj,
                                                    new JsonPrimitive(result)
                                            );

                                    GateUtils.setLongIfChanged(
                                            gateObj,
                                            "last_input",
                                            input
                                    );

                                    GateUtils.syncOutput(
                                            plugin,
                                            gateObj,
                                            ctx,
                                            key,
                                            true,
                                            outputChanged
                                    );

                                } catch (ArithmeticException ignored) {
                                    // Zapamiętujemy odrzuconą cyfrę, żeby
                                    // nie wykonywać tej samej nieudanej
                                    // operacji i nie logować jej co tick.
                                    GateUtils.setLongIfChanged(
                                            gateObj,
                                            "last_input",
                                            input
                                    );

                                    if (plugin.isDebugEnabled()) {
                                        Bukkit.getConsoleSender().sendMessage(
                                                AstraRS.DEBUG_PREFIX
                                                        + "§cDECIMAL_ACCUMULATOR §7at §e"
                                                        + key
                                                        + " §7overflow; value unchanged."
                                        );
                                    }
                                }
                            }
                        }
                    }

                    // ==============================================
                    // COMPARATOR — porównania liczb double
                    // ==============================================

                    case "COMPARATOR" -> {
                        Double leftInput = GateUtils.getDoubleFrom(
                                ctx.dirs().leftBlock(),
                                plugin
                        );

                        Double rightInput = GateUtils.getDoubleFrom(
                                ctx.dirs().rightBlock(),
                                plugin
                        );

                        JsonElement resultElement = JsonNull.INSTANCE;
                        boolean result = false;

                        // Bez obu liczb wynik nie istnieje.
                        if (leftInput != null && rightInput != null) {
                            int comparison = GateUtils.compareDoubles(
                                    leftInput,
                                    rightInput
                            );

                            String mode = GateUtils.readString(
                                    gateObj,
                                    "mode",
                                    "=="
                            );

                            result = switch (mode) {
                                case ">" -> comparison > 0;
                                case "<" -> comparison < 0;
                                case "==" -> comparison == 0;
                                case ">=" -> comparison >= 0;
                                case "<=" -> comparison <= 0;
                                case "!=" -> comparison != 0;
                                default -> false;
                            };

                            resultElement = new JsonPrimitive(result);
                        }

                        boolean outputChanged = GateUtils.setOutputIfChanged(
                                gateObj,
                                resultElement
                        );

                        if (outputChanged && plugin.isDebugEnabled()) {
                            Bukkit.getConsoleSender().sendMessage(
                                    AstraRS.DEBUG_PREFIX
                                            + "§2COMPARATOR §7at §e"
                                            + key + " §7result: "
                                            + (resultElement.isJsonNull()
                                            ? "§7NO DATA"
                                            : result ? "§aTRUE" : "§cFALSE")
                            );
                        }

                        GateUtils.syncOutput(
                                plugin,
                                gateObj,
                                ctx,
                                key,
                                result,
                                outputChanged
                        );
                    }

                    // ==============================================
                    // DECODER — dekodowanie wartości double
                    // ==============================================

                    case "DECODER" -> {
                        Double incoming = GateUtils.getDoubleFrom(
                                ctx.dirs().backBlock(),
                                plugin
                        );

                        double targetValue = GateUtils.readDouble(
                                gateObj,
                                "value",
                                0.0
                        );

                        boolean isMatch = incoming != null
                                && GateUtils.compareDoubles(incoming, targetValue) == 0;

                        JsonElement resultElement = incoming == null
                                ? JsonNull.INSTANCE
                                : new JsonPrimitive(isMatch);

                        boolean outputChanged = GateUtils.setOutputIfChanged(
                                gateObj,
                                resultElement
                        );

                        if (outputChanged && plugin.isDebugEnabled()) {
                            String status = incoming == null
                                    ? "§7NO DATA"
                                    : isMatch ? "§aMATCH" : "§cNO MATCH";

                            Bukkit.getConsoleSender().sendMessage(
                                    AstraRS.DEBUG_PREFIX
                                            + "§eDECODER §7at §e"
                                            + key
                                            + " §7(target: §6" + targetValue
                                            + "§7, received: §b" + incoming
                                            + "§7) -> " + status
                            );
                        }

                        GateUtils.syncOutput(
                                plugin,
                                gateObj,
                                ctx,
                                key,
                                isMatch,
                                outputChanged
                        );
                    }

                    // ==============================================
                    // RANDOM_NUMBER
                    // ==============================================

                    case "RANDOM_NUMBER" -> {
                        boolean input = ctx.pBack();

                        boolean lastInput = GateUtils.readBoolean(
                                gateObj,
                                "last_input",
                                false
                        );

                        // Losowanie wyłącznie na zboczu narastającym.
                        if (input && !lastInput) {
                            double min = GateUtils.readDouble(
                                    gateObj,
                                    "min",
                                    0.0
                            );

                            double max = GateUtils.readDouble(
                                    gateObj,
                                    "max",
                                    15.0
                            );

                            double value = GateUtils.randomDouble(min, max);

                            JsonElement resultElement = new JsonPrimitive(value);

                            boolean isActive = value != 0.0;

                            boolean outputChanged = GateUtils.setOutputIfChanged(
                                    gateObj,
                                    resultElement
                            );

                            GateUtils.syncOutput(
                                    plugin,
                                    gateObj,
                                    ctx,
                                    key,
                                    isActive,
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
                                "last_input",
                                input
                        );
                    }
                }
            });
        }
    }
}