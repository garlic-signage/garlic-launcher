package com.sagiadinos.garlic.launcher.helper;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * From Android 9 the signers come from SigningInfo (GET_SIGNING_CERTIFICATES).
 * This is the path of most devices; SignatureVerifierTest covers the older one.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 35})
public class SignatureVerifierSdkTest
{
    private static final String LAUNCHER = DeviceOwner.LAUNCHER_PACKAGE_NAME;
    private static final String APK_PATH = "/sdcard/garlic-player/cache/garlic-player.apk";

    PackageManager PackageManagerMocked;
    SignatureVerifier MyTestClass;

    @Before
    public void setUp()
    {
        PackageManagerMocked = mock(PackageManager.class);
        MyTestClass          = new SignatureVerifier(PackageManagerMocked, LAUNCHER);
    }

    @Test
    public void sameSigner() throws PackageManager.NameNotFoundException
    {
        mockInstalled(createInfo(new byte[]{1, 2, 3}));
        mockArchive(createInfo(new byte[]{1, 2, 3}));

        assertTrue(MyTestClass.isSignedLikeReference(APK_PATH));
    }

    @Test
    public void otherSigner() throws PackageManager.NameNotFoundException
    {
        mockInstalled(createInfo(new byte[]{1, 2, 3}));
        mockArchive(createInfo(new byte[]{4, 5, 6}));

        assertFalse(MyTestClass.isSignedLikeReference(APK_PATH));
    }

    @Test
    public void archiveWithoutSigningInfo() throws PackageManager.NameNotFoundException
    {
        mockInstalled(createInfo(new byte[]{1, 2, 3}));
        PackageInfo archive = new PackageInfo();
        archive.signingInfo = null;
        mockArchive(archive);

        assertFalse(MyTestClass.isSignedLikeReference(APK_PATH));
    }

    @Test
    public void installedWithoutSigningInfoRejects() throws PackageManager.NameNotFoundException
    {
        PackageInfo installed = new PackageInfo();
        installed.signingInfo = null;
        mockInstalled(installed);
        mockArchive(createInfo(new byte[]{1, 2, 3}));

        assertFalse(MyTestClass.isSignedLikeReference(APK_PATH));
    }

    @Test
    public void signersNullRejects() throws PackageManager.NameNotFoundException
    {
        mockInstalled(createInfo(new byte[]{1, 2, 3}));
        PackageInfo archive = new PackageInfo();
        archive.signingInfo = mock(SigningInfo.class);
        when(archive.signingInfo.getApkContentsSigners()).thenReturn(null);
        mockArchive(archive);

        assertFalse(MyTestClass.isSignedLikeReference(APK_PATH));
    }

    /**
     * The old signatures field is ignored from Android 9.
     */
    @Test
    public void legacySignaturesFieldIsIgnored() throws PackageManager.NameNotFoundException
    {
        mockInstalled(createInfo(new byte[]{1, 2, 3}));
        PackageInfo archive = new PackageInfo();
        archive.signatures  = new Signature[]{new Signature(new byte[]{1, 2, 3})};
        archive.signingInfo = null;
        mockArchive(archive);

        assertFalse(MyTestClass.isSignedLikeReference(APK_PATH));
    }

    private void mockInstalled(PackageInfo info) throws PackageManager.NameNotFoundException
    {
        when(PackageManagerMocked.getPackageInfo(LAUNCHER, PackageManager.GET_SIGNING_CERTIFICATES)).thenReturn(info);
    }

    private void mockArchive(PackageInfo info)
    {
        when(PackageManagerMocked.getPackageArchiveInfo(APK_PATH, PackageManager.GET_SIGNING_CERTIFICATES)).thenReturn(info);
    }

    private PackageInfo createInfo(byte[]... certificates)
    {
        Signature[] signatures = new Signature[certificates.length];
        for (int i = 0; i < certificates.length; i++)
        {
            signatures[i] = new Signature(certificates[i]);
        }
        PackageInfo info = new PackageInfo();
        info.signingInfo = mock(SigningInfo.class);
        when(info.signingInfo.getApkContentsSigners()).thenReturn(signatures);
        return info;
    }
}
