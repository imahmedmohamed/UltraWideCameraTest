package com.example.ultrawidetest;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.*;
import android.os.Build;
import android.os.Bundle;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Arrays;

public class MainActivity extends Activity {
    private TextureView preview;
    private TextView status;
    private CameraDevice camera;
    private CameraCaptureSession session;
    private CameraCharacteristics chars;
    private CaptureRequest.Builder builder;
    private CameraManager manager;
    private String selectedId;
    private float zoom = 0.6f;
    private final StringBuilder log = new StringBuilder();
    private final CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override public void onCaptureCompleted(CameraCaptureSession s, CaptureRequest r, TotalCaptureResult result) {
            String active = null;
            try { active = result.get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID); } catch (Exception ignored) {}
            Float actualZoom = null;
            if (Build.VERSION.SDK_INT >= 30) { try { actualZoom = result.get(CaptureResult.CONTROL_ZOOM_RATIO); } catch (Exception ignored) {} }
            final String a = active == null ? "(not reported / camera may not expose it)" : active;
            final String z = actualZoom == null ? "unknown" : String.format("%.2fx", actualZoom);
            runOnUiThread(() -> status.setText(log + "\n\nLIVE RESULT\nRequested zoom: " + zoom + "x\nReported zoom: " + z + "\nActive physical ID: " + a));
        }
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        manager = (CameraManager)getSystemService(CAMERA_SERVICE);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(12,12,12,12); root.setBackgroundColor(0xFF101820);
        preview = new TextureView(this); root.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout buttons = new LinearLayout(this); buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button wide = new Button(this); wide.setText("ULTRA WIDE 0.6x"); wide.setOnClickListener(v -> setZoom(0.6f));
        Button one = new Button(this); one.setText("1.0x"); one.setOnClickListener(v -> setZoom(1.0f));
        Button list = new Button(this); list.setText("CAMERA INFO"); list.setOnClickListener(v -> showCameraInfo());
        buttons.addView(wide, new LinearLayout.LayoutParams(0,-2,1)); buttons.addView(one, new LinearLayout.LayoutParams(0,-2,1)); buttons.addView(list, new LinearLayout.LayoutParams(0,-2,1)); root.addView(buttons);
        ScrollView sc = new ScrollView(this); status = new TextView(this); status.setTextColor(0xFFFFFFFF); status.setTextSize(13); status.setPadding(8,8,8,8); sc.addView(status); root.addView(sc, new LinearLayout.LayoutParams(-1, 220));
        setContentView(root);
        preview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) { startCamera(); }
            public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {}
            public boolean onSurfaceTextureDestroyed(SurfaceTexture st) { closeCamera(); return true; }
            public void onSurfaceTextureUpdated(SurfaceTexture st) {}
        });
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.CAMERA}, 10);
        else if (preview.isAvailable()) startCamera();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 10 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startCamera();
        else status.setText("Camera permission is required.");
    }

    private void startCamera() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED || !preview.isAvailable()) return;
        closeCamera(); log.setLength(0);
        try {
            String[] ids = manager.getCameraIdList();
            selectedId = null;
            // Prefer the logical rear camera ID 0, as indicated by the supplied camera dump.
            for (String id : ids) {
                CameraCharacteristics c = manager.getCameraCharacteristics(id);
                Integer facing = c.get(CameraCharacteristics.LENS_FACING);
                if ("0".equals(id) && facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) { selectedId = id; break; }
            }
            if (selectedId == null) for (String id : ids) { Integer f=manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING); if (f != null && f == CameraCharacteristics.LENS_FACING_BACK) { selectedId=id; break; } }
            if (selectedId == null) { status.setText("No rear camera found. IDs: " + Arrays.toString(ids)); return; }
            chars = manager.getCameraCharacteristics(selectedId);
            log.append("Selected camera ID: ").append(selectedId).append("\nAll camera IDs: ").append(Arrays.toString(ids)).append("\n");
            Range<Float> range = null;
            if (Build.VERSION.SDK_INT >= 30) range = chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
            log.append("Zoom ratio range: ").append(range == null ? "not available on this Android/API" : range.toString()).append("\n");
            float[] focal = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
            log.append("Logical focal lengths: ").append(focal == null ? "unknown" : Arrays.toString(focal)).append("\n");
            int[] caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
            log.append("Logical multi-camera capability: ").append(caps != null && Arrays.stream(caps).anyMatch(x -> x == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA)).append("\n");
            manager.openCamera(selectedId, new CameraDevice.StateCallback() {
                public void onOpened(CameraDevice c) { camera=c; createPreview(); }
                public void onDisconnected(CameraDevice c) { c.close(); camera=null; }
                public void onError(CameraDevice c, int error) { c.close(); camera=null; runOnUiThread(() -> status.setText(log + "\nCamera error: " + error)); }
            }, null);
        } catch (Exception e) { status.setText("Camera start failed: " + e); }
    }

    private void createPreview() {
        try {
            SurfaceTexture st = preview.getSurfaceTexture(); if (st == null) return;
            st.setDefaultBufferSize(1280, 720); Surface surface = new Surface(st);
            builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW); builder.addTarget(surface);
            builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            applyZoom(0.6f);
            camera.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                public void onConfigured(CameraCaptureSession s) { session=s; try { session.setRepeatingRequest(builder.build(), captureCallback, null); } catch (Exception e) { status.setText(log + "\nPreview request failed: " + e); } }
                public void onConfigureFailed(CameraCaptureSession s) { status.setText(log + "\nPreview session configuration failed."); }
            }, null);
        } catch (Exception e) { status.setText(log + "\nPreview failed: " + e); }
    }

    private void setZoom(float value) {
        zoom = value;
        if (builder == null || session == null) { status.setText(log + "\nCamera is not ready yet."); return; }
        try { applyZoom(value); session.setRepeatingRequest(builder.build(), captureCallback, null); }
        catch (Exception e) { status.setText(log + "\nZoom request failed: " + e); }
    }

    private void applyZoom(float value) {
        if (Build.VERSION.SDK_INT >= 30) {
            Range<Float> range = chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
            if (range != null) { float safe = Math.max(range.getLower(), Math.min(value, range.getUpper())); builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, safe); zoom=safe; return; }
        }
        android.graphics.Rect active = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        if (active != null) {
            // Legacy crop fallback: cannot request below 1x, so report that limitation instead of faking 0.6x.
            float cropZoom = Math.max(1.0f, value); int cw=(int)(active.width()/cropZoom), ch=(int)(active.height()/cropZoom); int left=active.centerX()-cw/2, top=active.centerY()-ch/2;
            builder.set(CaptureRequest.SCALER_CROP_REGION, new android.graphics.Rect(left,top,left+cw,top+ch));
            if (value < 1f) runOnUiThread(() -> status.setText(log + "\nThis Android version does not expose zoomRatio. Legacy crop cannot request 0.6x; use Android 11+ for this test."));
        }
    }

    private void showCameraInfo() {
        try {
            StringBuilder s = new StringBuilder("Camera IDs and physical IDs\n");
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics c = manager.getCameraCharacteristics(id);
                Integer facing=c.get(CameraCharacteristics.LENS_FACING); float[] f=c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                s.append("ID ").append(id).append(" facing=").append(facing).append(" focal=").append(f==null?"?":Arrays.toString(f));
                if (Build.VERSION.SDK_INT >= 28) { java.util.Set<String> p=c.getPhysicalCameraIds(); if (p != null && !p.isEmpty()) s.append(" physical=").append(p); }
                s.append("\n");
            }
            status.setText(s.toString());
        } catch(Exception e) { status.setText("Info error: " + e); }
    }

    private void closeCamera() { try { if(session!=null) { session.close(); session=null; } if(camera!=null) { camera.close(); camera=null; } builder=null; } catch(Exception ignored) {} }
    @Override protected void onPause() { closeCamera(); super.onPause(); }
}
