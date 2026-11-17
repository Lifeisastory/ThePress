package com.aster.core.design_system.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationBarDragDynamicsTest {
    @Test
    fun accelerationStretchesAndBrakingCompresses() {
        val dynamics = NavigationBarDragDynamics()
        assertTrue(dynamics.sample(0.1f, 16) > 0f)
        repeat(40) { dynamics.sample(0.1f, 16) }
        assertEquals(0f, dynamics.sample(0.1f, 16), 0.001f)
        assertTrue(dynamics.sample(0f, 16) < 0f)
    }

    @Test
    fun rapidDirectionChangesRemainBounded() {
        val dynamics = NavigationBarDragDynamics()
        repeat(100) { index ->
            val stretch = dynamics.sample(if (index % 2 == 0) 10f else -10f, 1)
            assertTrue(stretch.isFinite() && stretch in -0.12f..0.18f)
        }
        assertEquals(0f, dynamics.sample(1f, 0), 0f)
        assertEquals(0f, dynamics.sample(Float.NaN, 16), 0f)
    }

    @Test
    fun pauseAndResetDiscardPreviousVelocity() {
        val dynamics = NavigationBarDragDynamics()
        repeat(10) { dynamics.sample(1f, 16) }
        assertEquals(0f, dynamics.sample(1f, 200), 0f)
        val freshResponse = NavigationBarDragDynamics().sample(0.01f, 16)
        assertEquals(freshResponse, dynamics.sample(0.01f, 16), 0f)
        dynamics.reset()
        assertEquals(freshResponse, dynamics.sample(0.01f, 16), 0f)
    }

    @Test
    fun edgePullIsSymmetricBoundedAndProgressivelyResisted() {
        val limit = 24f
        assertEquals(0f, dampedGlassEdgePull(0f, limit), 0f)
        val near = dampedGlassEdgePull(20f, limit)
        val middle = dampedGlassEdgePull(40f, limit)
        val far = dampedGlassEdgePull(60f, limit)
        assertTrue(near > 0f && near < middle && middle < far && far < limit)
        assertTrue(far - middle < middle - near)
        assertEquals(-middle, dampedGlassEdgePull(-40f, limit), 0f)
        assertTrue(dampedGlassEdgePull(100_000f, limit) <= limit)
    }
}
