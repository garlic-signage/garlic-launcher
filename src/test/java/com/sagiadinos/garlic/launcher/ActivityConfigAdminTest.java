package com.sagiadinos.garlic.launcher;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.TextView;

import com.sagiadinos.garlic.launcher.configuration.MainConfiguration;
import com.sagiadinos.garlic.launcher.configuration.PasswordHasher;
import com.sagiadinos.garlic.launcher.configuration.SharedPreferencesModel;
import com.sagiadinos.garlic.launcher.testutil.ShadowRootChecker;
import com.sagiadinos.garlic.launcher.testutil.ShadowShellExecute;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * The admin configuration of the launcher on a real (Robolectric) Android
 * of the lowest supported version and a current one.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {25, 35})
public class ActivityConfigAdminTest
{
    MainConfiguration MyMainConfiguration;

    @Before
    public void setUp()
    {
        // Robolectric does not call the resetters of own shadows
        ShadowShellExecute.reset();
        ShadowRootChecker.reset();
        Context ctx         = RuntimeEnvironment.getApplication();
        MyMainConfiguration = new MainConfiguration(new SharedPreferencesModel(ctx));
    }

    @Test
    public void showsStoredConfiguration()
    {
        MyMainConfiguration.storeSmilIndex("https://example.com/index.smil");
        MyMainConfiguration.storePlayerStartDelay(30);
        MyMainConfiguration.toogleRebootAfterInstall(true);
        MyMainConfiguration.toggleNoPlayerStartDelayAfterBoot(true);

        ActivityConfigAdmin activity = start().get();

        assertEquals("https://example.com/index.smil", text(activity, R.id.editContentUrl));
        assertEquals("< 30s Player start delay", text(activity, R.id.editPlayerStartDelay));
        assertTrue(checkBox(activity, R.id.cbRebootAfterInstall).isChecked());
        assertTrue(checkBox(activity, R.id.cbNoPlayerStartDelayAfterBoot).isChecked());
        assertFalse(checkBox(activity, R.id.cbActiveServicePassword).isChecked());
        assertEquals(View.GONE, activity.findViewById(R.id.editServicePassword).getVisibility());
    }

    @Test
    public void ownBackButtonIsHiddenOnNotRootedDevice()
    {
        ActivityConfigAdmin activity = start().get();

        CheckBox cbOwnBackButton = checkBox(activity, R.id.cbOwnBackButton);
        assertEquals(View.GONE, cbOwnBackButton.getVisibility());
        assertFalse(cbOwnBackButton.isEnabled());
    }

    @Test
    public void activeServicePasswordShowsPasswordField()
    {
        MyMainConfiguration.toggleActiveServicePassword(true);

        ActivityConfigAdmin activity = start().get();

        assertTrue(checkBox(activity, R.id.cbActiveServicePassword).isChecked());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.editServicePassword).getVisibility());
    }

    @Test
    public void saveAndCloseStoresValuesAndFinishes()
    {
        ActivityConfigAdmin activity = start().get();

        ((EditText) activity.findViewById(R.id.editContentUrl)).setText("  https://example.com/new.smil  ");
        checkBox(activity, R.id.cbRebootAfterInstall).setChecked(true);
        checkBox(activity, R.id.cbNoPlayerStartDelayAfterBoot).setChecked(true);
        activity.onValueChange(null, 42, 0);

        activity.findViewById(R.id.btSave).performClick();

        assertEquals("https://example.com/new.smil", MyMainConfiguration.getSmilIndex());
        assertTrue(MyMainConfiguration.hasRebootAfterInstall());
        assertTrue(MyMainConfiguration.hasNoPlayerStartDelayAfterBoot());
        assertEquals(42, MyMainConfiguration.getPlayerStartDelay());
        assertFalse(MyMainConfiguration.hasActiveServicePassword());
        assertTrue(activity.isFinishing());
    }

    @Test
    public void numberPickerUpdatesDelayText()
    {
        ActivityConfigAdmin activity = start().get();

        activity.onValueChange(null, 12, 0);

        assertEquals("< 12s Player start delay", text(activity, R.id.editPlayerStartDelay));
    }

    @Test
    public void playerStartDelayBelowFiveIsStoredAsFive()
    {
        ActivityConfigAdmin activity = start().get();

        activity.onValueChange(null, 2, 0);
        activity.findViewById(R.id.btSave).performClick();

        assertEquals(5, MyMainConfiguration.getPlayerStartDelay());
    }

    @Test
    public void newServicePasswordIsStoredAsHash()
    {
        ActivityConfigAdmin activity = start().get();

        CheckBox cbActiveServicePassword = checkBox(activity, R.id.cbActiveServicePassword);
        cbActiveServicePassword.performClick();
        assertEquals(View.VISIBLE, activity.findViewById(R.id.editServicePassword).getVisibility());
        ((EditText) activity.findViewById(R.id.editServicePassword)).setText("secret");

        activity.findViewById(R.id.btSave).performClick();

        assertTrue(MyMainConfiguration.hasActiveServicePassword());
        assertTrue(MyMainConfiguration.compareServicePassword("secret", new PasswordHasher()));
        assertFalse(MyMainConfiguration.compareServicePassword("wrong", new PasswordHasher()));
        assertTrue(activity.isFinishing());
    }

    @Test
    public void emptyNewServicePasswordShowsErrorAndStaysOpen()
    {
        ActivityConfigAdmin activity = start().get();

        checkBox(activity, R.id.cbActiveServicePassword).performClick();
        EditText editServicePassword = activity.findViewById(R.id.editServicePassword);
        editServicePassword.setText("x");
        editServicePassword.setText("");

        activity.findViewById(R.id.btSave).performClick();

        assertEquals("There is no password set!", text(activity, R.id.textViewInformation));
        assertEquals(View.VISIBLE, activity.findViewById(R.id.textViewInformation).getVisibility());
        assertFalse(MyMainConfiguration.hasActiveServicePassword());
        assertFalse(activity.isFinishing());
    }

    @Test
    public void unchangedServicePasswordIsKept()
    {
        MyMainConfiguration.toggleActiveServicePassword(true);
        MyMainConfiguration.setServicePassword("secret", new PasswordHasher());

        ActivityConfigAdmin activity = start().get();
        activity.findViewById(R.id.btSave).performClick();

        assertTrue(MyMainConfiguration.hasActiveServicePassword());
        assertTrue(MyMainConfiguration.compareServicePassword("secret", new PasswordHasher()));
        assertTrue(activity.isFinishing());
    }

    @Test
    public void closeActivityDoesNotStore()
    {
        MyMainConfiguration.storeSmilIndex("https://example.com/index.smil");
        ActivityConfigAdmin activity = start().get();

        ((EditText) activity.findViewById(R.id.editContentUrl)).setText("https://example.com/other.smil");
        activity.closeActivity(null);

        assertEquals("https://example.com/index.smil", MyMainConfiguration.getSmilIndex());
        assertTrue(activity.isFinishing());
    }

    @Test
    public void numberPickerDialogStartsWithStoredDelayAndStoresChoice()
    {
        MyMainConfiguration.storePlayerStartDelay(30);
        ActivityConfigAdmin activity = start().get();

        activity.findViewById(R.id.editPlayerStartDelay).performClick();
        shadowOf(Looper.getMainLooper()).idle();

        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(dialog);
        NumberPicker picker = (NumberPicker) shadowOf(dialog).getView();
        assertEquals(5, picker.getMinValue());
        assertEquals(300, picker.getMaxValue());
        assertEquals(30, picker.getValue());

        picker.setValue(90);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("< 90s Player start delay", text(activity, R.id.editPlayerStartDelay));

        activity.findViewById(R.id.btSave).performClick();
        assertEquals(90, MyMainConfiguration.getPlayerStartDelay());
    }

    @Test
    public void numberPickerDialogCancelKeepsDelay()
    {
        MyMainConfiguration.storePlayerStartDelay(30);
        ActivityConfigAdmin activity = start().get();

        activity.findViewById(R.id.editPlayerStartDelay).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        ((NumberPicker) shadowOf(dialog).getView()).setValue(90);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals("< 30s Player start delay", text(activity, R.id.editPlayerStartDelay));
    }

    private ActivityController<ActivityConfigAdmin> start()
    {
        return Robolectric.buildActivity(ActivityConfigAdmin.class).setup();
    }

    private String text(ActivityConfigAdmin activity, int id)
    {
        return ((TextView) activity.findViewById(id)).getText().toString();
    }

    private CheckBox checkBox(ActivityConfigAdmin activity, int id)
    {
        return activity.findViewById(id);
    }
}
