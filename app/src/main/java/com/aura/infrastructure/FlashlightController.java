package com.aura.infrastructure;

import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.util.Log;

public class FlashlightController {
    private static final String TAG = "FlashlightController";
    private final CameraManager cameraManager;
    private String rearCameraId = null;
    private boolean isTorchOn = false;

    public FlashlightController(Context context) {
        if (context != null) {
            this.cameraManager = (CameraManager) context.getApplicationContext().getSystemService(Context.CAMERA_SERVICE);
            initCameraId();
        } else {
            this.cameraManager = null;
        }
    }

    private void initCameraId() {
        if (cameraManager == null) return;
        try {
            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics chars = cameraManager.getCameraCharacteristics(id);
                Boolean flashAvailable = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
                if (Boolean.TRUE.equals(flashAvailable) &&
                        facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    rearCameraId = id;
                    break;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "FLASHLIGHT_INIT_FAILURE");
        }
    }

    public synchronized boolean setTorch(boolean enable) {
        if (cameraManager == null) return false;
        if (rearCameraId == null) {
            initCameraId();
            if (rearCameraId == null) return false;
        }
        try {
            cameraManager.setTorchMode(rearCameraId, enable);
            isTorchOn = enable;
            return true;
        } catch (Exception e) {
            Log.w(TAG, "FLASHLIGHT_TOGGLE_FAILURE");
            return false;
        }
    }

    public synchronized boolean isTorchOn() {
        return isTorchOn;
    }
}
