package com.aster.core.design_system.component

import android.util.TypedValue
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tables.TableAwareMovementMethod
import io.noties.markwon.movement.MovementMethodPlugin

/** 原生 Markdown 渲染，不加载网页，链接通过调用方进入应用导航 */
@Composable
fun AppMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    onOpenLink: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val palette = MaterialTheme.colorScheme
    val onLink by rememberUpdatedState(onOpenLink)
    val textSize = with(density) { MaterialTheme.typography.bodyLarge.fontSize.toPx() }
    val markwon = remember(context, palette) {
        Markwon.builder(context)
            .usePlugin(TablePlugin.create(context))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(MovementMethodPlugin.create(TableAwareMovementMethod.create()))
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder.linkColor(palette.primary.toArgb())
                        .codeTextColor(palette.onSurface.toArgb())
                        .codeBackgroundColor(palette.surfaceContainerHighest.toArgb())
                        .blockQuoteColor(palette.primary.copy(alpha = 0.5f).toArgb())
                        .headingBreakHeight(0)
                }
                override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                    builder.linkResolver { _, link -> if (isMarkdownWebLink(link)) onLink(link) }
                }
            }).build()
    }
    val rendered = remember(markdown, markwon) { markwon.toMarkdown(markdown) }
    AndroidView(
        factory = { TextView(it).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            includeFontPadding = false
            setTextIsSelectable(true)
            setPadding(0, 0, 0, 0)
        } },
        modifier = modifier.semantics { text = AnnotatedString(rendered.toString()) },
        update = { view ->
            view.setTextColor(color.toArgb())
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
            if (view.tag !== rendered) {
                markwon.setParsedMarkdown(view, rendered)
                view.tag = rendered
            }
        }
    )
}

internal fun isMarkdownWebLink(link: String): Boolean = try {
    val uri = java.net.URI(link)
    uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()
} catch (_: Exception) { false }
