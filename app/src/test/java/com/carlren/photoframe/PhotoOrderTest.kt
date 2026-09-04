package com.carlren.photoframe

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PhotoOrderTest {
    @Test
    fun randomizedOrder_preservesEveryPhoto() {
        val photos = listOf("one", "two", "three", "four")

        val result = randomizedPhotoOrder(photos, random = Random(7))

        assertEquals(photos.toSet(), result.toSet())
        assertEquals(photos.size, result.size)
    }

    @Test
    fun randomizedOrder_avoidsRepeatingPreviousPhotoFirst() {
        val photos = listOf("one", "two", "three")

        val result = randomizedPhotoOrder(photos, previouslyShown = "one", random = Random(3))

        assertNotEquals("one", result.first())
    }

    @Test
    fun randomizedOrder_keepsSinglePhoto() {
        assertEquals(listOf("only"), randomizedPhotoOrder(listOf("only"), "only"))
    }
}
