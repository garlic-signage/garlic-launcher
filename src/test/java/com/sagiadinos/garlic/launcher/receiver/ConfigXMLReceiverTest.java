package com.sagiadinos.garlic.launcher.receiver;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.sagiadinos.garlic.launcher.configuration.ConfigXMLModel;
import com.sagiadinos.garlic.launcher.configuration.NetworkData;
import com.sagiadinos.garlic.launcher.configuration.SystemSettings;
import com.sagiadinos.garlic.launcher.helper.TaskExecutionReport;
import com.sagiadinos.garlic.launcher.helper.WiFi;
import com.sagiadinos.garlic.launcher.testutil.FakeSharedPreferences;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Contract: action "com.sagiadinos.garlic.launcher.receiver.ConfigXMLReceiver",
 * extras "config_path" and "task_id". On success a reboot command is sent
 * to CommandReceiver, otherwise the task is reported as aborted.
 */
class ConfigXMLReceiverTest
{
    private static final String ACTION         = "com.sagiadinos.garlic.launcher.receiver.ConfigXMLReceiver";
    private static final String COMMAND_ACTION = "com.sagiadinos.garlic.launcher.receiver.CommandReceiver";
    private static final String CONFIG_PATH    = "/path/to/config.xml";

    Context ContextMocked;
    FakeSharedPreferences Prefs;
    List<String> sentActions;

    @BeforeEach
    void setUp()
    {
        ContextMocked = mock(Context.class);
        Prefs         = FakeSharedPreferences.attachTo(ContextMocked);
        sentActions   = new ArrayList<>();
    }

    @Test
    void onReceiveIntentNull()
    {
        new ConfigXMLReceiver().onReceive(ContextMocked, null);

        verifyNoInteractions(ContextMocked);
    }

    @Test
    void onReceiveActionNull()
    {
        Intent IntentMocked = mock(Intent.class);
        when(IntentMocked.getAction()).thenReturn(null);

        new ConfigXMLReceiver().onReceive(ContextMocked, IntentMocked);

        verifyNoInteractions(ContextMocked);
    }

    /**
     * Current behaviour: a missing config_path crashes the receiver.
     */
    @Test
    void onReceiveWithoutConfigPathThrows()
    {
        Intent IntentMocked = createIntent(null, "t1");

        ConfigXMLReceiver MyTestClass = new ConfigXMLReceiver();
        assertThrows(NullPointerException.class, () -> MyTestClass.onReceive(ContextMocked, IntentMocked));
    }

    @Test
    void validConfigStoresValuesAndSendsReboot() throws IOException
    {
        Intent IntentMocked = createIntent(CONFIG_PATH, "task-1");

        try (MockedConstruction<ConfigXMLModel> model = mockConfigXMLModel(80, 60);
             MockedConstruction<Intent> intents = mockIntents();
             MockedConstruction<WiFi> wifi = mockConstruction(WiFi.class))
        {
            new ConfigXMLReceiver().onReceive(ContextMocked, IntentMocked);

            ConfigXMLModel ModelMocked = model.constructed().get(0);
            verify(ModelMocked, times(1)).readConfigXml(new File(CONFIG_PATH));
            verify(ModelMocked, times(1)).parseConfigXml("<xml/>");

            assertEquals(80, Prefs.getInt("volume", -1));
            assertEquals(60, Prefs.getInt("brightness", -1));
            assertEquals(0, wifi.constructed().size());

            assertEquals(List.of(COMMAND_ACTION), sentActions);
            Intent sent = intents.constructed().get(0);
            verify(sent, times(1)).putExtra("command", "reboot");
            verify(sent, times(1)).putExtra("task_id", "task-1");
            verify(ContextMocked, times(1)).sendBroadcast(sent);
        }
    }

    @Test
    void validConfigWithoutTaskIdSendsEmptyTaskId() throws IOException
    {
        Intent IntentMocked = createIntent(CONFIG_PATH, null);

        try (MockedConstruction<ConfigXMLModel> ignored = mockConfigXMLModel(0, 0);
             MockedConstruction<Intent> intents = mockIntents())
        {
            new ConfigXMLReceiver().onReceive(ContextMocked, IntentMocked);

            verify(intents.constructed().get(0), times(1)).putExtra("task_id", "");
        }
    }

    @Test
    void configWithSsidConnectsWiFi() throws IOException
    {
        Intent IntentMocked          = createIntent(CONFIG_PATH, "task-1");
        WifiManager WifiManagerMocked = mock(WifiManager.class);
        when(ContextMocked.getSystemService(Context.WIFI_SERVICE)).thenReturn(WifiManagerMocked);

        try (MockedConstruction<NetworkData> network = mockConstruction(NetworkData.class,
                     (mock, context) -> when(mock.getWifiSSID()).thenReturn("MySSID"));
             MockedConstruction<ConfigXMLModel> ignored = mockConfigXMLModel(0, 0);
             MockedConstruction<WifiConfiguration> ignoredConfig = mockConstruction(WifiConfiguration.class);
             MockedConstruction<WiFi> wifi = mockConstruction(WiFi.class,
                     (mock, context) -> assertEquals(WifiManagerMocked, context.arguments().get(0)));
             MockedConstruction<Intent> ignoredIntents = mockIntents())
        {
            new ConfigXMLReceiver().onReceive(ContextMocked, IntentMocked);

            assertEquals(1, wifi.constructed().size());
            WiFi WiFiMocked = wifi.constructed().get(0);
            verify(WiFiMocked, times(1)).prepareConnection(network.constructed().get(0));
            verify(WiFiMocked, times(1)).connectToNetwork();
            assertEquals(List.of(COMMAND_ACTION), sentActions);
        }
    }

    @Test
    void volumeAndBrightnessFromConfigAreApplied()
    {
        Intent IntentMocked = createIntent(CONFIG_PATH, "task-1");

        try (MockedConstruction<ConfigXMLModel> ignored = mockConfigXMLModel(80, true, 60, true);
             MockedConstruction<SystemSettings> settings = mockConstruction(SystemSettings.class);
             MockedConstruction<Intent> ignoredIntents = mockIntents())
        {
            new ConfigXMLReceiver().onReceive(ContextMocked, IntentMocked);

            SystemSettings SettingsMocked = settings.constructed().get(0);
            verify(SettingsMocked, times(1)).configMasterVolume(80);
            verify(SettingsMocked, times(1)).configBrightness(60);
            assertEquals(List.of(COMMAND_ACTION), sentActions);
        }
    }

    /**
     * A config without volume or brightness must not set them to the default of 100 %.
     */
    @Test
    void missingVolumeAndBrightnessAreNotApplied()
    {
        Intent IntentMocked = createIntent(CONFIG_PATH, "task-1");

        try (MockedConstruction<ConfigXMLModel> ignored = mockConfigXMLModel(100, false, 100, false);
             MockedConstruction<SystemSettings> settings = mockConstruction(SystemSettings.class);
             MockedConstruction<Intent> ignoredIntents = mockIntents())
        {
            new ConfigXMLReceiver().onReceive(ContextMocked, IntentMocked);

            SystemSettings SettingsMocked = settings.constructed().get(0);
            verify(SettingsMocked, never()).configMasterVolume(anyInt());
            verify(SettingsMocked, never()).configBrightness(anyInt());
            // stored as before
            assertEquals(100, Prefs.getInt("volume", -1));
            assertEquals(100, Prefs.getInt("brightness", -1));
        }
    }

    @Test
    void onlyVolumeFromConfigIsApplied()
    {
        Intent IntentMocked = createIntent(CONFIG_PATH, "task-1");

        try (MockedConstruction<ConfigXMLModel> ignored = mockConfigXMLModel(30, true, 100, false);
             MockedConstruction<SystemSettings> settings = mockConstruction(SystemSettings.class);
             MockedConstruction<Intent> ignoredIntents = mockIntents())
        {
            new ConfigXMLReceiver().onReceive(ContextMocked, IntentMocked);

            SystemSettings SettingsMocked = settings.constructed().get(0);
            verify(SettingsMocked, times(1)).configMasterVolume(30);
            verify(SettingsMocked, never()).configBrightness(anyInt());
        }
    }

    @Test
    void unreadableConfigReportsAborted() throws IOException
    {
        Intent IntentMocked = createIntent(CONFIG_PATH, "task-2");

        try (MockedConstruction<ConfigXMLModel> ignored = mockConstruction(ConfigXMLModel.class,
                     (mock, context) -> when(mock.readConfigXml(any(File.class))).thenThrow(new IOException("not found")));
             MockedStatic<TaskExecutionReport> report = mockStatic(TaskExecutionReport.class);
             MockedStatic<Log> ignoredLog = mockStatic(Log.class);
             MockedConstruction<Intent> ignoredIntents = mockIntents())
        {
            new ConfigXMLReceiver().onReceive(ContextMocked, IntentMocked);

            report.verify(() -> TaskExecutionReport.append("task-2", "aborted"), times(1));
            assertEquals(0, sentActions.size());
            verify(ContextMocked, never()).sendBroadcast(any(Intent.class));
            assertEquals(-1, Prefs.getInt("volume", -1));
        }
    }

    private Intent createIntent(String config_path, String task_id)
    {
        Intent IntentMocked = mock(Intent.class);
        when(IntentMocked.getAction()).thenReturn(ACTION);
        when(IntentMocked.getStringExtra("config_path")).thenReturn(config_path);
        when(IntentMocked.getStringExtra("task_id")).thenReturn(task_id);
        return IntentMocked;
    }

    private MockedConstruction<ConfigXMLModel> mockConfigXMLModel(int volume, int brightness)
    {
        return mockConfigXMLModel(volume, false, brightness, false);
    }

    private MockedConstruction<ConfigXMLModel> mockConfigXMLModel(int volume, boolean has_volume, int brightness, boolean has_brightness)
    {
        return mockConstruction(ConfigXMLModel.class, (mock, context) -> {
            when(mock.readConfigXml(any(File.class))).thenReturn("<xml/>");
            when(mock.getVolume()).thenReturn(volume);
            when(mock.getBrightness()).thenReturn(brightness);
            when(mock.hasVolume()).thenReturn(has_volume);
            when(mock.hasBrightness()).thenReturn(has_brightness);
        });
    }

    private MockedConstruction<Intent> mockIntents()
    {
        return mockConstruction(Intent.class, (mock, context) -> {
            if (!context.arguments().isEmpty() && context.arguments().get(0) instanceof String)
                sentActions.add((String) context.arguments().get(0));
        });
    }
}
