package com.rlfennec.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GamepadMapperTest {
    @Test
    void restingPadDoesNothing() {
        GamepadMapper.Controls c = GamepadMapper.map(new GamepadMapper.State());
        assertEquals(0.0F, c.steer);
        assertEquals(0.0F, c.pitch);
        assertEquals(0.0F, c.throttle);
        assertEquals(0.0F, c.roll);
        assertFalse(c.jump || c.boost || c.airRollFree || c.enterExit);
    }

    @Test
    void stickDriftInsideTheDeadZoneIsIgnoredAndTheRimReachesOne() {
        GamepadMapper.State s = new GamepadMapper.State();
        s.leftX = 0.1F;
        s.leftY = -0.1F;
        assertEquals(0.0F, GamepadMapper.map(s).steer);
        s.leftX = 1.0F;
        s.leftY = 0.0F;
        assertEquals(1.0F, GamepadMapper.map(s).steer, 1e-6F);
        s.leftX = -1.0F;
        assertEquals(-1.0F, GamepadMapper.map(s).steer, 1e-6F);
    }

    @Test
    void stickForwardIsNoseDownLikeRocketLeague() {
        GamepadMapper.State s = new GamepadMapper.State();
        s.leftY = -1.0F; // GLFW: up is negative
        assertEquals(1.0F, GamepadMapper.map(s).pitch, 1e-6F);
        s.leftY = 1.0F;
        assertEquals(-1.0F, GamepadMapper.map(s).pitch, 1e-6F);
    }

    @Test
    void triggersAreThrottleAndBrake() {
        GamepadMapper.State s = new GamepadMapper.State();
        s.rightTrigger = 1.0F;
        assertEquals(1.0F, GamepadMapper.map(s).throttle, 1e-6F);
        s.leftTrigger = 1.0F;
        assertEquals(0.0F, GamepadMapper.map(s).throttle, 1e-6F);
        s.rightTrigger = 0.0F;
        assertEquals(-1.0F, GamepadMapper.map(s).throttle, 1e-6F);
        s.leftTrigger = 0.03F; // lightly resting finger
        assertEquals(0.0F, GamepadMapper.map(s).throttle, 1e-6F);
    }

    @Test
    void buttons() {
        GamepadMapper.State s = new GamepadMapper.State();
        s.a = true;
        s.b = true;
        s.x = true;
        s.y = true;
        s.leftBumper = true;
        GamepadMapper.Controls c = GamepadMapper.map(s);
        assertTrue(c.jump && c.boost && c.airRollFree && c.enterExit);
        assertEquals(-1.0F, c.roll);
        s.leftBumper = false;
        s.rightBumper = true;
        assertEquals(1.0F, GamepadMapper.map(s).roll);
        s.leftBumper = true;
        assertEquals(0.0F, GamepadMapper.map(s).roll);
    }

    @Test
    void triggersThatReportZeroUntilTouchedAreNotTreatedAsHalfPressed() {
        assertEquals(0.0F, GamepadMapper.normaliseTrigger(0.0F, false));
        assertEquals(0.5F, GamepadMapper.normaliseTrigger(0.0F, true));
        assertEquals(0.0F, GamepadMapper.normaliseTrigger(-1.0F, true));
        assertEquals(1.0F, GamepadMapper.normaliseTrigger(1.0F, true));
    }
}
