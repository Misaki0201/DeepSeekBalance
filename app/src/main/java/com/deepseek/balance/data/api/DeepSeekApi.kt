package com.deepseek.balance.data.api

import com.deepseek.balance.data.model.BalanceResponse
import com.deepseek.balance.data.model.UsageResponse
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import java.util.concurrent.TimeUnit

interface DeepSeekApi {

    @GET("user/balance")
    suspend fun getBalance(): BalanceResponse

    @GET("v1/usage")
    suspend fun getUsage(
        @retrofit2.http.Query("start_date") startDate: String,
        @retrofit2.http.Query("end_date") endDate: String
    ): UsageResponse

    companion object {
        private const val BASE_URL = "https://api.deepseek.com/"

        fun create(apiKey: String): DeepSeekApi {
            val authInterceptor = Interceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }

            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .addInterceptor(loggingInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(DeepSeekApi::class.java)
        }
    }
}
