package com.sagiadinos.garlic.launcher.configuration;

import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class PasswordHasher
{
    /**
     * PBKDF2WithHmacSHA256 exists only from Android 8, the launcher runs from 7.1.1.
     * So it stays HMAC-SHA1 with more iterations.
     */
    public static final int ITERATIONS        = 100000;

    /** hashes stored before the iterations were saved alongside */
    public static final int LEGACY_ITERATIONS = 1000;

    public String generateSalt()
    {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);

        return Arrays.toString(salt);
    }

    public String hashClearTextWithSalt(String clear_text, String salt, int iterations)
    {
        try
        {
            PBEKeySpec spec      = new PBEKeySpec(clear_text.toCharArray(), salt.getBytes(), iterations, 160);
            SecretKeyFactory key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            byte[] hashed        = key.generateSecret(spec).getEncoded();
            return String.format("%x", new BigInteger(hashed));
        }
        catch (InvalidKeySpecException | NoSuchAlgorithmException e)
        {
            e.printStackTrace();
            return null;
        }
    }


}
