package com.sagiadinos.garlic.launcher.helper;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Build.VERSION.SDK_INT is 0 in local unit tests,
 * so the GET_SIGNATURES branch (below Android P) is covered here.
 */
@SuppressWarnings("deprecation")
class SignatureVerifierTest
{
    private static final String LAUNCHER = DeviceOwner.LAUNCHER_PACKAGE_NAME;
    private static final String APK_PATH = "/sdcard/garlic-player/cache/garlic-player.apk";

    PackageManager PackageManagerMocked;

    @AfterEach
    void tearDown()
    {
        PackageManagerMocked = null;
    }

    @Test
    void sameSigner() throws PackageManager.NameNotFoundException
    {
        SignatureVerifier MyTestClass = createClass();
        mockInstalled(createInfo(new byte[]{1, 2, 3}));
        mockArchive(createInfo(new byte[]{1, 2, 3}));

        assertTrue(MyTestClass.isSignedLikeReference(APK_PATH));
    }

    @Test
    void otherSigner() throws PackageManager.NameNotFoundException
    {
        SignatureVerifier MyTestClass = createClass();
        mockInstalled(createInfo(new byte[]{1, 2, 3}));
        mockArchive(createInfo(new byte[]{4, 5, 6}));

        assertFalse(MyTestClass.isSignedLikeReference(APK_PATH));
    }

    @Test
    void additionalSignerInArchive() throws PackageManager.NameNotFoundException
    {
        SignatureVerifier MyTestClass = createClass();
        mockInstalled(createInfo(new byte[]{1, 2, 3}));
        mockArchive(createInfo(new byte[]{1, 2, 3}, new byte[]{4, 5, 6}));

        assertFalse(MyTestClass.isSignedLikeReference(APK_PATH));
    }

    @Test
    void archiveNotParsable() throws PackageManager.NameNotFoundException
    {
        SignatureVerifier MyTestClass = createClass();
        mockInstalled(createInfo(new byte[]{1, 2, 3}));
        mockArchive(null);

        assertFalse(MyTestClass.isSignedLikeReference(APK_PATH));
    }

    @Test
    void archiveWithoutSignatures() throws PackageManager.NameNotFoundException
    {
        SignatureVerifier MyTestClass = createClass();
        mockInstalled(createInfo(new byte[]{1, 2, 3}));
        PackageInfo unsigned = mock(PackageInfo.class);
        unsigned.signatures  = null;
        mockArchive(unsigned);

        assertFalse(MyTestClass.isSignedLikeReference(APK_PATH));
    }

    @Test
    void referenceNotInstalled() throws PackageManager.NameNotFoundException
    {
        SignatureVerifier MyTestClass = createClass();
        when(PackageManagerMocked.getPackageInfo(LAUNCHER, PackageManager.GET_SIGNATURES))
                .thenThrow(new PackageManager.NameNotFoundException());

        assertFalse(MyTestClass.isSignedLikeReference(APK_PATH));
        verify(PackageManagerMocked, never()).getPackageArchiveInfo(anyString(), anyInt());
    }

    @Test
    void referenceWithoutSignaturesRejectsUnsignedArchive() throws PackageManager.NameNotFoundException
    {
        SignatureVerifier MyTestClass = createClass();
        PackageInfo unsigned = mock(PackageInfo.class);
        unsigned.signatures  = new Signature[0];
        mockInstalled(unsigned);
        mockArchive(unsigned);

        assertFalse(MyTestClass.isSignedLikeReference(APK_PATH));
    }

    void mockInstalled(PackageInfo info) throws PackageManager.NameNotFoundException
    {
        when(PackageManagerMocked.getPackageInfo(LAUNCHER, PackageManager.GET_SIGNATURES)).thenReturn(info);
    }

    void mockArchive(PackageInfo info)
    {
        when(PackageManagerMocked.getPackageArchiveInfo(APK_PATH, PackageManager.GET_SIGNATURES)).thenReturn(info);
    }

    PackageInfo createInfo(byte[]... certificates)
    {
        PackageInfo info = mock(PackageInfo.class);
        info.signatures  = new Signature[certificates.length];
        for (int i = 0; i < certificates.length; i++)
        {
            Signature signature = mock(Signature.class);
            when(signature.toByteArray()).thenReturn(certificates[i]);
            info.signatures[i] = signature;
        }
        return info;
    }

    SignatureVerifier createClass()
    {
        PackageManagerMocked = mock(PackageManager.class);
        return new SignatureVerifier(PackageManagerMocked, LAUNCHER);
    }
}
