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

package com.android.documentsui.util

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes

class ColorUtils {
    companion object {
        /**
         * Resolve a color attribute from the Material3 theme, example usage.
         * resolveMaterialColorAttribute(context, com.google.android.material.R.attr.XXX).
         */
        @JvmStatic
        fun resolveMaterialColorAttribute(context: Context, @AttrRes colorAttrId: Int): Int {
            val typedValue = TypedValue()
            context.theme.resolveAttribute(colorAttrId, typedValue, true)
            return typedValue.data
        }
    }
}
