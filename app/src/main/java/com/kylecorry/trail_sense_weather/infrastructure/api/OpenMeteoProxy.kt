package com.kylecorry.trail_sense_weather.infrastructure.api

import android.content.Context
import android.net.Uri
import android.util.Log
import com.kylecorry.andromeda.files.CacheFileSystem
import com.kylecorry.andromeda.json.JsonConvert
import com.kylecorry.luna.coroutines.onIO
import com.kylecorry.sol.time.Time.isInPast
import com.kylecorry.sol.units.Coordinate
import com.kylecorry.sol.units.Distance
import com.kylecorry.sol.units.DistanceUnits
import com.kylecorry.sol.units.Speed
import com.kylecorry.sol.units.Temperature
import com.kylecorry.sol.units.TimeUnits
import com.kylecorry.trail_sense_weather.domain.DailyWeather
import com.kylecorry.trail_sense_weather.domain.Forecast
import com.kylecorry.trail_sense_weather.domain.HourlyWeather
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

class CurrentWeatherDto(
    val time: String,
    val temperature_2m: Float,
    val relative_humidity_2m: Float,
    val apparent_temperature: Float,
    val precipitation: Float,
    val rain: Float,
    val showers: Float,
    val snowfall: Float,
    val weather_code: Int,
    val cloud_cover: Float,
    val wind_speed_10m: Float,
    val wind_gusts_10m: Float
)

class HourlyWeatherDto(
    val time: List<String>,
    val temperature_2m: List<Float>,
    val apparent_temperature: List<Float>,
    val relative_humidity_2m: List<Float>,
    val precipitation_probability: List<Float>,
    val precipitation: List<Float>,
    val rain: List<Float>,
    val showers: List<Float>,
    val snowfall: List<Float>,
    val snow_depth: List<Float>,
    val weather_code: List<Int>,
    val cloud_cover: List<Float>,
    val visibility: List<Float>,
    val wind_speed_10m: List<Float>,
    val wind_gusts_10m: List<Float>
)

class DailyWeatherDto(
    val time: List<String>,
    val weather_code: List<Int>,
    val temperature_2m_max: List<Float>,
    val temperature_2m_min: List<Float>,
    val uv_index_max: List<Float>,
    val snowfall_sum: List<Float>,
    val showers_sum: List<Float>,
    val rain_sum: List<Float>,
    val wind_speed_10m_max: List<Float>,
    val wind_gusts_10m_max: List<Float>
)

class ForecastDto(
    val latitude: Double,
    val longitude: Double,
    val generationtime_ms: Double,
    val timezone: String,
    val utc_offset_seconds: Long,
    val current: CurrentWeatherDto,
    val hourly: HourlyWeatherDto,
    val daily: DailyWeatherDto
)

class OpenMeteoProxy(context: Context) {

    private val cache = CacheFileSystem(context)
    private val http = HttpService()

    private fun getUrl(location: Coordinate): String {
        val url = Uri.Builder()
            .scheme("https")
            .authority("api.open-meteo.com")
            .appendPath("v1")
            .appendPath("forecast")
            .appendQueryParameter("latitude", location.latitude.toString())
            .appendQueryParameter("longitude", location.longitude.toString())
            .appendQueryParameter(
                "daily",
                "weather_code,temperature_2m_max,temperature_2m_min,uv_index_max,snowfall_sum,showers_sum,rain_sum,wind_speed_10m_max,wind_gusts_10m_max"
            )
            .appendQueryParameter(
                "hourly",
                "temperature_2m,apparent_temperature,relative_humidity_2m,precipitation_probability,precipitation,weather_code,cloud_cover,visibility,wind_speed_10m,wind_gusts_10m,rain,showers,snowfall,snow_depth"
            )
            .appendQueryParameter(
                "current",
                "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,rain,showers,snowfall,weather_code,cloud_cover,wind_speed_10m,wind_gusts_10m"
            )
            .appendQueryParameter("timezone", "auto")
            .build()

        return url.toString()
    }

    private suspend fun getForecast(location: Coordinate): ForecastDto? = onIO {
        // TODO: Better cache handling + lat lon cache
        // TODO: Parse json
        if (cache.getFile("weather.json", create = false).exists()) {
            val converted =
                JsonConvert.fromJson<ForecastDto>(cache.getFile("weather.json").readText())

            if (converted != null) {
                val timeSinceGeneration = Duration.between(
                    Instant.parse(converted.current.time + ":00.000Z")
                        .minusSeconds(converted.utc_offset_seconds),
                    Instant.now()
                )

                val distanceToGeneration =
                    Coordinate(converted.latitude, converted.longitude).distanceTo(location)

                // Recent / close enough to keep using
                if (timeSinceGeneration < Duration.ofHours(1) && distanceToGeneration < Distance.miles(
                        5f
                    ).meters().distance
                ) {
                    Log.d("OpenMeteoProxy", "Using cached weather data")
                    return@onIO converted
                }
            }
        }

        val url = getUrl(location)
        val data = http.get(url)
        cache.getFile("weather.json").writeText(data)
        JsonConvert.fromJson<ForecastDto>(data)
    }

    suspend fun getWeather(location: Coordinate): Forecast? = onIO {
        val forecast = getForecast(location) ?: return@onIO null
        Forecast(
            HourlyWeather(
                time = Instant.parse(forecast.current.time + ":00.000Z")
                    .minusSeconds(forecast.utc_offset_seconds),
                temperature = Temperature.celsius(forecast.current.temperature_2m),
                feelsLikeTemperature = Temperature.celsius(forecast.current.apparent_temperature),
                humidity = forecast.current.relative_humidity_2m / 100f,
                precipitationProbability = 0f, // TODO: Get this from the closest hourly
                precipitation = Distance.meters(forecast.current.precipitation),
                rain = Distance.meters(forecast.current.rain),
                showers = Distance.meters(forecast.current.showers),
                snow = Distance.meters(forecast.current.snowfall),
                snowDepth = Distance.meters(0f), // Not available in current
                weatherCode = forecast.current.weather_code,
                cloudCover = forecast.current.cloud_cover / 100f,
                visibility = Distance.kilometers(forecast.hourly.visibility[0]), // Use first hourly visibility
                windSpeed = Speed(
                    forecast.current.wind_speed_10m,
                    DistanceUnits.Kilometers,
                    TimeUnits.Hours
                ),
                windGusts = Speed(
                    forecast.current.wind_gusts_10m,
                    DistanceUnits.Kilometers,
                    TimeUnits.Hours
                ),
                uvIndex = 0f // TODO
            ),
            forecast.hourly.time.mapIndexed { index, time ->
                HourlyWeather(
                    time = Instant.parse(time + ":00.000Z")
                        .minusSeconds(forecast.utc_offset_seconds),
                    temperature = Temperature.celsius(forecast.hourly.temperature_2m[index]),
                    feelsLikeTemperature = Temperature.celsius(forecast.hourly.apparent_temperature[index]),
                    humidity = forecast.hourly.relative_humidity_2m[index] / 100f,
                    precipitationProbability = forecast.hourly.precipitation_probability[index] / 100f,
                    precipitation = Distance.meters(forecast.hourly.precipitation[index]),
                    rain = Distance.meters(forecast.hourly.rain[index]),
                    showers = Distance.meters(forecast.hourly.showers[index]),
                    snow = Distance.meters(forecast.hourly.snowfall[index]),
                    snowDepth = Distance.meters(forecast.hourly.snow_depth[index]),
                    weatherCode = forecast.hourly.weather_code[index],
                    cloudCover = forecast.hourly.cloud_cover[index] / 100f,
                    visibility = Distance.kilometers(forecast.hourly.visibility[index]),
                    windSpeed = Speed(
                        forecast.hourly.wind_speed_10m[index],
                        DistanceUnits.Kilometers,
                        TimeUnits.Hours
                    ),
                    windGusts = Speed(
                        forecast.hourly.wind_gusts_10m[index],
                        DistanceUnits.Kilometers,
                        TimeUnits.Hours
                    ),
                    uvIndex = 0f // TODO
                )
            }.filter {
                it.time >= ZonedDateTime.now().withMinute(0).withSecond(0).withNano(0).toInstant()
            }.sortedBy { it.time },
            forecast.daily.time.mapIndexed { index, time ->
                DailyWeather(
                    date = LocalDate.parse(time),
                    weatherCode = forecast.daily.weather_code[index],
                    maxTemperature = Temperature.celsius(forecast.daily.temperature_2m_max[index]),
                    minTemperature = Temperature.celsius(forecast.daily.temperature_2m_min[index]),
                    maxWindSpeed = Speed(
                        forecast.daily.wind_speed_10m_max[index], DistanceUnits.Kilometers,
                        TimeUnits.Hours
                    ),
                    maxWindGusts = Speed(
                        forecast.daily.wind_gusts_10m_max[index], DistanceUnits.Kilometers,
                        TimeUnits.Hours
                    ),
                    snowfall = Distance.meters(forecast.daily.snowfall_sum[index]),
                    showers = Distance.meters(forecast.daily.showers_sum[index]),
                    rain = Distance.meters(forecast.daily.rain_sum[index]),
                    uvIndex = forecast.daily.uv_index_max[index]
                )
            }.filter { it.date.isAfter(LocalDate.now().minusDays(1)) }.sortedBy { it.date }
        )
    }

}