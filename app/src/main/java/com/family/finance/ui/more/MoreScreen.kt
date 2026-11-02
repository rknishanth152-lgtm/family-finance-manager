package com.family.finance.ui.more

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.family.finance.AppLanguage
import com.family.finance.FamilyFinanceApplication
import com.family.finance.R
import com.family.finance.ui.common.formatDate
import java.io.File
import com.family.finance.ui.components.SectionHeader
import com.family.finance.ui.theme.Spacing

/** More, with the crash log read from the app's private file. */
@Composable
fun MoreRoute(
    onAccounts: () -> Unit,
    onCategories: () -> Unit,
    onNotebook: () -> Unit,
    onFamily: () -> Unit,
    onBackup: () -> Unit,
    onImport: () -> Unit,
    isAdmin: Boolean,
) {
    val context = LocalContext.current
    val crashLog = (context.applicationContext as FamilyFinanceApplication).crashLog
    val crashes = viewModel { CrashLogViewModel(crashLog) }
    val summary by crashes.summary.collectAsStateWithLifecycle()
    val shareTitle = stringResource(R.string.crash_log_share_title)
    MoreScreen(
        onAccounts, onCategories, onNotebook, onFamily, onBackup, onImport, isAdmin,
        crashSummary = summary,
        onShareCrashLog = { shareCrashLog(context, crashLog.copyForSharing(), shareTitle) },
        onClearCrashLog = crashes::clear,
    )
}

/** Read access is granted only to the app the person picks, only for this file. */
private fun shareCrashLog(context: Context, file: File, title: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.exports", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(file.name, uri)
    context.startActivity(Intent.createChooser(send, title))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    onAccounts: () -> Unit,
    onCategories: () -> Unit,
    onNotebook: () -> Unit,
    onFamily: () -> Unit,
    onBackup: () -> Unit,
    onImport: () -> Unit,
    /** Only the family admin may import the old app's records (PRD 13). */
    isAdmin: Boolean,
    crashSummary: CrashSummary = CrashSummary(0, null),
    onShareCrashLog: () -> Unit = {},
    onClearCrashLog: () -> Unit = {},
) {
    var choosingLanguage by remember { mutableStateOf(false) }
    var showingCrashLog by remember { mutableStateOf(false) }
    val current = AppLanguage.current()

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_more)) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            MoreItem(R.drawable.ic_sticky_note_2, R.string.more_notebook, onNotebook)
            MoreItem(R.drawable.ic_account_balance_wallet, R.string.more_accounts, onAccounts)
            MoreItem(R.drawable.ic_category, R.string.more_categories, onCategories)
            MoreItem(R.drawable.ic_group, R.string.more_family, onFamily)
            MoreItem(R.drawable.ic_archive, R.string.more_backup, onBackup)
            if (isAdmin) MoreItem(R.drawable.ic_unarchive, R.string.more_import, onImport)
            MoreItem(
                R.drawable.ic_language,
                R.string.more_language,
                onClick = { choosingLanguage = true },
                supporting = stringResource(R.string.language_current, stringResource(current.labelRes)),
            )
            MoreItem(
                R.drawable.ic_warning,
                R.string.more_crash_log,
                onClick = { showingCrashLog = true },
                supporting = crashSummary.lastCrashDay?.let {
                    pluralStringResource(R.plurals.crash_log_summary, crashSummary.count, crashSummary.count, formatDate(it))
                } ?: stringResource(R.string.crash_log_none),
            )
            HorizontalDivider()
            // PRD 5 lists these; they arrive in later phases and are shown disabled until then.
            SectionHeader(R.string.more_coming_later)
            listOf(
                R.string.more_budgets, R.string.more_recurring,
                R.string.more_privacy, R.string.more_about,
            ).forEach { label ->
                ListItem(
                    headlineContent = { Text(stringResource(label)) },
                    supportingContent = { Text(stringResource(R.string.more_later_phase)) },
                )
            }
        }
    }

    if (showingCrashLog) {
        val hasCrashes = crashSummary.count > 0
        AlertDialog(
            onDismissRequest = { showingCrashLog = false },
            title = { Text(stringResource(R.string.more_crash_log)) },
            text = { Text(stringResource(if (hasCrashes) R.string.crash_log_explain else R.string.crash_log_none)) },
            confirmButton = {
                if (hasCrashes) {
                    TextButton(onClick = { showingCrashLog = false; onShareCrashLog() }) { Text(stringResource(R.string.action_share)) }
                } else {
                    TextButton(onClick = { showingCrashLog = false }) { Text(stringResource(R.string.action_close)) }
                }
            },
            dismissButton = {
                if (hasCrashes) {
                    TextButton(onClick = { showingCrashLog = false; onClearCrashLog() }) { Text(stringResource(R.string.action_clear_crash_log)) }
                }
            },
        )
    }

    if (choosingLanguage) {
        LanguageDialog(
            current = current,
            onSelect = { language ->
                choosingLanguage = false
                if (language != current) AppLanguage.select(language)
            },
            onDismiss = { choosingLanguage = false },
        )
    }
}

@Composable
private fun LanguageDialog(current: AppLanguage, onSelect: (AppLanguage) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.more_language)) },
        text = {
            Column(Modifier.selectableGroup()) {
                AppLanguage.entries.forEach { language ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = language == current, role = Role.RadioButton) { onSelect(language) }
                            .padding(vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // The row is the touch target; the radio button is only the visual state.
                        RadioButton(selected = language == current, onClick = null)
                        Text(
                            stringResource(language.labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = Spacing.md),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun MoreItem(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    onClick: () -> Unit,
    supporting: String? = null,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        headlineContent = { Text(stringResource(label)) },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) },
    )
}
