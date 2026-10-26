package io.github.mohamtur1.fourcbon2

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FourCBOn2PrototypeApp()
        }
    }
}

@Composable
private fun FourCBOn2PrototypeApp() {
    var destination by remember { mutableStateOf(AppDestination.HOME) }
    var showKaggleManualHandoff by remember { mutableStateOf(false) }

    MaterialTheme {
        Scaffold(
            bottomBar = {
                if (!showKaggleManualHandoff) {
                    NavigationBar {
                        AppDestination.entries.forEach { item ->
                            NavigationBarItem(
                                selected = destination == item,
                                onClick = { destination = item },
                                icon = { Text(item.marker) },
                                label = { Text(item.label) },
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (showKaggleManualHandoff) {
                    KaggleManualHandoffScreen(onClose = { showKaggleManualHandoff = false })
                } else {
                    when (destination) {
                        AppDestination.HOME -> HomeScreen(
                            onOpenKaggleManualHandoff = { showKaggleManualHandoff = true },
                        )
                        AppDestination.ASK -> AskScreen()
                        AppDestination.AGENTS -> PlaceholderScreen(
                            title = "Agents",
                            detail = "Agent profiles, scopes, and task drafts are not built yet.",
                        )
                        AppDestination.BROWSER -> PlaceholderScreen(
                            title = "Browser",
                            detail = "No general browser workspace or phone/browser control is active yet. The Kaggle screen offers only an explicit manual competition handoff.",
                        )
                        AppDestination.ACTIVITY -> PlaceholderScreen(
                            title = "Activity",
                            detail = "No agent actions have run. This timeline will later show real plans, actions, observations, and checks.",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    onOpenKaggleManualHandoff: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "4CBOn2",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            modifier = Modifier.padding(top = 8.dp),
            text = "Android companion prototype",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(24.dp))
        StatusCard(
            title = "Foundation status",
            detail = "Manual Kaggle competition handoff is prepared. Ask, tutorials, agents, general browser access, active service connections, and phone control are not active yet.",
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onOpenKaggleManualHandoff,
        ) {
            Text("Kaggle competition — manual")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Choose a section below. Unfinished features stay visibly unavailable until they are implemented and verified.",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun KaggleManualHandoffScreen(
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var competitionInput by remember { mutableStateOf("") }
    var competitionReference by remember {
        mutableStateOf<KaggleCompetitionReference?>(null)
    }
    var handoffState by remember { mutableStateOf(KaggleManualHandoffState()) }

    fun closeAndClear() {
        competitionInput = ""
        competitionReference = null
        handoffState = handoffState.onCancel()
        onClose()
    }

    BackHandler {
        closeAndClear()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Kaggle competition — manual",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        StatusCard(
            title = "No Kaggle API or app sign-in",
            detail = "This screen stores no Kaggle credential, reads no Kaggle account data, and cannot join or submit for you.",
        )
        Text(
            text = "1. Paste a competition link. 2. Tap to request an installed app or browser. 3. On Kaggle, sign in if needed, review the rules, and tap Join yourself.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "Kaggle controls its own website and account session. This app does not see that session or verify whether you joined or submitted.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = competitionInput,
            onValueChange = { updatedInput ->
                competitionInput = updatedInput
                val validatedReference = KaggleCompetitionReference.parse(updatedInput)
                competitionReference = validatedReference
                handoffState = handoffState.onReferenceChanged(
                    validReferencePresent = validatedReference != null,
                )
            },
            label = { Text("Competition link") },
            supportingText = {
                Text(
                    when {
                        competitionInput.isBlank() ->
                            "Use https://www.kaggle.com/competitions/<competition-name>"
                        competitionReference != null ->
                            "Competition-link format accepted. Nothing has opened yet."
                        else ->
                            "Enter one HTTPS Kaggle competition link without extra paths or parameters."
                    },
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                autoCorrect = false,
                keyboardType = KeyboardType.Uri,
            ),
        )
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = handoffState.canOpen,
            onClick = {
                val currentReference = competitionReference
                if (currentReference == null) {
                    handoffState = handoffState.onOpenRequested()
                } else {
                    try {
                        val browserIntent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(currentReference.url),
                        ).addCategory(Intent.CATEGORY_BROWSABLE)
                        context.startActivity(browserIntent)
                        handoffState = handoffState.onOpenRequested()
                    } catch (_: ActivityNotFoundException) {
                        handoffState = handoffState.onOpenFailed()
                    } catch (_: SecurityException) {
                        handoffState = handoffState.onOpenFailed()
                    }
                }
            },
        ) {
            Text("Open competition page — manual handoff")
        }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                competitionInput = ""
                competitionReference = null
                handoffState = handoffState.onCancel()
            },
        ) {
            Text("Cancel and clear link")
        }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { closeAndClear() },
        ) {
            Text("Back")
        }
        when (handoffState.notice) {
            KaggleManualHandoffState.Notice.NONE -> Unit
            KaggleManualHandoffState.Notice.INVALID_REFERENCE -> Text(
                text = "A valid Kaggle competition link is required. No external handoff was requested.",
                style = MaterialTheme.typography.bodyMedium,
            )
            KaggleManualHandoffState.Notice.OPEN_REQUESTED -> Text(
                text = "External app or browser handoff requested. Joining, rules acceptance, and submission remain manual and unverified by this app.",
                style = MaterialTheme.typography.bodyMedium,
            )
            KaggleManualHandoffState.Notice.OPEN_FAILED -> Text(
                text = "No installed app or browser accepted the handoff. No competition page was opened by this app.",
                style = MaterialTheme.typography.bodyMedium,
            )
            KaggleManualHandoffState.Notice.CANCELLED -> Text(
                text = "Cancelled. The competition link was cleared and no external handoff was requested.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun AskScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Ask",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            modifier = Modifier.padding(top = 8.dp),
            text = "Questions and How do I? tutorials will live here.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = false,
            onClick = {},
        ) {
            Text("Ask a Question — coming soon")
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = false,
            onClick = {},
        ) {
            Text("How do I? tutorial — coming soon")
        }
        Spacer(modifier = Modifier.height(16.dp))
        StatusCard(
            title = "Truthful status",
            detail = "No local knowledge search, Colab answer, guide-me routine, or app action is active in this build.",
        )
    }
}

@Composable
private fun PlaceholderScreen(
    title: String,
    detail: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(16.dp))
        StatusCard(title = "Not active yet", detail = detail)
    }
}

@Composable
private fun StatusCard(
    title: String,
    detail: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                modifier = Modifier.padding(top = 8.dp),
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
