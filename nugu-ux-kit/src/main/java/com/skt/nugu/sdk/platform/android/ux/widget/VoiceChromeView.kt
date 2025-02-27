/**
 * Copyright (c) 2019 SK Telecom Co., Ltd. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http:www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.skt.nugu.sdk.platform.android.ux.widget

import android.animation.Animator
import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import com.skt.nugu.sdk.platform.android.ux.R
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * A Voice Chrome View is a animated view designed by Nugu Design guide.
 * See https://github.com/airbnb/lottie-android
 */
class VoiceChromeView : FrameLayout {
    /**
     * Provides animation information
     * @param resId is lottie resource
     * @param count is loop count of animation
     */
    inner class AnimationInfo(val resId: Int, val count: Int = 0)
    /**
     * Companion objects
     */
    companion object {
        private const val TAG = "VoiceChromeView"
    }

    private val queue = ConcurrentLinkedQueue<AnimationInfo>()

    private val lottieView: LottieAnimationView

    /**
     * VoiceChromeView constructor
     */
    constructor(context: Context) : this(context, null)

    /**
     * VoiceChromeView constructor
     */
    constructor(context: Context, attributeSet: AttributeSet?) : super(context, attributeSet) {
        lottieView = LottieAnimationView(context, attributeSet).also {
            it.enableMergePathsForKitKatAndAbove(true)
            it.addAnimatorListener(animationListener)
            addView(it, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            setAnimation(R.raw.lp, LottieDrawable.INFINITE)
        }
    }

    private val animationListener = object : Animator.AnimatorListener {
        override fun onAnimationStart(animation: Animator?) {
        }

        override fun onAnimationEnd(animation: Animator?) {
            nextAnimation()
        }

        override fun onAnimationCancel(animation: Animator?) {
        }

        override fun onAnimationRepeat(animation: Animator?) {
            if (queue.size > 0) {
                animation?.cancel()
                nextAnimation()
            }
        }
    }

    /**
     * Start animation sequentially
     * @param resId is lottie resource id
     * @param count is Repeat count
     */
    private fun addAnimation(resId: Int, count: Int = LottieDrawable.INFINITE) {
        this.post {
            queue.add(AnimationInfo(resId, count))
            nextAnimation()
        }
    }

    /**
     * Clear animation
     * resid, 0 means draw transparent screen
     */
    private fun resetAnimation() {
        this.post {
            this.addAnimation(0,LottieDrawable.INFINITE)
        }
    }

    /**
     * Start animation immediately
     * start Animation after clear animation
     * @param resId is lottie resource id
     * @param count is Repeat count
     */
    private fun setAnimation(resId: Int, count: Int = LottieDrawable.INFINITE) {
        this.post {
            lottieView.cancelAnimation()
            queue.clear()
            this.addAnimation(resId, count)
        }
    }

    /**
     * Next Animation
     */
    private fun nextAnimation() {
        if (lottieView.isAnimating) {
            return
        }
        queue.poll()?.apply {
            if(resId == 0) {
                // for clear
                lottieView.setImageDrawable(null)
            }
            else {
                lottieView.setAnimation(resId)
                lottieView.repeatCount = count
                lottieView.playAnimation()
            }
        }
    }

    /**
     * Start animation
     * @param animation @see [Animation]
     */
    fun startAnimation(animation: Animation) {
        when (animation) {
            Animation.WAITING -> setAnimation(R.raw.lp, LottieDrawable.INFINITE)
            Animation.LISTENING -> setAnimation(R.raw.la, LottieDrawable.INFINITE)
            Animation.SPEAKING -> {
                addAnimation(R.raw.sp_01_start, 0)
                addAnimation(R.raw.sp_02)
            }
            Animation.THINKING -> {
                addAnimation(R.raw.pc_01_start, 0)
                addAnimation(R.raw.pc_02, LottieDrawable.INFINITE)
            }
        }
    }

    /**
     * Stop animation
     */
    fun stopAnimation() {
        resetAnimation()
    }

    /**
     * The status of a voice animation
     */
    enum class Animation {
        /**
         * waiting animation
         **/
        WAITING,
        /**
         * listening animation
         **/
        LISTENING,
        /**
         * speaking animation
         **/
        SPEAKING,
        /**
         * thinking animation
         **/
        THINKING
    }
}