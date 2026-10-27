package com.finresearch.mobile;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.InputType;
import android.view.View;
import android.view.WindowInsets;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Standalone Android research MVP. Not the official AI4Finance FinRobot project.
 * External quote/news/API reliability is not guaranteed. Never executes trades.
 */
public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(12, 18, 30);
    private static final int PANEL = Color.rgb(24, 34, 52);
    private static final int ACCENT = Color.rgb(25, 136, 147);
    private static final int SUBTEXT = Color.rgb(183, 198, 211);
    private static final String[] ROLES = {
        "Fundamentals", "Technicals", "Sentiment",
        "Risk", "Valuation", "Investment thesis"
    };
    private static final String NVIDIA_URL = "https://integrate.api.nvidia.com/v1/chat/completions";
    private static final String KEYSTORE_ALIAS = "FinResearchMobile_ApiKey_v1";
    private SharedPreferences prefs;
    private EditText symbolInput, headlineInput, keyInput;
    private EditText[] models;
    private TextView quoteResult, sentimentResult, aiResult;
    private Spinner roleSpinner;
    private volatile String latestQuote = "";
    private volatile boolean running = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("finresearch", Context.MODE_PRIVATE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(40));
        scroll.addView(root);
        setContentView(scroll);
        if (Build.VERSION.SDK_INT >= 30) {
            scroll.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets sys = insets.getInsets(WindowInsets.Type.systemBars());
                root.setPadding(dp(18), dp(18) + sys.top, dp(18), dp(40) + sys.bottom);
                return insets;
            });
        }

        TextView title = text("FinResearch Mobile", 26, Color.WHITE, true);
        root.addView(title);
        root.addView(text("Independent Android financial research prototype • No trades", 12, SUBTEXT, false));

        LinearLayout market = panel(root);
        market.addView(text("1  MARKET DATA", 17, Color.WHITE, true));
        market.addView(text("Quotes are best-effort Yahoo Finance data. Use .NS for NSE (example: RELIANCE.NS).", 12, SUBTEXT, false));
        symbolInput = input("Ticker: AAPL or RELIANCE.NS", false);
        symbolInput.setText(prefs.getString("symbol", "AAPL"));
        market.addView(symbolInput);
        Button quoteButton = button("FETCH PRICE & INDICATORS");
        market.addView(quoteButton);
        quoteResult = text("No market data loaded. Live API access may be rate-limited.", 14, SUBTEXT, false);
        market.addView(quoteResult);
        quoteButton.setOnClickListener(v -> fetchQuote());

        LinearLayout sentiments = panel(root);
        sentiments.addView(text("2  NEWS & SENTIMENT", 17, Color.WHITE, true));
        sentiments.addView(text("Paste genuine, dated headlines or social posts. Offline score is a keyword count, not an AI prediction.", 12, SUBTEXT, false));
        headlineInput = input("Paste headline(s) here...", true);
        headlineInput.setMinLines(4);
        sentiments.addView(headlineInput);
        Button sentimentButton = button("SCORE PASTED HEADLINES (OFFLINE)");
        sentiments.addView(sentimentButton);
        sentimentResult = text("No sentiment calculated.", 14, SUBTEXT, false);
        sentiments.addView(sentimentResult);
        sentimentButton.setOnClickListener(v -> scoreSentiment());

        LinearLayout ai = panel(root);
        ai.addView(text("3  SIX-MODEL AI RESEARCH", 17, Color.WHITE, true));
        ai.addView(text("Select an analyst role. Each role can use its own NVIDIA-hosted model. Only you initiate API calls.", 12, SUBTEXT, false));
        roleSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ROLES);
        roleSpinner.setAdapter(adapter);
        ai.addView(roleSpinner);
        Button analyzeButton = button("ANALYZE SELECTED ROLE VIA NVIDIA");
        ai.addView(analyzeButton);
        aiResult = text("Configure your NVIDIA API key and model names below to enable cloud analysis.", 14, SUBTEXT, false);
        ai.addView(aiResult);
        analyzeButton.setOnClickListener(v -> analyze());

        LinearLayout settings = panel(root);
        settings.addView(text("4  NVIDIA SETTINGS", 17, Color.WHITE, true));
        settings.addView(text("Use a key from build.nvidia.com. It is encrypted with Android Keystore and sent only to NVIDIA when you initiate an analysis.", 12, SUBTEXT, false));
        keyInput = input("NVIDIA API key (leave blank to keep saved key)", false);
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        settings.addView(keyInput);
        models = new EditText[ROLES.length];
        for (int i = 0; i < ROLES.length; i++) {
            settings.addView(text(ROLES[i] + " model ID", 12, SUBTEXT, false));
            models[i] = input("e.g. nvidia/your-model-id", false);
            models[i].setText(prefs.getString("model_" + i, ""));
            settings.addView(models[i]);
        }
        Button save = button("SAVE SETTINGS ON DEVICE");
        settings.addView(save);
        save.setOnClickListener(v -> saveSettings());

        LinearLayout footer = panel(root);
        footer.addView(text("Scope & limitations", 15, Color.WHITE, true));
        footer.addView(text("This is not the official FinRobot V2 engine. It has no broker connection, live Reddit/X integration, automatic fundamentals feed, audited DCF, or investment execution. Cloud AI can hallucinate. Independently verify prices, timestamps, calculations and recommendations.", 12, SUBTEXT, false));
    }

    private void fetchQuote() {
        final String symbol = symbolInput.getText().toString().trim().toUpperCase(Locale.ROOT);
        if (!symbol.matches("[A-Z0-9.^=_-]{1,24}")) {
            quoteResult.setText("Invalid symbol: use a ticker such as AAPL or RELIANCE.NS.");
            return;
        }
        prefs.edit().putString("symbol", symbol).apply();
        quoteResult.setText("Fetching price history...");
        new Thread(() -> {
            try {
                String encoded = URLEncoder.encode(symbol, "UTF-8");
                URL url = new URL("https://query1.finance.yahoo.com/v8/finance/chart/" + encoded + "?range=6mo&interval=1d");
                JSONObject json = new JSONObject(get(url));
                JSONObject chart = json.getJSONObject("chart");
                JSONArray results = chart.getJSONArray("result");
                if (results.length() == 0 || results.isNull(0)) throw new IllegalStateException("Ticker not found.");
                JSONObject result = results.getJSONObject(0);
                JSONArray raw = result.getJSONObject("indicators").getJSONArray("quote")
                    .getJSONObject(0).getJSONArray("close");
                List<Double> prices = new ArrayList<>();
                for (int i = 0; i < raw.length(); i++) {
                    if (!raw.isNull(i)) {
                        double value = raw.getDouble(i);
                        if (!Double.isNaN(value) && value > 0) prices.add(value);
                    }
                }
                if (prices.size() < 2) throw new IllegalStateException("Not enough valid prices.");
                String currency = result.getJSONObject("meta").optString("currency", "");
                String output = String.format(Locale.US,
                    "%s • %s\nLast historical close: %.2f\nChange vs preceding close: %+.2f%%\nSMA20: %s\nSMA50: %s\nSimple RSI14: %s\nObservations: %d\nSource: Yahoo Finance chart; check market-date freshness.",
                    symbol, currency, prices.get(prices.size()-1),
                    ResearchMath.percentChange(prices), fmt(ResearchMath.sma(prices, 20)),
                    fmt(ResearchMath.sma(prices, 50)), fmt(ResearchMath.rsi(prices,14)),
                    prices.size());
                latestQuote = output;
                runOnUiThread(() -> quoteResult.setText(output));
            } catch (Exception ex) {
                latestQuote = "";
                runOnUiThread(() -> quoteResult.setText(
                    "Price request failed (" + ex.getClass().getSimpleName() +
                    "). Yahoo may block unauthenticated requests or the ticker may be invalid. No price was invented."));
            }
        }).start();
    }

    private static String fmt(double n) {
        return Double.isNaN(n) || Double.isInfinite(n) ? "Insufficient data" : String.format(Locale.US,"%.2f",n);
    }

    private void scoreSentiment() {
        String text = headlineInput.getText().toString().trim();
        if (text.isEmpty()) {
            sentimentResult.setText("Paste headlines first.");
            return;
        }
        double score = HeadlineSentiment.score(text);
        String label = score > 0.1 ? "Positive" : score < -0.1 ? "Negative" : "Neutral/mixed";
        sentimentResult.setText(String.format(Locale.US,
            "Keyword sentiment: %s (%+.2f on -1 to +1 scale)\nNot validated against future returns.",
            label, score));
    }

    private void saveSettings() {
        String newKey = keyInput.getText().toString().trim();
        try {
            if (!newKey.isEmpty()) {
                encryptKey(newKey);
                keyInput.setText("");
            }
            SharedPreferences.Editor edit = prefs.edit();
            for (int i=0; i<ROLES.length; i++) edit.putString("model_" + i, models[i].getText().toString().trim());
            edit.apply();
            aiResult.setText("Saved. Choose an AI role and tap Analyze.");
        } catch (Exception e) {
            aiResult.setText("Unable to encrypt and save key ("+e.getClass().getSimpleName()+").");
        }
    }

    private void analyze() {
        if (running) {
            aiResult.setText("An analysis is already running.");
            return;
        }
        int selected = roleSpinner.getSelectedItemPosition();
        if (selected < 0 || selected >= ROLES.length) return;
        String model = models[selected].getText().toString().trim();
        if (model.isEmpty()) {
            aiResult.setText("Enter an NVIDIA model ID for " + ROLES[selected] + " in Settings, then Save.");
            return;
        }
        final String key;
        try { key = decryptKey(); }
        catch (Exception e) { aiResult.setText("Saved API key could not be decrypted. Enter and save it again."); return; }
        if (key.isEmpty()) {
            aiResult.setText("Enter and SAVE your NVIDIA API key before analyzing.");
            return;
        }
        String manualText = headlineInput.getText().toString().trim();
        if (manualText.length() > 6000) manualText = manualText.substring(0,6000);
        final String prompt = "Act as a cautious " + ROLES[selected] + " financial research assistant. " +
            "Use only the provided information and explicitly distinguish unknowns. " +
            "Do not invent prices, dates, filings, financial ratios, or numerical valuations. " +
            "Do not recommend trades without verified evidence; explain risks and sources. " +
            "Ticker: " + symbolInput.getText().toString().trim() + "\n" +
            "Fetched price/indicators: " + (latestQuote.isEmpty() ? "Not available" : latestQuote) + "\n" +
            "User-supplied headlines/posts (unverified): " + manualText + "\n" +
            "Provide concise findings, missing information and a verification checklist.";
        running = true;
        aiResult.setText("Requesting NVIDIA model " + model + " ...");
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("model", model);
                body.put("temperature", 0.2);
                body.put("max_tokens", 900);
                JSONArray messages = new JSONArray();
                messages.put(new JSONObject().put("role", "user").put("content", prompt));
                body.put("messages", messages);
                HttpURLConnection conn = (HttpURLConnection) new URL(NVIDIA_URL).openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(60000);
                conn.setRequestProperty("Authorization", "Bearer " + key);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                int status = conn.getResponseCode();
                if (status < 200 || status >= 300) throw new IllegalStateException("NVIDIA HTTP " + status);
                String result;
                try (InputStream in = conn.getInputStream()) { result = readStream(in); }
                conn.disconnect();
                JSONObject response = new JSONObject(result);
                String answer = response.getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").optString("content", "Model returned no text.");
                if (answer.length() > 12000) answer = answer.substring(0,12000);
                final String finalAnswer = answer;
                runOnUiThread(() -> aiResult.setText(finalAnswer));
            } catch (Exception e) {
                runOnUiThread(() -> aiResult.setText(
                    "Cloud analysis failed (" + e.getClass().getSimpleName() + "). " +
                    "Check your NVIDIA key, exact model ID, quota and connectivity. No recommendation generated."));
            } finally {
                running = false;
            }
        }).start();
    }

    private static String get(URL url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 FinResearchMobile/0.1");
        try {
            int status = conn.getResponseCode();
            if (status != 200) throw new IllegalStateException("Quote HTTP " + status);
            try (InputStream in = conn.getInputStream()) { return readStream(in); }
        } finally { conn.disconnect(); }
    }

    private static String readStream(InputStream input) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = input.read(buf)) != -1) {
            bytes.write(buf, 0, n);
            if (bytes.size() > 1_000_000) throw new IllegalStateException("Response too large");
        }
        return bytes.toString("UTF-8");
    }

    private SecretKey keystoreKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(KEYSTORE_ALIAS)) {
            return ((KeyStore.SecretKeyEntry)ks.getEntry(KEYSTORE_ALIAS, null)).getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build());
        return generator.generateKey();
    }
    private void encryptKey(String raw) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, keystoreKey());
        byte[] iv = c.getIV();
        byte[] encrypted = c.doFinal(raw.getBytes(StandardCharsets.UTF_8));
        prefs.edit().putString("key_iv", android.util.Base64.encodeToString(iv,android.util.Base64.NO_WRAP))
            .putString("key_blob", android.util.Base64.encodeToString(encrypted,android.util.Base64.NO_WRAP)).apply();
    }
    private String decryptKey() throws Exception {
        String iv = prefs.getString("key_iv", "");
        String blob = prefs.getString("key_blob", "");
        if (iv.isEmpty() || blob.isEmpty()) return "";
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, keystoreKey(), new GCMParameterSpec(128,android.util.Base64.decode(iv,android.util.Base64.NO_WRAP)));
        byte[] value = c.doFinal(android.util.Base64.decode(blob,android.util.Base64.NO_WRAP));
        return new String(value, StandardCharsets.UTF_8);
    }

    private LinearLayout panel(LinearLayout parent) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16),dp(14),dp(16),dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(PANEL);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1),Color.rgb(43,58,78));
        l.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin = dp(18);
        parent.addView(l,lp);
        return l;
    }
    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(sp);
        t.setPadding(0,dp(5),0,dp(6));
        if (bold) t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private EditText input(String hint, boolean multiline) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(SUBTEXT);
        input.setTextSize(14);
        input.setBackgroundTintList(ColorStateList.valueOf(ACCENT));
        input.setInputType(multiline
            ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            : InputType.TYPE_CLASS_TEXT);
        input.setSingleLine(!multiline);
        return input;
    }
    private Button button(String caption) {
        Button b = new Button(this);
        b.setText(caption);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(13);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(ACCENT);
        bg.setCornerRadius(dp(10));
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,dp(48));
        lp.topMargin = dp(12);
        b.setLayoutParams(lp);
        return b;
    }
    private int dp(int dp) { return Math.round(dp * getResources().getDisplayMetrics().density); }
}
