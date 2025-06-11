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
package com.android.documentsui

import android.content.Intent
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.documentsui.files.FilesActivity
import com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3
import com.android.documentsui.flags.Flags.FLAG_VISUAL_SIGNALS_RO
import com.android.documentsui.services.FileOperationService.ACTION_PROGRESS
import com.android.documentsui.services.FileOperationService.EXTRA_PROGRESS
import com.android.documentsui.services.Job
import com.android.documentsui.services.JobProgress
import com.android.documentsui.testing.MutableJobProgress
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RequiresFlagsEnabled(FLAG_USE_MATERIAL3, FLAG_VISUAL_SIGNALS_RO)
@RunWith(AndroidJUnit4::class)
class JobPanelUiTest : ActivityTestJunit4<FilesActivity>() {
    @get:Rule
    val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private var mLastId = 0L

    private fun sendProgress(progresses: ArrayList<JobProgress>, id: Long = mLastId++) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var intent = Intent(ACTION_PROGRESS).apply {
            `package` = context.packageName
            putExtra("id", id)
            putParcelableArrayListExtra(EXTRA_PROGRESS, progresses)
        }
        context.sendBroadcast(intent)
    }

    @Before
    override fun setUp() {
        super.setUp()
    }

    @After
    override fun tearDown() {
        super.tearDown()
    }

    @Test
    fun testJobPanelAppearsOnClick() {
        onView(withId(R.id.option_menu_job_progress)).check(doesNotExist())
        onView(withId(R.id.job_progress_panel_title)).check(doesNotExist())

        val progress = MutableJobProgress(
            id = "jobId1",
            state = Job.STATE_SET_UP,
            msg = "Job started",
            hasFailures = false,
            currentBytes = 4,
            requiredBytes = 10,
            msRemaining = -1
        )
        sendProgress(arrayListOf(progress.toJobProgress()))

        onView(withId(R.id.option_menu_job_progress))
            .check(matches(isDisplayed()))
            .perform(click())
        onView(withId(R.id.job_progress_panel_title)).check(matches(isDisplayed()))
    }
}
