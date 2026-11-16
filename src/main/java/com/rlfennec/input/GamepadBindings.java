package com.rlfennec.input;

import java.util.EnumMap;
import java.util.Map;

/** Which gamepad input triggers which {@link PadAction}. */
public final class GamepadBindings {
    private final Map<PadAction, PadInput> inputs = new EnumMap<>(PadAction.class);

    public GamepadBindings() {
        resetAll();
    }

    public PadInput get(PadAction action) {
        return inputs.get(action);
    }

    public void set(PadAction action, PadInput input) {
        inputs.put(action, input);
    }

    public void reset(PadAction action) {
        inputs.put(action, action.defaultInput());
    }

    public void resetAll() {
        for (PadAction action : PadAction.values()) {
            reset(action);
        }
    }

    public boolean isDefault(PadAction action) {
        return inputs.get(action).equals(action.defaultInput());
    }
}
