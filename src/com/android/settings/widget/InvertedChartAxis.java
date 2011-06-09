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

/**
 * Utility to invert another {@link ChartAxis}.
 */
public class InvertedChartAxis implements ChartAxis {
    private final ChartAxis mWrapped;
    private float mSize;

    public InvertedChartAxis(ChartAxis wrapped) {
        mWrapped = wrapped;
    }

    /** {@inheritDoc} */
    public void setSize(float size) {
        mSize = size;
        mWrapped.setSize(size);
    }

    /** {@inheritDoc} */
    public float convertToPoint(long value) {
        return mSize - mWrapped.convertToPoint(value);
    }

    /** {@inheritDoc} */
    public long convertToValue(float point) {
        return mWrapped.convertToValue(mSize - point);
    }

    /** {@inheritDoc} */
    public CharSequence getLabel(long value) {
        return mWrapped.getLabel(value);
    }

    /** {@inheritDoc} */
    public float[] getTickPoints() {
        final float[] points = mWrapped.getTickPoints();
        for (int i = 0; i < points.length; i++) {
            points[i] = mSize - points[i];
        }
        return points;
    }
}
