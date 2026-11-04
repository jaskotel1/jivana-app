package com.uniccomj.jivana.presentation.jiveplayground

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate

/** Pose is read during drawing, so frames invalidate the Canvas without recomposing controls. */
@Composable
fun JiveCharacter(pose: () -> JivePose, outfit: JiveOutfit, modifier: Modifier = Modifier) {
    val renderer = remember { JiveRenderer() }
    Canvas(modifier) { renderer.draw(this, pose(), outfit) }
}

// Numeric literals here are vector drawing coordinates, rather than application rules.
@Suppress("MagicNumber")
internal class JiveRenderer {
    private val geometry = JiveGeometry()
    private val p = JiveProportions
    private val outline = Stroke(p.Outline, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val detail = Stroke(2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)

    fun draw(scope: DrawScope, pose: JivePose, outfit: JiveOutfit) = with(scope) {
        val fit = p.fitScale(size.width, size.height)
        if (fit <= 0f) return@with
        translate((size.width - p.Width * fit) / 2f, (size.height - p.Height * fit) / 2f) {
            scale(fit, fit, Offset.Zero) {
                translate(p.FramePadding, p.FramePadding) {
                    drawOval(JivePalette.Shadow, Offset(68f, 397f), Size(184f, 19f))
                    translate(top = pose.rootY) {
                        leg(p.LeftHipX, pose.leftLeg, pose.leftFootLift, false)
                        leg(p.RightHipX, pose.rightLeg, pose.rightFootLift, true)
                        translate(top = pose.bodyY) {
                            rotate(pose.bodyRotation, Offset(p.CenterX, p.BodyBottom)) {
                                scale(
                                    pose.bodyScaleX,
                                    pose.bodyScaleY,
                                    Offset(p.CenterX, p.BodyBottom)
                                ) {
                                    // Arms sit behind the body; their sleeves share the exact same joint transform.
                                    translate(top = pose.shoulderDrop) {
                                        arm(
                                            p.LeftShoulderX,
                                            pose.leftArm,
                                            false,
                                            JiveJoint.LEFT_ARM,
                                            outfit
                                        )
                                        arm(
                                            p.RightShoulderX,
                                            pose.rightArm,
                                            true,
                                            JiveJoint.RIGHT_ARM,
                                            outfit
                                        )
                                    }
                                    shape(geometry.body, JivePalette.Body)
                                    garments(JiveJoint.BODY, outfit)
                                    drawPath(geometry.hair, JivePalette.Outline, style = outline)
                                    garments(JiveJoint.HEAD, outfit)
                                    eye(p.LeftEyeX, pose)
                                    eye(p.RightEyeX, pose)
                                    mouth(pose.expression)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun DrawScope.leg(x: Float, angle: Float, lift: Float, mirrored: Boolean) {
        translate(x, p.HipY - lift) {
            rotate(angle, Offset.Zero) {
                scale(if (mirrored) -1f else 1f, 1f, Offset.Zero) {
                    shape(geometry.leg, JivePalette.Body)
                }
            }
        }
    }

    private fun DrawScope.arm(
        x: Float,
        angle: Float,
        mirrored: Boolean,
        joint: JiveJoint,
        outfit: JiveOutfit
    ) {
        translate(x, p.ShoulderY) {
            rotate(angle, Offset.Zero) {
                scale(if (mirrored) -1f else 1f, 1f, Offset.Zero) {
                    shape(geometry.arm, JivePalette.Body)
                    garments(joint, outfit)
                }
            }
        }
    }

    private fun DrawScope.eye(x: Float, pose: JivePose) {
        translate(x, p.EyeY) {
            rotate(if (x == p.LeftEyeX) pose.eyeTilt else -pose.eyeTilt, Offset.Zero) {
                val openness = pose.eyeOpenness.coerceIn(0f, 1f)
                val openAlpha = (openness / 0.25f).coerceIn(0f, 1f)
                if (openness > 0f) {
                    scale(1f, openness, Offset.Zero) {
                        drawPath(geometry.eye, JivePalette.White, alpha = openAlpha)
                        clipPath(geometry.eye) {
                            drawOval(
                                JivePalette.Pupil,
                                Offset(-7f + pose.pupilX, -12f + pose.pupilY),
                                Size(16f, 26f),
                                alpha = openAlpha
                            )
                        }
                    }
                }
                // The closed lid shares the eye/body transforms, but never the collapsing Y scale.
                drawPath(
                    geometry.eyelid,
                    JivePalette.Outline,
                    alpha = 1f - openAlpha,
                    style = detail
                )
            }
        }
    }

    private fun DrawScope.mouth(expression: Float) {
        val emotion = expression.coerceIn(-1f, 1f)
        val joy = emotion.coerceAtLeast(0f)
        val sadness = (-emotion).coerceAtLeast(0f)
        val width = 22f + joy * 8f - sadness * 4f
        val y = 232f - joy * 14f
        val curve = 8f + joy * 14f - sadness * 34f
        val opening = joy * joy * 48f
        geometry.mouth.apply {
            reset()
            moveTo(p.CenterX - width, y)
            quadraticTo(p.CenterX, y + curve, p.CenterX + width, y)
            quadraticTo(p.CenterX, y + curve + opening, p.CenterX - width, y)
            close()
        }
        drawPath(geometry.mouth, JivePalette.Outline)
        clipPath(geometry.mouth) {
            val teethAlpha = ((joy - 0.35f) / 0.35f).coerceIn(0f, 1f)
            // Sample the upper lip at each tooth's center (symmetric about CenterX).
            val toothOffset = 8.5f / width
            val curveFactor = 1f - toothOffset * toothOffset
            val upperLipY = y + curve * 0.5f * curveFactor
            val toothLength = minOf(12f, opening * 0.5f * curveFactor * 0.65f)
            // Hide the rounded roots under the upper lip; only the hanging crowns are visible.
            val toothY = upperLipY - 6f
            val toothSize = Size(13f, 6f + toothLength)
            drawRoundRect(
                JivePalette.White,
                Offset(145f, toothY),
                toothSize,
                CornerRadius(3f),
                alpha = teethAlpha
            )
            drawRoundRect(
                JivePalette.White,
                Offset(162f, toothY),
                toothSize,
                CornerRadius(3f),
                alpha = teethAlpha
            )
        }
        // The lip overlaps the tooth roots instead of being painted over by them.
        drawPath(geometry.mouth, JivePalette.Outline, style = detail)
    }

    private fun DrawScope.garments(joint: JiveJoint, outfit: JiveOutfit) {
        for (index in outfit.garments.indices) {
            val garment = outfit.garments[index]
            if (garment.joint != joint) continue
            when (garment.shape) {
                JiveGarmentShape.HOODIE -> {
                    shape(geometry.hoodie, garment.color)
                    shape(geometry.collar, garment.color)
                    drawPath(geometry.pocket, JivePalette.Outline, style = detail)
                    drawLine(
                        JivePalette.Outline,
                        Offset(140f, 276f),
                        Offset(140f, 292f),
                        strokeWidth = 2.5f
                    )
                    drawLine(
                        JivePalette.Outline,
                        Offset(180f, 276f),
                        Offset(180f, 292f),
                        strokeWidth = 2.5f
                    )
                }

                JiveGarmentShape.LONG_SLEEVE -> shape(geometry.longSleeve, garment.color)

                JiveGarmentShape.TSHIRT -> shape(geometry.tshirt, garment.color)

                JiveGarmentShape.SHORT_SLEEVE -> shape(geometry.shortSleeve, garment.color)

                JiveGarmentShape.SHORTS -> {
                    shape(geometry.shorts, garment.color)
                    drawLine(
                        JivePalette.White,
                        Offset(88f, 317f),
                        Offset(91f, 347f),
                        strokeWidth = 3f
                    )
                    drawLine(
                        JivePalette.White,
                        Offset(232f, 317f),
                        Offset(229f, 347f),
                        strokeWidth = 3f
                    )
                }

                JiveGarmentShape.HEADBAND -> shape(geometry.headband, garment.color)
            }
        }
    }

    private fun DrawScope.shape(path: Path, color: Color) {
        drawPath(path, color)
        drawPath(path, JivePalette.Outline, style = outline)
    }
}
