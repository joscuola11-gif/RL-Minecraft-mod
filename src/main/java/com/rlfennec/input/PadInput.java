package com.rlfennec.input;

import java.util.Locale;
import javax.annotation.Nullable;

/**
 * One physical gamepad input: a button or one direction of an axis (a stick direction or a trigger), using
 * GLFW's standard gamepad layout (so the same numbers mean "A", "left trigger"... on Xbox and PlayStation pads).
 *
 * <p>Text form (used in the config file): {@code button:0}, {@code axis:1:-} (left stick up), {@code axis:5:+}
 * (right trigger), {@code none}.</p>
 */
public record PadInput(Kind kind, int index, int sign) {
    public enum Kind { NONE, BUTTON, AXIS }

    public static final int BUTTON_COUNT = 15;
    public static final int AXIS_COUNT = 6;

    public static final PadInput NONE = new PadInput(Kind.NONE, 0, 0);

    private static final String[] BUTTON_NAMES = {
            "A", "B", "X", "Y", "LB", "RB", "Back", "Start", "Guide", "Left stick click", "Right stick click",
            "D-pad up", "D-pad right", "D-pad down", "D-pad left"
    };

    public static PadInput button(int index) {
        return new PadInput(Kind.BUTTON, index, 0);
    }

    /** @param sign +1 or -1: the direction of the axis (stick right / down, stick left / up). Triggers only go +. */
    public static PadInput axis(int index, int sign) {
        return new PadInput(Kind.AXIS, index, index >= 4 ? 1 : (sign < 0 ? -1 : 1));
    }

    public boolean isNone() {
        return kind == Kind.NONE;
    }

    /** @return the input, or {@code null} when the text is not valid. */
    @Nullable
    public static PadInput parse(String text) {
        if (text == null) {
            return null;
        }
        String[] parts = text.trim().toLowerCase(Locale.ROOT).split(":");
        try {
            switch (parts[0]) {
                case "none":
                    return parts.length == 1 ? NONE : null;
                case "button": {
                    int i = Integer.parseInt(parts[1]);
                    return parts.length == 2 && i >= 0 && i < BUTTON_COUNT ? button(i) : null;
                }
                case "axis": {
                    int i = Integer.parseInt(parts[1]);
                    if (i < 0 || i >= AXIS_COUNT) {
                        return null;
                    }
                    int sign = parts.length > 2 ? (parts[2].equals("-") ? -1 : parts[2].equals("+") ? 1 : 0) : 1;
                    return parts.length <= 3 && sign != 0 ? axis(i, sign) : null;
                }
                default:
                    return null;
            }
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return switch (kind) {
            case NONE -> "none";
            case BUTTON -> "button:" + index;
            case AXIS -> "axis:" + index + ":" + (sign < 0 ? "-" : "+");
        };
    }

    /** Label shown in the bindings screen (Xbox naming). */
    public String displayName() {
        switch (kind) {
            case BUTTON:
                return BUTTON_NAMES[index];
            case AXIS:
                switch (index) {
                    case GamepadMapper.AXIS_LEFT_TRIGGER:
                        return "LT";
                    case GamepadMapper.AXIS_RIGHT_TRIGGER:
                        return "RT";
                    default: {
                        String stick = index < 2 ? "Left stick " : "Right stick ";
                        boolean horizontal = index % 2 == 0;
                        return stick + (horizontal ? (sign > 0 ? "right" : "left") : (sign > 0 ? "down" : "up"));
                    }
                }
            default:
                return "-";
        }
    }
}
