package com.sagiadinos.garlic.launcher.helper;

import com.sagiadinos.garlic.launcher.helper.ShellExecute;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mock;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShellExecuteTest
{
    @Mock
    Process ProcessMocked;
    @Mock
    Runtime RuntimeMocked;

    @AfterEach
    void tearDown()
    {
        RuntimeMocked        = null;
        ProcessMocked = null;
    }

    @Test
    void executeAsRootSucceed() throws IOException, InterruptedException
    {
        ShellExecute MyTestClass   = createClassWithProcess();
        verify(RuntimeMocked, times(1)).exec("su\n");
    }

    @Test
    void executeAsRootFails() throws IOException, InterruptedException
    {
        ShellExecute MyTestClass = createClass();
        String command           = "run this command";

        when(RuntimeMocked.exec("su\n")).thenReturn(ProcessMocked);
        stubProcessStreams();
        when(ProcessMocked.waitFor()).thenReturn(10);

        assertFalse(MyTestClass.executeAsRoot(command));
        verify(RuntimeMocked, times(1)).exec("su\n");
    }

    @Test
    void executeAsUserSucceed() throws IOException, InterruptedException
    {
        ShellExecute MyTestClass = createClass();
        String command           = "run this command";

        when(RuntimeMocked.exec("sh\n")).thenReturn(ProcessMocked);
        stubProcessStreams();
        when(ProcessMocked.waitFor()).thenReturn(0);

        assertTrue(MyTestClass.executeAsUser(command));
        verify(RuntimeMocked, times(1)).exec("sh\n");
    }

    @Test
    void getErrorText() throws IOException, InterruptedException
    {
        String s                 = "ab\ncd\nefg\nhijk";
        ShellExecute MyTestClass = createClassWithStreams("", s, 1);

        assertFalse(MyTestClass.executeAsRoot("run this command"));

        assertEquals("\n" + s + "\n", MyTestClass.getErrorText());
    }

    @Test
    void getOutputText() throws IOException, InterruptedException
    {
        String s                 = "ab\ncd\nefg";
        ShellExecute MyTestClass = createClassWithStreams(s, "", 0);

        assertTrue(MyTestClass.executeAsRoot("run this command"));

        assertEquals(s + "\n", MyTestClass.getOutputText());
    }

    /**
     * execute() reads the streams itself; the texts must still be available
     * afterwards and can be read more than once.
     */
    @Test
    void textsCanBeReadRepeatedly() throws IOException, InterruptedException
    {
        ShellExecute MyTestClass = createClassWithStreams("some output", "permission denied", 1);

        assertFalse(MyTestClass.executeAsRoot("pm grant x y"));

        assertEquals("\npermission denied\n", MyTestClass.getErrorText());
        assertEquals("\npermission denied\n", MyTestClass.getErrorText());
        assertEquals("some output\n", MyTestClass.getOutputText());
        assertEquals("some output\n", MyTestClass.getOutputText());
    }

    @Test
    void nextExecutionReplacesTexts() throws IOException, InterruptedException
    {
        ShellExecute MyTestClass = createClass();
        when(RuntimeMocked.exec("su\n")).thenThrow(new IOException("su not found")).thenReturn(ProcessMocked);
        assertFalse(MyTestClass.executeAsRoot("first"));
        assertEquals("su not found", MyTestClass.getErrorText());

        when(ProcessMocked.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        when(ProcessMocked.getInputStream()).thenReturn(new ByteArrayInputStream("ok".getBytes()));
        when(ProcessMocked.getErrorStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(ProcessMocked.waitFor()).thenReturn(0);
        assertTrue(MyTestClass.executeAsRoot("second"));

        assertEquals("\n", MyTestClass.getErrorText());
        assertEquals("ok\n", MyTestClass.getOutputText());
    }

    @Test
    void writesCommandAndExitToShell() throws IOException, InterruptedException
    {
        ShellExecute MyTestClass = createClass();
        ByteArrayOutputStream stdin = new ByteArrayOutputStream();
        when(RuntimeMocked.exec("su\n")).thenReturn(ProcessMocked);
        stubProcessStreams();
        when(ProcessMocked.getOutputStream()).thenReturn(stdin);
        when(ProcessMocked.waitFor()).thenReturn(0);

        assertTrue(MyTestClass.executeAsRoot("pm grant x y"));

        assertEquals("pm grant x y\nexit\n", stdin.toString());
    }

    @Test
    void textsWithoutProcess()
    {
        ShellExecute MyTestClass = createClass();

        assertEquals("", MyTestClass.getErrorText());
        assertEquals("No OutputText cause MyProcess is Null", MyTestClass.getOutputText());
    }

    @Test
    void execFailureReturnsFalseWithErrorText() throws IOException
    {
        ShellExecute MyTestClass = createClass();
        when(RuntimeMocked.exec("su\n")).thenThrow(new IOException("su not found"));

        assertFalse(MyTestClass.executeAsRoot("anything"));
        assertEquals("su not found", MyTestClass.getErrorText());
    }

    @Test
    void interruptedReturnsFalse() throws IOException, InterruptedException
    {
        ShellExecute MyTestClass = createClass();
        when(RuntimeMocked.exec("sh\n")).thenReturn(ProcessMocked);
        stubProcessStreams();
        when(ProcessMocked.waitFor()).thenThrow(new InterruptedException("stopped"));

        assertFalse(MyTestClass.executeAsUser("anything"));
        assertTrue(MyTestClass.getErrorText().startsWith("stopped"));
    }

    /**
     * A real sh with more output than the pipe buffer (64 KB) on both streams.
     * Before the fix the process blocked and waitFor() never returned.
     */
    @Test
    void largeOutputDoesNotBlock()
    {
        ShellExecute MyTestClass = new ShellExecute(Runtime.getRuntime());
        String command = "head -c 300000 /dev/zero | tr '\\0' 'o' | fold -w 100; "
                       + "head -c 300000 /dev/zero | tr '\\0' 'e' | fold -w 100 >&2";

        boolean result = assertTimeoutPreemptively(Duration.ofSeconds(20), () -> MyTestClass.executeAsUser(command));

        assertTrue(result);
        assertEquals(3000 * 101, MyTestClass.getOutputText().length());
        assertEquals(1 + 3000 * 101, MyTestClass.getErrorText().length());
        assertTrue(MyTestClass.getOutputText().startsWith("ooo"));
        assertTrue(MyTestClass.getErrorText().startsWith("\neee"));
    }

    @Test
    void realFailingCommandKeepsStderr()
    {
        ShellExecute MyTestClass = new ShellExecute(Runtime.getRuntime());

        boolean result = assertTimeoutPreemptively(Duration.ofSeconds(10),
                () -> MyTestClass.executeAsUser("echo partial; echo 'permission denied' >&2; exit 3"));

        assertFalse(result);
        assertEquals("partial\n", MyTestClass.getOutputText());
        assertEquals("\npermission denied\n", MyTestClass.getErrorText());
    }

    @Test
    void streamsAreReadBeforeWaitingForTheProcess() throws IOException, InterruptedException
    {
        ShellExecute MyTestClass = createClassWithStreams("out", "err", 0);

        assertTrue(MyTestClass.executeAsRoot("cmd"));

        InOrder order = inOrder(ProcessMocked);
        order.verify(ProcessMocked).getOutputStream();
        order.verify(ProcessMocked).getInputStream();
        order.verify(ProcessMocked).waitFor();
        verify(ProcessMocked, times(1)).waitFor();
    }

    ShellExecute createClassWithStreams(String stdout, String stderr, int exit_code) throws IOException, InterruptedException
    {
        ShellExecute MyTestClass = createClass();
        when(RuntimeMocked.exec("su\n")).thenReturn(ProcessMocked);
        when(ProcessMocked.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        when(ProcessMocked.getInputStream()).thenReturn(new ByteArrayInputStream(stdout.getBytes()));
        when(ProcessMocked.getErrorStream()).thenReturn(new ByteArrayInputStream(stderr.getBytes()));
        when(ProcessMocked.waitFor()).thenReturn(exit_code);
        return MyTestClass;
    }

    ShellExecute createClassWithProcess() throws IOException, InterruptedException
    {
        ShellExecute MyTestClass = createClass();
        String command           = "run this command";

        when(RuntimeMocked.exec("su\n")).thenReturn(ProcessMocked);
        stubProcessStreams();
        when(ProcessMocked.waitFor()).thenReturn(0);
        assertTrue(MyTestClass.executeAsRoot(command));

        return MyTestClass;
    }

    void stubProcessStreams()
    {
        when(ProcessMocked.getOutputStream()).thenReturn(mock(OutputStream.class));
        when(ProcessMocked.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(ProcessMocked.getErrorStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
    }

    ShellExecute createClass()
    {

        RuntimeMocked = mock(Runtime.class);
        ProcessMocked = mock(Process.class);
        return new ShellExecute(RuntimeMocked);
    }
}