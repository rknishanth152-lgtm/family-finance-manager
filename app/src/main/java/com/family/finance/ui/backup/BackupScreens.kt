package com.family.finance.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.family.finance.R
import com.family.finance.data.backup.BackupFiles
import com.family.finance.data.legacy.LegacyDbReader
import com.family.finance.data.legacy.LegacyFormatException
import com.family.finance.domain.backup.BackupCodec
import com.family.finance.domain.backup.BackupProblem
import com.family.finance.domain.backup.RestorePlan
import com.family.finance.domain.legacy.ImportReport
import com.family.finance.domain.legacy.RejectReason
import com.family.finance.domain.model.Money
import com.family.finance.domain.model.MoneyFormatter
import com.family.finance.ui.common.appViewModel
import com.family.finance.ui.common.formatDate
import com.family.finance.ui.components.MoneyText
import com.family.finance.ui.components.WarningCard
import com.family.finance.ui.components.isLargeFont
import com.family.finance.ui.theme.Spacing
import java.time.ZoneId

@Composable
fun BackupRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm = appViewModel { c ->
        val files = BackupFiles(context)
        BackupViewModel(
            service = c.backupService,
            familyName = { c.familyRepository.workspace.name },
            sync = c.syncState,
            clock = c.clock,
            writeText = files::write,
            readText = files::read,
        )
    }
    val status by vm.status.collectAsStateWithLifecycle()
    val canBackUp by vm.canBackUp.collectAsStateWithLifecycle()
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupCodec.MIME_TYPE)) { vm.saveTo(it) }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { vm.open(it) }

    LaunchedEffect(status) {
        (status as? BackupStatus.ChooseWhere)?.let { saveLauncher.launch(it.fileName) }
    }

    Page(R.string.more_backup, onBack) {
        Section(R.string.backup_section_create, R.string.backup_explain) {
            if (!canBackUp) Text(stringResource(R.string.backup_wait_sync), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Button(onClick = vm::createBackup, enabled = canBackUp && status != BackupStatus.Working, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_create_backup))
            }
        }
        Section(R.string.backup_section_restore, R.string.restore_explain) {
            OutlinedButton(
                onClick = { openLauncher.launch(arrayOf(BackupCodec.MIME_TYPE, "application/octet-stream", "*/*")) },
                enabled = status != BackupStatus.Working,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.action_choose_backup)) }
        }

        when (val s = status) {
            BackupStatus.Idle, is BackupStatus.ChooseWhere -> Unit
            BackupStatus.Working -> Working()
            is BackupStatus.Saved -> Message(
                pluralStringResource(
                    R.plurals.backup_saved, s.backup.transactions.size, s.backup.transactions.size,
                    MoneyFormatter.format(s.backup.totals.income), MoneyFormatter.format(s.backup.totals.expense),
                ),
                vm::dismiss,
            )
            is BackupStatus.Invalid -> WarningCard(stringResource(R.string.restore_invalid_title), stringResource(s.problem.messageRes()))
            is BackupStatus.Preview -> RestorePreview(s.plan, synced = canBackUp, onConfirm = vm::confirmRestore, onCancel = vm::dismiss)
            is BackupStatus.Restored -> Message(pluralStringResource(R.plurals.restore_done, s.result.total, s.result.total), vm::dismiss)
            BackupStatus.Failed -> WarningCard(stringResource(R.string.backup_failed_title), stringResource(R.string.backup_failed))
        }
    }
}

@Composable
private fun RestorePreview(plan: RestorePlan, synced: Boolean, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val b = plan.backup
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.restore_preview_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.restore_file_from, b.familyName, formatDate(b.createdAt.atZone(ZoneId.systemDefault()).toLocalDate())))
            CountLine(R.string.restore_in_file, b.transactions.size)
            AmountLine(R.string.summary_income, b.totals.income)
            AmountLine(R.string.summary_expense, b.totals.expense)
            CountLine(R.string.restore_already_here, plan.alreadyHere)
            if (plan.differ > 0) CountLine(R.string.restore_differ, plan.differ)
            if (plan.deletedHere > 0) CountLine(R.string.restore_deleted_here, plan.deletedHere)
            if (!synced) Text(stringResource(R.string.backup_wait_sync), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            CountLine(R.string.restore_will_add, plan.addCount)
            if (!plan.canRestore) {
                WarningCard(stringResource(R.string.restore_invalid_title), pluralStringResource(R.plurals.restore_broken, plan.brokenLinks.size, plan.brokenLinks.size))
            }
            if (plan.addCount == 0) Text(stringResource(R.string.restore_nothing_to_add))
            Buttons(
                confirm = pluralStringResource(R.plurals.action_add_missing, plan.addCount, plan.addCount),
                confirmEnabled = synced && plan.canRestore && plan.addCount > 0,
                onConfirm = onConfirm,
                onCancel = onCancel,
            )
        }
    }
}

@Composable
fun LegacyImportRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm = appViewModel { c ->
        val reader = LegacyDbReader(context)
        LegacyImportViewModel(
            service = c.backupService,
            me = c.currentUser,
            familyName = { c.familyRepository.workspace.name },
            clock = c.clock,
            readSource = reader::read,
            isFormatError = { it is LegacyFormatException },
            sync = c.syncState,
        )
    }
    val status by vm.status.collectAsStateWithLifecycle()
    val canImport by vm.canImport.collectAsStateWithLifecycle()
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { vm.check(it) }

    Page(R.string.more_import, onBack) {
        Section(R.string.import_section, R.string.import_explain) {
            Button(onClick = { openLauncher.launch(arrayOf("*/*")) }, enabled = status != ImportStatus.Working, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_choose_old_file))
            }
        }
        when (val s = status) {
            ImportStatus.Idle -> Unit
            ImportStatus.Working -> Working()
            is ImportStatus.Checked -> {
                ImportReportCard(s.report, s.plan)
                if (s.report.rejectedCount > 0) {
                    WarningCard(stringResource(R.string.import_rejected_title), stringResource(R.string.import_rejected_message))
                }
                if (!canImport) Text(stringResource(R.string.backup_wait_sync), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Buttons(
                    confirm = pluralStringResource(R.plurals.action_import_records, s.plan.addCount, s.plan.addCount),
                    confirmEnabled = canImport && s.plan.canRestore && s.plan.addCount > 0,
                    onConfirm = vm::import,
                    onCancel = vm::dismiss,
                )
            }
            is ImportStatus.Imported -> Message(pluralStringResource(R.plurals.import_done, s.result.total, s.result.total), vm::dismiss)
            ImportStatus.NotOldAppFile -> WarningCard(stringResource(R.string.import_wrong_file_title), stringResource(R.string.import_wrong_file))
            ImportStatus.Failed -> WarningCard(stringResource(R.string.backup_failed_title), stringResource(R.string.import_failed))
        }
    }
}

/** The migration report (PRD 13.3), as totals and counts. */
@Composable
private fun ImportReportCard(r: ImportReport, plan: RestorePlan) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.import_report_title), style = MaterialTheme.typography.titleMedium)
            if (r.firstDate != null && r.lastDate != null) {
                Text(stringResource(R.string.period_range, formatDate(r.firstDate), formatDate(r.lastDate)))
            }
            CountLine(R.string.import_rows_in_file, r.totalRows)
            CountLine(R.string.import_deleted_skipped, r.deletedSkipped)
            CountLine(R.string.import_entries_ok, r.imported)
            CountLine(R.string.import_rejected, r.rejectedCount)
            r.rejected.forEach { (reason, ids) -> CountLine(reason.labelRes(), ids.size, indent = true) }
            AmountLine(R.string.summary_income, r.incomeTotal)
            AmountLine(R.string.summary_expense, r.expenseTotal)
            AmountLine(R.string.summary_balance, r.net)
            r.accounts.forEach { a ->
                Text("${a.oldName}: ${pluralStringResource(R.plurals.reports_entry_count, a.entries, a.entries)}", style = MaterialTheme.typography.bodyMedium)
            }
            CountLine(R.string.import_categories_used, r.categoryMappings.size)
            CountLine(R.string.import_categories_unused, r.unusedCategories)
            CountLine(R.string.import_untitled, r.untitled)
            CountLine(R.string.import_bills, r.withBills)
            CountLine(R.string.import_duplicates, r.possibleDuplicateGroups)
            CountLine(R.string.import_notes, r.notesImported)
            CountLine(R.string.restore_already_here, plan.alreadyHere)
            CountLine(R.string.restore_will_add, plan.addCount)
        }
    }
}

// ---- shared pieces ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Page(@StringRes title: Int, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) { content() }
    }
}

@Composable
private fun Section(@StringRes title: Int, @StringRes explain: Int, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(explain), style = MaterialTheme.typography.bodyMedium)
            content()
        }
    }
}

@Composable
private fun Working() = Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
    Text(stringResource(R.string.backup_working))
    LinearProgressIndicator(Modifier.fillMaxWidth())
}

@Composable
private fun Message(text: String, onDone: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = onDone) { Text(stringResource(R.string.action_done)) }
        }
    }
}

@Composable
private fun CountLine(@StringRes label: Int, count: Int, indent: Boolean = false) {
    val start = if (indent) Modifier.padding(start = Spacing.md) else Modifier
    if (isLargeFont()) {
        Column(start.fillMaxWidth()) {
            Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
            Text(count.toString(), style = MaterialTheme.typography.titleMedium)
        }
    } else {
        Row(start.fillMaxWidth()) {
            Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(count.toString(), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun AmountLine(@StringRes label: Int, amount: Money) {
    if (isLargeFont()) {
        Column(Modifier.fillMaxWidth()) {
            Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
            MoneyText(amount, style = MaterialTheme.typography.titleMedium)
        }
    } else {
        Row(Modifier.fillMaxWidth()) {
            Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            MoneyText(amount, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun Buttons(confirm: String, confirmEnabled: Boolean, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Button(onClick = onConfirm, enabled = confirmEnabled, modifier = Modifier.fillMaxWidth()) { Text(confirm) }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_cancel)) }
    }
}

@StringRes
private fun BackupProblem.messageRes(): Int = when (this) {
    BackupProblem.UNREADABLE -> R.string.restore_problem_unreadable
    BackupProblem.NOT_A_BACKUP -> R.string.restore_problem_not_backup
    BackupProblem.NEWER_VERSION -> R.string.restore_problem_newer
    BackupProblem.BAD_RECORD, BackupProblem.DUPLICATE_ID -> R.string.restore_problem_bad_record
    BackupProblem.TOTALS_MISMATCH -> R.string.restore_problem_totals
}

@StringRes
private fun RejectReason.labelRes(): Int = when (this) {
    RejectReason.NO_AMOUNT -> R.string.import_reject_no_amount
    RejectReason.BOTH_AMOUNTS -> R.string.import_reject_both_amounts
    RejectReason.NOT_EXACT_PAISE -> R.string.import_reject_not_exact
    RejectReason.UNKNOWN_CATEGORY -> R.string.import_reject_unknown_category
    RejectReason.UNKNOWN_PAYMENT_METHOD -> R.string.import_reject_unknown_account
    RejectReason.CATEGORY_KIND_MISMATCH -> R.string.import_reject_kind_mismatch
    RejectReason.AMOUNT_TOO_LARGE -> R.string.import_reject_too_large
}
