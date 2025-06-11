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

import android.os.RemoteException
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.android.documentsui.ActivityTestJunit4
import com.android.documentsui.files.FilesActivity
import com.android.documentsui.flags.Flags
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(Flags.FLAG_USE_MATERIAL3, Flags.FLAG_USE_PEEK_PREVIEW_RO)
class PeekUiTest : ActivityTestJunit4<FilesActivity?>() {
    @get:Rule
    val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Before
    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
        initTestFiles()
    }

    @After
    @Throws(Exception::class)
    override fun tearDown() {
        super.tearDown()
    }

    @Throws(RemoteException::class)
    override fun initTestFiles() {
        mDocsHelper!!.createDocument(rootDir0, "image/png", "image.png")
    }

    @Test
    @Throws(
        Exception::class
    )
    fun testShowPeek() {
        bots!!.peek.assertPeekHidden()
        bots!!.directory.selectDocument("image.png")
        bots!!.main.clickActionItem("Get info")
        bots!!.peek.assertPeekActive()
    }
}
