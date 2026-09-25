package pl.dawcou.astrars.gates.types;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;

import pl.dawcou.astrars.AstraRS;
import pl.dawcou.astrars.file.GateDataManager;
import pl.dawcou.astrars.gates.data.GateContext;
import pl.dawcou.astrars.gates.data.GateContextFactory;
import pl.dawcou.astrars.gates.data.GateValidator;
import pl.dawcou.astrars.gates.utils.GateUtils;

import java.util.Map;
import java.util.Set;

public class StringGates {
    private final AstraRS plugin;
    private final GateValidator validator;

    private static final Set<String> OUT_GATES = Set.of("STRING_GATE", "STRING_COMPARATOR", "STRING_DECODER");
    private static final Set<String> BACK_GATES = Set.of("STRING_GATE", "STRING_DECODER");

    public StringGates(AstraRS plugin, GateValidator validator) {
        this.plugin = plugin;
        this.validator = validator;
    }

    public void runStringGates() {
        GateDataManager manager = plugin.getGateDataManager();
        JsonObject gatesSection = manager.getGates();
        if (gatesSection == null) return;

        for (Map.Entry<String, JsonElement> entry : gatesSection.entrySet()) {
            GateContext ctx = GateContextFactory.create(entry, validator);
            if (ctx == null) continue;

            JsonObject gateObj = ctx.json();
            String type = ctx.type();
            String key = ctx.key();

            String sL = "";
            String sR = "";
            String sBack = "";

            if (OUT_GATES.contains(type)) {
                GateUtils.spawnStatusParticle(ctx.gateBlock(), ctx.dirs().out(), ctx.currentState());
            }

            if (BACK_GATES.contains(type)) {
                sBack = GateUtils.getStringFrom(ctx.dirs().backBlock(), plugin);
                GateUtils.spawnStatusParticle(ctx.gateBlock(), ctx.dirs().back(), ctx.pBack() || !sBack.isEmpty());
            }

            if (type.equals("STRING_COMPARATOR")) {
                sL = GateUtils.getStringFrom(ctx.dirs().leftBlock(), plugin);
                sR = GateUtils.getStringFrom(ctx.dirs().rightBlock(), plugin);
                GateUtils.spawnStatusParticle(ctx.gateBlock(), ctx.dirs().left(), !sL.isEmpty());
                GateUtils.spawnStatusParticle(ctx.gateBlock(), ctx.dirs().right(), !sR.isEmpty());
            }

            switch (type) {
                case "STRING_GATE" -> {
                    String storedText = gateObj.has("value") ? gateObj.get("value").getAsString() : "";
                    String finalText = ctx.pBack() ? storedText : "";
                    String lastOut = gateObj.has("current_out") ? gateObj.get("current_out").getAsString() : "";

                    if (!finalText.equals(lastOut)) {
                        if (plugin.isDebugMode()) {
                            Bukkit.getConsoleSender().sendMessage(
                                    AstraRS.DEBUG_PREFIX + "§dSTRING_GATE §7at §e" + key + " §7emitted text: §5\"" + finalText + "\""
                            );
                        }
                        gateObj.addProperty("current_out", finalText);
                        gateObj.addProperty("state", ctx.pBack());
                        GateUtils.updateOutput(plugin, key, ctx.dirs().targetBlock(), ctx.pBack());
                    }
                }

                case "STRING_COMPARATOR" -> {
                    String mode = gateObj.has("mode") ? gateObj.get("mode").getAsString().toUpperCase() : "EQUALS";

                    if (sL.isEmpty()) {
                        String currentOut = gateObj.has("current_out") ? gateObj.get("current_out").getAsString() : "";

                        if (!currentOut.isEmpty()) {
                            gateObj.addProperty("current_out", "");
                            gateObj.addProperty("state", false);
                            GateUtils.updateOutput(plugin, key, ctx.dirs().targetBlock(), false);
                        }
                        continue;
                    }

                    boolean result = switch (mode) {
                        case "EQUALS"              -> sL.equals(sR);
                        case "EQUALS_IGNORE_CASE"  -> sL.equalsIgnoreCase(sR);
                        case "CONTAINS"            -> sL.contains(sR);
                        case "STARTS_WITH"         -> sL.startsWith(sR);
                        case "ENDS_WITH"           -> sL.endsWith(sR);
                        default                    -> false;
                    };

                    String finalVal = result ? sL : "";
                    String lastOutStr = gateObj.has("current_out") ? gateObj.get("current_out").getAsString() : "";

                    if (!finalVal.equals(lastOutStr)) {
                        if (plugin.isDebugMode()) {
                            String matchColor = result ? "§a" : "§c";
                            Bukkit.getConsoleSender().sendMessage(
                                    AstraRS.DEBUG_PREFIX + "§dSTRING_COMP §7at §e" + key + " §7result (§6" + mode + "§7): " + matchColor + (result ? "TRUE" : "FALSE") + " §8[Sending: \"" + finalVal + "\"]"
                            );
                        }
                        gateObj.addProperty("current_out", finalVal);
                        gateObj.addProperty("state", result);

                        GateUtils.updateOutput(plugin, key, ctx.dirs().targetBlock(), result);
                    }
                }

                case "STRING_DECODER" -> {
                    String targetValue = gateObj.has("value") ? gateObj.get("value").getAsString() : "";

                    boolean isMatch = !sBack.isEmpty() && sBack.equals(targetValue);
                    String finalVal = isMatch ? "1" : "";
                    String lastOut = gateObj.has("current_out") ? gateObj.get("current_out").getAsString() : "";

                    if (!finalVal.equals(lastOut)) {
                        if (plugin.isDebugMode()) {
                            Bukkit.getConsoleSender().sendMessage(
                                    AstraRS.DEBUG_PREFIX + "§dSTRING_DECODER §7at §e" + key + " §7matched: §a" + isMatch
                            );
                        }
                        gateObj.addProperty("current_out", finalVal);
                        gateObj.addProperty("state", isMatch);
                        GateUtils.updateOutput(plugin, key, ctx.dirs().targetBlock(), isMatch);
                    }
                }
            }
        }
    }
}