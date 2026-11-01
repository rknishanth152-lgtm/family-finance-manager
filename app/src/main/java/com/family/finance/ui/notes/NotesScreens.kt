package com.family.finance.ui.notes

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.family.finance.R
import com.family.finance.domain.model.NoteId
import com.family.finance.domain.validation.Field
import com.family.finance.ui.common.appViewModel
import com.family.finance.ui.common.formatDate
import com.family.finance.ui.common.messageFor
import com.family.finance.ui.components.DateField
import com.family.finance.ui.components.EmptyState
import com.family.finance.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesRoute(onBack: () -> Unit, onAdd: () -> Unit, onOpen: (NoteId) -> Unit) {
    val vm = appViewModel { c -> NotesViewModel(c.noteRepository) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.more_notebook)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back)) }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text(stringResource(R.string.action_add_note)) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::search,
                placeholder = { Text(stringResource(R.string.notes_search_hint)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            )
            if (!state.loading && state.notes.isEmpty()) {
                EmptyState(R.drawable.ic_sticky_note_2, R.string.notes_empty)
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Spacing.fabClearance)) {
                items(state.notes, key = { it.id.value }) { note ->
                    ListItem(
                        modifier = Modifier.clickable { onOpen(note.id) },
                        overlineContent = { Text(formatDate(note.date)) },
                        headlineContent = { Text(note.title) },
                        supportingContent = if (note.description.isNotBlank()) {
                            { Text(note.description, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        } else {
                            null
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditRoute(editId: NoteId?, onDone: () -> Unit) {
    val vm = appViewModel(key = "note-${editId?.value}") { c -> NoteEditViewModel(editId, c.noteRepository, c.noteService, c::today) }
    val s by vm.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(s.done) { if (s.done) onDone() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (s.isEdit) R.string.note_edit_title else R.string.action_add_note)) },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_close)) }
                },
                actions = {
                    if (s.isEdit) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.action_delete))
                        }
                    }
                },
            )
        },
        bottomBar = {
            Button(
                onClick = vm::save,
                enabled = !s.saving && s.loaded,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(Spacing.md),
            ) { Text(stringResource(R.string.action_save)) }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).imePadding().padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            val titleError = s.errors.messageFor(Field.TITLE)
            OutlinedTextField(
                value = s.title,
                onValueChange = { v -> vm.edit { it.copy(title = v) } },
                label = { Text(stringResource(R.string.field_title)) },
                singleLine = true,
                isError = titleError != null,
                supportingText = titleError?.let { { Text(stringResource(it)) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            DateField(s.date, { d -> vm.edit { it.copy(date = d) } }, R.string.field_date, errorRes = s.errors.messageFor(Field.DATE))
            OutlinedTextField(
                value = s.description,
                onValueChange = { v -> vm.edit { it.copy(description = v) } },
                label = { Text(stringResource(R.string.field_description)) },
                minLines = 5,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_note_title)) },
            text = { Text(stringResource(R.string.delete_note_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete()
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
