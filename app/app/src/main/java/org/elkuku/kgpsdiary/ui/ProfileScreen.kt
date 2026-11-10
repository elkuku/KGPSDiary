package org.elkuku.kgpsdiary.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.elkuku.kgpsdiary.BuildConfig
import org.elkuku.kgpsdiary.R
import org.elkuku.kgpsdiary.asString
import org.elkuku.kgpsdiary.data.Me
import org.elkuku.kgpsdiary.ui.theme.KGPSDiaryTheme

/** The signed-in account. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    state: MainUiState,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        ProfileContent(state, onRefresh, onSignOut, Modifier.padding(padding))
    }
}

@Composable
private fun ProfileContent(
    state: MainUiState,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        state.displayName?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
        state.email?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }

        when {
            state.busy -> CircularProgressIndicator()
            state.me != null -> Text(
                stringResource(
                    R.string.profile_server_account,
                    state.me.id,
                    stringResource(if (state.me.isAdmin) R.string.role_admin else R.string.role_user),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Text(BuildConfig.SERVER_BASE_URL, style = MaterialTheme.typography.bodySmall)
        state.message?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRefresh, enabled = !state.busy) { Text(stringResource(R.string.refresh)) }
            OutlinedButton(onClick = onSignOut, enabled = !state.busy) { Text(stringResource(R.string.sign_out)) }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    KGPSDiaryTheme {
        ProfileScreen(
            state = MainUiState(
                started = true,
                email = "you@example.com",
                displayName = "You",
                me = Me(1, "you@example.com", "ROLE_USER"),
            ),
            onRefresh = {},
            onSignOut = {},
            onBack = {},
        )
    }
}
