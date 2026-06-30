package com.studento.telegramcast;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
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

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {
    private static final String SERVER_URL = trimUrl(BuildConfig.SERVER_URL);

    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_MUTED = 0xFF9FB9C8;
    private static final int DOT_WAITING = 0xFFF2B23E;
    private static final int DOT_LIVE = 0xFF43C463;
    private static final int DOT_ERROR = 0xFFE0584F;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ScrollView setupView;
    private FrameLayout playerContainer;
    private PlayerView playerView;
    private ExoPlayer player;
    private TextView codeView;
    private TextView pairHint;
    private TextView status;
    private View statusDot;
    private ImageView qrView;
    private TextView nowPlaying;
    private ProgressBar progress;

    private volatile boolean running;
    private volatile String deviceId;
    private String botUsername = "";
    private boolean paired;

    private static String trimUrl(String url) {
        if (url == null) return "";
        return url.replaceAll("/+$", "");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        if (SERVER_URL.isEmpty()) {
            setStatus(getString(R.string.status_not_configured), DOT_ERROR);
            return;
        }
        running = true;
        new Thread(this::networkLoop).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        running = false;
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
            showSetup(getString(R.string.status_stopped));
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // ---------------------------------------------------------------- UI

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0B1B28);
        root.addView(buildSetup(), new FrameLayout.LayoutParams(-1, -1));

        playerContainer = new FrameLayout(this);
        playerContainer.setBackgroundColor(Color.BLACK);
        playerContainer.setVisibility(View.GONE);

        player = new ExoPlayer.Builder(this).build();
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_ENDED) {
                    showSetup(getString(R.string.status_complete));
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                showSetup(getString(R.string.status_error_play));
            }
        });

        playerView = new PlayerView(this);
        playerView.setPlayer(player);
        playerView.setUseController(true);
        playerContainer.addView(playerView, new FrameLayout.LayoutParams(-1, -1));

        nowPlaying = new TextView(this);
        nowPlaying.setText(R.string.now_playing_none);
        nowPlaying.setTextColor(COLOR_TEXT);
        nowPlaying.setTextSize(16);
        nowPlaying.setShadowLayer(8f, 0f, 2f, 0xCC000000);
        nowPlaying.setPadding(dp(28), dp(22), dp(28), dp(22));
        playerContainer.addView(nowPlaying, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START));

        root.addView(playerContainer, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    private ScrollView buildSetup() {
        // Overscan-safe outer padding: TVs clip ~5% of every edge.
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setGravity(Gravity.CENTER);
        outer.setPadding(dp(64), dp(40), dp(64), dp(40));
        outer.addView(buildCard(), new LinearLayout.LayoutParams(-1, -2));

        setupView = new ScrollView(this);
        setupView.setFillViewport(true);
        setupView.addView(outer, new ScrollView.LayoutParams(-1, -1));
        return setupView;
    }

    private LinearLayout buildCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(dp(40), dp(36), dp(40), dp(36));

        // header: logo + brand name
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout logo = new FrameLayout(this);
        logo.setBackgroundResource(R.drawable.bg_logo);
        ImageView glyph = new ImageView(this);
        glyph.setImageResource(R.drawable.ic_logo);
        logo.addView(glyph, new FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER));
        headerRow.addView(logo, new LinearLayout.LayoutParams(dp(52), dp(52)));

        TextView title = new TextView(this);
        title.setText(R.string.brand_title);
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-2, -2);
        titleParams.setMarginStart(dp(16));
        headerRow.addView(title, titleParams);
        card.addView(headerRow, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = new TextView(this);
        subtitle.setText(R.string.brand_subtitle);
        subtitle.setTextColor(COLOR_MUTED);
        subtitle.setTextSize(16);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, -2);
        subParams.topMargin = dp(14);
        card.addView(subtitle, subParams);

        // body: steps + code (start), QR (end)
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, -2);
        bodyParams.topMargin = dp(24);
        card.addView(body, bodyParams);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        body.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));

        left.addView(buildStep(1, getString(R.string.step_1)), stepParams());
        left.addView(buildStep(2, getString(R.string.step_2)), stepParams());
        left.addView(buildStep(3, getString(R.string.step_3)), stepParams());

        TextView codeLabel = new TextView(this);
        codeLabel.setText(R.string.code_label);
        codeLabel.setTextColor(0xFF8FA7B5);
        codeLabel.setTextSize(15);
        LinearLayout.LayoutParams codeLabelParams = new LinearLayout.LayoutParams(-2, -2);
        codeLabelParams.topMargin = dp(22);
        codeLabelParams.bottomMargin = dp(8);
        left.addView(codeLabel, codeLabelParams);

        codeView = new TextView(this);
        codeView.setText("------");
        codeView.setTextColor(0xFFEAF4FB);
        codeView.setTextSize(40);
        codeView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        codeView.setLetterSpacing(0.2f);
        codeView.setBackgroundResource(R.drawable.bg_field);
        codeView.setPadding(dp(22), dp(14), dp(22), dp(14));
        left.addView(codeView, new LinearLayout.LayoutParams(-2, -2));

        pairHint = new TextView(this);
        pairHint.setText(R.string.status_connecting);
        pairHint.setTextColor(COLOR_MUTED);
        pairHint.setTextSize(15);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.topMargin = dp(12);
        left.addView(pairHint, hintParams);

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(-2, -2);
        rightParams.setMarginStart(dp(28));
        body.addView(right, rightParams);

        qrView = new ImageView(this);
        qrView.setBackgroundColor(0xFFFFFFFF);
        qrView.setPadding(dp(10), dp(10), dp(10), dp(10));
        qrView.setVisibility(View.INVISIBLE);
        right.addView(qrView, new LinearLayout.LayoutParams(dp(176), dp(176)));

        TextView scanLabel = new TextView(this);
        scanLabel.setText(R.string.scan_label);
        scanLabel.setTextColor(COLOR_MUTED);
        scanLabel.setTextSize(13);
        scanLabel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams scanParams = new LinearLayout.LayoutParams(dp(176), -2);
        scanParams.topMargin = dp(10);
        right.addView(scanLabel, scanParams);

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(32), dp(32));
        progressParams.topMargin = dp(20);
        card.addView(progress, progressParams);

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams statusRowParams = new LinearLayout.LayoutParams(-1, -2);
        statusRowParams.topMargin = dp(22);
        card.addView(statusRow, statusRowParams);

        statusDot = new View(this);
        statusDot.setBackgroundResource(R.drawable.dot);
        if (statusDot.getBackground() != null) statusDot.getBackground().setTint(DOT_WAITING);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(12), dp(12));
        dotParams.setMarginEnd(dp(12));
        statusRow.addView(statusDot, dotParams);

        status = new TextView(this);
        status.setText(R.string.status_starting);
        status.setTextColor(0xFFCBE0EC);
        status.setTextSize(17);
        statusRow.addView(status, new LinearLayout.LayoutParams(0, -2, 1f));

        return card;
    }

    private LinearLayout.LayoutParams stepParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
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
        numParams.setMarginEnd(dp(14));
        step.addView(num, numParams);

        TextView text = new TextView(this);
        text.setText(label);
        text.setTextColor(0xFFC7D8E4);
        text.setTextSize(17);
        step.addView(text, new LinearLayout.LayoutParams(0, -2, 1f));
        return step;
    }

    // ---------------------------------------------------------------- networking

    private void networkLoop() {
        while (running && !register()) {
            sleep(4000);
        }
        while (running) {
            try {
                String body = httpGet(SERVER_URL + "/api/poll?deviceId=" + deviceId, 30000);
                JSONObject json = new JSONObject(body);
                boolean nowPaired = json.optBoolean("paired");
                if (nowPaired && !paired) {
                    paired = true;
                    showStatus(getString(R.string.status_paired), DOT_LIVE);
                    handler.post(() -> pairHint.setText(getString(R.string.pair_hint_paired, botUsername)));
                }
                JSONObject command = json.optJSONObject("command");
                if (command != null) {
                    handleCommand(command);
                }
            } catch (Exception e) {
                showStatus(getString(R.string.status_reconnecting), DOT_WAITING);
                sleep(3000);
            }
        }
    }

    private boolean register() {
        showStatus(getString(R.string.status_connecting), DOT_WAITING);
        try {
            String body = httpPost(SERVER_URL + "/api/register");
            JSONObject json = new JSONObject(body);
            deviceId = json.getString("deviceId");
            final String code = json.getString("code");
            botUsername = json.optString("botUsername", "");
            handler.post(() -> {
                codeView.setText(code);
                pairHint.setText(botUsername.isEmpty()
                        ? getString(R.string.pair_hint_open_generic)
                        : getString(R.string.pair_hint_open_named, botUsername));
                renderQr(code);
                setStatus(getString(R.string.status_waiting_pairing), DOT_WAITING);
            });
            return true;
        } catch (Exception e) {
            showStatus(getString(R.string.status_cant_reach), DOT_ERROR);
            return false;
        }
    }

    private void handleCommand(JSONObject command) {
        String type = command.optString("type", "");
        switch (type) {
            case "play":
                final String url = command.optString("url", "");
                final String label = command.optString("label", url);
                if (!url.isEmpty()) {
                    handler.post(() -> {
                        nowPlaying.setText(getString(R.string.now_playing_fmt, label));
                        player.setMediaItem(MediaItem.fromUri(Uri.parse(url)));
                        player.prepare();
                        player.setPlayWhenReady(true);
                        showPlayer();
                    });
                }
                break;
            case "pause":
                handler.post(() -> player.pause());
                break;
            case "resume":
                handler.post(() -> player.play());
                break;
            case "stop":
                handler.post(() -> {
                    player.stop();
                    player.clearMediaItems();
                    showSetup(getString(R.string.status_stopped_telegram));
                });
                break;
            default:
                break;
        }
    }

    private void renderQr(String code) {
        if (botUsername.isEmpty()) {
            qrView.setVisibility(View.INVISIBLE);
            return;
        }
        String deepLink = "https://t.me/" + botUsername + "?start=" + code;
        try {
            int size = dp(190);
            QRCodeWriter writer = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix matrix = writer.encode(deepLink, BarcodeFormat.QR_CODE, size, size, hints);
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            qrView.setImageBitmap(bitmap);
            qrView.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            qrView.setVisibility(View.INVISIBLE);
        }
    }

    // ---------------------------------------------------------------- view switching

    private void showSetup(String message) {
        handler.post(() -> {
            playerContainer.setVisibility(View.GONE);
            setupView.setVisibility(View.VISIBLE);
            nowPlaying.setText(R.string.now_playing_none);
            if (message != null) setStatus(message, paired ? DOT_LIVE : DOT_WAITING);
        });
    }

    private void showPlayer() {
        handler.post(() -> {
            setupView.setVisibility(View.GONE);
            playerContainer.setVisibility(View.VISIBLE);
            playerView.requestFocus();
        });
    }

    private void togglePlayback() {
        if (player.isPlaying()) {
            player.pause();
        } else {
            player.play();
        }
    }

    // ---------------------------------------------------------------- helpers

    private void setStatus(String message, int dotColor) {
        if (status != null) status.setText(message);
        if (statusDot != null && statusDot.getBackground() != null) {
            statusDot.getBackground().setTint(dotColor);
        }
    }

    private void showStatus(String message, int dotColor) {
        handler.post(() -> setStatus(message, dotColor));
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private String httpGet(String endpoint, int readTimeout) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(readTimeout);
        connection.setRequestMethod("GET");
        return readBody(connection);
    }

    private String httpPost(String endpoint) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(10000);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.getOutputStream().close();
        return readBody(connection);
    }

    private String readBody(HttpURLConnection connection) throws Exception {
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
