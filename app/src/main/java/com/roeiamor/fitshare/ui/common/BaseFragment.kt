package com.roeiamor.fitshare.ui.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CallSuper
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding

/**
 * Base class for every Fragment in the app. It exists for one reason: to own the ViewBinding
 * lifecycle correctly, in one place, instead of repeating the same eight lines nine times.
 *
 * A Fragment's view can be destroyed while the Fragment itself lives on - for example when it sits
 * on the back stack. Holding the binding past [onDestroyView] leaks the whole view tree, so the
 * reference is nulled there and every access goes through [binding], which fails loudly rather
 * than returning null.
 *
 * A subclass supplies its own binding type and inflates it:
 * ```
 * class FeedFragment : BaseFragment<FragmentFeedBinding>() {
 *     override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
 *         FragmentFeedBinding.inflate(inflater, container, false)
 * }
 * ```
 *
 * @param VB the generated ViewBinding class for this Fragment's layout.
 */
abstract class BaseFragment<VB : ViewBinding> : Fragment() {

    private var _binding: VB? = null

    /**
     * The current view binding.
     *
     * Only valid between [onCreateView] and [onDestroyView]. Touching it outside that window is a
     * bug - typically a coroutine or a listener that outlived the view - so this throws a named
     * error instead of a bare NullPointerException.
     */
    protected val binding: VB
        get() = checkNotNull(_binding) {
            "${this::class.simpleName}: binding accessed outside the view lifecycle. " +
                "Use viewLifecycleOwner for anything that observes or waits."
        }

    /** Inflates the concrete binding for this screen. Implemented by every subclass. */
    protected abstract fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): VB

    final override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = inflateBinding(inflater, container)
        return binding.root
    }

    /**
     * Applies the app-wide keyboard behaviour to whatever fields this screen happens to have.
     *
     * Done here rather than per screen so a form cannot be built that forgets it - a new screen gets
     * a "done" affordance on its multi-line fields and scroll-into-view on focus simply by existing.
     * Called before the subclass's own `onViewCreated` work, which is free to override any of it.
     */
    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        FormKeyboardSupport.apply(view)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
