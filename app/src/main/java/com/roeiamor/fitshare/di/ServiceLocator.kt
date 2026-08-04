package com.roeiamor.fitshare.di

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * The project's dependency container, written by hand instead of using Hilt or Koin.
 *
 * Everything the app needs is created here, in one place, in an order you can read top to
 * bottom. [init] is called once from [com.roeiamor.fitshare.FitShareApp.onCreate]; from then
 * on ViewModels receive their dependencies through
 * [com.roeiamor.fitshare.di.ViewModelFactory].
 *
 * Data sources and repositories are added phase by phase - Phase 3 brings auth, Phase 4 the
 * feed, and so on. Only the shared Firebase singletons exist today.
 */
object ServiceLocator {

    /**
     * Application context, kept so later phases can reach SharedPreferences, the
     * ContentResolver and the cache directory without leaking an Activity.
     */
    private lateinit var applicationContext: Context

    /** The Firebase Authentication entry point, shared by every auth data source. */
    val firebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    /** The Cloud Firestore entry point, shared by every Firestore data source. */
    val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    /**
     * Stores the application context. Must be called exactly once, from the Application class,
     * before anything else touches this object.
     */
    fun init(context: Context) {
        applicationContext = context.applicationContext
    }

    /**
     * Returns the application context.
     * Throws if [init] has not run yet, which turns a subtle null crash into an obvious one.
     */
    fun requireContext(): Context {
        check(::applicationContext.isInitialized) {
            "ServiceLocator.init() was never called. Call it from FitShareApp.onCreate()."
        }
        return applicationContext
    }
}
