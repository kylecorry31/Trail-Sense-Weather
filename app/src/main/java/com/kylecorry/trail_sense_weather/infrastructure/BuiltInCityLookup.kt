package com.kylecorry.trail_sense_weather.infrastructure

import android.content.Context
import android.location.Geocoder
import com.kylecorry.sol.units.Coordinate

class BuiltInCityLookup(private val context: Context) {

    private val geocoder = Geocoder(context)

    suspend fun getCityName(location: Coordinate): String? {
        return try {
            // TODO: Support the callback version
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            if (addresses.isNullOrEmpty()) {
                null
            } else {
                addresses[0].locality ?: addresses[0].adminArea ?: addresses[0].countryName
            }
        } catch (e: Exception) {
            null // Handle exceptions such as network issues or no results found
        }
    }

}