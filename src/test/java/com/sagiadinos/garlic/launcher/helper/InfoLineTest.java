package com.sagiadinos.garlic.launcher.helper;

import android.app.Activity;
import android.net.Network;
import android.os.Looper;
import android.widget.TextView;

import com.sagiadinos.garlic.launcher.configuration.MainConfiguration;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowBuild;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 25)
public class InfoLineTest
{
    Activity activity;
    VersionInformation VersionMocked;
    MainConfiguration MainConfigurationMocked;
    DiscSpace DiscSpaceMocked;
    TextView tvAppVersion;
    TextView tvFreeDiscSpace;
    TextView tvIP;
    InfoLine MyTestClass;

    @Before
    public void setUp()
    {
        activity                = Robolectric.buildActivity(Activity.class).setup().get();
        VersionMocked           = mock(VersionInformation.class);
        MainConfigurationMocked = mock(MainConfiguration.class);
        DiscSpaceMocked         = mock(DiscSpace.class);
        tvAppVersion            = new TextView(activity);
        tvFreeDiscSpace         = new TextView(activity);
        tvIP                    = new TextView(activity);
        MyTestClass = new InfoLine(activity, VersionMocked, MainConfigurationMocked, DiscSpaceMocked,
                tvAppVersion, tvFreeDiscSpace, tvIP);
    }

    @Test
    public void displayAppInformation()
    {
        ShadowBuild.setModel("BT-4K");
        when(VersionMocked.forLauncher()).thenReturn("v1.0");
        when(VersionMocked.forPlayer()).thenReturn("not found");
        when(MainConfigurationMocked.getUUID()).thenReturn("the-uuid");

        MyTestClass.displayAppInformation();

        waitForText(tvAppVersion);
        assertEquals("Model: BT-4K \n Launcher: v1.0 | Player: not found | UUID: the-uuid", tvAppVersion.getText().toString());
    }

    @Test
    public void refreshFreeDiscSpace()
    {
        when(DiscSpaceMocked.getFreePercent()).thenReturn(42);

        MyTestClass.refreshFreeDiscSpace();

        waitForText(tvFreeDiscSpace);
        verify(DiscSpaceMocked, times(1)).refresh();
        assertEquals("Free: 42 %", tvFreeDiscSpace.getText().toString());
    }

    /**
     * The IP appears 2 s after the network is available; without an address
     * the launcher tries again every 2 s. The result depends on the machine.
     */
    @Test
    public void networkAvailableShowsIp()
    {
        when(VersionMocked.forLauncher()).thenReturn("v1.0");

        MyTestClass.onAvailable(mock(Network.class));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertEquals("", tvIP.getText().toString());

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));

        String ip = InfoLine.getLocalIpAddress();
        if (ip == null)
            assertEquals("", tvIP.getText().toString());
        else
            assertEquals(" | IP: " + ip, tvIP.getText().toString());
    }

    @Test
    public void networkLostKeepsText()
    {
        tvIP.setText(" | IP: 10.0.0.2");

        MyTestClass.onLost(mock(Network.class));
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals(" | IP: 10.0.0.2", tvIP.getText().toString());
    }

    @Test
    public void localIpIsIpv4AndNotLoopback() throws Exception
    {
        String ip = InfoLine.getLocalIpAddress();
        if (ip == null)
            return;

        InetAddress address = InetAddress.getByName(ip);
        assertTrue(address instanceof Inet4Address);
        assertFalse(address.isLoopbackAddress());
    }

    /** the text is computed in a background thread and set on the main thread */
    private void waitForText(TextView view)
    {
        long end = System.currentTimeMillis() + 5000;
        while (view.getText().length() == 0 && System.currentTimeMillis() < end)
        {
            shadowOf(Looper.getMainLooper()).idle();
            try
            {
                Thread.sleep(10);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
