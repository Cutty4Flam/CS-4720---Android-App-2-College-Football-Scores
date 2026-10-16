package edu.virginia.footballscores

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Navy = Color(0xFF0E1830)
private val Panel = Color(0xFF1B2946)
private val Accent = Color(0xFF80D9C5)
private val Muted = Color(0xFFB7C5DC)

class MainActivity : ComponentActivity() {
    // Creates the local Room database.
    private val database by lazy {
        Room.databaseBuilder(applicationContext, ScoresDatabase::class.java, "football_scores.db").build()
    }
    // Connects the NCAA API and database through the repository.
    private val repository by lazy {
        val api = Retrofit.Builder()
            .baseUrl("https://ncaa-api.henrygd.me/")
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(NcaaApi::class.java)
        ScoresRepository(api, database)
    }
    // Keeps the screen state in the ViewModel.
    private val model: ScoresViewModel by viewModels { ScoresViewModel.Factory(repository) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Sets up the Compose UI with the app's dark color theme.
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                background = Navy, surface = Panel, primary = Accent,
                onBackground = Color.White, onSurface = Color.White
            )) { ScoresScreen(model) }
        }
    }
}

@Composable
fun ScoresScreen(vm: ScoresViewModel) {

    // Observes UI state and selected filters from the ViewModel.
    val state by vm.state.collectAsStateWithLifecycle()
    val week by vm.week.collectAsStateWithLifecycle()
    val division by vm.division.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(Navy).statusBarsPadding()) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp)) {
            Text("NCAA · ${vm.year} SEASON", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))

            // Header with the manual refresh button.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("College Football", Modifier.weight(1f), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = vm::refresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh scores", tint = Accent)
                }
            }
            Spacer(Modifier.height(14.dp))

            // Allows the user to switch between football weeks.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { vm.setWeek(week - 1) }, enabled = week > 1) { Text("‹") }
                Text("Week $week", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                OutlinedButton(onClick = { vm.setWeek(week + 1) }, enabled = week < 14) { Text("›") }
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))

            // Toggle between FBS and FCS divisions.
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("fbs", "fcs").forEachIndexed { i, value ->
                    SegmentedButton(
                        selected = division == value,
                        onClick = { vm.setDivision(value) },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                        label = { Text(value.uppercase()) }
                    )
                }
            }
        }
        // Displays the appropriate screen based on the current UI state.
        when (val s = state) {

            // Shows a loading indicator while fetching scores.
            ScoresUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Loading scores…", color = Muted)
                }
            }
            // Displays an error message and lets the user retry.
            is ScoresUiState.Error -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.message, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = vm::refresh) { Text("Retry") }
                }
            }
            is ScoresUiState.Success -> Column(Modifier.fillMaxSize()) {
                // Displays saved games, including when the app is offline.
                if (s.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())

                // Shows an offline banner when cached scores are being used.
                if (s.offline) Text("Offline · showing saved scores", Modifier.fillMaxWidth().background(Color(0xFF544225)).padding(12.dp), color = Color(0xFFFFE3A6))

                // An empty week is different from a network error.
                if (s.games.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No games this week 🏈", color = Muted, fontSize = 18.sp)
                } else LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Uses a unique game key to avoid duplicate list items.
                    items(s.games, key = { "${it.division}-${it.year}-${it.week}-${it.gameId}" }) { game -> GameCard(game) }
                }
            }
        }
    }
}
// Displays a game with its status, teams, and scores.
@Composable
private fun GameCard(game: GameEntity) {
    val state = game.gameState.lowercase()

    // Determine whether the game is finished, upcoming, or live.
    val finished = state.contains("final") || state.contains("post")
    val upcoming = !finished && (state.contains("pre") || state.contains("sched") || state.contains("future"))

    // Overtime begins after the fourth quarter.
    val overtime = game.currentPeriod.toIntOrNull()?.let { it > 4 } == true ||
            game.currentPeriod.contains("OT", true) || game.finalMessage.contains("OT", true)
    // Formats the game status based on its current state.
    val label = when {
        finished -> if (overtime) "FINAL / OT" else "FINAL"
        upcoming -> gameStartLabel(game)
        else -> {
            val period = game.currentPeriod.toIntOrNull()
            val quarter = when {
                period == null -> game.currentPeriod.ifBlank { "LIVE" }
                period > 4 -> "OT${if (period > 5) period - 4 else ""}"
                else -> "Q$period"
            }
            "LIVE · $quarter · ${game.contestClock.ifBlank { "--:--" }}"
        }
    }
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(label, fontSize = 12.sp, color = if (upcoming) Muted else Accent, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            TeamRow("AWAY", game.awayName, if (upcoming) "–" else game.awayScore.ifBlank { "–" }, finished && game.awayWinner)
            Spacer(Modifier.height(10.dp))
            TeamRow("HOME", game.homeName, if (upcoming) "–" else game.homeScore.ifBlank { "–" }, finished && game.homeWinner)
        }
    }
}
// Highlights the winning team after a game is finished.
@Composable
private fun TeamRow(side: String, name: String, score: String, won: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(side, color = Muted, fontSize = 10.sp, modifier = Modifier.width(44.dp))
        Text(name + if (won) "  ★" else "", modifier = Modifier.weight(1f), fontWeight = if (won) FontWeight.Bold else FontWeight.Medium)
        Text(score, fontSize = 22.sp, fontWeight = if (won) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = if (won) Accent else Color.White)
    }
}

// Converts the game's start time into the device's local time zone.
private fun gameStartLabel(game: GameEntity): String {
    val timestamp = game.startTimeEpoch
    if (timestamp > 0L) {
        val local = Instant.ofEpochSecond(timestamp).atZone(ZoneId.systemDefault())
        return local.format(DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a z"))
    }
    return "UPCOMING · ${game.startDate} ${game.startTime}".trim()
}
