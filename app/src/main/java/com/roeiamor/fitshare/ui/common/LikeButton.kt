package com.roeiamor.fitshare.ui.common

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.roeiamor.fitshare.R
import com.roeiamor.fitshare.databinding.ViewLikeButtonBinding

/**
 * The app's signature element (SPEC section 7): the heart-and-dumbbell mark with its counter.
 *
 * It is a view, not a pair of widgets wired up per screen, because the same control appears on every
 * feed card and on the details screen, and the animation has to behave identically in both.
 *
 * Deliberately **dumb**. It renders whatever state it is given and reports taps; it does not know
 * what a like is, never writes anything, and holds no opinion about what should happen next. The
 * ViewModel owns the truth, which is what lets the same button be driven by a live Firestore
 * listener without the two disagreeing.
 *
 * Two animations, both short enough to feel like feedback rather than an effect:
 *  - the mark springs past its final size and settles back, via an overshoot interpolator;
 *  - the counter fades out, changes, and fades in, so the number never appears to teleport.
 */
class LikeButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding = ViewLikeButtonBinding.inflate(LayoutInflater.from(context), this)

    /** Whether the mark is currently drawn as liked. Kept so a re-render can skip the animation. */
    private var isLiked: Boolean = false

    /**
     * The count currently on screen, so an unchanged value does not trigger a pointless fade.
     *
     * Nullable, and that matters: initialised to 0 it would already "match" a workout with no
     * likes, the first render would skip the update, and the counter would stay blank forever.
     * Null means "nothing has been drawn yet", so the first render always writes the number.
     */
    private var currentCount: Long? = null

    init {
        orientation = HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        isClickable = true
        isFocusable = true
        minimumHeight = resources.getDimensionPixelSize(R.dimen.touch_target_min)
        applyTint(liked = false)
    }

    /**
     * Renders a state.
     *
     * @param liked whether the signed-in user has liked this workout.
     * @param count the like total.
     * @param animate false when the state is being restored - a rebound feed card or a rotation -
     *   so the button does not bounce at views the user never touched.
     */
    fun render(liked: Boolean, count: Long, animate: Boolean) {
        val likedChanged = liked != isLiked
        val isFirstRender = currentCount == null
        val countChanged = count != currentCount

        isLiked = liked
        applyTint(liked)

        if (animate && likedChanged && !isFirstRender) springMark()
        // The very first draw never animates: the number appears with the screen rather than
        // fading in from nothing, and the mark does not bounce at a view the user never touched.
        if (countChanged) setCount(count, animate = animate && !isFirstRender)

        contentDescription = context.getString(
            if (liked) R.string.cd_like_remove else R.string.cd_like
        )
    }

    /** The mint fill when liked, a muted outline colour when not. */
    private fun applyTint(liked: Boolean) {
        val colorRes = if (liked) R.color.link_text else R.color.text_muted
        binding.likeMark.setColorFilter(ContextCompat.getColor(context, colorRes))
    }

    /** A short scale that overshoots and settles - the tap feedback from SPEC section 7. */
    private fun springMark() {
        binding.likeMark.animate().cancel()
        binding.likeMark.scaleX = SPRING_FROM_SCALE
        binding.likeMark.scaleY = SPRING_FROM_SCALE
        binding.likeMark.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(SPRING_DURATION_MS)
            .setInterpolator(OvershootInterpolator(SPRING_TENSION))
            .start()
    }

    /** Swaps the number, fading through so it does not appear to jump. */
    private fun setCount(count: Long, animate: Boolean) {
        currentCount = count

        if (!animate) {
            binding.likeCount.alpha = 1f
            binding.likeCount.text = count.asCountLabel()
            return
        }

        binding.likeCount.animate().cancel()
        binding.likeCount.animate()
            .alpha(0f)
            .setDuration(COUNT_FADE_MS)
            .withEndAction {
                binding.likeCount.text = count.asCountLabel()
                binding.likeCount.animate().alpha(1f).setDuration(COUNT_FADE_MS).start()
            }
            .start()
    }

    private companion object {
        const val SPRING_DURATION_MS = 150L
        const val SPRING_FROM_SCALE = 0.75f
        const val SPRING_TENSION = 3f
        const val COUNT_FADE_MS = 90L
    }
}
