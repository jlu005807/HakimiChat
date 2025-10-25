package com.example.hakimichat.game;

import android.graphics.Point;

import com.example.hakimichat.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * 国际象棋游戏实现
 */
public class ChessGame extends BaseGame {

    public static final int BOARD_SIZE = 8;
    public static final int EMPTY = 0;
    public static final int PAWN = 1;   // 兵
    public static final int ROOK = 2;   // 车
    public static final int KNIGHT = 3; // 马
    public static final int BISHOP = 4; // 象
    public static final int QUEEN = 5;  // 后
    public static final int KING = 6;   // 王

    public static final int WHITE = 0;
    public static final int BLACK = 1;

    private static final String GAME_TYPE = "Chess";
    private static final int MAX_SEARCH_DEPTH = 4;
    private static final long TIME_LIMIT_MS = 2500;

    private ChessPiece[][] board = new ChessPiece[BOARD_SIZE][BOARD_SIZE];
    private int moveCount = 0;
    private int turnColor = WHITE;
    private String whitePlayerName;
    private String blackPlayerName;

    private boolean isAiEnabled = false;
    private boolean isStrictMode = false;
    private int humanPlayerColor = WHITE;
    private String humanPlayerName = null;

    private final Stack<Move> moveHistory = new Stack<>();

    public ChessGame() {
        super();
        initGame();
    }

    @Override
    public void initGame() {
        // 初始化空棋盘
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                board[i][j] = new ChessPiece(EMPTY, WHITE);
            }
        }

        // 设置黑方棋子 (第0行)
        board[0][0] = new ChessPiece(ROOK, BLACK);
        board[0][1] = new ChessPiece(KNIGHT, BLACK);
        board[0][2] = new ChessPiece(BISHOP, BLACK);
        board[0][3] = new ChessPiece(QUEEN, BLACK);
        board[0][4] = new ChessPiece(KING, BLACK);
        board[0][5] = new ChessPiece(BISHOP, BLACK);
        board[0][6] = new ChessPiece(KNIGHT, BLACK);
        board[0][7] = new ChessPiece(ROOK, BLACK);

        // 黑方兵 (第1行)
        for (int j = 0; j < BOARD_SIZE; j++) {
            board[1][j] = new ChessPiece(PAWN, BLACK);
        }

        // 设置白方棋子 (第7行)
        board[7][0] = new ChessPiece(ROOK, WHITE);
        board[7][1] = new ChessPiece(KNIGHT, WHITE);
        board[7][2] = new ChessPiece(BISHOP, WHITE);
        board[7][3] = new ChessPiece(QUEEN, WHITE);
        board[7][4] = new ChessPiece(KING, WHITE);
        board[7][5] = new ChessPiece(BISHOP, WHITE);
        board[7][6] = new ChessPiece(KNIGHT, WHITE);
        board[7][7] = new ChessPiece(ROOK, WHITE);

        // 白方兵 (第6行)
        for (int j = 0; j < BOARD_SIZE; j++) {
            board[6][j] = new ChessPiece(PAWN, WHITE);
        }

        moveCount = 0;
        turnColor = WHITE;
        isGameOver = false;
        gameResult = null;
        moveHistory.clear();
    }

    @Override
    public void reset() {
        initGame();
        if (players.size() >= 1) whitePlayerName = players.get(0);
        if (players.size() >= 2) blackPlayerName = players.get(1);
        currentPlayer = whitePlayerName;
    }

    @Override
    public String getGameType() {
        return GAME_TYPE;
    }

    @Override
    public String getGameName() {
        return "国际象棋";
    }

    @Override
    public String getGameDescription() {
        return "经典策略对战，智胜对手";
    }

    @Override
    public int getGameIcon() {
        return R.drawable.ic_chess;
    }

    @Override
    public int getMaxPlayers() {
        return 2;
    }

    @Override
    public int getMinPlayers() {
        return 2;
    }

    @Override
    public boolean addPlayer(String player) {
        if (super.addPlayer(player)) {
            if (players.size() == 1) {
                whitePlayerName = player;
                currentPlayer = whitePlayerName;
            } else if (players.size() == 2) {
                blackPlayerName = player;
                isGameStarted = true;

                // 如果不是AI模式（真人对战），随机决定谁是白方
                if (!isAiEnabled) {
                    java.util.Random random = new java.util.Random();
                    if (random.nextBoolean()) {
                        // 交换黑白方
                        String temp = whitePlayerName;
                        whitePlayerName = blackPlayerName;
                        blackPlayerName = temp;
                        currentPlayer = whitePlayerName;
                    }
                } else {
                    // AI模式：现在可以确定humanPlayerColor了
                    if (humanPlayerName != null) {
                        if (humanPlayerName.equals(whitePlayerName)) {
                            this.humanPlayerColor = WHITE;
                        } else {
                            this.humanPlayerColor = BLACK;
                        }
                    }
                }
            }
            return true;
        }
        return false;
    }

    public void setAiMode(boolean isAi, String humanPlayer) {
        this.isAiEnabled = isAi;
        if (isAi) {
            this.humanPlayerName = humanPlayer;
        }
    }

    public boolean isAiEnabled() {
        return isAiEnabled;
    }

    public String getWhitePlayerName() {
        return whitePlayerName;
    }

    public String getBlackPlayerName() {
        return blackPlayerName;
    }

    public ChessPiece[][] getBoard() {
        return board;
    }

    public int[][][] getBoardData() {
        int[][][] boardData = new int[BOARD_SIZE][BOARD_SIZE][2];
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                boardData[i][j][0] = board[i][j].getType();
                boardData[i][j][1] = board[i][j].getColor();
            }
        }
        return boardData;
    }

    public int getCurrentPlayerColor() {
        return turnColor;
    }

    public void setStrictMode(boolean isStrict) {
        this.isStrictMode = isStrict;
    }

    @Override
    public boolean processMove(String player, JSONObject moveData) {
        if (isGameOver || !isGameStarted || !player.equals(currentPlayer)) {
            return false;
        }

        try {
            int fromRow = moveData.getInt("fromRow");
            int fromCol = moveData.getInt("fromCol");
            int toRow = moveData.getInt("toRow");
            int toCol = moveData.getInt("toCol");

            if (isValidMove(fromRow, fromCol, toRow, toCol)) {
                // 执行移动
                Move move = new Move(fromRow, fromCol, toRow, toCol, board[toRow][toCol]);
                makeMove(move);
                moveHistory.push(move);

                // 检查游戏结束
                if (isCheckmate(getOpponentColor(turnColor))) {
                    isGameOver = true;
                    gameResult = (turnColor == WHITE ? "白方" : "黑方") + "获胜";
                } else if (isStalemate(getOpponentColor(turnColor))) {
                    isGameOver = true;
                    gameResult = "和棋";
                } else {
                    // 切换回合
                    turnColor = getOpponentColor(turnColor);
                    currentPlayer = (turnColor == WHITE ? whitePlayerName : blackPlayerName);
                }

                return true;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return false;
    }

    private boolean isValidMove(int fromRow, int fromCol, int toRow, int toCol) {
        if (fromRow < 0 || fromRow >= BOARD_SIZE || fromCol < 0 || fromCol >= BOARD_SIZE ||
            toRow < 0 || toRow >= BOARD_SIZE || toCol < 0 || toCol >= BOARD_SIZE) {
            return false;
        }

        ChessPiece piece = board[fromRow][fromCol];
        if (piece.getType() == EMPTY || piece.getColor() != turnColor) {
            return false;
        }

        ChessPiece targetPiece = board[toRow][toCol];
        if (targetPiece.getType() != EMPTY && targetPiece.getColor() == turnColor) {
            return false;
        }

        // 检查具体棋子的移动规则
        return isValidPieceMove(piece.getType(), fromRow, fromCol, toRow, toCol);
    }

    private boolean isValidPieceMove(int pieceType, int fromRow, int fromCol, int toRow, int toCol) {
        int deltaRow = toRow - fromRow;
        int deltaCol = toCol - fromCol;

        switch (pieceType) {
            case PAWN:
                return isValidPawnMove(fromRow, fromCol, toRow, toCol, deltaRow, deltaCol);
            case ROOK:
                return isValidRookMove(fromRow, fromCol, toRow, toCol, deltaRow, deltaCol);
            case KNIGHT:
                return isValidKnightMove(deltaRow, deltaCol);
            case BISHOP:
                return isValidBishopMove(fromRow, fromCol, toRow, toCol, deltaRow, deltaCol);
            case QUEEN:
                return isValidQueenMove(fromRow, fromCol, toRow, toCol, deltaRow, deltaCol);
            case KING:
                return isValidKingMove(deltaRow, deltaCol);
            default:
                return false;
        }
    }

    private boolean isValidPawnMove(int fromRow, int fromCol, int toRow, int toCol, int deltaRow, int deltaCol) {
        int direction = (board[fromRow][fromCol].getColor() == WHITE) ? -1 : 1;
        int startRow = (board[fromRow][fromCol].getColor() == WHITE) ? 6 : 1;

        // 前进一格
        if (deltaCol == 0 && deltaRow == direction && board[toRow][toCol].getType() == EMPTY) {
            return true;
        }

        // 前进两格（从起始位置）
        if (deltaCol == 0 && deltaRow == 2 * direction && fromRow == startRow &&
            board[toRow][toCol].getType() == EMPTY &&
            board[fromRow + direction][fromCol].getType() == EMPTY) {
            return true;
        }

        // 吃子
        if (Math.abs(deltaCol) == 1 && deltaRow == direction && board[toRow][toCol].getType() != EMPTY) {
            return true;
        }

        return false;
    }

    private boolean isValidRookMove(int fromRow, int fromCol, int toRow, int toCol, int deltaRow, int deltaCol) {
        if (deltaRow != 0 && deltaCol != 0) return false;
        return isPathClear(fromRow, fromCol, toRow, toCol);
    }

    private boolean isValidKnightMove(int deltaRow, int deltaCol) {
        return (Math.abs(deltaRow) == 2 && Math.abs(deltaCol) == 1) ||
               (Math.abs(deltaRow) == 1 && Math.abs(deltaCol) == 2);
    }

    private boolean isValidBishopMove(int fromRow, int fromCol, int toRow, int toCol, int deltaRow, int deltaCol) {
        if (Math.abs(deltaRow) != Math.abs(deltaCol)) return false;
        return isPathClear(fromRow, fromCol, toRow, toCol);
    }

    private boolean isValidQueenMove(int fromRow, int fromCol, int toRow, int toCol, int deltaRow, int deltaCol) {
        if (deltaRow == 0 && deltaCol == 0) return false;
        if (Math.abs(deltaRow) == Math.abs(deltaCol) || deltaRow == 0 || deltaCol == 0) {
            return isPathClear(fromRow, fromCol, toRow, toCol);
        }
        return false;
    }

    private boolean isValidKingMove(int deltaRow, int deltaCol) {
        return Math.abs(deltaRow) <= 1 && Math.abs(deltaCol) <= 1 && !(deltaRow == 0 && deltaCol == 0);
    }

    private boolean isPathClear(int fromRow, int fromCol, int toRow, int toCol) {
        int stepRow = Integer.compare(toRow, fromRow);
        int stepCol = Integer.compare(toCol, fromCol);

        int currentRow = fromRow + stepRow;
        int currentCol = fromCol + stepCol;

        while (currentRow != toRow || currentCol != toCol) {
            if (board[currentRow][currentCol].getType() != EMPTY) {
                return false;
            }
            currentRow += stepRow;
            currentCol += stepCol;
        }

        return true;
    }

    private void makeMove(Move move) {
        board[move.toRow][move.toCol] = board[move.fromRow][move.fromCol];
        board[move.fromRow][move.fromCol] = new ChessPiece(EMPTY, WHITE);
        board[move.toRow][move.toCol].setMoved(true);
        moveCount++;
    }

    private int getOpponentColor(int color) {
        return color == WHITE ? BLACK : WHITE;
    }

    private boolean isCheckmate(int color) {
        // 简化实现：检查是否被将军且无合法移动
        return isInCheck(color) && !hasLegalMoves(color);
    }

    private boolean isStalemate(int color) {
        // 简化实现：未被将军但无合法移动
        return !isInCheck(color) && !hasLegalMoves(color);
    }

    private boolean isInCheck(int color) {
        // 找到国王位置
        int kingRow = -1, kingCol = -1;
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                if (board[i][j].getType() == KING && board[i][j].getColor() == color) {
                    kingRow = i;
                    kingCol = j;
                    break;
                }
            }
        }

        if (kingRow == -1) return false;

        // 检查对方所有棋子是否能攻击国王
        int opponentColor = getOpponentColor(color);
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                if (board[i][j].getColor() == opponentColor) {
                    if (isValidMove(i, j, kingRow, kingCol)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean hasLegalMoves(int color) {
        for (int fromRow = 0; fromRow < BOARD_SIZE; fromRow++) {
            for (int fromCol = 0; fromCol < BOARD_SIZE; fromCol++) {
                if (board[fromRow][fromCol].getColor() == color) {
                    for (int toRow = 0; toRow < BOARD_SIZE; toRow++) {
                        for (int toCol = 0; toCol < BOARD_SIZE; toCol++) {
                            if (isValidMove(fromRow, fromCol, toRow, toCol)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    @Override
    public JSONObject getGameState() {
        JSONObject state = new JSONObject();
        try {
            state.put("gameType", getGameType());
            state.put("moveCount", moveCount);
            state.put("turnColor", turnColor);
            state.put("isGameOver", isGameOver);
            state.put("gameResult", gameResult);
            state.put("currentPlayer", currentPlayer);
            state.put("whitePlayerName", whitePlayerName);
            state.put("blackPlayerName", blackPlayerName);
            state.put("isAiEnabled", isAiEnabled);
            state.put("humanPlayerName", humanPlayerName);
            state.put("humanPlayerColor", humanPlayerColor);

            // 序列化棋盘
            JSONArray boardArray = new JSONArray();
            for (int i = 0; i < BOARD_SIZE; i++) {
                JSONArray rowArray = new JSONArray();
                for (int j = 0; j < BOARD_SIZE; j++) {
                    JSONObject pieceObj = new JSONObject();
                    pieceObj.put("type", board[i][j].getType());
                    pieceObj.put("color", board[i][j].getColor());
                    pieceObj.put("hasMoved", board[i][j].hasMoved());
                    rowArray.put(pieceObj);
                }
                boardArray.put(rowArray);
            }
            state.put("board", boardArray);

            // 序列化移动历史
            JSONArray historyArray = new JSONArray();
            for (Move move : moveHistory) {
                JSONObject moveObj = new JSONObject();
                moveObj.put("fromRow", move.fromRow);
                moveObj.put("fromCol", move.fromCol);
                moveObj.put("toRow", move.toRow);
                moveObj.put("toCol", move.toCol);
                moveObj.put("capturedType", move.capturedPiece.getType());
                moveObj.put("capturedColor", move.capturedPiece.getColor());
                historyArray.put(moveObj);
            }
            state.put("moveHistory", historyArray);

        } catch (JSONException e) {
            e.printStackTrace();
        }
        return state;
    }

    @Override
    public void setGameState(JSONObject state) {
        try {
            moveCount = state.getInt("moveCount");
            turnColor = state.getInt("turnColor");
            isGameOver = state.getBoolean("isGameOver");
            gameResult = state.optString("gameResult", null);
            currentPlayer = state.optString("currentPlayer", null);
            whitePlayerName = state.optString("whitePlayerName", null);
            blackPlayerName = state.optString("blackPlayerName", null);
            isAiEnabled = state.optBoolean("isAiEnabled", false);
            humanPlayerName = state.optString("humanPlayerName", null);
            humanPlayerColor = state.optInt("humanPlayerColor", WHITE);

            // 反序列化棋盘
            JSONArray boardArray = state.getJSONArray("board");
            for (int i = 0; i < BOARD_SIZE; i++) {
                JSONArray rowArray = boardArray.getJSONArray(i);
                for (int j = 0; j < BOARD_SIZE; j++) {
                    JSONObject pieceObj = rowArray.getJSONObject(j);
                    int type = pieceObj.getInt("type");
                    int color = pieceObj.getInt("color");
                    boolean hasMoved = pieceObj.getBoolean("hasMoved");
                    board[i][j] = new ChessPiece(type, color);
                    board[i][j].setMoved(hasMoved);
                }
            }

            // 反序列化移动历史
            JSONArray historyArray = state.getJSONArray("moveHistory");
            moveHistory.clear();
            for (int i = 0; i < historyArray.length(); i++) {
                JSONObject moveObj = historyArray.getJSONObject(i);
                int fromRow = moveObj.getInt("fromRow");
                int fromCol = moveObj.getInt("fromCol");
                int toRow = moveObj.getInt("toRow");
                int toCol = moveObj.getInt("toCol");
                int capturedType = moveObj.getInt("capturedType");
                int capturedColor = moveObj.getInt("capturedColor");
                ChessPiece capturedPiece = new ChessPiece(capturedType, capturedColor);
                Move move = new Move(fromRow, fromCol, toRow, toCol, capturedPiece);
                moveHistory.push(move);
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // 内部类用于表示移动
    private static class Move {
        int fromRow, fromCol, toRow, toCol;
        ChessPiece capturedPiece;

        Move(int fromRow, int fromCol, int toRow, int toCol, ChessPiece capturedPiece) {
            this.fromRow = fromRow;
            this.fromCol = fromCol;
            this.toRow = toRow;
            this.toCol = toCol;
            this.capturedPiece = capturedPiece;
        }
    }

    // 内部类用于表示棋子
    private static class ChessPiece {
        private int type;
        private int color;
        private boolean hasMoved;

        ChessPiece(int type, int color) {
            this.type = type;
            this.color = color;
            this.hasMoved = false;
        }

        int getType() { return type; }
        int getColor() { return color; }
        boolean hasMoved() { return hasMoved; }
        void setMoved(boolean moved) { this.hasMoved = moved; }
    }
}