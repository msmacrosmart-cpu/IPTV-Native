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
    const val CATALOG_WORK = "xtream-catalog-sync"
    const val GUIDE_WORK = "xtream-guide-sync"
    private const val LEGACY_CATALOG_WORK = "xtream-catalog-every-3-days"
    private const val LEGACY_GUIDE_WORK = "xtream-guide-every-12-hours"
    const val GUIDE_TTL_MILLIS = 24L * 60 * 60 * 1000

    fun schedule(context: Context, guideIntervalHours: Int = 24, catalogIntervalDays: Int = 3) {
        val manager = WorkManager.getInstance(context)
        val connected = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val days = catalogIntervalDays.coerceIn(1, 3).toLong()
        val catalog = PeriodicWorkRequestBuilder<CatalogSyncWorker>(days, TimeUnit.DAYS)
            .setInitialDelay(days, TimeUnit.DAYS).setConstraints(connected)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES).build()
        val hours = guideIntervalHours.coerceIn(6, 48).toLong()
        val guide = PeriodicWorkRequestBuilder<GuideSyncWorker>(hours, TimeUnit.HOURS)
            .setInitialDelay(hours, TimeUnit.HOURS).setConstraints(connected)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES).build()
        // Retire periodic jobs created by earlier versions so upgrades do not duplicate syncs.
        manager.cancelUniqueWork(LEGACY_CATALOG_WORK)
        manager.cancelUniqueWork(LEGACY_GUIDE_WORK)
        manager.enqueueUniquePeriodicWork(CATALOG_WORK, ExistingPeriodicWorkPolicy.UPDATE, catalog)
        manager.enqueueUniquePeriodicWork(GUIDE_WORK, ExistingPeriodicWorkPolicy.UPDATE, guide)
    }

    fun cancel(context: Context) {
        val manager = WorkManager.getInstance(context)
        listOf(CATALOG_WORK, GUIDE_WORK, LEGACY_CATALOG_WORK, LEGACY_GUIDE_WORK).forEach(manager::cancelUniqueWork)
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
            val last = if (loaded) app.epgRepository.guide.value.cachedAtMillis else 0L
            val fresh = last > 0 && System.currentTimeMillis() >= last &&
                System.currentTimeMillis() - last < TimeUnit.HOURS.toMillis(settings.epgIntervalHours.toLong())
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
