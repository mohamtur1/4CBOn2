package io.github.mohamtur1.fourcbon2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

    MaterialTheme {
        Scaffold(
            bottomBar = {
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
            },
        ) { innerPadding ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                when (destination) {
                    AppDestination.HOME -> HomeScreen()
                    AppDestination.ASK -> AskScreen()
                    AppDestination.AGENTS -> PlaceholderScreen(
                        title = "Agents",
                        detail = "Agent profiles, scopes, and task drafts are not built yet.",
                    )
                    AppDestination.BROWSER -> PlaceholderScreen(
                        title = "Browser",
                        detail = "No browser workspace, sign-in handoff, or phone/browser control is active yet.",
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

@Composable
private fun HomeScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
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
            detail = "Navigation is available. Ask, tutorials, agents, browser access, service connections, and phone control are not active yet.",
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Choose a section below. Unfinished features stay visibly unavailable until they are implemented and verified.",
            style = MaterialTheme.typography.bodyLarge,
        )
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
