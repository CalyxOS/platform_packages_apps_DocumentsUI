/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.documentsui.peek

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.IdRes
import androidx.fragment.app.FragmentManager
import com.android.documentsui.R
import androidx.fragment.app.FragmentTransaction
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.util.FlagUtils.Companion.isUsePeekPreviewFlagEnabled

/**
 * Manager that controls the Peek UI.
 */
open class PeekViewManager(
    private val mActivity: Activity
) {
    companion object {
        const val TAG = "PeekViewManager"
    }

    private var mPeekFragment: PeekFragment? = null

    open fun initFragment(
        fm: FragmentManager
    ) {
        if (!isUsePeekPreviewFlagEnabled()) {
            Log.e(TAG, "Attempting to create PeekViewManager while Peek disabled")
            return
        }

        if (getOverlayContainer() == null) {
            Log.e(TAG, "Unable to find Peek container")
            return
        }

        // Load the Peek fragment into its container.
        val peekFragment = PeekFragment()
        mPeekFragment = peekFragment
        val ft: FragmentTransaction = fm.beginTransaction()
        ft.replace(getOverlayId(), peekFragment)
        ft.commitAllowingStateLoss()
    }

    open fun peekDocument(doc: DocumentInfo) {
        if (mPeekFragment == null) {
            Log.e(TAG, "Peek fragment not initialized")
            return
        }
        show()
    }

    @IdRes
    private fun getOverlayId(): Int {
        return R.id.peek_overlay
    }

    private fun getOverlayContainer(): FrameLayout? {
        return mActivity.findViewById(getOverlayId())
    }

    private fun show() {
        getOverlayContainer()?.visibility = View.VISIBLE
    }
}