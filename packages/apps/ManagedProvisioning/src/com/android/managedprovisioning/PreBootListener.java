/*
 * Copyright 2014, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.managedprovisioning;

import android.app.admin.DevicePolicyManager;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.UserInfo;
import android.os.UserHandle;
import android.os.UserManager;

import com.android.managedprovisioning.task.DeleteNonRequiredAppsTask;

import java.util.List;

/**
 * After a system update, this class resets the cross-profile intent filters and checks
 * if apps that have been added to the system image need to be deleted.
 */
public class PreBootListener extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (context.getUserId() != UserHandle.USER_OWNER) {
            return;
        }

        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(
                Context.DEVICE_POLICY_SERVICE);
        PackageManager pm = context.getPackageManager();

        // Check for device owner.
        if (dpm.getDeviceOwner() != null && DeleteNonRequiredAppsTask
                    .shouldDeleteNonRequiredApps(context, UserHandle.USER_OWNER)) {

            // Delete new apps.
                deleteNonRequiredApps(context, dpm.getDeviceOwner(), UserHandle.USER_OWNER,
                        R.array.required_apps_managed_device,
                        R.array.vendor_required_apps_managed_device,
                        R.array.disallowed_apps_managed_device,
                        R.array.vendor_disallowed_apps_managed_device,
                        false /* Do not disable INSTALL_SHORTCUT listeners */);
        }

        // Check for managed profiles.
        UserManager um = (UserManager) context.getSystemService(Context.USER_SERVICE);
        List<UserInfo> profiles = um.getProfiles(UserHandle.USER_OWNER);
        if (profiles.size() <= 1) {
            return;
        }

        // Removes cross profile intent filters from the parent to all the managed profiles.
        pm.clearCrossProfileIntentFilters(UserHandle.USER_OWNER);

        // For each managed profile reset cross profile intent filters and delete new apps.
        for (UserInfo userInfo : profiles) {
            if (!userInfo.isManagedProfile()) {
                continue;
            }
            pm.clearCrossProfileIntentFilters(userInfo.id);
            CrossProfileIntentFiltersHelper.setFilters(
                    pm, UserHandle.USER_OWNER, userInfo.id);

            ComponentName profileOwner = dpm.getProfileOwnerAsUser(userInfo.id);
            if (profileOwner == null) {
                // Shouldn't happen.
                ProvisionLogger.loge("No profile owner on managed profile " + userInfo.id);
                continue;
            }
            deleteNonRequiredApps(context, profileOwner.getPackageName(), userInfo.id,
                    R.array.required_apps_managed_profile,
                    R.array.vendor_required_apps_managed_profile,
                    R.array.disallowed_apps_managed_profile,
                    R.array.vendor_disallowed_apps_managed_profile,
                    true /* Disable INSTALL_SHORTCUT listeners */);
        }
    }

    private void deleteNonRequiredApps(Context context, String mdmPackageName, int userId,
            int requiredAppsList, int vendorRequiredAppsList, int disallowedAppsList,
            int vendorDisallowedAppsList, boolean disableInstallShortcutListeners) {
        new DeleteNonRequiredAppsTask(context, mdmPackageName, userId,
                requiredAppsList, vendorRequiredAppsList,
                disallowedAppsList, vendorDisallowedAppsList,
                false /*we are not creating a new profile*/,
                disableInstallShortcutListeners,
                new DeleteNonRequiredAppsTask.Callback() {

                    @Override
                    public void onSuccess() {
                    }

                    @Override
                    public void onError() {
                        ProvisionLogger.loge("Error while checking if there are new system "
                                + "apps that need to be deleted");
                    }
                }).run();
    }
}
