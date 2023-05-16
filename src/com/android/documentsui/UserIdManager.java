/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.documentsui;

import static androidx.core.util.Preconditions.checkNotNull;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.annotation.VisibleForTesting;

import com.android.documentsui.base.Features;
import com.android.documentsui.base.UserId;
import com.android.documentsui.util.VersionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Interface to query user ids.
 */
public interface UserIdManager {

    /**
     * Returns the {@UserId} of each profile which should be queried for documents. This will always
     * include {@link UserId#CURRENT_USER}.
     */
    List<UserId> getUserIds();

    /**
     * Creates an implementation of {@link UserIdManager}.
     */
    static UserIdManager create(Context context) {
        return new RuntimeUserIdManager(context);
    }

    /**
     * Implementation of {@link UserIdManager}.
     */
    final class RuntimeUserIdManager implements UserIdManager {

        private static final String TAG = "UserIdManager";

        private final Context mContext;
        private final UserId mCurrentUser;
        private final boolean mIsDeviceSupported;

        @GuardedBy("mUserIds")
        private final List<UserId> mUserIds = new ArrayList<>();

        private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                synchronized (mUserIds) {
                    mUserIds.clear();
                }
            }
        };

        private RuntimeUserIdManager(Context context) {
            this(context, UserId.CURRENT_USER,
                    Features.CROSS_PROFILE_TABS && isDeviceSupported(context));
        }

        @VisibleForTesting
        RuntimeUserIdManager(Context context, UserId currentUser, boolean isDeviceSupported) {
            mContext = context.getApplicationContext();
            mCurrentUser = checkNotNull(currentUser);
            mIsDeviceSupported = isDeviceSupported;


            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_MANAGED_PROFILE_ADDED);
            filter.addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED);
            mContext.registerReceiver(mIntentReceiver, filter);
        }

        @Override
        public List<UserId> getUserIds() {
            synchronized (mUserIds) {
                if (mUserIds.isEmpty()) {
                    mUserIds.addAll(getUserIdsInternal());
                }
            }
            return mUserIds;
        }

        private List<UserId> getUserIdsInternal() {
            final List<UserId> result = new ArrayList<>();
            result.add(mCurrentUser);

            // If the feature is disabled, return a list just containing the current user.
            if (!mIsDeviceSupported) {
                return result;
            }

            UserManager userManager = (UserManager) mContext.getSystemService(Context.USER_SERVICE);
            if (userManager == null) {
                Log.e(TAG, "cannot obtain user manager");
                return result;
            }

            final List<UserHandle> userProfiles = userManager.getUserProfiles();
            if (userProfiles.size() < 2) {
                return result;
            }

            if (mCurrentUser.isManagedProfile(userManager)) {
                result.add(0,
                        UserId.of(userManager.getProfileParent(mCurrentUser.getIdentifier()).id));
            } else if (userManager.getUserInfo(mCurrentUser.getIdentifier()).isFull()) {
                result.addAll(userProfiles.stream()
                        .filter(userHandle -> !mCurrentUser.equals(UserId.of(userHandle)))
                        .map(UserId::of).collect(Collectors.toList()));
            }

            return result;
        }

        private static boolean isDeviceSupported(Context context) {
            // The feature requires Android R DocumentsContract APIs and INTERACT_ACROSS_USERS
            // permission.
            return VersionUtils.isAtLeastR()
                    && context.checkSelfPermission(Manifest.permission.INTERACT_ACROSS_USERS)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }
}
