package com.family.finance.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.family.finance.domain.model.NoteEntry
import com.family.finance.domain.model.NoteId
import com.family.finance.domain.repository.NoteRepository
import com.family.finance.domain.usecase.NoteService
import com.family.finance.domain.usecase.RecordResult
import com.family.finance.domain.usecase.TextSearch
import com.family.finance.domain.validation.NoteDraft
import com.family.finance.domain.validation.ValidationError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class NotesUiState(val loading: Boolean = true, val query: String = "", val notes: List<NoteEntry> = emptyList())

class NotesViewModel(notes: NoteRepository) : ViewModel() {
    private val query = MutableStateFlow("")

    val uiState: StateFlow<NotesUiState> = combine(query, notes.observeAll()) { q, all ->
        NotesUiState(
            loading = false,
            query = q,
            notes = all
                .filter { TextSearch.matches("${it.title}\n${it.description}", q) }
                .sortedWith(compareByDescending<NoteEntry> { it.date }.thenByDescending { it.updatedAt }),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesUiState())

    fun search(text: String) { query.value = text }
}

data class NoteEditState(
    val isEdit: Boolean,
    val loaded: Boolean = false,
    val title: String = "",
    val description: String = "",
    val date: LocalDate? = null,
    val errors: List<ValidationError> = emptyList(),
    val saving: Boolean = false,
    val done: Boolean = false,
)

class NoteEditViewModel(
    editId: NoteId?,
    private val notes: NoteRepository,
    private val service: NoteService,
    today: () -> LocalDate,
) : ViewModel() {
    private val id = editId ?: NoteId.generate()
    private val _state = MutableStateFlow(NoteEditState(isEdit = editId != null))
    val state: StateFlow<NoteEditState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = editId?.let { notes.get(it) }
            _state.update {
                if (existing != null) {
                    it.copy(loaded = true, title = existing.title, description = existing.description, date = existing.date)
                } else {
                    it.copy(loaded = true, date = today())
                }
            }
        }
    }

    fun edit(transform: (NoteEditState) -> NoteEditState) = _state.update { transform(it).copy(errors = emptyList()) }

    fun save() {
        val s = _state.value
        if (s.saving || s.done || !s.loaded) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            when (val r = service.save(id, NoteDraft(s.title, s.description, s.date))) {
                is RecordResult.Saved -> _state.update { it.copy(saving = false, done = true) }
                is RecordResult.Invalid -> _state.update { it.copy(saving = false, errors = r.errors) }
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            service.delete(id)
            _state.update { it.copy(done = true) }
        }
    }
}
