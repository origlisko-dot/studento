package com.studento.telegramcast;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

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
    private static final String DEFAULT_BOT_TOKEN = BuildConfig.DEFAULT_BOT_TOKEN;
    private static final String CHAT_ID = "chat_id";
    private static final String LAST_UPDATE_ID = "last_update_id";
    private static final long POLL_INTERVAL_MS = 3000L;
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s)\\]}>\\\"]+");
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\d{6,}:[-_A-Za-z0-9]{20,}");
    private static final String TOKEN_MASK = "<bot-token>";

    private static final int COLOR_BG = 0xFF081521;
    private static final int COLOR_ACCENT = 0xFF229ED9;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_MUTED = 0xFF9FB9C8;
    private static final int COLOR_HINT = 0xFF62798A;
    private static final int DOT_WAITING = 0xFFF2B23E;
    private static final int DOT_LIVE = 0xFF43C463;
    private static final int DOT_ERROR = 0xFFE0584F;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ScrollView setupView;
    private FrameLayout playerContainer;
    private PlayerView playerView;
    private ExoPlayer player;
    private EditText tokenInput;
    private EditText chatInput;
    private TextView status;
    private View statusDot;
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
        tokenInput.setText(prefs.getString(TOKEN, DEFAULT_BOT_TOKEN));
        chatInput.setText(prefs.getString(CHAT_ID, ""));
        lastUpdateId = prefs.getInt(LAST_UPDATE_ID, 0);
        if (tokenInput.getText().length() > 0) {
            setStatus("Bot token loaded. Press Connect to start casting.", DOT_WAITING);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopPolling();
        if (player != null && player.isPlaying()) {
            player.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_UP || player == null) {
            return super.dispatchKeyEvent(event);
        }
        int keyCode = event.getKeyCode();
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            togglePlayback();
            return true;
        }
        boolean playerVisible = playerContainer.getVisibility() == View.VISIBLE;
        if (keyCode == KeyEvent.KEYCODE_MEDIA_STOP
                || (keyCode == KeyEvent.KEYCODE_BACK && playerVisible)) {
            if (player.getPlaybackState() != Player.STATE_IDLE) {
                player.stop();
                player.clearMediaItems();
            }
            showSetup("Playback stopped.");
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundResource(R.drawable.bg_screen);

        root.addView(buildSetup(), new FrameLayout.LayoutParams(-1, -1));

        playerContainer = new FrameLayout(this);
        playerContainer.setBackgroundColor(Color.BLACK);
        playerContainer.setVisibility(View.GONE);

        player = new ExoPlayer.Builder(this).build();
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_BUFFERING) {
                    setStatus("Buffering...", DOT_LIVE);
                } else if (playbackState == Player.STATE_READY) {
                    setStatus("Playing. Use the remote or Telegram commands to control playback.", DOT_LIVE);
                } else if (playbackState == Player.STATE_ENDED) {
                    setStatus("Playback complete. Send another video to cast again.", DOT_WAITING);
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                setStatus("Unable to play this media. Send a direct MP4/HLS URL or a Telegram video file.", DOT_ERROR);
                showSetup("Unable to play this media. Send a direct MP4/HLS URL or a Telegram video file.");
            }
        });

        playerView = new PlayerView(this);
        playerView.setPlayer(player);
        playerView.setUseController(true);
        playerContainer.addView(playerView, new FrameLayout.LayoutParams(-1, -1));

        nowPlaying = new TextView(this);
        nowPlaying.setText("No video playing.");
        nowPlaying.setTextColor(COLOR_TEXT);
        nowPlaying.setTextSize(16);
        nowPlaying.setShadowLayer(8f, 0f, 2f, 0xCC000000);
        nowPlaying.setPadding(dp(28), dp(22), dp(28), dp(22));
        playerContainer.addView(nowPlaying, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START));

        root.addView(playerContainer, new FrameLayout.LayoutParams(-1, -1));

        setContentView(root);
    }

    private ScrollView buildSetup() {
        LinearLayout columns = new LinearLayout(this);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        columns.setPadding(dp(56), dp(48), dp(56), dp(48));
        columns.addView(buildBrandPanel(), new LinearLayout.LayoutParams(dp(380), -1));

        View spacer = new View(this);
        columns.addView(spacer, new LinearLayout.LayoutParams(dp(48), 1));

        columns.addView(buildCard(), new LinearLayout.LayoutParams(0, -2, 1f));

        setupView = new ScrollView(this);
        setupView.setFillViewport(true);
        setupView.addView(columns, new ScrollView.LayoutParams(-1, -1));
        return setupView;
    }

    private LinearLayout buildBrandPanel() {
        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout logo = new FrameLayout(this);
        logo.setBackgroundResource(R.drawable.bg_logo);
        ImageView glyph = new ImageView(this);
        glyph.setImageResource(R.drawable.ic_logo);
        FrameLayout.LayoutParams glyphParams = new FrameLayout.LayoutParams(dp(64), dp(64), Gravity.CENTER);
        logo.addView(glyph, glyphParams);
        brand.addView(logo, new LinearLayout.LayoutParams(dp(112), dp(112)));

        TextView title = new TextView(this);
        title.setText("Telegram\nTV Cast");
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(46);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLineSpacing(0f, 1.02f);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-2, -2);
        titleParams.topMargin = dp(28);
        brand.addView(title, titleParams);

        TextView subtitle = new TextView(this);
        subtitle.setText("Send any video link or file to your bot — it plays here, on the big screen.");
        subtitle.setTextColor(COLOR_MUTED);
        subtitle.setTextSize(18);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-2, -2);
        subParams.topMargin = dp(20);
        brand.addView(subtitle, subParams);

        brand.addView(buildStep(1, "Connect your bot"), stepParams());
        brand.addView(buildStep(2, "Send a video in Telegram"), stepParams());
        brand.addView(buildStep(3, "Watch it on your TV"), stepParams());

        return brand;
    }

    private LinearLayout.LayoutParams stepParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.topMargin = dp(14);
        return params;
    }

    private LinearLayout buildStep(int number, String label) {
        LinearLayout step = new LinearLayout(this);
        step.setOrientation(LinearLayout.HORIZONTAL);
        step.setGravity(Gravity.CENTER_VERTICAL);

        TextView num = new TextView(this);
        num.setText(String.valueOf(number));
        num.setTextColor(0xFF5FC2EE);
        num.setTextSize(16);
        num.setGravity(Gravity.CENTER);
        num.setBackgroundResource(R.drawable.bg_field);
        LinearLayout.LayoutParams numParams = new LinearLayout.LayoutParams(dp(34), dp(34));
        numParams.rightMargin = dp(14);
        step.addView(num, numParams);

        TextView text = new TextView(this);
        text.setText(label);
        text.setTextColor(0xFFC7D8E4);
        text.setTextSize(18);
        step.addView(text, new LinearLayout.LayoutParams(-2, -2));
        return step;
    }

    private LinearLayout buildCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(dp(44), dp(40), dp(44), dp(40));

        TextView header = new TextView(this);
        header.setText("Connect your bot");
        header.setTextColor(COLOR_TEXT);
        header.setTextSize(28);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(header, new LinearLayout.LayoutParams(-1, -2));

        tokenInput = buildField(card, "Telegram bot token (from BotFather)",
                "Paste your bot token");
        tokenInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);

        chatInput = buildField(card, "Allowed chat ID (optional)",
                "Shown automatically after the first message");
        chatInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(-1, -2);
        buttonsParams.topMargin = dp(26);
        card.addView(buttons, buttonsParams);

        connectButton = new Button(this);
        connectButton.setText("Connect");
        connectButton.setAllCaps(true);
        connectButton.setTextColor(0xFF04202D);
        connectButton.setTypeface(Typeface.DEFAULT_BOLD);
        connectButton.setTextSize(20);
        connectButton.setBackgroundResource(R.drawable.btn_primary);
        connectButton.setStateListAnimator(null);
        connectButton.setOnClickListener(v -> startTelegramCasting());
        LinearLayout.LayoutParams connectParams = new LinearLayout.LayoutParams(0, dp(64), 1f);
        connectParams.rightMargin = dp(20);
        buttons.addView(connectButton, connectParams);

        disconnectButton = new Button(this);
        disconnectButton.setText("Disconnect");
        disconnectButton.setAllCaps(true);
        disconnectButton.setTextColor(COLOR_MUTED);
        disconnectButton.setTypeface(Typeface.DEFAULT_BOLD);
        disconnectButton.setTextSize(20);
        disconnectButton.setBackgroundResource(R.drawable.btn_secondary);
        disconnectButton.setStateListAnimator(null);
        disconnectButton.setEnabled(false);
        disconnectButton.setOnClickListener(v -> stopPolling());
        buttons.addView(disconnectButton, new LinearLayout.LayoutParams(0, dp(64), 1f));

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        progressParams.topMargin = dp(22);
        card.addView(progress, progressParams);

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams statusRowParams = new LinearLayout.LayoutParams(-1, -2);
        statusRowParams.topMargin = dp(22);
        card.addView(statusRow, statusRowParams);

        statusDot = new View(this);
        statusDot.setBackgroundResource(R.drawable.dot);
        statusDot.getBackground().setTint(DOT_WAITING);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(12), dp(12));
        dotParams.rightMargin = dp(12);
        statusRow.addView(statusDot, dotParams);

        status = new TextView(this);
        status.setText("Waiting for Telegram connection");
        status.setTextColor(0xFFCBE0EC);
        status.setTextSize(18);
        statusRow.addView(status, new LinearLayout.LayoutParams(-2, -2));

        chatHelp = new TextView(this);
        chatHelp.setText("Tip: leave chat ID blank at first; the app shows the chat ID of incoming messages.");
        chatHelp.setTextColor(COLOR_MUTED);
        chatHelp.setTextSize(15);
        LinearLayout.LayoutParams helpParams = new LinearLayout.LayoutParams(-1, -2);
        helpParams.topMargin = dp(12);
        card.addView(chatHelp, helpParams);

        return card;
    }

    private EditText buildField(ViewGroup parent, String label, String hint) {
        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(0xFF8FA7B5);
        labelView.setTextSize(16);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
        labelParams.topMargin = dp(24);
        labelParams.bottomMargin = dp(10);
        parent.addView(labelView, labelParams);

        EditText field = new EditText(this);
        field.setHint(hint);
        field.setSingleLine(true);
        field.setTextColor(0xFFEAF4FB);
        field.setHintTextColor(COLOR_HINT);
        field.setTextSize(20);
        field.setBackgroundResource(R.drawable.bg_field);
        field.setPadding(dp(20), dp(16), dp(20), dp(16));
        parent.addView(field, new LinearLayout.LayoutParams(-1, -2));
        return field;
    }

    private void showSetup(String message) {
        handler.post(() -> {
            playerContainer.setVisibility(View.GONE);
            setupView.setVisibility(View.VISIBLE);
            nowPlaying.setText("No video playing.");
            if (message != null) {
                setStatus(message, polling ? DOT_LIVE : DOT_WAITING);
            }
            connectButton.requestFocus();
        });
    }

    private void showPlayer() {
        handler.post(() -> {
            setupView.setVisibility(View.GONE);
            playerContainer.setVisibility(View.VISIBLE);
            playerView.requestFocus();
        });
    }

    private void startTelegramCasting() {
        String token = tokenInput.getText().toString().trim();
        if (!TOKEN_PATTERN.matcher(token).matches()) {
            setStatus("Enter a valid Telegram bot token first (get one from BotFather).", DOT_ERROR);
            return;
        }
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(TOKEN, token)
                .putString(CHAT_ID, chatInput.getText().toString().trim())
                .apply();
        setStatus("Connected. Send a direct video URL or Telegram video file to your bot.", DOT_LIVE);
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
        setStatus("Disconnected from Telegram.", DOT_WAITING);
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
                    showStatus("Telegram rejected the token or request: " + response.optString("description"), DOT_ERROR);
                    return;
                }
                JSONArray updates = response.optJSONArray("result");
                if (updates == null || updates.length() == 0) {
                    showStatus("Connected. Waiting for a Telegram video URL or video file...", DOT_LIVE);
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
                showStatus("Telegram connection error: " + exception.getMessage(), DOT_ERROR);
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
            showStatus("Ignored message from another chat: " + incomingChatId, DOT_WAITING);
            return;
        }

        String text = message.optString("text", message.optString("caption", "")).trim();
        String command = text.toLowerCase(Locale.US);
        if (command.startsWith("/pause")) {
            handler.post(() -> {
                if (player.isPlaying()) {
                    player.pause();
                }
                setStatus("Paused from Telegram.", DOT_LIVE);
            });
            return;
        }
        if (command.startsWith("/stop")) {
            handler.post(() -> {
                player.stop();
                player.clearMediaItems();
                showSetup("Stopped from Telegram.");
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
                player.play();
                setStatus("Playing from Telegram command.", DOT_LIVE);
            });
            return;
        }

        String fileId = findPlayableTelegramFileId(message);
        if (!fileId.isEmpty()) {
            playUrl(resolveTelegramFileUrl(token, fileId));
        } else if (!text.isEmpty()) {
            showStatus("No playable URL or Telegram video file found in the latest message.", DOT_WAITING);
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
            nowPlaying.setText("Now playing: " + safeMediaLabel(mediaUrl));
            setStatus("Casting from Telegram.", DOT_LIVE);
            player.setMediaItem(MediaItem.fromUri(Uri.parse(mediaUrl)));
            player.prepare();
            player.setPlayWhenReady(true);
            showPlayer();
        });
    }

    private String safeMediaLabel(String mediaUrl) {
        String token = tokenInput.getText().toString().trim();
        if (!token.isEmpty()) {
            return mediaUrl.replace(token, TOKEN_MASK);
        }
        return mediaUrl;
    }

    private void togglePlayback() {
        if (player.isPlaying()) {
            player.pause();
            setStatus("Paused.", DOT_LIVE);
        } else {
            player.play();
            setStatus("Playing.", DOT_LIVE);
        }
    }

    private void setStatus(String message, int dotColor) {
        if (status != null) {
            status.setText(message);
        }
        if (statusDot != null && statusDot.getBackground() != null) {
            statusDot.getBackground().setTint(dotColor);
        }
    }

    private void showStatus(String message, int dotColor) {
        handler.post(() -> setStatus(message, dotColor));
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
