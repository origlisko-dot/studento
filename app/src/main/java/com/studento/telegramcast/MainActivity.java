package com.studento.telegramcast;

import android.app.Activity;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
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
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final String PREFS = "telegram_tv_cast";
    private static final String TOKEN = "bot_token";
    private static final String CHAT_ID = "chat_id";
    private static final String LAST_UPDATE_ID = "last_update_id";
    private static final long POLL_INTERVAL_MS = 3000L;
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\d{6,}:[-_A-Za-z0-9]{20,}");

    private final Handler handler = new Handler(Looper.getMainLooper());
    private VideoView videoView;
    private EditText tokenInput;
    private EditText chatInput;
    private TextView status;
    private TextView chatHelp;
    private TextView nowPlaying;
    private ProgressBar progress;
    private Button connectButton;
    private Button disconnectButton;
    private int lastUpdateId = 0;
    private boolean polling;
    private boolean requestInFlight;

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
        lastUpdateId = prefs.getInt(LAST_UPDATE_ID, 0);
        if (tokenInput.getText().length() > 0) {
            status.setText("Saved bot token found. Press Connect to start casting.");
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopPolling();
        if (videoView != null && videoView.isPlaying()) {
            videoView.pause();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_UP || videoView == null) {
            return super.dispatchKeyEvent(event);
        }
        int keyCode = event.getKeyCode();
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            togglePlayback();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_STOP || keyCode == KeyEvent.KEYCODE_BACK) {
            if (videoView.isPlaying()) {
                videoView.stopPlayback();
                nowPlaying.setText("No video playing.");
                status.setText("Playback stopped.");
                return true;
            }
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
        instructions.setText("Enter your Telegram bot token, send a video link or video file to the bot, and this TV starts playback automatically. Commands: /pause, /resume, /stop.");
        instructions.setTextColor(0xFFD6E4F0);
        instructions.setTextSize(16);
        instructions.setGravity(Gravity.CENTER);
        root.addView(instructions, new LinearLayout.LayoutParams(-1, -2));

        tokenInput = new EditText(this);
        tokenInput.setHint("Telegram bot token (from BotFather)");
        tokenInput.setSingleLine(true);
        tokenInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        tokenInput.setTextColor(0xFFFFFFFF);
        tokenInput.setHintTextColor(0xFF8FA7B5);
        root.addView(tokenInput, new LinearLayout.LayoutParams(-1, -2));

        chatInput = new EditText(this);
        chatInput.setHint("Allowed chat ID (optional, shown after first message)");
        chatInput.setSingleLine(true);
        chatInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        chatInput.setTextColor(0xFFFFFFFF);
        chatInput.setHintTextColor(0xFF8FA7B5);
        root.addView(chatInput, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        connectButton = new Button(this);
        connectButton.setText("Connect");
        connectButton.setOnClickListener(v -> startTelegramCasting());
        buttons.addView(connectButton, new LinearLayout.LayoutParams(0, -2, 1));
        disconnectButton = new Button(this);
        disconnectButton.setText("Disconnect");
        disconnectButton.setEnabled(false);
        disconnectButton.setOnClickListener(v -> stopPolling());
        buttons.addView(disconnectButton, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(buttons, new LinearLayout.LayoutParams(-1, -2));

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(-2, -2));

        status = new TextView(this);
        status.setText("Waiting for Telegram connection.");
        status.setTextColor(0xFFFFFFFF);
        status.setTextSize(18);
        status.setGravity(Gravity.CENTER);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        chatHelp = new TextView(this);
        chatHelp.setText("Tip: leave chat ID blank at first; the app will show the chat ID for incoming messages.");
        chatHelp.setTextColor(0xFF9FB9C8);
        chatHelp.setTextSize(14);
        chatHelp.setGravity(Gravity.CENTER);
        root.addView(chatHelp, new LinearLayout.LayoutParams(-1, -2));

        nowPlaying = new TextView(this);
        nowPlaying.setText("No video playing.");
        nowPlaying.setTextColor(0xFFD6E4F0);
        nowPlaying.setTextSize(14);
        nowPlaying.setGravity(Gravity.CENTER);
        root.addView(nowPlaying, new LinearLayout.LayoutParams(-1, -2));

        videoView = new VideoView(this);
        root.addView(videoView, new LinearLayout.LayoutParams(-1, 0, 1));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    private void startTelegramCasting() {
        String token = tokenInput.getText().toString().trim();
        if (!TOKEN_PATTERN.matcher(token).matches()) {
            status.setText("Enter a valid Telegram bot token first. I need your bot API token here to connect.");
            return;
        }
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(TOKEN, token)
                .putString(CHAT_ID, chatInput.getText().toString().trim())
                .apply();
        status.setText("Connected. Send a direct video URL or Telegram video file to your bot.");
        polling = true;
        connectButton.setEnabled(false);
        disconnectButton.setEnabled(true);
        handler.removeCallbacks(poller);
        handler.post(poller);
    }

    private void stopPolling() {
        polling = false;
        requestInFlight = false;
        handler.removeCallbacks(poller);
        if (progress != null) {
            progress.setVisibility(View.GONE);
        }
        if (connectButton != null) {
            connectButton.setEnabled(true);
        }
        if (disconnectButton != null) {
            disconnectButton.setEnabled(false);
        }
        if (status != null) {
            status.setText("Disconnected from Telegram.");
        }
    }

    private void pollTelegram() {
        if (requestInFlight) {
            return;
        }
        requestInFlight = true;
        progress.setVisibility(View.VISIBLE);
        String token = tokenInput.getText().toString().trim();
        String endpoint = "https://api.telegram.org/bot" + token + "/getUpdates?timeout=1&offset=" + (lastUpdateId + 1);
        new Thread(() -> {
            try {
                String json = get(endpoint);
                JSONObject response = new JSONObject(json);
                if (!response.optBoolean("ok")) {
                    showStatus("Telegram rejected the token or request: " + response.optString("description"));
                    return;
                }
                JSONArray updates = response.optJSONArray("result");
                if (updates == null || updates.length() == 0) {
                    showStatus("Connected. Waiting for a Telegram video URL or video file...");
                    return;
                }
                for (int i = 0; i < updates.length(); i++) {
                    JSONObject update = updates.getJSONObject(i);
                    lastUpdateId = Math.max(lastUpdateId, update.optInt("update_id"));
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(LAST_UPDATE_ID, lastUpdateId).apply();
                    JSONObject message = update.optJSONObject("message");
                    if (message == null) {
                        message = update.optJSONObject("channel_post");
                    }
                    handleMessage(message, token);
                }
            } catch (Exception exception) {
                showStatus("Telegram connection error: " + exception.getMessage());
            } finally {
                requestInFlight = false;
                handler.post(() -> progress.setVisibility(View.GONE));
            }
        }).start();
    }

    private void handleMessage(JSONObject message, String token) throws Exception {
        if (message == null) {
            return;
        }
        JSONObject chat = message.optJSONObject("chat");
        String incomingChatId = chat == null ? "unknown" : String.valueOf(chat.optLong("id"));
        String incomingChatTitle = chat == null ? "chat" : chat.optString("title", chat.optString("first_name", "chat"));
        showChatHelp("Last message came from " + incomingChatTitle + " (chat ID: " + incomingChatId + ").");

        String allowedChat = chatInput.getText().toString().trim();
        if (!allowedChat.isEmpty() && !allowedChat.equals(incomingChatId)) {
            showStatus("Ignored message from another chat: " + incomingChatId);
            return;
        }

        String text = message.optString("text", message.optString("caption", "")).trim();
        String command = text.toLowerCase(Locale.US);
        if (command.startsWith("/pause")) {
            handler.post(() -> {
                if (videoView.isPlaying()) {
                    videoView.pause();
                }
                status.setText("Paused from Telegram.");
            });
            return;
        }
        if (command.startsWith("/stop")) {
            handler.post(() -> {
                videoView.stopPlayback();
                nowPlaying.setText("No video playing.");
                status.setText("Stopped from Telegram.");
            });
            return;
        }

        Matcher matcher = URL_PATTERN.matcher(text);
        if (matcher.find()) {
            playUrl(matcher.group());
            return;
        }

        if (command.startsWith("/resume") || command.equals("/play")) {
            handler.post(() -> {
                videoView.start();
                status.setText("Playing from Telegram command.");
            });
            return;
        }

        String fileId = findPlayableTelegramFileId(message);
        if (!fileId.isEmpty()) {
            playUrl(resolveTelegramFileUrl(token, fileId));
        } else if (!text.isEmpty()) {
            showStatus("No playable URL or Telegram video file found in the latest message.");
        }
    }

    private String findPlayableTelegramFileId(JSONObject message) {
        JSONObject video = message.optJSONObject("video");
        if (video != null) {
            return video.optString("file_id", "");
        }
        JSONObject document = message.optJSONObject("document");
        if (document != null) {
            String mimeType = document.optString("mime_type", "");
            if (mimeType.startsWith("video/")) {
                return document.optString("file_id", "");
            }
        }
        JSONObject animation = message.optJSONObject("animation");
        if (animation != null) {
            return animation.optString("file_id", "");
        }
        return "";
    }

    private String resolveTelegramFileUrl(String token, String fileId) throws Exception {
        String json = get("https://api.telegram.org/bot" + token + "/getFile?file_id=" + fileId);
        JSONObject response = new JSONObject(json);
        if (!response.optBoolean("ok")) {
            throw new IllegalStateException(response.optString("description", "Unable to resolve Telegram file"));
        }
        String filePath = response.getJSONObject("result").getString("file_path");
        return "https://api.telegram.org/file/bot" + token + "/" + filePath;
    }

    private void playUrl(String mediaUrl) {
        handler.post(() -> {
            status.setText("Casting from Telegram.");
            nowPlaying.setText(mediaUrl);
            videoView.setVideoURI(Uri.parse(mediaUrl));
            videoView.setOnPreparedListener((MediaPlayer player) -> {
                player.setLooping(false);
                videoView.start();
                status.setText("Playing. Use TV OK/play-pause or Telegram commands to control playback.");
            });
            videoView.setOnCompletionListener(player -> status.setText("Playback complete. Send another video to cast again."));
            videoView.setOnErrorListener((player, what, extra) -> {
                status.setText("Unable to play this media. Send a direct MP4/HLS URL or a Telegram video file.");
                return true;
            });
        });
    }

    private void togglePlayback() {
        if (videoView.isPlaying()) {
            videoView.pause();
            status.setText("Paused.");
        } else {
            videoView.start();
            status.setText("Playing.");
        }
    }

    private void showStatus(String message) {
        handler.post(() -> status.setText(message));
    }

    private void showChatHelp(String message) {
        handler.post(() -> chatHelp.setText(message));
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
