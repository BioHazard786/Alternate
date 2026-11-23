package com.lulu786.alternate

import android.app.Application
import com.lulu786.alternate.data.AppDatabase
import com.lulu786.alternate.data.BackupService
import com.lulu786.alternate.data.ContactRepository
import com.lulu786.alternate.data.LegacyMmkvImport
import com.lulu786.alternate.data.PhotoStore
import com.lulu786.alternate.data.Prefs
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("no AppContainer") }

/** Manual DI: one container, created with the Application. */
class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val prefs = Prefs(app)
    val db by lazy { AppDatabase.get(app) }
    val photos = PhotoStore(app)
    val repo by lazy { ContactRepository(db, photos, prefs) }
    val backups by lazy { BackupService(app, repo, prefs) }
}

class AlternateApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Must be read before Room creates/opens the file below.
        val upgrading = getDatabasePath(AppDatabase.NAME).exists()
        container.scope.launch(Dispatchers.IO) {
            LegacyMmkvImport(this@AlternateApp, container.prefs).run(upgrading)
            runCatching { container.repo.finishLegacyMigration() }
        }
    }
}
