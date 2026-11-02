package com.family.finance.ui.session

import android.app.Activity
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.family.finance.R
import com.family.finance.data.NotAllowedToCreateFamily
import com.family.finance.data.SessionManager
import com.family.finance.data.SessionState
import com.family.finance.ui.common.LocalAppContainer
import com.family.finance.ui.navigation.AppNavHost
import com.family.finance.ui.theme.Spacing
import kotlinx.coroutines.launch

/** Sign in -> create or join the family -> the app (PRD 3.3, 10.1, 10.2). */
@Composable
fun SessionGate(session: SessionManager) {
    val state by session.state.collectAsStateWithLifecycle()
    when (val s = state) {
        SessionState.Loading -> Loading()
        SessionState.SignedOut -> SignInScreen(session)
        is SessionState.NeedsFamily -> FamilySetupScreen(session, s)
        is SessionState.Ready -> key(s.container) {
            CompositionLocalProvider(LocalAppContainer provides s.container) { AppNavHost() }
        }
    }
}

@Composable
private fun Loading() {
    Surface(Modifier.fillMaxSize()) {
        Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}

@Composable
private fun CenteredPage(content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    }
}

@Composable
private fun ErrorText(@StringRes message: Int?) {
    if (message == null) return
    Text(
        stringResource(message),
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun SignInScreen(session: SessionManager) {
    val activity = checkNotNull(LocalActivity.current)
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }

    CenteredPage {
        Icon(
            painterResource(R.drawable.ic_account_balance_wallet),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp),
        )
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(stringResource(R.string.signin_message), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Button(
            onClick = {
                busy = true
                error = null
                scope.launch {
                    error = when (val result = requestGoogleIdToken(activity)) {
                        is GoogleSignInResult.Token ->
                            runCatching { session.signInWithGoogle(result.idToken) }.exceptionOrNull()?.let { R.string.signin_error_network }
                        GoogleSignInResult.Cancelled -> null
                        GoogleSignInResult.NoAccount -> R.string.signin_error_no_account
                        is GoogleSignInResult.Failed -> R.string.signin_error_generic
                    }
                    busy = false
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.action_sign_in_google)) }
        if (busy) CircularProgressIndicator()
        ErrorText(error)
    }
}

@Composable
private fun FamilySetupScreen(session: SessionManager, state: SessionState.NeedsFamily) {
    val activity = checkNotNull(LocalActivity.current)
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    var familyName by rememberSaveable { mutableStateOf("") }

    fun run(action: suspend () -> Unit) {
        busy = true
        error = null
        scope.launch {
            error = runCatching { action() }.exceptionOrNull()?.let {
                if (it is NotAllowedToCreateFamily) R.string.family_error_not_allowed else R.string.family_error
            }
            busy = false
        }
    }

    CenteredPage {
        Text(stringResource(R.string.family_setup_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.signed_in_as, state.user.email), color = MaterialTheme.colorScheme.onSurfaceVariant)

        val invite = state.invite
        if (invite != null) {
            Text(
                stringResource(R.string.family_invite_message, invite.invitedByName, invite.workspaceName),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Button(onClick = { run { session.joinFamily(invite) } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_join_family))
            }
            HorizontalDivider(Modifier.padding(vertical = Spacing.sm))
        } else {
            Text(
                stringResource(R.string.family_waiting_invite, state.user.email),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { session.refreshInvite() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_check_again))
            }
            HorizontalDivider(Modifier.padding(vertical = Spacing.sm))
        }

        Text(stringResource(R.string.family_create_message), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        OutlinedTextField(
            value = familyName,
            onValueChange = { familyName = it },
            label = { Text(stringResource(R.string.field_family_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        val defaultName = stringResource(R.string.family_name_default)
        Button(
            onClick = { run { session.createFamily(familyName.ifBlank { defaultName }) } },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.action_create_family)) }

        if (busy) CircularProgressIndicator()
        ErrorText(error)
        TextButton(onClick = {
            session.signOut()
            scope.launch { clearGoogleCredential(activity) }
        }) { Text(stringResource(R.string.action_sign_out)) }
    }
}
