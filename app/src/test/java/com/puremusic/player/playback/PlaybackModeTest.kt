package com.puremusic.player.playback

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackModeTest {
    @Test fun oneItemQueueAlwaysReturnsZero() {
        repeat(100) { assertEquals(0, chooseRandomIndex(1, Random(it))) }
    }

    @Test fun everyIndexCanBeSelectedAndDistributionIsApproximatelyUniform() {
        val counts = IntArray(4)
        val random = Random(20261104)
        repeat(100_000) { counts[chooseRandomIndex(counts.size, random)]++ }
        assertTrue(counts.all { it in 24_000..26_000 })
    }

    @Test fun randomMaySelectTheSameIndexConsecutively() {
        val alwaysZero = object : Random() {
            override fun nextBits(bitCount: Int): Int = 0
        }
        assertEquals(0, chooseRandomIndex(4, alwaysZero))
        assertEquals(0, chooseRandomIndex(4, alwaysZero))
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyQueueIsRejected() {
        chooseRandomIndex(0)
    }
}
