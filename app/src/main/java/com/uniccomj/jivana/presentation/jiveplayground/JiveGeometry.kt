package com.uniccomj.jivana.presentation.jiveplayground

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path

/** Paths are created once per character. Limbs use local coordinates with their joint at (0, 0). */
internal class JiveGeometry {
    private val p = JiveProportions
    val body = Path().apply {
        moveTo(p.BodyLeft, 204f)
        cubicTo(p.BodyLeft, 135f, 101f, p.BodyTop, p.CenterX, p.BodyTop)
        cubicTo(219f, p.BodyTop, p.BodyRight, 135f, p.BodyRight, 204f)
        lineTo(p.BodyRight, 283f)
        cubicTo(p.BodyRight, 324f, 214f, p.BodyBottom, p.CenterX, p.BodyBottom)
        cubicTo(106f, p.BodyBottom, p.BodyLeft, 324f, p.BodyLeft, 283f)
        close()
    }
    val arm = Path().apply {
        moveTo(0f, -8f)
        cubicTo(-11f, -12f, -22f, 13f, -29f, 43f)
        cubicTo(-38f, 76f, -19f, 91f, -9f, 70f)
        cubicTo(-3f, 56f, 1f, 30f, 8f, 11f)
        close()
    }
    val leg = Path().apply {
        moveTo(-11f, 0f)
        lineTo(-11f, 55f)
        cubicTo(-31f, 62f, -27f, 76f, -8f, 76f)
        cubicTo(5f, 76f, 11f, 73f, 11f, 62f)
        lineTo(11f, 0f)
        close()
    }
    val eye = Path().apply {
        addOval(Rect(-p.EyeWidth / 2f, -p.EyeHeight / 2f, p.EyeWidth / 2f, p.EyeHeight / 2f))
    }
    val eyelid = Path().apply {
        moveTo(-22f, 0f)
        quadraticTo(0f, 12f, 22f, 0f)
    }
    val hair = Path().apply {
        moveTo(145f, p.BodyTop)
        quadraticTo(143f, 69f, 132f, 63f)
        moveTo(158f, p.BodyTop)
        quadraticTo(157f, 63f, 171f, 51f)
        moveTo(171f, p.BodyTop)
        quadraticTo(180f, 67f, 192f, 66f)
    }

    // Mutable mouth paths allow continuous expression changes without allocating Paths per frame.
    val mouth = Path()
    val hoodie = Path().apply {
        moveTo(65f, 239f)
        cubicTo(99f, 230f, 129f, 244f, 160f, 257f)
        cubicTo(191f, 244f, 221f, 230f, 255f, 239f)
        cubicTo(261f, 260f, 255f, 297f, 247f, 318f)
        cubicTo(239f, 340f, 200f, 346f, 160f, 346f)
        cubicTo(120f, 346f, 81f, 340f, 73f, 318f)
        cubicTo(65f, 297f, 59f, 260f, 65f, 239f)
        close()
    }
    val collar = Path().apply {
        moveTo(65f, 239f)
        cubicTo(99f, 230f, 129f, 244f, 160f, 257f)
        cubicTo(191f, 244f, 221f, 230f, 255f, 239f)
        quadraticTo(260f, 246f, 253f, 252f)
        cubicTo(219f, 244f, 189f, 258f, 160f, 270f)
        cubicTo(131f, 258f, 101f, 244f, 67f, 252f)
        quadraticTo(60f, 246f, 65f, 239f)
        close()
    }
    val pocket = Path().apply {
        moveTo(127f, 302f)
        lineTo(193f, 302f)
        lineTo(204f, 323f)
        quadraticTo(160f, 334f, 116f, 323f)
        close()
    }
    val longSleeve = Path().apply {
        moveTo(0f, -12f)
        cubicTo(-17f, -16f, -29f, 15f, -34f, 49f)
        quadraticTo(-21f, 60f, -7f, 56f)
        lineTo(12f, 10f)
        close()
    }
    val shortSleeve = Path().apply {
        moveTo(0f, -10f)
        quadraticTo(-18f, -13f, -29f, 24f)
        lineTo(-3f, 34f)
        lineTo(13f, 8f)
        close()
    }
    val tshirt = Path().apply {
        moveTo(65f, 240f)
        cubicTo(100f, 233f, 128f, 248f, 160f, 260f)
        cubicTo(192f, 248f, 220f, 233f, 255f, 240f)
        cubicTo(260f, 262f, 252f, 291f, 246f, 311f)
        quadraticTo(160f, 335f, 74f, 311f)
        cubicTo(68f, 291f, 60f, 262f, 65f, 240f)
        close()
    }
    val shorts = Path().apply {
        moveTo(72f, 309f)
        quadraticTo(160f, 325f, 248f, 309f)
        lineTo(242f, 353f)
        lineTo(171f, 353f)
        lineTo(168f, 340f)
        lineTo(152f, 340f)
        lineTo(149f, 353f)
        lineTo(78f, 353f)
        close()
    }
    val headband = Path().apply {
        moveTo(78f, 133f)
        quadraticTo(160f, 109f, 242f, 133f)
        lineTo(238f, 115f)
        quadraticTo(160f, 92f, 82f, 115f)
        close()
    }
}
