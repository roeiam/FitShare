package com.roeiamor.fitshare.di

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.roeiamor.fitshare.data.remote.AuthDataSource
import com.roeiamor.fitshare.data.remote.UserDataSource
import com.roeiamor.fitshare.data.repository.AuthRepository
import com.roeiamor.fitshare.data.repository.AuthRepositoryImpl

/**
 * The project's dependency container, written by hand instead of using Hilt or Koin.
 *
 * Everything the app needs is created here, in one place, in an order you can read top to bottom:
 * Firebase singletons, then data sources over them, then repositories over those, then the factory
 * that hands repositories to ViewModels. [init] is called once from
 * [com.roeiamor.fitshare.FitShareApp.onCreate].
 *
 * Everything is `by lazy`, so nothing is built until something asks for it and each dependency
 * exists exactly once for the life of the process.
 */
object ServiceLocator {

    /**
     * Application context, kept so later phases can reach SharedPreferences, the ContentResolver
     * and the cache directory without leaking an Activity.
     */
    private lateinit var applicationContext: Context

    // ---- Firebase --------------------------------------------------------------------------

    /** The Firebase Authentication entry point, shared by every auth data source. */
    private val firebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    /** The Cloud Firestore entry point, shared by every Firestore data source. */
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    // ---- Data sources ----------------------------------------------------------------------

    private val authDataSource: AuthDataSource by lazy { AuthDataSource(firebaseAuth) }

    private val userDataSource: UserDataSource by lazy { UserDataSource(firestore) }

    // ---- Repositories ----------------------------------------------------------------------

    /** Accounts and sessions. Exposed as the interface so callers cannot reach Firebase through it. */
    val authRepository: AuthRepository by lazy {
        AuthRepositoryImpl(authDataSource, userDataSource)
    }

    // ---- ViewModels ------------------------------------------------------------------------

    /** The single factory every Fragment uses to obtain its ViewModel. */
    val viewModelFactory: ViewModelFactory by lazy { ViewModelFactory(authRepository) }

    // ---- Lifecycle -------------------------------------------------------------------------

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
