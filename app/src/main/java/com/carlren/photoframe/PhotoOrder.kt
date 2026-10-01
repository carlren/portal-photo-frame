package com.carlren.photoframe

import kotlin.random.Random

/** Preserve the active pass during refresh; append new photos without restarting it. */
internal fun <T> reconcilePhotoOrder(current: List<T>, available: List<T>): List<T> {
    val availableSet = available.toSet()
    val retained = current.filter { it in availableSet }
    val currentSet = current.toSet()
    return retained + available.filterNot { it in currentSet }.shuffled()
}

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
