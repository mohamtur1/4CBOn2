package io.github.mohamtur1.fourcbon2

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FourCBOn2PrototypeApp() }
    }
}

@Composable
private fun FourCBOn2PrototypeApp() {
    var destination by rememberSaveable { mutableStateOf(AppDestination.HOME) }
    MaterialTheme {
        Scaffold(bottomBar = {
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
        }) { padding ->
            Surface(Modifier.fillMaxSize().padding(padding)) {
                when (destination) {
                    AppDestination.HOME -> HomeScreen()
                    AppDestination.ASK -> AskScreen()
                    AppDestination.AGENTS -> DraftScreen()
                    AppDestination.BROWSER -> BrowserScreen()
                    AppDestination.ACTIVITY -> Page("Activity") {
                        StatusCard("No automated actions", "Drafts and browser handoffs do not run agents. An action timeline is not connected in this build.")
                    }
                }
            }
        }
    }
}

@Composable
private fun Page(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun HomeScreen() = Page("4CBOn2") {
    Text("Your offline-first companion prototype", style = MaterialTheme.typography.titleMedium)
    StatusCard("Start with a draft", "Use Agents to save one private goal on this device. Open Browser for manual links to GitHub, Kaggle and Colab.")
    StatusCard("What is not connected", "AI answers, autonomous agents, phone control, authenticated service access and submissions are not active. Saving a draft does not start a job.")
    Text("Prototype • Android 8+ • No device compatibility or production security certification", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun AskScreen() = Page("Ask") {
    StatusCard("AI answers unavailable", "No model or knowledge search is connected. Use Agents to write down a goal for later; it will not be sent anywhere.")
    Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("Ask a question — unavailable") }
}

@Composable
private fun DraftScreen() {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("private_goal_draft", Context.MODE_PRIVATE) }
    var title by rememberSaveable { mutableStateOf(preferences.getString("title", "") ?: "") }
    var goal by rememberSaveable { mutableStateOf(preferences.getString("goal", "") ?: "") }
    var message by rememberSaveable { mutableStateOf("") }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Page("Agent draft") {
        Text("Plan a goal offline. This is a note only: it cannot run, queue work or control apps.")
        OutlinedTextField(value = title, onValueChange = { title = it.take(DraftValidation.MAX_TITLE); message = "" },
            label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = goal, onValueChange = { goal = it.take(DraftValidation.MAX_GOAL); message = "" },
            label = { Text("What would you like to do?") }, minLines = 4, modifier = Modifier.fillMaxWidth())
        Text("${goal.length}/${DraftValidation.MAX_GOAL} characters • Do not enter passwords or tokens.", style = MaterialTheme.typography.bodySmall)
        Button(modifier = Modifier.fillMaxWidth(), onClick = {
            val error = DraftValidation.error(title, goal)
            message = if (error != null) error else try {
                if (preferences.edit().putString("title", title).putString("goal", goal).commit())
                    "Draft saved on this device." else "Could not save the draft. Try again."
            } catch (_: Exception) { "Could not save the draft. Try again." }
        }) { Text("Save draft") }
        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { confirmDelete = true }) { Text("Delete saved draft") }
        if (message.isNotEmpty()) Text(message, style = MaterialTheme.typography.bodyMedium)
        StatusCard("Private to this app", "The saved draft stays in app storage and is not uploaded. It is removed if the app is uninstalled or its data is cleared. This storage is not intended for secrets.")
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete draft?") },
            text = { Text("This removes the saved draft and the text currently being edited.") },
            confirmButton = { TextButton(onClick = {
                val deleted = try { preferences.edit().clear().commit() } catch (_: Exception) { false }
                if (deleted) { title = ""; goal = ""; message = "Draft deleted." }
                else message = "Could not delete the saved draft."
                confirmDelete = false
            }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BrowserScreen() {
    val context = LocalContext.current
    var message by rememberSaveable { mutableStateOf("") }
    Page("Browser") {
        Text("Open a service in your browser. Sign-in and all actions remain under your control; the app cannot see or control the page.")
        listOf(
            "GitHub repository" to "https://github.com/mohamtur1/4CBOn2",
            "Kaggle" to "https://www.kaggle.com/",
            "Google Colab" to "https://colab.research.google.com/",
        ).forEach { (label, url) ->
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
                message = try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    "Handed off to a browser. Sign-in and service availability are not verified."
                } catch (_: ActivityNotFoundException) { "No browser is available on this device." }
                  catch (_: SecurityException) { "Android could not open this link." }
            }) { Text("Open $label") }
        }
        if (message.isNotEmpty()) Text(message, style = MaterialTheme.typography.bodyMedium)
        StatusCard("Manual handoff only", "These links do not connect an account, run a notebook or grant the app browser access. The planned public Kaggle6 notebook path has not been verified.")
    }
}

@Composable
private fun StatusCard(title: String, detail: String) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
