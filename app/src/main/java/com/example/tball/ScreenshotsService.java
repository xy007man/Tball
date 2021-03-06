package com.example.tball;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;

import pers.xiaoyu.billiards.utils.BilliardsUtils;

public class ScreenshotsService extends Service {

    public int mResultCode = 0;
    public Intent mResultData = null;
    public MediaProjectionManager mMediaProjectionManager;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private int windowWidth = 0;
    private int windowHeight = 0;
    private Handler backgroundHandler;

    public static final int INIT_SERVICE = 0x111;
    public static final int STOP_SERVICE = 0x112;
    public static final int CAPTURE_SUCCESS = 0x1113;

    private Messenger mMessenger;
    private Messenger mReplyToClient;
    private String TAG = "ScreenshotsService";
    private DisplayMetrics metrics;
    private int mScreenDensity;
    private ImageReader mImageReader;

    private String rootDirectory;

    static class MessageHandler extends Handler {
        ScreenshotsService service;

        public MessageHandler(ScreenshotsService s) {
            service = s;
        }

        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {
                case INIT_SERVICE:
                    service.initVirtualDisplay(msg);
                    break;
                case STOP_SERVICE:
                    service.stopCaptureService();
                    break;
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        rootDirectory = Environment.getExternalStorageDirectory().getPath() + "/Pictures/";

        mMessenger = new Messenger(new MessageHandler(this));
        mMediaProjectionManager = (MediaProjectionManager) getApplication().getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        WindowManager mWindowManager = (WindowManager) getApplication().getSystemService(Context.WINDOW_SERVICE);
        Point realSize = new Point();
        mWindowManager.getDefaultDisplay().getRealSize(realSize);
        // ?????????????????????????????????????????????????????????????????????x???y????????????
        windowWidth = realSize.y;
        windowHeight = realSize.x;
        metrics = new DisplayMetrics();
        mWindowManager.getDefaultDisplay().getMetrics(metrics);
        mScreenDensity = metrics.densityDpi;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        mResultCode = intent.getIntExtra("code", -1);
        mResultData = intent.getParcelableExtra("data");
        return mMessenger.getBinder();
    }


    public void stopCaptureService() {
        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }

        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
        if (mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection = null;
        }
        stopForeground(true);
    }

    /**
     * ?????????????????????
     */
    public void initVirtualDisplay(Message msg) {
        mReplyToClient = msg.replyTo;

        if (mMediaProjection == null) {
            setUpMediaProjection();
        }

        if (mImageReader == null || mVirtualDisplay == null)//??????????????????????????????  virtualDisplay
            virtualDisplay();

    }

    private void setUpMediaProjection() {
        createNotificationChannel();//????????????????????????api 29,??????29???????????????
        mMediaProjection = mMediaProjectionManager.getMediaProjection(mResultCode, mResultData);
        Log.d(TAG, "mMediaProjection ???????????????");
    }

    private void virtualDisplay() {
        mVirtualDisplay = mMediaProjection.createVirtualDisplay("screen-mirror",
                windowWidth, windowHeight, mScreenDensity, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                getSurface(), null, null);
    }


    private Surface getSurface() {
        initImageReader();
        return mImageReader.getSurface();
    }

    @SuppressLint("WrongConstant")
    private void initImageReader() {

        if (backgroundHandler == null) {
            HandlerThread backgroundThread =
                    new HandlerThread("catwindow", android.os.Process
                            .THREAD_PRIORITY_BACKGROUND);
            backgroundThread.start();
            backgroundHandler = new Handler(backgroundThread.getLooper());
        }

        mImageReader = ImageReader.newInstance(windowWidth, windowHeight, 0x1, 2); //ImageFormat.RGB_565
        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Image image = mImageReader.acquireLatestImage();
                if (image == null) {
                    return;
                }

                int width = image.getWidth();
                int height = image.getHeight();
                final Image.Plane[] planes = image.getPlanes();
                final ByteBuffer buffer = planes[0].getBuffer();
                int pixelStride = planes[0].getPixelStride();
                int rowStride = planes[0].getRowStride();
                int rowPadding = rowStride - pixelStride * width;
                Bitmap bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
                bitmap.copyPixelsFromBuffer(buffer);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height);
                image.close();
                Log.i(TAG, "??????????????????");

                if (bitmap != null) {
                    try {
                        String screenPath = rootDirectory + "screen.jpg";
                        String templatePath = rootDirectory + "circle.jpg";
                        File fileImage = new File(screenPath);
                        if (!fileImage.exists()) {
                            fileImage.createNewFile();
                            Log.i(TAG, "????????????");
                        }
                        FileOutputStream out = new FileOutputStream(fileImage);
                        if (out != null) {
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
                            out.flush();
                            out.close();
                            //??????????????????
                            Intent media = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                            Uri contentUri = Uri.fromFile(fileImage);
                            media.setData(contentUri);
                            ScreenshotsService.this.sendBroadcast(media);
                            Log.i(TAG, "????????????");

                            Point[] p = BilliardsUtils.billiardsGuide(screenPath, templatePath);

                            if (p != null && p.length > 1) {
                                int[] arr = new int[p.length * 2];
                                for (int i = 0; i < p.length; i++) {
                                    arr[i * 2] = p[i].x;
                                    arr[i * 2 + 1] = p[i].y;
                                }
                                Message msgToClient = Message.obtain(null, CAPTURE_SUCCESS);
                                Bundle bundle = new Bundle();
                                bundle.putIntArray("pos", arr);
                                msgToClient.setData(bundle);
                                mReplyToClient.send(msgToClient);
                            }
                        }
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    } finally {
                        bitmap.recycle();
                    }
                }
            }
        }, backgroundHandler);
    }

    private void createNotificationChannel() {
        Notification.Builder builder = new Notification.Builder(getApplicationContext()); //????????????Notification?????????
        builder.setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher)) // ??????????????????????????????(?????????)
                //.setContentTitle("SMI InstantView") // ??????????????????????????????
                .setSmallIcon(R.mipmap.ic_launcher) // ??????????????????????????????
                .setContentText("is running......") // ?????????????????????
                .setWhen(System.currentTimeMillis()); // ??????????????????????????????

        /*????????????Android 8.0?????????*/
        //??????notification??????
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId("notification_id");
        }
        //????????????notification??????
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel("notification_id", "notification_name", NotificationManager.IMPORTANCE_LOW);
            notificationManager.createNotificationChannel(channel);
        }

        Notification notification = builder.build(); // ??????????????????Notification
        startForeground(110, notification);
    }
}