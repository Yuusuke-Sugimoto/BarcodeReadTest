package jp.ddo.kingdragon;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/***
 * 参考:らぼ☆ろぐ >> Android カメラプレビューよりサイズを指定して画像取得
 *      http://lablog.lanche.jp/archives/423
 */
public class CameraOverlayView extends View {
    private int width;
    private int height;

    public CameraOverlayView(Context context) {
        super(context);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        width = w;
        height = h;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawColor(Color.TRANSPARENT);

        Paint mPaint = new Paint();
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setARGB(100, 0, 0, 0);

        canvas.drawRect(0, 0, width, height / 2 - 50, mPaint);
        canvas.drawRect(0, height / 2 + 50, width, height, mPaint);
        canvas.drawRect(0, height / 2 - 50, width / 2 - 150, height / 2 + 50, mPaint);
        canvas.drawRect(width / 2 + 150, height / 2 - 50, width, height / 2 + 50, mPaint);

        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setARGB(255, 255, 0, 0);

        canvas.drawRect(width / 2 - 150, height / 2 - 50, width / 2 + 150, height / 2 + 50, mPaint);
    }
}