package com.family.finance.ui.categories

import com.family.finance.ui.theme.Spacing
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.family.finance.R
import com.family.finance.domain.model.Category
import com.family.finance.domain.model.CategoryId
import com.family.finance.domain.model.CategoryKind
import com.family.finance.domain.repository.CategoryRepository
import com.family.finance.domain.usecase.CategoryService
import com.family.finance.domain.usecase.RecordResult
import com.family.finance.ui.common.appViewModel
import com.family.finance.ui.common.categoryLabel
import com.family.finance.ui.components.SectionHeader
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoriesViewModel(
    categories: CategoryRepository,
    private val service: CategoryService,
) : ViewModel() {
    val categories: StateFlow<List<Category>> = categories.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Returns false when the name is blank, so the dialog stays open. */
    fun save(existing: Category?, name: String, kind: CategoryKind, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = if (existing == null) service.create(CategoryId.generate(), name, kind) else service.rename(existing.id, name)
            onResult(result is RecordResult.Saved)
        }
    }

    fun setArchived(id: CategoryId, archived: Boolean) {
        viewModelScope.launch { service.setArchived(id, archived) }
    }
}

private sealed interface Dialog {
    data object New : Dialog
    data class Rename(val category: Category) : Dialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesRoute(onBack: () -> Unit) {
    val vm = appViewModel { c -> CategoriesViewModel(c.categoryRepository, c.categoryService) }
    val all by vm.categories.collectAsStateWithLifecycle()
    var kind by remember { mutableStateOf(CategoryKind.EXPENSE) }
    var dialog by remember { mutableStateOf<Dialog?>(null) }

    // Sort by what the user actually sees (localized names).
    val labelled = all.filter { it.kind == kind }.map { it to categoryLabel(it) }.sortedBy { it.second.lowercase() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.more_categories)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back)) }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { dialog = Dialog.New },
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text(stringResource(R.string.action_add_category)) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = if (kind == CategoryKind.EXPENSE) 0 else 1) {
                Tab(selected = kind == CategoryKind.EXPENSE, onClick = { kind = CategoryKind.EXPENSE }, text = { Text(stringResource(R.string.type_expense)) })
                Tab(selected = kind == CategoryKind.INCOME, onClick = { kind = CategoryKind.INCOME }, text = { Text(stringResource(R.string.type_income)) })
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Spacing.fabClearance)) {
                items(labelled.filter { !it.first.isArchived }, key = { it.first.id.value }) { (c, label) ->
                    ListItem(
                        headlineContent = { Text(label) },
                        trailingContent = {
                            Column {
                                TextButton(onClick = { dialog = Dialog.Rename(c) }) { Text(stringResource(R.string.action_rename)) }
                                TextButton(onClick = { vm.setArchived(c.id, true) }) { Text(stringResource(R.string.action_archive)) }
                            }
                        },
                    )
                }
                val archived = labelled.filter { it.first.isArchived }
                if (archived.isNotEmpty()) {
                    item { SectionHeader(R.string.categories_archived) }
                    items(archived, key = { it.first.id.value }) { (c, label) ->
                        ListItem(
                            headlineContent = { Text(label) },
                            trailingContent = {
                                TextButton(onClick = { vm.setArchived(c.id, false) }) { Text(stringResource(R.string.action_restore)) }
                            },
                        )
                    }
                }
            }
        }
    }

    dialog?.let { d ->
        val existing = (d as? Dialog.Rename)?.category
        val initial = existing?.let { categoryLabel(it) }.orEmpty()
        var name by remember(d) { mutableStateOf(initial) }
        var error by remember(d) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(if (existing == null) R.string.action_add_category else R.string.action_rename)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = false },
                    label = { Text(stringResource(R.string.field_category_name)) },
                    singleLine = true,
                    isError = error,
                    supportingText = if (error) { { Text(stringResource(R.string.error_required)) } } else null,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.save(existing, name, kind) { ok -> if (ok) dialog = null else error = true }
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
