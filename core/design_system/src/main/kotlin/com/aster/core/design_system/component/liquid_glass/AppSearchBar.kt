package com.aster.core.design_system.component.liquid_glass

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import com.kyant.backdrop.Backdrop

/** 受控搜索框，查询与结果处理由调用方负责 */
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
    val keyboard = LocalSoftwareKeyboardController.current
    AppInputBar(
        value, onValueChange, backdrop, modifier,
        placeholder = placeholder, leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = trailingIcon, enabled = enabled, singleLine = true, imeAction = ImeAction.Search,
        onSubmit = { onSearch(value); keyboard?.hide() }
    )
}
