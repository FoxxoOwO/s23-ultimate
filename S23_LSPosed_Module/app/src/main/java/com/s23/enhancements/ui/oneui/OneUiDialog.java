package com.s23.enhancements.ui.oneui;

import android.app.Dialog;
import android.content.Context;
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
import android.widget.TextView;

import com.s23.enhancements.R;

import java.util.ArrayList;
import java.util.List;

/**
 * One UI styled dialogs: rounded card, single-choice list with radio indicators
 * and a centred accent action label.
 */
public final class OneUiDialog {

    public interface OnOptionPicked {
        void onPicked(int index);
    }

    public interface OnInputConfirmed {
        void onConfirmed(String value);
    }

    private OneUiDialog() {
    }

    public static Dialog showSingleChoice(Context context, CharSequence title,
                                          CharSequence[] options, int selectedIndex,
                                          OnOptionPicked callback) {
        LinearLayout card = createCard(context);

        card.addView(createTitle(context, title));

        ScrollView scroll = new ScrollView(context);
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);

        List<OneUiRadioDot> dots = new ArrayList<>();
        Dialog[] holder = new Dialog[1];

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
        card.addView(createCancel(context, holder));

        Dialog dialog = buildDialog(context, card);
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

        Dialog dialog = buildDialog(context, card);
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

    private static Dialog buildDialog(Context context, View content) {
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
            params.width = screenWidth - OneUiTheme.dp(context, 48);
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(params);
        }
        return dialog;
    }
}
