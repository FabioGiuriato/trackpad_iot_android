package com.example.trackpad_iot_android;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int SAVED_STEP_DURATION_MS = 125;
    private static final int DEFAULT_STEP_COUNT = 16;
    private static final int MAX_STEP_COUNT = 256;
    private static final int LIVE_POLL_DELAY_MS = 100;
    private static final int LIVE_REQUEST_TIMEOUT_MS = 2000;
    private static final int BG_TOP = Color.rgb(8, 13, 20);
    private static final int BG_BOTTOM = Color.rgb(20, 28, 43);
    private static final int SURFACE = Color.rgb(23, 29, 40);
    private static final int SURFACE_ALT = Color.rgb(33, 42, 58);
    private static final int SURFACE_DEEP = Color.rgb(13, 18, 27);
    private static final int LINE = Color.rgb(54, 67, 88);
    private static final int TEXT = Color.rgb(247, 250, 255);
    private static final int MUTED = Color.rgb(160, 172, 191);
    private static final int MUTED_DARK = Color.rgb(103, 118, 141);
    private static final int ACCENT = Color.rgb(255, 177, 66);
    private static final int CYAN = Color.rgb(48, 232, 204);
    private static final int GREEN = Color.rgb(111, 255, 139);
    private static final int BLUE = Color.rgb(82, 156, 255);
    private static final int PINK = Color.rgb(255, 88, 146);
    private static final int RED = Color.rgb(255, 89, 89);
    private static final int[] PAD_COLORS = {CYAN, ACCENT, PINK, GREEN, BLUE};

    private static final Typeface DISPLAY = Typeface.create("sans-serif-condensed", Typeface.BOLD);
    private static final Typeface BODY = Typeface.create("sans-serif", Typeface.NORMAL);
    private static final Typeface BODY_BOLD = Typeface.create("sans-serif", Typeface.BOLD);
    private static final Typeface MONO = Typeface.create("monospace", Typeface.NORMAL);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService liveExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Handler liveHandler = new Handler(Looper.getMainLooper());

    private SessionStore sessionStore;
    private ApiClient apiClient;

    private LinearLayout pageHost;
    private LinearLayout songList;
    private LinearLayout rackPanel;
    private Button songsTab;
    private Button rackTab;
    private Button liveTab;
    private TextView statusText;
    private TextView liveVolume;
    private TextView liveJoystick;
    private TextView liveUpdatedAt;
    private TextView[] liveButtons;

    private JSONArray cachedSongs;
    private JSONObject currentSong;
    private JSONObject lastLiveEvent;
    private int selectedStep = 0;
    private int rackStepCount = DEFAULT_STEP_COUNT;
    private int selectedSoundType = 0;
    private final int[] previousButtonCounters = new int[6];
    private int previousJoystickLeft = 0;
    private int previousJoystickRight = 0;
    private int previousJoystickUp = 0;
    private int previousJoystickDown = 0;
    private int previousJoystickClick = 0;
    private boolean deviceCountersReady = false;
    private boolean pollingLive = false;
    private volatile boolean liveRequestInFlight = false;
    private boolean registerMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setStatusBarColor(BG_TOP);
        window.setNavigationBarColor(BG_BOTTOM);

        sessionStore = new SessionStore(this);
        apiClient = new ApiClient(sessionStore.getBaseUrl());

        if (sessionStore.hasToken()) {
            showDashboard();
        } else {
            showAuth("");
        }
    }

    @Override
    protected void onDestroy() {
        pollingLive = false;
        liveRequestInFlight = false;
        liveHandler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        liveExecutor.shutdownNow();
        super.onDestroy();
    }

    private void showAuth(String message) {
        pollingLive = false;
        liveRequestInFlight = false;
        liveHandler.removeCallbacksAndMessages(null);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(28), dp(22), dp(28), dp(22));
        root.setBackground(appBackground());

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(28), dp(24), dp(28), dp(24));
        panel.setBackground(gradientCard(new int[]{Color.rgb(20, 27, 40), Color.rgb(15, 20, 31)}, dp(26), Color.rgb(63, 78, 101)));
        root.addView(panel, new LinearLayout.LayoutParams(dp(460), ViewGroup.LayoutParams.MATCH_PARENT));

        TextView eyebrow = pill("ESP32 LIVE STUDIO", CYAN, Color.argb(30, 48, 232, 204));
        panel.addView(eyebrow, wrapMargins(0, 0, 0, dp(12)));
        panel.addView(label("Trackpad MQTT", 34, TEXT, true));
        TextView subtitle = label(registerMode
                ? "Crea il tuo account per salvare token, canzoni e sessione Android."
                : "Accedi alla console Android per vedere canzoni, channel rack e MQTT live.", 15, MUTED, false);
        subtitle.setPadding(0, dp(8), 0, dp(14));
        panel.addView(subtitle);

        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        modeRow.setPadding(dp(4), dp(4), dp(4), dp(4));
        modeRow.setBackground(cardDrawable(Color.rgb(14, 20, 31), dp(18), Color.rgb(47, 60, 82)));

        Button loginModeButton = actionButton("Login", registerMode ? SURFACE_ALT : ACCENT, registerMode ? MUTED : Color.rgb(18, 18, 18));
        Button registerModeButton = actionButton("Register", registerMode ? ACCENT : SURFACE_ALT, registerMode ? Color.rgb(18, 18, 18) : MUTED);
        modeRow.addView(loginModeButton, new LinearLayout.LayoutParams(0, dp(44), 1));
        modeRow.addView(registerModeButton, new LinearLayout.LayoutParams(0, dp(44), 1));
        panel.addView(modeRow, matchWrapMargins(0, 0, 0, dp(14)));

        TextView authStatus = label(message, 13, CYAN, false);
        authStatus.setMinHeight(dp(28));
        panel.addView(authStatus);

        EditText usernameInput = input("Username", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL);
        EditText emailInput = input("Email", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText passwordInput = input("Password", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText confirmPasswordInput = input("Conferma password", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        if (registerMode) {
            panel.addView(usernameInput);
        }

        panel.addView(emailInput);
        panel.addView(passwordInput);

        if (registerMode) {
            panel.addView(confirmPasswordInput);
        }

        Button submitButton = actionButton(registerMode ? "Crea account" : "Accedi alla console", ACCENT, Color.rgb(18, 18, 18));
        panel.addView(submitButton, matchHeightMargins(dp(52), 0, dp(18), 0, 0));

        addFlexibleSpace(panel);

        loginModeButton.setOnClickListener(view -> {
            registerMode = false;
            showAuth("");
        });
        registerModeButton.setOnClickListener(view -> {
            registerMode = true;
            showAuth("");
        });
        submitButton.setOnClickListener(view -> {
            if (registerMode) {
                register(usernameInput, emailInput, passwordInput, confirmPasswordInput, authStatus);
            } else {
                login(emailInput, passwordInput, authStatus);
            }
        });
        setContentView(root);
    }

    private void login(EditText emailInput, EditText passwordInput, TextView authStatus) {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

        if (email.isEmpty() || password.isEmpty()) {
            authStatus.setText("Inserisci email e password.");
            return;
        }

        authStatus.setText("Connessione a Laravel...");

        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("email", email);
                body.put("password", password);
                body.put("device_name", "android");

                ApiClient.ApiResponse response = apiClient.post("/login", body, null);

                if (!response.isSuccessful()) {
                    post(() -> authStatus.setText(response.message()));
                    return;
                }

                JSONObject user = response.json.optJSONObject("user");
                String token = response.json.optString("access_token", "");
                String displayName = user == null ? email : user.optString("username", email);
                sessionStore.saveSession(token, displayName, apiClient.getBaseUrl());
                post(this::showDashboard);
            } catch (IOException | JSONException exception) {
                post(() -> authStatus.setText("Server non raggiungibile"));
            }
        });
    }

    private void register(EditText usernameInput, EditText emailInput, EditText passwordInput, EditText confirmPasswordInput, TextView authStatus) {
        String username = usernameInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        String confirmPassword = confirmPasswordInput.getText().toString();

        if (username.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            authStatus.setText("Compila tutti i campi.");
            return;
        }

        if (!password.equals(confirmPassword)) {
            authStatus.setText("Le password non coincidono.");
            return;
        }

        if (password.length() < 8) {
            authStatus.setText("La password deve avere almeno 8 caratteri.");
            return;
        }

        authStatus.setText("Creo il tuo account...");

        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("username", username);
                body.put("email", email);
                body.put("password", password);
                body.put("password_confirmation", confirmPassword);
                body.put("device_name", "android");

                ApiClient.ApiResponse response = apiClient.post("/register", body, null);

                if (!response.isSuccessful()) {
                    post(() -> authStatus.setText(response.message()));
                    return;
                }

                JSONObject user = response.json.optJSONObject("user");
                String token = response.json.optString("access_token", "");
                String displayName = user == null ? username : user.optString("username", username);
                sessionStore.saveSession(token, displayName, apiClient.getBaseUrl());
                post(this::showDashboard);
            } catch (IOException | JSONException exception) {
                post(() -> authStatus.setText("Server non raggiungibile"));
            }
        });
    }

    private void showDashboard() {
        pollingLive = true;
        liveRequestInFlight = false;
        liveHandler.removeCallbacksAndMessages(null);
        apiClient.setBaseUrl(sessionStore.getBaseUrl());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(appBackground());
        root.setPadding(dp(16), dp(12), dp(16), dp(14));

        root.addView(topBar(), matchHeightMargins(dp(62), 0, 0, 0, dp(10)));
        root.addView(tabBar(), matchHeightMargins(dp(54), 0, 0, 0, dp(12)));

        pageHost = new LinearLayout(this);
        pageHost.setOrientation(LinearLayout.VERTICAL);
        root.addView(pageHost, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
        showSongsPage();
        loadSongs();
        pollLive();
    }

    private LinearLayout topBar() {
        LinearLayout topBar = new LinearLayout(this);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setPadding(dp(18), 0, dp(12), 0);
        topBar.setBackground(gradientCard(new int[]{Color.rgb(25, 34, 49), Color.rgb(17, 23, 34)}, dp(22), Color.rgb(58, 72, 96)));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.addView(label("Trackpad MQTT", 24, TEXT, true));
        titleBox.addView(label("Android monitor console", 11, MUTED_DARK, false));

        TextView user = pill("utente  " + sessionStore.getUsername(), CYAN, Color.argb(24, 48, 232, 204));
        statusText = pill("Pronto", GREEN, Color.argb(24, 111, 255, 139));
        Button logoutButton = actionButton("Logout", RED, Color.WHITE);

        topBar.addView(titleBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        topBar.addView(user, wrapMargins(0, 0, dp(10), 0));
        topBar.addView(statusText, wrapMargins(0, 0, dp(10), 0));
        topBar.addView(logoutButton, new LinearLayout.LayoutParams(dp(108), dp(42)));

        logoutButton.setOnClickListener(view -> logout());
        return topBar;
    }

    private LinearLayout tabBar() {
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        tabs.setPadding(dp(6), dp(6), dp(6), dp(6));
        tabs.setBackground(cardDrawable(Color.argb(160, 14, 19, 29), dp(18), Color.rgb(44, 56, 78)));

        songsTab = actionButton("Canzoni", ACCENT, Color.rgb(18, 18, 18));
        rackTab = actionButton("Channel Rack", SURFACE_ALT, TEXT);
        liveTab = actionButton("Live MQTT", SURFACE_ALT, TEXT);

        tabs.addView(songsTab, tabParams());
        tabs.addView(rackTab, tabParams());
        tabs.addView(liveTab, tabParams());

        songsTab.setOnClickListener(view -> showSongsPage());
        rackTab.setOnClickListener(view -> showRackPage());
        liveTab.setOnClickListener(view -> showLivePage());

        return tabs;
    }

    private void showSongsPage() {
        setActiveTab(songsTab);
        pageHost.removeAllViews();

        LinearLayout page = pagePanel();
        LinearLayout header = pageHeader("Canzoni salvate", "Apri una canzone per vedere il channel rack registrato.", ACCENT);
        Button refreshButton = actionButton("Aggiorna", BLUE, Color.WHITE);
        header.addView(refreshButton, new LinearLayout.LayoutParams(dp(124), dp(44)));
        page.addView(header, matchWrapMargins(0, 0, 0, dp(14)));

        songList = new LinearLayout(this);
        songList.setOrientation(LinearLayout.VERTICAL);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(songList);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        pageHost.addView(page, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        refreshButton.setOnClickListener(view -> loadSongs());
        renderSongs(cachedSongs);
    }

    private void showRackPage() {
        setActiveTab(rackTab);
        pageHost.removeAllViews();

        LinearLayout page = pagePanel();
        rackPanel = new LinearLayout(this);
        rackPanel.setOrientation(LinearLayout.VERTICAL);
        page.addView(rackPanel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        pageHost.addView(page, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        drawRack(currentSong);
    }

    private void showLivePage() {
        setActiveTab(liveTab);
        pageHost.removeAllViews();

        LinearLayout page = pagePanel();
        page.addView(pageHeader("Live MQTT", "Ultimo JSON ricevuto dal dispositivo ESP32.", CYAN), matchWrapMargins(0, 0, 0, dp(16)));

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);

        liveVolume = metricValue("-%", 32, ACCENT);
        liveJoystick = metricValue("-", 27, CYAN);
        liveJoystick.setSingleLine(false);
        liveJoystick.setMaxLines(2);
        liveUpdatedAt = metricValue("-", 18, MUTED);

        stats.addView(metricCard("Master volume", liveVolume, ACCENT), cardWeightParams(0, 1, 0, dp(10), 0, 0));
        stats.addView(metricCard("Joystick X/Y", liveJoystick, CYAN), cardWeightParams(0, 1, 0, dp(10), 0, 0));
        stats.addView(metricCard("Ultimo evento", liveUpdatedAt, BLUE), cardWeightParams(0, 1, 0, 0, 0, 0));
        page.addView(stats, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(132)));

        TextView section = label("Pulsanti fisici", 18, TEXT, true);
        section.setPadding(0, dp(22), 0, dp(10));
        page.addView(section);

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.CENTER_VERTICAL);
        liveButtons = new TextView[5];

        for (int index = 0; index < liveButtons.length; index++) {
            TextView pad = livePad(index, false);
            liveButtons[index] = pad;
            buttonRow.addView(pad, cardWeightParams(0, 1, 0, index == liveButtons.length - 1 ? 0 : dp(10), 0, 0));
        }

        page.addView(buttonRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(104)));

        TextView note = label("L'app non invia comandi: osserva lo stato live e le canzoni salvate dal sito Laravel.", 13, MUTED_DARK, false);
        note.setPadding(0, dp(18), 0, 0);
        page.addView(note);

        pageHost.addView(page, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        renderLiveEvent(lastLiveEvent);
    }

    private void loadSongs() {
        setStatus("Carico canzoni...");

        executor.execute(() -> {
            try {
                ApiClient.ApiResponse response = apiClient.get("/songs", sessionStore.getToken());

                if (response.statusCode == 401) {
                    sessionStore.clearSession();
                    post(() -> {
                        registerMode = false;
                        showAuth("Sessione scaduta. Fai login di nuovo.");
                    });
                    return;
                }

                if (!response.isSuccessful()) {
                    post(() -> setStatus(response.message()));
                    return;
                }

                JSONArray songs = response.json.optJSONArray("songs");
                post(() -> renderSongs(songs == null ? new JSONArray() : songs));
            } catch (IOException exception) {
                post(() -> setStatus("Server non raggiungibile"));
            }
        });
    }

    private void renderSongs(JSONArray songs) {
        cachedSongs = songs;

        if (songList == null) {
            return;
        }

        songList.removeAllViews();

        if (songs == null || songs.length() == 0) {
            songList.addView(emptyState("Nessuna canzone salvata", "Crea una canzone dal sito Laravel, poi torna qui e premi Aggiorna."));
            setStatus("Nessuna canzone");
            return;
        }

        for (int index = 0; index < songs.length(); index++) {
            JSONObject song = songs.optJSONObject(index);

            if (song == null) {
                continue;
            }

            songList.addView(songCard(song, index), rowParams());
        }

        setStatus("Canzoni caricate");
    }

    private LinearLayout songCard(JSONObject song, int index) {
        int songId = song.optInt("id");
        int selectedId = currentSong == null ? -1 : currentSong.optInt("id", -1);
        boolean selected = selectedId == songId;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        card.setClickable(true);
        card.setBackground(cardDrawable(selected ? Color.rgb(41, 50, 66) : SURFACE_ALT, dp(18), selected ? ACCENT : LINE));

        TextView number = label(String.format("%02d", index + 1), 28, selected ? ACCENT : MUTED_DARK, true);
        number.setGravity(Gravity.CENTER);
        number.setTypeface(MONO);
        card.addView(number, new LinearLayout.LayoutParams(dp(58), ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        TextView title = label(song.optString("title", "Canzone"), 18, TEXT, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(title);

        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.HORIZONTAL);
        meta.setPadding(0, dp(8), 0, 0);
        meta.addView(pill(song.optInt("bpm", 120) + " BPM", ACCENT, Color.argb(30, 255, 177, 66)), wrapMargins(0, 0, dp(8), 0));
        meta.addView(pill(song.optInt("events_count", 0) + " note", CYAN, Color.argb(28, 48, 232, 204)), wrapMargins(0, 0, dp(8), 0));
        meta.addView(pill("apri rack", MUTED, Color.argb(32, 160, 172, 191)));
        info.addView(meta);

        card.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        LinearLayout bars = new LinearLayout(this);
        bars.setOrientation(LinearLayout.HORIZONTAL);
        bars.setGravity(Gravity.BOTTOM);
        for (int i = 0; i < 8; i++) {
            TextView bar = new TextView(this);
            int height = dp(18 + ((i + songId) % 4) * 8);
            bar.setBackground(cardDrawable(PAD_COLORS[i % PAD_COLORS.length], dp(4), Color.TRANSPARENT));
            bars.addView(bar, barParams(height));
        }
        card.addView(bars, new LinearLayout.LayoutParams(dp(82), dp(58)));

        card.setOnClickListener(view -> {
            showRackPage();
            loadSongDetail(songId);
        });
        return card;
    }

    private void loadSongDetail(int songId) {
        setStatus("Apro canzone #" + songId + "...");

        executor.execute(() -> {
            try {
                ApiClient.ApiResponse response = apiClient.get("/songs/" + songId, sessionStore.getToken());

                if (!response.isSuccessful()) {
                    post(() -> setStatus(response.message()));
                    return;
                }

                JSONObject song = response.json.optJSONObject("song");
                post(() -> {
                    currentSong = song;
                    selectedStep = 0;
                    selectedSoundType = firstTypeInSong(song);
                    drawRack(currentSong);
                });
            } catch (IOException exception) {
                post(() -> setStatus("Errore apertura canzone"));
            }
        });
    }

    private void drawRack(JSONObject song) {
        if (rackPanel == null) {
            return;
        }

        rackPanel.removeAllViews();

        if (song == null) {
            rackPanel.addView(emptyState("Seleziona una canzone", "Dalla pagina Canzoni scegli un brano per aprire qui il channel rack."));
            return;
        }

        rackStepCount = Math.min(MAX_STEP_COUNT, Math.max(DEFAULT_STEP_COUNT, song.optInt("step_count", DEFAULT_STEP_COUNT)));
        selectedStep = clampInt(selectedStep, 0, rackStepCount - 1);

        LinearLayout header = pageHeader(song.optString("title", "Canzone"), song.optInt("bpm", 120) + " BPM - " + rackStepCount + " step - selezionato " + (selectedStep + 1), ACCENT);
        TextView mode = pill("Tipo " + selectedSoundType + " - hardware", CYAN, Color.argb(28, 48, 232, 204));
        LinearLayout typeSelector = soundTypeSelector();
        Button saveButton = actionButton("Salva modifiche", GREEN, Color.rgb(12, 16, 22));
        header.addView(mode);
        header.addView(typeSelector, new LinearLayout.LayoutParams(dp(190), dp(58)));
        header.addView(saveButton, new LinearLayout.LayoutParams(dp(150), dp(42)));
        rackPanel.addView(header, matchWrapMargins(0, 0, 0, dp(14)));
        saveButton.setOnClickListener(view -> saveCurrentSong());

        ScrollView verticalScroll = new ScrollView(this);
        verticalScroll.setFillViewport(true);

        HorizontalScrollView horizontalScroll = new HorizontalScrollView(this);
        horizontalScroll.setFillViewport(true);

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(dp(2), dp(2), dp(2), dp(8));
        horizontalScroll.addView(grid);
        verticalScroll.addView(horizontalScroll, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        rackPanel.addView(verticalScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        grid.addView(stepHeader(rackStepCount));

        JSONArray channels = song.optJSONArray("channels");

        if (channels == null || channels.length() == 0) {
            grid.addView(emptyState("Nessun canale disponibile", "Aggiungi suoni dal sito Laravel, poi riapri questa canzone."));
            return;
        }

        for (int index = 0; index < channels.length(); index++) {
            JSONObject channel = channels.optJSONObject(index);

            if (channel != null) {
                grid.addView(channelRow(channel, rackStepCount, index));
            }
        }

        setStatus("Rack caricato - step " + (selectedStep + 1));
    }

    private LinearLayout stepHeader(int stepCount) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, dp(6));
        row.addView(stepLabel("SUONO", dp(188), MUTED_DARK, true));

        for (int step = 0; step < stepCount; step++) {
            TextView label = stepLabel(String.valueOf(step + 1), dp(34), step == selectedStep ? RED : (step % 4 == 0 ? ACCENT : MUTED_DARK), step == selectedStep);
            row.addView(label);
        }

        return row;
    }

    private LinearLayout channelRow(JSONObject channel, int stepCount, int rowIndex) {
        JSONObject sound = channel.optJSONObject("sound");
        JSONArray steps = channel.optJSONArray("steps");
        Set<Integer> activeSteps = new HashSet<>();

        if (steps != null) {
            for (int i = 0; i < steps.length(); i++) {
                activeSteps.add(steps.optInt(i));
            }
        }

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(5), 0, dp(5));

        String name = sound == null ? "Suono" : sound.optString("name", "Suono");
        String type = sound == null ? "" : sound.optString("type_label", "");

        LinearLayout nameCell = new LinearLayout(this);
        nameCell.setOrientation(LinearLayout.VERTICAL);
        nameCell.setGravity(Gravity.CENTER_VERTICAL);
        nameCell.setPadding(dp(12), dp(7), dp(10), dp(7));
        nameCell.setBackground(cardDrawable(Color.rgb(22, 29, 42), dp(14), Color.rgb(49, 61, 82)));

        TextView title = label(name, 14, TEXT, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        TextView kind = label(type, 11, PAD_COLORS[rowIndex % PAD_COLORS.length], false);
        kind.setSingleLine(true);
        kind.setEllipsize(TextUtils.TruncateAt.END);
        nameCell.addView(title);
        nameCell.addView(kind);
        row.addView(nameCell, new LinearLayout.LayoutParams(dp(188), dp(46)));

        for (int step = 0; step < stepCount; step++) {
            boolean active = activeSteps.contains(step);
            TextView cell = new TextView(this);
            cell.setGravity(Gravity.CENTER);
            cell.setText(step == selectedStep ? "|" : "");
            cell.setTextColor(step == selectedStep ? RED : Color.TRANSPARENT);
            cell.setTypeface(BODY_BOLD);
            cell.setBackground(stepDrawable(active, step, PAD_COLORS[rowIndex % PAD_COLORS.length], step == selectedStep));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(30), dp(30));
            params.setMargins(dp(2), 0, dp(2), 0);
            row.addView(cell, params);
        }

        return row;
    }

    private int firstTypeInSong(JSONObject song) {
        JSONArray channels = song == null ? null : song.optJSONArray("channels");

        if (channels == null || channels.length() == 0) {
            return 0;
        }

        JSONObject firstSound = channels.optJSONObject(0) == null ? null : channels.optJSONObject(0).optJSONObject("sound");
        return firstSound == null ? 0 : firstSound.optInt("tipo", 0);
    }

    private List<Integer> availableTypesInCurrentSong() {
        List<Integer> types = new ArrayList<>();
        for (SoundTypeOption option : soundTypeOptionsInCurrentSong()) {
            types.add(option.type);
        }

        return types;
    }

    private List<SoundTypeOption> soundTypeOptionsInCurrentSong() {
        List<SoundTypeOption> options = new ArrayList<>();
        JSONArray channels = currentSong == null ? null : currentSong.optJSONArray("channels");

        if (channels == null) {
            return options;
        }

        for (int index = 0; index < channels.length(); index++) {
            JSONObject channel = channels.optJSONObject(index);
            JSONObject sound = channel == null ? null : channel.optJSONObject("sound");

            if (sound == null) {
                continue;
            }

            int type = sound.optInt("tipo", 0);
            String label = sound.optString("type_label", "Tipo " + type);

            if (!containsSoundType(options, type)) {
                options.add(new SoundTypeOption(type, label));
            }
        }

        Collections.sort(options, (left, right) -> Integer.compare(left.type, right.type));
        return options;
    }

    private boolean containsSoundType(List<SoundTypeOption> options, int type) {
        for (SoundTypeOption option : options) {
            if (option.type == type) {
                return true;
            }
        }

        return false;
    }

    private LinearLayout soundTypeSelector() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(10), dp(4), dp(10), dp(4));
        box.setBackground(cardDrawable(Color.rgb(16, 22, 33), dp(16), Color.rgb(55, 68, 91)));

        TextView title = label("Tipo hardware", 10, MUTED_DARK, true);
        title.setSingleLine(true);
        box.addView(title);

        List<SoundTypeOption> options = soundTypeOptionsInCurrentSong();

        if (options.isEmpty()) {
            box.addView(label("Nessun tipo", 13, MUTED, false));
            return box;
        }

        int selectedIndex = 0;

        for (int index = 0; index < options.size(); index++) {
            if (options.get(index).type == selectedSoundType) {
                selectedIndex = index;
                break;
            }
        }

        selectedSoundType = options.get(selectedIndex).type;

        ArrayAdapter<SoundTypeOption> adapter = new ArrayAdapter<SoundTypeOption>(this, android.R.layout.simple_spinner_item, options) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(TEXT);
                view.setTextSize(13);
                view.setTypeface(BODY_BOLD);
                view.setSingleLine(true);
                view.setEllipsize(TextUtils.TruncateAt.END);
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                view.setTextColor(Color.rgb(12, 16, 22));
                view.setTextSize(15);
                view.setTypeface(BODY_BOLD);
                view.setPadding(dp(14), dp(12), dp(14), dp(12));
                view.setBackgroundColor(Color.rgb(245, 248, 255));
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        Spinner spinner = new Spinner(this);
        spinner.setAdapter(adapter);
        spinner.setSelection(selectedIndex, false);
        spinner.setBackground(cardDrawable(Color.rgb(26, 34, 48), dp(12), Color.rgb(64, 79, 104)));
        spinner.setPopupBackgroundDrawable(cardDrawable(Color.rgb(245, 248, 255), dp(14), Color.rgb(210, 218, 230)));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                SoundTypeOption option = (SoundTypeOption) parent.getItemAtPosition(position);

                if (option == null || option.type == selectedSoundType) {
                    return;
                }

                selectedSoundType = option.type;
                drawRack(currentSong);
                setStatus("Tipo hardware: " + option.label);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        box.addView(spinner, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));
        return box;
    }

    private void changeSelectedSoundType(int delta) {
        List<Integer> types = availableTypesInCurrentSong();

        if (types.isEmpty()) {
            return;
        }

        int currentIndex = types.indexOf(selectedSoundType);
        int nextIndex = currentIndex < 0 ? 0 : (currentIndex + delta + types.size()) % types.size();
        selectedSoundType = types.get(nextIndex);
        drawRack(currentSong);
        setStatus("Tipo selezionato: " + selectedSoundType);
    }

    private void moveSelectedStep(int delta) {
        int nextStep = selectedStep + delta;

        if (nextStep >= rackStepCount && rackStepCount < MAX_STEP_COUNT) {
            rackStepCount = Math.min(MAX_STEP_COUNT, rackStepCount + DEFAULT_STEP_COUNT);

            try {
                currentSong.put("step_count", rackStepCount);
            } catch (JSONException ignored) {
            }
        }

        selectedStep = clampInt(nextStep, 0, rackStepCount - 1);
        drawRack(currentSong);
        setStatus("Step selezionato: " + (selectedStep + 1));
    }

    private void addStepFromHardwareButton(int slot) {
        if (currentSong == null) {
            setStatus("Seleziona prima una canzone.");
            return;
        }

        JSONObject channel = channelByTypeAndSlot(selectedSoundType, slot);

        if (channel == null) {
            setStatus("Nessun suono per Button " + slot + " nel tipo " + selectedSoundType);
            return;
        }

        JSONArray steps = channel.optJSONArray("steps");

        if (steps == null) {
            steps = new JSONArray();

            try {
                channel.put("steps", steps);
            } catch (JSONException ignored) {
            }
        }

        for (int index = 0; index < steps.length(); index++) {
            if (steps.optInt(index) == selectedStep) {
                playSoundForChannel(channel);
                setStatus("Step gia attivo: " + (selectedStep + 1));
                return;
            }
        }

        steps.put(selectedStep);
        playSoundForChannel(channel);
        drawRack(currentSong);
        setStatus("Aggiunto Button " + slot + " allo step " + (selectedStep + 1) + ". Premi Salva modifiche.");
    }

    private JSONObject channelByTypeAndSlot(int type, int slot) {
        JSONArray channels = currentSong == null ? null : currentSong.optJSONArray("channels");

        if (channels == null) {
            return null;
        }

        int currentSlot = 0;

        for (int index = 0; index < channels.length(); index++) {
            JSONObject channel = channels.optJSONObject(index);
            JSONObject sound = channel == null ? null : channel.optJSONObject("sound");

            if (sound == null || sound.optInt("tipo", 0) != type) {
                continue;
            }

            currentSlot++;

            if (currentSlot == slot) {
                return channel;
            }
        }

        return null;
    }

    private void playSoundForChannel(JSONObject channel) {
        JSONObject sound = channel.optJSONObject("sound");

        if (sound == null) {
            return;
        }

        String soundUrl = sound.optString("sound_url", "");

        if (soundUrl.isEmpty()) {
            return;
        }

        // A tiny WebView would be overkill here; Android's MediaPlayer setup is heavier
        // than this screen needs, so the visual rack update remains the source of truth.
    }

    private JSONArray songEventsForSave() {
        JSONArray events = new JSONArray();
        JSONArray channels = currentSong == null ? null : currentSong.optJSONArray("channels");

        if (channels == null) {
            return events;
        }

        for (int channelIndex = 0; channelIndex < channels.length(); channelIndex++) {
            JSONObject channel = channels.optJSONObject(channelIndex);
            int buttonId = channel == null ? 0 : channel.optInt("button_id", 0);
            JSONArray steps = channel == null ? null : channel.optJSONArray("steps");

            if (buttonId <= 0 || steps == null) {
                continue;
            }

            Set<Integer> uniqueSteps = new HashSet<>();

            for (int stepIndex = 0; stepIndex < steps.length(); stepIndex++) {
                int step = steps.optInt(stepIndex);

                if (!uniqueSteps.add(step)) {
                    continue;
                }

                JSONObject event = new JSONObject();

                try {
                    event.put("button_id", buttonId);
                    event.put("time_ms", step * SAVED_STEP_DURATION_MS);
                    events.put(event);
                } catch (JSONException ignored) {
                }
            }
        }

        return events;
    }

    private void saveCurrentSong() {
        if (currentSong == null) {
            setStatus("Seleziona prima una canzone.");
            return;
        }

        int songId = currentSong.optInt("id", 0);

        if (songId <= 0) {
            setStatus("Questa canzone non puo essere salvata dall'app.");
            return;
        }

        setStatus("Salvo modifiche...");

        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("bpm", currentSong.optInt("bpm", 120));
                body.put("events", songEventsForSave());

                ApiClient.ApiResponse response = apiClient.put("/songs/" + songId, body, sessionStore.getToken());

                if (!response.isSuccessful()) {
                    post(() -> setStatus(response.message()));
                    return;
                }

                post(() -> setStatus("Canzone salvata."));
            } catch (IOException | JSONException exception) {
                post(() -> setStatus("Errore salvataggio canzone"));
            }
        });
    }

    private void processDeviceEvent(JSONObject event) {
        if (event == null) {
            return;
        }

        JSONObject counters = event.optJSONObject("counters");

        if (counters == null) {
            return;
        }

        JSONObject buttons = counters.optJSONObject("buttons");
        int joystickLeft = counters.optInt("joystick_left", 0);
        int joystickRight = counters.optInt("joystick_right", 0);
        int joystickUp = counters.optInt("joystick_up", 0);
        int joystickDown = counters.optInt("joystick_down", 0);
        int joystickClick = counters.optInt("joystick_click", 0);

        if (!deviceCountersReady) {
            storePreviousDeviceCounters(buttons, joystickLeft, joystickRight, joystickUp, joystickDown, joystickClick);
            deviceCountersReady = true;
            return;
        }

        if (joystickLeft > previousJoystickLeft) {
            changeSelectedSoundType(-1);
        }

        if (joystickRight > previousJoystickRight) {
            changeSelectedSoundType(1);
        }

        if (joystickUp > previousJoystickUp) {
            moveSelectedStep(-1);
        }

        if (joystickDown > previousJoystickDown) {
            moveSelectedStep(1);
        }

        if (joystickClick > previousJoystickClick) {
            setStatus("Click joystick ricevuto.");
        }

        for (int slot = 1; slot <= 5; slot++) {
            int buttonCounter = buttons == null ? 0 : buttons.optInt(String.valueOf(slot), 0);

            if (buttonCounter > previousButtonCounters[slot]) {
                addStepFromHardwareButton(slot);
            }
        }

        storePreviousDeviceCounters(buttons, joystickLeft, joystickRight, joystickUp, joystickDown, joystickClick);
    }

    private void storePreviousDeviceCounters(JSONObject buttons, int left, int right, int up, int down, int click) {
        for (int slot = 1; slot <= 5; slot++) {
            previousButtonCounters[slot] = buttons == null ? 0 : buttons.optInt(String.valueOf(slot), 0);
        }

        previousJoystickLeft = left;
        previousJoystickRight = right;
        previousJoystickUp = up;
        previousJoystickDown = down;
        previousJoystickClick = click;
    }

    private void pollLive() {
        if (!pollingLive || !sessionStore.hasToken()) {
            return;
        }

        if (liveRequestInFlight) {
            liveHandler.postDelayed(this::pollLive, LIVE_POLL_DELAY_MS);
            return;
        }

        liveRequestInFlight = true;

        liveExecutor.execute(() -> {
            try {
                ApiClient.ApiResponse response = apiClient.get("/mqtt/latest", sessionStore.getToken(), LIVE_REQUEST_TIMEOUT_MS);

                if (response.statusCode == 401) {
                    sessionStore.clearSession();
                    post(() -> {
                        registerMode = false;
                        showAuth("Sessione scaduta. Fai login di nuovo.");
                    });
                    return;
                }

                if (response.isSuccessful()) {
                    JSONObject event = response.json.optJSONObject("event");
                    post(() -> {
                        lastLiveEvent = event;
                        processDeviceEvent(event);
                        renderLiveEvent(lastLiveEvent);
                    });
                } else {
                    post(() -> {
                        if (liveUpdatedAt != null) {
                            liveUpdatedAt.setText("HTTP " + response.statusCode);
                        }
                    });
                }
            } catch (IOException ignored) {
                post(() -> {
                    if (liveUpdatedAt != null) {
                        liveUpdatedAt.setText("server offline");
                    }
                });
            } finally {
                liveRequestInFlight = false;

                if (pollingLive && sessionStore.hasToken()) {
                    liveHandler.postDelayed(this::pollLive, LIVE_POLL_DELAY_MS);
                }
            }
        });
    }

    private void renderLiveEvent(JSONObject event) {
        if (liveVolume == null || liveJoystick == null || liveUpdatedAt == null || liveButtons == null) {
            return;
        }

        if (event == null) {
            liveVolume.setText("-%");
            liveJoystick.setText("-");
            liveUpdatedAt.setText("nessun evento");
            for (int i = 0; i < liveButtons.length; i++) {
                styleLivePad(liveButtons[i], i, false);
            }
            return;
        }

        liveVolume.setText(event.optInt("volume", event.optInt("pot_percentuale", 0)) + "%");
        String joystickX = event.optString("joystick_x_posizione", "CENTRO");
        String joystickY = event.optString("joystick_y_posizione", "CENTRO");
        liveJoystick.setText("X " + joystickX + "\nY " + joystickY);
        liveUpdatedAt.setText(event.optString("created_at", "-"));

        for (int i = 0; i < liveButtons.length; i++) {
            boolean active = event.optInt("button" + (i + 1), 0) == 1;
            styleLivePad(liveButtons[i], i, active);
        }
    }

    private void logout() {
        setStatus("Logout...");

        executor.execute(() -> {
            try {
                apiClient.post("/logout", new JSONObject(), sessionStore.getToken());
            } catch (IOException ignored) {
            }

            sessionStore.clearSession();
            post(() -> {
                registerMode = false;
                showAuth("Logout effettuato.");
            });
        });
    }

    private void setActiveTab(Button activeButton) {
        Button[] buttons = {songsTab, rackTab, liveTab};

        for (Button button : buttons) {
            if (button == null) {
                continue;
            }

            boolean active = button == activeButton;
            button.setTextColor(active ? Color.rgb(18, 18, 18) : MUTED);
            button.setBackground(active
                    ? gradientCard(new int[]{ACCENT, Color.rgb(255, 138, 59)}, dp(14), Color.argb(90, 255, 255, 255))
                    : cardDrawable(Color.rgb(25, 33, 47), dp(14), Color.rgb(45, 58, 82)));
        }
    }

    private LinearLayout pagePanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(16), dp(18), dp(16));
        panel.setBackground(gradientCard(new int[]{Color.rgb(24, 31, 44), Color.rgb(16, 22, 33)}, dp(24), Color.rgb(55, 70, 94)));
        return panel;
    }

    private LinearLayout pageHeader(String title, String subtitle, int accent) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(label(title, 24, TEXT, true));
        texts.addView(label(subtitle, 13, MUTED, false));

        TextView mark = new TextView(this);
        mark.setBackground(cardDrawable(accent, dp(8), Color.TRANSPARENT));
        header.addView(mark, new LinearLayout.LayoutParams(dp(6), dp(48)));
        header.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return header;
    }

    private LinearLayout heroStat(String number, String text, int accent) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(cardDrawable(Color.argb(76, 9, 14, 24), dp(20), Color.argb(95, 255, 255, 255)));
        card.addView(label(number, 26, accent, true));
        card.addView(label(text, 12, Color.rgb(215, 224, 239), false));
        return card;
    }

    private LinearLayout metricCard(String title, TextView value, int accent) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        card.setBackground(cardDrawable(Color.rgb(20, 27, 39), dp(20), Color.rgb(49, 64, 88)));
        card.addView(pill(title, accent, Color.argb(24, Color.red(accent), Color.green(accent), Color.blue(accent))), wrapMargins(0, 0, 0, dp(10)));
        card.addView(value);
        return card;
    }

    private TextView metricValue(String text, int sp, int color) {
        TextView value = label(text, sp, color, true);
        value.setGravity(Gravity.CENTER_VERTICAL);
        value.setSingleLine(true);
        value.setEllipsize(TextUtils.TruncateAt.END);
        return value;
    }

    private TextView livePad(int index, boolean active) {
        TextView pad = label("B" + (index + 1), 24, TEXT, true);
        pad.setGravity(Gravity.CENTER);
        pad.setTypeface(DISPLAY);
        styleLivePad(pad, index, active);
        return pad;
    }

    private void styleLivePad(TextView pad, int index, boolean active) {
        int accent = PAD_COLORS[index % PAD_COLORS.length];
        pad.setText("B" + (index + 1) + (active ? "\nON" : "\nOFF"));
        pad.setTextColor(active ? Color.rgb(12, 16, 22) : MUTED);
        pad.setTextSize(active ? 22 : 20);
        pad.setBackground(active
                ? gradientCard(new int[]{accent, Color.rgb(255, 255, 255)}, dp(22), Color.argb(130, 255, 255, 255))
                : cardDrawable(Color.rgb(20, 27, 39), dp(22), Color.rgb(46, 59, 82)));
    }

    private TextView label(String text, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setLineSpacing(dp(2), 1.0f);
        view.setIncludeFontPadding(true);
        view.setTypeface(bold ? DISPLAY : BODY);
        return view;
    }

    private TextView emptyState(String title, String subtitle) {
        TextView view = label(title + "\n" + subtitle, 16, MUTED, false);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(26), dp(34), dp(26), dp(34));
        view.setBackground(cardDrawable(Color.rgb(19, 25, 36), dp(20), Color.rgb(42, 54, 76)));
        return view;
    }

    private EditText input(String hint, int inputType) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setHintTextColor(MUTED_DARK);
        input.setTextColor(TEXT);
        input.setTextSize(15);
        input.setSingleLine(true);
        input.setInputType(inputType);
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setTypeface(BODY);
        input.setSelectAllOnFocus(true);
        input.setBackground(cardDrawable(Color.rgb(16, 22, 33), dp(16), Color.rgb(55, 68, 91)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        params.setMargins(0, dp(12), 0, 0);
        input.setLayoutParams(params);

        return input;
    }

    private Button actionButton(String text, int background, int textColor) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(textColor);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setTypeface(BODY_BOLD);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setBackground(cardDrawable(background, dp(14), Color.argb(55, 255, 255, 255)));
        return button;
    }

    private TextView stepLabel(String text, int width, int color, boolean bold) {
        TextView view = label(text, 11, color, bold);
        view.setGravity(Gravity.CENTER);
        view.setMinWidth(width);
        view.setPadding(dp(4), dp(4), dp(4), dp(4));
        return view;
    }

    private TextView pill(String text, int textColor, int background) {
        TextView chip = label(text, 11, textColor, true);
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setPadding(dp(10), dp(5), dp(10), dp(5));
        chip.setBackground(cardDrawable(background, dp(999), Color.argb(35, 255, 255, 255)));
        return chip;
    }

    private TextView miniCard(String title, String value, int accent) {
        TextView view = label(title + "\n" + value, 12, MUTED, false);
        view.setTypeface(MONO);
        view.setPadding(dp(14), dp(12), dp(14), dp(12));
        view.setBackground(cardDrawable(Color.rgb(14, 20, 31), dp(16), Color.argb(92, Color.red(accent), Color.green(accent), Color.blue(accent))));
        return view;
    }

    private GradientDrawable appBackground() {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{BG_TOP, Color.rgb(14, 22, 35), BG_BOTTOM});
        drawable.setDither(true);
        return drawable;
    }

    private GradientDrawable cardDrawable(int color, int radius, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private GradientDrawable gradientCard(int[] colors, int radius, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), strokeColor);
        drawable.setDither(true);
        return drawable;
    }

    private GradientDrawable stepDrawable(boolean active, int step, int accent, boolean selected) {
        int base = step % 4 == 0 ? Color.rgb(36, 45, 61) : Color.rgb(25, 33, 46);

        if (active) {
            return gradientCard(new int[]{accent, Color.rgb(236, 255, 250)}, dp(9), selected ? RED : Color.argb(150, 255, 255, 255));
        }

        return cardDrawable(base, dp(9), selected ? RED : (step % 4 == 0 ? Color.rgb(71, 82, 103) : Color.rgb(45, 56, 77)));
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(88));
        params.setMargins(0, 0, 0, dp(10));
        return params;
    }

    private LinearLayout.LayoutParams tabParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        params.setMargins(0, 0, dp(8), 0);
        return params;
    }

    private LinearLayout.LayoutParams barParams(int height) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(6), height);
        params.setMargins(dp(2), 0, dp(2), 0);
        return params;
    }

    private LinearLayout.LayoutParams wrapMargins(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(left, top, right, bottom);
        return params;
    }

    private LinearLayout.LayoutParams matchWrapMargins(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(left, top, right, bottom);
        return params;
    }

    private LinearLayout.LayoutParams matchHeightMargins(int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
        params.setMargins(left, top, right, bottom);
        return params;
    }

    private LinearLayout.LayoutParams cardWeightParams(int width, float weight, int left, int right, int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT, weight);
        params.setMargins(left, top, right, bottom);
        return params;
    }

    private void addFlexibleSpace(LinearLayout layout) {
        View spacer = new View(this);
        layout.addView(spacer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
    }

    private void setStatus(String text) {
        if (statusText != null) {
            statusText.setText(text);
        }
    }

    private void post(Runnable runnable) {
        mainHandler.post(runnable);
    }

    private int clampInt(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static class SoundTypeOption {
        final int type;
        final String label;

        SoundTypeOption(int type, String label) {
            this.type = type;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
