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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest
{

    @Test
    void generateSalt()
    {
        PasswordHasher MyClass = new PasswordHasher();
        String result = MyClass.generateSalt();

        assertTrue(result.length() > 16);

    }

    /**
     * Hashes stored by older launchers must still verify.
     */
    @Test
    void hashClearTextWithSaltLegacyIterations()
    {
        PasswordHasher MyClass = new PasswordHasher();
        String result = MyClass.hashClearTextWithSalt("Abracadabra", "Salem Aleikum", PasswordHasher.LEGACY_ITERATIONS);

        assertEquals("26d724ca38d9ef9b2e3ac16c96c781a987b93d33", result);
    }

    @Test
    void hashClearTextWithSaltCurrentIterations()
    {
        PasswordHasher MyClass = new PasswordHasher();
        String result = MyClass.hashClearTextWithSalt("Abracadabra", "Salem Aleikum", PasswordHasher.ITERATIONS);

        assertNotEquals("26d724ca38d9ef9b2e3ac16c96c781a987b93d33", result);
        assertEquals(result, MyClass.hashClearTextWithSalt("Abracadabra", "Salem Aleikum", PasswordHasher.ITERATIONS));
    }

    @Test
    void iterationsAreRaised()
    {
        assertEquals(1000, PasswordHasher.LEGACY_ITERATIONS);
        assertTrue(PasswordHasher.ITERATIONS >= 100000);
    }
}