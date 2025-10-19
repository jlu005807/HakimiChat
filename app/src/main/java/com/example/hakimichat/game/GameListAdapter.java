package com.example.hakimichat.game;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.hakimichat.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 游戏列表适配器
 */
public class GameListAdapter extends RecyclerView.Adapter<GameListAdapter.GameViewHolder> {
    
    private List<GameInfo> games;
    private OnGameClickListener listener;
    
    public interface OnGameClickListener {
        void onGameClick(GameInfo game);
    }
    
    public static class GameInfo {
        public String gameType;
        public String gameName;
        public String gameDescription;
        public int gameIcon;
        
        public GameInfo(String gameType, String gameName, String gameDescription, int gameIcon) {
            this.gameType = gameType;
            this.gameName = gameName;
            this.gameDescription = gameDescription;
            this.gameIcon = gameIcon;
        }
    }
    
    public GameListAdapter(OnGameClickListener listener) {
        this.games = new ArrayList<>();
        this.listener = listener;
        
        // 添加可用的游戏
        TicTacToeGame dummyGame = new TicTacToeGame();
        games.add(new GameInfo(
            dummyGame.getGameType(),
            dummyGame.getGameName(),
            dummyGame.getGameDescription(),
            dummyGame.getGameIcon()
        ));
        
        // 未来可以在这里添加更多游戏
    }
    
    @NonNull
    @Override
    public GameViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_game, parent, false);
        return new GameViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull GameViewHolder holder, int position) {
        GameInfo game = games.get(position);
        holder.bind(game, listener);
    }
    
    @Override
    public int getItemCount() {
        return games.size();
    }
    
    static class GameViewHolder extends RecyclerView.ViewHolder {
        ImageView ivGameIcon;
        TextView tvGameName;
        TextView tvGameDescription;
        
        public GameViewHolder(@NonNull View itemView) {
            super(itemView);
            ivGameIcon = itemView.findViewById(R.id.ivGameIcon);
            tvGameName = itemView.findViewById(R.id.tvGameName);
            tvGameDescription = itemView.findViewById(R.id.tvGameDescription);
        }
        
        public void bind(GameInfo game, OnGameClickListener listener) {
            ivGameIcon.setImageResource(game.gameIcon);
            tvGameName.setText(game.gameName);
            tvGameDescription.setText(game.gameDescription);
            
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onGameClick(game);
                }
            });
        }
    }
}
