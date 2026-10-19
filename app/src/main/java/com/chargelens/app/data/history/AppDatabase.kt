package com.chargelens.app.data.history

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Insert
    suspend fun insertSample(sample: SampleEntity)

    @Query("SELECT * FROM sessions WHERE type = :type ORDER BY startTime DESC LIMIT :limit")
    fun observeSessions(type: SessionType, limit: Int = 200): Flow<List<SessionEntity>>

    @Query("SELECT * FROM samples WHERE sessionId = :sessionId ORDER BY time")
    fun observeSamples(sessionId: Long): Flow<List<SampleEntity>>

    @Query("UPDATE sessions SET endTime = updatedAt WHERE endTime IS NULL")
    suspend fun closeDanglingSessions()

    @Query("SELECT * FROM sessions ORDER BY startTime")
    suspend fun allSessions(): List<SessionEntity>

    @Query("SELECT * FROM samples ORDER BY time")
    suspend fun allSamples(): List<SampleEntity>

    @Query("DELETE FROM sessions")
    suspend fun clearAll()
}

@Database(entities = [SessionEntity::class, SampleEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
}
