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
package com.android.documentsui.bots

import android.content.Context
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue

/**
 * A test helper class that provides support for controlling the peek overlay
 * and making assertions against the state of it.
 */
class PeekBot(
    device: UiDevice,
    context: Context,
    timeout: Int
) : Bots.BaseBot(device, context, timeout) {

    private val mOverlayId: String = "$mTargetPackage:id/peek_overlay"
    private val mContainerId: String = "$mTargetPackage:id/peek_container"

    fun assertPeekActive() {
        val peekContainer = findPeekContainer()
        assertTrue(peekContainer.exists())
    }

    fun assertPeekHidden() {
        val peekContainer = findPeekContainer()
        assertFalse(peekContainer.exists())
    }

    fun findPeekContainer(): UiObject {
        return findObject(mOverlayId, mContainerId)
    }
}
