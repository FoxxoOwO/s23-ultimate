package com.s23.enhancements.ui.oneui;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SeslSeekBar;

/**
 * Dialog builders backed by SESL9 (One UI 9) AppCompat AlertDialog and SeslSeekBar.
 */
public final class OneUiDialog {

    public interface OnOptionPicked {
        void onPicked(int index);
    }

    public interface OnTextConfirmed {
        void onConfirmed(String value);
    }

    public interface OnMultiChoiceConfirmed {
        void onConfirmed(boolean[] checked);
    }

    public interface SliderFormatter {
        CharSequence format(int value);
    }

    public interface OnSliderConfirmed {
        void onConfirmed(int value);
    }

    private OneUiDialog() {
    }

    public static Dialog showSingleChoice(Context context, CharSequence title,
                                          CharSequence[] options, int selectedIndex,
                                          OnOptionPicked listener) {
        return showSingleChoice(context, title, options, selectedIndex, listener, null, null);
    }

    public static Dialog showSingleChoice(Context context, CharSequence title,
                                          CharSequence[] options, int selectedIndex,
                                          OnOptionPicked listener,
                                          CharSequence testLabel, OnOptionPicked testListener) {
        final int[] selected = {Math.max(0, Math.min(options.length - 1, selectedIndex))};
        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(title)
                .setNegativeButton(android.R.string.cancel, null);

        if (testLabel != null && testListener != null) {
            builder.setSingleChoiceItems(options, selected[0], (d, which) -> selected[0] = which);
            builder.setPositiveButton(android.R.string.ok, (d, w) -> {
                if (listener != null) {
                    listener.onPicked(selected[0]);
                }
            });
            builder.setNeutralButton(testLabel, null);
            AlertDialog dialog = builder.create();
            dialog.show();
            android.widget.Button neutral = dialog.getButton(DialogInterface.BUTTON_NEUTRAL);
            if (neutral != null) {
                neutral.setOnClickListener(v -> testListener.onPicked(selected[0]));
            }
            return dialog;
        }

        builder.setSingleChoiceItems(options, selected[0], (d, which) -> {
            d.dismiss();
            if (listener != null) {
                listener.onPicked(which);
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
        return dialog;
    }

    public static Dialog showMultiChoice(Context context, CharSequence title,
                                         CharSequence[] options, boolean[] initialChecked,
                                         CharSequence positiveLabel,
                                         OnMultiChoiceConfirmed callback) {
        boolean[] state = new boolean[options.length];
        if (initialChecked != null) {
            System.arraycopy(initialChecked, 0, state, 0, Math.min(initialChecked.length, state.length));
        }
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(title)
                .setMultiChoiceItems(options, state, (d, which, isChecked) -> state[which] = isChecked)
                .setPositiveButton(positiveLabel, (d, w) -> {
                    if (callback != null) {
                        callback.onConfirmed(state);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.show();
        return dialog;
    }

    public static Dialog showSlider(Context context, CharSequence title, CharSequence subtitle,
                                    int min, int max, int current, SliderFormatter formatter,
                                    CharSequence resetLabel, int resetValue,
                                    CharSequence positiveLabel, OnSliderConfirmed callback) {
        int padH = dp(context, 24);
        int padV = dp(context, 8);
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padH, padV, padH, padV);

        if (subtitle != null) {
            TextView sub = new TextView(context);
            sub.setText(subtitle);
            sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            sub.setPadding(0, 0, 0, dp(context, 12));
            container.addView(sub);
        }

        final int[] valueHolder = {Math.max(min, Math.min(max, current))};

        TextView valueLabel = new TextView(context);
        valueLabel.setText(formatter.format(valueHolder[0]));
        valueLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        valueLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        valueLabel.setGravity(Gravity.CENTER);
        valueLabel.setPadding(0, dp(context, 4), 0, dp(context, 10));
        container.addView(valueLabel);

        SeslSeekBar seekBar = new SeslSeekBar(context);
        seekBar.setMax(max - min);
        seekBar.setProgress(valueHolder[0] - min);
        seekBar.setPadding(0, dp(context, 6), 0, dp(context, 6));
        seekBar.setOnSeekBarChangeListener(new SeslSeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeslSeekBar sb, int progress, boolean fromUser) {
                valueHolder[0] = min + progress;
                valueLabel.setText(formatter.format(valueHolder[0]));
            }

            @Override
            public void onStartTrackingTouch(SeslSeekBar sb) {
            }

            @Override
            public void onStopTrackingTouch(SeslSeekBar sb) {
            }
        });
        container.addView(seekBar);

        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(container)
                .setPositiveButton(positiveLabel, (d, w) -> {
                    if (callback != null) {
                        callback.onConfirmed(valueHolder[0]);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null);

        if (resetLabel != null) {
            builder.setNeutralButton(resetLabel, (d, w) -> {
                if (callback != null) {
                    callback.onConfirmed(resetValue);
                }
            });
        }

        AlertDialog dialog = builder.create();
        dialog.show();
        return dialog;
    }

    public static Dialog showInput(Context context, CharSequence title, CharSequence hint,
                                   CharSequence initialValue, CharSequence positiveLabel,
                                   OnTextConfirmed callback) {
        int padH = dp(context, 24);
        int padV = dp(context, 8);
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padH, padV, padH, padV);

        EditText input = new EditText(context);
        input.setHint(hint);
        if (initialValue != null) {
            input.setText(initialValue);
            input.setSelection(initialValue.length());
        }
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true);
        container.addView(input);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(container)
                .setPositiveButton(positiveLabel, (d, w) -> {
                    if (callback != null) {
                        callback.onConfirmed(input.getText().toString().trim());
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
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
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(positiveLabel, (d, w) -> {
                    if (onConfirm != null) {
                        onConfirm.run();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.show();
        return dialog;
    }

    private static int dp(Context ctx, int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, ctx.getResources().getDisplayMetrics()));
    }
}
