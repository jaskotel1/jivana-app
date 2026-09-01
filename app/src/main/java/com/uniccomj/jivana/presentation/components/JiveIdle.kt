package com.uniccomj.jivana.presentation.components

import android.content.Context
import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.uniccomj.jivana.core.ui.theme.JivanaTheme
import com.uniccomj.jivana.domain.model.JiveCondition
import com.uniccomj.jivana.presentation.avatar.JiveAnimationPlayback
import com.uniccomj.jivana.presentation.avatar.JiveAnimationResolver

private const val JiveAspectRatio = 1f

@Composable
fun JiveMascot(
    condition: JiveCondition,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val resolver = remember { JiveAnimationResolver() }
    val animation = resolver.resolve(condition)
    val sizedModifier = modifier.aspectRatio(JiveAspectRatio)
    if (LocalInspectionMode.current) {
        Image(
            painter = painterResource(animation.drawableRes),
            contentDescription = contentDescription,
            modifier = sizedModifier,
            contentScale = ContentScale.Fit
        )
        return
    }

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val drawable = remember(context, configuration, animation.drawableRes) {
        loadJiveDrawable(context, animation.drawableRes)
    }
    AndroidView(
        factory = { viewContext ->
            LifecycleAwareImageView(viewContext).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
                setJiveDrawable(drawable, animation.playback)
                updateAccessibility(contentDescription)
            }
        },
        modifier = sizedModifier,
        update = { imageView ->
            imageView.setJiveDrawable(drawable, animation.playback)
            imageView.updateAccessibility(contentDescription)
        }
    )
}

@Composable
fun JiveIdle(modifier: Modifier = Modifier, contentDescription: String? = null) {
    JiveMascot(
        condition = JiveCondition(),
        modifier = modifier,
        contentDescription = contentDescription
    )
}

private fun loadJiveDrawable(context: Context, @DrawableRes drawableRes: Int): Drawable? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeDrawable(
            ImageDecoder.createSource(context.resources, drawableRes)
        )
    } else {
        ContextCompat.getDrawable(context, drawableRes)
    }

private class LifecycleAwareImageView(context: Context) : ImageView(context) {
    private var playback = JiveAnimationPlayback.LOOP

    fun setJiveDrawable(newDrawable: Drawable?, newPlayback: JiveAnimationPlayback) {
        if (drawable === newDrawable && playback == newPlayback) return
        stopAnimation()
        playback = newPlayback
        setImageDrawable(newDrawable)
        startAnimationIfVisible()
    }

    fun updateAccessibility(description: String?) {
        contentDescription = description
        importantForAccessibility = if (description == null) {
            IMPORTANT_FOR_ACCESSIBILITY_NO
        } else {
            IMPORTANT_FOR_ACCESSIBILITY_YES
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimationIfVisible()
    }

    override fun onDetachedFromWindow() {
        stopAnimation()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) {
            startAnimationIfVisible()
        } else {
            stopAnimation()
        }
    }

    private fun startAnimationIfVisible() {
        if (!isAttachedToWindow || windowVisibility != VISIBLE) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            (drawable as? AnimatedImageDrawable)?.repeatCount =
                when (playback) {
                    JiveAnimationPlayback.LOOP -> AnimatedImageDrawable.REPEAT_INFINITE
                    JiveAnimationPlayback.ONE_SHOT -> 0
                }
        }
        (drawable as? Animatable)?.start()
    }

    private fun stopAnimation() {
        (drawable as? Animatable)?.stop()
    }
}

@Preview(showBackground = true)
@Composable
private fun JiveIdlePreview() {
    JivanaTheme {
        JiveMascot(condition = JiveCondition())
    }
}
