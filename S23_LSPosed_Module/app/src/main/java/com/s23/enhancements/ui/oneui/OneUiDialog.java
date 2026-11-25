package com.s23.enhancements.ui.oneui;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.s23.enhancements.R;

import java.util.ArrayList;
import java.util.List;

/**
 * One UI styled dialogs: rounded card, single-choice list with radio indicators,
 * multi-choice switch list, slider dialog and centred accent action labels.
 */
public final class OneUiDialog {

    public interface OnOptionPicked {
        void onPicked(int index);
    }

    public interface OnInputConfirmed {
        void onConfirmed(String value);
    }

    public interface OnMultiChoiceConfirmed {
        void onConfirmed(boolean[] checked);
    }

    public interface SliderFormatter {
        String format(int value);
    }

    public interface OnSliderConfirmed {
        void onConfirmed(int value);
    }

    private OneUiDialog() {
    }

    public static Dialog showSingleChoice(Context context, CharSequence title,
                                          CharSequence[] options, int selectedIndex,
                                          OnOptionPicked callback) {
        return showSingleChoice(context, title, options, selectedIndex, callback, null, null);
    }

    public static Dialog showSingleChoice(Context context, CharSequence title,
                                          CharSequence[] options, int selectedIndex,
                                          OnOptionPicked callback,
                                          CharSequence testLabel,
                                          OnOptionPicked onTest) {
        LinearLayout card = createCard(context);

        card.addView(createTitle(context, title));

        ScrollView scroll = new ScrollView(context);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        scroll.setLayoutParams(scrollParams);
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);

        List<OneUiRadioDot> dots = new ArrayList<>();
        Dialog[] holder = new Dialog[1];
        final int[] currentSelected = {selectedIndex};

        for (int i = 0; i < options.length; i++) {
            final int index = i;
            OneUiRadioDot dot = new OneUiRadioDot(context);
            dot.setSelected(i == selectedIndex);
            dots.add(dot);

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(R.drawable.oneui_row_ripple);
            int padH = OneUiTheme.dp(context, 10);
            int padV = OneUiTheme.dp(context, 13);
            row.setPadding(padH, padV, padH, padV);

            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            dotParams.setMarginEnd(OneUiTheme.dp(context, 14));
            dot.setLayoutParams(dotParams);

            TextView label = new TextView(context);
            label.setText(options[i]);
            label.setTextColor(OneUiTheme.TEXT_PRIMARY);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
            label.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            row.addView(dot);
            row.addView(label);
            row.setOnClickListener(v -> {
                currentSelected[0] = index;
                for (int k = 0; k < dots.size(); k++) {
                    dots.get(k).setSelected(k == index);
                }
                if (holder[0] != null) {
                    holder[0].dismiss();
                }
                if (callback != null) {
                    callback.onPicked(index);
                }
            });
            list.addView(row);
        }

        card.addView(scroll);
        if (testLabel != null && onTest != null) {
            TextView testBtn = createAction(context, testLabel, OneUiTheme.ACCENT);
            testBtn.setOnClickListener(v -> onTest.onPicked(currentSelected[0]));
            card.addView(testBtn);
        }
        card.addView(createCancel(context, holder));

        Dialog dialog = buildDialog(context, card, options.length > 7);
        holder[0] = dialog;
        dialog.show();
        return dialog;
    }

    public static Dialog showMultiChoice(Context context, CharSequence title,
                                         CharSequence[] options, boolean[] initialChecked,
                                         CharSequence positiveLabel,
                                         OnMultiChoiceConfirmed callback) {
        LinearLayout card = createCard(context);
        card.addView(createTitle(context, title));

        ScrollView scroll = new ScrollView(context);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);

        boolean[] state = new boolean[options.length];
        System.arraycopy(initialChecked, 0, state, 0, Math.min(initialChecked.length, state.length));

        for (int i = 0; i < options.length; i++) {
            final int index = i;
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(R.drawable.oneui_row_ripple);
            int padH = OneUiTheme.dp(context, 10);
            int padV = OneUiTheme.dp(context, 11);
            row.setPadding(padH, padV, padH, padV);

            TextView label = new TextView(context);
            label.setText(options[i]);
            label.setTextColor(OneUiTheme.TEXT_PRIMARY);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
            label.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            OneUiSwitch sw = new OneUiSwitch(context);
            sw.setCheckedSilently(state[i]);
            LinearLayout.LayoutParams swParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            swParams.setMarginStart(OneUiTheme.dp(context, 12));
            sw.setLayoutParams(swParams);
            sw.setOnCheckedChangeListener((b, isChecked) -> state[index] = isChecked);

            row.addView(label);
            row.addView(sw);
            row.setOnClickListener(v -> sw.toggle());
            list.addView(row);
        }

        card.addView(scroll);
        Dialog[] holder = new Dialog[1];

        TextView positive = createAction(context, positiveLabel, OneUiTheme.ACCENT);
        positive.setOnClickListener(v -> {
            if (holder[0] != null) holder[0].dismiss();
            if (callback != null) callback.onConfirmed(state);
        });
        card.addView(positive);
        card.addView(createCancel(context, holder));

        Dialog dialog = buildDialog(context, card, options.length > 7);
        holder[0] = dialog;
        dialog.show();
        return dialog;
    }

    public static Dialog showSlider(Context context, CharSequence title, CharSequence subtitle,
                                    int min, int max, int current, SliderFormatter formatter,
                                    CharSequence resetLabel, int resetValue,
                                    CharSequence positiveLabel, OnSliderConfirmed callback) {
        LinearLayout card = createCard(context);
        card.addView(createTitle(context, title));

        int padH = OneUiTheme.dp(context, 10);
        if (subtitle != null) {
            TextView sub = new TextView(context);
            sub.setText(subtitle);
            sub.setTextColor(OneUiTheme.TEXT_SECONDARY);
            sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
            sub.setPadding(padH, 0, padH, OneUiTheme.dp(context, 12));
            card.addView(sub);
        }

        final int[] valueHolder = {Math.max(min, Math.min(max, current))};

        TextView valueLabel = new TextView(context);
        valueLabel.setText(formatter.format(valueHolder[0]));
        valueLabel.setTextColor(OneUiTheme.ACCENT);
        valueLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        valueLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        valueLabel.setGravity(Gravity.CENTER);
        valueLabel.setPadding(padH, OneUiTheme.dp(context, 4), padH, OneUiTheme.dp(context, 8));
        card.addView(valueLabel);

        SeekBar seekBar = new SeekBar(context);
        seekBar.setMax(max - min);
        seekBar.setProgress(valueHolder[0] - min);
        seekBar.setProgressTintList(ColorStateList.valueOf(OneUiTheme.ACCENT));
        seekBar.setThumbTintList(ColorStateList.valueOf(OneUiTheme.ACCENT));
        seekBar.setPadding(padH, OneUiTheme.dp(context, 8), padH, OneUiTheme.dp(context, 14));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                valueHolder[0] = min + progress;
                valueLabel.setText(formatter.format(valueHolder[0]));
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
            }
        });
        card.addView(seekBar);

        Dialog[] holder = new Dialog[1];

        TextView positive = createAction(context, positiveLabel, OneUiTheme.ACCENT);
        positive.setOnClickListener(v -> {
            if (holder[0] != null) holder[0].dismiss();
            if (callback != null) callback.onConfirmed(valueHolder[0]);
        });
        card.addView(positive);

        if (resetLabel != null) {
            TextView reset = createAction(context, resetLabel, OneUiTheme.TEXT_PRIMARY);
            reset.setOnClickListener(v -> {
                if (holder[0] != null) holder[0].dismiss();
                if (callback != null) callback.onConfirmed(resetValue);
            });
            card.addView(reset);
        }

        card.addView(createCancel(context, holder));

        Dialog dialog = buildDialog(context, card, false);
        holder[0] = dialog;
        dialog.show();
        return dialog;
    }

    public static Dialog showInput(Context context, CharSequence title, CharSequence hint,
                                   String initialValue, CharSequence positiveLabel,
                                   OnInputConfirmed callback) {
        LinearLayout card = createCard(context);
        card.addView(createTitle(context, title));

        EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setTextColor(OneUiTheme.TEXT_PRIMARY);
        input.setHintTextColor(OneUiTheme.TEXT_SECONDARY);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        input.setHint(hint);
        if (initialValue != null) {
            input.setText(initialValue);
            input.setSelection(initialValue.length());
        }
        input.setBackgroundResource(R.drawable.oneui_input_bg);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        inputParams.topMargin = OneUiTheme.dp(context, 12);
        inputParams.bottomMargin = OneUiTheme.dp(context, 8);
        input.setLayoutParams(inputParams);
        card.addView(input);

        Dialog[] holder = new Dialog[1];

        TextView positive = createAction(context, positiveLabel, OneUiTheme.ACCENT);
        positive.setOnClickListener(v -> {
            String value = input.getText().toString().trim();
            if (holder[0] != null) {
                holder[0].dismiss();
            }
            if (callback != null) {
                callback.onConfirmed(value);
            }
        });
        card.addView(positive);
        card.addView(createCancel(context, holder));

        Dialog dialog = buildDialog(context, card, false);
        holder[0] = dialog;
        dialog.show();

        input.requestFocus();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }
        return dialog;
    }

    public static Dialog showConfirm(Context context, CharSequence title, CharSequence message,
                                     CharSequence positiveLabel, Runnable onConfirm) {
        LinearLayout card = createCard(context);
        card.addView(createTitle(context, title));

        boolean longMsg = message != null && message.length() > 260;
        if (message != null) {
            TextView msg = new TextView(context);
            msg.setText(message);
            msg.setTextColor(OneUiTheme.TEXT_SECONDARY);
            msg.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
            msg.setLineSpacing(OneUiTheme.dp(context, 2), 1.15f);
            msg.setPadding(OneUiTheme.dp(context, 10), OneUiTheme.dp(context, 2),
                    OneUiTheme.dp(context, 10), OneUiTheme.dp(context, 14));

            ScrollView scroll = new ScrollView(context);
            scroll.setLayoutParams(longMsg
                    ? new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
                    : new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT));
            scroll.addView(msg);
            card.addView(scroll);
        }

        Dialog[] holder = new Dialog[1];

        TextView positive = createAction(context, positiveLabel, OneUiTheme.ACCENT);
        positive.setOnClickListener(v -> {
            if (holder[0] != null) holder[0].dismiss();
            if (onConfirm != null) onConfirm.run();
        });
        card.addView(positive);
        card.addView(createCancel(context, holder));

        Dialog dialog = buildDialog(context, card, longMsg);
        holder[0] = dialog;
        dialog.show();
        return dialog;
    }

    private static LinearLayout createCard(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.oneui_dialog_bg);
        int pad = OneUiTheme.dp(context, 22);
        card.setPadding(pad, OneUiTheme.dp(context, 20), pad, OneUiTheme.dp(context, 8));
        return card;
    }

    private static TextView createTitle(Context context, CharSequence title) {
        TextView view = new TextView(context);
        view.setText(title);
        view.setTextColor(OneUiTheme.TEXT_PRIMARY);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setPadding(OneUiTheme.dp(context, 10), 0, OneUiTheme.dp(context, 10),
                OneUiTheme.dp(context, 10));
        return view;
    }

    private static TextView createAction(Context context, CharSequence label, int color) {
        TextView view = new TextView(context);
        view.setText(label);
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setGravity(Gravity.CENTER);
        view.setPadding(0, OneUiTheme.dp(context, 14), 0, OneUiTheme.dp(context, 14));
        view.setBackgroundResource(R.drawable.oneui_row_ripple);
        view.setClickable(true);
        return view;
    }

    private static TextView createCancel(Context context, Dialog[] holder) {
        TextView cancel = createAction(context, context.getString(android.R.string.cancel),
                OneUiTheme.TEXT_SECONDARY);
        cancel.setOnClickListener(v -> {
            if (holder[0] != null) {
                holder[0].dismiss();
            }
        });
        return cancel;
    }

    private static Dialog buildDialog(Context context, View content, boolean capHeight) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(content);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            params.dimAmount = 0.6f;
            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
            int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
            params.width = screenWidth - OneUiTheme.dp(context, 48);
            params.height = capHeight
                    ? Math.round(screenHeight * 0.72f)
                    : WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(params);
        }
        return dialog;
    }
}
