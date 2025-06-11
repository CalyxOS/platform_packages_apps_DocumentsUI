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

package com.android.documentsui.sidebar;


import android.view.View;

import com.android.documentsui.ActionHandler;
import com.android.documentsui.R;
import com.android.documentsui.base.RootInfo;

/**
 * Similar to {@link RootItem} but only used in the navigation rail.
 */
public class NavRailRootItem extends RootItem {

    public NavRailRootItem(RootInfo root, ActionHandler actionHandler, boolean maybeShowBadge) {
        super(
                R.layout.nav_rail_item_root,
                root,
                actionHandler,
                "" /* packageName */,
                maybeShowBadge);
    }

    public NavRailRootItem(
            RootInfo root,
            ActionHandler actionHandler,
            String packageName,
            boolean maybeShowBadge) {
        super(R.layout.nav_rail_item_root, root, actionHandler, packageName, maybeShowBadge);
    }

    @Override
    public void bindView(View convertView) {
        bindIconAndTitle(convertView);
    }
}
