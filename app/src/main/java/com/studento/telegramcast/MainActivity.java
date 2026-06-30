package com.studento.telegramcast;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.SharedPreferences;
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
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

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
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {
    private static final String SERVER_URL = trimUrl(BuildConfig.SERVER_URL);

    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_MUTED = 0xFF9FB9C8;
    private static final int DOT_WAITING = 0xFFF2B23E;
    private static final int DOT_LIVE = 0xFF43C463;
    private static final int DOT_ERROR = 0xFFE0584F;

    private static final String PREFS = "telegram_tv_cast";
    private static final String HISTORY_KEY = "history";
    private static final int FREE_HISTORY_LIMIT = 5;
    private static final int FREE_QUEUE_LIMIT = 5;
    private static final int MAX_HISTORY_STORED = 30;
    private static final float[] SPEEDS = {1.0f, 1.25f, 1.5f, 1.75f, 2.0f};
    private static final String[] SPEED_LABELS = {"1.0x", "1.25x", "1.5x", "1.75x", "2.0x"};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayDeque<String[]> queue = new ArrayDeque<>();
    private SharedPreferences prefs;
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
    private TextView queueLabel;
    private TextView speedBadge;
    private ProgressBar progress;
    private TextView proBadge;
    private TextView historyEmpty;
    private LinearLayout historyContainer;
    private ObjectAnimator dotPulse;

    private volatile boolean running;
    private volatile boolean regenerateRequested;
    private volatile String deviceId;
    private String botUsername = "";
    private boolean paired;
    private boolean premium;
    private int speedIndex;

    private static String trimUrl(String url) {
        if (url == null) return "";
        return url.replaceAll("/+$", "");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        buildUi();
        refreshHistory();
        startDotPulse();
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
            queue.clear();
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
                    if (!queue.isEmpty()) {
                        String[] next = queue.pollFirst();
                        playMedia(next[0], next[1]);
                    } else {
                        showSetup(getString(R.string.status_complete));
                    }
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

        LinearLayout overlayTop = new LinearLayout(this);
        overlayTop.setOrientation(LinearLayout.VERTICAL);
        overlayTop.setPadding(dp(28), dp(22), dp(28), dp(22));

        nowPlaying = new TextView(this);
        nowPlaying.setText(R.string.now_playing_none);
        nowPlaying.setTextColor(COLOR_TEXT);
        nowPlaying.setTextSize(16);
        nowPlaying.setShadowLayer(8f, 0f, 2f, 0xCC000000);
        overlayTop.addView(nowPlaying, new LinearLayout.LayoutParams(-2, -2));

        queueLabel = new TextView(this);
        queueLabel.setTextColor(COLOR_MUTED);
        queueLabel.setTextSize(14);
        queueLabel.setShadowLayer(8f, 0f, 2f, 0xCC000000);
        queueLabel.setVisibility(View.GONE);
        LinearLayout.LayoutParams queueLabelParams = new LinearLayout.LayoutParams(-2, -2);
        queueLabelParams.topMargin = dp(6);
        overlayTop.addView(queueLabel, queueLabelParams);

        playerContainer.addView(overlayTop, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START));

        speedBadge = new TextView(this);
        speedBadge.setText(SPEED_LABELS[0]);
        speedBadge.setTextColor(0xFFEAF4FB);
        speedBadge.setTextSize(15);
        speedBadge.setTypeface(Typeface.DEFAULT_BOLD);
        speedBadge.setGravity(Gravity.CENTER);
        speedBadge.setBackgroundResource(R.drawable.btn_secondary);
        speedBadge.setPadding(dp(18), dp(10), dp(18), dp(10));
        speedBadge.setFocusable(true);
        speedBadge.setClickable(true);
        speedBadge.setOnClickListener(v -> cycleSpeed());
        FrameLayout.LayoutParams speedParams = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        speedParams.setMargins(0, 0, dp(28), dp(28));
        playerContainer.addView(speedBadge, speedParams);

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
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1f);
        titleParams.setMarginStart(dp(16));
        headerRow.addView(title, titleParams);

        proBadge = new TextView(this);
        proBadge.setText(R.string.badge_pro);
        proBadge.setTextColor(0xFF06202C);
        proBadge.setTextSize(13);
        proBadge.setTypeface(Typeface.DEFAULT_BOLD);
        proBadge.setGravity(Gravity.CENTER);
        proBadge.setBackgroundResource(R.drawable.bg_pro);
        proBadge.setPadding(dp(14), dp(5), dp(14), dp(5));
        proBadge.setVisibility(View.GONE);
        headerRow.addView(proBadge, new LinearLayout.LayoutParams(-2, -2));

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

        Button newCodeButton = new Button(this);
        newCodeButton.setText(R.string.new_code_action);
        newCodeButton.setAllCaps(false);
        newCodeButton.setTextColor(0xFF5FC2EE);
        newCodeButton.setTextSize(14);
        newCodeButton.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        newCodeButton.setBackgroundResource(R.drawable.btn_secondary);
        newCodeButton.setStateListAnimator(null);
        newCodeButton.setPadding(dp(14), dp(8), dp(14), dp(8));
        newCodeButton.setOnClickListener(v -> requestNewCode());
        LinearLayout.LayoutParams newCodeParams = new LinearLayout.LayoutParams(-2, -2);
        newCodeParams.topMargin = dp(10);
        left.addView(newCodeButton, newCodeParams);

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

        // recently played (free feature)
        TextView historyTitle = new TextView(this);
        historyTitle.setText(R.string.history_title);
        historyTitle.setTextColor(0xFF8FA7B5);
        historyTitle.setTextSize(15);
        LinearLayout.LayoutParams historyTitleParams = new LinearLayout.LayoutParams(-1, -2);
        historyTitleParams.topMargin = dp(24);
        historyTitleParams.bottomMargin = dp(8);
        card.addView(historyTitle, historyTitleParams);

        historyEmpty = new TextView(this);
        historyEmpty.setText(R.string.history_empty);
        historyEmpty.setTextColor(COLOR_MUTED);
        historyEmpty.setTextSize(14);
        card.addView(historyEmpty, new LinearLayout.LayoutParams(-1, -2));

        historyContainer = new LinearLayout(this);
        historyContainer.setOrientation(LinearLayout.VERTICAL);
        card.addView(historyContainer, new LinearLayout.LayoutParams(-1, -2));

        // premium teaser (freemium scaffold)
        TextView perks = new TextView(this);
        perks.setText(R.string.premium_perks);
        perks.setTextColor(0xFF6E8595);
        perks.setTextSize(13);
        LinearLayout.LayoutParams perksParams = new LinearLayout.LayoutParams(-1, -2);
        perksParams.topMargin = dp(20);
        card.addView(perks, perksParams);

        Button premiumCta = new Button(this);
        premiumCta.setText(R.string.premium_cta);
        premiumCta.setAllCaps(false);
        premiumCta.setTextColor(0xFF06202C);
        premiumCta.setTypeface(Typeface.DEFAULT_BOLD);
        premiumCta.setTextSize(15);
        premiumCta.setBackgroundResource(R.drawable.btn_pro);
        premiumCta.setStateListAnimator(null);
        premiumCta.setPadding(dp(18), dp(10), dp(18), dp(10));
        premiumCta.setOnClickListener(v ->
                Toast.makeText(this, R.string.premium_coming_soon, Toast.LENGTH_SHORT).show());
        LinearLayout.LayoutParams ctaParams = new LinearLayout.LayoutParams(-2, -2);
        ctaParams.topMargin = dp(12);
        card.addView(premiumCta, ctaParams);

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
            if (regenerateRequested) {
                regenerateRequested = false;
                while (running && !register()) {
                    sleep(4000);
                }
                continue;
            }
            try {
                String body = httpGet(SERVER_URL + "/api/poll?deviceId=" + deviceId, 30000);
                JSONObject json = new JSONObject(body);
                boolean nowPaired = json.optBoolean("paired");
                if (nowPaired && !paired) {
                    paired = true;
                    showStatus(getString(R.string.status_paired), DOT_LIVE);
                    handler.post(() -> {
                        pairHint.setText(getString(R.string.pair_hint_paired, botUsername));
                        stopDotPulse();
                    });
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
            premium = json.optBoolean("premium", false);
            handler.post(() -> {
                codeView.setText(code);
                pairHint.setText(botUsername.isEmpty()
                        ? getString(R.string.pair_hint_open_generic)
                        : getString(R.string.pair_hint_open_named, botUsername));
                renderQr(code);
                proBadge.setVisibility(premium ? View.VISIBLE : View.GONE);
                refreshHistory();
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
                    handler.post(() -> enqueueOrPlay(url, label));
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
                    queue.clear();
                    showSetup(getString(R.string.status_stopped_telegram));
                });
                break;
            case "skip":
                handler.post(() -> {
                    if (!queue.isEmpty()) {
                        String[] next = queue.pollFirst();
                        playMedia(next[0], next[1]);
                    } else {
                        player.stop();
                        player.clearMediaItems();
                        showSetup(getString(R.string.status_stopped_telegram));
                    }
                });
                break;
            case "clear":
                handler.post(() -> {
                    queue.clear();
                    updateQueueLabel();
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
            crossfade(setupView, playerContainer);
            nowPlaying.setText(R.string.now_playing_none);
            if (message != null) setStatus(message, paired ? DOT_LIVE : DOT_WAITING);
        });
    }

    private void showPlayer() {
        handler.post(() -> {
            crossfade(playerContainer, setupView);
            playerView.requestFocus();
        });
    }

    private void crossfade(View showView, View hideView) {
        if (showView.getVisibility() == View.VISIBLE && hideView.getVisibility() == View.GONE) {
            return;
        }
        showView.animate().cancel();
        hideView.animate().cancel();
        showView.setAlpha(0f);
        showView.setVisibility(View.VISIBLE);
        showView.animate().alpha(1f).setDuration(220).start();
        hideView.animate().alpha(0f).setDuration(180).withEndAction(() -> {
            hideView.setVisibility(View.GONE);
            hideView.setAlpha(1f);
        }).start();
    }

    private void togglePlayback() {
        if (player.isPlaying()) {
            player.pause();
        } else {
            player.play();
        }
    }

    // ---------------------------------------------------------------- playback + queue + history

    private void playMedia(String url, String label) {
        nowPlaying.setText(getString(R.string.now_playing_fmt, label));
        speedIndex = 0;
        speedBadge.setText(SPEED_LABELS[0]);
        player.setPlaybackSpeed(SPEEDS[0]);
        player.setMediaItem(MediaItem.fromUri(Uri.parse(url)));
        player.prepare();
        player.setPlayWhenReady(true);
        showPlayer();
        recordHistory(url, label);
        updateQueueLabel();
    }

    private void enqueueOrPlay(String url, String label) {
        boolean currentlyPlaying = playerContainer.getVisibility() == View.VISIBLE
                && player.getPlaybackState() != Player.STATE_IDLE
                && player.getPlaybackState() != Player.STATE_ENDED;
        if (!currentlyPlaying) {
            playMedia(url, label);
            return;
        }
        int limit = premium ? Integer.MAX_VALUE : FREE_QUEUE_LIMIT;
        if (queue.size() >= limit) {
            Toast.makeText(this, R.string.queue_full_toast, Toast.LENGTH_SHORT).show();
            return;
        }
        queue.addLast(new String[]{url, label});
        updateQueueLabel();
    }

    private void updateQueueLabel() {
        if (queue.isEmpty()) {
            queueLabel.setVisibility(View.GONE);
            return;
        }
        String[] next = queue.peekFirst();
        String text = getString(R.string.queue_next_fmt, next[1]);
        if (queue.size() > 1) {
            text += getString(R.string.queue_more_fmt, queue.size() - 1);
        }
        queueLabel.setText(text);
        queueLabel.setVisibility(View.VISIBLE);
    }

    private void cycleSpeed() {
        if (!premium) {
            Toast.makeText(this, R.string.premium_speed_locked, Toast.LENGTH_SHORT).show();
            return;
        }
        speedIndex = (speedIndex + 1) % SPEEDS.length;
        player.setPlaybackSpeed(SPEEDS[speedIndex]);
        speedBadge.setText(SPEED_LABELS[speedIndex]);
    }

    private void requestNewCode() {
        if (deviceId == null) return;
        paired = false;
        startDotPulse();
        setStatus(getString(R.string.status_generating_code), DOT_WAITING);
        regenerateRequested = true;
    }

    private void startDotPulse() {
        if (dotPulse != null || statusDot == null) return;
        dotPulse = ObjectAnimator.ofFloat(statusDot, "alpha", 1f, 0.35f);
        dotPulse.setDuration(900);
        dotPulse.setRepeatMode(ValueAnimator.REVERSE);
        dotPulse.setRepeatCount(ValueAnimator.INFINITE);
        dotPulse.start();
    }

    private void stopDotPulse() {
        if (dotPulse != null) {
            dotPulse.cancel();
            dotPulse = null;
        }
        if (statusDot != null) statusDot.setAlpha(1f);
    }

    private void recordHistory(String url, String label) {
        try {
            org.json.JSONArray arr = loadHistory();
            org.json.JSONArray next = new org.json.JSONArray();
            org.json.JSONObject head = new org.json.JSONObject();
            head.put("url", url);
            head.put("label", label);
            next.put(head);
            for (int i = 0; i < arr.length() && next.length() < MAX_HISTORY_STORED; i++) {
                org.json.JSONObject item = arr.optJSONObject(i);
                if (item != null && !url.equals(item.optString("url"))) {
                    next.put(item);
                }
            }
            prefs.edit().putString(HISTORY_KEY, next.toString()).apply();
            handler.post(this::refreshHistory);
        } catch (Exception ignored) {
        }
    }

    private org.json.JSONArray loadHistory() {
        try {
            return new org.json.JSONArray(prefs.getString(HISTORY_KEY, "[]"));
        } catch (Exception e) {
            return new org.json.JSONArray();
        }
    }

    private void refreshHistory() {
        if (historyContainer == null) return;
        historyContainer.removeAllViews();
        org.json.JSONArray arr = loadHistory();
        historyEmpty.setVisibility(arr.length() == 0 ? View.VISIBLE : View.GONE);

        int limit = premium ? arr.length() : Math.min(arr.length(), FREE_HISTORY_LIMIT);
        for (int i = 0; i < limit; i++) {
            org.json.JSONObject item = arr.optJSONObject(i);
            if (item == null) continue;
            final String url = item.optString("url");
            final String label = item.optString("label", url);
            Button row = new Button(this);
            row.setText(label);
            row.setSingleLine(true);
            row.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.setAllCaps(false);
            row.setTextColor(0xFFEAF4FB);
            row.setTextSize(15);
            row.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            row.setBackgroundResource(R.drawable.btn_secondary);
            row.setStateListAnimator(null);
            row.setPadding(dp(18), dp(10), dp(18), dp(10));
            row.setOnClickListener(v -> playMedia(url, label));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
            rp.topMargin = dp(8);
            historyContainer.addView(row, rp);
        }

        if (!premium && arr.length() > FREE_HISTORY_LIMIT) {
            TextView more = new TextView(this);
            more.setText(getString(R.string.premium_history_locked));
            more.setTextColor(0xFF5FC2EE);
            more.setTextSize(14);
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, -2);
            mp.topMargin = dp(10);
            historyContainer.addView(more, mp);
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
