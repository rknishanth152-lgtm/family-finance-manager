package com.family.finance.ui.session

import android.app.Activity
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.family.finance.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

sealed interface GoogleSignInResult {
    data class Token(val idToken: String) : GoogleSignInResult
    data object Cancelled : GoogleSignInResult
    data object NoAccount : GoogleSignInResult
    data class Failed(val error: Exception) : GoogleSignInResult
}

/** Shows the system "choose a Google account" sheet and returns its ID token. */
suspend fun requestGoogleIdToken(activity: Activity): GoogleSignInResult {
    val option = GetGoogleIdOption.Builder()
        // The web client ID is generated from google-services.json by the Google services plugin.
        .setServerClientId(activity.getString(R.string.default_web_client_id))
        .setFilterByAuthorizedAccounts(false)
        .setAutoSelectEnabled(false)
        .build()
    return try {
        val response = CredentialManager.create(activity)
            .getCredential(activity, GetCredentialRequest.Builder().addCredentialOption(option).build())
        val credential = response.credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            GoogleSignInResult.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
        } else {
            GoogleSignInResult.Failed(IllegalStateException("Unexpected credential type"))
        }
    } catch (e: GetCredentialCancellationException) {
        GoogleSignInResult.Cancelled
    } catch (e: NoCredentialException) {
        GoogleSignInResult.NoAccount
    } catch (e: Exception) {
        GoogleSignInResult.Failed(e)
    }
}

/** So the next sign-in shows the account chooser again instead of reusing this account. */
suspend fun clearGoogleCredential(activity: Activity) {
    runCatching { CredentialManager.create(activity).clearCredentialState(ClearCredentialStateRequest()) }
}
