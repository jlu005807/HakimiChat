package com.example.hakimichat.game;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class GobangBoardView extends View {

    private static final int BOARD_SIZE = 15;

    // Paints
    private Paint gridPaint, coordPaint, starPointPaint;
    private Paint blackPiecePaint, whitePiecePaint;
    private Paint highlightPaint;
    private Paint boardBackgroundPaint;

    // Dimensions
    private float gridSize;
    private float pieceRadius;
    private float margin;
    private final Rect textBounds = new Rect();
    private final Point[] starPoints = {
            new Point(3, 3), new Point(11, 3),
            new Point(3, 11), new Point(11, 11),
            new Point(7, 7)
    };

    // Game state
    private int[][] board;
    private Point lastMove;
    private OnBoardClickListener clickListener;

    public interface OnBoardClickListener {
        void onCellClick(int x, int y);
    }

    public GobangBoardView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        board = new int[BOARD_SIZE][BOARD_SIZE];

        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(Color.parseColor("#A0000000"));
        gridPaint.setStrokeWidth(2);

        boardBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boardBackgroundPaint.setColor(Color.parseColor("#E3C16F"));
        boardBackgroundPaint.setStyle(Paint.Style.FILL);
        boardBackgroundPaint.setShadowLayer(12, 4, 4, Color.argb(100, 0, 0, 0));
        setLayerType(View.LAYER_TYPE_SOFTWARE, boardBackgroundPaint);

        coordPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        coordPaint.setColor(Color.parseColor("#A0000000"));
        coordPaint.setTextAlign(Paint.Align.CENTER);

        starPointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        starPointPaint.setColor(Color.parseColor("#80000000"));
        starPointPaint.setStyle(Paint.Style.FILL);

        blackPiecePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        whitePiecePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        highlightPaint.setColor(Color.parseColor("#FFFF0000"));
        highlightPaint.setStyle(Paint.Style.STROKE);
        highlightPaint.setStrokeWidth(4);
    }

    public void setOnBoardClickListener(OnBoardClickListener listener) {
        this.clickListener = listener;
    }

    public void updateBoard(int[][] newBoard, Point lastMove) {
        this.board = newBoard;
        this.lastMove = lastMove;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int dimen = Math.min(getMeasuredWidth(), getMeasuredHeight());
        setMeasuredDimension(dimen, dimen);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldWidth, int oldHeight) {
        super.onSizeChanged(w, h, oldWidth, oldHeight);
        margin = w / 20f;
        float contentWidth = w - 2 * margin;
        gridSize = contentWidth / (BOARD_SIZE - 1);
        coordPaint.setTextSize(margin * 0.6f);
        pieceRadius = gridSize * 0.45f;

        RadialGradient blackGradient = new RadialGradient(0, 0, pieceRadius,
                Color.parseColor("#444444"), Color.BLACK, Shader.TileMode.CLAMP);
        blackPiecePaint.setShader(blackGradient);

        RadialGradient whiteGradient = new RadialGradient(-pieceRadius * 0.3f, -pieceRadius * 0.3f, pieceRadius * 1.5f,
                Color.WHITE, Color.parseColor("#CCCCCC"), Shader.TileMode.CLAMP);
        whitePiecePaint.setShader(whiteGradient);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        drawBoardAndCoords(canvas);
        drawPieces(canvas);
        drawHighlight(canvas);
    }

    private void drawBoardAndCoords(Canvas canvas) {
        float lineStart = margin;
        float lineEnd = getWidth() - margin;
        float cornerRadius = gridSize / 2;

        canvas.drawRoundRect(lineStart, lineStart, lineEnd, lineEnd, cornerRadius, cornerRadius, boardBackgroundPaint);

        for (int i = 0; i < BOARD_SIZE; i++) {
            float pos = margin + i * gridSize;
            canvas.drawLine(lineStart, pos, lineEnd, pos, gridPaint);
            canvas.drawLine(pos, lineStart, pos, lineEnd, gridPaint);
        }

        float starPointRadius = gridSize * 0.1f;
        for (Point p : starPoints) {
            canvas.drawCircle(margin + p.x * gridSize, margin + p.y * gridSize, starPointRadius, starPointPaint);
        }

        for (int i = 0; i < BOARD_SIZE; i++) {
            float pos = margin + i * gridSize;
            String coordText = String.valueOf(i + 1);
            coordPaint.getTextBounds(coordText, 0, coordText.length(), textBounds);
            float textOffset = textBounds.height() / 2f;
            canvas.drawText(coordText, pos, margin / 2 + textOffset, coordPaint);
            canvas.drawText(coordText, pos, lineEnd + margin / 2 + textOffset, coordPaint);
            canvas.drawText(coordText, margin / 2, pos + textOffset, coordPaint);
            canvas.drawText(coordText, lineEnd + margin / 2, pos + textOffset, coordPaint);
        }
    }

    private void drawPieces(Canvas canvas) {
        if (board == null) return;
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                if (board[i][j] != 0) {
                    float cx = margin + i * gridSize;
                    float cy = margin + j * gridSize;

                    canvas.save();
                    canvas.translate(cx, cy);

                    if (board[i][j] == 1) { // BLACK
                        canvas.drawCircle(0, 0, pieceRadius, blackPiecePaint);
                    } else { // WHITE
                        canvas.drawCircle(0, 0, pieceRadius, whitePiecePaint);
                    }
                    canvas.restore();
                }
            }
        }
    }

    private void drawHighlight(Canvas canvas) {
        if (lastMove != null) {
            float cx = margin + lastMove.x * gridSize;
            float cy = margin + lastMove.y * gridSize;
            canvas.drawCircle(cx, cy, pieceRadius * 0.8f, highlightPaint);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            int gridX = Math.round((event.getX() - margin) / gridSize);
            int gridY = Math.round((event.getY() - margin) / gridSize);

            if (gridX >= 0 && gridX < BOARD_SIZE && gridY >= 0 && gridY < BOARD_SIZE) {
                if (clickListener != null) {
                    clickListener.onCellClick(gridX, gridY);
                }
            }
        }
        return true;
    }
}
