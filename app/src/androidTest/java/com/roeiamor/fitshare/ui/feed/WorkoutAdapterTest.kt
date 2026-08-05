package com.roeiamor.fitshare.ui.feed

import android.widget.FrameLayout
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.Timestamp
import com.roeiamor.fitshare.R
import com.roeiamor.fitshare.data.model.Difficulty
import com.roeiamor.fitshare.data.model.Workout
import com.roeiamor.fitshare.data.model.WorkoutCategory
import com.roeiamor.fitshare.databinding.ItemWorkoutBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

/**
 * The project's one instrumented test (SPEC section 12): a real [WorkoutAdapter] inflates the real
 * `item_workout.xml` against the real resource table, fills it from a [Workout], and reports the tap.
 *
 * It runs on a device rather than on the JVM because that is the only place the things it checks
 * exist - the layout, the string and plural resources, and a view hierarchy that can be clicked. A
 * JVM test would have to replace all three with fakes and would then be testing the fakes.
 *
 * SPEC section 12 phrases this as "the like click reaches the ViewModel". On the feed card the like
 * is a counter, not a control - the interactive LikeButton lives on the details screen - so the
 * click asserted here is the one the card actually has: the whole card opening that workout.
 *
 * Labels are compared against `getString`, not against literal Hebrew, so the test passes whatever
 * language the device running it is set to. What it pins down is the mapping - STRENGTH must reach
 * `category_strength` - which is the part that could silently break.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutAdapterTest {

    /**
     * The app context wearing the app theme.
     *
     * The bare instrumentation context carries no theme, and `item_workout.xml` starts with a
     * `MaterialCardView`, which reads Material3 attributes at inflation time and throws without
     * them. Wrapping is what an Activity would do for us; this test does not need an Activity for
     * anything else.
     */
    private val context = ContextThemeWrapper(
        InstrumentationRegistry.getInstrumentation().targetContext,
        R.style.Theme_FitShare
    )

    /** A fully populated workout, so every view on the card has something to show. */
    private val workout = Workout(
        id = "workout-1",
        authorId = "user-1",
        authorName = "רועי עמור",
        title = "אימון כוח לפלג גוף עליון",
        description = "תיאור",
        category = WorkoutCategory.STRENGTH.name,
        durationMinutes = 45,
        difficulty = Difficulty.MEDIUM.name,
        likesCount = 12,
        commentsCount = 4,
        createdAt = Timestamp(Date())
    )

    /**
     * Binding fills the card: title, author, the three meta chips and both counters.
     *
     * The chips matter most - they go through the shared `view_meta_chips.xml` and `EnumLabels`, so
     * this covers the enum-to-label mapping and the shared component in one pass.
     */
    @Test
    fun bindingAWorkoutFillsTheCard() {
        val binding = ItemWorkoutBinding.bind(createBoundViewHolder(workout).itemView)

        assertEquals(workout.title, binding.workoutTitle.text.toString())
        assertEquals(workout.authorName, binding.authorName.text.toString())

        assertEquals(
            context.getString(R.string.category_strength),
            binding.metaChips.categoryChip.text.toString()
        )
        assertEquals(
            context.getString(R.string.difficulty_medium),
            binding.metaChips.difficultyChip.text.toString()
        )
        assertTrue(
            "the duration chip should mention 45",
            binding.metaChips.durationChip.text.contains("45")
        )

        assertEquals("12", binding.likesCount.text.toString())
        assertEquals("4", binding.commentsCount.text.toString())
    }

    /** A workout with no photo still binds, rather than crashing on a null image URL. */
    @Test
    fun aWorkoutWithNoPhotoStillBinds() {
        val binding = ItemWorkoutBinding.bind(
            createBoundViewHolder(workout.copy(imageUrl = null)).itemView
        )

        assertEquals(workout.title, binding.workoutTitle.text.toString())
    }

    /** Tapping the card reports the workout that was bound, so the Fragment can navigate. */
    @Test
    fun tappingTheCardReportsThatWorkout() {
        var tapped: Workout? = null
        val holder = createBoundViewHolder(workout, onWorkoutClick = { tapped = it })

        holder.itemView.performClick()

        assertEquals(workout, tapped)
    }

    /** Tapping the author reports the author's uid, not the workout. */
    @Test
    fun tappingTheAuthorReportsThatUser() {
        var openedUserId: String? = null
        val holder = createBoundViewHolder(workout, onAuthorClick = { openedUserId = it })

        ItemWorkoutBinding.bind(holder.itemView).authorName.performClick()

        assertEquals(workout.authorId, openedUserId)
    }

    /**
     * Runs the adapter's real `onCreateViewHolder` and `bind` against an inflated card.
     *
     * `submitList` is deliberately avoided: it diffs on a background thread, so the test would have
     * to wait on a callback for no benefit. What is under test is the binding, not `ListAdapter`.
     */
    private fun createBoundViewHolder(
        workout: Workout,
        onWorkoutClick: (Workout) -> Unit = {},
        onAuthorClick: (String) -> Unit = {}
    ): WorkoutAdapter.WorkoutViewHolder {
        val adapter = WorkoutAdapter(onWorkoutClick, onAuthorClick)
        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
        holder.bind(workout)
        return holder
    }
}
