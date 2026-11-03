package com.puremusic.player.playback

import kotlin.random.Random

enum class PlaybackMode { SHUFFLE, REPEAT_ALL, REPEAT_ONE }

/**
 * Selects uniformly from every item in the active queue.
 * The current item is deliberately NOT excluded: consecutive repeats are valid
 * outcomes in a truly independent, equal-probability random draw.
 */
fun chooseRandomIndex(queueSize: Int, random: Random = Random.Default): Int {
    require(queueSize > 0) { "queueSize must be positive" }
    return random.nextInt(queueSize)
}
