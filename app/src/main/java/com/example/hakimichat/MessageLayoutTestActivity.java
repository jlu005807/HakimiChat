package com.example.hakimichat;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * 测试消息布局显示的Activity
 */
public class MessageLayoutTestActivity extends AppCompatActivity {
    
    private RecyclerView recyclerView;
    private MessageAdapter adapter;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 创建RecyclerView
        recyclerView = new RecyclerView(this);
        setContentView(recyclerView);
        
        // 设置布局管理器
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        
        // 创建适配器
        adapter = new MessageAdapter();
        recyclerView.setAdapter(adapter);
        
        // 添加测试消息
        addTestMessages();
    }
    
    private void addTestMessages() {
        // 添加接收的消息（左侧，白色）
        Message received1 = new Message("张三", "你好！");
        received1.setSentByMe(false);
        adapter.addMessage(received1);
        
        // 添加发送的消息（右侧，蓝色）
        Message sent1 = new Message("我", "你好，欢迎！");
        sent1.setSentByMe(true);
        adapter.addMessage(sent1);
        
        // 添加接收的消息
        Message received2 = new Message("李四", "大家好");
        received2.setSentByMe(false);
        adapter.addMessage(received2);
        
        // 添加发送的消息
        Message sent2 = new Message("我", "欢迎欢迎");
        sent2.setSentByMe(true);
        adapter.addMessage(sent2);
        
        android.util.Log.d("MessageLayoutTest", "已添加测试消息");
    }
}
