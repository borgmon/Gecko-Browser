package com.example.geckobrowser;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.widget.EditText;

/** Address field that stops editing (collapses the bar) when Back closes the keyboard. */
public class UrlEditText extends EditText {

    public UrlEditText(Context context) {
        super(context);
    }

    public UrlEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public UrlEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP
                && hasFocus()) {
            clearFocus();
        }
        return super.onKeyPreIme(keyCode, event);
    }
}
