package com.inovixlabs.tazkiyah.di

import android.content.Context
import androidx.room.Room
import com.inovixlabs.tazkiyah.BuildConfig
import com.inovixlabs.tazkiyah.data.local.TazkiyahDatabase
import com.inovixlabs.tazkiyah.data.local.dao.CatalogDao
import com.inovixlabs.tazkiyah.data.local.dao.UserDao
import com.inovixlabs.tazkiyah.data.remote.HttpContentSource
import com.inovixlabs.tazkiyah.data.repository.SettingsRepository
import com.inovixlabs.tazkiyah.data.sync.AssetContentSource
import com.inovixlabs.tazkiyah.data.sync.CatalogImporter
import com.inovixlabs.tazkiyah.data.sync.ContentSyncer
import com.inovixlabs.tazkiyah.data.sync.RoomCatalogStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun okHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): TazkiyahDatabase =
        Room.databaseBuilder(context, TazkiyahDatabase::class.java, "tazkiyah.db").build()

    @Provides
    fun catalogDao(db: TazkiyahDatabase): CatalogDao = db.catalogDao()

    @Provides
    fun userDao(db: TazkiyahDatabase): UserDao = db.userDao()

    @Provides
    @Singleton
    fun contentSyncer(db: TazkiyahDatabase, settings: SettingsRepository): ContentSyncer =
        ContentSyncer(
            store = RoomCatalogStore(db, CatalogImporter(db.catalogDao(), BuildConfig.CONTENT_BASE_URL)),
            state = settings,
            appVersionCode = BuildConfig.VERSION_CODE,
        )

    @Provides
    @Singleton
    fun remoteContent(client: OkHttpClient): HttpContentSource =
        HttpContentSource(client, listOf(BuildConfig.CONTENT_BASE_URL, BuildConfig.CONTENT_FALLBACK_URL))

    @Provides
    @Singleton
    fun seedContent(@ApplicationContext context: Context): AssetContentSource = AssetContentSource(context.assets)
}
