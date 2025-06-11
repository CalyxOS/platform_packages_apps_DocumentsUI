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

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.text.TextUtils
import android.util.Log
import com.android.documentsui.DirectoryResult
import com.android.documentsui.LockingContentObserver
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.base.FilteringCursorWrapper
import com.android.documentsui.base.Lookup
import com.android.documentsui.base.RootInfo
import com.android.documentsui.base.UserId
import com.android.documentsui.sorting.SortModel
import com.google.common.util.concurrent.AbstractFuture
import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import kotlin.time.measureTime

/**
 * A specialization of the BaseFileLoader that searches the set of specified roots. To search
 * the roots you must provider:
 *
 *  - The current application context
 *  - A content lock for which a locking content observer is built
 *  - A list of user IDs, on whose behalf we query content provider clients.
 *  - A list of RootInfo objects representing searched roots
 *  - A query used to search for matching files.
 *  - Query options such as maximum number of results, last modified time delta, etc.
 *  - a lookup from file extension to file type
 *  - The model capable of sorting results
 *  - An executor for running searches across multiple roots in parallel
 *
 *  SearchLoader requires that either a query is not null and not empty or that QueryOptions
 *  specify a last modified time restriction. This is to prevent searching for every file
 *  across every specified root.
 */
class SearchLoader(
    context: Context,
    userIdList: List<UserId>,
    mimeTypeLookup: Lookup<String, String>,
    private val mObserver: LockingContentObserver,
    private val mRootList: Collection<RootInfo>,
    private val mQuery: String?,
    private val mOptions: QueryOptions,
    private val mSortModel: SortModel,
    private val mExecutorService: ExecutorService,
) : BaseFileLoader(context, userIdList, mimeTypeLookup) {

    /**
     * Helper class that runs query on a single user for the given parameter. This class implements
     * an abstract future so that if the task is completed, we can retrieve the cursor via the get
     * method.
     */
    inner class SearchTask(
        private val mRootId: String,
        private val mSearchUri: Uri,
        private val mQueryArgs: Bundle,
        private val mLatch: CountDownLatch,
    ) : Closeable, Runnable, AbstractFuture<Cursor>() {
        private var mCursor: Cursor? = null
        val cursor: Cursor? get() = mCursor
        val taskId: String get() = mSearchUri.toString()

        override fun close() {
            mCursor = null
        }

        override fun run() {
            val queryDuration = measureTime {
                try {
                    mCursor = queryLocation(mRootId, mSearchUri, mQueryArgs, mOptions.maxResults)
                    set(mCursor)
                } finally {
                    mLatch.countDown()
                }
            }
            Log.d(TAG, "Query on $mSearchUri took $queryDuration")
        }
    }

    @Volatile
    private var mSearchTaskList: List<SearchTask> = listOf()

    // Creates a directory result object corresponding to the current parameters of the loader.
    override fun loadInBackground(): DirectoryResult? {
        val result = DirectoryResult()
        // TODO(b:378590632): If root list has one root use it to construct result.doc
        result.doc = DocumentInfo()
        result.cursor = emptyCursor()

        val searchedRoots = mRootList
        val countDownLatch = CountDownLatch(searchedRoots.size)
        val rejectBeforeTimestamp = mOptions.getRejectBeforeTimestamp()

        // Step 1: Build a list of search tasks.
        val searchTaskList =
            createSearchTaskList(rejectBeforeTimestamp, countDownLatch, mRootList)
        Log.d(TAG, "${searchTaskList.size} tasks have been created")

        // Check if we are cancelled; if not copy the task list.
        if (isLoadInBackgroundCanceled) {
            return result
        }
        mSearchTaskList = searchTaskList

        // Step 2: Enqueue tasks and wait for them to complete or time out.
        for (task in mSearchTaskList) {
            mExecutorService.execute(task)
        }
        Log.d(TAG, "${mSearchTaskList.size} tasks have been enqueued")

        // Step 3: Wait for the results.
        try {
            if (mOptions.isQueryTimeUnlimited()) {
                Log.d(TAG, "Waiting for results with no time limit")
                countDownLatch.await()
            } else {
                Log.d(TAG, "Waiting ${mOptions.maxQueryTime!!.toMillis()}ms for results")
                countDownLatch.await(
                    mOptions.maxQueryTime.toMillis(),
                    TimeUnit.MILLISECONDS
                )
            }
            Log.d(TAG, "Waiting for results is done")
        } catch (e: InterruptedException) {
            Log.d(TAG, "Failed to complete all searches within ${mOptions.maxQueryTime}")
            // TODO(b:388336095): Record a metrics indicating incomplete search.
            throw RuntimeException(e)
        }

        // Step 4: Collect cursors from done tasks.
        val cursorList = mutableListOf<Cursor>()
        for (task in mSearchTaskList) {
            Log.d(TAG, "Processing task ${task.taskId}")
            if (isLoadInBackgroundCanceled) {
                break
            }
            // TODO(b:388336095): Record a metric for each done and not done task.
            val cursor = task.cursor
            if (task.isDone && cursor != null) {
                // TODO(b:388336095): Record a metric for null and not null cursor.
                Log.d(TAG, "Task ${task.taskId} has ${cursor.count} results")
                cursorList.add(cursor)
            }
        }
        Log.d(TAG, "Search complete with ${cursorList.size} cursors collected")

        // Step 5: Assign the cursor, after adding filtering and sorting, to the results.
        val mergedCursor = toSingleCursor(cursorList)
        mergedCursor.registerContentObserver(mObserver)
        val filteringCursor = FilteringCursorWrapper(mergedCursor)
        filteringCursor.filterHiddenFiles(mOptions.showHidden)
        filteringCursor.filterMimes(
            mOptions.acceptableMimeTypes,
            if (TextUtils.isEmpty(mQuery)) arrayOf<String>(Document.MIME_TYPE_DIR) else null
        )
        if (rejectBeforeTimestamp > 0L) {
            filteringCursor.filterLastModified(rejectBeforeTimestamp)
        }
        result.cursor = mSortModel.sortCursor(filteringCursor, mMimeTypeLookup)

        // TODO(b:388336095): Record the total time it took to complete search.
        return result
    }

    private fun createContentProviderQuery(root: RootInfo) =
        if (TextUtils.isEmpty(mQuery) && mOptions.otherQueryArgs.isEmpty) {
            // NOTE: recent document URI does not respect query-arg-mime-types restrictions. Thus
            // we only create the recents URI if both the query and other args are empty.
            DocumentsContract.buildRecentDocumentsUri(
                root.authority,
                root.rootId
            )
        } else {
            // NOTE: We pass empty query, as the name matching query is placed in queryArgs.
            DocumentsContract.buildSearchDocumentsUri(
                root.authority,
                root.rootId,
                ""
            )
        }

    private fun createQueryArgs(rejectBeforeTimestamp: Long): Bundle {
        val queryArgs = Bundle()
        mSortModel.addQuerySortArgs(queryArgs)
        if (rejectBeforeTimestamp > 0L) {
            queryArgs.putLong(
                DocumentsContract.QUERY_ARG_LAST_MODIFIED_AFTER,
                rejectBeforeTimestamp
            )
        }
        if (!TextUtils.isEmpty(mQuery)) {
            queryArgs.putString(DocumentsContract.QUERY_ARG_DISPLAY_NAME, mQuery)
        }
        queryArgs.putAll(mOptions.otherQueryArgs)
        return queryArgs
    }

    /**
     * Helper function that creates a list of search tasks for the given countdown latch.
     */
    private fun createSearchTaskList(
        rejectBeforeTimestamp: Long,
        countDownLatch: CountDownLatch,
        rootList: Collection<RootInfo>
    ): List<SearchTask> {
        val searchTaskList = mutableListOf<SearchTask>()
        for (root in rootList) {
            if (isLoadInBackgroundCanceled) {
                break
            }
            val rootSearchUri = createContentProviderQuery(root)
            // TODO(b:385789236): Correctly pass sort order information.
            val queryArgs = createQueryArgs(rejectBeforeTimestamp)
            mSortModel.addQuerySortArgs(queryArgs)
            Log.d(TAG, "Query $rootSearchUri and queryArgs $queryArgs")
            val task = SearchTask(
                root.rootId,
                rootSearchUri,
                queryArgs,
                countDownLatch
            )
            searchTaskList.add(task)
        }
        return searchTaskList
    }

    override fun onReset() {
        for (task in mSearchTaskList) {
            task.close()
        }
        Log.d(TAG, "Resetting search loader; search task list emptied.")
        super.onReset()
    }
}
