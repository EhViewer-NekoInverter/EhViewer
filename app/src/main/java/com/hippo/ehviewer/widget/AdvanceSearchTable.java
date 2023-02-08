/*
 * Copyright (C) 2015 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.ehviewer.widget;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;

import com.hippo.ehviewer.R;
import com.hippo.yorozuya.NumberUtils;

public class AdvanceSearchTable extends LinearLayout {
    public static final int SH = 0x1;
    public static final int STO = 0x2;
    public static final int SFL = 0x100;
    public static final int SFU = 0x200;
    public static final int SFT = 0x400;
    private static final String STATE_KEY_SUPER = "super";
    private static final String STATE_KEY_ADVANCE_SEARCH = "advance_search";
    private static final String STATE_KEY_MIN_RATING = "min_rating";
    private static final String STATE_KEY_PAGE_FROM = "page_from";
    private static final String STATE_KEY_PAGE_TO = "page_to";
    private CheckBox mSh;
    private CheckBox mSto;
    private CheckBox mSr;
    private Spinner mMinRating;
    private CheckBox mSp;
    private EditText mSpf;
    private EditText mSpt;
    private CheckBox mSfl;
    private CheckBox mSfu;
    private CheckBox mSft;

    public AdvanceSearchTable(Context context) {
        super(context);
        init(context);
    }

    public AdvanceSearchTable(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public void init(Context context) {
        setOrientation(LinearLayout.VERTICAL);

        LayoutInflater inflater = LayoutInflater.from(getContext());
        inflater.inflate(R.layout.widget_advance_search_table, this);

        ViewGroup row0 = (ViewGroup) getChildAt(0);
        mSh = (CheckBox) row0.getChildAt(0);
        mSto = (CheckBox) row0.getChildAt(1);

        ViewGroup row1 = (ViewGroup) getChildAt(1);
        mSr = (CheckBox) row1.getChildAt(0);
        mMinRating = (Spinner) row1.getChildAt(1);

        ViewGroup row2 = (ViewGroup) getChildAt(2);
        mSp = (CheckBox) row2.getChildAt(0);
        mSpf = (EditText) row2.getChildAt(1);
        mSpt = (EditText) row2.getChildAt(3);

        ViewGroup row4 = (ViewGroup) getChildAt(4);
        mSfl = (CheckBox) row4.getChildAt(0);
        mSfu = (CheckBox) row4.getChildAt(1);
        mSft = (CheckBox) row4.getChildAt(2);

        // Avoid java.lang.IllegalStateException: focus search returned a view that wasn't able to take focus!
        mSpt.setOnEditorActionListener((v, actionId, event) -> {
            View nextView = v.focusSearch(View.FOCUS_DOWN);
            if (nextView != null) {
                nextView.requestFocus(View.FOCUS_DOWN);
            }
            return true;
        });
    }

    public int getAdvanceSearch() {
        int advanceSearch = 0;
        if (mSh.isChecked()) advanceSearch |= SH;
        if (mSto.isChecked()) advanceSearch |= STO;
        if (mSfl.isChecked()) advanceSearch |= SFL;
        if (mSfu.isChecked()) advanceSearch |= SFU;
        if (mSft.isChecked()) advanceSearch |= SFT;
        return advanceSearch;
    }

    public void setAdvanceSearch(int advanceSearch) {
        mSh.setChecked(NumberUtils.int2boolean(advanceSearch & SH));
        mSto.setChecked(NumberUtils.int2boolean(advanceSearch & STO));
        mSfl.setChecked(NumberUtils.int2boolean(advanceSearch & SFL));
        mSfu.setChecked(NumberUtils.int2boolean(advanceSearch & SFU));
        mSft.setChecked(NumberUtils.int2boolean(advanceSearch & SFT));
    }

    public int getMinRating() {
        int position = mMinRating.getSelectedItemPosition();
        if (mSr.isChecked() && position >= 0) {
            return position + 2;
        } else {
            return -1;
        }
    }

    public void setMinRating(int minRating) {
        if (minRating >= 2 && minRating <= 5) {
            mSr.setChecked(true);
            mMinRating.setSelection(minRating - 2);
        } else {
            mSr.setChecked(false);
        }
    }

    public int getPageFrom() {
        if (mSp.isChecked()) {
            return NumberUtils.parseIntSafely(mSpf.getText().toString(), -1);
        }
        return -1;
    }

    public void setPageFrom(int pageFrom) {
        if (pageFrom > 0) {
            mSpf.setText(Integer.toString(pageFrom));
            mSp.setChecked(true);
        } else {
            mSp.setChecked(false);
            mSpf.setText(null);
        }
    }

    public int getPageTo() {
        if (mSp.isChecked()) {
            return NumberUtils.parseIntSafely(mSpt.getText().toString(), -1);
        }
        return -1;
    }

    public void setPageTo(int pageTo) {
        if (pageTo > 0) {
            mSpt.setText(Integer.toString(pageTo));
            mSp.setChecked(true);
        } else {
            mSp.setChecked(false);
            mSpt.setText(null);
        }
    }

    @Override
    public Parcelable onSaveInstanceState() {
        final Bundle state = new Bundle();
        state.putParcelable(STATE_KEY_SUPER, super.onSaveInstanceState());
        state.putInt(STATE_KEY_ADVANCE_SEARCH, getAdvanceSearch());
        state.putInt(STATE_KEY_MIN_RATING, getMinRating());
        state.putInt(STATE_KEY_PAGE_FROM, getPageFrom());
        state.putInt(STATE_KEY_PAGE_TO, getPageTo());
        return state;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (state instanceof Bundle) {
            final Bundle savedState = (Bundle) state;
            super.onRestoreInstanceState(savedState.getParcelable(STATE_KEY_SUPER));
            setAdvanceSearch(savedState.getInt(STATE_KEY_ADVANCE_SEARCH));
            setMinRating(savedState.getInt(STATE_KEY_MIN_RATING));
            setPageFrom(savedState.getInt(STATE_KEY_PAGE_FROM));
            setPageTo(savedState.getInt(STATE_KEY_PAGE_TO));
        }
    }
}
