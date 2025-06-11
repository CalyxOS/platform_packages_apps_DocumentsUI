/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.app.Activity
import android.app.UiAutomation
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.RemoteException
import android.provider.DocumentsContract
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import com.android.documentsui.base.Features
import com.android.documentsui.base.Features.RuntimeFeatures
import com.android.documentsui.base.RootInfo
import com.android.documentsui.base.UserId
import com.android.documentsui.bots.Bots
import com.android.documentsui.files.FilesActivity
import java.io.IOException
import java.util.Objects

/**
 * Provides basic test environment for UI tests:
 * - Launches activity
 * - Creates and gives access to test root directories and test files
 * - Cleans up the test environment
 */
abstract class ActivityTestJunit4<T : Activity?> {
    @JvmField
    var bots: Bots? = null

    @JvmField
    var device: UiDevice? = null

    @JvmField
    var context: Context? = null
    var userId: UserId? = null
    var automation: UiAutomation? = null

    @JvmField
    var features: Features? = null

    /**
     * Returns the root that will be opened within the activity.
     * By default tests are started with one of the test roots.
     * Override the method if you want to open different root on start.
     * @return Root that will be opened. Return null if you want to open activity's default root.
     */
    protected open var initialRoot: RootInfo? = null

    @JvmField
    var rootDir0: RootInfo? = null

    @JvmField
    var rootDir1: RootInfo? = null
    protected var mResolver: ContentResolver? = null

    @JvmField
    protected var mDocsHelper: DocumentsProviderHelper? = null
    protected var mActivityScenario: ActivityScenario<T?>? = null
    private var initialScreenOffTimeoutValue: String? = null
    private var initialSleepTimeoutValue: String? = null

    protected val testingProviderAuthority: String
        /**
         * Returns the authority of the testing provider begin used.
         * By default it's StubProvider's authority.
         * @return Authority of the provider.
         */
        get() = StubProvider.DEFAULT_AUTHORITY

    /**
     * Resolves testing roots.
     */
    @Throws(RemoteException::class)
    protected fun setupTestingRoots() {
        rootDir0 = mDocsHelper!!.getRoot(StubProvider.ROOT_0_ID)
        rootDir1 = mDocsHelper!!.getRoot(StubProvider.ROOT_1_ID)
        this.initialRoot = rootDir0
    }

    @Throws(Exception::class)
    open fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        // NOTE: Must be the "target" context, else security checks in content provider will fail.
        context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        userId = UserId.DEFAULT_USER
        automation = InstrumentationRegistry.getInstrumentation().getUiAutomation()
        features = RuntimeFeatures(context!!.getResources(), null)

        bots = Bots(device, automation, context, TIMEOUT)

        Configurator.getInstance().setToolType(MotionEvent.TOOL_TYPE_MOUSE)

        mResolver = context!!.getContentResolver()
        mDocsHelper = DocumentsProviderHelper(
            userId, this.testingProviderAuthority, context,
            this.testingProviderAuthority
        )

        device!!.setOrientationNatural()
        device!!.pressKeyCode(KeyEvent.KEYCODE_WAKEUP)

        disableScreenOffAndSleepTimeouts()

        setupTestingRoots()

        launchActivity()
        resetStorage()

        // Since at the launch of activity, ROOT_0 and ROOT_1 have no files, drawer will
        // automatically open for phone devices. Espresso register click() as (x, y) MotionEvents,
        // so if a drawer is on top of a file we want to select, it will actually click the drawer.
        // Thus to start a clean state, we always try to close first.
        bots!!.roots!!.closeDrawer()

        // Configure the provider back to default.
        mDocsHelper!!.configure(null, Bundle.EMPTY)
    }

    @Throws(Exception::class)
    open fun tearDown() {
        device!!.unfreezeRotation()
        mDocsHelper!!.cleanUp()
        restoreScreenOffAndSleepTimeouts()
        mActivityScenario!!.close()
    }

    protected fun launchActivity() {
        val intent = Intent(context, FilesActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        if (this.initialRoot != null) {
            intent.setAction(Intent.ACTION_VIEW)
            intent.setDataAndType(
                this.initialRoot!!.uri,
                DocumentsContract.Root.MIME_TYPE_ITEM
            )
        }
        mActivityScenario = ActivityScenario.launch(intent)
    }

    @Throws(RemoteException::class)
    protected fun resetStorage() {
        mDocsHelper!!.clear(null, null)
        device!!.waitForIdle()
    }

    @Throws(RemoteException::class)
    protected open fun initTestFiles() {
        mDocsHelper!!.createFolder(this.initialRoot, dirName1)
        mDocsHelper!!.createDocument(this.initialRoot, "text/plain", fileName1)
        mDocsHelper!!.createDocument(this.initialRoot, "image/png", fileName2)
        mDocsHelper!!.createDocumentWithFlags(
            initialRoot!!.documentId,
            "text/plain",
            fileNameNoRename,
            DocumentsContract.Document.FLAG_SUPPORTS_WRITE
        )

        mDocsHelper!!.createDocument(rootDir1, "text/plain", fileName3)
        mDocsHelper!!.createDocument(rootDir1, "text/plain", fileName4)
    }

    @Throws(IOException::class)
    private fun disableScreenOffAndSleepTimeouts() {
        initialScreenOffTimeoutValue = device!!.executeShellCommand(
            "settings get system screen_off_timeout"
        )
        initialSleepTimeoutValue = device!!.executeShellCommand(
            "settings get secure sleep_timeout"
        )
        device!!.executeShellCommand("settings put system screen_off_timeout -1")
        device!!.executeShellCommand("settings put secure sleep_timeout -1")
    }

    @Throws(IOException::class)
    private fun restoreScreenOffAndSleepTimeouts() {
        Objects.requireNonNull<String?>(initialScreenOffTimeoutValue)
        Objects.requireNonNull<String?>(initialSleepTimeoutValue)
        try {
            device!!.executeShellCommand(
                "settings put system screen_off_timeout $initialScreenOffTimeoutValue"
            )
            device!!.executeShellCommand(
                "settings put secure sleep_timeout $initialSleepTimeoutValue"
            )
        } finally {
            initialScreenOffTimeoutValue = null
            initialSleepTimeoutValue = null
        }
    }

    companion object {
        // Testing files. For custom ones, override initTestFiles().
        const val dirName1 = "Dir1"
        const val childDir1 = "ChildDir1"
        const val fileName1 = "file1.log"
        const val fileName2 = "file12.png"
        const val fileName3 = "anotherFile0.log"
        const val fileName4 = "poodles.text"
        const val fileNameNoRename = "NO_RENAMEfile.txt"
        const val TIMEOUT = 5000
    }
}
