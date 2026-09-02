package com.bas080.autosleepdroid;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

public class LogActivity extends Activity implements EventLogger.Listener {
    private ScrollView scrollView;
    private TextView eventLogText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log);

        scrollView = findViewById(R.id.event_scroll_view);
        eventLogText = findViewById(R.id.event_log_text);
    }

    @Override
    protected void onResume() {
        super.onResume();
        EventLogger.setListener(this);
        refreshEventLog();
    }

    @Override
    protected void onPause() {
        super.onPause();
        EventLogger.setListener(null);
    }

    private void refreshEventLog() {
        List<String> events = EventLogger.getEvents(this);
        android.text.SpannableStringBuilder ssb = new android.text.SpannableStringBuilder();
        for (String event : events) {
            ssb.append(EventLogger.formatColoredEvent(this, event)).append("\n");
        }
        if (eventLogText != null) {
            eventLogText.setText(ssb);
            scrollToBottom();
        }
    }

    @Override
    public void onEventLogged(String event) {
        if (eventLogText != null) {
            eventLogText.append(EventLogger.formatColoredEvent(this, event));
            eventLogText.append("\n");
            scrollToBottom();
        }
    }

    private void scrollToBottom() {
        if (scrollView != null) {
            scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
        }
    }
}
