package com.itravel.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.itravel.app.data.AppDatabase
import com.itravel.app.data.PhotoStorage
import com.itravel.app.data.Place
import com.itravel.app.data.PlaceWithPhotos
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.get(app).placeDao()

    val places = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun savePlace(
        existing: Place?,
        title: String,
        placeName: String?,
        latitude: Double,
        longitude: Double,
        notes: String?,
        visitDate: Long,
        sortDate: Long,
        photoPaths: List<String>,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val cover = photoPaths.firstOrNull()
            if (existing == null) {
                val id = dao.insert(
                    Place(
                        title = title,
                        placeName = placeName?.takeIf { it.isNotBlank() },
                        latitude = latitude,
                        longitude = longitude,
                        notes = notes?.takeIf { it.isNotBlank() },
                        visitDate = visitDate,
                        sortDate = sortDate,
                        coverPath = cover
                    )
                )
                if (photoPaths.isNotEmpty()) {
                    dao.insertPhotos(photoPaths.map {
                        com.itravel.app.data.Photo(placeId = id, path = it)
                    })
                }
            } else {
                dao.update(
                    existing.copy(
                        title = title,
                        placeName = placeName?.takeIf { it.isNotBlank() },
                        latitude = latitude,
                        longitude = longitude,
                        notes = notes?.takeIf { it.isNotBlank() },
                        visitDate = visitDate,
                        sortDate = sortDate,
                        coverPath = cover
                    )
                )
                dao.clearPhotos(existing.id)
                if (photoPaths.isNotEmpty()) {
                    dao.insertPhotos(photoPaths.map {
                        com.itravel.app.data.Photo(placeId = existing.id, path = it)
                    })
                }
            }
            onDone()
        }
    }

    fun deletePlace(item: PlaceWithPhotos) {
        viewModelScope.launch {
            item.photos.forEach { PhotoStorage.delete(it.path) }
            dao.delete(item.place)
        }
    }
}
