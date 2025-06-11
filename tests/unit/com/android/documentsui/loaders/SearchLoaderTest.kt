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
package com.android.documentsui.loaders

import android.os.Bundle
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.provider.DocumentsContract
import androidx.test.filters.SmallTest
import com.android.documentsui.ContentLock
import com.android.documentsui.LockingContentObserver
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.flags.Flags.FLAG_USE_SEARCH_V2_READ_ONLY
import com.android.documentsui.testing.TestFileTypeLookup
import com.android.documentsui.testing.TestProvidersAccess
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import junit.framework.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

private const val TOTAL_FILE_COUNT = 8

fun createQueryArgs(vararg mimeTypes: String): Bundle {
    val args = Bundle()
    args.putStringArray(DocumentsContract.QUERY_ARG_MIME_TYPES, arrayOf<String>(*mimeTypes))
    return args
}

@RunWith(Parameterized::class)
@SmallTest
class SearchLoaderTest(private val testParams: LoaderTestParams) : BaseLoaderTest() {
    lateinit var mExecutor: ExecutorService
    val mContentLock = ContentLock()
    val mContentObserver = LockingContentObserver(mContentLock) {}

    companion object {
        @JvmStatic
        @Parameters(name = "with parameters {0}")
        fun data() = listOf(
            LoaderTestParams("sample", null, Bundle(), TOTAL_FILE_COUNT),
            LoaderTestParams("txt", null, Bundle(), 2),
            LoaderTestParams("foozig", null, Bundle(), 0),
            // The first file is at NOW, the second at NOW - 1h; expect 2.
            LoaderTestParams("sample", Duration.ofMinutes(60 + 1), Bundle(), 2),
            LoaderTestParams("sample", null, createQueryArgs("image/*"), 2),
            LoaderTestParams("sample", null, createQueryArgs("image/*", "video/*"), 6),
            LoaderTestParams("sample", null, createQueryArgs("application/pdf"), 0),
        )
    }

    @get:Rule
    val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Before
    override fun setUp() {
        super.setUp()
        mExecutor = Executors.newSingleThreadExecutor()
    }

    @Test
    @RequiresFlagsEnabled(FLAG_USE_SEARCH_V2_READ_ONLY)
    fun testLoadInBackground() {
        val mockProvider = mEnv.mockProviders[TestProvidersAccess.DOWNLOADS.authority]
        val docs = createDocuments(TOTAL_FILE_COUNT)
        mockProvider!!.setNextChildDocumentsReturns(*docs)
        val userIds = listOf(TestProvidersAccess.DOWNLOADS.userId)
        val queryOptions =
            QueryOptions(
                TOTAL_FILE_COUNT + 1,
                testParams.lastModifiedDelta,
                null,
                true,
                arrayOf("*/*"),
                testParams.otherArgs,
            )
        val rootIds = listOf(TestProvidersAccess.DOWNLOADS)

        // TODO(majewski): Is there a better way to create Downloads root folder DocumentInfo?
        val rootFolderInfo = DocumentInfo()
        rootFolderInfo.authority = TestProvidersAccess.DOWNLOADS.authority
        rootFolderInfo.userId = userIds[0]

        val loader =
            SearchLoader(
                mActivity,
                userIds,
                TestFileTypeLookup(),
                mContentObserver,
                rootIds,
                testParams.query,
                queryOptions,
                mEnv.state.sortModel,
                mExecutor,
            )
        val directoryResult = loader.loadInBackground()
        assertEquals(testParams.expectedCount, getFileCount(directoryResult))
    }

    @Test
    @RequiresFlagsEnabled(FLAG_USE_SEARCH_V2_READ_ONLY)
    @Ignore("b/397095797")
    fun testBlankQueryAndRecency() {
        val userIds = listOf(TestProvidersAccess.DOWNLOADS.userId)
        val rootIds = listOf(TestProvidersAccess.DOWNLOADS)
        val noLastModifiedQueryOptions =
            QueryOptions(10, null, null, true, arrayOf("*/*"), Bundle())

        // Blank query and no last modified duration is invalid.
        assertThrows(IllegalArgumentException::class.java) {
            SearchLoader(
                mActivity,
                userIds,
                TestFileTypeLookup(),
                mContentObserver,
                rootIds,
                "",
                noLastModifiedQueryOptions,
                mEnv.state.sortModel,
                mExecutor,
            )
        }

        // Null query and no last modified duration is invalid.
        assertThrows(IllegalArgumentException::class.java) {
            SearchLoader(
                mActivity,
                userIds,
                TestFileTypeLookup(),
                mContentObserver,
                rootIds,
                null,
                noLastModifiedQueryOptions,
                mEnv.state.sortModel,
                mExecutor,
            )
        }
    }
}
