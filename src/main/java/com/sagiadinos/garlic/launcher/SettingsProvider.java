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
package com.sagiadinos.garlic.launcher;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Binder;
import android.os.Process;

import com.sagiadinos.garlic.launcher.configuration.SharedPreferencesModel;
import com.sagiadinos.garlic.launcher.configuration.PasswordHasher;
import com.sagiadinos.garlic.launcher.configuration.ConfigXMLModel;
import com.sagiadinos.garlic.launcher.configuration.MainConfiguration;
import com.sagiadinos.garlic.launcher.configuration.NetworkData;
import com.sagiadinos.garlic.launcher.configuration.SystemSettings;
import com.sagiadinos.garlic.launcher.helper.CommandExecutor;
import com.sagiadinos.garlic.launcher.helper.ShellExecute;
import com.sagiadinos.garlic.launcher.helper.VersionInformation;

import org.jetbrains.annotations.NotNull;
import java.util.Objects;

/**
 * Answers only apps signed like the launcher (garlic-player and the launcher itself).
 * Otherwise every app could read content url and uuid or try service passwords.
 * A ContentProvider runs via Binder, so the caller is known, other than with a broadcast.
 *
 * call("apply_config", configuration_xml, null) takes the content of a configuration.xml
 * (not a path) and applies player name, content url, volume and brightness.
 * WiFi and ethernet are ignored on purpose: a wrong remote network config locks the device
 * out and the password would be in clear text on the shared storage. They stay USB only.
 * Returns a bundle with "result" = "accepted" or "rejected" (empty content).
 *
 * call("command", reboot|screen_off|screen_on|deep_standby, extras) executes a command
 * like the CommandReceiver. Extras (optional): "task_id", "seconds_to_wakeup".
 * Returns "accepted" or "rejected" (unknown command).
 */
public class SettingsProvider extends ContentProvider
{
    private static final String PROVIDER_NAME = "com.sagiadinos.garlic.launcher.SettingsProvider";

    private static final int SMIL_CONTENT_URL = 1;
    private static final int SERVICE_PASSWORD = 2;
    private static final int LAUNCHER_UUID    = 3;
    private static final int LAUNCHER_VERSION = 4;

    public static final String METHOD_APPLY_CONFIG = "apply_config";
    public static final String METHOD_COMMAND      = "command";
    public static final String RESULT_KEY          = "result";
    public static final String RESULT_ACCEPTED     = "accepted";
    public static final String RESULT_REJECTED     = "rejected";
    private static final UriMatcher uriMatcher = getUriMatcher();

    private MainConfiguration MyMainConfiguration = null;
    private VersionInformation MyVersionInformation = null;

    @Override
    public boolean onCreate()
    {
        MyMainConfiguration  = new MainConfiguration(new SharedPreferencesModel(Objects.requireNonNull(getContext())));
        MyVersionInformation = new VersionInformation(getContext());
        return true;
    }

    @Override
    public int delete(@NotNull Uri uri, String selection, String[] selectionArgs)
    {
        throw new UnsupportedOperationException("Not supported!");
    }

    @Override
    public String getType(@NotNull Uri uri)
    {
        return "vnd.android.cursor.item/vnd.com.sagiadinos.garlic.launcher.SettingsProvider.provider.string";
    }

    @Override
    public Uri insert(@NotNull Uri uri, ContentValues values)
    {
        throw new UnsupportedOperationException("Not supported!");
    }

    @Override
    public Cursor query(@NotNull Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder)
    {
        if (!isCallerTrusted())
            throw new SecurityException("caller is not signed like the launcher");

        MatrixCursor cursor                = new MatrixCursor(new String[] {uri.getPathSegments().get(0)});
        MatrixCursor.RowBuilder rowBuilder = cursor.newRow();
        switch (uriMatcher.match(uri))
        {
            case SMIL_CONTENT_URL:
                rowBuilder.add(MyMainConfiguration.getSmilIndex());
                break;
            case SERVICE_PASSWORD:
                rowBuilder.add(validatePassword(selection));
                break;
            case LAUNCHER_VERSION:
                rowBuilder.add(MyVersionInformation.forLauncher());
                break;
            case LAUNCHER_UUID:
                rowBuilder.add(MyMainConfiguration.getUUID());
                break;
            default:
                throw new IllegalArgumentException("Unsupported uri " + uri);
        }
        return cursor;
    }

    @Override
    public int update(@NotNull Uri uri, ContentValues values, String selection,
                      String[] selectionArgs)
    {
        throw new UnsupportedOperationException("Not supported!");
    }

    @Override
    public Bundle call(@NotNull String method, String arg, Bundle extras)
    {
        if (!isCallerTrusted())
            throw new SecurityException("caller is not signed like the launcher");

        boolean accepted;
        switch (method)
        {
            case METHOD_APPLY_CONFIG:
                accepted = arg != null && !arg.trim().isEmpty();
                if (accepted)
                    applyConfig(arg);
                break;
            case METHOD_COMMAND:
                accepted = executeCommand(arg, extras);
                break;
            default:
                throw new IllegalArgumentException("Unsupported method " + method);
        }

        Bundle result = new Bundle();
        result.putString(RESULT_KEY, accepted ? RESULT_ACCEPTED : RESULT_REJECTED);
        return result;
    }

    boolean executeCommand(String command, Bundle extras)
    {
        String task_id           = extras != null ? extras.getString("task_id") : null;
        String seconds_to_wakeup = extras != null ? extras.getString("seconds_to_wakeup") : null;
        return new CommandExecutor(Objects.requireNonNull(getContext())).execute(command, task_id, seconds_to_wakeup);
    }

    private void applyConfig(String xml)
    {
        // network data is parsed but not applied, see class comment
        ConfigXMLModel MyConfigXMLModel = new ConfigXMLModel(new NetworkData(), MyMainConfiguration);
        MyConfigXMLModel.parseConfigXml(xml); // stores content url and player name

        // only what the content contains, otherwise a config without volume would set 100 %
        SystemSettings MySystemSettings = new SystemSettings(Objects.requireNonNull(getContext()), MyMainConfiguration, new ShellExecute(Runtime.getRuntime()));
        if (MyConfigXMLModel.hasVolume())
        {
            MyMainConfiguration.storeVolume(MyConfigXMLModel.getVolume());
            MySystemSettings.configMasterVolume(MyConfigXMLModel.getVolume());
        }
        if (MyConfigXMLModel.hasBrightness())
        {
            MyMainConfiguration.storeBrightness(MyConfigXMLModel.getBrightness());
            MySystemSettings.configBrightness(MyConfigXMLModel.getBrightness());
        }
    }

    boolean isCallerTrusted()
    {
        int caller_uid = Binder.getCallingUid();
        int own_uid    = Process.myUid();
        if (caller_uid == own_uid)
            return true;

        PackageManager pm = Objects.requireNonNull(getContext()).getPackageManager();
        return pm.checkSignatures(caller_uid, own_uid) == PackageManager.SIGNATURE_MATCH;
    }

    private static UriMatcher getUriMatcher()
    {
        UriMatcher uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        uriMatcher.addURI(PROVIDER_NAME, "smil_content_url", SMIL_CONTENT_URL);
        uriMatcher.addURI(PROVIDER_NAME, "service_password", SERVICE_PASSWORD);
        uriMatcher.addURI(PROVIDER_NAME, "uuid", LAUNCHER_UUID);
        uriMatcher.addURI(PROVIDER_NAME, "launcher_version", LAUNCHER_VERSION);
        return uriMatcher;
    }

    private String validatePassword(String cleartext)
    {
        if (MyMainConfiguration.compareServicePassword(cleartext, new PasswordHasher()))
        {
            return "valid";
        }
        else
        {
            return "invalid";
        }

    }

}
