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

import com.sagiadinos.garlic.launcher.BuildConfig;
import com.sagiadinos.garlic.launcher.MainActivity;
import com.sagiadinos.garlic.launcher.configuration.MainConfiguration;

/**
 * This class is responsible for methods which needed to create a Kiosk Mode
 * Kiosk Mode means:
 *
 * Standard Home-Button Activity! this App will become a system launcher and is set
 * LockTask/Pinning, No one can escape from the app.
 *
 */
public class KioskManager
{
    private final DeviceOwner          MyDeviceOwner;
    private final HomeLauncherManager  MyLauncher;
    private final MainActivity         MyMainActivity;
    private final MainConfiguration    MyMainConfiguration;

    public KioskManager(DeviceOwner dvo, HomeLauncherManager hlm, MainActivity ma, MainConfiguration mc)
    {
        MyDeviceOwner       = dvo;
        MyLauncher          = hlm;
        MyMainActivity      = ma;
        MyMainConfiguration = mc;
    }

    public void pin()
    {
        if (BuildConfig.DEBUG) // to get rid of this annoying Couldn't terminate previous instance  shit
            return;

        if (MyDeviceOwner.isLockTaskPermitted())
        {
            MyMainActivity.startLockTask();
        }
    }

    public void unpin()
    {
        MyMainActivity.stopLockTask();
    }

    public void toggleServiceMode(boolean value)
    {
        MyMainConfiguration.setStrictKioskMode(!value);
    }

    public boolean isStrictKioskModeActive()
    {
        return MyMainConfiguration.isStrictKioskModeActive();
    }

    public boolean isHomeActivity()
    {
        return MyLauncher.isHomeActivity();
    }

    public void becomeHomeActivity()
    {
        if (checkForDeviceRights())
        {
            MyLauncher.becomeHomeActivity(MyDeviceOwner);
        }
    }

    private boolean checkForDeviceRights()
    {
        if (!MyDeviceOwner.isAdminActive())
        {
            return false;
        }
        return MyDeviceOwner.isDeviceOwner();
    }


}