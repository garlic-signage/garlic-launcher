package com.sagiadinos.garlic.launcher.helper;

import com.sagiadinos.garlic.launcher.helper.ShellExecute;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static org.junit.jupiter.api.Assertions.*;
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
        ShellExecute MyTestClass   = createClassWithProcess();
        String s                   = "ab\ncd\nefg\nhijk";
        InputStream in             = new ByteArrayInputStream(s.getBytes());
        when(ProcessMocked.getErrorStream()).thenReturn(in);

        String result = MyTestClass.getErrorText();
        assertEquals("\n" + s +"\n", result);
    }

    @Test
    void getOutputText() throws IOException, InterruptedException
    {
        ShellExecute MyTestClass   = createClassWithProcess();
        String s                   = "ab\ncd\nefg";
        InputStream in             = new ByteArrayInputStream(s.getBytes());
        when(ProcessMocked.getInputStream()).thenReturn(in);

        String result = MyTestClass.getOutputText();
        assertEquals( s +"\n", result);
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