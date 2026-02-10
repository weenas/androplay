package com.androplay.lyrics;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lyrics Display Manager - Handles lyrics parsing and synchronization
 * Supports LRC format lyrics with time synchronization
 */
public class LyricsDisplayManager {
    
    private static final String TAG = "LyricsDisplayManager";
    
    private Context context;
    private Handler mainHandler;
    private List<LyricLine> lyrics;
    private boolean isDisplaying = false;
    private long currentPosition = 0;
    private LyricsDisplayCallback callback;
    
    // LRC format pattern: [mm:ss.xx]lyric text
    private static final Pattern LRC_PATTERN = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)");
    
    public interface LyricsDisplayCallback {
        void onLyricUpdate(String lyric, int position);
        void onLyricsComplete();
    }
    
    public LyricsDisplayManager(Context context) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.lyrics = new ArrayList<>();
    }
    
    /**
     * Start lyrics display for a media file
     */
    public void startLyricsDisplay(String mediaPath) {
        if (isDisplaying) {
            stopLyricsDisplay();
        }
        
        // Try to find lyrics file
        String lyricsPath = findLyricsFile(mediaPath);
        if (lyricsPath == null) {
            Log.w(TAG, "No lyrics file found for: " + mediaPath);
            return;
        }
        
        // Parse lyrics
        if (!parseLyrics(lyricsPath)) {
            Log.e(TAG, "Failed to parse lyrics file: " + lyricsPath);
            return;
        }
        
        isDisplaying = true;
        startLyricsUpdateLoop();
        Log.i(TAG, "Started lyrics display for: " + mediaPath);
    }
    
    /**
     * Stop lyrics display
     */
    public void stopLyricsDisplay() {
        isDisplaying = false;
        mainHandler.removeCallbacksAndMessages(null);
        lyrics.clear();
        Log.i(TAG, "Stopped lyrics display");
    }
    
    /**
     * Update current playback position
     */
    public void updatePosition(long position) {
        this.currentPosition = position;
    }
    
    /**
     * Set callback for lyrics updates
     */
    public void setCallback(LyricsDisplayCallback callback) {
        this.callback = callback;
    }
    
    /**
     * Find lyrics file for a media file
     */
    private String findLyricsFile(String mediaPath) {
        try {
            File mediaFile = new File(mediaPath);
            if (!mediaFile.exists()) {
                return null;
            }
            
            String baseName = mediaFile.getName();
            int dotIndex = baseName.lastIndexOf('.');
            if (dotIndex > 0) {
                baseName = baseName.substring(0, dotIndex);
            }
            
            // Try common lyrics file extensions
            String[] extensions = {".lrc", ".txt", ".srt"};
            File parentDir = mediaFile.getParentFile();
            
            if (parentDir != null && parentDir.exists()) {
                for (String ext : extensions) {
                    File lyricsFile = new File(parentDir, baseName + ext);
                    if (lyricsFile.exists()) {
                        return lyricsFile.getAbsolutePath();
                    }
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error finding lyrics file", e);
        }
        
        return null;
    }
    
    /**
     * Parse LRC format lyrics file
     */
    private boolean parseLyrics(String lyricsPath) {
        try (BufferedReader reader = new BufferedReader(new FileReader(lyricsPath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                
                Matcher matcher = LRC_PATTERN.matcher(line);
                if (matcher.find()) {
                    int minutes = Integer.parseInt(matcher.group(1));
                    int seconds = Integer.parseInt(matcher.group(2));
                    int milliseconds = Integer.parseInt(matcher.group(3));
                    String text = matcher.group(4).trim();
                    
                    long timeMs = minutes * 60 * 1000 + seconds * 1000 + milliseconds;
                    lyrics.add(new LyricLine(timeMs, text));
                }
            }
            
            // Sort by time
            lyrics.sort((a, b) -> Long.compare(a.time, b.time));
            
            Log.i(TAG, "Parsed " + lyrics.size() + " lyric lines");
            return !lyrics.isEmpty();
            
        } catch (IOException e) {
            Log.e(TAG, "Error parsing lyrics file", e);
            return false;
        }
    }
    
    /**
     * Start the lyrics update loop
     */
    private void startLyricsUpdateLoop() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!isDisplaying) return;
                
                updateLyricsDisplay();
                mainHandler.postDelayed(this, 100); // Update every 100ms
            }
        });
    }
    
    /**
     * Update lyrics display based on current position
     */
    private void updateLyricsDisplay() {
        if (lyrics.isEmpty() || callback == null) return;
        
        // Find current lyric line
        String currentLyric = "";
        int currentIndex = -1;
        
        for (int i = 0; i < lyrics.size(); i++) {
            LyricLine line = lyrics.get(i);
            if (line.time <= currentPosition) {
                currentLyric = line.text;
                currentIndex = i;
            } else {
                break;
            }
        }
        
        // Update display
        if (!currentLyric.isEmpty()) {
            callback.onLyricUpdate(currentLyric, currentIndex);
        }
        
        // Check if we've reached the end
        if (currentIndex == lyrics.size() - 1 && 
            currentPosition >= lyrics.get(lyrics.size() - 1).time) {
            callback.onLyricsComplete();
            stopLyricsDisplay();
        }
    }
    
    /**
     * Internal class to represent a lyric line
     */
    private static class LyricLine {
        long time;
        String text;
        
        LyricLine(long time, String text) {
            this.time = time;
            this.text = text;
        }
    }
}