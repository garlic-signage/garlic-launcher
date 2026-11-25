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
package com.sagiadinos.garlic.launcher.helper;


import android.app.Activity;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.sagiadinos.garlic.launcher.R;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

/**
 * Downloads garlic-player when the network becomes available and hands the
 * APK to InstallAppReceiver, which checks the signature and installs it.
 */
public class PlayerDownloader extends ConnectivityManager.NetworkCallback
{
    static final String         DEFAULT_DOWNLOAD_URL = "https://garlic-signage.com/downloads/garlic-player.apk";
    /** wait some seconds to get sure the net is really reachable */
    static final long           DEFAULT_WAIT_MILLIS  = 5000;
    private static final String DOWNLOADED_FILENAME  = "garlic-player.apk";

    private final ProgressBar   DownloadProgressBar;
    private final String        apk_path;
    private final TextView      tvInformation;
    private final Activity      MyActivity;
    private final String        download_url;
    private final long          wait_millis;

    public PlayerDownloader(Activity a, ProgressBar pb, TextView tv)
    {
        this(a, pb, tv, DEFAULT_DOWNLOAD_URL, DEFAULT_WAIT_MILLIS);
    }

    /**
     * For tests: another source (e.g. a file: URL) and no waiting.
     */
    PlayerDownloader(Activity a, ProgressBar pb, TextView tv, String download_url, long wait_millis)
    {
        DownloadProgressBar = pb;
        tvInformation       = tv;
        MyActivity          = a;
        apk_path            = a.getExternalCacheDir() + "/" + DOWNLOADED_FILENAME;
        this.download_url   = download_url;
        this.wait_millis    = wait_millis;
    }

    @Override
    public void onAvailable(@NotNull Network network)
    {
        setInformationText(tvInformation, MyActivity.getString(R.string.download_player_in_progress));
        startPlayerDownload();
    }

    @Override
    public void onLost(@NotNull Network network)
    {
        setInformationText(tvInformation, MyActivity.getString(R.string.no_network));
    }

    String getApkPath()
    {
        return apk_path;
    }

    private void startPlayerDownload()
    {
        try
        {
            deleteOldDownload();
            if (wait_millis > 0)
                Thread.sleep(wait_millis);

            download();
            if (Installer.getAppNameFromPkgName(MyActivity, apk_path).equals(DeviceOwner.PLAYER_PACKAGE_NAME))
            {
                installPlayer();
            }
        }
        catch (Exception e)
        {
            setInformationText(tvInformation, e.getMessage());
            e.printStackTrace();
        }
    }

    private void download() throws Exception
    {
        URLConnection connection = new URL(download_url).openConnection();
        connection.connect();
        long file_size           = connection.getContentLengthLong();

        // closed before the installer reads the file
        try (InputStream in = connection.getInputStream();
             OutputStream out = new FileOutputStream(apk_path))
        {
            byte[] buffer          = new byte[16384];
            long downloaded_so_far = 0;
            int  read;
            while ((read = in.read(buffer)) > 0)
            {
                out.write(buffer, 0, read);
                downloaded_so_far += read;
                // the server may not send a length
                if (file_size > 0)
                    DownloadProgressBar.setProgress((int) ((downloaded_so_far * 100) / file_size), true);
            }
        }
    }

    private void installPlayer()
    {
        setInformationText(tvInformation,  MyActivity.getString(R.string.install_player_in_progress));
        // call InstallApp Broadcast
        Intent intent = new Intent("com.sagiadinos.garlic.launcher.receiver.InstallAppReceiver");
        intent.putExtra("apk_path", apk_path);
        MyActivity.sendBroadcast(intent);
     }

    private void deleteOldDownload() throws Exception
    {
        File old_file = new File(apk_path);
        if (old_file.exists() && !old_file.delete())
        {
            throw new Exception(apk_path + " could not be deleted");
        }
    }

    private void setInformationText(final TextView text, final String value)
    {
        MyActivity.runOnUiThread(() -> text.setText(value));
    }
}
