package com.majidali.appcloner

import android.app.Application
import com.majidali.appcloner.data.crash.CrashReporter
import com.majidali.appcloner.data.local.BackupManager
import com.majidali.appcloner.data.local.CloneStore
import com.majidali.appcloner.data.profile.ProfileManager
import com.majidali.appcloner.data.sharing.FileShuttle
import com.majidali.appcloner.domain.engine.CloneEngine
import com.majidali.appcloner.domain.engine.SandboxCloneEngine
import com.majidali.appcloner.domain.engine.WorkProfileCloneEngine
import com.majidali.appcloner.domain.repository.CloneRepository

/**
 * Application class. It owns the [AppContainer], which wires the app
 * together by hand — no dependency-injection framework, because V1
 * has only a handful of objects and a framework would be more code
 * than the wiring itself.
 *
 * Clean-architecture shape:
 *   ui.*          -> screens, adapters, view models. No Android
 *                   framework class is ever constructed in domain/.
 *   domain.*      -> pure rules: models, the CloneEngine contract,
 *                   the CloneRepository contract. No Android imports
 *                   except where the platform forces them.
 *   data.*        -> the real implementations: profile, storage,
 *                   backup, file shuttle.
 */
class AppClonerApp : Application() {

    lateinit var container: AppContainer
        private set

    /**
     * Set when building the container failed. The main screen checks
     * for a saved crash report first, so a startup failure is shown
     * on the crash screen instead of killing the app before it
     * starts. Null on a normal launch.
     */
    var containerInitError: Throwable? = null
        private set

    override fun onCreate() {
        super.onCreate()
        // First, before anything else is built: catch any crash and
        // save the exact error, so a crash on a phone can be fixed
        // from the real cause instead of a guess. See CrashReporter.
        CrashReporter.install(this)
        // The container must never kill the launch: if building it
        // throws, write the error as a crash report and let the main
        // screen show it — the user then sees what broke instead of
        // the app closing back to the home screen every time.
        try {
            container = AppContainer(this)
        } catch (t: Throwable) {
            containerInitError = t
            CrashReporter.saveReport(this, t)
        }
    }
}

/**
 * One place that builds every collaborator. Screens take what they
 * need from here instead of building objects themselves, which keeps
 * them small and testable.
 *
 * To add the V2 sandbox engine, build it here and hand it to the
 * screens alongside [cloneEngine] — no screen code changes shape.
 */
class AppContainer(context: android.content.Context) {

    private val appContext: android.content.Context =
        context.applicationContext

    val profileManager: ProfileManager = ProfileManager(appContext)

    val cloneRepository: CloneRepository = CloneStore(appContext)

    /** V1 engine: clones through the managed work profile. */
    val cloneEngine: CloneEngine =
        WorkProfileCloneEngine(appContext, profileManager)

    /**
     * V2 engine: sandbox mode. Present but unavailable in V1, so the
     * Limits screen can describe it honestly and the code already
     * has the seam it will plug into.
     */
    @Suppress("unused")
    val sandboxEngine: CloneEngine = SandboxCloneEngine()

    val backupManager: BackupManager = BackupManager(appContext, cloneRepository)

    val fileShuttle: FileShuttle = FileShuttle(appContext)
}
