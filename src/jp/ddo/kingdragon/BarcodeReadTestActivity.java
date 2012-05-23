package jp.ddo.kingdragon;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Display;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.Toast;

import java.io.File;
import java.util.List;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Reader;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

public class BarcodeReadTestActivity extends Activity implements SurfaceHolder.Callback, Camera.PreviewCallback {
    private boolean focusing;
    private boolean launched;

    private File baseDir;
    private Reader mReader;
    private SurfaceView preview;
    private Camera mCamera;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.main);
        addContentView(new CameraOverlayView(BarcodeReadTestActivity.this),
                       new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        focusing = false;
        launched = false;

        mReader = new MultiFormatReader();

        // 保存用ディレクトリの作成
        baseDir = new File(Environment.getExternalStorageDirectory(), "BarcodeReadTest");
        try {
            if(!baseDir.exists() && !baseDir.mkdirs()) {
                finish();
            }
        }
        catch(Exception e) {
            finish();
        }

        preview = (SurfaceView)findViewById(R.id.preview);
        preview.getHolder().addCallback(BarcodeReadTestActivity.this);
        preview.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        preview.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // 画面がタッチされたらオートフォーカスを実行
                if(launched && !focusing) {
                    // オートフォーカス中でなければオートフォーカスを実行
                    // フラグを更新
                    focusing = true;

                    mCamera.autoFocus(new Camera.AutoFocusCallback() {
                        @Override
                        public void onAutoFocus(boolean success, Camera camera) {
                            focusing = false;
                        }
                    });
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();

        surfaceChanged(preview.getHolder(), 0, 0, 0);
    }

    @Override
    public void onPause() {
        super.onPause();

        if(mCamera != null) {
            // カメラのリソースを利用中であれば解放する
            mCamera.stopPreview();
            mCamera.setPreviewCallback(null);
            mCamera.release();
            mCamera = null;
        }
        launched = false;
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle args) {
        Dialog retDialog = super.onCreateDialog(id);

        AlertDialog.Builder builder;

        switch(id) {
        case 0:
            builder = new AlertDialog.Builder(BarcodeReadTestActivity.this);
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setTitle("読み取り完了");
            builder.setMessage(args.getString("message"));
            builder.setPositiveButton(getString(android.R.string.ok), null);
            builder.setCancelable(true);
            retDialog = builder.create();
            retDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    removeDialog(0);
                }
            });

            break;
        default:
            break;
        }

        return(retDialog);
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        Size mSize = mCamera.getParameters().getPictureSize();
        int width = mSize.width;
        int height = mSize.height;

        int[] rgb = new int[(width * height)];
        Bitmap srcBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        decodeYUV420SP(rgb, data, width, height);
        try {
            srcBitmap.setPixels(rgb, 0, width, 0, 0, width, height);
        }
        catch (Exception e) {
            Log.e("onPreviewFrame", e.getMessage(), e);
        }

        Bitmap mBitmap = Bitmap.createBitmap(srcBitmap, srcBitmap.getWidth() / 2 - 150, srcBitmap.getHeight() / 2 - 50, 300, 100, null, true);
        LuminanceSource source = new RGBLuminanceSource(mBitmap);
        BinaryBitmap mBinaryBitmap = new BinaryBitmap(new HybridBinarizer(source));
        try {
            Result mResult = mReader.decode(mBinaryBitmap);
            Bundle args = new Bundle();
            args.putString("message", mResult.getText());
            showDialog(0, args);
        }
        catch (Exception e) {
            Log.e("onActivityResult", e.getMessage(), e);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if(mCamera != null) {
            mCamera.stopPreview();
        }
        else {
            try {
                mCamera = Camera.open();
            }
            catch(Exception e) {
                Toast.makeText(BarcodeReadTestActivity.this, R.string.error_launch_camera_failed, Toast.LENGTH_SHORT).show();
                Log.e("surfaceChanged", e.getMessage(), e);

                finish();
            }
        }

        if(!launched) {
            Camera.Parameters params = mCamera.getParameters();

            List<Camera.Size> previewSizes = params.getSupportedPreviewSizes();
            Camera.Size preSize = previewSizes.get(0);
            for(int i = 1; i < previewSizes.size(); i++) {
                Camera.Size temp = previewSizes.get(i);
                if(preSize.width * preSize.height < temp.width * temp.height) {
                    preSize = temp;
                }
            }
            params.setPreviewSize(preSize.width, preSize.height);
            params.setPictureSize(preSize.width, preSize.height);

            // プレビューサイズを元にSurfaceViewのサイズを決定
            WindowManager manager = (WindowManager)getSystemService(WINDOW_SERVICE);
            Display mDisplay = manager.getDefaultDisplay();
            ViewGroup.LayoutParams lParams = preview.getLayoutParams();
            if(preSize.width <= mDisplay.getWidth() && preSize.height <= mDisplay.getHeight()) {
                lParams.width  = preSize.width;
                lParams.height = preSize.height;
            }
            else {
                lParams.width  = mDisplay.getWidth();
                lParams.height = mDisplay.getHeight();
                if((double)preSize.width / (double)preSize.height
                    < (double)mDisplay.getWidth() / (double)mDisplay.getHeight()) {
                     // 横の長さに合わせる
                     lParams.height = preSize.height * mDisplay.getWidth() / preSize.width;
                 }
                 else if((double)preSize.width / (double)preSize.height
                          > (double)mDisplay.getWidth() / (double)mDisplay.getHeight()) {
                     // 縦の長さに合わせる
                     lParams.width  = preSize.width * mDisplay.getHeight() / preSize.height;
                 }
            }
            preview.setLayoutParams(lParams);
            mCamera.setParameters(params);

            launched = true;
        }

        try {
            mCamera.setPreviewDisplay(preview.getHolder());
            mCamera.setPreviewCallback(BarcodeReadTestActivity.this);
            mCamera.cancelAutoFocus();
            mCamera.startPreview();
        }
        catch(Exception e) {
            Toast.makeText(BarcodeReadTestActivity.this, R.string.error_launch_camera_failed, Toast.LENGTH_SHORT).show();
            Log.e("surfaceChanged", e.getMessage(), e);

            finish();
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {}

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {}

    /***
     * YUV420データをBitmapに変換する<br />
     * 参考:カメラから取得したbyteデータをbitmap化したい - Android-SDK-Japan | Google グループ<br />
     *      https://groups.google.com/group/android-sdk-japan/browse_thread/thread/09f3545c7f7cfdac/018d2eb85fb9cb44?hl=ja&pli=1
     *
     * @param rgb
     * @param yuv420sp
     * @param width
     * @param height
     */
    public static final void decodeYUV420SP(int[] rgb, byte[] yuv420sp, int width, int height) {
        final int frameSize = width * height;

        for(int j = 0, yp = 0; j < height; j++) {
            int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
            for(int i = 0; i < width; i++, yp++) {
                int y = (0xff & ((int) yuv420sp[yp])) - 16;
                if(y < 0) {
                    y = 0;
                }
                if((i & 1) == 0) {
                        v = (0xff & yuv420sp[uvp++]) - 128;
                        u = (0xff & yuv420sp[uvp++]) - 128;
                }

                int y1192 = 1192 * y;
                int r = (y1192 + 1634 * v);
                int g = (y1192 - 833 * v - 400 * u);
                int b = (y1192 + 2066 * u);

                if(r < 0) r = 0; else if (r > 262143) r = 262143;
                if(g < 0) g = 0; else if (g > 262143) g = 262143;
                if(b < 0) b = 0; else if (b > 262143) b = 262143;

                rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
            }
        }
    }
}