package com.studento.telegramcast;

import android.app.Activity;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.VideoView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final String PREFS = "telegram_tv_cast";
    private static final String TOKEN = "bot_token";
    private static final String CHAT_ID = "chat_id";
    private static final long POLL_INTERVAL_MS = 4000L;
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");

    private final Handler handler = new Handler(Looper.getMainLooper());
    private VideoView videoView;
    private EditText tokenInput;
    private EditText chatInput;
    private TextView status;
    private ProgressBar progress;
    private int lastUpdateId = 0;
    private boolean polling;

    private final Runnable poller = new Runnable() {
        @Override
        public void run() {
            if (!polling) {
                return;
            }
            pollTelegram();
            handler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        tokenInput.setText(prefs.getString(TOKEN, ""));
        chatInput.setText(prefs.getString(CHAT_ID, ""));
    }

    @Override
    protected void onStop() {
        super.onStop();
        polling = false;
        handler.removeCallbacks(poller);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            if (videoView.isPlaying()) {
                videoView.pause();
                status.setText("Paused");
            } else {
                videoView.start();
                status.setText("Playing");
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 36, 48, 36);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(0xFF101820);

        TextView title = new TextView(this);
        title.setText("Telegram TV Cast");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(30);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView instructions = new TextView(this);
        instructions.setText("Create a Telegram bot, add it to a private chat or channel, then send a direct video URL to cast it on this Android TV app.");
        instructions.setTextColor(0xFFD6E4F0);
        instructions.setTextSize(16);
        instructions.setGravity(Gravity.CENTER);
        root.addView(instructions, new LinearLayout.LayoutParams(-1, -2));

        tokenInput = new EditText(this);
        tokenInput.setHint("Telegram bot token");
        tokenInput.setSingleLine(true);
        tokenInput.setTextColor(0xFFFFFFFF);
        tokenInput.setHintTextColor(0xFF8FA7B5);
        root.addView(tokenInput, new LinearLayout.LayoutParams(-1, -2));

        chatInput = new EditText(this);
        chatInput.setHint("Allowed chat ID (optional)");
        chatInput.setSingleLine(true);
        chatInput.setTextColor(0xFFFFFFFF);
        chatInput.setHintTextColor(0xFF8FA7B5);
        root.addView(chatInput, new LinearLayout.LayoutParams(-1, -2));

        Button connect = new Button(this);
        connect.setText("Connect to Telegram");
        connect.setOnClickListener(v -> startTelegramCasting());
        root.addView(connect, new LinearLayout.LayoutParams(-1, -2));

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(-2, -2));

        status = new TextView(this);
        status.setText("Waiting for Telegram connection.");
        status.setTextColor(0xFFFFFFFF);
        status.setTextSize(18);
        status.setGravity(Gravity.CENTER);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        videoView = new VideoView(this);
        root.addView(videoView, new LinearLayout.LayoutParams(-1, 0, 1));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    private void startTelegramCasting() {
        String token = tokenInput.getText().toString().trim();
        if (token.isEmpty()) {
            status.setText("Enter a Telegram bot token first.");
            return;
        }
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(TOKEN, token)
                .putString(CHAT_ID, chatInput.getText().toString().trim())
                .apply();
        status.setText("Connected. Send a video URL to your Telegram bot to cast it here.");
        polling = true;
        handler.removeCallbacks(poller);
        handler.post(poller);
    }

    private void pollTelegram() {
        progress.setVisibility(View.VISIBLE);
        String token = tokenInput.getText().toString().trim();
        String endpoint = "https://api.telegram.org/bot" + token + "/getUpdates?timeout=1&offset=" + (lastUpdateId + 1);
        new Thread(() -> {
            try {
                String json = get(endpoint);
                JSONObject response = new JSONObject(json);
                JSONArray updates = response.optJSONArray("result");
                if (updates == null) {
                    showStatus("Telegram returned no updates yet.");
                    return;
                }
                for (int i = 0; i < updates.length(); i++) {
                    JSONObject update = updates.getJSONObject(i);
                    lastUpdateId = Math.max(lastUpdateId, update.optInt("update_id"));
                    JSONObject message = update.optJSONObject("message");
                    if (message == null) {
                        message = update.optJSONObject("channel_post");
                    }
                    handleMessage(message);
                }
                if (updates.length() == 0) {
                    showStatus("Connected. Waiting for a Telegram video URL...");
                }
            } catch (Exception exception) {
                showStatus("Telegram connection error: " + exception.getMessage());
            } finally {
                handler.post(() -> progress.setVisibility(View.GONE));
            }
        }).start();
    }

    private void handleMessage(JSONObject message) {
        if (message == null) {
            return;
        }
        String allowedChat = chatInput.getText().toString().trim();
        JSONObject chat = message.optJSONObject("chat");
        if (!allowedChat.isEmpty() && chat != null && !allowedChat.equals(String.valueOf(chat.optLong("id")))) {
            showStatus("Ignored message from another chat.");
            return;
        }
        String text = message.optString("text", message.optString("caption", ""));
        Matcher matcher = URL_PATTERN.matcher(text);
        if (matcher.find()) {
            playUrl(matcher.group());
        }
    }

    private void playUrl(String mediaUrl) {
        handler.post(() -> {
            status.setText("Casting from Telegram: " + mediaUrl);
            videoView.setVideoURI(Uri.parse(mediaUrl));
            videoView.setOnPreparedListener(player -> {
                player.setLooping(false);
                videoView.start();
            });
            videoView.setOnErrorListener((player, what, extra) -> {
                status.setText("Unable to play this URL. Send a direct MP4/HLS video link.");
                return true;
            });
        });
    }

    private void showStatus(String message) {
        handler.post(() -> status.setText(message));
    }

    private String get(String endpoint) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setRequestMethod("GET");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
            return body.toString();
        } finally {
            connection.disconnect();
        }
    }
}
