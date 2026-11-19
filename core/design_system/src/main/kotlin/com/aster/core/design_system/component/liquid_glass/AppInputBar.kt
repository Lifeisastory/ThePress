package com.aster.core.design_system.component.liquid_glass

import android.os.Build
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.RoundedRectangle

/** 通用玻璃输入容器，与搜索框共用样式，采样内容必须位于兄弟节点 */
@Composable
fun AppInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    singleLine: Boolean = false,
    imeAction: ImeAction = ImeAction.Send,
    onSubmit: () -> Unit = {},
    drawSurface: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val colors = MaterialTheme.colorScheme
    val surface = colors.surface.copy(alpha = if (Build.VERSION.SDK_INT < 31) 0.94f else 0.4f)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.heightIn(min = 48.dp, max = if (singleLine) 48.dp else 144.dp).then(if (drawSurface) Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { RoundedRectangle(24.dp) },
            effects = { vibrancy(); blur(8.dp.toPx()); lens(24.dp.toPx(), 24.dp.toPx()) },
            onDrawSurface = { drawRect(surface) }
        ) else Modifier),
        enabled = enabled,
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else 4,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface.copy(alpha = if (enabled) 1f else 0.38f)),
        cursorBrush = SolidColor(colors.primary),
        interactionSource = interaction,
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(onSend = { onSubmit() }, onSearch = { onSubmit() }, onDone = { onSubmit() }),
        decorationBox = { inner ->
            TextFieldDefaults.DecorationBox(
                value = value, innerTextField = inner, enabled = enabled, singleLine = singleLine,
                visualTransformation = VisualTransformation.None, interactionSource = interaction,
                placeholder = placeholder, leadingIcon = leadingIcon, trailingIcon = trailingIcon,
                contentPadding = TextFieldDefaults.contentPaddingWithoutLabel(
                    top = if (singleLine) 0.dp else 12.dp, bottom = if (singleLine) 0.dp else 12.dp
                ), container = {}
            )
        }
    )
}
