package org.elkuku.kgpsdiary.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.elkuku.kgpsdiary.R
import org.elkuku.kgpsdiary.ui.theme.KGPSDiaryTheme

@Composable
fun SignInScreen(busy: Boolean, onSignIn: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.welcome_tagline),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        if (busy) {
            CircularProgressIndicator()
        } else {
            Button(onClick = onSignIn) { Text(stringResource(R.string.sign_in_with_google)) }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SignInScreenPreview() {
    KGPSDiaryTheme { SignInScreen(busy = false, onSignIn = {}) }
}
