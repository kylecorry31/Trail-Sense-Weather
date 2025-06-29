package com.kylecorry.trail_sense_weather.ui

import android.annotation.SuppressLint
import android.content.Context
import android.text.format.DateUtils
import java.time.ZonedDateTime

class FormatService private constructor(private val context: Context) {
    fun formatDate(
        date: ZonedDateTime,
        includeWeekDay: Boolean = true,
        abbreviateMonth: Boolean = false
    ): String {
        return DateUtils.formatDateTime(
            context,
            date.toEpochSecond() * 1000,
            DateUtils.FORMAT_SHOW_DATE or (if (includeWeekDay) DateUtils.FORMAT_SHOW_WEEKDAY else 0) or DateUtils.FORMAT_SHOW_YEAR or (if (abbreviateMonth) DateUtils.FORMAT_ABBREV_MONTH else 0)
        )
    }

    fun formatDateTime(
        date: ZonedDateTime,
        includeWeekDay: Boolean = true,
        abbreviateMonth: Boolean = false
    ): String {
        return DateUtils.formatDateTime(
            context,
            date.toEpochSecond() * 1000,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or (if (includeWeekDay) DateUtils.FORMAT_SHOW_WEEKDAY else 0) or DateUtils.FORMAT_SHOW_YEAR or (if (abbreviateMonth) DateUtils.FORMAT_ABBREV_MONTH else 0)
        )
    }

    fun formatHour(
        hour: Int,
        includeAmPm: Boolean = true
    ): String {
        val isAm = hour < 12
        val formattedHour = if (includeAmPm) {
            if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
        } else {
            hour % 24
        }

        return "${formattedHour}${if (includeAmPm) if (isAm) " AM" else " PM" else ""}"
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        private var instance: FormatService? = null

        @Synchronized
        fun getInstance(context: Context): FormatService {
            if (instance == null) {
                instance = FormatService(context)
            }
            return instance!!
        }
    }

}