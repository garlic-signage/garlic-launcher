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

import com.sagiadinos.garlic.launcher.helper.RootChecker;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mock;


import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.*;

class MainConfigurationTest
{
    @Mock
    SharedPreferencesModel SharedPreferencesModelMocked;

    @AfterEach
    void tearDown()
    {
        SharedPreferencesModelMocked = null;
    }


    @Test
    void firstStart()
    {
        MainConfiguration MyMainConfiguration = createClass();
        RootChecker RootCheckerMocked = mock(RootChecker.class);


        when(RootCheckerMocked.isDeviceRooted()).thenReturn(false);

        MyMainConfiguration.firstStart(RootCheckerMocked);

        verify(SharedPreferencesModelMocked, times(2)).storeString(anyString(), anyString());

        verify(SharedPreferencesModelMocked, times(1)).storeBoolean("is_device_rooted", false);

    }

    @Test
    void isFirstStartWhenNull()
    {
        MainConfiguration MyMainConfiguration = createClass();
        when(SharedPreferencesModelMocked.getString("uuid")).thenReturn(null);

        assertTrue(MyMainConfiguration.isFirstStart());
    }

    @Test
    void isFirstStartWhenNot()
    {
        MainConfiguration MyMainConfiguration = createClass();
        when(SharedPreferencesModelMocked.getString("uuid")).thenReturn("a uuid");

        assertFalse(MyMainConfiguration.isFirstStart());
    }


    @Test
    void storeSmilIndex()
    {
        MainConfiguration MyMainConfiguration = createClass();

        MyMainConfiguration.storeSmilIndex("an index");

        verify(SharedPreferencesModelMocked, times(1)).storeString("smil_index_uri", "an index");
    }

    @Test
    void getSmilIndex()
    {
        MainConfiguration MyMainConfiguration = createClass();

        MyMainConfiguration.getSmilIndex();

        verify(SharedPreferencesModelMocked, times(1)).getString("smil_index_uri");
    }

    @Test
    void storePlayerStartDelay()
    {
        MainConfiguration MyMainConfiguration = createClass();

        MyMainConfiguration.storePlayerStartDelay(22);

        verify(SharedPreferencesModelMocked, times(1)).storeInt("player_start_delay", 22);
    }

    @Test
    void getPlayerStartDelayWithExistingParameter()
    {
        MainConfiguration MyMainConfiguration = createClass();
        when(SharedPreferencesModelMocked.hasParameter("player_start_delay")).thenReturn(true);
        when(SharedPreferencesModelMocked.getInt("player_start_delay")).thenReturn(10);

        assertEquals(10, MyMainConfiguration.getPlayerStartDelay());

        verify(SharedPreferencesModelMocked, times(1)).getInt("player_start_delay");
    }

    @Test
    void getPlayerStartDelayWithOutExistingParameter()
    {
        MainConfiguration MyMainConfiguration = createClass();
        when(SharedPreferencesModelMocked.hasParameter("player_start_delay")).thenReturn(false);

        assertEquals(15, MyMainConfiguration.getPlayerStartDelay());

        verify(SharedPreferencesModelMocked, times(0)).getInt("player_start_delay");
    }

    @Test
    void hasNoPlayerStartDelayAfterBoot()
    {
        MainConfiguration MyMainConfiguration = createClass();

        MyMainConfiguration.hasNoPlayerStartDelayAfterBoot();

        verify(SharedPreferencesModelMocked, times(1)).getBoolean("no_player_start_delay_after_boot");
    }

    @Test
    void toggleNoPlayerStartDelayAfterBoot()
    {
        MainConfiguration MyMainConfiguration = createClass();

        MyMainConfiguration.toggleNoPlayerStartDelayAfterBoot(true);

        verify(SharedPreferencesModelMocked, times(1)).storeBoolean("no_player_start_delay_after_boot", true);
    }

    @Test
    void getUUID()
    {
        MainConfiguration MyMainConfiguration = createClass();

        MyMainConfiguration.getUUID();

        verify(SharedPreferencesModelMocked, times(1)).getString("uuid");
    }

    @Test
    void toggleOwnBackButton()
    {
        MainConfiguration MyMainConfiguration = createClass();

        MyMainConfiguration.toggleOwnBackButton(true);

        verify(SharedPreferencesModelMocked, times(1)).storeBoolean("own_back_button", true);
    }

    @Test
    void hasOwnBackButton()
    {
        MainConfiguration MyMainConfiguration = createClass();

        MyMainConfiguration.hasOwnBackButton();

        verify(SharedPreferencesModelMocked, times(1)).getBoolean("own_back_button");
    }

    @Test
    void toggleActiveServicePassword()
    {
        MainConfiguration MyMainConfiguration = createClass();

        MyMainConfiguration.toggleActiveServicePassword(true);

        verify(SharedPreferencesModelMocked, times(1)).storeBoolean("active_service_password", true);
    }

    @Test
    void hasActiveServicePassword()
    {
        MainConfiguration MyMainConfiguration = createClass();

        MyMainConfiguration.hasActiveServicePassword();

        verify(SharedPreferencesModelMocked, times(1)).getBoolean("active_service_password");
    }

    @Test
    void isStrictKioskModeActive()
    {
        MainConfiguration MyMainConfiguration = createClass();

        MyMainConfiguration.isStrictKioskModeActive();

        verify(SharedPreferencesModelMocked, times(1)).getBoolean("is_strict_kiosk_mode");
    }

    @Test
    void isPlayerInstalled()
    {
        MainConfiguration MyMainConfiguration = createClass();

        MyMainConfiguration.isPlayerInstalled();

        verify(SharedPreferencesModelMocked, times(1)).getBoolean("is_player_installed");
    }

    @Test
    void togglePlayerInstalled()
    {
        MainConfiguration MyMainConfiguration = createClass();

        MyMainConfiguration.togglePlayerInstalled(false);

        verify(SharedPreferencesModelMocked, times(1)).storeBoolean("is_player_installed", false);

    }

    @Test
    void setServicePassword()
    {
        MainConfiguration MyMainConfiguration = createClass();

        PasswordHasher PasswordHasherMock = mock(PasswordHasher.class);

        String salt               = "a crazy salt";
        String cleartext_password = "a secret password";
        String hashed_password    = "heidewitzkaderkapitän";

        when(PasswordHasherMock.generateSalt()).thenReturn(salt);
        when(PasswordHasherMock.hashClearTextWithSalt(cleartext_password, salt, PasswordHasher.ITERATIONS)).thenReturn(hashed_password);

        InOrder order = inOrder(SharedPreferencesModelMocked);

        MyMainConfiguration.setServicePassword(cleartext_password, PasswordHasherMock);

        order.verify(SharedPreferencesModelMocked, times(1)).storeString("service_password_salt", salt);
        order.verify(SharedPreferencesModelMocked, times(1)).storeString("service_password_hash", hashed_password);
        order.verify(SharedPreferencesModelMocked, times(1)).storeInt("service_password_iterations", PasswordHasher.ITERATIONS);
    }

    @Test
    void compareServicePasswordSucceed()
    {
        MainConfiguration MyMainConfiguration = createClass();

        PasswordHasher PasswordHasherMock = mock(PasswordHasher.class);

        String salt               = "a crazy salt";
        String cleartext_password = "a secret password";
        String hashed_password    = "heidewitzkaderkapitän";

        mockStoredPassword(salt, hashed_password, PasswordHasher.ITERATIONS);
        when(PasswordHasherMock.hashClearTextWithSalt(cleartext_password, salt, PasswordHasher.ITERATIONS)).thenReturn(hashed_password);

        assertTrue(MyMainConfiguration.compareServicePassword(cleartext_password, PasswordHasherMock));

        // current hash: nothing is rewritten
        verify(SharedPreferencesModelMocked, never()).storeString(anyString(), anyString());
    }

    @Test
    void compareServicePasswordFails()
    {
        MainConfiguration MyMainConfiguration = createClass();

        PasswordHasher PasswordHasherMock = mock(PasswordHasher.class);

        String salt               = "a crazy salt";
        String cleartext_password = "a secret password";

        mockStoredPassword(salt, "differt hash", PasswordHasher.ITERATIONS);
        when(PasswordHasherMock.hashClearTextWithSalt(cleartext_password, salt, PasswordHasher.ITERATIONS)).thenReturn("another hash");

        assertFalse(MyMainConfiguration.compareServicePassword(cleartext_password, PasswordHasherMock));
    }

    @Test
    void compareServicePasswordWithLegacyHashRehashes()
    {
        MainConfiguration MyMainConfiguration = createClass();

        PasswordHasher PasswordHasherMock = mock(PasswordHasher.class);

        String old_salt           = "old salt";
        String new_salt           = "new salt";
        String cleartext_password = "a secret password";

        // hashes of launcher versions before the iterations were stored
        when(SharedPreferencesModelMocked.getString("service_password_salt")).thenReturn(old_salt);
        when(SharedPreferencesModelMocked.getString("service_password_hash")).thenReturn("legacy hash");
        when(SharedPreferencesModelMocked.getInt("service_password_iterations", PasswordHasher.LEGACY_ITERATIONS))
                .thenReturn(PasswordHasher.LEGACY_ITERATIONS);
        when(PasswordHasherMock.hashClearTextWithSalt(cleartext_password, old_salt, PasswordHasher.LEGACY_ITERATIONS)).thenReturn("legacy hash");
        when(PasswordHasherMock.generateSalt()).thenReturn(new_salt);
        when(PasswordHasherMock.hashClearTextWithSalt(cleartext_password, new_salt, PasswordHasher.ITERATIONS)).thenReturn("new hash");

        assertTrue(MyMainConfiguration.compareServicePassword(cleartext_password, PasswordHasherMock));

        verify(SharedPreferencesModelMocked, times(1)).storeString("service_password_salt", new_salt);
        verify(SharedPreferencesModelMocked, times(1)).storeString("service_password_hash", "new hash");
        verify(SharedPreferencesModelMocked, times(1)).storeInt("service_password_iterations", PasswordHasher.ITERATIONS);
    }

    @Test
    void compareServicePasswordWithLegacyHashFailsWithoutRehash()
    {
        MainConfiguration MyMainConfiguration = createClass();

        PasswordHasher PasswordHasherMock = mock(PasswordHasher.class);

        mockStoredPassword("old salt", "legacy hash", PasswordHasher.LEGACY_ITERATIONS);
        when(PasswordHasherMock.hashClearTextWithSalt("wrong", "old salt", PasswordHasher.LEGACY_ITERATIONS)).thenReturn("other hash");

        assertFalse(MyMainConfiguration.compareServicePassword("wrong", PasswordHasherMock));

        verify(SharedPreferencesModelMocked, never()).storeString(anyString(), anyString());
    }

    @Test
    void compareServicePasswordWithoutStoredPasswordFails()
    {
        MainConfiguration MyMainConfiguration = createClass();

        PasswordHasher PasswordHasherMock = mock(PasswordHasher.class);

        when(SharedPreferencesModelMocked.getString("service_password_salt")).thenReturn(null);
        when(SharedPreferencesModelMocked.getString("service_password_hash")).thenReturn(null);

        assertFalse(MyMainConfiguration.compareServicePassword("anything", PasswordHasherMock));
        verify(PasswordHasherMock, never()).hashClearTextWithSalt(anyString(), anyString(), anyInt());
    }

    @Test
    void compareServicePasswordWithNullCleartextFails()
    {
        MainConfiguration MyMainConfiguration = createClass();

        PasswordHasher PasswordHasherMock = mock(PasswordHasher.class);

        mockStoredPassword("salt", "hash", PasswordHasher.ITERATIONS);

        assertFalse(MyMainConfiguration.compareServicePassword(null, PasswordHasherMock));
    }

    private void mockStoredPassword(String salt, String hash, int iterations)
    {
        when(SharedPreferencesModelMocked.getString("service_password_salt")).thenReturn(salt);
        when(SharedPreferencesModelMocked.getString("service_password_hash")).thenReturn(hash);
        when(SharedPreferencesModelMocked.getInt("service_password_iterations", PasswordHasher.LEGACY_ITERATIONS)).thenReturn(iterations);
    }


    @Test
    void getVolume()
    {
        MainConfiguration MyMainConfiguration = createClass();
        when(SharedPreferencesModelMocked.getInt("volume", 100)).thenReturn(30);

        assertEquals(30, MyMainConfiguration.getVolume());
    }

    @Test
    void getBrightnessReadsBrightnessNotVolume()
    {
        MainConfiguration MyMainConfiguration = createClass();
        when(SharedPreferencesModelMocked.getInt("volume", 100)).thenReturn(30);
        when(SharedPreferencesModelMocked.getInt("brightness", 100)).thenReturn(70);

        assertEquals(70, MyMainConfiguration.getBrightness());
    }

    @Test
    void isDeviceRooted()
    {
        MainConfiguration MyMainConfiguration = createClass();

        MyMainConfiguration.isDeviceRooted();

        verify(SharedPreferencesModelMocked, times(1)).getBoolean("is_device_rooted");
    }

    @Test
    void setStrictKioskMode()
    {
        MainConfiguration MyMainConfiguration = createClass();

        MyMainConfiguration.setStrictKioskMode(true);

        verify(SharedPreferencesModelMocked, times(1)).storeBoolean("is_strict_kiosk_mode", true);
    }


    MainConfiguration createClass()
    {
        SharedPreferencesModelMocked       = mock(SharedPreferencesModel.class);
        return new MainConfiguration(SharedPreferencesModelMocked);
    }


}