package com.androplay;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.OnItemViewSelectedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;

import com.androplay.airplay.AirPlayManager;
import com.androplay.player.PlayerActivity;
import com.androplay.lyrics.LyricsActivity;

public class MainActivity extends AppCompatActivity {
    
    private BrowseSupportFragment browseFragment;
    private ArrayObjectAdapter rowsAdapter;
    private AirPlayManager airPlayManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Initialize AirPlay Manager
        airPlayManager = new AirPlayManager(this);
        
        // Setup browse fragment
        browseFragment = (BrowseSupportFragment) getSupportFragmentManager()
                .findFragmentById(R.id.browse_fragment);
        
        setupBrowseFragment();
    }
    
    private void setupBrowseFragment() {
        rowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());
        
        // Create categories
        createCategories();
        
        browseFragment.setAdapter(rowsAdapter);
        browseFragment.setOnItemViewSelectedListener(new OnItemViewSelectedListener() {
            @Override
            public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
                                     RowPresenter.ViewHolder rowViewHolder, Row row) {
                if (item instanceof String) {
                    String selectedItem = (String) item;
                    handleSelection(selectedItem);
                }
            }
        });
    }
    
    private void createCategories() {
        // AirPlay Category
        ArrayObjectAdapter airPlayAdapter = new ArrayObjectAdapter(new StringPresenter());
        airPlayAdapter.add("启动 AirPlay 接收");
        airPlayAdapter.add("连接设备列表");
        airPlayAdapter.add("AirPlay 设置");
        HeaderItem airPlayHeader = new HeaderItem("AirPlay 功能");
        rowsAdapter.add(new ListRow(airPlayHeader, airPlayAdapter));
        
        // Media Player Category
        ArrayObjectAdapter playerAdapter = new ArrayObjectAdapter(new StringPresenter());
        playerAdapter.add("本地音乐播放");
        playerAdapter.add("本地视频播放");
        playerAdapter.add("播放列表管理");
        HeaderItem playerHeader = new HeaderItem("媒体播放");
        rowsAdapter.add(new ListRow(playerHeader, playerAdapter));
        
        // Lyrics Category
        ArrayObjectAdapter lyricsAdapter = new ArrayObjectAdapter(new StringPresenter());
        lyricsAdapter.add("歌词库");
        lyricsAdapter.add("歌词同步设置");
        lyricsAdapter.add("歌词样式");
        HeaderItem lyricsHeader = new HeaderItem("歌词管理");
        rowsAdapter.add(new ListRow(lyricsHeader, lyricsAdapter));
        
        // Settings Category
        ArrayObjectAdapter settingsAdapter = new ArrayObjectAdapter(new StringPresenter());
        settingsAdapter.add("网络设置");
        settingsAdapter.add("显示设置");
        settingsAdapter.add("关于");
        HeaderItem settingsHeader = new HeaderItem("设置");
        rowsAdapter.add(new ListRow(settingsHeader, settingsAdapter));
    }
    
    private void handleSelection(String selectedItem) {
        switch (selectedItem) {
            case "启动 AirPlay 接收":
                airPlayManager.startAirPlayService();
                break;
            case "连接设备列表":
                // Show connected devices
                break;
            case "AirPlay 设置":
                // Open AirPlay settings
                break;
            case "本地音乐播放":
                PlayerActivity.startMusicPlayer(this);
                break;
            case "本地视频播放":
                PlayerActivity.startVideoPlayer(this);
                break;
            case "播放列表管理":
                // Open playlist manager
                break;
            case "歌词库":
                LyricsActivity.showLyricsLibrary(this);
                break;
            case "歌词同步设置":
                LyricsActivity.showSyncSettings(this);
                break;
            case "歌词样式":
                LyricsActivity.showStyleSettings(this);
                break;
            case "网络设置":
                // Open network settings
                break;
            case "显示设置":
                // Open display settings
                break;
            case "关于":
                // Show about dialog
                break;
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (airPlayManager != null) {
            airPlayManager.stopAirPlayService();
        }
    }
    
    // Simple presenter for string items
    private static class StringPresenter extends Presenter {
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent) {
            TextView view = new TextView(parent.getContext());
            view.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            view.setPadding(32, 32, 32, 32);
            view.setTextColor(0xFFFFFFFF);
            view.setTextSize(18);
            return new ViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(ViewHolder viewHolder, Object item) {
            ((TextView) viewHolder.view).setText((String) item);
        }
        
        @Override
        public void onUnbindViewHolder(ViewHolder viewHolder) {
            // Clean up if needed
        }
    }
}