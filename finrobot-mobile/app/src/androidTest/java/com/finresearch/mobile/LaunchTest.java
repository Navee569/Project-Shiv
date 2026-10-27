package com.finresearch.mobile;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LaunchTest {
    private static View findLabel(View v, String content) {
        if (v instanceof TextView) {
            CharSequence text = ((TextView) v).getText();
            if (text != null && text.toString().contains(content)) return v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) v;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findLabel(group.getChildAt(i), content);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static EditText findInputByHint(View v, String content) {
        if (v instanceof EditText) {
            CharSequence hint = ((EditText) v).getHint();
            if (hint != null && hint.toString().contains(content)) return (EditText) v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) v;
            for (int i = 0; i < group.getChildCount(); i++) {
                EditText found = findInputByHint(group.getChildAt(i), content);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test public void launchAndOfflineResearchWorkWithoutApiKeys() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Intent intent = new Intent(instrumentation.getTargetContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Activity activity = instrumentation.startActivitySync(intent);
        assertNotNull("Activity launch returned null", activity);
        instrumentation.waitForIdleSync();

        View root = activity.getWindow().getDecorView();
        assertEquals("com.finresearch.mobile", activity.getPackageName());
        assertNotNull("Title missing", findLabel(root, "FinResearch Mobile"));
        assertNotNull("Market data section missing", findLabel(root, "MARKET DATA"));
        assertNotNull("Sentiment section missing", findLabel(root, "NEWS & SENTIMENT"));
        assertNotNull("DCF section missing", findLabel(root, "OFFLINE DCF VALUATION"));
        assertNotNull("AI section missing", findLabel(root, "SIX-MODEL AI RESEARCH"));

        final EditText headlines = findInputByHint(root, "Paste headline(s)");
        final View sentimentButton = findLabel(root, "SCORE PASTED HEADLINES");
        assertNotNull("Headline input missing", headlines);
        assertTrue("Sentiment button missing", sentimentButton instanceof Button);
        instrumentation.runOnMainSync(() -> {
            headlines.setText("Company profits surge");
            sentimentButton.performClick();
        });
        instrumentation.waitForIdleSync();
        assertNotNull("Sentiment output missing", findLabel(root, "Keyword sentiment: Positive"));

        final View dcfButton = findLabel(root, "COMPUTE ILLUSTRATIVE DCF");
        assertTrue("DCF button missing", dcfButton instanceof Button);
        instrumentation.runOnMainSync(dcfButton::performClick);
        instrumentation.waitForIdleSync();
        assertNotNull("DCF result missing", findLabel(root, "Indicative DCF value per share:"));

        instrumentation.runOnMainSync(activity::finish);
    }
}
