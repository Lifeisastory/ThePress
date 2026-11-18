package com.aster.core.design_system.component.liquid_glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 独立合成文字层，边缘效果不会影响下面的玻璃底板与选中胶囊 */
@Composable
internal fun Modifier.tabRowEdgeEffect(edgeWidth: Dp): Modifier {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val shader = remember { RuntimeShader(TabRowEdgeShader) }
        return graphicsLayer {
            clip = true
            compositingStrategy = CompositingStrategy.Offscreen
            shader.setFloatUniform("width", size.width)
            shader.setFloatUniform("edgeWidth", edgeWidth.toPx().coerceAtLeast(1f))
            shader.setFloatUniform("maxRadius", 4.dp.toPx())
            renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
        }
    }
    return graphicsLayer {
        clip = true
        compositingStrategy = CompositingStrategy.Offscreen
    }.drawWithCache {
        val fraction = (edgeWidth.toPx() / size.width.coerceAtLeast(1f)).coerceIn(0f, 0.5f)
        val mask = Brush.horizontalGradient(
            0f to Color.Transparent,
            fraction to Color.Black,
            (1f - fraction) to Color.Black,
            1f to Color.Transparent
        )
        onDrawWithContent {
            drawContent()
            drawRect(mask, blendMode = BlendMode.DstIn)
        }
    }
}

// 中心只采样一次；边缘使用随距离线性增大的 3×3 高斯核，并线性衰减透明度
private const val TabRowEdgeShader = """
uniform shader content;
uniform float width;
uniform float edgeWidth;
uniform float maxRadius;

half4 main(float2 coord) {
    float distanceToEdge = min(coord.x, width - coord.x);
    float visibility = clamp(distanceToEdge / edgeWidth, 0.0, 1.0);
    if (visibility >= 1.0) return content.eval(coord);
    float radius = maxRadius * (1.0 - visibility);
    half4 color = content.eval(coord) * 4.0;
    color += content.eval(coord + float2(radius, 0.0)) * 2.0;
    color += content.eval(coord - float2(radius, 0.0)) * 2.0;
    color += content.eval(coord + float2(0.0, radius)) * 2.0;
    color += content.eval(coord - float2(0.0, radius)) * 2.0;
    color += content.eval(coord + float2(radius, radius));
    color += content.eval(coord + float2(radius, -radius));
    color += content.eval(coord + float2(-radius, radius));
    color += content.eval(coord - float2(radius, radius));
    return color * (visibility / 16.0);
}
"""
