package com.kylecorry.trail_sense_weather.ui

import android.widget.ImageView
import android.widget.TextView
import com.kylecorry.andromeda.core.ui.useService
import com.kylecorry.andromeda.fragments.XmlReactiveFragment
import com.kylecorry.andromeda.fragments.useBackgroundMemo
import com.kylecorry.andromeda.sense.location.GPS
import com.kylecorry.andromeda.views.list.AndromedaListView
import com.kylecorry.andromeda.views.list.ListItem
import com.kylecorry.andromeda.views.list.ResourceListIcon
import com.kylecorry.sol.science.geography.CoordinateFormatter.toDecimalDegrees
import com.kylecorry.sol.time.Time.plusHours
import com.kylecorry.sol.time.Time.toZonedDateTime
import com.kylecorry.sol.units.DistanceUnits
import com.kylecorry.sol.units.TemperatureUnits
import com.kylecorry.sol.units.TimeUnits
import com.kylecorry.trail_sense_weather.R
import com.kylecorry.trail_sense_weather.domain.WeatherCodeLookup
import com.kylecorry.trail_sense_weather.infrastructure.BuiltInCityLookup
import com.kylecorry.trail_sense_weather.infrastructure.api.OpenMeteoProxy
import kotlinx.coroutines.withTimeout
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

class MainFragment : XmlReactiveFragment(R.layout.fragment_main) {
    override fun onUpdate() {
        val listView = useView<AndromedaListView>(R.id.list)
        val locationView = useView<TextView>(R.id.location)
        val currentTemperatureView = useView<TextView>(R.id.current_temperature)
        val currentWeatherView = useView<TextView>(R.id.current_weather)
        val currentWeatherImageView = useView<ImageView>(R.id.current_weather_image)
        val currentHighLowView = useView<TextView>(R.id.current_high_low)
        val context = useAndroidContext()
        val weatherApi = useMemo(context) { OpenMeteoProxy(context) }
        val cityLookup = useMemo(context) { BuiltInCityLookup(context) }
        val formatter = useService<FormatService>()

        // TODO: Cache last known location
        val location = useBackgroundMemo(resetOnResume) {
            val gps = GPS(context)
            if (Duration.between(gps.time, Instant.now()).toHours() > 5) {
                withTimeout(10000) {
                    gps.read()
                }
            }
            gps.location
        }

        val city = useBackgroundMemo(location, cityLookup) {
            location?.let { cityLookup.getCityName(it) }
        }

        val data = useBackgroundMemo(weatherApi, location, resetOnResume) {
            if (location == null) {
                return@useBackgroundMemo null
            }
            weatherApi.getWeather(location)
        }

        useEffect(data, listView, formatter) {
            data ?: return@useEffect

            var items = mutableListOf<ListItem>()

            items.add(ListItem(0, "HOURLY"))

            val hourlyItems = data.hourly.filter { it.time < Instant.now().plusHours(24) }.map {
                val zoned = it.time.toZonedDateTime()
                val isNow =
                    zoned.toLocalDate() == LocalDate.now() && zoned.toLocalTime().hour == LocalTime.now().hour

                val formattedTime = if (isNow) {
                    "Now"
                } else {
                    formatter.formatHour(it.time.toZonedDateTime().hour)
                }

                ListItem(
                    it.time.toEpochMilli(),
                    "$formattedTime  -  ${WeatherCodeLookup.getWeatherDescription(it.weatherCode)}",
                    subtitle = "${it.temperature.convertTo(TemperatureUnits.F).temperature.roundToInt()}°  -  ${(it.precipitationProbability * 100).roundToInt()}% precip.  -  ${(it.humidity * 100).roundToInt()}% humidity  -  ${
                        it.windSpeed.convertTo(
                            DistanceUnits.Miles, TimeUnits.Hours
                        ).speed.roundToInt()
                    } mph",
                    icon = ResourceListIcon(WeatherCodeLookup.getWeatherImage(it.weatherCode))
                )
            }
            items.addAll(hourlyItems)

            items.add(ListItem(1, "DAILY"))
            val dailyItems = data.daily.map {
                val maxTemp =
                    it.maxTemperature.convertTo(TemperatureUnits.F).temperature.roundToInt()
                val minTemp =
                    it.minTemperature.convertTo(TemperatureUnits.F).temperature.roundToInt()
                ListItem(
                    it.date.toEpochDay(),
                    "${it.date.monthValue}/${it.date.dayOfMonth}  -  ${
                        WeatherCodeLookup.getWeatherDescription(
                            it.weatherCode
                        )
                    }",
                    subtitle = "$maxTemp° / $minTemp°",
                    icon = ResourceListIcon(WeatherCodeLookup.getWeatherImage(it.weatherCode))
                )
            }

            items.addAll(dailyItems)

            listView.setItems(items)
        }

        useEffect(locationView, city, location) {
            locationView.text = city ?: location?.toDecimalDegrees(2)
        }

        useEffect(
            data,
            currentTemperatureView,
            currentWeatherView,
            currentWeatherImageView,
            currentHighLowView
        ) {
            if (data == null) {
                currentTemperatureView.text = "Loading"
                currentWeatherView.text = "-"
                currentHighLowView.text = "-"
                currentWeatherImageView.setImageDrawable(null)
                return@useEffect
            }

            val currentTempConverted =
                data.current.temperature.convertTo(TemperatureUnits.F).temperature
            val today = data.daily.firstOrNull { it.date == LocalDate.now() }
            val currentHighConverted =
                today?.maxTemperature?.convertTo(TemperatureUnits.F)?.temperature
            val currentLowConverted =
                today?.minTemperature?.convertTo(TemperatureUnits.F)?.temperature

            currentWeatherImageView.setImageResource(WeatherCodeLookup.getWeatherImage(data.current.weatherCode))
            currentWeatherView.text =
                WeatherCodeLookup.getWeatherDescription(data.current.weatherCode)
            currentTemperatureView.text = "${currentTempConverted.roundToInt()}°"
            currentHighLowView.text =
                if (currentHighConverted != null && currentLowConverted != null) {
                    "${currentHighConverted.roundToInt()}° / ${currentLowConverted.roundToInt()}°"
                } else {
                    "-"
                }
        }

    }
}