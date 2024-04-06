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

package com.sagiadinos.garlic.launcher.configuration;

import android.annotation.SuppressLint;

import com.sagiadinos.garlic.launcher.helper.RootChecker;

import java.util.UUID;

public class MainConfiguration
{
    private SharedPreferencesModel Model;
    private final String DEFAULT_CONTENT_URL = "https://indexes.smil-control.com";
    public enum STANDBY_MODE {
        none,
        partially,
        deep
    }
    public MainConfiguration(SharedPreferencesModel model)
    {
        this.Model = model;
    }

    @SuppressLint("ApplySharedPref")
    public void firstStart(RootChecker MyRootChecker)
    {
        Model.storeString("uuid", UUID.randomUUID().toString());
        storeSmilIndex(DEFAULT_CONTENT_URL);
        setIsDeviceRooted(MyRootChecker.isDeviceRooted());
    }

    public void convertValues()
    {
        // we delete use_device_standby and replace it with something better
        if (Model.hasParameter("use_device_standby"))
        {
            boolean is_standby = Model.getBoolean("use_device_standby");
            if (is_standby)
                setStandbyMode(STANDBY_MODE.partially.toString());
            else
                setStandbyMode(STANDBY_MODE.none.toString());
            Model.removeParameter("use_device_standby");
        }
    }

    public boolean isFirstStart()
    {
        return (getUUID() == null);
    }


    public void storeSmilIndex(String smil_index)
    {
        Model.storeString("smil_index_uri", smil_index);
    }

    public String getSmilIndex()
    {
        return Model.getString("smil_index_uri");
    }

    public void storePlayerStartDelay(int player_start_delay)
    {
        Model.storeInt("player_start_delay", player_start_delay);
    }

    public int getPlayerStartDelay()
    {
        if (Model.hasParameter("player_start_delay"))
        {
            return Model.getInt("player_start_delay");
        }
        else
        {
            return 15;
        }
    }

    public void toggleNoPlayerStartDelayAfterBoot(boolean value)
    {
        Model.storeBoolean("no_player_start_delay_after_boot", value);
    }

    public void toogleRebootAfterInstall(boolean value)
    {
        Model.storeBoolean("reboot_after_install", value);
    }

    public boolean hasRebootAfterInstall()
    {
        return Model.getBoolean("reboot_after_install", true);
    }

    public boolean hasDailyReboot()
    {
        return Model.getBoolean("has_daily_reboot", false);
    }

    public void toggleDailyReboot(boolean value)
    {
        Model.storeBoolean("has_daily_reboot", value);
    }

    public String getRebootTime()
    {
      return Model.getString("reboot_time", "3:00");
    }

    public void setRebootTime(String value)
    {
        Model.storeString("reboot_time", value);
    }

    public boolean hasNoPlayerStartDelayAfterBoot()
    {
        return Model.getBoolean("no_player_start_delay_after_boot");
    }

    public void toggleJustBooted(boolean value)
    {
        Model.storeBoolean("just_booted", value);
    }

    public boolean isJustBooted()
    {
        return Model.getBoolean("just_booted");
    }

    public String getUUID()
    {
        return Model.getString("uuid");
    }

    public void toggleOwnBackButton(boolean value)
    {
        Model.storeBoolean("own_back_button", value);
    }

    public boolean hasOwnBackButton()
    {
        return Model.getBoolean("own_back_button");
    }

    public void toggleActiveServicePassword(boolean value)
    {
        Model.storeBoolean("active_service_password", value);
    }

    public String getStandbyMode()
    {
        return Model.getString("standby_mode");
    }

    public void setStandbyMode(String value)
    {
        Model.storeString("standby_mode", value);
    }

    public boolean hasActiveServicePassword()
    {
        return Model.getBoolean("active_service_password");
    }


    public boolean isStrictKioskModeActive()
    {
        return Model.getBoolean("is_strict_kiosk_mode");
    }

    public boolean isPlayerInstalled()
    {
        return Model.getBoolean("is_player_installed");
    }

    public void togglePlayerInstalled(boolean value)
    {
        Model.storeBoolean("is_player_installed", value);
    }

    public void setServicePassword(String cleartext__password, PasswordHasher MyPasswordHasher)
    {
        String salt                = MyPasswordHasher.generateSalt();
        Model.storeString("service_password_salt", salt);

        String hashed              = MyPasswordHasher.hashClearTextWithSalt(cleartext__password, salt);
        Model.storeString("service_password_hash", hashed);
    }

    public boolean compareServicePassword(String cleartext__password, PasswordHasher MyPasswordHasher)
    {
        String salt                = Model.getString("service_password_salt");
        String hashed              = MyPasswordHasher.hashClearTextWithSalt(cleartext__password, salt);

        return (hashed.equals(Model.getString("service_password_hash")));
    }

    public boolean isDeviceRooted()
    {
        return Model.getBoolean("is_device_rooted");
    }

    public void setStrictKioskMode(boolean value)
    {
        Model.storeBoolean("is_strict_kiosk_mode", value);
    }

    private void setIsDeviceRooted(Boolean value)
    {
        Model.storeBoolean("is_device_rooted", value);
    }
  }
