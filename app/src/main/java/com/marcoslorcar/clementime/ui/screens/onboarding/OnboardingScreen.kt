package com.marcoslorcar.clementime.ui.screens.onboarding

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marcoslorcar.clementime.R
import com.marcoslorcar.clementime.ui.components.AppSkeletonPreview
import com.marcoslorcar.clementime.ui.theme.getThemeColorScheme
import com.marcoslorcar.clementime.utils.fadingEdges
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { 5 })
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                userScrollEnabled = false
            ) { page ->
                when (page) {
                    0 -> WelcomePage()
                    1 -> LanguagePage(
                        selectedLanguage = uiState.appLanguage,
                        onLanguageSelected = viewModel::setAppLanguage
                    )
                    2 -> ThemePage(
                        themeMode = uiState.themeMode,
                        selectedTheme = uiState.selectedTheme,
                        onThemeModeSelected = viewModel::setThemeMode,
                        onColorThemeSelected = viewModel::setSelectedTheme
                    )
                    3 -> AutoUpdatePage(
                        selectedInterval = uiState.autoUpdateIntervalHours,
                        onIntervalSelected = { hours -> viewModel.setAutoUpdateIntervalHours(hours, context) }
                    )
                    4 -> ReadyPage()
                }
            }

            OnboardingNavigation(
                currentPage = pagerState.currentPage,
                pageCount = pagerState.pageCount,
                onNext = {
                    if (pagerState.currentPage < pagerState.pageCount - 1) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    } else {
                        coroutineScope.launch {
                            viewModel.completeOnboarding()
                            onFinish()
                        }
                    }
                },
                onBack = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                    }
                },
                onSkip = {
                    coroutineScope.launch {
                        viewModel.completeOnboarding()
                        onFinish()
                    }
                }
            )
        }
    }
}

@Composable
fun WelcomePage() {
    OnboardingPageContent(
        icon = Icons.Default.Lightbulb,
        title = stringResource(R.string.onboarding_welcome_title),
        description = stringResource(R.string.onboarding_welcome_desc)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagePage(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Translate,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.onboarding_language_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_language_desc),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            val languages = listOf("en" to stringResource(R.string.lang_en), "es" to stringResource(R.string.lang_es))
            languages.forEachIndexed { index, (code, label) ->
                SegmentedButton(
                    selected = selectedLanguage == code,
                    onClick = { onLanguageSelected(code) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = languages.size),
                    label = { 
                        Text(
                            text = label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemePage(
    themeMode: String,
    selectedTheme: String,
    onThemeModeSelected: (String) -> Unit,
    onColorThemeSelected: (String) -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Icon(
            imageVector = Icons.Default.Brush,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.onboarding_theme_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_theme_desc),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.theme_setting_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            val modes = listOf(
                "system" to stringResource(R.string.theme_system),
                "light" to stringResource(R.string.theme_light),
                "dark" to stringResource(R.string.theme_dark)
            )
            modes.forEachIndexed { index, (id, label) ->
                SegmentedButton(
                    selected = themeMode == id,
                    onClick = { onThemeModeSelected(id) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                    label = { 
                        Text(
                            text = label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.theme_preview_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        val themes = listOf(
            "clementine" to stringResource(R.string.theme_clementine),
            "blueberry" to stringResource(R.string.theme_blueberry),
            "matcha" to stringResource(R.string.theme_matcha),
            "espresso" to stringResource(R.string.theme_espresso),
            "grape" to stringResource(R.string.theme_grape)
        )

        val themeListState = rememberLazyListState()
        LazyRow(
            state = themeListState,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .fadingEdges(themeListState, horizontal = true)
        ) {
            items(themes) { (id, name) ->
                val systemIsDark = androidx.compose.foundation.isSystemInDarkTheme()
                val isPreviewDark = when (themeMode) {
                    "dark" -> true
                    "light" -> false
                    else -> systemIsDark
                }
                val previewColorScheme = if (id == "clementine" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (isPreviewDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                } else {
                    getThemeColorScheme(id, isPreviewDark)
                }

                AppSkeletonPreview(
                    name = name,
                    colorScheme = previewColorScheme,
                    isSelected = selectedTheme == id,
                    onClick = { onColorThemeSelected(id) }
                )
            }
        }
    }
}

@Composable
fun ReadyPage() {
    OnboardingPageContent(
        icon = Icons.Default.CheckCircle,
        title = stringResource(R.string.onboarding_ready_title),
        description = stringResource(R.string.onboarding_ready_desc)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoUpdatePage(
    selectedInterval: Int,
    onIntervalSelected: (Int) -> Unit
) {
    val context = LocalContext.current
    var isPermissionGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        isPermissionGranted = granted
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Sync,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.onboarding_autoupdate_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_autoupdate_desc),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.onboarding_autoupdate_frequency),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            val intervals = listOf(6, 12, 24, 0)
            intervals.forEachIndexed { index, hours ->
                val label = when (hours) {
                    6 -> "6h"
                    12 -> "12h"
                    24 -> "24h"
                    else -> stringResource(R.string.auto_update_interval_off_short)
                }
                SegmentedButton(
                    selected = selectedInterval == hours,
                    onClick = {
                        onIntervalSelected(hours)
                        if (hours > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isPermissionGranted) {
                            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = intervals.size)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && selectedInterval > 0) {
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedButton(
                onClick = {
                    if (!isPermissionGranted) {
                        permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isPermissionGranted) {
                        stringResource(R.string.onboarding_notifications_permission_granted)
                    } else {
                        stringResource(R.string.onboarding_notifications_permission_btn)
                    }
                )
            }
        }
    }
}

@Composable
fun OnboardingPageContent(
    icon: ImageVector,
    title: String,
    description: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 22.sp
        )
    }
}

@Composable
fun OnboardingNavigation(
    currentPage: Int,
    pageCount: Int,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (currentPage > 0) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.onboarding_back))
            }
        } else {
            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.onboarding_skip))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(pageCount) { index ->
                val color = if (index == currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .padding(1.dp)
                        .size(6.dp)
                        .align(Alignment.CenterVertically)
                ) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(color = color)
                    }
                }
            }
        }

        Button(onClick = onNext) {
            Text(
                text = if (currentPage == pageCount - 1) stringResource(R.string.onboarding_finish) else stringResource(R.string.onboarding_next)
            )
            if (currentPage < pageCount - 1) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}
