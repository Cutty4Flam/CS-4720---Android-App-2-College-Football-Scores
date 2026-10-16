package edu.virginia.footballscores

import androidx.room.withTransaction

class ScoresRepository(private val api: NcaaApi, private val db: ScoresDatabase) {
    private val dao get() = db.scoresDao()

    fun observe(division: String, year: Int, week: Int) = dao.observeGames(division, year, week)
    suspend fun games(division: String, year: Int, week: Int) = dao.getGames(division, year, week)
    suspend fun hasCache(division: String, year: Int, week: Int) = dao.hasLoaded(division, year, week) > 0

    suspend fun refresh(division: String, year: Int, week: Int) {
        val response = api.getScores(division, year, week.toString().padStart(2, '0'))
        val newRows = response.games.orEmpty().mapNotNull { it.game }
            .filter { !it.gameID.isNullOrBlank() }
            .map { it.toEntity(division, year, week) }
            .distinctBy { it.gameId }
        // Entire response is applied atomically. A 200 + [] is a valid, cached empty week.
        db.withTransaction {
            if (newRows.isEmpty()) dao.deleteWeek(division, year, week)
            else {
                dao.upsertGames(newRows)
                dao.deleteMissing(division, year, week, newRows.map { it.gameId })
            }
            dao.markLoaded(LoadedWeek(division, year, week))
        }
    }
}
