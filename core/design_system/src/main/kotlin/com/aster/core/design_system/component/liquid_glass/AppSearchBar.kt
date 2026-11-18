package com.aster.core.design_system.component.liquid_glass

import android.os.Build
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
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
    val surfaceColor = MaterialTheme.colorScheme.surface.copy(
        alpha = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) 0.94f else 0.4f
    )
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .heightIn(min = 56.dp)
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
        textStyle = MaterialTheme.typography.bodyLarge,
        placeholder = placeholder,
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = trailingIcon,
        singleLine = true,
        shape = Capsule(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = {
                onSearch(value)
                keyboardController?.hide()
            }
        ),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        )
    )
}
