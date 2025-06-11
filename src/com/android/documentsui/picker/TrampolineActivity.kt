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
package com.android.documentsui.picker

import android.content.ComponentName
import android.content.Intent
import android.content.Intent.ACTION_GET_CONTENT
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.ext.SdkExtensions
import android.provider.MediaStore.ACTION_PICK_IMAGES
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.android.documentsui.base.SharedMinimal.DEBUG

/**
 * DocumentsUI PickActivity currently defers picking of media mime types to the Photopicker. This
 * activity trampolines the intent to either Photopicker or to the PickActivity depending on whether
 * there are non-media mime types to handle.
 */
class TrampolineActivity : AppCompatActivity() {
    companion object {
        const val TAG = "TrampolineActivity"
    }

    override fun onCreate(savedInstanceBundle: Bundle?) {
        super.onCreate(savedInstanceBundle)

        // This activity should not be present in the back stack nor should handle any of the
        // corresponding results when picking items.
        intent?.apply {
            addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT)
            addFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP)
        }

        // In the event there is no photopicker returned, just refer to DocumentsUI.
        val photopickerComponentName = getPhotopickerComponentName(intent.type)
        if (photopickerComponentName == null) {
            forwardIntentToDocumentsUI()
            return
        }

        // The Photopicker has an entry point to take them back to DocumentsUI. In the event the
        // user originated from Photopicker, we don't want to send them back.
        val referredFromPhotopicker = referrer?.host == photopickerComponentName.packageName
        if (referredFromPhotopicker || !shouldForwardIntentToPhotopicker(intent)) {
            forwardIntentToDocumentsUI()
            return
        }

        // Forward intent to Photopicker.
        intent.setComponent(photopickerComponentName)
        startActivity(intent)
        finish()
    }

    private fun forwardIntentToDocumentsUI() {
        intent.setClass(applicationContext, PickActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun getPhotopickerComponentName(type: String?): ComponentName? {
        // Intent.ACTION_PICK_IMAGES is only available from SdkExtensions v2 onwards. Prior to that
        // the Photopicker was not available, so in those cases should always send to DocumentsUI.
        if (SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) < 2) {
            return null
        }

        // Attempt to resolve the `ACTION_PICK_IMAGES` intent to get the Photopicker package.
        // On T+ devices this is is a standalone package, whilst prior to T it is part of the
        // MediaProvider module.
        val pickImagesIntent = Intent(
            ACTION_PICK_IMAGES
        ).apply { addCategory(Intent.CATEGORY_DEFAULT) }
        val photopickerComponentName: ComponentName? = pickImagesIntent.resolveActivity(
            packageManager
        )

        // For certain devices the activity that handles ACTION_GET_CONTENT can be disabled (when
        // the ACTION_PICK_IMAGES is enabled) so double check by explicitly checking the
        // ACTION_GET_CONTENT activity on the same activity that handles ACTION_PICK_IMAGES.
        val photopickerGetContentIntent = Intent(ACTION_GET_CONTENT).apply {
            setType(type)
            setPackage(photopickerComponentName?.packageName)
        }
        val photopickerGetContentComponent: ComponentName? =
            photopickerGetContentIntent.resolveActivity(packageManager)

        // Ensure the `ACTION_GET_CONTENT` activity is enabled.
        if (!isComponentEnabled(photopickerGetContentComponent)) {
            if (DEBUG) {
                Log.d(TAG, "Photopicker PICK_IMAGES component has no enabled GET_CONTENT handler")
            }
            return null
        }

        return photopickerGetContentComponent
    }

    private fun isComponentEnabled(componentName: ComponentName?): Boolean {
        if (componentName == null) {
            return false
        }

        return when (packageManager.getComponentEnabledSetting(componentName)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> {
                // DEFAULT is a state that essentially defers to the state defined in the
                // AndroidManifest which can be either enabled or disabled.
                packageManager.getPackageInfo(
                    componentName.packageName,
                    PackageManager.GET_ACTIVITIES
                )?.let { packageInfo: PackageInfo ->
                    if (packageInfo.activities == null) {
                        return false
                    }
                    for (val info in packageInfo.activities) {
                        if (info.name == componentName.className) {
                            return info.enabled
                        }
                    }
                }
                return false
            }

            // Everything else is considered disabled.
            else -> false
        }
    }
}

fun shouldForwardIntentToPhotopicker(intent: Intent): Boolean {
    // Photopicker can only handle `ACTION_GET_CONTENT` intents.
    if (intent.action != ACTION_GET_CONTENT) {
        return false
    }

    // Photopicker only handles media mime types (i.e. image/* or video/*), however, it also handles
    // requests that have type */* with EXTRA_MIME_TYPES that are media mime types. In that scenario
    // it provides an escape hatch to the user to go back to DocumentsUI.
    val intentTypeIsMedia = isMediaMimeType(intent.type)
    if (!intentTypeIsMedia && intent.type != "*/*") {
        return false
    }

    val extraMimeTypes = intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)

    // In the event there were no `EXTRA_MIME_TYPES` this should exclusively be handled by
    // DocumentsUI and not Photopicker.
    if (intent.type == "*/*" && extraMimeTypes == null) {
        return false
    }

    if (extraMimeTypes == null) {
        return intentTypeIsMedia
    }

    return extraMimeTypes.isNotEmpty() && extraMimeTypes.none { !isMediaMimeType(it) }
}

fun isMediaMimeType(mimeType: String?): Boolean {
    return mimeType?.let { mimeType ->
        mimeType.startsWith("image/") || mimeType.startsWith("video/")
    } == true
}
