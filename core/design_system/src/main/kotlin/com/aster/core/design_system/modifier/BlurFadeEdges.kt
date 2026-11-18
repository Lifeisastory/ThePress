package com.aster.core.design_system.modifier

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * 在滚动方向的两端逐渐模糊并透明，不影响兄弟节点或后方底板
 *
 * [fadeStart]、[fadeEnd] 为逻辑首尾：横向随 RTL 镜像，纵向对应顶部和底部
 * [edgeSize] 为每端的过渡宽度，最多占对应方向尺寸的一半；0.dp 关闭效果
 */
@Composable
fun Modifier.blurFadeEdges(
    orientation: Orientation,
    edgeSize: Dp = 24.dp,
    fadeStart: Boolean = true,
    fadeEnd: Boolean = true
): Modifier {
    require(edgeSize.value.isFinite() && edgeSize >= 0.dp) { "Edge size must be finite and non-negative" }
    if (edgeSize == 0.dp) return this

    val start by animateFloatAsState(if (fadeStart) 1f else 0f, tween(150), label = "edgeStartFade")
    val end by animateFloatAsState(if (fadeEnd) 1f else 0f, tween(150), label = "edgeEndFade")
    val isHorizontal = orientation == Orientation.Horizontal
    val isRtl = isHorizontal && LocalLayoutDirection.current == LayoutDirection.Rtl
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val shaders = remember { List(2) { RuntimeShader(BlurFadeEdgesShader) } }
        return graphicsLayer {
            clip = true
            compositingStrategy = CompositingStrategy.Offscreen
            val extent = if (isHorizontal) size.width else size.height
            shaders.forEachIndexed { index, shader ->
                shader.setFloatUniform("size", size.width, size.height)
                shader.setFloatUniform("edgeSize", edgeSize.toPx().coerceAtMost(extent / 2f).coerceAtLeast(1f))
                shader.setFloatUniform("fadeAxis", if (isHorizontal) 1f else 0f, if (isHorizontal) 0f else 1f)
                shader.setFloatUniform("fadeStrength", if (isRtl) end else start, if (isRtl) start else end)
                // 每侧 16 个采样点，限制核半径以保证高密度屏幕上也不跳过像素
                shader.setFloatUniform("maxRadius", 4.dp.toPx().coerceAtMost(16f))
                shader.setFloatUniform("axis", if (index == 0) 1f else 0f, if (index == 0) 0f else 1f)
                shader.setFloatUniform("applyFade", if (index == 1) 1f else 0f)
            }
            val horizontal = RenderEffect.createRuntimeShaderEffect(shaders[0], "content")
            val vertical = RenderEffect.createRuntimeShaderEffect(shaders[1], "content")
            renderEffect = RenderEffect.createChainEffect(vertical, horizontal).asComposeRenderEffect()
        }
    }
    return graphicsLayer {
        clip = true
        compositingStrategy = CompositingStrategy.Offscreen
    }.drawWithCache {
        val extent = if (isHorizontal) size.width else size.height
        val fraction = (edgeSize.toPx() / extent.coerceAtLeast(1f)).coerceIn(0f, 0.5f)
        val stops = arrayOf(
            0f to Color.Black.copy(alpha = 1f - if (isRtl) end else start),
            fraction to Color.Black,
            (1f - fraction) to Color.Black,
            1f to Color.Black.copy(alpha = 1f - if (isRtl) start else end)
        )
        val mask = if (isHorizontal) Brush.horizontalGradient(*stops) else Brush.verticalGradient(*stops)
        onDrawWithContent {
            drawContent()
            drawRect(mask, blendMode = BlendMode.DstIn)
        }
    }
}

// 横、纵两次密集采样形成可分离高斯核；仅在最后一遍渐隐，避免重复衰减
private const val BlurFadeEdgesShader = """
uniform shader content;
uniform float2 size;
uniform float edgeSize;
uniform float2 fadeAxis;
uniform float2 fadeStrength;
uniform float maxRadius;
uniform float2 axis;
uniform float applyFade;

half4 main(float2 coord) {
    float positionAlongEdge = dot(coord, fadeAxis);
    float extent = dot(size, fadeAxis);
    float leading = mix(1.0, clamp(positionAlongEdge / edgeSize, 0.0, 1.0), fadeStrength.x);
    float trailing = mix(1.0, clamp((extent - positionAlongEdge) / edgeSize, 0.0, 1.0), fadeStrength.y);
    float visibility = min(leading, trailing);
    if (visibility >= 1.0) return content.eval(coord);
    float radius = maxRadius * (1.0 - visibility);
    half4 color = half4(0.0);
    float totalWeight = 0.0;
    for (int i = -16; i <= 16; ++i) {
        float position = float(i) / 16.0;
        float weight = exp(-4.5 * position * position);
        float2 sampleCoord = coord + axis * (position * radius);
        if (sampleCoord.x >= 0.0 && sampleCoord.x < size.x &&
            sampleCoord.y >= 0.0 && sampleCoord.y < size.y) {
            color += content.eval(sampleCoord) * weight;
        }
        totalWeight += weight;
    }
    return color * (mix(1.0, visibility, applyFade) / totalWeight);
}
"""
