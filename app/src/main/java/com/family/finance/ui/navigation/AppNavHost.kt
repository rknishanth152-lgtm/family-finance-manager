package com.family.finance.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.min
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import kotlinx.coroutines.launch
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.family.finance.R
import com.family.finance.domain.model.AccountId
import com.family.finance.domain.model.NoteId
import com.family.finance.domain.model.TransactionId
import com.family.finance.ui.accounts.AccountEditRoute
import com.family.finance.ui.accounts.AccountsRoute
import com.family.finance.ui.calendar.CalendarRoute
import com.family.finance.ui.categories.CategoriesRoute
import com.family.finance.ui.family.FamilyRoute
import com.family.finance.ui.backup.BackupRoute
import com.family.finance.ui.backup.LegacyImportRoute
import com.family.finance.ui.common.LocalAppContainer
import com.family.finance.domain.model.Role
import com.family.finance.ui.form.TransactionFormRoute
import com.family.finance.ui.home.HomeRoute
import com.family.finance.ui.more.MoreScreen
import com.family.finance.ui.notes.NoteEditRoute
import com.family.finance.ui.notes.NotesRoute
import com.family.finance.ui.reports.EntriesFilter
import com.family.finance.ui.reports.ReportEntriesRoute
import com.family.finance.ui.reports.ReportsRoute
import com.family.finance.domain.calc.DateRange
import com.family.finance.domain.model.CategoryId
import com.family.finance.domain.usecase.TypeFilter
import android.net.Uri
import java.time.LocalDate
import com.family.finance.ui.transactions.TransactionDetailRoute
import com.family.finance.ui.transactions.TransactionsRoute

private const val NAV_LABEL_MAX_SCALE = 1.15f

enum class FormKind(val route: String) { INCOME("income"), EXPENSE("expense"), TRANSFER("transfer") }

/** The five primary destinations from PRD section 5. */
enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
) {
    HOME("home", R.string.nav_home, R.drawable.ic_home),
    TRANSACTIONS("transactions", R.string.nav_transactions, R.drawable.ic_receipt_long),
    CALENDAR("calendar", R.string.nav_calendar, R.drawable.ic_calendar_month),
    REPORTS("reports", R.string.nav_reports, R.drawable.ic_bar_chart),
    MORE("more", R.string.nav_more, R.drawable.ic_more_horiz),
}

object Routes {
    /** The five top-level screens, side by side: swipe sideways or tap the bottom bar. */
    const val TABS = "tabs"
    const val FORM = "form/{kind}?id={id}"
    const val DETAIL = "transaction/{id}"
    const val ACCOUNTS = "accounts"
    const val ACCOUNT_EDIT = "accounts/edit?id={id}"
    const val CATEGORIES = "categories"
    const val FAMILY = "family"
    const val BACKUP = "backup"
    const val IMPORT = "import"
    const val NOTES = "notes"
    const val NOTE_EDIT = "notes/edit?id={id}"
    const val REPORT_ENTRIES = "reports/entries?from={from}&to={to}&account={account}&category={category}&type={type}"

    fun form(kind: FormKind, id: TransactionId? = null) = "form/${kind.route}" + (id?.let { "?id=${it.value}" } ?: "")
    fun detail(id: TransactionId) = "transaction/${id.value}"
    fun accountEdit(id: AccountId? = null) = "accounts/edit" + (id?.let { "?id=${it.value}" } ?: "")
    fun noteEdit(id: NoteId? = null) = "notes/edit" + (id?.let { "?id=${it.value}" } ?: "")
    fun reportEntries(f: EntriesFilter) = "reports/entries?from=${f.range.start}&to=${f.range.end}&type=${f.type.name}" +
        (f.accountId?.let { "&account=${Uri.encode(it.value)}" } ?: "") +
        (f.categoryId?.let { "&category=${Uri.encode(it.value)}" } ?: "")
}

private val optionalId = navArgument("id") {
    type = NavType.StringType
    nullable = true
    defaultValue = null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val onTopLevel = backStackEntry?.destination?.route == Routes.TABS
    var showAddSheet by remember { mutableStateOf(false) }

    val openForm: (FormKind) -> Unit = { navController.navigate(Routes.form(it)) }
    val openDetail: (TransactionId) -> Unit = { navController.navigate(Routes.detail(it)) }

    // Kept here, outside the tabs screen, so the open tab survives a trip to a detail screen.
    val pagerState = rememberPagerState(pageCount = { TopLevelDestination.entries.size })
    val scope = rememberCoroutineScope()
    val goToTab: (TopLevelDestination) -> Unit = { destination ->
        scope.launch { pagerState.animateToTab(destination.ordinal) }
    }
    // While a finger drags, the bar follows the page under it; after a tap or a fling it
    // jumps straight to where the pager is heading, so it never lags behind the motion.
    val dragging by pagerState.interactionSource.collectIsDraggedAsState()
    val selectedTab = if (dragging) pagerState.currentPage else pagerState.targetPage

    Scaffold(
        // Each screen draws its own top bar and handles the status bar inset.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (onTopLevel) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = selectedTab == destination.ordinal,
                            onClick = { goToTab(destination) },
                            // The label already names the item, so the icon is decorative.
                            icon = { Icon(painterResource(destination.iconRes), contentDescription = null) },
                            // Long single Tamil words must not be cut or split (PRD 6.1):
                            // one line, shrinking only as far as needed to fit. Five tabs
                            // cannot hold 2x-size labels, so they follow the phone's font
                            // size only up to NAV_LABEL_MAX_SCALE; the icons stay as cues.
                            label = {
                                val fontScale = LocalDensity.current.fontScale
                                val cap = min(fontScale, NAV_LABEL_MAX_SCALE) / fontScale
                                BasicText(
                                    stringResource(destination.labelRes),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = LocalContentColor.current,
                                        textAlign = TextAlign.Center,
                                    ),
                                    maxLines = 1,
                                    softWrap = false,
                                    autoSize = TextAutoSize.StepBased(
                                        minFontSize = 7.5.sp * cap,
                                        maxFontSize = 12.sp * cap,
                                        stepSize = 0.25.sp,
                                    ),
                                )
                            },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            // Persistent Add Transaction action (PRD 5).
            if (onTopLevel) {
                FloatingActionButton(onClick = { showAddSheet = true }) {
                    Icon(painterResource(R.drawable.ic_add), stringResource(R.string.action_add_transaction))
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.TABS,
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
        ) {
            composable(Routes.TABS) {
                // Back from any other tab returns to Home first, as before.
                BackHandler(enabled = pagerState.currentPage != TopLevelDestination.HOME.ordinal) {
                    goToTab(TopLevelDestination.HOME)
                }
                // Pages leave the screen as they scroll away; this keeps each one's scroll
                // position and filters for when it comes back.
                val pageStates = rememberSaveableStateHolder()
                HorizontalPager(
                    state = pagerState,
                    // The neighbouring tabs are ready before the swipe starts, so the first
                    // frames of the motion never wait for a screen to be built.
                    beyondViewportPageCount = 1,
                    key = { TopLevelDestination.entries[it].route },
                ) { page ->
                    val destination = TopLevelDestination.entries[page]
                    pageStates.SaveableStateProvider(destination.route) {
                        when (destination) {
                            TopLevelDestination.HOME -> HomeRoute(
                                onAddTransaction = openForm,
                                onOpenTransaction = openDetail,
                                onSeeAll = { goToTab(TopLevelDestination.TRANSACTIONS) },
                                onAddAccount = { navController.navigate(Routes.accountEdit()) },
                            )
                            TopLevelDestination.TRANSACTIONS -> TransactionsRoute(onOpenTransaction = openDetail)
                            TopLevelDestination.CALENDAR -> CalendarRoute(onOpenTransaction = openDetail)
                            TopLevelDestination.REPORTS -> ReportsRoute(onOpenEntries = { navController.navigate(Routes.reportEntries(it)) })
                            TopLevelDestination.MORE -> MoreScreen(
                                onAccounts = { navController.navigate(Routes.ACCOUNTS) },
                                onCategories = { navController.navigate(Routes.CATEGORIES) },
                                onNotebook = { navController.navigate(Routes.NOTES) },
                                onFamily = { navController.navigate(Routes.FAMILY) },
                                onBackup = { navController.navigate(Routes.BACKUP) },
                                onImport = { navController.navigate(Routes.IMPORT) },
                                isAdmin = LocalAppContainer.current.familyRepository.me.role == Role.ADMIN,
                            )
                        }
                    }
                }
            }
            composable(
                Routes.REPORT_ENTRIES,
                arguments = listOf("from", "to", "type", "account", "category").map { name ->
                    navArgument(name) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                },
            ) { entry ->
                val args = checkNotNull(entry.arguments)
                val filter = EntriesFilter(
                    range = DateRange(LocalDate.parse(args.getString("from")), LocalDate.parse(args.getString("to"))),
                    accountId = args.getString("account")?.let(::AccountId),
                    categoryId = args.getString("category")?.let(::CategoryId),
                    type = args.getString("type")?.let(TypeFilter::valueOf) ?: TypeFilter.ALL,
                )
                ReportEntriesRoute(filter, onBack = { navController.popBackStack() }, onOpenTransaction = openDetail)
            }
            composable(
                Routes.FORM,
                arguments = listOf(navArgument("kind") { type = NavType.StringType }, optionalId),
            ) { entry ->
                val kind = FormKind.entries.first { it.route == entry.arguments?.getString("kind") }
                val id = entry.arguments?.getString("id")?.let(::TransactionId)
                TransactionFormRoute(
                    kind = kind,
                    editId = id,
                    onClose = { navController.popBackStack() },
                    onViewTransaction = { saved ->
                        navController.navigate(Routes.detail(saved)) {
                            popUpTo(Routes.FORM) { inclusive = true }
                        }
                    },
                    onAddAccount = { navController.navigate(Routes.accountEdit()) },
                )
            }
            composable(Routes.DETAIL, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                TransactionDetailRoute(
                    id = TransactionId(entry.arguments!!.getString("id")!!),
                    onBack = { navController.popBackStack() },
                    onEdit = { kind, id -> navController.navigate(Routes.form(kind, id)) },
                )
            }
            composable(Routes.ACCOUNTS) {
                AccountsRoute(
                    onBack = { navController.popBackStack() },
                    onAdd = { navController.navigate(Routes.accountEdit()) },
                    onEdit = { navController.navigate(Routes.accountEdit(it)) },
                )
            }
            composable(Routes.ACCOUNT_EDIT, arguments = listOf(optionalId)) { entry ->
                AccountEditRoute(
                    editId = entry.arguments?.getString("id")?.let(::AccountId),
                    onDone = { navController.popBackStack() },
                )
            }
            composable(Routes.CATEGORIES) { CategoriesRoute(onBack = { navController.popBackStack() }) }
            composable(Routes.FAMILY) { FamilyRoute(onBack = { navController.popBackStack() }) }
            composable(Routes.BACKUP) { BackupRoute(onBack = { navController.popBackStack() }) }
            composable(Routes.IMPORT) { LegacyImportRoute(onBack = { navController.popBackStack() }) }
            composable(Routes.NOTES) {
                NotesRoute(
                    onBack = { navController.popBackStack() },
                    onAdd = { navController.navigate(Routes.noteEdit()) },
                    onOpen = { navController.navigate(Routes.noteEdit(it)) },
                )
            }
            composable(Routes.NOTE_EDIT, arguments = listOf(optionalId)) { entry ->
                NoteEditRoute(
                    editId = entry.arguments?.getString("id")?.let(::NoteId),
                    onDone = { navController.popBackStack() },
                )
            }
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(onDismissRequest = { showAddSheet = false }) {
            Column(Modifier.navigationBarsPadding()) {
                AddOption(R.drawable.ic_south_west, R.string.action_add_income) { showAddSheet = false; openForm(FormKind.INCOME) }
                AddOption(R.drawable.ic_north_east, R.string.action_add_expense) { showAddSheet = false; openForm(FormKind.EXPENSE) }
                AddOption(R.drawable.ic_swap_horiz, R.string.action_transfer) { showAddSheet = false; openForm(FormKind.TRANSFER) }
            }
        }
    }
}

/**
 * Tapping a far tab (Home to More) would otherwise slide through every screen in between.
 * Instead, jump silently to the neighbour of the target and slide the last step, so every
 * tap looks like the same single, short movement.
 */
private suspend fun PagerState.animateToTab(page: Int) {
    if (page == currentPage && !isScrollInProgress) return
    val from = currentPage
    if (abs(page - from) > 1) scrollToPage(if (page > from) page - 1 else page + 1)
    animateScrollToPage(page)
}

@Composable
private fun AddOption(@DrawableRes icon: Int, @StringRes label: Int, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        headlineContent = { Text(stringResource(label)) },
    )
}
