package com.rlfennec.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GamepadMapperTest {
    private final GamepadBindings defaults = new GamepadBindings();

    private GamepadMapper.Controls map(GamepadMapper.State s) {
        return GamepadMapper.map(s, defaults);
    }

    @Test
    void restingPadDoesNothing() {
        GamepadMapper.Controls c = map(new GamepadMapper.State());
        assertEquals(0.0F, c.steer);
        assertEquals(0.0F, c.pitch);
        assertEquals(0.0F, c.throttle);
        assertEquals(0.0F, c.roll);
        assertFalse(c.jump || c.boost || c.airRollFree || c.enterExit);
    }

    @Test
    void stickDriftInsideTheDeadZoneIsIgnoredAndTheRimReachesOne() {
        GamepadMapper.State s = new GamepadMapper.State();
        s.axes[GamepadMapper.AXIS_LEFT_X] = 0.1F;
        s.axes[GamepadMapper.AXIS_LEFT_Y] = -0.1F;
        assertEquals(0.0F, map(s).steer);
        s.axes[GamepadMapper.AXIS_LEFT_X] = 1.0F;
        s.axes[GamepadMapper.AXIS_LEFT_Y] = 0.0F;
        assertEquals(1.0F, map(s).steer, 1e-6F);
        s.axes[GamepadMapper.AXIS_LEFT_X] = -1.0F;
        assertEquals(-1.0F, map(s).steer, 1e-6F);
    }

    @Test
    void stickForwardIsNoseDownLikeRocketLeague() {
        GamepadMapper.State s = new GamepadMapper.State();
        s.axes[GamepadMapper.AXIS_LEFT_Y] = -1.0F; // GLFW: up is negative
        assertEquals(1.0F, map(s).pitch, 1e-6F);
        s.axes[GamepadMapper.AXIS_LEFT_Y] = 1.0F;
        assertEquals(-1.0F, map(s).pitch, 1e-6F);
    }

    @Test
    void triggersAreThrottleAndBrake() {
        GamepadMapper.State s = new GamepadMapper.State();
        s.axes[GamepadMapper.AXIS_RIGHT_TRIGGER] = 1.0F;
        assertEquals(1.0F, map(s).throttle, 1e-6F);
        s.axes[GamepadMapper.AXIS_LEFT_TRIGGER] = 1.0F;
        assertEquals(0.0F, map(s).throttle, 1e-6F);
        s.axes[GamepadMapper.AXIS_RIGHT_TRIGGER] = 0.0F;
        assertEquals(-1.0F, map(s).throttle, 1e-6F);
        s.axes[GamepadMapper.AXIS_LEFT_TRIGGER] = 0.03F; // lightly resting finger
        assertEquals(0.0F, map(s).throttle, 1e-6F);
    }

    @Test
    void defaultButtons() {
        GamepadMapper.State s = new GamepadMapper.State();
        s.buttons[GamepadMapper.BUTTON_A] = true;
        s.buttons[GamepadMapper.BUTTON_B] = true;
        s.buttons[GamepadMapper.BUTTON_X] = true;
        s.buttons[GamepadMapper.BUTTON_Y] = true;
        s.buttons[GamepadMapper.BUTTON_LEFT_BUMPER] = true;
        GamepadMapper.Controls c = map(s);
        assertTrue(c.jump && c.boost && c.airRollFree && c.enterExit);
        assertEquals(-1.0F, c.roll);
        s.buttons[GamepadMapper.BUTTON_LEFT_BUMPER] = false;
        s.buttons[GamepadMapper.BUTTON_RIGHT_BUMPER] = true;
        assertEquals(1.0F, map(s).roll);
        s.buttons[GamepadMapper.BUTTON_LEFT_BUMPER] = true;
        assertEquals(0.0F, map(s).roll);
    }

    @Test
    void everyActionCanBeRebound() {
        GamepadBindings b = new GamepadBindings();
        b.set(PadAction.JUMP, PadInput.button(5));             // RB
        b.set(PadAction.BOOST, PadInput.axis(AXIS(5), 1));     // right trigger as a button
        b.set(PadAction.ACCELERATE, PadInput.button(0));       // A as the throttle
        b.set(PadAction.STEER_RIGHT, PadInput.axis(2, 1));     // right stick right
        b.set(PadAction.NOSE_UP, PadInput.button(11));         // d-pad up

        GamepadMapper.State s = new GamepadMapper.State();
        s.buttons[5] = true;
        s.buttons[0] = true;
        s.buttons[11] = true;
        s.axes[2] = 1.0F;
        s.axes[5] = 1.0F;
        GamepadMapper.Controls c = GamepadMapper.map(s, b);
        assertTrue(c.jump);
        assertTrue(c.boost);
        assertEquals(1.0F, c.throttle);
        assertEquals(1.0F, c.steer, 1e-6F);
        assertEquals(-1.0F, c.pitch);
        // the old default of the jump button no longer does anything
        s = new GamepadMapper.State();
        s.buttons[GamepadMapper.BUTTON_A] = true;
        assertFalse(GamepadMapper.map(s, b).jump);
        // ... A is the throttle now
        assertEquals(1.0F, GamepadMapper.map(s, b).throttle);
    }

    private static int AXIS(int i) {
        return i;
    }

    @Test
    void unboundActionsDoNothing() {
        GamepadBindings b = new GamepadBindings();
        b.set(PadAction.JUMP, PadInput.NONE);
        GamepadMapper.State s = new GamepadMapper.State();
        s.buttons[GamepadMapper.BUTTON_A] = true;
        assertFalse(GamepadMapper.map(s, b).jump);
    }

    @Test
    void anAxisBoundToAnActionOnlyCountsInItsOwnDirection() {
        GamepadBindings b = new GamepadBindings();
        b.set(PadAction.BOOST, PadInput.axis(GamepadMapper.AXIS_RIGHT_Y, -1)); // right stick up
        GamepadMapper.State s = new GamepadMapper.State();
        s.axes[GamepadMapper.AXIS_RIGHT_Y] = 1.0F; // down
        assertFalse(GamepadMapper.map(s, b).boost);
        s.axes[GamepadMapper.AXIS_RIGHT_Y] = -1.0F; // up
        assertTrue(GamepadMapper.map(s, b).boost);
    }

    @Test
    void detectPressedCapturesButtonsSticksAndTriggers() {
        GamepadMapper.State s = new GamepadMapper.State();
        assertNull(GamepadMapper.detectPressed(s));

        s.buttons[3] = true;
        assertEquals(PadInput.button(3), GamepadMapper.detectPressed(s));
        s.buttons[3] = false;

        s.axes[GamepadMapper.AXIS_RIGHT_X] = -0.9F;
        assertEquals(PadInput.axis(GamepadMapper.AXIS_RIGHT_X, -1), GamepadMapper.detectPressed(s));
        s.axes[GamepadMapper.AXIS_RIGHT_X] = 0.3F; // below the threshold: just a wobble
        assertNull(GamepadMapper.detectPressed(s));
        s.axes[GamepadMapper.AXIS_RIGHT_X] = 0.0F;

        s.axes[GamepadMapper.AXIS_LEFT_TRIGGER] = 0.8F;
        assertEquals(PadInput.axis(GamepadMapper.AXIS_LEFT_TRIGGER, 1), GamepadMapper.detectPressed(s));
    }

    @Test
    void textFormRoundTripsAndRejectsGarbage() {
        for (PadAction action : PadAction.values()) {
            assertEquals(action.defaultInput(), PadInput.parse(action.defaultInput().toString()), action.name());
        }
        assertEquals(PadInput.NONE, PadInput.parse("none"));
        assertEquals(PadInput.button(14), PadInput.parse(" Button:14 "));
        assertEquals(PadInput.axis(1, -1), PadInput.parse("axis:1:-"));
        assertNull(PadInput.parse("button:15"));
        assertNull(PadInput.parse("axis:6:+"));
        assertNull(PadInput.parse("axis:1:*"));
        assertNull(PadInput.parse("banana"));
        assertNull(PadInput.parse(""));
        assertNull(PadInput.parse(null));
        assertNotNull(PadInput.parse("axis:4"));
    }

    @Test
    void displayNames() {
        assertEquals("A", PadInput.button(0).displayName());
        assertEquals("RT", PadInput.axis(5, 1).displayName());
        assertEquals("LT", PadInput.axis(4, 1).displayName());
        assertEquals("Left stick up", PadInput.axis(1, -1).displayName());
        assertEquals("Left stick left", PadInput.axis(0, -1).displayName());
        assertEquals("Right stick down", PadInput.axis(3, 1).displayName());
    }

    @Test
    void triggersThatReportZeroUntilTouchedAreNotTreatedAsHalfPressed() {
        assertEquals(0.0F, GamepadMapper.normaliseTrigger(0.0F, false));
        assertEquals(0.5F, GamepadMapper.normaliseTrigger(0.0F, true));
        assertEquals(0.0F, GamepadMapper.normaliseTrigger(-1.0F, true));
        assertEquals(1.0F, GamepadMapper.normaliseTrigger(1.0F, true));
    }

    @Test
    void bindingsResetToTheDefaults() {
        GamepadBindings b = new GamepadBindings();
        b.set(PadAction.JUMP, PadInput.button(7));
        assertFalse(b.isDefault(PadAction.JUMP));
        b.reset(PadAction.JUMP);
        assertTrue(b.isDefault(PadAction.JUMP));
        b.set(PadAction.BOOST, PadInput.NONE);
        b.resetAll();
        assertTrue(b.isDefault(PadAction.BOOST));
    }
}
