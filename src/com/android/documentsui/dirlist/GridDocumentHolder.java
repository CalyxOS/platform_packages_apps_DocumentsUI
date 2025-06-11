/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.documentsui.dirlist;

import static com.android.documentsui.DevicePolicyResources.Drawables.Style.SOLID_COLORED;
import static com.android.documentsui.DevicePolicyResources.Drawables.WORK_PROFILE_ICON;
import static com.android.documentsui.base.DocumentInfo.getCursorInt;
import static com.android.documentsui.base.DocumentInfo.getCursorLong;
import static com.android.documentsui.base.DocumentInfo.getCursorString;
import static com.android.documentsui.util.FlagUtils.isUseMaterial3FlagEnabled;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.provider.DocumentsContract.Document;
import android.text.format.Formatter;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.android.documentsui.ConfigStore;
import com.android.documentsui.DocumentsApplication;
import com.android.documentsui.IconUtils;
import com.android.documentsui.R;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.Shared;
import com.android.documentsui.base.State;
import com.android.documentsui.base.UserId;
import com.android.documentsui.roots.RootCursorWrapper;
import com.android.documentsui.ui.Views;
import com.android.modules.utils.build.SdkLevel;

import com.google.android.material.card.MaterialCardView;

import java.util.Map;
import java.util.function.Function;

final class GridDocumentHolder extends DocumentHolder {

    final TextView mTitle;
    final TextView mDate;
    final TextView mDetails;
    // Non-null only when useMaterial3 flag is ON.
    final @Nullable TextView mBullet;
    final ImageView mIconMimeLg;
    // Null when useMaterial3 flag is ON.
    final @Nullable ImageView mIconMimeSm;
    final ImageView mIconThumb;
    // Null when useMaterial3 flag is ON.
    final @Nullable ImageView mIconCheck;
    final ImageView mIconBadge;
    final IconHelper mIconHelper;
    // Null when useMaterial3 flag is ON.
    final @Nullable View mIconLayout;
    final View mPreviewIcon;

    // This is used in as a convenience in our bind method.
    private final DocumentInfo mDoc = new DocumentInfo();

    // Non-null only when useMaterial3 flag is ON.
    private final @Nullable MaterialCardView mIconWrapper;
    // It will be 0 when use_material flag is OFF.
    private final int mThumbnailStrokeWidth;

    GridDocumentHolder(Context context, ViewGroup parent, IconHelper iconHelper,
            ConfigStore configStore) {
        super(context, parent, R.layout.item_doc_grid, configStore);

        if (isUseMaterial3FlagEnabled()) {
            mBullet = itemView.findViewById(R.id.bullet);
            mIconWrapper = itemView.findViewById(R.id.icon_wrapper);
            mIconLayout = null;
            mIconMimeSm = null;
            mIconCheck = null;
            mThumbnailStrokeWidth =
                    context.getResources()
                            .getDimensionPixelSize(R.dimen.thumbnail_border_width);
        } else {
            mBullet = null;
            mIconWrapper = null;
            mIconLayout = itemView.findViewById(R.id.icon);
            mIconMimeSm = (ImageView) itemView.findViewById(R.id.icon_mime_sm);
            mIconCheck = (ImageView) itemView.findViewById(R.id.icon_check);
            mThumbnailStrokeWidth = 0;
        }

        mTitle = (TextView) itemView.findViewById(android.R.id.title);
        mDate = (TextView) itemView.findViewById(R.id.date);
        mDetails = (TextView) itemView.findViewById(R.id.details);
        mIconMimeLg = (ImageView) itemView.findViewById(R.id.icon_mime_lg);
        mIconThumb = (ImageView) itemView.findViewById(R.id.icon_thumb);
        mIconBadge = (ImageView) itemView.findViewById(R.id.icon_profile_badge);
        mPreviewIcon = itemView.findViewById(R.id.preview_icon);

        if (isUseMaterial3FlagEnabled()) {
            int clipCornerRadius = context.getResources()
                    .getDimensionPixelSize(R.dimen.thumbnail_clip_corner_radius);
            IconUtils.applyThumbnailClipOutline(
                    mIconThumb, mThumbnailStrokeWidth, clipCornerRadius);
        }

        mIconHelper = iconHelper;

        if (SdkLevel.isAtLeastT() && !mConfigStore.isPrivateSpaceInDocsUIEnabled()) {
            setUpdatableWorkProfileIcon(context);
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private void setUpdatableWorkProfileIcon(Context context) {
        DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);
        Drawable drawable = dpm.getResources().getDrawable(WORK_PROFILE_ICON, SOLID_COLORED, () ->
                context.getDrawable(R.drawable.ic_briefcase));
        mIconBadge.setImageDrawable(drawable);
    }

    @Override
    public void setSelected(boolean selected, boolean animate) {
        float checkAlpha = selected ? 1f : 0f;
        if (!isUseMaterial3FlagEnabled()) {
            // We always want to make sure our check box disappears if we're not selected,
            // even if the item is disabled. This is because this object can be reused
            // and this method will be called to setup initial state.
            if (animate) {
                fade(mIconMimeSm, checkAlpha).start();
                fade(mIconCheck, checkAlpha).start();
            } else {
                mIconCheck.setAlpha(checkAlpha);
            }
        }


        // But it should be an error to be set to selected && be disabled.
        if (!itemView.isEnabled()) {
            assert (!selected);
        }

        super.setSelected(selected, animate);

        if (!isUseMaterial3FlagEnabled()) {
            if (animate) {
                fade(mIconMimeSm, 1f - checkAlpha).start();
            } else {
                mIconMimeSm.setAlpha(1f - checkAlpha);
            }
        }

        // Do not show stroke when selected, only show stroke when not selected if it has thumbnail.
        if (mIconWrapper != null) {
            if (selected) {
                mIconWrapper.setStrokeWidth(0);
            } else if (mIconThumb.getDrawable() != null) {
                mIconWrapper.setStrokeWidth(mThumbnailStrokeWidth);
            }
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);

        float imgAlpha = enabled ? 1f : DISABLED_ALPHA;

        mIconMimeLg.setAlpha(imgAlpha);
        if (!isUseMaterial3FlagEnabled()) {
            mIconMimeSm.setAlpha(imgAlpha);
        }
        mIconThumb.setAlpha(imgAlpha);
    }

    @Override
    public void bindPreviewIcon(boolean show, Function<View, Boolean> clickCallback) {
        if (isUseMaterial3FlagEnabled() && mDoc.isDirectory()) {
            mPreviewIcon.setVisibility(View.GONE);
            return;
        }
        mPreviewIcon.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            mPreviewIcon.setContentDescription(
                    getPreviewIconContentDescription(
                            mIconHelper.shouldShowBadge(mDoc.userId.getIdentifier()),
                            mDoc.displayName, mDoc.userId));
            mPreviewIcon.setAccessibilityDelegate(
                    new PreviewAccessibilityDelegate(clickCallback));
        }
    }

    @Override
    public void bindBriefcaseIcon(boolean show) {
        mIconBadge.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    @RequiresApi(Build.VERSION_CODES.S)
    public void bindProfileIcon(boolean show, int userIdIdentifier) {
        Map<UserId, Drawable> userIdToBadgeMap = DocumentsApplication.getUserManagerState(
                mContext).getUserIdToBadgeMap();
        Drawable drawable = userIdToBadgeMap.get(UserId.of(userIdIdentifier));
        mIconBadge.setImageDrawable(drawable);
        mIconBadge.setVisibility(show ? View.VISIBLE : View.GONE);
        mIconBadge.setContentDescription(mIconHelper.getProfileLabel(userIdIdentifier));
    }

    @Override
    public boolean inDragRegion(MotionEvent event) {
        // Entire grid box should be draggable
        return true;
    }

    @Override
    public boolean inSelectRegion(MotionEvent event) {
        if (isUseMaterial3FlagEnabled()) {
            return (mDoc.isDirectory() && !(mAction == State.ACTION_BROWSE)) ? false
                    : Views.isEventOver(event, itemView.getParent(), mIconWrapper);
        }
        return Views.isEventOver(event, itemView.getParent(), mIconLayout);
    }

    @Override
    public boolean inPreviewIconRegion(MotionEvent event) {
        return Views.isEventOver(event, itemView.getParent(), mPreviewIcon);
    }

    /**
     * Bind this view to the given document for display.
     *
     * @param cursor  Pointing to the item to be bound.
     * @param modelId The model ID of the item.
     */
    @Override
    public void bind(Cursor cursor, String modelId) {
        assert (cursor != null);

        mModelId = modelId;

        mDoc.updateFromCursor(cursor,
                UserId.of(getCursorInt(cursor, RootCursorWrapper.COLUMN_USER_ID)),
                getCursorString(cursor, RootCursorWrapper.COLUMN_AUTHORITY));

        mIconHelper.stopLoading(mIconThumb);

        mIconMimeLg.animate().cancel();
        mIconMimeLg.setAlpha(1f);
        mIconThumb.animate().cancel();
        mIconThumb.setAlpha(0f);

        if (isUseMaterial3FlagEnabled()) {
            mIconHelper.load(
                    mDoc, mIconThumb, mIconMimeLg, /* subIconMime= */ null,
                    thumbnailLoaded -> {
                        // Show stroke when thumbnail is loaded.
                        if (mIconWrapper != null) {
                            mIconWrapper.setStrokeWidth(
                                    thumbnailLoaded ? mThumbnailStrokeWidth : 0);
                        }
                    });
        } else {
            mIconHelper.load(
                    mDoc, mIconThumb, mIconMimeLg, mIconMimeSm, /* thumbnailLoadedCallback= */
                    null);
        }

        mTitle.setText(mDoc.displayName, TextView.BufferType.SPANNABLE);
        mTitle.setVisibility(View.VISIBLE);

        // If file is partial, we want to show summary field as that's more relevant than fileSize
        // and date
        if (mDoc.isPartial()) {
            final String docSummary = getCursorString(cursor, Document.COLUMN_SUMMARY);
            mDetails.setVisibility(View.VISIBLE);
            mDate.setText(null);
            mDetails.setText(docSummary);
        } else {
            if (mDoc.lastModified == -1) {
                mDate.setText(null);
            } else {
                mDate.setText(Shared.formatTime(mContext, mDoc.lastModified));
            }

            final long docSize = getCursorLong(cursor, Document.COLUMN_SIZE);
            if (mDoc.isDirectory() || docSize == -1) {
                mDetails.setVisibility(View.GONE);
            } else {
                mDetails.setVisibility(View.VISIBLE);
                mDetails.setText(Formatter.formatFileSize(mContext, docSize));
            }
        }

        if (mBullet != null && (mDetails.getText() == null || mDetails.getText().length() == 0
                || mDate.getText() == null || mDate.getText().length() == 0)) {
            // There is no need for the bullet separating the details and date.
            mBullet.setVisibility(View.GONE);
        }
    }
}
