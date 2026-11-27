package com.r4xn.jejak.ui;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.time.LocalDate;
import java.util.function.Consumer;

/** Small building blocks used by every screen, so they all look alike. */
public final class Ui {
    public static final int BRAND = Color.parseColor("#1D4ED8");
    public static final int BRAND_DARK = Color.parseColor("#1E3A8A");
    public static final int BRAND_SOFT = Color.parseColor("#E0E7FF");
    public static final int ACCENT = Color.parseColor("#F97316");
    public static final int INK = Color.parseColor("#0F172A");
    public static final int MUTED = Color.parseColor("#64748B");
    public static final int LINE = Color.parseColor("#E2E8F0");
    public static final int PAGE = Color.parseColor("#F8FAFC");
    public static final int GOOD = Color.parseColor("#16A34A");
    public static final int GOOD_SOFT = Color.parseColor("#DCFCE7");
    public static final int BAD = Color.parseColor("#DC2626");
    public static final int BAD_SOFT = Color.parseColor("#FEE2E2");
    public static final int WARN = Color.parseColor("#B45309");
    public static final int WARN_SOFT = Color.parseColor("#FEF3C7");
    public static final int GREY_SOFT = Color.parseColor("#F1F5F9");

    private Ui() {
    }

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    /** Pads a screen so nothing hides under the status bar, the navigation bar or the keyboard. */
    public static void fitSystemBars(View root) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    // ------------------------------------------------------------------ text

    public static TextView text(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(bold ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : Typeface.DEFAULT);
        return t;
    }

    public static TextView title(Context c, CharSequence s) {
        TextView t = text(c, s, 22, INK, true);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        return t;
    }

    public static TextView heading(Context c, CharSequence s) {
        return text(c, s, 17, INK, true);
    }

    public static TextView body(Context c, CharSequence s) {
        return text(c, s, 15, INK, false);
    }

    public static TextView muted(Context c, CharSequence s) {
        return text(c, s, 13, MUTED, false);
    }

    /** Small grey capitals above a group of things. */
    public static TextView section(Context c, CharSequence s) {
        TextView t = text(c, s.toString().toUpperCase(java.util.Locale.ROOT), 12, MUTED, true);
        t.setLetterSpacing(0.06f);
        t.setPadding(dp(c, 4), dp(c, 18), 0, dp(c, 8));
        return t;
    }

    /** Coloured rounded label ("Recording", "Sample"…). */
    public static TextView pill(Context c, CharSequence s, int fg, int bg) {
        TextView t = text(c, s, 12, fg, true);
        t.setPadding(dp(c, 10), dp(c, 3), dp(c, 10), dp(c, 3));
        t.setBackground(round(bg, dp(c, 99)));
        return t;
    }

    public static GradientDrawable round(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    /** Round badge with initials, in a colour. */
    public static TextView avatar(Context c, String name, int color, int sizeDp) {
        TextView t = text(c, initials(name), sizeDp * 0.38f, Color.WHITE, true);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        t.setBackground(g);
        int px = dp(c, sizeDp);
        t.setLayoutParams(new LinearLayout.LayoutParams(px, px));
        return t;
    }

    public static String initials(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] parts = name.trim().split("\\s+");
        String a = parts[0].substring(0, 1);
        String b = parts.length > 1 && Character.isLetter(parts[parts.length - 1].charAt(0)) ? parts[parts.length - 1].substring(0, 1) : "";
        return (a + b).toUpperCase(java.util.Locale.ROOT);
    }

    public static int color(String hex, int fallback) {
        try {
            return Color.parseColor(hex);
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    // ------------------------------------------------------------------ layout

    public static LinearLayout vbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout hbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams match() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
    }

    public static View gap(Context c, int dp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, dp), dp(c, dp)));
        return v;
    }

    public static View grow(Context c) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        return v;
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(LINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 1) / 2 + 1));
        v.setLayoutParams(lp);
        return v;
    }

    /** White rounded card with a thin outline; returns the inner column to put things in. */
    public static MaterialCardView card(Context c, LinearLayout inner) {
        MaterialCardView card = new MaterialCardView(c);
        card.setRadius(dp(c, 16));
        card.setCardElevation(0);
        card.setStrokeColor(LINE);
        card.setStrokeWidth(dp(c, 1));
        card.setCardBackgroundColor(Color.WHITE);
        inner.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        card.addView(inner);
        LinearLayout.LayoutParams lp = match();
        lp.bottomMargin = dp(c, 10);
        card.setLayoutParams(lp);
        return card;
    }

    /** One tappable line: optional leading view, title + subtitle, optional trailing view. */
    public static LinearLayout row(Context c, View lead, CharSequence title, CharSequence sub, View trail, Runnable onClick) {
        LinearLayout r = hbox(c);
        r.setPadding(dp(c, 4), dp(c, 10), dp(c, 4), dp(c, 10));
        if (lead != null) {
            r.addView(lead);
            r.addView(gap(c, 12));
        }
        LinearLayout texts = vbox(c);
        TextView t = body(c, title);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        texts.addView(t);
        if (sub != null && sub.length() > 0) texts.addView(muted(c, sub));
        r.addView(texts, weight(1));
        if (trail != null) r.addView(trail);
        if (onClick != null) {
            TypedValue tv = new TypedValue();
            c.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
            r.setBackgroundResource(tv.resourceId);
            r.setOnClickListener(v -> onClick.run());
        }
        return r;
    }

    /** A label on the left and an amount on the right. */
    public static LinearLayout pair(Context c, CharSequence label, CharSequence value, boolean bold) {
        LinearLayout r = hbox(c);
        r.setPadding(0, dp(c, 7), 0, dp(c, 7));
        TextView l = text(c, label, bold ? 17 : 15, bold ? INK : INK, bold);
        r.addView(l, weight(1));
        TextView v = text(c, value, bold ? 17 : 15, INK, bold);
        v.setGravity(Gravity.END);
        r.addView(v);
        return r;
    }

    // ------------------------------------------------------------------ buttons and inputs

    public static MaterialButton button(Context c, String text, String icon, Runnable onClick) {
        MaterialButton b = new MaterialButton(c);
        b.setText(text);
        b.setAllCaps(false);
        b.setCornerRadius(dp(c, 12));
        if (icon != null) {
            b.setIcon(Icons.of(icon, Color.WHITE, dp(c, 18)));
            b.setIconTint(ColorStateList.valueOf(Color.WHITE));
        }
        if (onClick != null) b.setOnClickListener(v -> onClick.run());
        return b;
    }

    public static MaterialButton outlined(Context c, String text, String icon, Runnable onClick) {
        MaterialButton b = new MaterialButton(c, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        b.setText(text);
        b.setAllCaps(false);
        b.setCornerRadius(dp(c, 12));
        if (icon != null) {
            b.setIcon(Icons.of(icon, BRAND, dp(c, 18)));
            b.setIconTint(ColorStateList.valueOf(BRAND));
        }
        if (onClick != null) b.setOnClickListener(v -> onClick.run());
        return b;
    }

    public static MaterialButton tonal(Context c, String text, String icon, int fg, int bg, Runnable onClick) {
        MaterialButton b = new MaterialButton(c);
        b.setText(text);
        b.setAllCaps(false);
        b.setCornerRadius(dp(c, 12));
        b.setBackgroundTintList(ColorStateList.valueOf(bg));
        b.setTextColor(fg);
        if (icon != null) {
            b.setIcon(Icons.of(icon, fg, dp(c, 18)));
            b.setIconTint(ColorStateList.valueOf(fg));
        }
        if (onClick != null) b.setOnClickListener(v -> onClick.run());
        return b;
    }

    /** Round icon button. */
    public static MaterialButton iconButton(Context c, String icon, int color, String description, Runnable onClick) {
        MaterialButton b = new MaterialButton(c, null, com.google.android.material.R.attr.materialIconButtonStyle);
        b.setIcon(Icons.of(icon, color, dp(c, 22)));
        b.setIconTint(ColorStateList.valueOf(color));
        b.setContentDescription(description);
        b.setOnClickListener(v -> onClick.run());
        return b;
    }

    public static Chip chip(Context c, String text, boolean checkable) {
        Chip ch = new Chip(c);
        ch.setText(text);
        ch.setCheckable(checkable);
        ch.setCheckedIconVisible(checkable);
        ch.setEnsureMinTouchTargetSize(false);
        return ch;
    }

    /** Outlined text box with a floating label. Returns the box; the text field is box.getEditText(). */
    public static TextInputLayout input(Context c, String label, String value, int inputType) {
        TextInputLayout til = new TextInputLayout(c, null, com.google.android.material.R.attr.textInputOutlinedStyle);
        til.setHint(label);
        til.setBoxCornerRadii(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 12));
        TextInputEditText e = new TextInputEditText(til.getContext());
        e.setInputType(inputType);
        if (value != null) e.setText(value);
        if ((inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0) {
            e.setMinLines(2);
            e.setGravity(Gravity.TOP | Gravity.START);
        } else {
            e.setSingleLine(true);
        }
        til.addView(e);
        LinearLayout.LayoutParams lp = match();
        lp.bottomMargin = dp(c, 10);
        til.setLayoutParams(lp);
        return til;
    }

    public static String value(TextInputLayout til) {
        return til.getEditText() == null || til.getEditText().getText() == null ? "" : til.getEditText().getText().toString().trim();
    }

    // ------------------------------------------------------------------ actions

    public static void toast(Context c, String text) {
        Toast.makeText(c, text, Toast.LENGTH_SHORT).show();
    }

    public static void confirm(Context c, String title, String message, String ok, Runnable onOk) {
        new MaterialAlertDialogBuilder(c)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(ok, (d, w) -> onOk.run())
                .show();
    }

    public static void shareText(Context c, String subject, String text) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, subject);
        i.putExtra(Intent.EXTRA_TEXT, text);
        c.startActivity(Intent.createChooser(i, "Share"));
    }

    public static void shareFile(Context c, File f, String mime, String subject) {
        Uri uri = FileProvider.getUriForFile(c, c.getPackageName() + ".files", f);
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType(mime);
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.putExtra(Intent.EXTRA_SUBJECT, subject);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        c.startActivity(Intent.createChooser(i, "Share"));
    }

    public static void pickDate(Context c, LocalDate start, Consumer<LocalDate> picked) {
        new DatePickerDialog(c, (v, y, m, d) -> picked.accept(LocalDate.of(y, m + 1, d)),
                start.getYear(), start.getMonthValue() - 1, start.getDayOfMonth()).show();
    }

    public static void pickTime(Context c, String hhmm, Consumer<String> picked) {
        int h = 16, m = 0;
        if (hhmm != null && hhmm.matches("\\d{1,2}:\\d{2}")) {
            h = Integer.parseInt(hhmm.split(":")[0]);
            m = Integer.parseInt(hhmm.split(":")[1]);
        }
        new TimePickerDialog(c, (v, hh, mm) -> picked.accept(String.format(java.util.Locale.US, "%02d:%02d", hh, mm)), h, m, false).show();
    }

    /** Big friendly message for empty lists. */
    public static View empty(Context c, String icon, String title, String message) {
        LinearLayout v = vbox(c);
        v.setGravity(Gravity.CENTER_HORIZONTAL);
        v.setPadding(dp(c, 24), dp(c, 40), dp(c, 24), dp(c, 40));
        FrameLayout circle = new FrameLayout(c);
        circle.setBackground(round(BRAND_SOFT, dp(c, 99)));
        android.widget.ImageView iv = new android.widget.ImageView(c);
        iv.setImageDrawable(Icons.of(icon, BRAND, dp(c, 30)));
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(dp(c, 30), dp(c, 30), Gravity.CENTER);
        circle.addView(iv, ip);
        v.addView(circle, new LinearLayout.LayoutParams(dp(c, 64), dp(c, 64)));
        TextView t = heading(c, title);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(c, 14), 0, dp(c, 4));
        v.addView(t);
        TextView m = muted(c, message);
        m.setGravity(Gravity.CENTER);
        v.addView(m);
        return v;
    }

    public static Activity activity(Context c) {
        return c instanceof Activity a ? a : null;
    }
}
