package com.michael.tradelab.di

import android.content.Context
import androidx.room.Room
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.michael.tradelab.data.local.TradeLabDatabase
import com.michael.tradelab.data.remote.BinanceApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    @Provides @Singleton
    fun provideBinanceApi(client: OkHttpClient, json: Json): BinanceApi = Retrofit.Builder()
        .baseUrl(BinanceApi.BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(BinanceApi::class.java)

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TradeLabDatabase =
        Room.databaseBuilder(context, TradeLabDatabase::class.java, "tradelab.db")
            // Virtual-only data; safe to rebuild on schema change.
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun providePairDao(db: TradeLabDatabase) = db.pairDao()
    @Provides fun provideTickerDao(db: TradeLabDatabase) = db.tickerDao()
    @Provides fun provideCandleDao(db: TradeLabDatabase) = db.candleDao()
    @Provides fun providePortfolioDao(db: TradeLabDatabase) = db.portfolioDao()
    @Provides fun provideIndicatorDao(db: TradeLabDatabase) = db.indicatorDao()
    @Provides fun provideAlertDao(db: TradeLabDatabase) = db.alertDao()
    @Provides fun provideEntitlementDao(db: TradeLabDatabase) = db.entitlementDao()
}
