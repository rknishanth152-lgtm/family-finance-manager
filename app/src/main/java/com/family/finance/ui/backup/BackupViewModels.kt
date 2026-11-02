package com.family.finance.ui.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.family.finance.domain.backup.Backup
import com.family.finance.domain.backup.BackupCodec
import com.family.finance.domain.backup.BackupProblem
import com.family.finance.domain.backup.BackupRead
import com.family.finance.domain.backup.BackupService
import com.family.finance.domain.backup.RestorePlan
import com.family.finance.domain.backup.RestoreResult
import com.family.finance.domain.legacy.ImportReport
import com.family.finance.domain.legacy.LegacyImport
import com.family.finance.domain.legacy.LegacySource
import com.family.finance.domain.model.UserId
import com.family.finance.domain.sync.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

sealed interface BackupStatus {
    data object Idle : BackupStatus
    data object Working : BackupStatus
    /** A backup is ready to be written to the place the person picks. */
    data class ChooseWhere(val fileName: String, val backup: Backup) : BackupStatus
    data class Saved(val backup: Backup) : BackupStatus
    data class Preview(val plan: RestorePlan) : BackupStatus
    data class Restored(val result: RestoreResult) : BackupStatus
    data class Invalid(val problem: BackupProblem) : BackupStatus
    data object Failed : BackupStatus
}

/**
 * Backup and restore (PRD 12.5-12.7). A backup is only offered once the phone is in sync,
 * so it cannot silently miss records that have not arrived from the cloud yet.
 */
class BackupViewModel(
    private val service: BackupService,
    private val familyName: () -> String,
    sync: Flow<SyncState> = flowOf(SyncState.SYNCED),
    private val clock: Clock,
    private val writeText: suspend (Uri, String) -> Unit,
    private val readText: suspend (Uri) -> String,
) : ViewModel() {

    val canBackUp: StateFlow<Boolean> = sync.map { it == SyncState.SYNCED }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _status = MutableStateFlow<BackupStatus>(BackupStatus.Idle)
    val status: StateFlow<BackupStatus> = _status.asStateFlow()

    fun createBackup() = work {
        val backup = service.create(familyName())
        BackupStatus.ChooseWhere("family-finance-backup_${LocalDate.now(clock)}.json", backup)
    }

    /** Called with the place picked in the system save dialog (null if cancelled). */
    fun saveTo(uri: Uri?) {
        val ready = _status.value as? BackupStatus.ChooseWhere ?: return
        if (uri == null) { _status.value = BackupStatus.Idle; return }
        work {
            writeText(uri, BackupCodec.encode(ready.backup))
            // Read the written file back: a backup is only reported as saved once it restores cleanly.
            when (val check = BackupCodec.decode(readText(uri))) {
                is BackupRead.Ok -> if (check.backup == ready.backup) BackupStatus.Saved(ready.backup) else BackupStatus.Failed
                is BackupRead.Invalid -> BackupStatus.Failed
            }
        }
    }

    /** Reads and checks a backup file, then shows what restoring it would do. Nothing is written yet. */
    fun open(uri: Uri?) {
        if (uri == null) return
        work {
            when (val read = BackupCodec.decode(readText(uri))) {
                is BackupRead.Invalid -> BackupStatus.Invalid(read.problem)
                is BackupRead.Ok -> BackupStatus.Preview(service.plan(read.backup))
            }
        }
    }

    fun confirmRestore() {
        val preview = _status.value as? BackupStatus.Preview ?: return
        // Restoring only once in sync, so a record that exists in the cloud is never taken as missing.
        if (!preview.plan.canRestore || !canBackUp.value) return
        work { BackupStatus.Restored(service.restore(preview.plan)) }
    }

    fun dismiss() {
        _status.value = BackupStatus.Idle
    }

    private fun work(block: suspend () -> BackupStatus) {
        if (_status.value == BackupStatus.Working) return
        _status.value = BackupStatus.Working
        viewModelScope.launch {
            _status.value = runCatching { block() }.getOrElse { BackupStatus.Failed }
        }
    }
}

sealed interface ImportStatus {
    data object Idle : ImportStatus
    data object Working : ImportStatus
    /** Check-only result: the report, and how many records are not in the app yet. Nothing written. */
    data class Checked(val report: ImportReport, val plan: RestorePlan) : ImportStatus
    data class Imported(val report: ImportReport, val result: RestoreResult) : ImportStatus
    data object NotOldAppFile : ImportStatus
    data object Failed : ImportStatus
}

/** One-time import from the old app (PRD 13): check first, then import only what is missing. */
class LegacyImportViewModel(
    private val service: BackupService,
    private val me: () -> UserId,
    private val familyName: () -> String,
    private val clock: Clock,
    private val readSource: suspend (Uri) -> LegacySource,
    private val isFormatError: (Throwable) -> Boolean,
    sync: Flow<SyncState> = flowOf(SyncState.SYNCED),
) : ViewModel() {

    /** Importing only once in sync, so "already here" checks see every record the cloud has. */
    val canImport: StateFlow<Boolean> = sync.map { it == SyncState.SYNCED }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _status = MutableStateFlow<ImportStatus>(ImportStatus.Idle)
    val status: StateFlow<ImportStatus> = _status.asStateFlow()

    fun check(uri: Uri?) {
        if (uri == null) return
        work {
            val plan = LegacyImport.plan(readSource(uri), me(), Instant.now(clock), familyName())
            ImportStatus.Checked(plan.report, service.plan(plan.records))
        }
    }

    fun import() {
        val checked = _status.value as? ImportStatus.Checked ?: return
        if (!checked.plan.canRestore || !canImport.value) return
        work { ImportStatus.Imported(checked.report, service.restore(checked.plan)) }
    }

    fun dismiss() {
        _status.value = ImportStatus.Idle
    }

    private fun work(block: suspend () -> ImportStatus) {
        if (_status.value == ImportStatus.Working) return
        _status.value = ImportStatus.Working
        viewModelScope.launch {
            _status.value = runCatching { block() }.getOrElse { if (isFormatError(it)) ImportStatus.NotOldAppFile else ImportStatus.Failed }
        }
    }
}
