package com.aster.core.design_system.component.liquid_glass

import android.graphics.RenderEffect
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.effect
import com.kyant.backdrop.effects.vibrancy
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sqrt

internal val AppNavigationHeight = 64.dp
internal val AppNavigationGap = 8.dp
internal val AppNavigationTravel = 24.dp

/** 底板和按钮只绘制一次；裁剪、阴影与折射共用融合轮廓，避免连接处出现重叠边框 */
@Composable
internal fun AppNavigationSurface(
    backdrop: Backdrop,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val shape = remember(progress) { NavigationShape(progress) }
    val surface = MaterialTheme.colorScheme.surface.copy(
        alpha = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) 0.94f else 0.4f
    )
    Box(
        modifier
            .dropShadow(
                shape = shape,
                shadow = Shadow(
                    radius = 24.dp,
                    offset = DpOffset(0.dp, 4.dp),
                    color = Color.Black.copy(alpha = 0.1f)
                )
            )
            .drawWithCache {
                val path = shape.createOutline(size, layoutDirection, this).path
                val highlightStroke = Stroke(1.dp.toPx())
                onDrawWithContent {
                    // 在最终绘制阶段裁剪背景和底色；支持融合中的凹路径及分离后的多轮廓
                    // 阴影在本节点外绘制，避免被裁掉；每次形状变化都会重建此裁剪路径
                    clipPath(path) {
                        this@onDrawWithContent.drawContent()
                        drawPath(
                            path = path,
                            color = Color.White.copy(alpha = 0.38f),
                            style = highlightStroke,
                            blendMode = BlendMode.Plus
                        )
                    }
                }
            }
            .drawPlainBackdrop(
                backdrop = backdrop,
                // Backdrop 只负责矩形背景采样，动态轮廓由外层显式裁剪
                shape = { RectangleShape },
                effects = {
                    vibrancy()
                    blur(8.dp.toPx())
                    navigationLens(shape)
                },
                onDrawSurface = { drawRect(surface) }
            )
    )
}

private data class NavigationShape(val progress: Float) : Shape {
    fun geometry(size: Size, density: Density): NavigationGeometry = with(density) {
        val radius = size.height / 2f
        val width = (size.width - (size.height + AppNavigationGap.toPx()) * progress)
            .coerceAtLeast(size.height)
        // 水滴从底板内部向外移动；末段收紧连接细颈，最终留下完整间隙
        val center = size.width - radius - AppNavigationTravel.toPx() * (1f - progress)
        val release = ((progress - 0.75f) / 0.25f).coerceIn(0f, 1f)
        NavigationGeometry(
            radius = radius,
            barEnd = width - radius,
            buttonCenter = center,
            blend = 24.dp.toPx() * (1f - release * release * (3f - 2f * release))
        )
    }

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline.Generic {
        val geometry = geometry(size, density)
        val path = geometry.outline(density)
        if (layoutDirection == LayoutDirection.Rtl) {
            path.asAndroidPath().transform(android.graphics.Matrix().apply {
                setScale(-1f, 1f)
                postTranslate(size.width, 0f)
            })
        }
        return Outline.Generic(path)
    }
}

private data class NavigationGeometry(
    val radius: Float,
    val barEnd: Float,
    val buttonCenter: Float,
    val blend: Float
) {
    // 与下面 AGSL 使用相同的平滑并集。仅相向的表面融合，保持胶囊顶部平直
    private fun distance(x: Float, y: Float): Float {
        val ax = x - barEnd
        val bx = x - buttonCenter
        val a = sqrt(ax * ax + y * y)
        val b = sqrt(bx * bx + y * y)
        val facing = (-(ax * bx + y * y) / (a * b).coerceAtLeast(0.0001f)).coerceAtLeast(0f)
        val strength = blend * facing
        val overlap = (strength - abs(a - b)).coerceAtLeast(0f)
        return min(a, b) - radius - overlap * overlap / (4f * strength).coerceAtLeast(0.0001f)
    }

    fun outline(density: Density): Path {
        val height = radius * 2f
        val body = RoundRect(0f, 0f, barEnd + radius, height, CornerRadius(radius))
        val path = Path()
        if (buttonCenter <= barEnd) {
            path.addRoundRect(body)
            return path
        }
        if (blend <= 0f) {
            path.addRoundRect(body)
            path.addOval(Rect(buttonCenter - radius, 0f, buttonCenter + radius, height))
            return path
        }

        val middle = (barEnd + buttonCenter) / 2f
        val connected = distance(middle, 0f) <= 0f
        var edge = middle
        if (!connected) {
            var inside = barEnd
            var outside = middle
            repeat(14) {
                edge = (inside + outside) / 2f
                if (distance(edge, 0f) <= 0f) inside = edge else outside = edge
            }
            edge = (inside + outside) / 2f
        }

        // 只采样两个圆心之间的变形部分；其余轮廓保留精确圆弧
        fun curve(from: Float, to: Float): List<Pair<Float, Float>> {
            val steps = ceil((to - from) / (density.density * 0.75f)).toInt().coerceAtLeast(1)
            return (0..steps).map { index ->
                val x = from + (to - from) * index / steps
                var low = 0f
                var high = radius
                repeat(12) {
                    val y = (low + high) / 2f
                    if (distance(x, y) <= 0f) low = y else high = y
                }
                x to (low + high) / 2f
            }
        }
        fun Path.top(points: List<Pair<Float, Float>>) {
            points.forEach { (x, y) -> lineTo(x, radius - y) }
        }
        fun Path.bottom(points: List<Pair<Float, Float>>) {
            points.asReversed().forEach { (x, y) -> lineTo(x, radius + y) }
        }
        fun Path.closeBody() {
            lineTo(radius, height)
            arcTo(Rect(0f, 0f, height, height), 90f, 180f, false)
            close()
        }
        val buttonRightArc = Rect(buttonCenter - radius, 0f, buttonCenter + radius, height)
        path.moveTo(radius, 0f)
        path.lineTo(barEnd, 0f)
        if (connected) {
            val points = curve(barEnd, buttonCenter)
            path.top(points)
            path.arcTo(buttonRightArc, -90f, 180f, false)
            path.bottom(points)
            path.closeBody()
        } else {
            val left = curve(barEnd, edge)
            path.top(left)
            path.bottom(left)
            path.closeBody()
            val right = curve(2f * middle - edge, buttonCenter)
            path.moveTo(right.first().first, radius)
            path.top(right)
            path.arcTo(buttonRightArc, -90f, 180f, false)
            path.bottom(right)
            path.close()
        }
        return path
    }
}

private fun BackdropEffectScope.navigationLens(shape: NavigationShape) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val geometry = shape.geometry(size, this)
    val refraction = 24.dp.toPx()
    padding = (padding - refraction).coerceAtLeast(0f)
    val shader = obtainRuntimeShader("NavigationGlassLens", NavigationShader)
    shader.setFloatUniform("geometry", geometry.barEnd, geometry.buttonCenter, geometry.radius, geometry.blend)
    shader.setFloatUniform("width", size.width)
    shader.setFloatUniform("rtl", if (layoutDirection == LayoutDirection.Rtl) 1f else 0f)
    shader.setFloatUniform("padding", padding)
    shader.setFloatUniform("refraction", refraction)
    effect(RenderEffect.createRuntimeShaderEffect(shader, "content"))
}

// Backdrop 的内置 lens 仅支持圆角矩形；这里沿融合轮廓的法线折射背景
private const val NavigationShader = """
uniform shader content;
uniform float4 geometry;
uniform float width;
uniform float rtl;
uniform float padding;
uniform float refraction;

float field(float2 p) {
    float radius = geometry.z;
    float2 a = float2(p.x - clamp(p.x, radius, geometry.x), p.y - radius);
    float2 b = float2(p.x - geometry.y, p.y - radius);
    float da = length(a);
    float db = length(b);
    float facing = max(0.0, -dot(a, b) / max(da * db, 0.0001));
    float strength = geometry.w * facing;
    float overlap = max(0.0, strength - abs(da - db));
    return min(da, db) - radius - overlap * overlap / max(4.0 * strength, 0.0001);
}

half4 main(float2 coord) {
    float2 p = coord - float2(padding);
    if (rtl > 0.5) p.x = width - p.x;
    float depth = max(0.0, -field(p));
    if (depth >= refraction) return content.eval(coord);
    float2 gradient = float2(
        field(p + float2(0.5, 0.0)) - field(p - float2(0.5, 0.0)),
        field(p + float2(0.0, 0.5)) - field(p - float2(0.0, 0.5))
    );
    gradient /= max(length(gradient), 0.0001);
    if (rtl > 0.5) gradient.x = -gradient.x;
    float edge = 1.0 - depth / refraction;
    float displacement = refraction * (1.0 - sqrt(max(0.0, 1.0 - edge * edge)));
    return content.eval(coord - gradient * displacement);
}
"""
