package com.family.finance.ui.family

import android.app.Activity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.family.finance.R
import com.family.finance.domain.model.Invite
import com.family.finance.domain.model.Member
import com.family.finance.domain.model.Role
import com.family.finance.domain.repository.FamilyRepository
import com.family.finance.domain.validation.EmailValidator
import com.family.finance.ui.common.LocalAppContainer
import com.family.finance.ui.common.appViewModel
import com.family.finance.ui.components.SectionHeader
import com.family.finance.ui.session.clearGoogleCredential
import com.family.finance.ui.theme.Spacing
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FamilyViewModel(private val family: FamilyRepository) : ViewModel() {
    val me: Member get() = family.me
    val workspaceName: String get() = family.workspace.name
    val isAdmin: Boolean get() = family.me.role == Role.ADMIN

    val members: StateFlow<List<Member>> = family.observeMembers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val invites: StateFlow<List<Invite>> = family.observeInvites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Returns false (and invites nobody) if the address is not valid. */
    fun invite(email: String): Boolean {
        if (!isAdmin || !EmailValidator.isValid(email)) return false
        viewModelScope.launch { family.invite(email) }
        return true
    }

    fun cancelInvite(email: String) { viewModelScope.launch { family.cancelInvite(email) } }
    fun remove(member: Member) { viewModelScope.launch { family.removeMember(member.uid) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyRoute(onBack: () -> Unit) {
    val vm = appViewModel { c -> FamilyViewModel(c.familyRepository) }
    val container = LocalAppContainer.current
    val members by vm.members.collectAsStateWithLifecycle()
    val invites by vm.invites.collectAsStateWithLifecycle()
    val activity = checkNotNull(LocalActivity.current)
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Member?>(null) }
    var confirmSignOut by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(vm.workspaceName.ifBlank { stringResource(R.string.more_family) }) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item { SectionHeader(R.string.more_family) }
            items(members, key = { it.uid.value }) { member ->
                ListItem(
                    headlineContent = { Text(member.displayName.ifBlank { member.email }) },
                    supportingContent = {
                        Text(
                            member.email + " · " + stringResource(
                                if (member.role == Role.ADMIN) R.string.family_role_admin else R.string.family_role_member,
                            ),
                        )
                    },
                    trailingContent = if (vm.isAdmin && member.uid != vm.me.uid) {
                        { TextButton(onClick = { removing = member }) { Text(stringResource(R.string.action_remove_member)) } }
                    } else {
                        null
                    },
                )
            }

            if (vm.isAdmin) {
                item {
                    HorizontalDivider(Modifier.padding(vertical = Spacing.sm))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; emailError = false },
                        label = { Text(stringResource(R.string.family_invite_label)) },
                        singleLine = true,
                        isError = emailError,
                        supportingText = if (emailError) { { Text(stringResource(R.string.error_invalid_email)) } } else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
                    )
                    Button(
                        onClick = { if (vm.invite(email)) email = "" else emailError = true },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    ) { Text(stringResource(R.string.action_invite)) }
                    Text(
                        stringResource(R.string.family_invite_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.md),
                    )
                }
                if (invites.isNotEmpty()) {
                    item { SectionHeader(R.string.family_pending_invites) }
                    items(invites, key = { it.email }) { invite ->
                        ListItem(
                            headlineContent = { Text(invite.email) },
                            trailingContent = {
                                TextButton(onClick = { vm.cancelInvite(invite.email) }) { Text(stringResource(R.string.action_cancel_invite)) }
                            },
                        )
                    }
                }
            } else {
                item {
                    Text(
                        stringResource(R.string.family_only_admin_invites),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.md),
                    )
                }
            }

            item {
                HorizontalDivider(Modifier.padding(vertical = Spacing.sm))
                Text(
                    stringResource(R.string.signed_in_as, vm.me.email),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.md),
                )
                OutlinedButton(
                    onClick = { confirmSignOut = true },
                    modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                ) { Text(stringResource(R.string.action_sign_out)) }
            }
        }
    }

    removing?.let { member ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.remove_member_title, member.displayName.ifBlank { member.email })) },
            text = { Text(stringResource(R.string.remove_member_message)) },
            confirmButton = {
                TextButton(onClick = { vm.remove(member); removing = null }) {
                    Text(stringResource(R.string.action_remove_member), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.sign_out_title)) },
            text = { Text(stringResource(R.string.sign_out_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    container.signOut()
                    scope.launch { clearGoogleCredential(activity) }
                }) { Text(stringResource(R.string.action_sign_out)) }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
