package com.aster.feature.me.impl

import android.os.Build
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aster.core.data.repository.NewsRepository
import com.aster.core.data.repository.UserPreferencesRepository
import com.aster.core.design_system.component.AppLoadingIndicator
import com.aster.core.model.ThemeColor
import com.aster.core.model.ThemeMode
import com.aster.core.design_system.component.AppSettingsGroup as SettingsGroup

@Composable
fun ProfilePage(
    contentPadding: PaddingValues,
    repository: NewsRepository,
    preferencesRepository: UserPreferencesRepository,
    onOpenLink: (String) -> Unit
) {
    val factory = remember(repository, preferencesRepository) {
        ProfileViewModel.factory(repository, preferencesRepository)
    }
    val viewModel: ProfileViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var showCredits by rememberSaveable { mutableStateOf(false) }
    val version = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(context.getString(it))
            viewModel.dismissMessage()
        }
    }

    Box(Modifier.fillMaxSize().testTag("profile_page")) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("profile_settings"),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                    Text(
                        stringResource(com.aster.feature.me.api.R.string.feature_me_title),
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.testTag("page_title")
                    )
                    Text(stringResource(R.string.profile_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                SettingsGroup(stringResource(R.string.profile_appearance)) {
                    Text(stringResource(R.string.profile_theme), style = MaterialTheme.typography.titleMedium)
                    val editable = state.appearance != null && !state.saving && !state.appearanceReadFailed
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = state.appearance?.mode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                enabled = editable,
                                label = { Text(stringResource(mode.label())) },
                                modifier = Modifier.testTag("theme_mode_${mode.name}")
                            )
                        }
                    }
                    HorizontalDivider()
                    Text(stringResource(R.string.profile_color), style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeColor.entries.forEach { color ->
                            FilterChip(
                                selected = state.appearance?.color == color,
                                onClick = { viewModel.setThemeColor(color) },
                                enabled = editable,
                                label = { Text(stringResource(color.label())) },
                                leadingIcon = {
                                    Box(Modifier.size(16.dp).background(color.swatch(), CircleShape))
                                },
                                modifier = Modifier.testTag("theme_color_${color.name}")
                            )
                        }
                    }
                    Text(
                        stringResource(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                            R.string.profile_dynamic_color_hint else R.string.profile_dynamic_color_fallback),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.appearanceReadFailed) {
                        Text(stringResource(R.string.profile_preferences_read_failed), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            item {
                SettingsGroup(stringResource(R.string.profile_storage)) {
                    Text(stringResource(R.string.profile_cache), style = MaterialTheme.typography.titleMedium)
                    val cache = state.cache
                    when {
                        state.cacheReadFailed -> Text(stringResource(R.string.profile_cache_read_failed), color = MaterialTheme.colorScheme.error)
                        cache == null -> AppLoadingIndicator()
                        else -> Text(
                            stringResource(R.string.profile_cache_size, Formatter.formatShortFileSize(context, cache.contentBytes), cache.articleCount),
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                    Text(
                        stringResource(R.string.profile_cache_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.clearing) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppLoadingIndicator()
                            Text(stringResource(R.string.profile_clearing))
                        }
                    } else if (confirmClear) {
                        Text(stringResource(R.string.profile_cache_confirm))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.profile_cancel)) }
                            Button(onClick = { confirmClear = false; viewModel.clearCache() }) {
                                Text(stringResource(R.string.profile_confirm_clear))
                            }
                        }
                    } else {
                        TextButton(
                            onClick = { confirmClear = true },
                            enabled = state.cache != null && !state.cacheReadFailed,
                            modifier = Modifier.testTag("clear_news_cache")
                        ) { Text(stringResource(R.string.profile_clear_cache)) }
                    }
                }
            }
            item {
                SettingsGroup(stringResource(R.string.profile_about)) {
                    Text(stringResource(R.string.profile_app_version, version), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.profile_app_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider()
                    SettingsLink(
                        stringResource(R.string.profile_project), "Lifeisastory / ThePress",
                        onClick = { onOpenLink("https://github.com/Lifeisastory/ThePress") }
                    )
                    SettingsLink(
                        stringResource(R.string.profile_credits),
                        stringResource(if (showCredits) R.string.profile_collapse else R.string.profile_credits_hint),
                        onClick = { showCredits = !showCredits }
                    )
                    if (showCredits) {
                        HorizontalDivider()
                        SettingsLink("NewsNow", stringResource(R.string.profile_credit_newsnow)) {
                            onOpenLink("https://github.com/newsnext/newsnow")
                        }
                        SettingsLink("Android / Jetpack Compose", stringResource(R.string.profile_credit_android)) {
                            onOpenLink("https://developer.android.com/jetpack/compose")
                        }
                        SettingsLink("Backdrop / Shapes", stringResource(R.string.profile_credit_backdrop)) {
                            onOpenLink("https://github.com/Kyant0/AndroidLiquidGlass")
                        }
                        SettingsLink("Coil", stringResource(R.string.profile_credit_coil)) { onOpenLink("https://coil-kt.github.io/coil/") }
                        SettingsLink("OkHttp / Retrofit", stringResource(R.string.profile_credit_network)) { onOpenLink("https://square.github.io/") }
                        SettingsLink("jsoup", stringResource(R.string.profile_credit_jsoup)) { onOpenLink("https://jsoup.org/") }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = contentPadding.calculateBottomPadding()))
    }
}

@Composable
private fun SettingsLink(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun ThemeMode.label() = when (this) {
    ThemeMode.SYSTEM -> R.string.profile_theme_system
    ThemeMode.LIGHT -> R.string.profile_theme_light
    ThemeMode.DARK -> R.string.profile_theme_dark
}

private fun ThemeColor.label() = when (this) {
    ThemeColor.SYSTEM -> R.string.profile_color_system
    ThemeColor.PURPLE -> R.string.profile_color_purple
    ThemeColor.BLUE -> R.string.profile_color_blue
    ThemeColor.GREEN -> R.string.profile_color_green
}

@Composable
private fun ThemeColor.swatch() = when (this) {
    ThemeColor.SYSTEM -> MaterialTheme.colorScheme.primary
    ThemeColor.PURPLE -> Color(0xFF6650A4)
    ThemeColor.BLUE -> Color(0xFF245FA6)
    ThemeColor.GREEN -> Color(0xFF006C4F)
}
