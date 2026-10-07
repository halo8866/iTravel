package com.itravel.app.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Transaction
import androidx.room.Update

@Entity(tableName = "places")
data class Place(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val placeName: String? = null,
    val latitude: Double,
    val longitude: Double,
    val notes: String? = null,
    val visitDate: Long = System.currentTimeMillis(),
    val coverPath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(
            entity = Place::class,
            parentColumns = ["id"],
            childColumns = ["placeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("placeId")]
)
data class Photo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val placeId: Long,
    val path: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class PlaceWithPhotos(
    @Embedded val place: Place,
    @Relation(
        parentColumn = "id",
        entityColumn = "placeId"
    )
    val photos: List<Photo>
)

@Dao
interface PlaceDao {

    @Transaction
    @Query("SELECT * FROM places ORDER BY visitDate DESC, createdAt DESC")
    fun observeAll(): kotlinx.coroutines.flow.Flow<List<PlaceWithPhotos>>

    @Query("SELECT * FROM places WHERE id = :id")
    suspend fun getById(id: Long): Place?

    @Insert
    suspend fun insert(place: Place): Long

    @Update
    suspend fun update(place: Place)

    @Delete
    suspend fun delete(place: Place)

    @Insert
    suspend fun insertPhotos(photos: List<Photo>)

    @Query("DELETE FROM photos WHERE placeId = :placeId")
    suspend fun clearPhotos(placeId: Long)
}
