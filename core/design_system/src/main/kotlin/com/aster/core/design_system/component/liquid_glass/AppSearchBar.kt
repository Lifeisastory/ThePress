package com.aster.core.design_system.component.liquid_glass

import android.os.Build
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule

/**
 * 业务无关的受控搜索框，查询与结果处理由调用方负责
 * [backdrop] 只记录搜索框后方的内容，搜索框本身必须作为采样内容的兄弟节点绘制
 * [placeholder] 与 [trailingIcon] 由调用方提供文案和操作（例如清空）
 * 默认高度为 48.dp，与 AppIconButton 的直径一致。
 */
@Composable
fun AppSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    placeholder: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    onSearch: (String) -> Unit = {}
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val interactionSource = remember { MutableInteractionSource() }
    val colors = MaterialTheme.colorScheme
    val surfaceColor = MaterialTheme.colorScheme.surface.copy(
        alpha = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) 0.94f else 0.4f
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .height(48.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(8.dp.toPx())
                    lens(24.dp.toPx(), 24.dp.toPx())
                },
                onDrawSurface = { drawRect(surfaceColor) }
            ),
        enabled = enabled,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.38f)
        ),
        cursorBrush = SolidColor(colors.primary),
        interactionSource = interactionSource,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = {
                onSearch(value)
                keyboardController?.hide()
            }
        ),
        decorationBox = { innerTextField ->
            TextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = innerTextField,
                enabled = enabled,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                placeholder = placeholder,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = trailingIcon,
                // 单行内容垂直居中，避免默认上下内边距在 48.dp 高度内挤压文字。
                contentPadding = TextFieldDefaults.contentPaddingWithoutLabel(top = 0.dp, bottom = 0.dp),
                container = {}
            )
        }
    )
}
