package com.family.finance.data

import com.family.finance.domain.model.DefaultCategories
import com.family.finance.domain.model.UserId
import com.family.finance.domain.repository.AccountRepository
import com.family.finance.domain.repository.CategoryRepository
import com.family.finance.domain.repository.NoteRepository
import com.family.finance.domain.repository.TransactionRepository
import com.family.finance.domain.usecase.AccountService
import com.family.finance.domain.usecase.CategoryService
import com.family.finance.domain.usecase.NoteService
import com.family.finance.domain.usecase.TransactionService
import java.time.Clock
import java.time.LocalDate

/** Hand-written dependency container (no DI framework, by decision for this small app). */
class AppContainer(
    val clock: Clock = Clock.systemDefaultZone(),
    val transactionRepository: TransactionRepository = InMemoryTransactionRepository(),
    val accountRepository: AccountRepository = InMemoryAccountRepository(),
    val categoryRepository: CategoryRepository = InMemoryCategoryRepository(DefaultCategories.all),
    val noteRepository: NoteRepository = InMemoryNoteRepository(),
) {
    /** Placeholder until Firebase Authentication arrives in Phase 4. */
    val currentUser: () -> UserId = { UserId("local-user") }

    val entryDefaults = EntryDefaults()

    val transactionService = TransactionService(
        transactionRepository, accountRepository, categoryRepository, currentUser, clock,
    )
    val accountService = AccountService(accountRepository, clock)
    val categoryService = CategoryService(categoryRepository)
    val noteService = NoteService(noteRepository, currentUser, clock)

    fun today(): LocalDate = LocalDate.now(clock)
}
