package com.uniccomj.jivana.presentation.components

import android.content.Context
import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.ImageView
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
import com.uniccomj.jivana.R
import com.uniccomj.jivana.core.ui.theme.JivanaTheme

private const val JiveAspectRatio = 1f

@Composable
fun JiveIdle(modifier: Modifier = Modifier, contentDescription: String? = null) {
    val sizedModifier = modifier.aspectRatio(JiveAspectRatio)
    if (LocalInspectionMode.current) {
        Image(
            painter = painterResource(R.drawable.jive_idle),
            contentDescription = contentDescription,
            modifier = sizedModifier,
            contentScale = ContentScale.Fit
        )
        return
    }

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val drawable = remember(context, configuration) {
        loadJiveDrawable(context)
    }
    AndroidView(
        factory = { viewContext ->
            LifecycleAwareImageView(viewContext).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
                setJiveDrawable(drawable)
                updateAccessibility(contentDescription)
            }
        },
        modifier = sizedModifier,
        update = { imageView ->
            imageView.setJiveDrawable(drawable)
            imageView.updateAccessibility(contentDescription)
        }
    )
}

private fun loadJiveDrawable(context: Context): Drawable? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeDrawable(
            ImageDecoder.createSource(context.resources, R.drawable.jive_idle)
        )
    } else {
        ContextCompat.getDrawable(context, R.drawable.jive_idle)
    }

private class LifecycleAwareImageView(context: Context) : ImageView(context) {
    fun setJiveDrawable(newDrawable: Drawable?) {
        if (drawable === newDrawable) return
        stopAnimation()
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
                AnimatedImageDrawable.REPEAT_INFINITE
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
        JiveIdle()
    }
}
