package com.family.finance.ui.more

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.family.finance.R
import com.family.finance.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    onAccounts: () -> Unit,
    onCategories: () -> Unit,
    onNotebook: () -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_more)) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            MoreItem(R.drawable.ic_sticky_note_2, R.string.more_notebook, onNotebook)
            MoreItem(R.drawable.ic_account_balance_wallet, R.string.more_accounts, onAccounts)
            MoreItem(R.drawable.ic_category, R.string.more_categories, onCategories)
            HorizontalDivider()
            // PRD 5 lists these; they arrive in later phases and are shown disabled until then.
            SectionHeader(R.string.more_coming_later)
            listOf(
                R.string.more_budgets, R.string.more_recurring, R.string.more_backup,
                R.string.more_language, R.string.more_workspace, R.string.more_privacy, R.string.more_about,
            ).forEach { label ->
                ListItem(
                    headlineContent = { Text(stringResource(label)) },
                    supportingContent = { Text(stringResource(R.string.more_later_phase)) },
                )
            }
        }
    }
}

@Composable
private fun MoreItem(@DrawableRes icon: Int, @StringRes label: Int, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        headlineContent = { Text(stringResource(label)) },
        trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) },
    )
}
