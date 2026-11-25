package com.sagiadinos.garlic.launcher.helper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

class TaskExecutionReportTest
{
    @AfterEach
    void tearDown()
    {
        File TestFile = new File("./here/task_execution_log.xml");
        if (TestFile.exists() && !TestFile.delete())
        {
            fail();
        }

        File dir = new File("./here");
        if (dir.exists() && !dir.delete())
        {
            fail();
        }

    }

    @Test
    void constructor()
    {
        TaskExecutionReport MyTestClass  = new TaskExecutionReport("./here");
        File TestFile = new File("./here/task_execution_log.xml");
        assertTrue(TestFile.exists());
    }

    @Test
    void append()
    {
        TaskExecutionReport MyTestClass  = new TaskExecutionReport("./here");
        String task_id = "the_task_id";
        String state   = "the_state";

        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mmXXX", Locale.US);
        df.setTimeZone(TimeZone.getDefault());
        String date = df.format(new Date());

        TaskExecutionReport.append(task_id, state);
        File TestFile = new File("./here/task_execution_log.xml");
        StringBuilder text = new StringBuilder();

        try
        {
            BufferedReader br = new BufferedReader(new FileReader(TestFile));
            String line = br.readLine();
            br.close();
            String expected = "<task id=\""+task_id+"\">"+ "<lastUpdateTime>"+date+"</lastUpdateTime>"+"<state>"+state+"</state></task>";
            assertEquals(expected, line);

        }
        catch (IOException e)
        {
            //You'll need to add proper error handling here
        }
    }

    @Test
    void appendEscapesTaskId() throws IOException
    {
        new TaskExecutionReport("./here");
        TaskExecutionReport.append("a\"><evil/><task id=\"b", "completed");

        BufferedReader br = new BufferedReader(new FileReader("./here/task_execution_log.xml"));
        String line = br.readLine();
        assertNull(br.readLine());
        br.close();

        assertTrue(line.startsWith("<task id=\"a&quot;&gt;&lt;evil/&gt;&lt;task id=&quot;b\">"));
        assertTrue(line.endsWith("<state>completed</state></task>"));
    }

    /**
     * Before the constructor ran (e.g. right after an update of the launcher)
     * append must not crash and writes nothing.
     */
    @Test
    void appendWithoutLogFileDoesNothing() throws Exception
    {
        setLogFile(null);

        TaskExecutionReport.append("t1", "completed");

        assertFalse(new File("./here/task_execution_log.xml").exists());
    }

    @Test
    void appendAddsOneLinePerTask() throws IOException
    {
        new TaskExecutionReport("./here");
        TaskExecutionReport.append("t1", "completed");
        TaskExecutionReport.append("t2", "aborted");

        BufferedReader br = new BufferedReader(new FileReader("./here/task_execution_log.xml"));
        assertTrue(br.readLine().startsWith("<task id=\"t1\">"));
        assertTrue(br.readLine().startsWith("<task id=\"t2\">"));
        assertNull(br.readLine());
        br.close();
    }

    @Test
    void constructorKeepsExistingLog() throws IOException
    {
        new TaskExecutionReport("./here");
        TaskExecutionReport.append("t1", "completed");

        new TaskExecutionReport("./here");
        TaskExecutionReport.append("t2", "completed");

        BufferedReader br = new BufferedReader(new FileReader("./here/task_execution_log.xml"));
        assertTrue(br.readLine().contains("t1"));
        assertTrue(br.readLine().contains("t2"));
        br.close();
    }

    @Test
    void constructorWithUncreatableDirLogs(@TempDir File tmp) throws IOException
    {
        File blocker = new File(tmp, "file");
        assertTrue(blocker.createNewFile());

        try (MockedStatic<Log> log = mockStatic(Log.class))
        {
            new TaskExecutionReport(blocker.getAbsolutePath() + "/logs");

            log.verify(() -> Log.wtf(org.mockito.ArgumentMatchers.eq("TaskExecutionReport"), org.mockito.ArgumentMatchers.contains("could not be created")), times(1));
        }
    }

    @Test
    void appendToUnwritableLogFileLogs(@TempDir File tmp) throws Exception
    {
        // a directory cannot be opened for writing
        setLogFile(tmp);

        try (MockedStatic<Log> log = mockStatic(Log.class))
        {
            TaskExecutionReport.append("t1", "completed");

            log.verify(() -> Log.wtf(org.mockito.ArgumentMatchers.eq("TaskExecutionReport"), org.mockito.ArgumentMatchers.anyString()), times(1));
        }
        finally
        {
            setLogFile(null);
        }
    }

    private static void setLogFile(File file) throws Exception
    {
        Field field = TaskExecutionReport.class.getDeclaredField("LogFile");
        field.setAccessible(true);
        field.set(null, file);
    }

    @Test
    void escapeXmlSpecialCharacters()
    {
        assertEquals("&amp;&lt;&gt;&quot;&apos;", TaskExecutionReport.escapeXml("&<>\"'"));
    }

    @Test
    void escapeXmlLineBreaks()
    {
        assertEquals("a&#10;b&#13;c&#9;d", TaskExecutionReport.escapeXml("a\nb\rc\td"));
    }

    @Test
    void escapeXmlDropsInvalidCharacters()
    {
        assertEquals("ab", TaskExecutionReport.escapeXml("a\u0000\u0001\uFFFEb"));
        assertEquals("ab", TaskExecutionReport.escapeXml("a\uD800b"));   // lone high surrogate
        assertEquals("ab", TaskExecutionReport.escapeXml("a\uDC00b"));   // lone low surrogate
    }

    @Test
    void escapeXmlKeepsValidCharacters()
    {
        assertEquals("via usb äöü € \uD83D\uDE00", TaskExecutionReport.escapeXml("via usb äöü € \uD83D\uDE00"));
    }

    @Test
    void escapeXmlNull()
    {
        assertEquals("", TaskExecutionReport.escapeXml(null));
    }
}