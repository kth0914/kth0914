package tw.kth.teacherwords;

import android.app.Activity;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private WebView webView;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean paused = false;
    private boolean stopRequested = false;
    private float speechRate = 1.0f;
    private final ArrayList<String> chunks = new ArrayList<>();
    private int chunkIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);
        s.setDefaultTextEncodingName("UTF-8");

        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new TtsBridge(), "AndroidTTS");

        tts = new TextToSpeech(getApplicationContext(), this);
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) {
            ttsReady = false;
            notifyJs("unavailable");
            return;
        }

        int result = tts.setLanguage(new Locale("zh", "TW"));
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            result = tts.setLanguage(Locale.TRADITIONAL_CHINESE);
        }

        ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                   result != TextToSpeech.LANG_NOT_SUPPORTED;
        tts.setSpeechRate(speechRate);

        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                notifyJs("speaking");
            }

            @Override
            public void onDone(String utteranceId) {
                runOnUiThread(() -> {
                    if (paused || stopRequested) return;
                    chunkIndex++;
                    if (chunkIndex < chunks.size()) {
                        speakCurrentChunk();
                    } else {
                        chunks.clear();
                        chunkIndex = 0;
                        notifyJs("done");
                    }
                });
            }

            @Override
            public void onError(String utteranceId) {
                notifyJs("error");
            }
        });

        notifyJs(ttsReady ? "ready" : "unavailable");
    }

    private void startSpeech(String text) {
        if (!ttsReady || text == null || text.trim().isEmpty()) {
            notifyJs("unavailable");
            return;
        }
        stopRequested = false;
        paused = false;
        tts.stop();
        chunks.clear();
        chunks.addAll(splitText(text));
        chunkIndex = 0;
        speakCurrentChunk();
    }

    private void speakCurrentChunk() {
        if (!ttsReady || paused || stopRequested || chunkIndex >= chunks.size()) return;
        tts.setSpeechRate(speechRate);
        String id = "teacher_words_" + chunkIndex + "_" + System.currentTimeMillis();
        int result = tts.speak(chunks.get(chunkIndex), TextToSpeech.QUEUE_FLUSH, null, id);
        if (result == TextToSpeech.ERROR) notifyJs("error");
    }

    private ArrayList<String> splitText(String text) {
        ArrayList<String> result = new ArrayList<>();
        String cleaned = text.replace("\r", "").replaceAll("[ \\t]+", " ").trim();
        String[] parts = cleaned.split("(?<=[。！？!?；;\\n])");
        StringBuilder current = new StringBuilder();
        final int MAX = 1400;

        for (String part : parts) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            if (p.length() > MAX) {
                if (current.length() > 0) {
                    result.add(current.toString());
                    current.setLength(0);
                }
                for (int i = 0; i < p.length(); i += MAX) {
                    result.add(p.substring(i, Math.min(i + MAX, p.length())));
                }
            } else if (current.length() + p.length() + 1 > MAX) {
                result.add(current.toString());
                current.setLength(0);
                current.append(p);
            } else {
                if (current.length() > 0) current.append("\n");
                current.append(p);
            }
        }

        if (current.length() > 0) result.add(current.toString());
        if (result.isEmpty() && !cleaned.isEmpty()) result.add(cleaned);
        return result;
    }

    private void pauseSpeech() {
        if (!ttsReady || chunks.isEmpty()) return;
        paused = true;
        tts.stop();
        notifyJs("paused");
    }

    private void resumeSpeech() {
        if (!ttsReady || chunks.isEmpty() || !paused) return;
        paused = false;
        stopRequested = false;
        speakCurrentChunk();
    }

    private void stopSpeech() {
        stopRequested = true;
        paused = false;
        if (tts != null) tts.stop();
        chunks.clear();
        chunkIndex = 0;
        notifyJs("idle");
    }

    private void setRate(double rate) {
        speechRate = (float) Math.max(0.5, Math.min(2.0, rate));
        if (tts != null) tts.setSpeechRate(speechRate);
    }

    private void notifyJs(String state) {
        if (webView == null) return;
        final String js = "if(window.TW&&TW.onTTSState){TW.onTTSState('" + state + "');}";
        runOnUiThread(() -> webView.evaluateJavascript(js, null));
    }

    public class TtsBridge {
        @JavascriptInterface
        public void speak(String text) {
            runOnUiThread(() -> startSpeech(text));
        }

        @JavascriptInterface
        public void pause() {
            runOnUiThread(() -> pauseSpeech());
        }

        @JavascriptInterface
        public void resume() {
            runOnUiThread(() -> resumeSpeech());
        }

        @JavascriptInterface
        public void stop() {
            runOnUiThread(() -> stopSpeech());
        }

        @JavascriptInterface
        public void setRate(double rate) {
            runOnUiThread(() -> MainActivity.this.setRate(rate));
        }

        @JavascriptInterface
        public boolean isReady() {
            return ttsReady;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null) {
            webView.evaluateJavascript("if(window.TW&&TW.ttsStop){TW.ttsStop();}", null);
        }
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        stopSpeech();
        if (tts != null) {
            tts.shutdown();
            tts = null;
        }
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidTTS");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
