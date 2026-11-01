package com.family.finance.ui.reports

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.family.finance.R
import com.family.finance.data.export.ExportService
import com.family.finance.data.export.ExportedFile
import com.family.finance.ui.common.appViewModel

@Composable
fun ReportsRoute(onOpenEntries: (EntriesFilter) -> Unit) {
    val context = LocalContext.current
    val vm = appViewModel { c ->
        val service = ExportService(context)
        ReportsViewModel(
            transactions = c.transactionRepository,
            accounts = c.accountRepository,
            categories = c.categoryRepository,
            members = c.familyRepository.observeMembers(),
            familyName = { c.familyRepository.workspace.name },
            today = c::today,
            exportFile = service::export,
            saveFile = service::saveTo,
        )
    }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val export by vm.export.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    val saveLauncher = rememberLauncherForActivityResult(CreateExportFile()) { uri -> vm.saveTo(uri) }
    val savedText = stringResource(R.string.export_saved)
    val failedText = stringResource(R.string.export_failed)
    val chooserTitle = stringResource(R.string.export_share_chooser)

    LaunchedEffect(export) {
        when (val e = export) {
            is ExportStatus.Ready -> when (e.target) {
                ExportTarget.SAVE -> saveLauncher.launch(e.file)
                ExportTarget.SHARE -> {
                    shareExport(context, e.file, chooserTitle)
                    vm.exportHandled()
                }
            }
            ExportStatus.Saved -> {
                vm.exportHandled()
                snackbar.showSnackbar(savedText)
            }
            ExportStatus.Failed -> {
                vm.exportHandled()
                snackbar.showSnackbar(failedText)
            }
            ExportStatus.Idle, ExportStatus.Working -> Unit
        }
    }

    ReportsScreen(
        state = state,
        export = export,
        snackbar = snackbar,
        actions = ReportsActions(
            previous = vm::previousPeriod,
            next = vm::nextPeriod,
            selectPeriod = vm::selectPeriod,
            selectCustom = vm::selectCustom,
            selectAccount = vm::selectAccount,
            selectCategory = vm::selectCategory,
            selectType = vm::selectType,
            selectBucket = vm::selectBucket,
            selectCategoryKind = vm::selectCategoryKind,
            openEntries = onOpenEntries,
            export = vm::startExport,
        ),
    )
}

/** The system "save as" dialog, with the file's own name and type filled in. */
private class CreateExportFile : ActivityResultContract<ExportedFile, Uri?>() {
    override fun createIntent(context: Context, input: ExportedFile): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.mimeType)
            .putExtra(Intent.EXTRA_TITLE, input.displayName)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

/** Read access is granted only to the app the person picks, only for this file. */
private fun shareExport(context: Context, file: ExportedFile, title: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.exports", file.file)
    val send = Intent(Intent.ACTION_SEND)
        .setType(file.mimeType)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(file.displayName, uri)
    context.startActivity(Intent.createChooser(send, title))
}
