package com.example.hakimichat.game;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.example.hakimichat.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 参考原始 chess 项目的实现，同时改进：
 * - 动态根据 View 大小计算格子尺寸（使棋盘能填满可用空间）
 * - 异步预加载并缩放棋子位图，避免主线程阻塞
 * - 缓存位图并在 onDetachedFromWindow 回收
 */
public class ChessBoardView extends View {
    private static final int BOARD_SIZE = 8;

    private ChessPiece[][] board;
    private Paint paint;
    private int selectedRow = -1;
    private int selectedCol = -1;
    private ChessGameCallback callback;
    private int currentPlayer = ChessPiece.WHITE;

    private List<int[]> possibleMoves = new ArrayList<>();
    private Move lastMove;

    // 棋子图片资源ID映射
    private Map<String, Integer> pieceResMap = new HashMap<>();
    // 棋子图片缓存（按当前 cellSize 大小）
    private Map<String, Bitmap> pieceBitmapCache = new HashMap<>();

    // 当前每格大小（像素），基于 view 宽高动态计算
    private int cellSize = 0;

    public ChessBoardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setAntiAlias(true);

        initializePieceResources();
        initializeBoard();
    }

    private void initializePieceResources() {
        // 白棋
        pieceResMap.put("pawn_white", R.drawable.pawn_white);
        pieceResMap.put("rook_white", R.drawable.rook_white);
        pieceResMap.put("knight_white", R.drawable.knight_white);
        pieceResMap.put("bishop_white", R.drawable.bishop_white);
        pieceResMap.put("queen_white", R.drawable.queen_white);
        pieceResMap.put("king_white", R.drawable.king_white);

        // 黑棋
        pieceResMap.put("pawn_black", R.drawable.pawn_black);
        pieceResMap.put("rook_black", R.drawable.rook_black);
        pieceResMap.put("knight_black", R.drawable.knight_black);
        pieceResMap.put("bishop_black", R.drawable.bishop_black);
        pieceResMap.put("queen_black", R.drawable.queen_black);
        pieceResMap.put("king_black", R.drawable.king_black);
    }

    private void initializeBoard() {
        board = new ChessPiece[BOARD_SIZE][BOARD_SIZE];

        // 初始化空棋盘
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                board[i][j] = new ChessPiece(ChessPiece.EMPTY, ChessPiece.WHITE);
            }
        }

        // 黑方
        board[0][0] = new ChessPiece(ChessPiece.ROOK, ChessPiece.BLACK);
        board[0][1] = new ChessPiece(ChessPiece.KNIGHT, ChessPiece.BLACK);
        board[0][2] = new ChessPiece(ChessPiece.BISHOP, ChessPiece.BLACK);
        board[0][3] = new ChessPiece(ChessPiece.QUEEN, ChessPiece.BLACK);
        board[0][4] = new ChessPiece(ChessPiece.KING, ChessPiece.BLACK);
        board[0][5] = new ChessPiece(ChessPiece.BISHOP, ChessPiece.BLACK);
        board[0][6] = new ChessPiece(ChessPiece.KNIGHT, ChessPiece.BLACK);
        board[0][7] = new ChessPiece(ChessPiece.ROOK, ChessPiece.BLACK);
        for (int j = 0; j < BOARD_SIZE; j++)
            board[1][j] = new ChessPiece(ChessPiece.PAWN, ChessPiece.BLACK);

        // 白方
        for (int j = 0; j < BOARD_SIZE; j++)
            board[6][j] = new ChessPiece(ChessPiece.PAWN, ChessPiece.WHITE);
        board[7][0] = new ChessPiece(ChessPiece.ROOK, ChessPiece.WHITE);
        board[7][1] = new ChessPiece(ChessPiece.KNIGHT, ChessPiece.WHITE);
        board[7][2] = new ChessPiece(ChessPiece.BISHOP, ChessPiece.WHITE);
        board[7][3] = new ChessPiece(ChessPiece.QUEEN, ChessPiece.WHITE);
        board[7][4] = new ChessPiece(ChessPiece.KING, ChessPiece.WHITE);
        board[7][5] = new ChessPiece(ChessPiece.BISHOP, ChessPiece.WHITE);
        board[7][6] = new ChessPiece(ChessPiece.KNIGHT, ChessPiece.WHITE);
        board[7][7] = new ChessPiece(ChessPiece.ROOK, ChessPiece.WHITE);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int size = Math.min(width, height);
        // 如果父是 wrap_content，fallback 到 8*50 px
        if (size == 0) size = 8 * 50;
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        int newCell = Math.max(1, Math.min(w / BOARD_SIZE, h / BOARD_SIZE));
        if (newCell != cellSize) {
            cellSize = newCell;
            // 异步预加载并缩放位图
            ensureScaledPieceBitmaps();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        try {
            super.onDraw(canvas);

            if (cellSize <= 0 || board == null) return;

            // 绘制格子
            for (int i = 0; i < BOARD_SIZE; i++) {
                for (int j = 0; j < BOARD_SIZE; j++) drawCell(canvas, i, j);
            }

            for (int[] move : possibleMoves) {
                if (move != null && move.length >= 2) drawPossibleMove(canvas, move[0], move[1]);
            }

            if (selectedRow != -1 && selectedCol != -1)
                drawSelectedCell(canvas, selectedRow, selectedCol);

            for (int i = 0; i < BOARD_SIZE; i++)
                for (int j = 0; j < BOARD_SIZE; j++) drawPiece(canvas, i, j);
        } catch (Exception e) {
            android.util.Log.e("ChessBoardView", "onDraw error", e);
        }
    }

    private void drawCell(Canvas canvas, int row, int col) {
        Rect rect = new Rect(col * cellSize, row * cellSize, (col + 1) * cellSize, (row + 1) * cellSize);
        if ((row + col) % 2 == 0) paint.setColor(Color.parseColor("#F0D9B5"));
        else paint.setColor(Color.parseColor("#B58863"));
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(rect, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.BLACK);
        paint.setStrokeWidth(1);
        canvas.drawRect(rect, paint);
    }

    private void drawSelectedCell(Canvas canvas, int row, int col) {
        Rect rect = new Rect(col * cellSize, row * cellSize, (col + 1) * cellSize, (row + 1) * cellSize);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.RED);
        paint.setStrokeWidth(4);
        canvas.drawRect(rect, paint);
    }

    private void drawPossibleMove(Canvas canvas, int row, int col) {
        float centerX = col * cellSize + cellSize / 2f;
        float centerY = row * cellSize + cellSize / 2f;
        float radius = cellSize / 6f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(128, 0, 255, 0));
        canvas.drawCircle(centerX, centerY, radius, paint);
    }

    private void drawPiece(Canvas canvas, int row, int col) {
        try {
            ChessPiece piece = board[row][col];
            if (piece == null || piece.getType() == ChessPiece.EMPTY) return;

            String imageName = piece.getImageName();
            Bitmap bmp = pieceBitmapCache.get(imageName);
            if (bmp != null) {
                int left = col * cellSize + (cellSize - bmp.getWidth()) / 2;
                int top = row * cellSize + (cellSize - bmp.getHeight()) / 2;
                canvas.drawBitmap(bmp, left, top, paint);
            } else {
                drawTextPiece(canvas, row, col, piece);
            }
        } catch (Exception e) {
            android.util.Log.e("ChessBoardView", "drawPiece error", e);
        }
    }

    private void drawTextPiece(Canvas canvas, int row, int col, ChessPiece piece) {
        float centerX = col * cellSize + cellSize / 2f;
        float centerY = row * cellSize + cellSize / 2f + cellSize * 0.3f;
        paint.setTextSize(cellSize * 0.6f);
        paint.setTextAlign(Paint.Align.CENTER);
        if (piece.getColor() == ChessPiece.WHITE) {
            paint.setColor(Color.WHITE);
            paint.setShadowLayer(2, 1, 1, Color.BLACK);
        } else {
            paint.setColor(Color.BLACK);
            paint.setShadowLayer(2, 1, 1, Color.WHITE);
        }
        paint.setStyle(Paint.Style.FILL);
        canvas.drawText(piece.getDisplayName(), centerX, centerY, paint);
        paint.clearShadowLayer();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        try {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                int col = (int) (event.getX() / cellSize);
                int row = (int) (event.getY() / cellSize);
                if (row >= 0 && row < BOARD_SIZE && col >= 0 && col < BOARD_SIZE)
                    handleCellClick(row, col);
            }
        } catch (Exception e) {
            android.util.Log.e("ChessBoardView", "onTouchEvent error", e);
        }
        return true;
    }

    private void handleCellClick(int row, int col) {
        ChessPiece clickedPiece = board[row][col];
        if (selectedRow != -1 && selectedCol != -1) {
            if (isValidMove(selectedRow, selectedCol, row, col)) {
                movePiece(selectedRow, selectedCol, row, col);
                selectedRow = -1;
                selectedCol = -1;
                possibleMoves.clear();
            } else if (clickedPiece.getType() != ChessPiece.EMPTY && clickedPiece.getColor() == currentPlayer) {
                selectedRow = row;
                selectedCol = col;
                calculatePossibleMoves(row, col);
                updateSelectedPieceInfo();
            } else {
                selectedRow = -1;
                selectedCol = -1;
                possibleMoves.clear();
                updateSelectedPieceInfo();
            }
        } else if (clickedPiece.getType() != ChessPiece.EMPTY && clickedPiece.getColor() == currentPlayer) {
            selectedRow = row;
            selectedCol = col;
            calculatePossibleMoves(row, col);
            updateSelectedPieceInfo();
        }
        invalidate();
    }

    private void calculatePossibleMoves(int fromRow, int fromCol) {
        possibleMoves.clear();
        ChessPiece piece = board[fromRow][fromCol];
        switch (piece.getType()) {
            case ChessPiece.PAWN:
                calculatePawnMoves(fromRow, fromCol, piece);
                break;
            case ChessPiece.ROOK:
                calculateRookMoves(fromRow, fromCol, piece);
                break;
            case ChessPiece.KNIGHT:
                calculateKnightMoves(fromRow, fromCol, piece);
                break;
            case ChessPiece.BISHOP:
                calculateBishopMoves(fromRow, fromCol, piece);
                break;
            case ChessPiece.QUEEN:
                calculateQueenMoves(fromRow, fromCol, piece);
                break;
            case ChessPiece.KING:
                calculateKingMoves(fromRow, fromCol, piece);
                break;
        }
    }

    // (move calculation methods omitted for brevity — copy from chess project)
    private void calculatePawnMoves(int row, int col, ChessPiece piece) {
        int direction = (piece.getColor() == ChessPiece.WHITE) ? -1 : 1;
        if (isValidPosition(row + direction, col) && board[row + direction][col].getType() == ChessPiece.EMPTY) {
            possibleMoves.add(new int[]{row + direction, col});
            if ((piece.getColor() == ChessPiece.WHITE && row == 6) || (piece.getColor() == ChessPiece.BLACK && row == 1)) {
                if (board[row + 2 * direction][col].getType() == ChessPiece.EMPTY)
                    possibleMoves.add(new int[]{row + 2 * direction, col});
            }
        }
        for (int offset : new int[]{-1, 1}) {
            if (isValidPosition(row + direction, col + offset)) {
                ChessPiece target = board[row + direction][col + offset];
                if (target.getType() != ChessPiece.EMPTY && target.isOpponent(piece))
                    possibleMoves.add(new int[]{row + direction, col + offset});
            }
        }
    }

    private void calculateRookMoves(int row, int col, ChessPiece piece) {
        calculateLinearMoves(row, col, piece, new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}});
    }

    private void calculateBishopMoves(int row, int col, ChessPiece piece) {
        calculateLinearMoves(row, col, piece, new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}});
    }

    private void calculateQueenMoves(int row, int col, ChessPiece piece) {
        calculateLinearMoves(row, col, piece, new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}});
    }

    private void calculateLinearMoves(int row, int col, ChessPiece piece, int[][] dirs) {
        for (int[] dir : dirs) {
            for (int i = 1; i < BOARD_SIZE; i++) {
                int nr = row + dir[0] * i, nc = col + dir[1] * i;
                if (!isValidPosition(nr, nc)) break;
                ChessPiece t = board[nr][nc];
                if (t.getType() == ChessPiece.EMPTY) possibleMoves.add(new int[]{nr, nc});
                else {
                    if (t.isOpponent(piece)) possibleMoves.add(new int[]{nr, nc});
                    break;
                }
            }
        }
    }

    private void calculateKnightMoves(int row, int col, ChessPiece piece) {
        int[][] m = {{2, 1}, {2, -1}, {-2, 1}, {-2, -1}, {1, 2}, {1, -2}, {-1, 2}, {-1, -2}};
        for (int[] mv : m) {
            int nr = row + mv[0], nc = col + mv[1];
            if (isValidPosition(nr, nc)) {
                ChessPiece t = board[nr][nc];
                if (t.getType() == ChessPiece.EMPTY || t.isOpponent(piece))
                    possibleMoves.add(new int[]{nr, nc});
            }
        }
    }

    private void calculateKingMoves(int row, int col, ChessPiece piece) {
        for (int i = -1; i <= 1; i++)
            for (int j = -1; j <= 1; j++) {
                if (i == 0 && j == 0) continue;
                int nr = row + i, nc = col + j;
                if (isValidPosition(nr, nc)) {
                    ChessPiece t = board[nr][nc];
                    if (t.getType() == ChessPiece.EMPTY || t.isOpponent(piece))
                        possibleMoves.add(new int[]{nr, nc});
                }
            }
    }

    private boolean isValidPosition(int row, int col) {
        return row >= 0 && row < BOARD_SIZE && col >= 0 && col < BOARD_SIZE;
    }

    private boolean isValidMove(int fromRow, int fromCol, int toRow, int toCol) {
        for (int[] m : possibleMoves) if (m[0] == toRow && m[1] == toCol) return true;
        return false;
    }

    private void movePiece(int fromRow, int fromCol, int toRow, int toCol) {
        // 不在视图层直接修改棋局状态，改为将移动意图通知 Activity
        // 由 Activity 通过 GameManager 发起网络/本地的状态变更，随后通过
        // GameStateListener 同步回到视图（避免本地-远端重复执行移动导致的不同步/逻辑错误）
        if (callback != null) {
            callback.onPieceMoved(fromRow, fromCol, toRow, toCol);
        }

        // 清理选中和可能移动集合，等待来自 GameManager 的状态更新
        selectedRow = -1;
        selectedCol = -1;
        possibleMoves.clear();
        invalidate();
    }

    public void undoMove() {
        if (lastMove != null) {
            board[lastMove.fromRow][lastMove.fromCol] = lastMove.movingPiece;
            board[lastMove.toRow][lastMove.toCol] = lastMove.capturedPiece;
            currentPlayer = (currentPlayer == ChessPiece.WHITE) ? ChessPiece.BLACK : ChessPiece.WHITE;
            lastMove = null;
            if (callback != null) callback.onPlayerChanged(currentPlayer);
            invalidate();
        }
    }

    private void checkGameStatus() {
        boolean whiteKing = false, blackKing = false;
        for (int i = 0; i < BOARD_SIZE; i++)
            for (int j = 0; j < BOARD_SIZE; j++) {
                ChessPiece p = board[i][j];
                if (p.getType() == ChessPiece.KING) {
                    if (p.getColor() == ChessPiece.WHITE) whiteKing = true;
                    else blackKing = true;
                }
            }
        if (!whiteKing && callback != null) callback.onGameOver("黑方胜利!");
        else if (!blackKing && callback != null) callback.onGameOver("白方胜利!");
    }

    private void updateSelectedPieceInfo() {
        if (selectedRow != -1 && selectedCol != -1 && callback != null) {
            ChessPiece sel = board[selectedRow][selectedCol];
            String player = (sel.getColor() == ChessPiece.WHITE) ? "白方" : "黑方";
            callback.onPieceSelected(player + sel.getDisplayName());
        } else if (callback != null) {
            callback.onPieceSelected("无");
        }
    }

    public void setGameCallback(ChessGameCallback callback) {
        this.callback = callback;
    }

    /**
     * 使用 ChessGame 的状态更新视图棋盘（被 ChessActivity 调用）
     */
    public void setBoardFromGame(ChessGame game) {
        try {
            if (game == null) return;
            int[][][] data = game.getBoardData();
            if (data == null) return;

            // 确保内部 board 已分配
            if (board == null || board.length != BOARD_SIZE) board = new ChessPiece[BOARD_SIZE][BOARD_SIZE];

            for (int i = 0; i < BOARD_SIZE; i++) {
                for (int j = 0; j < BOARD_SIZE; j++) {
                    int type = data[i][j][0];
                    int color = data[i][j][1];
                    board[i][j] = new ChessPiece(type, color);
                }
            }

            // 使用游戏的当前回合颜色来设置 currentPlayer，以便 UI 仅允许正确方行动
            try {
                int turnColor = game.getCurrentPlayerColor();
                this.currentPlayer = (turnColor == 0) ? ChessPiece.WHITE : ChessPiece.BLACK;
            } catch (Exception ignored) {}

            // 日志有助于远端诊断
            android.util.Log.d("ChessBoardView", "setBoardFromGame: board updated from game, cellSize=" + cellSize);
            invalidate();
        } catch (Exception e) {
            android.util.Log.e("ChessBoardView", "setBoardFromGame error", e);
        }
    }

    public void restartGame() {
        initializeBoard();
        selectedRow = -1;
        selectedCol = -1;
        possibleMoves.clear();
        currentPlayer = ChessPiece.WHITE;
        lastMove = null;
        if (callback != null) {
            callback.onPlayerChanged(currentPlayer);
            callback.onPieceSelected("无");
        }
        invalidate();
    }

    // 异步预加载并缩放位图（在 background thread），完成后在 UI 线程替换缓存
    private final java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
    private final java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<?>> preloadFuture = new java.util.concurrent.atomic.AtomicReference<>();

    private void ensureScaledPieceBitmaps() {
        try {
            final int target = Math.max(1, (int) (cellSize * 0.8f));
            java.util.concurrent.Future<?> prev = preloadFuture.getAndSet(null);
            if (prev != null && !prev.isDone()) prev.cancel(true);
            java.util.concurrent.Future<?> f = executor.submit(() -> {
                final Map<String, Bitmap> newCache = new HashMap<>();
                try {
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inScaled = false;
                    opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    for (Map.Entry<String, Integer> e : pieceResMap.entrySet()) {
                        if (Thread.currentThread().isInterrupted()) return;
                        Integer resId = e.getValue();
                        if (resId == null) continue;
                        try {
                            Bitmap bmp = BitmapFactory.decodeResource(getResources(), resId, opts);
                            if (bmp == null) continue;
                            Bitmap scaled = Bitmap.createScaledBitmap(bmp, target, target, true);
                            newCache.put(e.getKey(), scaled);
                            if (scaled != bmp) bmp.recycle();
                        } catch (OutOfMemoryError oom) {
                            android.util.Log.w("ChessBoardView", "OOM decoding " + e.getKey(), oom);
                        } catch (Exception ex) {
                            android.util.Log.w("ChessBoardView", "decode failed " + e.getKey(), ex);
                        }
                    }
                } finally {
                    post(() -> {
                        try {
                            for (Bitmap b : pieceBitmapCache.values()) {
                                if (b != null && !b.isRecycled()) b.recycle();
                            }
                        } catch (Exception ignored) {
                        }
                        pieceBitmapCache.clear();
                        pieceBitmapCache.putAll(newCache);
                        invalidate();
                    });
                }
            });
            preloadFuture.set(f);
        } catch (Exception ex) {
            android.util.Log.w("ChessBoardView", "start preload failed", ex);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        try {
            java.util.concurrent.Future<?> f = preloadFuture.getAndSet(null);
            if (f != null && !f.isDone()) f.cancel(true);
            executor.shutdownNow();
        } catch (Exception ignored) {
        }
        for (Bitmap b : pieceBitmapCache.values()) {
            try {
                if (b != null && !b.isRecycled()) b.recycle();
            } catch (Exception ignored) {
            }
        }
        pieceBitmapCache.clear();
    }

    // 回收帮助类 / 移动记录
    private static class Move {
        int fromRow, fromCol, toRow, toCol;
        ChessPiece movingPiece, capturedPiece;

        Move(int fr, int fc, int tr, int tc, ChessPiece m, ChessPiece c) {
            fromRow = fr;
            fromCol = fc;
            toRow = tr;
            toCol = tc;
            movingPiece = m;
            capturedPiece = c;
        }
    }

    public interface ChessGameCallback {
        void onPieceSelected(String pieceInfo);

        void onPieceMoved(int fromRow, int fromCol, int toRow, int toCol);

        void onPlayerChanged(int player);

        void onGameOver(String message);
    }

    // 内部棋子类（视图用）
    private static class ChessPiece {
        static final int EMPTY = 0, PAWN = 1, ROOK = 2, KNIGHT = 3, BISHOP = 4, QUEEN = 5, KING = 6;
        static final int WHITE = 0, BLACK = 1;
        private int type;
        private int color;
        private boolean moved = false;

        ChessPiece(int t, int c) {
            type = t;
            color = c;
            moved = false;
        }

        int getType() {
            return type;
        }

        int getColor() {
            return color;
        }

        void setMoved(boolean m) {
            moved = m;
        }

        boolean isOpponent(ChessPiece o) {
            return o != null && o.color != this.color;
        }

        boolean isEmpty() {
            return type == EMPTY;
        }

        String getDisplayName() {
            switch (type) {
                case PAWN:
                    return "P";
                case ROOK:
                    return "R";
                case KNIGHT:
                    return "N";
                case BISHOP:
                    return "B";
                case QUEEN:
                    return "Q";
                case KING:
                    return "K";
                default:
                    return "?";
            }
        }

        String getImageName() {
            String col = (color == WHITE) ? "white" : "black";
            switch (type) {
                case PAWN:
                    return "pawn_" + col;
                case ROOK:
                    return "rook_" + col;
                case KNIGHT:
                    return "knight_" + col;
                case BISHOP:
                    return "bishop_" + col;
                case QUEEN:
                    return "queen_" + col;
                case KING:
                    return "king_" + col;
                default:
                    return "";
            }
        }
    }
}
