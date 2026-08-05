package com.roeiamor.fitshare.ui.common

import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.roeiamor.fitshare.R
import com.roeiamor.fitshare.databinding.LayoutStateEmptyBinding
import com.roeiamor.fitshare.databinding.LayoutStateErrorBinding
import com.roeiamor.fitshare.databinding.LayoutStateLoadingBinding

/**
 * Shows exactly one of a screen's four states: loading, empty, error or content (SPEC section 5).
 *
 * Every screen includes the same three state layouts and hands them to this class, so the
 * show-one-hide-three logic is written once rather than repeated in nine fragments. A screen never
 * toggles a state view's visibility itself - it calls one of the four methods here.
 *
 * @param content the screen's own content view, hidden whenever a state view is showing.
 * @param loading the included layout_state_loading binding.
 * @param empty the included layout_state_empty binding.
 * @param error the included layout_state_error binding.
 */
class StateRenderer(
    private val content: View,
    private val loading: LayoutStateLoadingBinding,
    private val empty: LayoutStateEmptyBinding,
    private val error: LayoutStateErrorBinding
) {

    /** Shows the spinner and hides everything else. */
    fun showLoading() = show(loading.root)

    /** Shows the screen's real content and hides every state view. */
    fun showContent() = show(content)

    /**
     * Shows the empty state.
     *
     * @param titleRes the headline, for example "no workouts here yet".
     * @param bodyRes the sentence under it, explaining what to do next.
     * @param iconRes the illustration. Defaults to the plus, which suits every screen that is empty
     *   because nothing has been added yet; a screen saying something is *gone* passes its own.
     * @param actionRes optional button label. Pass null when the screen has no useful action.
     * @param onAction invoked when that button is tapped.
     */
    fun showEmpty(
        @StringRes titleRes: Int,
        @StringRes bodyRes: Int,
        @DrawableRes iconRes: Int = R.drawable.ic_empty_state,
        @StringRes actionRes: Int? = null,
        onAction: (() -> Unit)? = null
    ) {
        empty.emptyIcon.setImageResource(iconRes)
        empty.emptyTitle.setText(titleRes)
        empty.emptyBody.setText(bodyRes)
        if (actionRes != null && onAction != null) {
            empty.emptyAction.setText(actionRes)
            empty.emptyAction.visibility = View.VISIBLE
            empty.emptyAction.setOnClickListener { onAction() }
        } else {
            empty.emptyAction.visibility = View.GONE
            empty.emptyAction.setOnClickListener(null)
        }
        show(empty.root)
    }

    /**
     * Shows the error state with a specific, already-translated message.
     *
     * @param message the Hebrew message produced by util/ErrorMapper.
     * @param onRetry invoked when the retry button is tapped.
     */
    fun showError(message: String, onRetry: () -> Unit) {
        error.errorMessage.text = message
        error.errorRetry.setOnClickListener { onRetry() }
        show(error.root)
    }

    /** Makes [visibleView] the only visible one of the four. */
    private fun show(visibleView: View) {
        content.visibility = if (visibleView === content) View.VISIBLE else View.GONE
        loading.root.visibility = if (visibleView === loading.root) View.VISIBLE else View.GONE
        empty.root.visibility = if (visibleView === empty.root) View.VISIBLE else View.GONE
        error.root.visibility = if (visibleView === error.root) View.VISIBLE else View.GONE
    }
}
