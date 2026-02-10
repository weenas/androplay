package com.androplay.player;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.androplay.R;
import com.androplay.lyrics.LyricsDisplayManager;

/**
 * Player Activity - Handles media playback for AndroPlay
 * Supports both audio and video playback with lyrics synchronization
 */
public class PlayerActivity extends AppCompatActivity {
    
    private static final String TAG = "PlayerActivity";
    private static final String EXTRA_MEDIA_TYPE = "media_type";
    private static final String EXTRA_MEDIA_PATH = "media_path";
    
    private static final int MEDIA_TYPE_MUSIC = 1;
    private static final int MEDIA_TYPE_VIDEO = 2;
    
    private ExoPlayer player;
    private PlayerView playerView;
    private LyricsDisplayManager lyricsManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        
        // Initialize player view
        playerView = findViewById(R.id.player_view);
        
        // Initialize lyrics manager
        lyricsManager = new LyricsDisplayManager(this);
        
        // Setup player
        setupPlayer();
        
        // Handle intent
        handleIntent(getIntent());
    }
    
    private void setupPlayer() {
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_ENDED) {
                    onPlaybackComplete();
                }
            }
            
            @Override
            public void onPositionDiscontinuity(Player.PositionInfo oldPosition, 
                                               Player.PositionInfo newPosition, 
                                               int reason) {
                // Update lyrics position when seeking
                if (lyricsManager != null) {
                    lyricsManager.updatePosition(player.getCurrentPosition());
                }
            }
        });
    }
    
    private void handleIntent(Intent intent) {
        if (intent == null) return;
        
        int mediaType = intent.getIntExtra(EXTRA_MEDIA_TYPE, MEDIA_TYPE_MUSIC);
        String mediaPath = intent.getStringExtra(EXTRA_MEDIA_PATH);
        
        if (mediaPath != null) {
            playMedia(mediaType, mediaPath);
        } else {
            // Demo mode - show message
            Toast.makeText(this, "请选择要播放的媒体文件", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void playMedia(int mediaType, String mediaPath) {
        try {
            MediaItem mediaItem = MediaItem.fromUri(mediaPath);
            player.setMediaItem(mediaItem);
            player.prepare();
            player.play();
            
            // Start lyrics display if it's music
            if (mediaType == MEDIA_TYPE_MUSIC) {
                lyricsManager.startLyricsDisplay(mediaPath);
            }
            
        } catch (Exception e) {
            Toast.makeText(this, "播放失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void onPlaybackComplete() {
        if (lyricsManager != null) {
            lyricsManager.stopLyricsDisplay();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
        if (lyricsManager != null) {
            lyricsManager.stopLyricsDisplay();
        }
    }
    
    // Static methods to start the activity
    public static void startMusicPlayer(Context context) {
        Intent intent = new Intent(context, PlayerActivity.class);
        intent.putExtra(EXTRA_MEDIA_TYPE, MEDIA_TYPE_MUSIC);
        context.startActivity(intent);
    }
    
    public static void startVideoPlayer(Context context) {
        Intent intent = new Intent(context, PlayerActivity.class);
        intent.putExtra(EXTRA_MEDIA_TYPE, MEDIA_TYPE_VIDEO);
        context.startActivity(intent);
    }
    
    public static void playMediaFile(Context context, int mediaType, String mediaPath) {
        Intent intent = new Intent(context, PlayerActivity.class);
        intent.putExtra(EXTRA_MEDIA_TYPE, mediaType);
        intent.putExtra(EXTRA_MEDIA_PATH, mediaPath);
        context.startActivity(intent);
    }
}