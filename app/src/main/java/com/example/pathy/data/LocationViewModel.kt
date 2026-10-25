package com.example.pathy.data

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import com.mapbox.geojson.Point

class LocationViewModel : ViewModel() {

    private val _lastKnownLocation = MutableStateFlow<Point?>(null)

    fun updateLocation(point: Point) {
        _lastKnownLocation.value = point
    }
}

enum class MovementMode {
    WALKING, BICYCLE, DRIVING
}
