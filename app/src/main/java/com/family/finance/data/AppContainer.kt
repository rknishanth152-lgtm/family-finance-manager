package com.family.finance.data

import com.family.finance.domain.backup.BackupService
import com.family.finance.domain.model.DefaultCategories
import com.family.finance.domain.model.UserId
import com.family.finance.domain.repository.AccountRepository
import com.family.finance.domain.repository.CategoryRepository
import com.family.finance.domain.repository.FamilyRepository
import com.family.finance.domain.repository.NoteRepository
import com.family.finance.domain.repository.TransactionRepository
import com.family.finance.domain.sync.SyncState
import com.family.finance.domain.usecase.AccountService
import com.family.finance.domain.usecase.CategoryService
import com.family.finance.domain.usecase.NoteService
import com.family.finance.domain.usecase.TransactionService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.Clock
import java.time.LocalDate

/**
 * Everything the finance screens need for one signed-in person in one family workspace.
 * Hand-written DI (no framework, by decision). The app builds it from Firestore once the
 * person is signed in and in a family (see SessionManager); the defaults are in-memory
 * stand-ins for unit tests.
 */
class AppContainer(
    val clock: Clock = Clock.systemDefaultZone(),
    val currentUser: () -> UserId = { UserId("local-user") },
    val transactionRepository: TransactionRepository = InMemoryTransactionRepository(currentUser, clock),
    val accountRepository: AccountRepository = InMemoryAccountRepository(),
    val categoryRepository: CategoryRepository = InMemoryCategoryRepository(DefaultCategories.all),
    val noteRepository: NoteRepository = InMemoryNoteRepository(),
    val familyRepository: FamilyRepository = InMemoryFamilyRepository(),
    val syncState: Flow<SyncState> = flowOf(SyncState.SYNCED),
    val signOut: () -> Unit = {},
) {
    val entryDefaults = EntryDefaults()

    val transactionService = TransactionService(
        transactionRepository, accountRepository, categoryRepository, currentUser, clock,
    )
    val accountService = AccountService(accountRepository, clock)
    val categoryService = CategoryService(categoryRepository)
    val noteService = NoteService(noteRepository, currentUser, clock)
    val backupService = BackupService(
        transactionRepository, accountRepository, categoryRepository, noteRepository, currentUser, clock,
    )

    fun today(): LocalDate = LocalDate.now(clock)
}
