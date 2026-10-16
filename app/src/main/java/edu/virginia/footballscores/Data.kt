package edu.virginia.footballscores

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import retrofit2.http.GET
import retrofit2.http.Path

// Models the JSON response returned by the NCAA API.
data class ScoreboardResponse(val games: List<GameWrapper>? = null)
data class GameWrapper(val game: NetworkGame? = null)
data class TeamNames(val short: String? = null, val full: String? = null)

// Stores team details such as name, score, and winner status.
data class NetworkTeam(
    val names: TeamNames? = null,
    val winner: Boolean = false,
    val score: String? = null,
)

// Represents one game received from the API.
data class NetworkGame(
    val gameID: String? = null,
    val away: NetworkTeam? = null,
    val home: NetworkTeam? = null,
    val gameState: String? = null,
    val startDate: String? = null,
    val startTime: String? = null,
    val startTimeEpoch: String? = null,
    val currentPeriod: String? = null,
    val contestClock: String? = null,
    val finalMessage: String? = null
)

// Retrofit interface for requesting scores by division, year, and week.
interface NcaaApi {
    @GET("scoreboard/football/{division}/{year}/{week}")
    suspend fun getScores(
        @Path("division") division: String,
        @Path("year") year: Int,
        @Path("week") week: String
    ): ScoreboardResponse
}
// Game information is saved locally so that scores are accessible offline.
// A composite primary key will prevent duplicate game records within the same week.
@Entity(tableName = "games", primaryKeys = ["division", "year", "week", "gameId"])
data class GameEntity(
    val division: String,
    val year: Int,
    val week: Int,
    val gameId: String,
    val awayName: String,
    val homeName: String,
    val awayScore: String,
    val homeScore: String,
    val awayWinner: Boolean,
    val homeWinner: Boolean,
    val gameState: String,
    val startDate: String,
    val startTime: String,
    val startTimeEpoch: Long,
    val currentPeriod: String,
    val contestClock: String,
    val finalMessage: String
)
// Tracks which weeks have already been fetched, including empty weeks.
@Entity(tableName = "loaded_weeks", primaryKeys = ["division", "year", "week"])
data class LoadedWeek(val division: String, val year: Int, val week: Int)

@Dao
interface ScoresDao {
    // Observes database changes so the UI can update automatically.
    @Query("SELECT * FROM games WHERE division=:division AND year=:year AND week=:week ORDER BY startTimeEpoch ASC, gameId ASC")
    fun observeGames(division: String, year: Int, week: Int): Flow<List<GameEntity>>

    // Gets saved games for a selected week.
    @Query("SELECT * FROM games WHERE division=:division AND year=:year AND week=:week ORDER BY startTimeEpoch ASC, gameId ASC")
    suspend fun getGames(division: String, year: Int, week: Int): List<GameEntity>

    // Checks whether a week has been loaded before.
    @Query("SELECT COUNT(*) FROM loaded_weeks WHERE division=:division AND year=:year AND week=:week")
    suspend fun hasLoaded(division: String, year: Int, week: Int): Int

    // Inserts new games or replaces existing games with updated scores.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGames(games: List<GameEntity>)

    // Marks a week as successfully fetched.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markLoaded(week: LoadedWeek)

    // Removes games that are no longer included in the API response.
    @Query("DELETE FROM games WHERE division=:division AND year=:year AND week=:week AND gameId NOT IN (:ids)")
    suspend fun deleteMissing(division: String, year: Int, week: Int, ids: List<String>)

    // Clears all saved games for a specific week.
    @Query("DELETE FROM games WHERE division=:division AND year=:year AND week=:week")
    suspend fun deleteWeek(division: String, year: Int, week: Int)
}
// Defines the Room database and provides access to the DAO.
@Database(entities = [GameEntity::class, LoadedWeek::class], version = 1, exportSchema = false)
abstract class ScoresDatabase : RoomDatabase() {
    abstract fun scoresDao(): ScoresDao
}

// Converts an API game into a database entity.
// Missing fields are replaced with safe default values.
fun NetworkGame.toEntity(division: String, year: Int, week: Int): GameEntity = GameEntity(
    division = division, year = year, week = week,
    gameId = gameID.orEmpty(),
    awayName = away?.names?.short?.takeIf { it.isNotBlank() } ?: "Away team",
    homeName = home?.names?.short?.takeIf { it.isNotBlank() } ?: "Home team",
    awayScore = away?.score.orEmpty(), homeScore = home?.score.orEmpty(),
    awayWinner = away?.winner == true, homeWinner = home?.winner == true,
    gameState = gameState.orEmpty(), startDate = startDate.orEmpty(),
    startTime = startTime.orEmpty(), startTimeEpoch = startTimeEpoch?.toLongOrNull() ?: 0L,
    currentPeriod = currentPeriod.orEmpty(), contestClock = contestClock.orEmpty(),
    finalMessage = finalMessage.orEmpty()
)
