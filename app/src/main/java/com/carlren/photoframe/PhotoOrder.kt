package com.carlren.photoframe

import kotlin.random.Random

/** Shuffles a slideshow pass while avoiding an immediate repeat between passes. */
internal fun <T> randomizedPhotoOrder(
    photos: List<T>,
    previouslyShown: T? = null,
    random: Random = Random.Default
): List<T> {
    if (photos.size < 2) return photos.toList()

    val shuffled = photos.shuffled(random).toMutableList()
    if (previouslyShown != null && shuffled.first() == previouslyShown) {
        val replacementIndex = shuffled.indexOfFirst { it != previouslyShown }
        if (replacementIndex > 0) {
            val replacement = shuffled[replacementIndex]
            shuffled[replacementIndex] = shuffled[0]
            shuffled[0] = replacement
        }
    }
    return shuffled
}
