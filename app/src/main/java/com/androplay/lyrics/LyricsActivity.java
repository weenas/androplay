package com.androplay.lyrics;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.androplay.R;

/**
 * Lyrics Activity - Manages lyrics library and settings
 */
public class LyricsActivity extends AppCompatActivity {
    
    private static final String TAG = "LyricsActivity";
    private static final String EXTRA_ACTION = "action";
    
    private static final int ACTION_LIBRARY = 1;
    private static final int ACTION_SYNC_SETTINGS = 2;
    private static final int ACTION_STYLE_SETTINGS = 3;
    
    private TextView titleText;
    private TextView contentText;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lyrics);
        
        titleText = findViewById(R.id.lyrics_title);
        contentText = findViewById(R.id.lyrics_content);
        
        handleIntent(getIntent());
    }
    
    private void handleIntent(Intent intent) {
        if (intent == null) return;
        
        int action = intent.getIntExtra(EXTRA_ACTION, ACTION_LIBRARY);
        
        switch (action) {
            case ACTION_LIBRARY:
                showLyricsLibrary();
                break;
            case ACTION_SYNC_SETTINGS:
                showSyncSettings();
                break;
            case ACTION_STYLE_SETTINGS:
                showStyleSettings();
                break;
        }
    }
    
    private void showLyricsLibrary() {
        titleText.setText("歌词库");
        contentText.setText("歌词库功能开发中...\n\n支持功能：\n• 导入本地歌词文件\n• 在线歌词搜索\n• 歌词编辑\n• 歌词同步校准");
    }
    
    private void showSyncSettings() {
        titleText.setText("歌词同步设置");
        contentText.setText("歌词同步设置功能开发中...\n\n支持功能：\n• 手动调整歌词偏移\n• 自动同步检测\n• 同步精度设置\n• 同步测试工具");
    }
    
    private void showStyleSettings() {
        titleText.setText("歌词样式设置");
        contentText.setText("歌词样式设置功能开发中...\n\n支持功能：\n• 字体大小调整\n• 字体颜色选择\n• 背景透明度\n• 动画效果设置");
    }
    
    // Static methods to start the activity
    public static void showLyricsLibrary(Context context) {
        Intent intent = new Intent(context, LyricsActivity.class);
        intent.putExtra(EXTRA_ACTION, ACTION_LIBRARY);
        context.startActivity(intent);
    }
    
    public static void showSyncSettings(Context context) {
        Intent intent = new Intent(context, LyricsActivity.class);
        intent.putExtra(EXTRA_ACTION, ACTION_SYNC_SETTINGS);
        context.startActivity(intent);
    }
    
    public static void showStyleSettings(Context context) {
        Intent intent = new Intent(context, LyricsActivity.class);
        intent.putExtra(EXTRA_ACTION, ACTION_STYLE_SETTINGS);
        context.startActivity(intent);
    }
}