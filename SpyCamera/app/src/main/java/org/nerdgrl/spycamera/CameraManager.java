package org.nerdgrl.spycamera;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class CameraManager implements Camera.ErrorCallback, Camera.PreviewCallback, Camera.AutoFocusCallback, Camera.PictureCallback {

    public static CameraManager getInstance(Context context) {
        if(mManager == null) mManager = new CameraManager(context);
        return mManager;
    }

    public void takePhoto() {
        if(isBackCameraAvailable() && !isWorking) {
            initCamera();
        }
    }

    private void initCamera() {
        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... voids) {
                try {
                    isWorking = true;
                    mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);
                } catch (RuntimeException e) {
                    Log.e(TAG, "Cannot open camera");
                    e.printStackTrace();
                    isWorking = false;
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                try {
                    if(mCamera != null) {
                        mSurface = new SurfaceTexture(123);
                        mCamera.setPreviewTexture(mSurface);

                        Camera.Parameters params = mCamera.getParameters();
                        int angle = 270;//getCameraRotationAngle(Camera.CameraInfo.CAMERA_FACING_BACK, mCamera);
                        params.setRotation(angle);

                        if (autoFocusSupported(mCamera)) {
                            params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                        } else {
                            Log.w(TAG, "Autofocus is not supported");
                        }

                        mCamera.setParameters(params);
                        mCamera.setPreviewCallback(CameraManager.this);
                        mCamera.setErrorCallback(CameraManager.this);
                        mCamera.startPreview();
                        muteSound();
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Cannot set preview for the front camera");
                    e.printStackTrace();
                    releaseCamera();
                }
            }

        }.execute();
    }

    private void releaseCamera() {
        if(mCamera != null) {
            mCamera.release();
            mSurface.release();
            mCamera = null;
            mSurface = null;
        }
        unmuteSound();
        isWorking = false;
    }

    @Override
    public void onPreviewFrame(byte[] bytes, Camera camera) {
        try {
            if(autoFocusSupported(camera)) {
                mCamera.autoFocus(this);
            } else {
                camera.setPreviewCallback(null);
                camera.takePicture(null, null, this);
            }
        } catch (Exception e) {
            Log.e(TAG, "Camera error while taking picture");
            e.printStackTrace();
            releaseCamera();
        }
    }

    @Override
    public void onAutoFocus(boolean success, Camera camera) {
        if(camera != null) {
            try {
                camera.takePicture(null, null, this);
                mCamera.autoFocus(null);
            } catch (Exception e) {
                e.printStackTrace();
                releaseCamera();
            }
        }
    }

    @Override
    public void onPictureTaken(byte[] bytes, Camera camera) {
        savePicture(bytes);
        releaseCamera();
    }

    @Override
    public void onError(int error, Camera camera) {
        switch (error) {
            case Camera.CAMERA_ERROR_SERVER_DIED:
                Log.e(TAG, "Camera error: Media server died");
                break;
            case Camera.CAMERA_ERROR_UNKNOWN:
                Log.e(TAG, "Camera error: Unknown");
                break;
            case Camera.CAMERA_ERROR_EVICTED:
                Log.e(TAG, "Camera error: Camera was disconnected due to use by higher priority user");
                break;
            default:
                Log.e(TAG, "Camera error: no such error id (" + error + ")");
                break;
        }
    }

    private CameraManager(Context context) {
        mContext = context;
    }

    private boolean isBackCameraAvailable() {
        boolean result = false;
        if(mContext != null && mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            int numberOfCameras = Camera.getNumberOfCameras();
            for (int i = 0; i < numberOfCameras; i++) {
                Camera.CameraInfo info = new Camera.CameraInfo();
                Camera.getCameraInfo(i, info);
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    result = true;
                    break;
                }
            }
        }
        return result;
    }

    private boolean autoFocusSupported(Camera camera) {
        if(camera != null) {
            Camera.Parameters params = camera.getParameters();
            List<String> focusModes = params.getSupportedFocusModes();
            if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                return true;
            }
        }
        return false;
    }

    private void muteSound() {
        if(mContext != null) {
            AudioManager mgr = (AudioManager)mContext.getSystemService(Context.AUDIO_SERVICE);
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mgr.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_MUTE, 0);
            } else {
                mgr.setStreamMute(AudioManager.STREAM_SYSTEM, true);
            }
        }
    }

    private void unmuteSound() {
        if(mContext != null) {
            AudioManager mgr = (AudioManager)mContext.getSystemService(Context.AUDIO_SERVICE);
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mgr.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0);
            } else {
                mgr.setStreamMute(AudioManager.STREAM_SYSTEM, false);
            }
        }
    }

    public int getCameraRotationAngle(int cameraId, android.hardware.Camera camera) {
        int result = 270;
        if(camera != null && mContext != null) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(cameraId, info);
            int rotation = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
            int degrees = getRotationAngle(rotation);

            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                result = (info.orientation + degrees) % 360;
                result = (360 - result) % 360; //compensate mirroring
                Log.d(TAG, "Screen rotation: " + degrees +
                        "; camera orientation: " + info.orientation +
                        "; rotate to angle: " + result);
            }
        }
        return result;
    }

    private int getRotationAngle(int rotation) {
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }
        return degrees;
    }

    private String savePicture(byte[] bytes) {

        String filepath = null;
        try {
            File pictureFileDir = getDir();
            if (bytes == null) {
                Log.e(TAG, "Can't save image - no data");
                return null;
            }
            if (!pictureFileDir.exists() && !pictureFileDir.mkdirs()) {
                Log.e(TAG, "Can't create directory to save image.");
                return null;
            }

            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddhhmmss");
            String date = dateFormat.format(new Date());
            String photoFile = "spyapp_" + date + ".jpg";

            filepath = pictureFileDir.getPath() + File.separator + photoFile;

            File pictureFile = new File(filepath);
            FileOutputStream fos = new FileOutputStream(pictureFile);
            fos.write(bytes);
            fos.close();
            Log.d(TAG, "New image was saved:" + photoFile);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return filepath;
    }

    public static File getDir() {
        File sdDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        return new File(sdDir, "SpyApp");
    }

    private static final String TAG = CameraManager.class.getSimpleName();

    private static CameraManager mManager;

    private Context mContext;
    private Camera mCamera;
    private SurfaceTexture mSurface;
    private boolean isWorking = false;

}
