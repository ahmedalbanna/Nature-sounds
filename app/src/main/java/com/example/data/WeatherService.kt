package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class CurrentWeather(
    @Json(name = "temperature") val temperature: Float,
    @Json(name = "windspeed") val windspeed: Float,
    @Json(name = "weathercode") val weathercode: Int,
    @Json(name = "is_day") val isDay: Int
)

@JsonClass(generateAdapter = true)
data class WeatherResponse(
    @Json(name = "current_weather") val current_weather: CurrentWeather?
)

interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun getWeather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current_weather") currentWeather: Boolean = true
    ): WeatherResponse
}

object WeatherClient {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.open-meteo.com/")
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val api: OpenMeteoApi = retrofit.create(OpenMeteoApi::class.java)
}

object WeatherUtils {
    fun getWeatherDescription(code: Int): String {
        return when (code) {
            0 -> "صافي تماماً ☀️"
            1 -> "صافي غالباً 🌤️"
            2 -> "غائم جزئياً ⛅"
            3 -> "غائم كلياً ☁️"
            45, 48 -> "الأجواء ضبابية 🌫️"
            51, 53, 55 -> "رذاذ مطر خفيف 🌧️"
            56, 57 -> "رذاذ متجمد بارد ❄️🌧️"
            61, 63, 65 -> "مطر متساقط 🌧️⛈"
            66, 67 -> "مطر بارد متجمد ❄️🌧"
            71, 73, 75, 77 -> "ثلوج بيضاء متساقطة ❄️☃️"
            80, 81, 82 -> "زخات مطرية منعشة 🌧️🌦"
            85, 86 -> "زخات ثلجية كثيفة ❄️☃️"
            95, 96, 99 -> "عاصفة رعدية قوية ⛈️⚡"
            else -> "طقس طبيعي معتدل 🍃"
        }
    }
}
