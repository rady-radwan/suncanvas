package com.suncanvas.app.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class LocationDao {
    @Query("SELECT * FROM locations ORDER BY sortOrder, id")
    abstract fun observeAll(): Flow<List<LocationEntity>>

    @Query("SELECT * FROM locations ORDER BY sortOrder, id")
    abstract suspend fun getAll(): List<LocationEntity>

    @Query("SELECT * FROM locations WHERE id = :id")
    abstract fun observe(id: Long): Flow<LocationEntity?>

    @Query("SELECT * FROM locations WHERE id = :id")
    abstract suspend fun get(id: Long): LocationEntity?

    @Query("SELECT * FROM locations WHERE isDefault = 1 LIMIT 1")
    abstract suspend fun getDefault(): LocationEntity?

    @Query("SELECT * FROM locations WHERE source = 'DEVICE' LIMIT 1")
    abstract suspend fun getDevice(): LocationEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM locations")
    abstract suspend fun maxSortOrder(): Int

    @Insert
    abstract suspend fun insert(location: LocationEntity): Long

    @Update
    abstract suspend fun update(location: LocationEntity)

    @Query("DELETE FROM locations WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("UPDATE locations SET isDefault = (id = :id)")
    abstract suspend fun setDefault(id: Long)

    @Query("UPDATE locations SET sortOrder = :order WHERE id = :id")
    abstract suspend fun setSortOrder(id: Long, order: Int)

    @Transaction
    open suspend fun reorder(idsInOrder: List<Long>) {
        idsInOrder.forEachIndexed { index, id -> setSortOrder(id, index + 1) }
    }
}

@Dao
interface SolarCacheDao {
    @Query("SELECT * FROM solar_cache WHERE `key` = :key")
    suspend fun get(key: String): SolarCacheEntity?

    @Upsert
    suspend fun put(entity: SolarCacheEntity)

    @Query("DELETE FROM solar_cache WHERE computedAt < :olderThan")
    suspend fun prune(olderThan: Long)
}

@Dao
interface WidgetDao {
    @Query("SELECT * FROM widget_designs ORDER BY updatedAt DESC")
    fun observeDesigns(): Flow<List<WidgetDesignEntity>>

    @Query("SELECT * FROM widget_designs ORDER BY updatedAt DESC")
    suspend fun getDesigns(): List<WidgetDesignEntity>

    @Query("SELECT * FROM widget_designs WHERE id = :id")
    suspend fun getDesign(id: Long): WidgetDesignEntity?

    @Insert
    suspend fun insertDesign(design: WidgetDesignEntity): Long

    @Update
    suspend fun updateDesign(design: WidgetDesignEntity)

    @Query("DELETE FROM widget_designs WHERE id = :id")
    suspend fun deleteDesign(id: Long)

    @Query("SELECT * FROM widget_instances ORDER BY createdAt")
    fun observeInstances(): Flow<List<WidgetInstanceEntity>>

    @Query("SELECT * FROM widget_instances")
    suspend fun getInstances(): List<WidgetInstanceEntity>

    @Query("SELECT * FROM widget_instances WHERE appWidgetId = :id")
    suspend fun getInstance(id: Int): WidgetInstanceEntity?

    @Query("SELECT * FROM widget_instances WHERE appWidgetId = :id")
    fun observeInstance(id: Int): Flow<WidgetInstanceEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putInstance(instance: WidgetInstanceEntity)

    @Query("DELETE FROM widget_instances WHERE appWidgetId = :id")
    suspend fun deleteInstance(id: Int)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY id")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders ORDER BY id")
    suspend fun getAll(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun get(id: Long): ReminderEntity?

    @Insert
    suspend fun insert(reminder: ReminderEntity): Long

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: Long)
}

class Converters {
    @TypeConverter fun sourceToString(s: LocationSource): String = s.name
    @TypeConverter fun stringToSource(s: String): LocationSource = LocationSource.valueOf(s)
    @TypeConverter fun eventToString(e: ReminderEvent): String = e.name
    @TypeConverter fun stringToEvent(s: String): ReminderEvent = ReminderEvent.valueOf(s)
}

@Database(
    entities = [
        LocationEntity::class,
        SolarCacheEntity::class,
        WidgetDesignEntity::class,
        WidgetInstanceEntity::class,
        ReminderEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun locations(): LocationDao
    abstract fun solarCache(): SolarCacheDao
    abstract fun widgets(): WidgetDao
    abstract fun reminders(): ReminderDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "suncanvas.db").build()
    }
}
