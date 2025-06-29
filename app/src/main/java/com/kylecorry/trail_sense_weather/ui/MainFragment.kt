package com.kylecorry.trail_sense_weather.ui

import com.kylecorry.andromeda.core.ui.useService
import com.kylecorry.andromeda.fragments.XmlReactiveFragment
import com.kylecorry.andromeda.fragments.useBackgroundMemo
import com.kylecorry.andromeda.sense.location.GPS
import com.kylecorry.andromeda.views.list.AndromedaListView
import com.kylecorry.andromeda.views.list.ListItem
import com.kylecorry.andromeda.views.toolbar.Toolbar
import com.kylecorry.sol.time.Time.plusHours
import com.kylecorry.sol.time.Time.toZonedDateTime
import com.kylecorry.sol.units.DistanceUnits
import com.kylecorry.sol.units.TemperatureUnits
import com.kylecorry.sol.units.TimeUnits
import com.kylecorry.trail_sense_weather.R
import com.kylecorry.trail_sense_weather.domain.WeatherCodeLookup
import com.kylecorry.trail_sense_weather.infrastructure.api.OpenMeteoProxy
import kotlinx.coroutines.withTimeout
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

class MainFragment : XmlReactiveFragment(R.layout.fragment_main) {
    override fun onUpdate() {
        val listView = useView<AndromedaListView>(R.id.list)
        val toolbar = useView<Toolbar>(R.id.home_title)
        val context = useAndroidContext()
        val proxy = useMemo(context) { OpenMeteoProxy(context) }
        val formatter = useService<FormatService>()

        val location = useBackgroundMemo {
            val gps = GPS(context)
            if (Duration.between(gps.time, Instant.now()).toHours() > 5) {
                withTimeout(10000) {
                    gps.read()
                }
            }
            gps.location
        }

        val data = useBackgroundMemo(proxy, location) {
            if (location == null) {
                return@useBackgroundMemo null
            }
            proxy.getWeather(location)
        }

        useEffect(data, listView, formatter) {
            data ?: return@useEffect

            val items = data.hourly.filter { it.time < Instant.now().plusHours(24) }.map {
                ListItem(
                    it.time.toEpochMilli(),
                    formatter.formatHour(
                        it.time.toZonedDateTime().hour
                    ) + "  -  " + WeatherCodeLookup.getWeatherDescription(it.weatherCode),
                    subtitle = "${it.temperature.convertTo(TemperatureUnits.F).temperature.roundToInt()}° F  -  ${(it.precipitationProbability * 100).roundToInt()}% precip.  -  ${(it.humidity * 100).roundToInt()}% humidity  -  ${
                        it.windSpeed.convertTo(
                            DistanceUnits.Miles, TimeUnits.Hours
                        ).speed.roundToInt()
                    } mph",
                )
            }

            listView.setItems(items)
        }

        useEffect(data, toolbar) {
            if (data == null) {
                toolbar.title.text = "Loading"
                return@useEffect
            }
            toolbar.title.text = WeatherCodeLookup.getWeatherDescription(data.current.weatherCode)
            toolbar.subtitle.text =
                "${data.current.temperature.convertTo(TemperatureUnits.F).temperature.roundToInt()}° F  -  ${(data.current.humidity * 100).roundToInt()}% humidity  -  ${
                    data.current.windSpeed.convertTo(
                        DistanceUnits.Miles,
                        TimeUnits.Hours
                    ).speed.roundToInt()
                } mph"
        }

    }
}