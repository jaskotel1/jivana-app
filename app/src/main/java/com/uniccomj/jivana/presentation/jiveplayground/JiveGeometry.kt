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
        moveTo(75f, 251f)
        quadraticTo(115f, 248f, 160f, 266f)
        quadraticTo(205f, 248f, 245f, 251f)
        lineTo(251f, 304f)
        cubicTo(251f, 340f, 210f, 346f, 160f, 346f)
        cubicTo(110f, 346f, 69f, 340f, 69f, 304f)
        close()
    }
    val collar = Path().apply {
        moveTo(76f, 246f)
        quadraticTo(111f, 245f, 160f, 266f)
        quadraticTo(209f, 245f, 244f, 246f)
        quadraticTo(254f, 249f, 247f, 260f)
        quadraticTo(208f, 263f, 160f, 279f)
        quadraticTo(112f, 263f, 73f, 260f)
        quadraticTo(66f, 251f, 76f, 246f)
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
        moveTo(0f, -10f)
        cubicTo(-15f, -12f, -26f, 17f, -32f, 49f)
        quadraticTo(-21f, 60f, -7f, 56f)
        lineTo(10f, 10f)
        close()
    }
    val shortSleeve = Path().apply {
        moveTo(0f, -8f)
        quadraticTo(-15f, -8f, -26f, 24f)
        lineTo(-3f, 34f)
        lineTo(11f, 8f)
        close()
    }
    val tshirt = Path().apply {
        moveTo(76f, 250f)
        quadraticTo(160f, 281f, 244f, 250f)
        lineTo(252f, 309f)
        quadraticTo(160f, 330f, 68f, 309f)
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
