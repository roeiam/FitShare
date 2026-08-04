package com.roeiamor.fitshare.util

import com.roeiamor.fitshare.util.ImageCompressor.Companion.calculateInSampleSize
import com.roeiamor.fitshare.util.ImageCompressor.Companion.scaleToMaxEdge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the sizing maths in [ImageCompressor].
 *
 * Decoding and writing a bitmap needs a real device, but the arithmetic that decides *how much* to
 * shrink does not - and it is the part with the edge cases. It was extracted into pure functions
 * over [ImageDimensions] precisely so it could be tested here.
 */
class ImageCompressorTest {

    private val maxEdge = ImageCompressor.MAX_EDGE_PIXELS

    // ---- inSampleSize ----------------------------------------------------------------------

    @Test
    fun `an image smaller than the cap is not sampled down`() {
        assertEquals(1, calculateInSampleSize(ImageDimensions(800, 600), maxEdge))
    }

    @Test
    fun `an image exactly at the cap is not sampled down`() {
        assertEquals(1, calculateInSampleSize(ImageDimensions(1080, 1080), maxEdge))
    }

    @Test
    fun `a typical 12 megapixel photo is halved twice`() {
        // 4032 / 4 = 1008, which is below the cap, so it stops at 2: 4032 / 2 = 2016 >= 1080.
        assertEquals(2, calculateInSampleSize(ImageDimensions(4032, 3024), maxEdge))
    }

    @Test
    fun `a very large image is sampled further`() {
        assertEquals(4, calculateInSampleSize(ImageDimensions(8000, 6000), maxEdge))
    }

    @Test
    fun `sampling uses the longest edge, whichever way round the photo is`() {
        val landscape = calculateInSampleSize(ImageDimensions(4032, 3024), maxEdge)
        val portrait = calculateInSampleSize(ImageDimensions(3024, 4032), maxEdge)
        assertEquals(landscape, portrait)
    }

    @Test
    fun `the sample size is always a power of two`() {
        listOf(1200, 2500, 3000, 5000, 9000, 13000).forEach { edge ->
            val sampleSize = calculateInSampleSize(ImageDimensions(edge, edge / 2), maxEdge)
            assertTrue(
                "inSampleSize $sampleSize for edge $edge is not a power of two",
                sampleSize > 0 && (sampleSize and (sampleSize - 1)) == 0
            )
        }
    }

    @Test
    fun `sampling never overshoots below the cap`() {
        // Decoding must still leave at least maxEdge pixels; the exact fit is scaleToMaxEdge's job.
        listOf(1200, 2500, 4032, 8000).forEach { edge ->
            val sampleSize = calculateInSampleSize(ImageDimensions(edge, edge), maxEdge)
            assertTrue("edge $edge sampled to below the cap", edge / sampleSize >= maxEdge)
        }
    }

    // ---- scaleToMaxEdge --------------------------------------------------------------------

    @Test
    fun `a small image is returned untouched rather than upscaled`() {
        val small = ImageDimensions(640, 480)
        assertEquals(small, scaleToMaxEdge(small, maxEdge))
    }

    @Test
    fun `a landscape image is capped on its width`() {
        assertEquals(ImageDimensions(1080, 810), scaleToMaxEdge(ImageDimensions(4032, 3024), maxEdge))
    }

    @Test
    fun `a portrait image is capped on its height`() {
        assertEquals(ImageDimensions(810, 1080), scaleToMaxEdge(ImageDimensions(3024, 4032), maxEdge))
    }

    @Test
    fun `a square image becomes exactly the cap`() {
        assertEquals(ImageDimensions(1080, 1080), scaleToMaxEdge(ImageDimensions(3000, 3000), maxEdge))
    }

    @Test
    fun `an extreme panorama keeps at least one pixel on its short side`() {
        // 20000x5 would round the height to 0, and createScaledBitmap throws on a zero dimension.
        val result = scaleToMaxEdge(ImageDimensions(20000, 5), maxEdge)
        assertEquals(1080, result.width)
        assertTrue("height collapsed to ${result.height}", result.height >= 1)
    }

    @Test
    fun `the aspect ratio survives scaling`() {
        val source = ImageDimensions(4000, 2250)
        val scaled = scaleToMaxEdge(source, maxEdge)
        val sourceRatio = source.width.toDouble() / source.height
        val scaledRatio = scaled.width.toDouble() / scaled.height
        assertTrue("ratio drifted: $sourceRatio vs $scaledRatio", Math.abs(sourceRatio - scaledRatio) < 0.01)
    }
}
