package com.example.hakimichat;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int VIEW_TYPE_SENT = 1;
    private static final int VIEW_TYPE_RECEIVED = 2;
    private static final int VIEW_TYPE_GAME_INVITE = 3;

    private List<Message> messages;
    private SimpleDateFormat timeFormat;
    private OnGameActionListener gameActionListener;
    
    // 游戏操作监听器接口
    public interface OnGameActionListener {
        void onJoinGame(String gameId, String gameType);
        void onSpectateGame(String gameId, String gameType);
    }

    public MessageAdapter() {
        this.messages = new ArrayList<>();
        this.timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    }
    
    public void setGameActionListener(OnGameActionListener listener) {
        this.gameActionListener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        Message message = messages.get(position);
        
        // 如果是游戏邀请消息，使用特殊的布局
        if (message.getMessageType() == Message.TYPE_GAME_INVITE) {
            return VIEW_TYPE_GAME_INVITE;
        }
        
        int viewType = message.isSentByMe() ? VIEW_TYPE_SENT : VIEW_TYPE_RECEIVED;
        android.util.Log.d("MessageAdapter", "消息[" + position + "] isSentByMe=" + message.isSentByMe() + 
                ", viewType=" + (viewType == VIEW_TYPE_SENT ? "SENT" : "RECEIVED") + 
                ", sender=" + message.getSender() + ", content=" + message.getContent());
        return viewType;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_SENT) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message_sent, parent, false);
            return new SentMessageViewHolder(view);
        } else if (viewType == VIEW_TYPE_GAME_INVITE) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message_game_invite, parent, false);
            return new GameInviteViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message_received, parent, false);
            return new ReceivedMessageViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Message message = messages.get(position);
        
        if (holder instanceof SentMessageViewHolder) {
            ((SentMessageViewHolder) holder).bind(message);
        } else if (holder instanceof ReceivedMessageViewHolder) {
            ((ReceivedMessageViewHolder) holder).bind(message);
        } else if (holder instanceof GameInviteViewHolder) {
            ((GameInviteViewHolder) holder).bind(message);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public void addMessage(Message message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public void clearMessages() {
        messages.clear();
        notifyDataSetChanged();
    }
    
    /**
     * 更新游戏邀请卡片的状态
     */
    public void updateGameInviteCard(String gameId, int currentPlayers, int maxPlayers, boolean gameStarted, boolean gameEnded) {
        for (int i = 0; i < messages.size(); i++) {
            Message message = messages.get(i);
            if (message.getMessageType() == Message.TYPE_GAME_INVITE && 
                gameId.equals(message.getGameId())) {
                // 更新消息对象
                message.setCurrentPlayerCount(currentPlayers);
                message.setMaxPlayerCount(maxPlayers);
                message.setGameStarted(gameStarted);
                message.setGameEnded(gameEnded);
                // 通知适配器更新该项
                notifyItemChanged(i);
                break;
            }
        }
    }

    class SentMessageViewHolder extends RecyclerView.ViewHolder {
        TextView tvSender, tvMessage, tvTimestamp;

        SentMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSender = itemView.findViewById(R.id.tvSender);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
        }

        void bind(Message message) {
            String displayName = "我";
            if (message.isHost()) {
                displayName += "（房主）";
            }
            tvSender.setText(displayName);
            tvMessage.setText(message.getContent());
            tvTimestamp.setText(timeFormat.format(new Date(message.getTimestamp())));
            
            // 长按复制消息
            tvMessage.setOnLongClickListener(v -> {
                copyToClipboard(v.getContext(), message.getContent());
                return true;
            });
        }
    }

    class ReceivedMessageViewHolder extends RecyclerView.ViewHolder {
        TextView tvSender, tvMessage, tvTimestamp;

        ReceivedMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSender = itemView.findViewById(R.id.tvSender);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
        }

        void bind(Message message) {
            String displayName = message.getSender();
            if (message.isHost()) {
                displayName += "（房主）";
            }
            tvSender.setText(displayName);
            tvMessage.setText(message.getContent());
            tvTimestamp.setText(timeFormat.format(new Date(message.getTimestamp())));
            
            // 长按复制消息
            tvMessage.setOnLongClickListener(v -> {
                copyToClipboard(v.getContext(), message.getContent());
                return true;
            });
        }
    }
    
    /**
     * 复制消息到剪贴板
     */
    private void copyToClipboard(Context context, String text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("消息内容", text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
    }
    
    /**
     * 游戏邀请消息ViewHolder
     */
    class GameInviteViewHolder extends RecyclerView.ViewHolder {
        android.widget.ImageView ivGameIcon;
        TextView tvGameName, tvSender, tvGameStatus, tvTimestamp, tvDisabledHint;
        android.widget.Button btnJoinGame, btnSpectate;
        android.widget.LinearLayout llButtons;

        GameInviteViewHolder(@NonNull View itemView) {
            super(itemView);
            ivGameIcon = itemView.findViewById(R.id.ivGameIcon);
            tvGameName = itemView.findViewById(R.id.tvGameName);
            tvSender = itemView.findViewById(R.id.tvSender);
            tvGameStatus = itemView.findViewById(R.id.tvGameStatus);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            tvDisabledHint = itemView.findViewById(R.id.tvDisabledHint);
            btnJoinGame = itemView.findViewById(R.id.btnJoinGame);
            btnSpectate = itemView.findViewById(R.id.btnSpectate);
            llButtons = itemView.findViewById(R.id.llButtons);
        }

        void bind(Message message) {
            // 设置游戏图标
            String gameType = message.getGameType();
            int iconResId;
            if ("TicTacToe".equals(gameType)) {
                iconResId = R.drawable.ic_tictactoe;
            } else if ("Gobang".equals(gameType)) {
                iconResId = R.drawable.ic_gobang;
            } else if ("Chess".equals(gameType)) {
                iconResId = R.drawable.ic_chess;
            } else {
                iconResId = R.drawable.ic_game; // 默认图标
            }
            ivGameIcon.setImageResource(iconResId);
            
            // 设置游戏名称
            String gameName = message.getGameName();
            if (gameName == null || gameName.isEmpty()) {
                if ("TicTacToe".equals(gameType)) {
                    gameName = "井字棋";
                } else if ("Gobang".equals(gameType)) {
                    gameName = "五子棋";
                } else if ("Chess".equals(gameType)) {
                    gameName = "国际象棋";
                } else {
                    gameName = gameType;
                }
            }
            tvGameName.setText(gameName);
            
            // 设置发起人信息
            String senderText = message.getSender() + " 发起了游戏";
            tvSender.setText(senderText);
            
            // 设置游戏状态
            int currentPlayers = message.getCurrentPlayerCount();
            int maxPlayers = message.getMaxPlayerCount();
            boolean gameStarted = message.isGameStarted();
            boolean gameEnded = message.isGameEnded();
            
            if (gameEnded) {
                tvGameStatus.setText("游戏已结束");
            } else if (maxPlayers > 0) {
                if (gameStarted) {
                    tvGameStatus.setText("游戏进行中");
                } else {
                    tvGameStatus.setText("等待玩家加入 (" + currentPlayers + "/" + maxPlayers + ")");
                }
            } else {
                tvGameStatus.setText("等待玩家加入");
            }
            
            // 设置时间戳
            tvTimestamp.setText(timeFormat.format(new Date(message.getTimestamp())));
            
            // 根据游戏状态显示/隐藏按钮
            if (gameEnded) {
                // 游戏已结束（房主退出），禁用所有操作
                llButtons.setVisibility(View.GONE);
                tvDisabledHint.setVisibility(View.VISIBLE);
                tvDisabledHint.setText("游戏已结束");
                itemView.setOnClickListener(null);
            } else if (gameStarted) {
                // 游戏已开始，只显示观战按钮
                llButtons.setVisibility(View.VISIBLE);
                tvDisabledHint.setVisibility(View.GONE);
                btnJoinGame.setVisibility(View.GONE);
                btnSpectate.setVisibility(View.VISIBLE);
                
                // 观战按钮
                btnSpectate.setOnClickListener(v -> {
                    if (gameActionListener != null) {
                        gameActionListener.onSpectateGame(message.getGameId(), message.getGameType());
                    }
                });
                
                // 移除卡片点击事件
                itemView.setOnClickListener(null);
            } else if (currentPlayers >= maxPlayers && maxPlayers > 0) {
                // 游戏已满，只能观战
                llButtons.setVisibility(View.GONE);
                tvDisabledHint.setVisibility(View.VISIBLE);
                tvDisabledHint.setText("游戏人数已满，只能观战");
                
                // 设置卡片点击事件，点击可观战
                itemView.setOnClickListener(v -> {
                    if (gameActionListener != null) {
                        gameActionListener.onSpectateGame(message.getGameId(), message.getGameType());
                    }
                });
            } else {
                // 可以加入或观战
                llButtons.setVisibility(View.VISIBLE);
                tvDisabledHint.setVisibility(View.GONE);
                btnJoinGame.setVisibility(View.VISIBLE);
                btnSpectate.setVisibility(View.VISIBLE);
                
                // 加入游戏按钮
                btnJoinGame.setOnClickListener(v -> {
                    if (gameActionListener != null) {
                        gameActionListener.onJoinGame(message.getGameId(), message.getGameType());
                    }
                });
                
                // 观战按钮
                btnSpectate.setOnClickListener(v -> {
                    if (gameActionListener != null) {
                        gameActionListener.onSpectateGame(message.getGameId(), message.getGameType());
                    }
                });
                
                // 移除卡片点击事件
                itemView.setOnClickListener(null);
            }
        }
    }
}
