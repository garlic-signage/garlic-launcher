/*
 garlic-launcher: Android Launcher for the Digital Signage Software garlic-player

 Copyright (C) 2026 Nikolaos Sagiadinos <ns@smil-control.com>
 This file is part of the garlic-launcher source code

 This program is free software: you can redistribute it and/or  modify
 it under the terms of the GNU Affero General Public License, version 3,
 as published by the Free Software Foundation.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sagiadinos.garlic.launcher.helper;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;

/**
 * Checks that an APK file is signed with the same certificate(s)
 * as an installed package, normally the launcher itself.
 *
 * Launcher and player share one signing key, so this accepts updates of
 * the player and the launcher and rejects every other APK.
 */
public class SignatureVerifier
{
    private final PackageManager MyPackageManager;
    private final String         reference_package;

    public SignatureVerifier(PackageManager pm, String reference_package_name)
    {
        MyPackageManager  = pm;
        reference_package = reference_package_name;
    }

    public boolean isSignedLikeReference(String apk_path)
    {
        Set<String> reference = determineInstalledSigners();
        if (reference.isEmpty())
            return false;

        return reference.equals(determineArchiveSigners(apk_path));
    }

    private Set<String> determineInstalledSigners()
    {
        try
        {
            return extractSigners(MyPackageManager.getPackageInfo(reference_package, getSignatureFlag()));
        }
        catch (PackageManager.NameNotFoundException e)
        {
            return new HashSet<>();
        }
    }

    private Set<String> determineArchiveSigners(String apk_path)
    {
        return extractSigners(MyPackageManager.getPackageArchiveInfo(apk_path, getSignatureFlag()));
    }

    @SuppressWarnings("deprecation")
    private int getSignatureFlag()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            return PackageManager.GET_SIGNING_CERTIFICATES;

        return PackageManager.GET_SIGNATURES;
    }

    @SuppressWarnings("deprecation")
    private Set<String> extractSigners(PackageInfo info)
    {
        Set<String> signers = new HashSet<>();
        if (info == null)
            return signers;

        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        {
            if (info.signingInfo == null)
                return signers;
            // returns the current signer also after a key rotation
            signatures = info.signingInfo.getApkContentsSigners();
        }
        else
        {
            signatures = info.signatures;
        }

        if (signatures == null)
            return signers;

        for (Signature signature : signatures)
        {
            signers.add(sha256(signature.toByteArray()));
        }
        return signers;
    }

    private static String sha256(byte[] data)
    {
        try
        {
            byte[] digest      = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder hex  = new StringBuilder();
            for (byte b : digest)
            {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException(e); // SHA-256 is mandatory on every JVM / Android
        }
    }
}
