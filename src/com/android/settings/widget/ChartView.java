/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.widget;

import static com.google.common.base.Preconditions.checkNotNull;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Container for two-dimensional chart, drawn with a combination of
 * {@link ChartGridView}, {@link ChartNetworkSeriesView} and {@link ChartSweepView}
 * children. The entire chart uses {@link ChartAxis} to map between raw values
 * and screen coordinates.
 */
public class ChartView extends FrameLayout {
    private static final String TAG = "ChartView";

    // TODO: extend something that supports two-dimensional scrolling

    ChartAxis mHoriz;
    ChartAxis mVert;

    private Rect mContent = new Rect();

    public ChartView(Context context) {
        this(context, null, 0);
    }

    public ChartView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ChartView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        setClipToPadding(false);
        setClipChildren(false);
    }

    void init(ChartAxis horiz, ChartAxis vert) {
        mHoriz = checkNotNull(horiz, "missing horiz");
        mVert = checkNotNull(vert, "missing vert");
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        mContent.set(getPaddingLeft(), getPaddingTop(), r - l - getPaddingRight(),
                b - t - getPaddingBottom());
        final int width = mContent.width();
        final int height = mContent.height();

        // no scrolling yet, so tell dimensions to fill exactly
        mHoriz.setSize(width);
        mVert.setSize(height);

        final Rect parentRect = new Rect();
        final Rect childRect = new Rect();
        final Rect extraMargins = new Rect();

        for (int i = 0; i < getChildCount(); i++) {
            final View child = getChildAt(i);
            final LayoutParams params = (LayoutParams) child.getLayoutParams();

            parentRect.set(mContent);

            if (child instanceof ChartNetworkSeriesView || child instanceof ChartGridView) {
                // series are always laid out to fill entire graph area
                // TODO: handle scrolling for series larger than content area
                Gravity.apply(params.gravity, width, height, parentRect, childRect);
                child.layout(childRect.left, childRect.top, childRect.right, childRect.bottom);

            } else if (child instanceof ChartSweepView) {
                // sweep is always placed along specific dimension
                final ChartSweepView sweep = (ChartSweepView) child;
                final float point = sweep.getPoint();
                sweep.getExtraMargins(extraMargins);

                if (sweep.getFollowAxis() == ChartSweepView.HORIZONTAL) {
                    parentRect.left = parentRect.right = (int) point + getPaddingLeft();
                    parentRect.top -= extraMargins.top;
                    parentRect.bottom += extraMargins.bottom;
                    Gravity.apply(params.gravity, child.getMeasuredWidth(), parentRect.height(),
                            parentRect, childRect);

                } else {
                    parentRect.top = parentRect.bottom = (int) point + getPaddingTop();
                    parentRect.left -= extraMargins.left;
                    parentRect.right += extraMargins.right;
                    Gravity.apply(params.gravity, parentRect.width(), child.getMeasuredHeight(),
                            parentRect, childRect);
                }
            }

            child.layout(childRect.left, childRect.top, childRect.right, childRect.bottom);
        }
    }

}
