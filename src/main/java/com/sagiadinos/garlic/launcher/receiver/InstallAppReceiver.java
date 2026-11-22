/*
 garlic-launcher: Android Launcher for the Digital Signage Software garlic-player

 Copyright (C) 2020 Nikolaos Sagiadinos <ns@smil-control.com>
 This file is part of the garlic-launcher source code

 This program is free software: you can redistribute it and/or  modify
 it under the terms of the GNU Affero General Public License, version 3,
 as published by the Free Software Foundation.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sagiadinos.garlic.launcher.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.io.File;
import java.io.IOException;

import com.sagiadinos.garlic.launcher.configuration.MainConfiguration;
import com.sagiadinos.garlic.launcher.configuration.SharedPreferencesModel;
import com.sagiadinos.garlic.launcher.helper.DeviceOwner;
import com.sagiadinos.garlic.launcher.helper.Installer;
import com.sagiadinos.garlic.launcher.helper.ShellExecute;
import com.sagiadinos.garlic.launcher.helper.SignatureVerifier;
import com.sagiadinos.garlic.launcher.helper.TaskExecutionReport;


/**
 * This receiver is responsible for signal to install
 * a software.
 *
 * Only APKs signed with the certificate of the launcher are installed.
 * Launcher and player share the signing key, so this covers updates of both.
 *
 */
public class InstallAppReceiver extends BroadcastReceiver
{
    private static final String TAG = "InstallAppReceiver";
    MainConfiguration MyMainConfiguration;
    @Override
    public void onReceive(Context ctx, Intent intent)
    {
        if (intent == null || intent.getAction() == null)
        {
            return;
        }
        if (!intent.getAction().equals("com.sagiadinos.garlic.launcher.receiver.InstallAppReceiver") ||
                !ctx.getPackageName().equals(DeviceOwner.LAUNCHER_PACKAGE_NAME))
        {
            return;
        }

        MyMainConfiguration      = new MainConfiguration(new SharedPreferencesModel(ctx));
        Installer MyInstaller    = new Installer(ctx);
        String file_path         = intent.getStringExtra("apk_path");
        String task_id           = "";
        if (intent.getStringExtra("task_id") != null)
        {
            task_id = intent.getStringExtra("task_id");
        }
        if (file_path == null)
        {
            TaskExecutionReport.append(task_id, "aborted");
            return;
        }

        SignatureVerifier MyVerifier = new SignatureVerifier(ctx.getPackageManager(), ctx.getPackageName());
        if (!MyVerifier.isSignedLikeReference(file_path))
        {
            Log.w(TAG, "Rejected installation of " + file_path + ": signature does not match the launcher");
            TaskExecutionReport.append(task_id, "aborted");
            deleteFromPlayerCache(file_path);
            return;
        }

        try
        {
            if (MyMainConfiguration.isDeviceRooted())
            {
                if (MyInstaller.installViaRootedShell(new ShellExecute(Runtime.getRuntime()), file_path))
                {
                    TaskExecutionReport.append(task_id, "completed");
                }
                else
                {
                    TaskExecutionReport.append(task_id, "aborted");
                }
            }
            else
            {
                MyInstaller.installPackage(task_id, file_path);
            }
        }
        catch (IOException e)
        {
            TaskExecutionReport.append(task_id, "aborted");
        }

        deleteFromPlayerCache(file_path);
    }

    /**
     * delete downloaded files in all cases which are in player cache
     * important! No delete - No further updates
     */
    private void deleteFromPlayerCache(String file_path)
    {
        if (file_path.contains("cache"))
        {
            File file = new File(file_path);
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }
}
