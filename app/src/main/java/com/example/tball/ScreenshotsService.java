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
        // 启动辅助器是竖屏的，进入游戏是横屏，所以暂时将x与y反过来写
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
     * 初始化截屏服务
     */
    public void initVirtualDisplay(Message msg) {
        mReplyToClient = msg.replyTo;

        if (mMediaProjection == null) {
            setUpMediaProjection();
        }

        if (mImageReader == null || mVirtualDisplay == null)//截取屏幕需要先初始化  virtualDisplay
            virtualDisplay();

    }

    private void setUpMediaProjection() {
        createNotificationChannel();//构建通知栏，适配api 29,小于29可以不用，
        mMediaProjection = mMediaProjectionManager.getMediaProjection(mResultCode, mResultData);
        Log.d(TAG, "mMediaProjection 初始化成功");
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
                Log.i(TAG, "捕获图像数据");

                if (bitmap != null) {
                    try {
                        String screenPath = rootDirectory + "screen.jpg";
                        String templatePath = rootDirectory + "circle.jpg";
                        File fileImage = new File(screenPath);
                        if (!fileImage.exists()) {
                            fileImage.createNewFile();
                            Log.i(TAG, "创建文件");
                        }
                        FileOutputStream out = new FileOutputStream(fileImage);
                        if (out != null) {
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
                            out.flush();
                            out.close();
                            //通知更新图库
                            Intent media = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                            Uri contentUri = Uri.fromFile(fileImage);
                            media.setData(contentUri);
                            ScreenshotsService.this.sendBroadcast(media);
                            Log.i(TAG, "保存图片");

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
        Notification.Builder builder = new Notification.Builder(getApplicationContext()); //获取一个Notification构造器
        builder.setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher)) // 设置下拉列表中的图标(大图标)
                //.setContentTitle("SMI InstantView") // 设置下拉列表里的标题
                .setSmallIcon(R.mipmap.ic_launcher) // 设置状态栏内的小图标
                .setContentText("is running......") // 设置上下文内容
                .setWhen(System.currentTimeMillis()); // 设置该通知发生的时间

        /*以下是对Android 8.0的适配*/
        //普通notification适配
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId("notification_id");
        }
        //前台服务notification适配
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel("notification_id", "notification_name", NotificationManager.IMPORTANCE_LOW);
            notificationManager.createNotificationChannel(channel);
        }

        Notification notification = builder.build(); // 获取构建好的Notification
        startForeground(110, notification);
    }
}