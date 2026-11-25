package com.sagiadinos.garlic.launcher.receiver;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import com.sagiadinos.garlic.launcher.configuration.MainConfiguration;
import com.sagiadinos.garlic.launcher.helper.CopyHandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A stick with a SMIL/ directory: the content is copied to the device and
 * garlic-player gets the new index via
 * "com.sagiadinos.garlic.player.java.SmilIndexReceiver" (extra "smil_index_path").
 * It has priority over config.xml and garlic-player.apk.
 */
class UsbConnectionReceiverSmilTest
{
    private static final String SMIL_ACTION   = "com.sagiadinos.garlic.player.java.SmilIndexReceiver";
    private static final String CONFIG_ACTION = "com.sagiadinos.garlic.launcher.receiver.ConfigXMLReceiver";

    @TempDir
    File tmp;

    File stick;
    File destination;
    Context ContextMocked;
    CopyHandler CopyHandlerMocked;
    MainConfiguration MainConfigurationMocked;
    Intent SmilIntentMocked;
    Intent ConfigIntentMocked;
    UsbConnectionReceiver MyTestClass;

    @BeforeEach
    void setUp()
    {
        stick       = new File(tmp, "stick");
        destination = new File(tmp, "files/SMIL");
        assertTrue(stick.mkdirs());

        ContextMocked           = mock(Context.class);
        CopyHandlerMocked       = mock(CopyHandler.class);
        MainConfigurationMocked = mock(MainConfiguration.class);
        SmilIntentMocked        = mock(Intent.class);
        ConfigIntentMocked      = mock(Intent.class);
        when(ContextMocked.getExternalFilesDir("/SMIL")).thenReturn(destination);

        MyTestClass = spy(new UsbConnectionReceiver());
        doReturn(CopyHandlerMocked).when(MyTestClass).createCopyHandler();
        doReturn(MainConfigurationMocked).when(MyTestClass).createMainConfiguration();
        doReturn(SmilIntentMocked).when(MyTestClass).createIntent(SMIL_ACTION);
        doReturn(ConfigIntentMocked).when(MyTestClass).createIntent(CONFIG_ACTION);
    }

    @Test
    void smilDirIsCopiedAndPlayerInformed() throws IOException
    {
        File smil = createDir("SMIL");

        MyTestClass.onReceive(ContextMocked, mountIntent());

        verify(CopyHandlerMocked, times(1)).removeRecursively(destination);
        verify(CopyHandlerMocked, times(1)).copy(smil, destination);
        verify(MainConfigurationMocked, times(1)).storeSmilIndex(destination + "/index.smil");
        verify(SmilIntentMocked, times(1)).putExtra("smil_index_path", destination.getAbsolutePath() + "/index.smil");
        verify(ContextMocked, times(1)).sendBroadcast(SmilIntentMocked);
    }

    @Test
    void smilDirHasPriorityOverConfigXml() throws IOException
    {
        createDir("SMIL");
        createFile("config.xml");

        MyTestClass.onReceive(ContextMocked, mountIntent());

        verify(ContextMocked, times(1)).sendBroadcast(SmilIntentMocked);
        verify(ContextMocked, never()).sendBroadcast(ConfigIntentMocked);
    }

    @Test
    void withoutSmilDirNothingIsCopied() throws IOException
    {
        createFile("config.xml");

        MyTestClass.onReceive(ContextMocked, mountIntent());

        verify(CopyHandlerMocked, never()).copy(any(), any());
        verify(MainConfigurationMocked, never()).storeSmilIndex(anyString());
        verify(ContextMocked, times(1)).sendBroadcast(ConfigIntentMocked);
    }

    /**
     * Not enough space or another copy error: a toast, and the stick is
     * examined further for config.xml.
     */
    @Test
    void copyErrorShowsToastAndContinuesWithConfigXml() throws IOException
    {
        createDir("SMIL");
        createFile("config.xml");
        doThrow(new IOException("not enough space")).when(CopyHandlerMocked).copy(any(), any());
        Toast ToastMocked = mock(Toast.class);

        try (MockedStatic<Toast> toast = mockStatic(Toast.class);
             MockedStatic<Log> ignoredLog = mockStatic(Log.class))
        {
            toast.when(() -> Toast.makeText(eq(ContextMocked), anyString(), anyInt())).thenReturn(ToastMocked);

            MyTestClass.onReceive(ContextMocked, mountIntent());

            toast.verify(() -> Toast.makeText(ContextMocked, "Error: not enough space", Toast.LENGTH_LONG), times(1));
            verify(ToastMocked, times(1)).show();
            verify(MainConfigurationMocked, never()).storeSmilIndex(anyString());
            verify(ContextMocked, never()).sendBroadcast(SmilIntentMocked);
            verify(ContextMocked, times(1)).sendBroadcast(ConfigIntentMocked);
        }
    }

    private Intent mountIntent()
    {
        Intent IntentMocked = mock(Intent.class);
        Uri UriMocked       = mock(Uri.class);
        when(IntentMocked.getAction()).thenReturn(Intent.ACTION_MEDIA_MOUNTED);
        when(IntentMocked.getData()).thenReturn(UriMocked);
        when(UriMocked.getPath()).thenReturn(stick.getAbsolutePath());
        return IntentMocked;
    }

    private File createDir(String name)
    {
        File dir = new File(stick, name);
        assertTrue(dir.mkdirs());
        return dir;
    }

    private File createFile(String name) throws IOException
    {
        File file = new File(stick, name);
        assertTrue(file.createNewFile());
        return file;
    }
}
