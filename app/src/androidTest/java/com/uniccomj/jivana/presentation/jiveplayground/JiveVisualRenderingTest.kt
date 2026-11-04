package com.uniccomj.jivana.presentation.jiveplayground

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import com.uniccomj.jivana.domain.model.JiveCondition
import com.uniccomj.jivana.domain.model.JiveEnergy
import com.uniccomj.jivana.domain.model.JiveMood
import com.uniccomj.jivana.domain.model.JiveSleepiness
import java.io.File
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JiveVisualRenderingTest {
    private val p = JiveProportions

    @Test
    fun sevenMoodsHaveDistinctFacesWithNaturalArmsAndRepresentativePreviews() {
        val faces = mutableListOf<Bitmap>()
        for (mood in JiveMood.entries) {
            val pose = restingPose(JiveCondition(mood = mood))
            assertEquals(0f, pose.leftArm)
            assertEquals(0f, pose.rightArm)
            save("mood_${mood.name.lowercase()}", render(pose))
            // Compare only faces in the same posture, so body/arm differences cannot pass this test.
            pose.bodyY = 0f
            pose.bodyRotation = 0f
            pose.bodyScaleY = 1f
            val face = Bitmap.createBitmap(render(pose), 105, 145, 150, 135)
            faces.forEach { assertFalse("Face for $mood duplicates another mood", it.sameAs(face)) }
            faces += face
        }
        JiveEnergy.entries.forEach { energy ->
            save(
                "energy_${energy.name.lowercase()}",
                render(restingPose(JiveCondition(energy = energy)))
            )
        }
    }

    @Test
    fun sleepingHasTwoCurvedLidsAndOpenEyesProgressivelyShrink() {
        val whiteCounts = JiveSleepiness.entries.map { sleepiness ->
            val bitmap = render(restingPose(JiveCondition(sleepiness = sleepiness)))
            save("sleep_${sleepiness.name.lowercase()}", bitmap)
            val left = eyeRegion(p.LeftEyeX)
            val right = eyeRegion(p.RightEyeX)
            if (sleepiness == JiveSleepiness.SLEEPING) {
                assertTrue(countColor(bitmap, left, JivePalette.Outline.toArgb()) > 40)
                assertTrue(countColor(bitmap, right, JivePalette.Outline.toArgb()) > 40)
            }
            countColor(bitmap, left, JivePalette.White.toArgb())
        }
        whiteCounts.zipWithNext().forEach { (before, after) -> assertTrue(after < before) }
        assertEquals(0, whiteCounts.last())

        // At half of the lid crossfade the line is visible, but softer than the closed lid.
        val closed = render(JivePose().apply { eyeOpenness = 0f }).getPixel(146, 204)
        val halfway = render(JivePose().apply { eyeOpenness = 0.125f }).getPixel(146, 204)
        assertTrue(Color.red(halfway) > Color.red(closed))
        assertTrue(Color.red(halfway) < Color.red(JivePalette.Body.toArgb()))
    }

    @Test
    fun closedLidsFollowRootBodyScaleAndRotation() {
        val pose = JivePose().apply {
            eyeOpenness = 0f
            rootY = -28f
            bodyY = 7f
            bodyScaleX = 1.04f
            bodyScaleY = 0.96f
            bodyRotation = -5f
        }
        val bitmap = render(pose)
        for (eyeX in listOf(p.LeftEyeX, p.RightEyeX)) {
            val center = transform(Offset(eyeX, p.EyeY + 6f), pose)
            val region = Rect(center.x - 20f, center.y - 10f, center.x + 20f, center.y + 10f)
            assertTrue(countColor(bitmap, region, JivePalette.Outline.toArgb()) > 30)
            assertEquals(0, countColor(bitmap, region, JivePalette.White.toArgb()))
        }
        save("sleep_transformed", bitmap)
    }

    @Test
    fun widerGarmentsCoverBothShouldersWithoutChangingBodyGeometry() {
        val geometry = JiveGeometry()
        assertEquals(Rect(70f, 86f, 250f, 342f), geometry.body.getBounds())
        for (path in listOf(geometry.hoodie, geometry.tshirt)) {
            assertTrue(path.getBounds().left <= 65f)
            assertTrue(path.getBounds().right >= 255f)
        }
        for ((outfit, color) in listOf(
            JiveWardrobe.Hoodie to JivePalette.Cream,
            JiveWardrobe.Sport to JivePalette.White
        )) {
            val bitmap = render(JivePose(), outfit)
            assertEquals(color.toArgb(), bitmap.getPixel(92, 279))
            assertEquals(color.toArgb(), bitmap.getPixel(268, 279))
        }
    }

    @Test
    fun evenTheLargestGrinStaysAboveTheClothingNeckline() {
        for (mood in JiveMood.entries) {
            val pose = restingPose(JiveCondition(mood = mood))
            pose.bodyY = 0f
            pose.bodyRotation = 0f
            pose.bodyScaleY = 1f
            val faceBelowMouth = Rect(150f, 276f, 210f, 298f)
            assertEquals(0, countColor(render(pose), faceBelowMouth, JivePalette.Outline.toArgb()))
        }
        save(
            "ecstatic_hoodie",
            render(restingPose(JiveCondition(mood = JiveMood.ECSTATIC)), JiveWardrobe.Hoodie)
        )
    }

    @Test
    fun all112ConditionsCanWearEveryOutfitWithoutMutatingTheirPose() {
        for (mood in JiveMood.entries) {
            for (energy in JiveEnergy.entries) {
                for (sleepiness in JiveSleepiness.entries) {
                    val pose = restingPose(JiveCondition(mood, energy, sleepiness))
                    val before = values(pose)
                    JiveWardrobe.All.forEach { outfit ->
                        render(pose, outfit)
                        assertEquals(before, values(pose))
                    }
                }
            }
        }
        // Save and exercise complete moving silhouettes, including raised arms and opposite strides.
        for (animation in JiveAnimation.entries) {
            for (outfit in JiveWardrobe.All) {
                for ((index, time) in listOf(0f, 0.3f, 0.55f, 0.9f).withIndex()) {
                    val pose = JivePose()
                    JiveMotion.sample(animation, time, pose)
                    val before = values(pose)
                    val bitmap = render(pose, outfit)
                    assertEquals(before, values(pose))
                    save("motion_${animation.name.lowercase()}_${outfit.id}_$index", bitmap)
                }
            }
        }
    }

    @Test
    fun twoTeethHangFromTheUpperLipAndStayHiddenInClosedSmiles() {
        for (mood in listOf(
            JiveMood.NEUTRAL,
            JiveMood.HAPPY,
            JiveMood.VERY_HAPPY,
            JiveMood.ECSTATIC
        )) {
            val bitmap = render(restingPose(JiveCondition(mood = mood)))
            if (mood == JiveMood.NEUTRAL || mood == JiveMood.HAPPY) {
                assertEquals(
                    0,
                    countColor(bitmap, Rect(152f, 238f, 208f, 276f), JivePalette.White.toArgb())
                )
            } else {
                assertUpperTeethAttached(bitmap, mood.name)
            }
            save("teeth_${mood.name.lowercase()}", bitmap)
        }
    }

    @Test
    fun upperTeethStayAttachedThroughoutAnimationAndExpressionInterpolation() {
        var checkedFrames = 0
        for (animation in JiveAnimation.entries) {
            val animator = JiveAnimator()
            animator.setAnimationsEnabled(false)
            animator.updateVisualState(
                JiveVisualStateResolver.resolve(JiveCondition(mood = JiveMood.VERY_HAPPY))
            )
            animator.setAnimationsEnabled(true)
            animator.select(animation)
            repeat(60) { frame ->
                if (frame == 20 || frame == 40) {
                    val mood = if (frame == 20) JiveMood.ECSTATIC else JiveMood.VERY_HAPPY
                    animator.updateVisualState(
                        JiveVisualStateResolver.resolve(JiveCondition(mood = mood))
                    )
                }
                animator.advance(1f / 60f)
                // Inspect mouth attachment in local coordinates, independent of the moving body.
                val localPose = JivePose().apply {
                    setFrom(animator.pose)
                    rootY = 0f
                    bodyY = 0f
                    bodyRotation = 0f
                    bodyScaleX = 1f
                    bodyScaleY = 1f
                }
                if (localPose.expression >= 0.7f) {
                    assertUpperTeethAttached(render(localPose), "$animation frame $frame")
                    checkedFrames++
                }
                if (frame == 35) {
                    save(
                        "teeth_motion_${animation.name.lowercase()}",
                        render(animator.pose, JiveWardrobe.Hoodie)
                    )
                }
            }
        }
        assertTrue(checkedFrames > 100)
    }

    private fun assertUpperTeethAttached(bitmap: Bitmap, label: String) {
        for (x in listOf(167, 193)) {
            val rows = 238 until 276
            val lip = rows.first { bitmap.getPixel(x, it) == JivePalette.Outline.toArgb() }
            val tooth = rows.first { bitmap.getPixel(x, it) == JivePalette.White.toArgb() }
            // Only the lip stroke/antialiasing may separate the tooth from the upper contour.
            assertTrue("Detached upper tooth: $label at x=$x", tooth - lip <= 3)
        }
        for (x in listOf(172, 188)) {
            val lastWhite = (238 until 276).last {
                bitmap.getPixel(x, it) ==
                    JivePalette.White.toArgb()
            }
            val darkBelow = (lastWhite + 1 until 276).count {
                bitmap.getPixel(x, it) == JivePalette.Outline.toArgb()
            }
            assertTrue("Tooth touches the lower lip: $label at x=$x", darkBelow >= 2)
        }
    }

    private fun restingPose(condition: JiveCondition): JivePose {
        val animator = JiveAnimator()
        animator.setAnimationsEnabled(false)
        animator.updateVisualState(JiveVisualStateResolver.resolve(condition))
        return animator.pose
    }

    private fun render(pose: JivePose, outfit: JiveOutfit = JiveWardrobe.Default): Bitmap {
        val image = ImageBitmap(360, 480)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(360f, 480f)) {
            drawRect(JivePalette.Background)
            JiveRenderer().draw(this, pose, outfit)
        }
        return image.asAndroidBitmap()
    }

    private fun eyeRegion(x: Float) =
        Rect(x - 28f + p.FramePadding, 157f, x + 28f + p.FramePadding, 240f)

    private fun countColor(bitmap: Bitmap, rect: Rect, color: Int): Int {
        var count = 0
        for (y in rect.top.toInt() until rect.bottom.toInt()) {
            for (x in rect.left.toInt() until rect.right.toInt()) {
                if (bitmap.getPixel(x, y) == color) count++
            }
        }
        return count
    }

    private fun transform(point: Offset, pose: JivePose): Offset {
        val x = (point.x - p.CenterX) * pose.bodyScaleX
        val y = (point.y - p.BodyBottom) * pose.bodyScaleY
        val angle = Math.toRadians(pose.bodyRotation.toDouble())
        return Offset(
            p.FramePadding + p.CenterX + (x * cos(angle) - y * sin(angle)).toFloat(),
            p.FramePadding + pose.rootY + pose.bodyY + p.BodyBottom +
                (x * sin(angle) + y * cos(angle)).toFloat()
        )
    }

    private fun values(pose: JivePose): List<Float> = with(pose) {
        listOf(
            rootY, bodyY, bodyRotation, bodyScaleX, bodyScaleY, leftArm, rightArm, shoulderDrop,
            leftLeg, rightLeg, leftFootLift, rightFootLift, eyeOpenness, eyeTilt, pupilX, pupilY,
            expression
        )
    }

    private fun save(name: String, bitmap: Bitmap) {
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val directory = if (output == null) {
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
        } else {
            File(output).apply { mkdirs() }
        }
        File(directory, "refinement_$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
