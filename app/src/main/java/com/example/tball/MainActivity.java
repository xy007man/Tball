package com.example.tball;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.hjq.permissions.OnPermissionCallback;
import com.hjq.permissions.Permission;
import com.hjq.permissions.XXPermissions;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private int REQUEST_MEDIA_PROJECTION = 1;
    private MediaProjectionManager mMediaProjectionManager;
    private WindowManager windowManager;
    private FrameLayout coverLayout;
    private Paint mPaint = new Paint();
    private String TAG = "MainActivity";
    private int[] position = null;

    @SuppressLint("WrongConstant")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPaint.setColor(Color.RED);
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    | WindowManager.LayoutParams.TYPE_STATUS_BAR;
        } else {
            params.type = WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
        }
        params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        params.format = PixelFormat.TRANSLUCENT;
        params.gravity = Gravity.START | Gravity.TOP;
        Point point = new Point();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            windowManager.getDefaultDisplay().getRealSize(point);
        }
        params.width = point.y;
        params.height = point.x;

        coverLayout = new FrameLayout(this) {
            @Override
            public void onDraw(Canvas canvas) {
                super.onDraw(canvas);

                if (position == null || position.length < 4) {
                    return;
                }

                Log.d(TAG, "circle center (" + position[0] + "," + position[1] + ")");
                for (int i = 1; i < position.length / 2; i++) {
                    canvas.drawLine(position[0], position[1], position[i * 2], position[i * 2 + 1], mPaint);
                    Log.d(TAG, "end position (" + position[i * 2] + "," + position[i * 2 + 1] + ")");
                }
            }
        };
        coverLayout.setBackgroundColor(Color.argb(0, 0, 0,0));

        XXPermissions.with(this)
            .permission(Permission.SYSTEM_ALERT_WINDOW)
            .permission(Permission.MANAGE_EXTERNAL_STORAGE)
            .request(new OnPermissionCallback() {
                @Override
                public void onGranted(List<String> granted, boolean all) {
                    windowManager.addView(coverLayout, params);
                }
            });
    }

    /**
     * 启动服务器，bindingService
     *
     * @param view
     */
    public void startService(View view) {
        mMediaProjectionManager = (MediaProjectionManager) getApplication().getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mMediaProjectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);
    }
    
    /**
     * 初始化服务
     *
     * @param v
     */
    public void initService(View v) {
        sendMessage(ScreenshotsService.INIT_SERVICE);
    }

    /**
     * 停止服务
     *
     * @param view
     */
    public void stopService(View view) {
        sendMessage(ScreenshotsService.STOP_SERVICE);
    }

    public void sendMessage(int code) {

        if (isConn) {
            Message msgFromClient = Message.obtain(null, code);
            msgFromClient.replyTo = mMessenger;
            try {
                mService.send(msgFromClient);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Toast.makeText(this, "意外终止", Toast.LENGTH_SHORT).show();
        }
    }

    private Messenger mService;
    private boolean isConn;

    @SuppressLint("HandlerLeak")
    private Messenger mMessenger = new Messenger(new Handler() {
        @Override
        public void handleMessage(Message msgFromServer) {
            switch (msgFromServer.what) {
                case ScreenshotsService.CAPTURE_SUCCESS:
                    Bundle bundle = msgFromServer.getData();
                    position = bundle.getIntArray("pos");
                    coverLayout.invalidate();
                    break;
            }
            super.handleMessage(msgFromServer);
        }
    });

    private ServiceConnection mConn = new ServiceConnection() {

        //IBinder 对象，需要Bundle包装，传给Unity页面，和service进行通信
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = new Messenger(service);
            isConn = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            isConn = false;
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode != Activity.RESULT_OK) {
                return;
            }
            if (data != null && resultCode != 0) {
                Intent intent = new Intent(getApplicationContext(), ScreenshotsService.class);
                intent.putExtra("code", resultCode);
                intent.putExtra("data", data);
                bindService(intent, mConn, Context.BIND_AUTO_CREATE);
            }
        }
    }
}