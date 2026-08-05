package com.roeiamor.fitshare.di

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.roeiamor.fitshare.BuildConfig
import com.roeiamor.fitshare.data.remote.AuthDataSource
import com.roeiamor.fitshare.data.remote.CloudinaryApi
import com.roeiamor.fitshare.data.remote.CloudinaryImageUploader
import com.roeiamor.fitshare.data.remote.ImageUploader
import com.roeiamor.fitshare.data.remote.InteractionDataSource
import com.roeiamor.fitshare.data.remote.UserDataSource
import com.roeiamor.fitshare.data.remote.WorkoutDataSource
import com.roeiamor.fitshare.data.repository.AuthRepository
import com.roeiamor.fitshare.data.repository.AuthRepositoryImpl
import com.roeiamor.fitshare.data.repository.InteractionRepository
import com.roeiamor.fitshare.data.repository.InteractionRepositoryImpl
import com.roeiamor.fitshare.data.repository.UserRepository
import com.roeiamor.fitshare.data.repository.UserRepositoryImpl
import com.roeiamor.fitshare.data.repository.WorkoutRepository
import com.roeiamor.fitshare.data.repository.WorkoutRepositoryImpl
import com.roeiamor.fitshare.util.ImageCompressor
import com.roeiamor.fitshare.util.NetworkGuard
import com.roeiamor.fitshare.util.NetworkMonitor
import com.roeiamor.fitshare.util.ThemePreferences
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

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

    private val workoutDataSource: WorkoutDataSource by lazy { WorkoutDataSource(firestore) }

    private val interactionDataSource: InteractionDataSource by lazy {
        InteractionDataSource(firestore)
    }

    // ---- Cloudinary ------------------------------------------------------------------------

    /**
     * The HTTP client behind Retrofit.
     *
     * Uploads carry a photo over mobile data, so the timeouts are deliberately longer than OkHttp's
     * ten-second defaults - a slow connection should be slow, not a failure. Request logging is
     * added only in debug builds, so a release APK never prints upload URLs to logcat.
     */
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(UPLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(UPLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC)
                    )
                }
            }
            .build()
    }

    private val cloudinaryApi: CloudinaryApi by lazy {
        Retrofit.Builder()
            .baseUrl(CLOUDINARY_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CloudinaryApi::class.java)
    }

    private val imageCompressor: ImageCompressor by lazy { ImageCompressor(requireContext()) }

    /** Swapping storage providers means changing this one line (SPEC section 1). */
    private val imageUploader: ImageUploader by lazy {
        CloudinaryImageUploader(cloudinaryApi, imageCompressor)
    }

    // ---- Platform ----------------------------------------------------------------------------

    /** Watches connectivity: drives the offline banner and lets [networkGuard] fail fast. */
    val networkMonitor: NetworkMonitor by lazy { NetworkMonitor(requireContext()) }

    /** Wraps every network-bound repository call in a connectivity check and a timeout. */
    private val networkGuard: NetworkGuard by lazy { NetworkGuard(networkMonitor) }

    /** The stored light/dark choice. Read once at startup by FitShareApp. */
    val themePreferences: ThemePreferences by lazy { ThemePreferences(requireContext()) }

    // ---- Repositories ----------------------------------------------------------------------

    /** Accounts and sessions. Exposed as the interface so callers cannot reach Firebase through it. */
    val authRepository: AuthRepository by lazy {
        AuthRepositoryImpl(authDataSource, userDataSource, networkGuard)
    }

    // ---- ViewModels ------------------------------------------------------------------------

    /** Reading and publishing workouts. Exposed as the interface, never the implementation. */
    val workoutRepository: WorkoutRepository by lazy {
        WorkoutRepositoryImpl(
            workoutDataSource, userDataSource, authDataSource, imageUploader, networkGuard
        )
    }

    /** Likes, comments and favourites for the signed-in user. */
    val interactionRepository: InteractionRepository by lazy {
        InteractionRepositoryImpl(
            interactionDataSource, userDataSource, authDataSource, networkGuard
        )
    }

    /** Profiles: reading one, and editing your own. */
    val userRepository: UserRepository by lazy {
        UserRepositoryImpl(
            userDataSource, workoutDataSource, authDataSource, imageUploader, networkGuard
        )
    }

    /** The factory for screens that need no arguments. */
    val viewModelFactory: ViewModelFactory by lazy {
        ViewModelFactory(authRepository, workoutRepository, interactionRepository, userRepository)
    }

    /**
     * The factory for screens that are *about* a particular workout - the details screen, and the
     * add form when it is editing.
     *
     * Not a `by lazy` singleton like the one above, because the argument differs per screen. Built
     * fresh each time, which is cheap: a factory holds references, not state.
     */
    fun viewModelFactory(screenArgument: String?): ViewModelFactory =
        ViewModelFactory(
            authRepository, workoutRepository, interactionRepository, userRepository, screenArgument
        )

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

    private const val CLOUDINARY_BASE_URL = "https://api.cloudinary.com/"
    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val UPLOAD_TIMEOUT_SECONDS = 60L
}
