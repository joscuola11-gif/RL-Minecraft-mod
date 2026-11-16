package com.rlfennec.registry;

import com.rlfennec.input.GamepadBindings;
import com.rlfennec.input.PadAction;
import com.rlfennec.input.PadInput;
import java.util.EnumMap;
import java.util.Map;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Per-player settings: which gamepad input does what (config/rlfennec-client.toml). */
public final class ClientConfig {
    public static final ModConfigSpec SPEC;

    private static final Map<PadAction, ModConfigSpec.ConfigValue<String>> VALUES = new EnumMap<>(PadAction.class);
    private static volatile GamepadBindings cache;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Gamepad bindings. A value is \"button:N\" (0 A, 1 B, 2 X, 3 Y, 4 LB, 5 RB, 6 Back, 7 Start, 8 Guide,",
                "9 left stick click, 10 right stick click, 11-14 d-pad up/right/down/left), \"axis:N:+\" or \"axis:N:-\"",
                "(0 left stick X, 1 left stick Y, 2 right stick X, 3 right stick Y, 4 left trigger, 5 right trigger;",
                "+ = right / down, - = left / up) or \"none\". Easier: use the in-game screen (Mods > Rocket League Fennec > Config,",
                "or the button at the top left of Options > Controls > Key Binds).")
                .push("controller");
        for (PadAction action : PadAction.values()) {
            VALUES.put(action, builder.define(action.configKey(), action.defaultInput().toString(),
                    value -> value instanceof String text && PadInput.parse(text) != null));
        }
        builder.pop();
        SPEC = builder.build();
    }

    private ClientConfig() {
    }

    /** The current bindings (the defaults until the config file is loaded). Treat as read-only. */
    public static GamepadBindings bindings() {
        GamepadBindings bindings = cache;
        if (bindings == null) {
            bindings = new GamepadBindings();
            if (SPEC.isLoaded()) {
                for (PadAction action : PadAction.values()) {
                    PadInput input = PadInput.parse(VALUES.get(action).get());
                    bindings.set(action, input != null ? input : action.defaultInput());
                }
            }
            cache = bindings;
        }
        return bindings;
    }

    /** Called when the file was (re)loaded. */
    public static void invalidate() {
        cache = null;
    }

    public static void set(PadAction action, PadInput input) {
        if (SPEC.isLoaded()) {
            VALUES.get(action).set(input.toString());
        }
        invalidate();
        if (!SPEC.isLoaded()) {
            // not loaded yet (should not happen in game): keep the change in memory only
            GamepadBindings bindings = bindings();
            bindings.set(action, input);
        }
    }

    public static void resetAll() {
        for (PadAction action : PadAction.values()) {
            set(action, action.defaultInput());
        }
    }

    public static void save() {
        if (SPEC.isLoaded()) {
            SPEC.save();
        }
    }
}
