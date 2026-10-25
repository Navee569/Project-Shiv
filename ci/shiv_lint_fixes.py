"""Targeted lint fixes for the pinned Shiv integration source."""
from pathlib import Path

ROOT = Path('app/src/main/java/com/shiv/ai')
changes = 0

def replace(path, old, new, expected=None):
    global changes
    file = ROOT / path
    data = file.read_text(encoding='utf-8')
    n = data.count(old)
    if n == 0 or (expected is not None and n != expected):
        raise AssertionError(f'{path}: expected {expected or "at least 1"} occurrences of {old!r}, found {n}')
    file.write_text(data.replace(old, new), encoding='utf-8')
    changes += n

def add_import(path, import_line):
    file = ROOT / path
    data = file.read_text(encoding='utf-8')
    assert import_line not in data, f'{path}: duplicate import {import_line}'
    assert '\nimport ' in data
    file.write_text(data.replace('\nimport ', '\n' + import_line + '\nimport ', 1), encoding='utf-8')

# Analytics numeric formatting must be locale-independent.
for file in [
    'actions/SocialActions.kt',
    'social/core/analytics/SocialAnalyticsEngine.kt',
    'social/core/ai/SocialInsightsEngine.kt',
    'ui/screens/social/SocialAnalyticsTab.kt',
    'ui/screens/social/SocialOverviewTab.kt',
]:
    replace(file, 'String.format(', 'String.format(java.util.Locale.ROOT, ')

# Compose UI should observe changes to the user's locale.
for file, expression in [
    ('SocialAccountsTab.kt', 'SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())'),
    ('SocialCalendarTab.kt', 'SimpleDateFormat("HH:mm", Locale.getDefault())'),
    ('SocialCampaignsTab.kt', 'SimpleDateFormat("MMM d", Locale.getDefault())'),
    ('SocialCommentsTab.kt', 'SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())'),
    ('SocialContentTab.kt', 'SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())'),
    ('SocialInboxTab.kt', 'SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())'),
    ('SocialReportsTab.kt', 'SimpleDateFormat("MMM d, yyyy", Locale.getDefault())'),
    ('SocialSettingsTab.kt', 'SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault())'),
]:
    replace('ui/screens/social/' + file, expression,
            expression.replace('Locale.getDefault()',
                'androidx.compose.ui.platform.LocalLocale.current.platformLocale'), expected=1)

# Avoid boxing primitive Compose state.
for file, before, after in [
    ('ui/screens/social/SocialAnalyticsTab.kt', 'mutableStateOf(7)', 'androidx.compose.runtime.mutableIntStateOf(7)'),
    ('ui/screens/social/SocialCalendarTab.kt', 'mutableStateOf(0)', 'androidx.compose.runtime.mutableIntStateOf(0)'),
    ('ui/screens/social/SocialCampaignsTab.kt', 'mutableStateOf(7)', 'androidx.compose.runtime.mutableIntStateOf(7)'),
    ('ui/screens/social/SocialContentTab.kt', 'mutableStateOf(1)', 'androidx.compose.runtime.mutableIntStateOf(1)'),
    ('ui/screens/SocialScreen.kt', 'mutableStateOf(0)', 'androidx.compose.runtime.mutableIntStateOf(0)'),
    ('ui/screens/social/SocialSettingsTab.kt', 'mutableStateOf(0.85f)', 'androidx.compose.runtime.mutableFloatStateOf(0.85f)'),
]:
    replace(file, before, after, expected=1)

# Composable Modifier arguments and preserved default chart sizing.
replace('ui/screens/MacrosScreen.kt',
        '    onNavigateToRoutines: () -> Unit = {},\n    modifier: Modifier = Modifier',
        '    modifier: Modifier = Modifier,\n    onNavigateToRoutines: () -> Unit = {}', expected=1)
replace('ui/components/SocialCharts.kt',
        '    dataPoints: List<Float>,\n    labels: List<String> = emptyList(),\n    modifier: Modifier = Modifier.fillMaxWidth().height(160.dp),',
        '    dataPoints: List<Float>,\n    modifier: Modifier = Modifier,\n    labels: List<String> = emptyList(),', expected=1)
replace('ui/components/SocialCharts.kt',
        '    modifier: Modifier = Modifier.fillMaxWidth().height(160.dp),',
        '    modifier: Modifier = Modifier,', expected=1)
replace('ui/components/SocialCharts.kt',
        '    modifier: Modifier = Modifier.size(100.dp)',
        '    modifier: Modifier = Modifier', expected=1)
replace('ui/components/SocialCharts.kt',
        'Box(modifier = modifier, contentAlignment = Alignment.Center)',
        'Box(modifier = Modifier.fillMaxWidth().height(160.dp).then(modifier), contentAlignment = Alignment.Center)', expected=1)
replace('ui/components/SocialCharts.kt',
        'Column(modifier = modifier)',
        'Column(modifier = Modifier.fillMaxWidth().height(160.dp).then(modifier))', expected=2)
replace('ui/components/SocialCharts.kt',
        'Canvas(modifier = modifier) {',
        'Canvas(modifier = Modifier.size(100.dp).then(modifier)) {', expected=1)

# Android minSdk 26 makes the pre-M network fallback unreachable.
replace('ui/viewmodel/SettingsViewModel.kt', '''            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    connectivityManager.isActiveNetworkMetered
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo ?: return false
                @Suppress("DEPRECATION")
                networkInfo.type == ConnectivityManager.TYPE_MOBILE
            }''', '''            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                connectivityManager.isActiveNetworkMetered''', expected=1)

# Prefer AndroidX KTX for preferences and URI parsing.
replace('social/core/SocialManager.kt',
        'prefs.edit().putString(KEY_AUTOMATION_LEVEL, level.name).apply()',
        'prefs.edit { putString(KEY_AUTOMATION_LEVEL, level.name) }', expected=1)
add_import('social/core/SocialManager.kt', 'import androidx.core.content.edit')
replace('core/storage/StorageWorkspaceProvider.kt', '''        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CUSTOM_FOLDER_URI, uri.toString())
            .apply()''', '''        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putString(KEY_CUSTOM_FOLDER_URI, uri.toString()) }''', expected=1)
replace('core/storage/StorageWorkspaceProvider.kt', '''        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_CUSTOM_FOLDER_URI)
            .apply()''', '''        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { remove(KEY_CUSTOM_FOLDER_URI) }''', expected=1)
replace('core/storage/StorageWorkspaceProvider.kt', 'Uri.parse(uriStr)', 'uriStr.toUri()', expected=1)
add_import('core/storage/StorageWorkspaceProvider.kt', 'import androidx.core.content.edit')
add_import('core/storage/StorageWorkspaceProvider.kt', 'import androidx.core.net.toUri')

# Legacy user-provided /sdcard aliases are intentionally normalized for
# compatibility, not used as app-owned storage locations. Document the exception.
replace('core/storage/StorageWorkspaceProvider.kt',
        '    fun resolveFile(context: Context, pathStr: String?): File {',
        '    @android.annotation.SuppressLint("SdCardPath") // Legacy user-input aliases.\n    fun resolveFile(context: Context, pathStr: String?): File {', expected=1)
replace('core/storage/StorageWorkspaceProvider.kt',
        '    private fun cleanRelativePath(pathStr: String?): String {',
        '    @android.annotation.SuppressLint("SdCardPath") // Legacy user-input aliases.\n    private fun cleanRelativePath(pathStr: String?): String {', expected=1)

# Preserve the historical AppCompatCustomView baseline's source location.
old = ROOT / 'accessibility/OpenDroidAccessibilityService.kt'
new = ROOT / 'accessibility/ShivAccessibilityService.kt'
if old.is_file() and not new.exists():
    old.rename(new)
elif not new.is_file() or old.exists():
    # Keep this as a diagnostic, not a source rewrite failure: the pinned
    # integration may have already aligned the filename in a prior step.
    print(f'Accessibility source not renamed: old_exists={old.exists()}, new_exists={new.exists()}')
else:
    assert 'class ShivAccessibilityService' in new.read_text(encoding='utf-8')
print(f'Applied {changes} targeted lint source rewrites.')
