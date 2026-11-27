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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Objects;

import static org.mockito.Mockito.*;

import static org.junit.jupiter.api.Assertions.*;

class ConfigXMLModelTest
{
    @Mock
    NetworkData NetworkDataMocked;
    @Mock
    MainConfiguration mainConfigurationMocked;

    @AfterEach
    void tearDown()
    {
        NetworkDataMocked = null;
        mainConfigurationMocked = null;
    }

    @Test
    void readConfigXmlWithExistingConfig() throws IOException
    {
        ConfigXMLModel MyModel = createModel();
        String result = MyModel.readConfigXml(new File("sampledata/files/config_wlan_dhcp.xml"));

        assertTrue(result.contains("<prop name=\"info.playerName\" value=\"Playername\"/>"));
        assertTrue(result.contains("<prop name=\"content.bootFromServer\" value=\"true\"/>"));
        assertTrue(result.contains("<prop name=\"content.serverUrl\" value=\"https://indexes.a-server.test/\"/>"));
        assertTrue(result.contains("<prop name=\"net.wifi.ssid\" value=\"THESSID\"/>"));
        assertTrue(result.contains("<prop name=\"net.wifi.authentication\" value=\"WPA2PSK\"/>"));
        assertTrue(result.contains("<prop name=\"net.wifi.encryption\" value=\"TKIP\"/>"));
        assertTrue(result.contains("<prop name=\"net.wifi.password\" value=\"secret_passwd\"/>"));
        assertTrue(result.contains("<prop name=\"net.ethernet.dhcp.enabled\" value=\"true\"/>"));
    }

    @Test
    void readConfigXmlWithMissingConfig()
    {
        final ConfigXMLModel MyModel = createModel();
        Locale.setDefault(Locale.forLanguageTag("pt-BR"));

        Exception exception = assertThrows(FileNotFoundException.class, new Executable()
        {
            @Override
            public void execute() throws Throwable
            {
                MyModel.readConfigXml(new File("no/existing/filepath"));
            }
        });

       // assertEquals("no/existing/filepath (Datei oder Verzeichnis nicht gefunden)", exception.getMessage());
        // Todo Try to find out how we get this idiotic system to throw error messages in english for testing
        assertTrue(Objects.requireNonNull(exception.getMessage()).contains("no/existing/filepath"));
    }


    /**
     * Only the chars of the file, no '\0' from a not full last block.
     * Parsing and storing are tested in ConfigXMLModelParseTest (Robolectric).
     */
    @Test
    void readConfigXmlReturnsExactContent(@TempDir File tmp) throws IOException
    {
        File config = new File(tmp, "config.xml");
        Files.write(config.toPath(), "<config/>abcd".getBytes(StandardCharsets.UTF_8));

        assertEquals("<config/>abcd", createModel().readConfigXml(config));
    }

    @Test
    void readConfigXmlLargerThanBuffer(@TempDir File tmp) throws IOException
    {
        StringBuilder content = new StringBuilder("<configuration><userPref>");
        for (int i = 0; i < 200; i++)
            content.append("<prop name=\"p").append(i).append("\" value=\"äöü ").append(i).append("\"/>\n");
        content.append("</userPref></configuration>");
        File config = new File(tmp, "config.xml");
        Files.write(config.toPath(), content.toString().getBytes(StandardCharsets.UTF_8));

        assertEquals(content.toString(), createModel().readConfigXml(config));
    }

    @Test
    void readSampleFileHasNoNullChars() throws IOException
    {
        String result = createModel().readConfigXml(new File("sampledata/files/config_wlan_dhcp.xml"));

        assertEquals(-1, result.indexOf('\0'));
        assertTrue(result.trim().endsWith("</configuration>"));
    }

    @Test
    void readConfigXmlEmptyFile(@TempDir File tmp) throws IOException
    {
        File config = new File(tmp, "config.xml");
        assertTrue(config.createNewFile());

        assertEquals("", createModel().readConfigXml(config));
    }

    @Test
    void copy(@TempDir File tmp) throws IOException
    {
        byte[] content = new byte[5000];
        for (int i = 0; i < content.length; i++)
            content[i] = (byte) i;
        File source      = new File(tmp, "source.xml");
        File destination = new File(tmp, "destination.xml");
        Files.write(source.toPath(), content);

        createModel().copy(source, destination);

        assertArrayEquals(content, Files.readAllBytes(destination.toPath()));
    }

    @Test
    void copyOverwritesDestination(@TempDir File tmp) throws IOException
    {
        File source      = new File(tmp, "source.xml");
        File destination = new File(tmp, "destination.xml");
        Files.write(source.toPath(), "new".getBytes(StandardCharsets.UTF_8));
        Files.write(destination.toPath(), "old and longer".getBytes(StandardCharsets.UTF_8));

        createModel().copy(source, destination);

        assertEquals("new", new String(Files.readAllBytes(destination.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    void copyMissingSourceThrows(@TempDir File tmp)
    {
        File destination = new File(tmp, "destination.xml");

        assertThrows(FileNotFoundException.class, () -> createModel().copy(new File(tmp, "missing.xml"), destination));
        assertFalse(destination.exists());
    }

    ConfigXMLModel createModel()
    {
        NetworkDataMocked       = mock(NetworkData.class);
        mainConfigurationMocked = mock(MainConfiguration.class);
        return new ConfigXMLModel(NetworkDataMocked, mainConfigurationMocked);
    }

}