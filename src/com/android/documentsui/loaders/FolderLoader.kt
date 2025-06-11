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
import android.provider.DocumentsContract
import com.android.documentsui.ContentLock
import com.android.documentsui.DirectoryResult
import com.android.documentsui.LockingContentObserver
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.base.FilteringCursorWrapper
import com.android.documentsui.base.Lookup
import com.android.documentsui.base.RootInfo
import com.android.documentsui.base.UserId
import com.android.documentsui.sorting.SortModel

/**
 * A specialization of the BaseFileLoader that loads the children of a single folder. To list
 * a directory you need to provide:
 *
 *  - The current application context
 *  - A content lock for which a locking content observer is built
 *  - A list of user IDs on behalf of which the search is conducted
 *  - The root info of the listed directory
 *  - The document info of the listed directory
 *  - a lookup from file extension to file type
 *  - The model capable of sorting results
 */
class FolderLoader(
    context: Context,
    userIdList: List<UserId>,
    mimeTypeLookup: Lookup<String, String>,
    contentLock: ContentLock,
    private val mRoot: RootInfo,
    private val mListedDir: DocumentInfo,
    private val mOptions: QueryOptions,
    private val mSortModel: SortModel,
) : BaseFileLoader(context, userIdList, mimeTypeLookup) {

    // An observer registered on the cursor to force a reload if the cursor reports a change.
    private val mObserver = LockingContentObserver(contentLock, this::onContentChanged)

    // Creates a directory result object corresponding to the current parameters of the loader.
    override fun loadInBackground(): DirectoryResult? {
        val rejectBeforeTimestamp = mOptions.getRejectBeforeTimestamp()
        val folderChildrenUri = DocumentsContract.buildChildDocumentsUri(
            mListedDir.authority,
            mListedDir.documentId
        )
        val cursor =
            queryLocation(mRoot.rootId, folderChildrenUri, mOptions.otherQueryArgs, ALL_RESULTS)
                ?: emptyCursor()
        cursor.registerContentObserver(mObserver)

        val filteredCursor = FilteringCursorWrapper(cursor)
        filteredCursor.filterHiddenFiles(mOptions.showHidden)
        filteredCursor.filterMimes(mOptions.acceptableMimeTypes, null)
        if (rejectBeforeTimestamp > 0L) {
            filteredCursor.filterLastModified(rejectBeforeTimestamp)
        }
        // TODO(b:380945065): Add filtering by category, such as images, audio, video.
        val sortedCursor = mSortModel.sortCursor(filteredCursor, mMimeTypeLookup)

        val result = DirectoryResult()
        result.doc = mListedDir
        result.cursor = sortedCursor
        return result
    }
}
