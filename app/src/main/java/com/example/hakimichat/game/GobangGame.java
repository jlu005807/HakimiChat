package com.example.hakimichat.game;

import android.graphics.Point;

import com.example.hakimichat.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Stack;

public class GobangGame extends BaseGame {

    public static final int BOARD_SIZE = 15;
    public static final int EMPTY = 0;
    public static final int BLACK_PIECE = 1;
    public static final int WHITE_PIECE = 2;
    private static final String GAME_TYPE = "Gobang";
    private static final int MAX_SEARCH_DEPTH = 4;
    private static final long TIME_LIMIT_MS = 2500;

    private int[][] board = new int[BOARD_SIZE][BOARD_SIZE];
    private int moveCount = 0;
    private int turnColor = BLACK_PIECE;
    private String blackPlayerName;
    private String whitePlayerName;
    private Point winLineStart = null;
    private Point winLineEnd = null;

    private boolean isAiEnabled = false;
    private boolean isStrictMode = false;
    private int humanPlayerColor = BLACK_PIECE;
    private String humanPlayerName = null;  // 记录真人玩家名字

    private final Stack<Point> moveHistory = new Stack<>();

    // AI相关
    private static final int[][] POSITIONAL_VALUE = {
            {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0},
            {0, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 1, 0},
            {0, 1, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 2, 1, 0},
            {0, 1, 2, 3, 4, 4, 4, 4, 4, 4, 4, 3, 2, 1, 0},
            {0, 1, 2, 3, 4, 5, 5, 5, 5, 5, 4, 3, 2, 1, 0},
            {0, 1, 2, 3, 4, 5, 6, 6, 6, 5, 4, 3, 2, 1, 0},
            {0, 1, 2, 3, 4, 5, 6, 7, 6, 5, 4, 3, 2, 1, 0},
            {0, 1, 2, 3, 4, 5, 6, 6, 6, 5, 4, 3, 2, 1, 0},
            {0, 1, 2, 3, 4, 5, 5, 5, 5, 5, 4, 3, 2, 1, 0},
            {0, 1, 2, 3, 4, 4, 4, 4, 4, 4, 4, 3, 2, 1, 0},
            {0, 1, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 2, 1, 0},
            {0, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 1, 0},
            {0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}
    };
    private final long[][][] zobristTable = new long[BOARD_SIZE][BOARD_SIZE][3];
    private long currentZobristKey = 0;
    private final Map<Long, TranspositionEntry> transpositionTable = new HashMap<>();
    private long startTime;

    public GobangGame() {
        super();
        initZobrist();
        initGame();
    }

    private void initZobrist() {
        Random rand = new Random();
        for (int i = 0; i < BOARD_SIZE; i++)
            for (int j = 0; j < BOARD_SIZE; j++)
                for (int k = 1; k <= 2; k++) {
                    zobristTable[i][j][k] = rand.nextLong();
                }
    }

    @Override
    public void initGame() {
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                board[i][j] = EMPTY;
            }
        }
        moveCount = 0;
        turnColor = BLACK_PIECE;
        isGameOver = false;
        gameResult = null;
        moveHistory.clear();
        // Player assignments are handled by addPlayer
    }

    @Override
    public void reset() {
        initGame();
        // Keep players, but reset board and state
        if (players.size() >= 1) blackPlayerName = players.get(0);
        if (players.size() >= 2) whitePlayerName = players.get(1);
        currentPlayer = blackPlayerName;
    }

    @Override
    public String getGameType() {
        return GAME_TYPE;
    }

    @Override
    public String getGameName() {
        return "五子棋";
    }

    @Override
    public String getGameDescription() {
        return "策略对战，连成五子获胜";
    }

    @Override
    public int getGameIcon() {
        return R.drawable.ic_gobang;
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
                blackPlayerName = player;
                currentPlayer = blackPlayerName;
            } else if (players.size() == 2) {
                whitePlayerName = player;
                isGameStarted = true;

                // 如果不是AI模式（真人对战），随机决定谁是黑方
                if (!isAiEnabled) {
                    Random random = new Random();
                    if (random.nextBoolean()) {
                        // 交换黑白方
                        String temp = blackPlayerName;
                        blackPlayerName = whitePlayerName;
                        whitePlayerName = temp;
                        currentPlayer = blackPlayerName;
                    }
                } else {
                    // AI模式：现在可以确定humanPlayerColor了
                    if (humanPlayerName != null) {
                        if (humanPlayerName.equals(blackPlayerName)) {
                            this.humanPlayerColor = BLACK_PIECE;
                        } else {
                            this.humanPlayerColor = WHITE_PIECE;
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
            // humanPlayerColor在addPlayer完成后再设置
        }
    }

    public boolean isAiEnabled() {
        return isAiEnabled;
    }

    public String getBlackPlayerName() {
        return blackPlayerName;
    }

    public String getWhitePlayerName() {
        return whitePlayerName;
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
            int x = moveData.getInt("x");
            int y = moveData.getInt("y");

            if (!isValid(x, y) || board[x][y] != EMPTY) {
                return false;
            }

            // Strict mode rules for black player
            if (isStrictMode && turnColor == BLACK_PIECE) {
                if (moveCount == 0 && (x != BOARD_SIZE / 2 || y != BOARD_SIZE / 2)) return false;
                if (isForbiddenMoveForBlack(x, y)) return false;
            }

            updateBoardState(x, y, turnColor);
            checkGameStatus(x, y);
            return true;
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void updateBoardState(int x, int y, int pieceType) {
        updateBoardState(x, y, pieceType, true);
    }
    
    private void updateBoardState(int x, int y, int pieceType, boolean updateHistory) {
        int originalPiece = board[x][y];
        if (originalPiece != EMPTY) currentZobristKey ^= zobristTable[x][y][originalPiece];
        if (pieceType != EMPTY) currentZobristKey ^= zobristTable[x][y][pieceType];
        board[x][y] = pieceType;
        if (pieceType != EMPTY) {
            moveCount++;
            if (updateHistory) {
                moveHistory.push(new Point(x, y));
            }
        } else {
            moveCount--;
        }
    }

    private void checkGameStatus(int x, int y) {
        if (checkWin(x, y)) {
            isGameOver = true;
            gameResult = currentPlayer + " 获胜！";
        } else if (moveCount == BOARD_SIZE * BOARD_SIZE) {
            isGameOver = true;
            gameResult = "平局！";
        } else {
            turnColor = (turnColor == BLACK_PIECE) ? WHITE_PIECE : BLACK_PIECE;
            currentPlayer = (currentPlayer.equals(blackPlayerName)) ? whitePlayerName : blackPlayerName;
        }
    }

    /**
     * 检查是否可以悔棋
     * 轮到玩家且棋盘上棋子大于等于2时才能悔棋
     *
     * @return 是否可以悔棋
     */
    public boolean canUndo() {
        if (!isAiEnabled || isGameOver) return false;

        // 至少需要有2步棋
        if (moveCount < 2) return false;

        // 检查当前轮到的是否是真人玩家
        String humanPlayer = (humanPlayerColor == BLACK_PIECE) ? blackPlayerName : whitePlayerName;
        return currentPlayer != null && currentPlayer.equals(humanPlayer);
    }

    public boolean undoMove() {
        android.util.Log.d("GobangGame", "undoMove: isAiEnabled=" + isAiEnabled + ", moveCount=" + moveCount + ", moveHistory.size=" + moveHistory.size());

        if (moveCount < 2) return false;

        // 悔两步
        int undoSteps = 0;
        for (int i = 0; i < 2; i++) {
            if (moveHistory.isEmpty()) break;
            Point last = moveHistory.pop();
            updateBoardState(last.x, last.y, EMPTY);
            turnColor = (turnColor == BLACK_PIECE) ? WHITE_PIECE : BLACK_PIECE;
            undoSteps++;
        }
        android.util.Log.d("GobangGame", "AI mode: undid " + undoSteps + " steps");

        currentPlayer = (turnColor == BLACK_PIECE) ? blackPlayerName : whitePlayerName;
        isGameOver = false;
        gameResult = null;
        winLineStart = null;
        winLineEnd = null;
        return true;
    }

    private boolean checkWin(int x, int y) {
        int pieceType = board[x][y];
        int countInLine = getLineCountForWinCheck(x, y, pieceType);
        if (isStrictMode) {
            if (pieceType == BLACK_PIECE) return countInLine == 5;
            if (pieceType == WHITE_PIECE) return countInLine >= 5;
        }
        return countInLine >= 5;
    }

    private int getLineCountForWinCheck(int x, int y, int pieceType) {
        int[][] directions = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
        int maxCount = 0;
        for (int[] dir : directions) {
            int count = 1;
            Point start = new Point(x, y);
            Point end = new Point(x, y);
            for (int i = 1; i < 6; i++) {
                int nx = x + i * dir[0], ny = y + i * dir[1];
                if (isValid(nx, ny) && board[nx][ny] == pieceType) {
                    count++;
                    end.set(nx, ny);
                } else break;
            }
            for (int i = 1; i < 6; i++) {
                int nx = x - i * dir[0], ny = y - i * dir[1];
                if (isValid(nx, ny) && board[nx][ny] == pieceType) {
                    count++;
                    start.set(nx, ny);
                } else break;
            }
            if (count > maxCount) maxCount = count;
            if (count >= 5) {
                winLineStart = start;
                winLineEnd = end;
                if (isStrictMode && pieceType == BLACK_PIECE && count > 5) continue;
                return count;
            }
        }
        return maxCount;
    }

    private boolean isForbiddenMoveForBlack(int x, int y) {
        if (isWinningMoveForBlack(x, y)) return false;
        if (checkLineCountForBlack(x, y) > 5) return true;
        int liveThrees = 0, fours = 0;
        int[][] directions = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
        board[x][y] = BLACK_PIECE;
        for (int[] dir : directions) {
            String lineType = getLineType(x, y, dir[0], dir[1], BLACK_PIECE);
            if (lineType.equals("LIVE_THREE")) liveThrees++;
            if (lineType.equals("FOUR")) fours++;
        }
        board[x][y] = EMPTY;
        return liveThrees >= 2 || fours >= 2;
    }

    private boolean isWinningMoveForBlack(int x, int y) {
        return checkLineCountForBlack(x, y) >= 5;
    }

    private int checkLineCountForBlack(int x, int y) {
        int maxCount = 0;
        int[][] directions = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
        board[x][y] = BLACK_PIECE;
        for (int[] dir : directions) {
            int count = 1;
            for (int i = 1; i < 6; i++) {
                if (isValid(x + i * dir[0], y + i * dir[1]) && board[x + i * dir[0]][y + i * dir[1]] == BLACK_PIECE)
                    count++;
                else break;
            }
            for (int i = 1; i < 6; i++) {
                if (isValid(x - i * dir[0], y - i * dir[1]) && board[x - i * dir[0]][y - i * dir[1]] == BLACK_PIECE)
                    count++;
                else break;
            }
            if (count > maxCount) maxCount = count;
        }
        board[x][y] = EMPTY;
        return maxCount;
    }

    private String getLineType(int x, int y, int dx, int dy, int pieceType) {
        int count = 1, openEnds = 0;
        for (int i = 1; i < 6; i++) {
            int nx = x + i * dx, ny = y + i * dy;
            if (isValid(nx, ny) && board[nx][ny] == pieceType) count++;
            else {
                if (isValid(nx, ny) && board[nx][ny] == EMPTY) openEnds++;
                break;
            }
        }
        for (int i = 1; i < 6; i++) {
            int nx = x - i * dx, ny = y - i * dy;
            if (isValid(nx, ny) && board[nx][ny] == pieceType) count++;
            else {
                if (isValid(nx, ny) && board[nx][ny] == EMPTY) openEnds++;
                break;
            }
        }
        if (count == 3 && openEnds == 2) return "LIVE_THREE";
        if (count == 4 && (openEnds == 1 || openEnds == 2)) return "FOUR";
        return "";
    }

    private boolean isValid(int x, int y) {
        return x >= 0 && x < BOARD_SIZE && y >= 0 && y < BOARD_SIZE;
    }

    @Override
    public JSONObject getGameState() {
        JSONObject state = new JSONObject();
        try {
            state.put("gameId", gameId);
            state.put("gameType", GAME_TYPE);
            state.put("isGameStarted", isGameStarted);
            state.put("isGameOver", isGameOver);
            state.put("currentPlayer", currentPlayer);
            state.put("gameResult", gameResult);
            state.put("moveCount", moveCount);
            state.put("blackPlayerName", blackPlayerName);
            state.put("whitePlayerName", whitePlayerName);
            state.put("players", playersToJson());
            state.put("spectators", spectatorsToJson());
            state.put("isStrictMode", isStrictMode);
            state.put("isAiEnabled", isAiEnabled);
            state.put("humanPlayerName", humanPlayerName);
            state.put("humanPlayerColor", humanPlayerColor);

            JSONArray boardArray = new JSONArray();
            for (int i = 0; i < BOARD_SIZE; i++) {
                JSONArray row = new JSONArray();
                for (int j = 0; j < BOARD_SIZE; j++) {
                    row.put(board[i][j]);
                }
                boardArray.put(row);
            }
            state.put("board", boardArray);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return state;
    }

    @Override
    public void setGameState(JSONObject gameState) {
        try {
            this.gameId = gameState.getString("gameId");
            this.isGameStarted = gameState.getBoolean("isGameStarted");
            this.isGameOver = gameState.getBoolean("isGameOver");
            this.currentPlayer = gameState.optString("currentPlayer");
            this.gameResult = gameState.optString("gameResult");
            this.moveCount = gameState.getInt("moveCount");
            this.blackPlayerName = gameState.optString("blackPlayerName");
            this.whitePlayerName = gameState.optString("whitePlayerName");
            this.isStrictMode = gameState.optBoolean("isStrictMode", false);
            this.isAiEnabled = gameState.optBoolean("isAiEnabled", false);
            this.humanPlayerName = gameState.optString("humanPlayerName", null);
            this.humanPlayerColor = gameState.optInt("humanPlayerColor", BLACK_PIECE);

            playersFromJson(gameState.getJSONArray("players"));
            spectatorsFromJson(gameState.getJSONArray("spectators"));

            JSONArray boardArray = gameState.getJSONArray("board");
            for (int i = 0; i < boardArray.length(); i++) {
                JSONArray row = boardArray.getJSONArray(i);
                for (int j = 0; j < row.length(); j++) {
                    this.board[i][j] = row.getInt(j);
                }
            }

            // Recalculate turn color based on move count
            this.turnColor = (this.moveCount % 2 == 0) ? BLACK_PIECE : WHITE_PIECE;

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // Complete AI for single player mode
    public Point getAiMove() {
        if (isGameOver) return null;
        startTime = System.currentTimeMillis();
        transpositionTable.clear();

        int aiColor = (humanPlayerColor == BLACK_PIECE) ? WHITE_PIECE : BLACK_PIECE;

        // 1. Find kill move (double threats)
        Point killMove = findKillMove(aiColor);
        if (killMove != null) return killMove;

        // 2. Block opponent's kill move
        Point urgentDefense = findKillMove(humanPlayerColor);
        if (urgentDefense != null) return urgentDefense;

        // 3. Find winning move
        Point winningMove = findWinningMove(aiColor);
        if (winningMove != null) return winningMove;

        // 4. Block opponent's winning move
        Point defensiveMove = findWinningMove(humanPlayerColor);
        if (defensiveMove != null) return defensiveMove;

        // 5. Opening moves
        if (moveCount <= 1) {
            if (board[7][7] == EMPTY) return new Point(7, 7);
            int[] offsets = {0, 1, -1};
            for (int dx : offsets)
                for (int dy : offsets)
                    if (dx != 0 || dy != 0)
                        if (isValid(7 + dx, 7 + dy) && board[7 + dx][7 + dy] == EMPTY)
                            return new Point(7 + dx, 7 + dy);
        }

        // 6. Iterative deepening search
        return findBestMoveByIterativeDeepening();
    }

    private Point findBestMoveByIterativeDeepening() {
        Point bestMove = null;
        for (int depth = 1; depth <= MAX_SEARCH_DEPTH; depth++) {
            Point currentBestMove = findBestMoveAtDepth(depth);
            if (System.currentTimeMillis() - startTime > TIME_LIMIT_MS) {
                return bestMove != null ? bestMove : currentBestMove;
            }
            bestMove = currentBestMove;
        }
        return bestMove;
    }

    private Point findBestMoveAtDepth(int depth) {
        Point bestMove = null;
        int bestScore = Integer.MIN_VALUE;
        List<Point> moves = generateMoves();
        if (moves.isEmpty()) {
            for (int i = 0; i < BOARD_SIZE; i++)
                for (int j = 0; j < BOARD_SIZE; j++)
                    if (board[i][j] == EMPTY) return new Point(i, j);
        }
        for (Point move : moves) {
            updateBoardState(move.x, move.y, turnColor, false);  // AI搜索不更新history
            int score = minimax(depth - 1, Integer.MIN_VALUE, Integer.MAX_VALUE, false,
                    (turnColor == BLACK_PIECE) ? WHITE_PIECE : BLACK_PIECE);
            updateBoardState(move.x, move.y, EMPTY, false);  // AI搜索不更新history
            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
        }
        return bestMove;
    }

    private int minimax(int depth, int alpha, int beta, boolean isMaximizingPlayer, int currentTurnColor) {
        if (System.currentTimeMillis() - startTime > TIME_LIMIT_MS) return 0;
        TranspositionEntry entry = transpositionTable.get(currentZobristKey);
        if (entry != null && entry.depth >= depth) return entry.score;

        int immediateWinner = getImmediateWinner();
        if (immediateWinner != EMPTY) return evaluate(immediateWinner);
        if (depth == 0) return evaluateBoard();

        List<Point> moves = generateMoves();
        if (moves.isEmpty()) return evaluateBoard();

        int nextTurnColor = (currentTurnColor == BLACK_PIECE) ? WHITE_PIECE : BLACK_PIECE;
        if (isMaximizingPlayer) {
            int maxEval = Integer.MIN_VALUE;
            for (Point move : moves) {
                updateBoardState(move.x, move.y, currentTurnColor, false);  // AI搜索不更新history
                int eval = minimax(depth - 1, alpha, beta, false, nextTurnColor);
                updateBoardState(move.x, move.y, EMPTY, false);  // AI搜索不更新history
                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);
                if (beta <= alpha) break;
            }
            transpositionTable.put(currentZobristKey, new TranspositionEntry(maxEval, depth));
            return maxEval;
        } else {
            int minEval = Integer.MAX_VALUE;
            for (Point move : moves) {
                updateBoardState(move.x, move.y, currentTurnColor, false);  // AI搜索不更新history
                int eval = minimax(depth - 1, alpha, beta, true, nextTurnColor);
                updateBoardState(move.x, move.y, EMPTY, false);  // AI搜索不更新history
                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);
                if (beta <= alpha) break;
            }
            transpositionTable.put(currentZobristKey, new TranspositionEntry(minEval, depth));
            return minEval;
        }
    }

    private int evaluate(int winner) {
        int aiColor = (humanPlayerColor == BLACK_PIECE) ? WHITE_PIECE : BLACK_PIECE;
        if (winner == aiColor) return 1000000;
        if (winner == humanPlayerColor) return -1000000;
        return 0;
    }

    private int evaluateBoard() {
        int aiColor = (humanPlayerColor == BLACK_PIECE) ? WHITE_PIECE : BLACK_PIECE;
        return calculateTotalScore(aiColor) - calculateTotalScore(humanPlayerColor);
    }

    private int calculateTotalScore(int pieceType) {
        int totalScore = 0;
        for (int i = 0; i < BOARD_SIZE; i++)
            for (int j = 0; j < BOARD_SIZE; j++) {
                if (board[i][j] == pieceType) totalScore += POSITIONAL_VALUE[i][j];
            }
        totalScore += calculateLineScore(pieceType);
        return totalScore;
    }

    private int calculateLineScore(int pieceType) {
        int score = 0;
        boolean[][][] visited = new boolean[BOARD_SIZE][BOARD_SIZE][4];
        int[][] directions = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
        for (int i = 0; i < BOARD_SIZE; i++)
            for (int j = 0; j < BOARD_SIZE; j++) {
                if (board[i][j] == pieceType) {
                    for (int d = 0; d < 4; d++) {
                        if (!visited[i][j][d]) {
                            int[] result = countLineOnBoard(i, j, directions[d], pieceType);
                            score += getScoreFromLine(result[0], result[1]);
                            for (int k = 0; k < result[0]; k++) {
                                int ni = i + k * directions[d][0], nj = j + k * directions[d][1];
                                if (isValid(ni, nj)) visited[ni][nj][d] = true;
                            }
                        }
                    }
                }
            }
        return score;
    }

    private int[] countLineOnBoard(int x, int y, int[] dir, int pieceType) {
        int count = 1;
        int openEnds = 0;
        int opponentType = (pieceType == BLACK_PIECE) ? WHITE_PIECE : BLACK_PIECE;
        // Forward
        for (int i = 1; i < 5; i++) {
            int curX = x + i * dir[0], curY = y + i * dir[1];
            if (!isValid(curX, curY) || board[curX][curY] == opponentType) break;
            if (board[curX][curY] == pieceType) count++;
            else {
                openEnds++;
                break;
            }
        }
        // Backward
        for (int i = 1; i < 5; i++) {
            int curX = x - i * dir[0], curY = y - i * dir[1];
            if (!isValid(curX, curY) || board[curX][curY] == opponentType) break;
            if (board[curX][curY] == pieceType) count++;
            else {
                openEnds++;
                break;
            }
        }
        return new int[]{count, openEnds};
    }

    private int getScoreFromLine(int count, int openEnds) {
        if (count >= 5) return 500000;
        if (count == 4) return (openEnds == 2) ? 50000 : 500;
        if (count == 3) return (openEnds == 2) ? 200 : 10;
        if (count == 2) return (openEnds == 2) ? 5 : 0;
        return 0;
    }

    private List<Point> generateMoves() {
        List<PointScore> scoredMoves = new ArrayList<>();
        for (int i = 0; i < BOARD_SIZE; i++)
            for (int j = 0; j < BOARD_SIZE; j++) {
                if (board[i][j] == EMPTY && hasNeighbor(i, j)) {
                    int score = scoreSingleLine(i, j, turnColor) + scoreSingleLine(i, j, humanPlayerColor);
                    scoredMoves.add(new PointScore(new Point(i, j), score));
                }
            }
        scoredMoves.sort((a, b) -> b.score - a.score);
        List<Point> moves = new ArrayList<>();
        for (PointScore sm : scoredMoves) moves.add(sm.point);
        if (moves.isEmpty() && moveCount < 2) {
            if (board[7][7] == EMPTY) moves.add(new Point(7, 7));
            else moves.add(new Point(6, 6));
        }
        return moves;
    }

    private int scoreSingleLine(int x, int y, int pieceType) {
        int score = 0;
        int[][] directions = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
        for (int[] dir : directions) {
            int[] result = countLine(x, y, dir, pieceType);
            score += getScoreFromLine(result[0], result[1]);
        }
        return score;
    }

    private int[] countLine(int x, int y, int[] dir, int pieceType) {
        board[x][y] = pieceType;
        int count = 0, openEnds = 0;
        int opponentType = (pieceType == BLACK_PIECE) ? WHITE_PIECE : BLACK_PIECE;
        for (int i = 1; i < 5; i++) {
            int curX = x - i * dir[0], curY = y - i * dir[1];
            if (!isValid(curX, curY) || board[curX][curY] == opponentType) {
                openEnds = 0;
                break;
            }
            if (board[curX][curY] == pieceType) count++;
            else {
                openEnds++;
                break;
            }
        }
        for (int i = 1; i < 5; i++) {
            int curX = x + i * dir[0], curY = y + i * dir[1];
            if (!isValid(curX, curY) || board[curX][curY] == opponentType) {
                break;
            }
            if (board[curX][curY] == pieceType) count++;
            else {
                openEnds++;
                break;
            }
        }
        board[x][y] = EMPTY;
        return new int[]{count + 1, openEnds};
    }

    private Point findKillMove(int pieceType) {
        List<Point> candidates = generateMoves();
        for (Point p : candidates) {
            board[p.x][p.y] = pieceType;
            int liveThrees = 0, fours = 0;
            int[][] directions = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
            for (int[] dir : directions) {
                String lineType = getLineType(p.x, p.y, dir[0], dir[1], pieceType);
                if (lineType.equals("LIVE_THREE")) liveThrees++;
                if (lineType.equals("FOUR")) fours++;
            }
            board[p.x][p.y] = EMPTY;
            if (fours >= 1 && liveThrees >= 1) return p;
            if (liveThrees >= 2) return p;
        }
        return null;
    }

    private Point findWinningMove(int pieceType) {
        for (int i = 0; i < BOARD_SIZE; i++)
            for (int j = 0; j < BOARD_SIZE; j++) {
                if (board[i][j] == EMPTY && hasNeighbor(i, j)) {
                    board[i][j] = pieceType;
                    if (checkWin(i, j)) {
                        board[i][j] = EMPTY;
                        return new Point(i, j);
                    }
                    board[i][j] = EMPTY;
                }
            }
        return null;
    }

    private int getImmediateWinner() {
        for (int i = 0; i < BOARD_SIZE; i++)
            for (int j = 0; j < BOARD_SIZE; j++)
                if (board[i][j] != EMPTY) if (checkWin(i, j)) return board[i][j];
        return EMPTY;
    }

    private boolean hasNeighbor(int x, int y) {
        for (int i = -1; i <= 1; i++)
            for (int j = -1; j <= 1; j++) {
                if (i == 0 && j == 0) continue;
                if (isValid(x + i, y + j) && board[x + i][y + j] != EMPTY) return true;
            }
        return false;
    }

    private static class PointScore {
        Point point;
        int score;

        PointScore(Point point, int score) {
            this.point = point;
            this.score = score;
        }
    }

    private static class TranspositionEntry {
        int score;
        int depth;

        TranspositionEntry(int score, int depth) {
            this.score = score;
            this.depth = depth;
        }
    }
}
