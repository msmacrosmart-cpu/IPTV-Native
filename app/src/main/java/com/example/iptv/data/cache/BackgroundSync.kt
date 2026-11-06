package com.example.iptv.data.cache

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.iptv.IPTVApplication
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/** WorkManager is intentionally inexact: Android may defer work for Doze/battery/network. */
object BackgroundSync {
    const val CATALOG_WORK = "xtream-catalog-every-3-days"
    const val GUIDE_WORK = "xtream-guide-every-12-hours"
    const val GUIDE_TTL_MILLIS = 12L * 60 * 60 * 1000

    fun schedule(context: Context) {
        val manager = WorkManager.getInstance(context)
        val connected = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val catalog = PeriodicWorkRequestBuilder<CatalogSyncWorker>(3, TimeUnit.DAYS)
            .setInitialDelay(3, TimeUnit.DAYS).setConstraints(connected)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES).build()
        val guide = PeriodicWorkRequestBuilder<GuideSyncWorker>(12, TimeUnit.HOURS)
            .setInitialDelay(12, TimeUnit.HOURS).setConstraints(connected)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES).build()
        manager.enqueueUniquePeriodicWork(CATALOG_WORK, ExistingPeriodicWorkPolicy.KEEP, catalog)
        manager.enqueueUniquePeriodicWork(GUIDE_WORK, ExistingPeriodicWorkPolicy.KEEP, guide)
    }

    fun cancel(context: Context) {
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork(CATALOG_WORK)
        manager.cancelUniqueWork(GUIDE_WORK)
    }
}

class CatalogSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as IPTVApplication
        val credentials = app.repository.currentCredentials() ?: return Result.success()
        val token = app.repository.sessionToken(credentials) ?: return Result.success()
        return try {
            val refreshed = app.catalogCache.refresh(credentials)
            if (!app.repository.isSessionCurrent(credentials, token)) return Result.success()
            val heroOk = refreshed.snapshot?.let {
                app.heroLoader.prepare(credentials, releaseHeroTargets(it))
            } ?: false
            if (refreshed.failures == 0 && heroOk) Result.success() else Result.retry()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

/** XMLTV needs a shorter horizon than the catalog, because its parsed window is 48 hours. */
class GuideSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as IPTVApplication
        val credentials = app.repository.currentCredentials() ?: return Result.success()
        val token = app.repository.sessionToken(credentials) ?: return Result.success()
        val settings = app.repository.settings.value
        if (!settings.autoUpdateEpg) return Result.success()
        return try {
            val loaded = app.epgRepository.loadFromDisk(credentials, settings.customXmltvUrl)
            if (!app.repository.isSessionCurrent(credentials, token)) return Result.success()
            val last = settings.lastEpgSyncMillis
            val fresh = last > 0 && System.currentTimeMillis() >= last &&
                System.currentTimeMillis() - last < BackgroundSync.GUIDE_TTL_MILLIS
            if (loaded && fresh) return Result.success()
            val updated = app.epgRepository.refresh(credentials, settings.customXmltvUrl)
            if (!app.repository.isSessionCurrent(credentials, token)) return Result.success()
            if (updated && app.repository.markEpgSyncedIfActive(credentials, token)) Result.success() else Result.retry()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
