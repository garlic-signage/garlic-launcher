package com.sagiadinos.garlic.launcher.helper;

import android.os.StatFs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

class CopyHandlerTest
{
    @TempDir
    File tmp;

    @Test
    void copySingleFile() throws IOException
    {
        File source      = write(new File(tmp, "source.txt"), "hello");
        File destination = new File(tmp, "destination.txt");

        try (MockedConstruction<StatFs> ignored = mockFreeSpace(1024))
        {
            new CopyHandler().copy(source, destination);
        }

        assertEquals("hello", read(destination));
    }

    @Test
    void copyDirectoryRecursively() throws IOException
    {
        File source = new File(tmp, "usb/SMIL");
        assertTrue(new File(source, "media").mkdirs());
        write(new File(source, "index.smil"), "<smil/>");
        write(new File(source, "media/image.jpg"), "jpg");
        File destination = new File(tmp, "files/SMIL");
        assertTrue(destination.getParentFile().mkdirs());

        List<String> statPaths = new ArrayList<>();
        try (MockedConstruction<StatFs> ignored = mockConstruction(StatFs.class, (mock, context) -> {
            statPaths.add((String) context.arguments().get(0));
            when(mock.getBlockSizeLong()).thenReturn(1L);
            when(mock.getAvailableBlocksLong()).thenReturn(1024L);
        }))
        {
            new CopyHandler().copy(source, destination);
        }

        // free space is checked on the parent of the SMIL directory
        assertEquals(List.of(new File(tmp, "files").getAbsolutePath()), statPaths);
        assertEquals("<smil/>", read(new File(destination, "index.smil")));
        assertEquals("jpg", read(new File(destination, "media/image.jpg")));
    }

    @Test
    void copyFailsWithoutEnoughSpace() throws IOException
    {
        File source      = write(new File(tmp, "source.txt"), "hello");
        File destination = new File(tmp, "destination.txt");

        try (MockedConstruction<StatFs> ignored = mockFreeSpace(4))
        {
            CopyHandler MyTestClass = new CopyHandler();
            assertThrows(IOException.class, () -> MyTestClass.copy(source, destination));
        }

        assertFalse(destination.exists());
    }

    @Test
    void removeRecursively() throws IOException
    {
        File dir = new File(tmp, "SMIL");
        assertTrue(new File(dir, "sub").mkdirs());
        write(new File(dir, "index.smil"), "x");
        write(new File(dir, "sub/a.jpg"), "y");

        new CopyHandler().removeRecursively(dir);

        assertFalse(dir.exists());
    }

    @Test
    void removeRecursivelyIgnoresMissingFile() throws IOException
    {
        File missing = new File(tmp, "missing");

        new CopyHandler().removeRecursively(missing);

        assertFalse(missing.exists());
    }

    private MockedConstruction<StatFs> mockFreeSpace(long bytes)
    {
        return mockConstruction(StatFs.class, (mock, context) -> {
            when(mock.getBlockSizeLong()).thenReturn(1L);
            when(mock.getAvailableBlocksLong()).thenReturn(bytes);
        });
    }

    private File write(File file, String content) throws IOException
    {
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private String read(File file) throws IOException
    {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
