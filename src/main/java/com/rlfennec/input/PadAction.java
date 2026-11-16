package com.rlfennec.input;

import java.util.Locale;

/** Everything the car can be told to do from a gamepad, with the default binding of each. */
public enum PadAction {
    ACCELERATE(PadInput.axis(GamepadMapper.AXIS_RIGHT_TRIGGER, 1)),
    BRAKE(PadInput.axis(GamepadMapper.AXIS_LEFT_TRIGGER, 1)),
    STEER_LEFT(PadInput.axis(GamepadMapper.AXIS_LEFT_X, -1)),
    STEER_RIGHT(PadInput.axis(GamepadMapper.AXIS_LEFT_X, 1)),
    /** In the air; stick forward = nose down, like Rocket League. */
    NOSE_DOWN(PadInput.axis(GamepadMapper.AXIS_LEFT_Y, -1)),
    NOSE_UP(PadInput.axis(GamepadMapper.AXIS_LEFT_Y, 1)),
    JUMP(PadInput.button(GamepadMapper.BUTTON_A)),
    BOOST(PadInput.button(GamepadMapper.BUTTON_B)),
    AIR_ROLL_FREE(PadInput.button(GamepadMapper.BUTTON_X)),
    ROLL_LEFT(PadInput.button(GamepadMapper.BUTTON_LEFT_BUMPER)),
    ROLL_RIGHT(PadInput.button(GamepadMapper.BUTTON_RIGHT_BUMPER)),
    /** Get into the nearest Fennec / get out of the one you drive. */
    ENTER_EXIT(PadInput.button(GamepadMapper.BUTTON_Y));

    private final PadInput defaultInput;

    PadAction(PadInput defaultInput) {
        this.defaultInput = defaultInput;
    }

    public PadInput defaultInput() {
        return defaultInput;
    }

    /** Key used in the config file. */
    public String configKey() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "gui.rlfennec.pad.action." + configKey();
    }
}
