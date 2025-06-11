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
import android.widget.ActionMenuView
import android.widget.ProgressBar
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3
import com.android.documentsui.flags.Flags.FLAG_VISUAL_SIGNALS_RO
import com.android.documentsui.services.FileOperationService.ACTION_PROGRESS
import com.android.documentsui.services.FileOperationService.EXTRA_PROGRESS
import com.android.documentsui.services.Job
import com.android.documentsui.services.JobProgress
import com.android.documentsui.testing.MutableJobProgress
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RequiresFlagsEnabled(FLAG_USE_MATERIAL3, FLAG_VISUAL_SIGNALS_RO)
@RunWith(AndroidJUnit4::class)
class JobPanelControllerTest {
    @get:Rule
    val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private val mContext = InstrumentationRegistry.getInstrumentation().targetContext

    // The default progress bar only has an indeterminate state, so we need to style it to allow
    // determinate progress.
    private val mProgressBar = ProgressBar(
        mContext,
        null,
        android.R.attr.progressBarStyleHorizontal
    )
    private val mMenuItem = ActionMenuView(mContext).menu.add("job_panel").apply {
        actionView = mProgressBar
    }
    private lateinit var mController: JobPanelController
    private var mLastId = 0L

    private fun sendProgress(progress: ArrayList<JobProgress>, id: Long = mLastId++) {
        var intent = Intent(ACTION_PROGRESS).apply {
            `package` = mContext.packageName
            putExtra("id", id)
            putParcelableArrayListExtra(EXTRA_PROGRESS, progress)
        }
        mController.onReceive(mContext, intent)
    }

    @Before
    fun setUp() {
        mController = JobPanelController(mContext)
        mController.setMenuItem(mMenuItem)
    }

    @Test
    fun testSingleJob() {
        assertFalse(mMenuItem.isVisible())
        assertFalse(mMenuItem.isEnabled())

        val progress = MutableJobProgress(
            id = "jobId1",
            state = Job.STATE_STARTED,
            msg = "Job started",
            hasFailures = false,
            currentBytes = 0,
            requiredBytes = 10,
            msRemaining = -1
        )
        sendProgress(arrayListOf(progress.toJobProgress()))

        assertTrue(mMenuItem.isVisible())
        assertTrue(mMenuItem.isEnabled())
        assertEquals(0, mProgressBar.progress)

        progress.apply {
            state = Job.STATE_SET_UP
            msg = "Job in progress"
            currentBytes = 4
        }
        sendProgress(arrayListOf(progress.toJobProgress()))

        assertTrue(mMenuItem.isVisible())
        assertTrue(mMenuItem.isEnabled())
        assertEquals(40, mProgressBar.progress)

        progress.apply {
            state = Job.STATE_COMPLETED
            msg = "Job completed"
            currentBytes = 10
        }
        sendProgress(arrayListOf(progress.toJobProgress()))

        assertTrue(mMenuItem.isVisible())
        assertTrue(mMenuItem.isEnabled())
        assertEquals(100, mProgressBar.progress)
    }

    @Test
    fun testMultipleJobs() {
        assertFalse(mMenuItem.isVisible())
        assertFalse(mMenuItem.isEnabled())

        val progress1 = MutableJobProgress(
            id = "jobId1",
            state = Job.STATE_STARTED,
            msg = "Job started",
            hasFailures = false,
            currentBytes = 0,
            requiredBytes = 10,
            msRemaining = -1
        )
        val progress2 = MutableJobProgress(
            id = "jobId2",
            state = Job.STATE_STARTED,
            msg = "Job started",
            hasFailures = false,
            currentBytes = 0,
            requiredBytes = 40,
            msRemaining = -1
        )
        sendProgress(arrayListOf(progress1.toJobProgress(), progress2.toJobProgress()))

        assertTrue(mMenuItem.isVisible())
        assertTrue(mMenuItem.isEnabled())
        assertEquals(0, mProgressBar.progress)

        progress1.apply {
            state = Job.STATE_SET_UP
            msg = "Job in progress"
            currentBytes = 4
        }
        sendProgress(arrayListOf(progress1.toJobProgress(), progress2.toJobProgress()))

        assertTrue(mMenuItem.isVisible())
        assertTrue(mMenuItem.isEnabled())
        assertEquals(8, mProgressBar.progress)

        progress1.apply {
            state = Job.STATE_COMPLETED
            msg = "Job completed"
            currentBytes = 10
        }
        sendProgress(arrayListOf(progress1.toJobProgress(), progress2.toJobProgress()))

        assertTrue(mMenuItem.isVisible())
        assertTrue(mMenuItem.isEnabled())
        assertEquals(20, mProgressBar.progress)

        progress2.apply {
            state = Job.STATE_SET_UP
            msg = "Job in progress"
            currentBytes = 30
        }
        sendProgress(arrayListOf(progress1.toJobProgress(), progress2.toJobProgress()))

        assertTrue(mMenuItem.isVisible())
        assertTrue(mMenuItem.isEnabled())
        assertEquals(80, mProgressBar.progress)

        progress2.apply {
            state = Job.STATE_COMPLETED
            msg = "Job completed"
            currentBytes = 40
        }
        sendProgress(arrayListOf(progress1.toJobProgress(), progress2.toJobProgress()))

        assertTrue(mMenuItem.isVisible())
        assertTrue(mMenuItem.isEnabled())
        assertEquals(100, mProgressBar.progress)
    }
}
