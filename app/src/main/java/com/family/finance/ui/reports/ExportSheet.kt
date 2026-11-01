package com.family.finance.ui.reports

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.family.finance.AppLanguage
import com.family.finance.R
import com.family.finance.data.export.ExportFormat
import com.family.finance.ui.components.ChoiceChips
import com.family.finance.ui.components.isLargeFont
import com.family.finance.ui.theme.Spacing

/** Choose file type and the file's own language, then save or share (PRD 12, 7.4). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(
    export: ExportStatus,
    onExport: (ExportFormat, AppLanguage, ExportTarget) -> Unit,
    onDismiss: () -> Unit,
) {
    var format by rememberSaveable { mutableStateOf(ExportFormat.PDF) }
    // The file's language starts as the app's language but can be changed for this file only.
    var language by rememberSaveable { mutableStateOf(AppLanguage.current()) }
    val working = export == ExportStatus.Working

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(stringResource(R.string.export_title), style = MaterialTheme.typography.titleLarge)

            Text(stringResource(R.string.export_format), style = MaterialTheme.typography.titleSmall)
            ChoiceChips(ExportFormat.entries, format, { stringResource(it.labelRes()) }, { format = it })

            Text(stringResource(R.string.export_language), style = MaterialTheme.typography.titleSmall)
            ChoiceChips(AppLanguage.entries, language, { stringResource(it.labelRes) }, { language = it })
            Text(stringResource(R.string.export_language_hint), style = MaterialTheme.typography.bodySmall)

            Text(
                stringResource(R.string.export_private_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (working) {
                Text(stringResource(R.string.export_working), style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            val save: @Composable (Modifier) -> Unit = { m ->
                Button(onClick = { onExport(format, language, ExportTarget.SAVE) }, enabled = !working, modifier = m) {
                    Icon(painterResource(R.drawable.ic_download), contentDescription = null)
                    Text(stringResource(R.string.action_save_file), Modifier.padding(start = Spacing.sm))
                }
            }
            val share: @Composable (Modifier) -> Unit = { m ->
                OutlinedButton(onClick = { onExport(format, language, ExportTarget.SHARE) }, enabled = !working, modifier = m) {
                    Icon(painterResource(R.drawable.ic_share), contentDescription = null)
                    Text(stringResource(R.string.action_share), Modifier.padding(start = Spacing.sm))
                }
            }
            if (isLargeFont()) {
                save(Modifier.fillMaxWidth())
                share(Modifier.fillMaxWidth())
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    save(Modifier.weight(1f))
                    share(Modifier.weight(1f))
                }
            }
        }
    }
}

@StringRes
private fun ExportFormat.labelRes(): Int = when (this) {
    ExportFormat.PDF -> R.string.export_format_pdf
    ExportFormat.CSV -> R.string.export_format_csv
    ExportFormat.XLSX -> R.string.export_format_xlsx
}
