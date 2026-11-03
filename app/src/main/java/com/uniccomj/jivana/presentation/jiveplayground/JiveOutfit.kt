package com.uniccomj.jivana.presentation.jiveplayground

import androidx.compose.ui.graphics.Color

enum class JiveJoint { BODY, LEFT_ARM, RIGHT_ARM, HEAD }

enum class JiveGarmentShape { HOODIE, LONG_SLEEVE, TSHIRT, SHORT_SLEEVE, SHORTS, HEADBAND }

data class JiveGarment(val joint: JiveJoint, val shape: JiveGarmentShape, val color: Color)

/** Ordered layers are drawn inside the joint's transform, never inside the motion sampler. */
data class JiveOutfit(val id: String, val garments: List<JiveGarment>)

object JiveWardrobe {
    val Default = JiveOutfit("default", emptyList())
    val Hoodie = JiveOutfit(
        "hoodie",
        listOf(
            JiveGarment(JiveJoint.BODY, JiveGarmentShape.HOODIE, JivePalette.Cream),
            JiveGarment(JiveJoint.LEFT_ARM, JiveGarmentShape.LONG_SLEEVE, JivePalette.Cream),
            JiveGarment(JiveJoint.RIGHT_ARM, JiveGarmentShape.LONG_SLEEVE, JivePalette.Cream)
        )
    )
    val Sport = JiveOutfit(
        "sport",
        listOf(
            JiveGarment(JiveJoint.BODY, JiveGarmentShape.TSHIRT, JivePalette.White),
            JiveGarment(JiveJoint.BODY, JiveGarmentShape.SHORTS, JivePalette.Shorts),
            JiveGarment(JiveJoint.LEFT_ARM, JiveGarmentShape.SHORT_SLEEVE, JivePalette.White),
            JiveGarment(JiveJoint.RIGHT_ARM, JiveGarmentShape.SHORT_SLEEVE, JivePalette.White),
            JiveGarment(JiveJoint.HEAD, JiveGarmentShape.HEADBAND, JivePalette.White)
        )
    )
    val All = listOf(Default, Hoodie, Sport)
}

internal object JivePalette {
    val Body = Color(0xFFD2ABF0)
    val Outline = Color(0xFF3C234F)
    val White = Color(0xFFFFFCFA)
    val Pupil = Color(0xFF21172C)
    val Cream = Color(0xFFFFF2D9)
    val Shorts = Color(0xFF666080)
    val Background = Color(0xFFFBF8F5)
    val Shadow = Color(0xFFE1D8E6)
}
