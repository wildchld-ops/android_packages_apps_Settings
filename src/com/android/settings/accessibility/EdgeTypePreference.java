/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.settings.accessibility;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;
import android.view.accessibility.CaptioningManager;
import android.view.accessibility.CaptioningManager.CaptionStyle;
import android.widget.TextView;

import com.android.settings.R;

/**
 * Grid preference that allows the user to pick a captioning edge type.
 */
public class EdgeTypePreference extends ListDialogPreference {
    public EdgeTypePreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        final Resources res = context.getResources();
        setValues(res.getIntArray(R.array.captioning_edge_type_selector_values));
        setTitles(res.getStringArray(R.array.captioning_edge_type_selector_titles));
        setDialogLayoutResource(R.layout.grid_picker_dialog);
        setListItemLayoutResource(R.layout.preset_picker_item);
    }

    @Override
    public boolean shouldDisableDependents() {
        return getValue() == CaptionStyle.EDGE_TYPE_NONE || super.shouldDisableDependents();
    }

    @Override
    protected void onBindListItem(View view, int index) {
        final float fontSize = CaptioningManager.getFontSize(getContext().getContentResolver());
        final CaptioningTextView preview = (CaptioningTextView) view.findViewById(R.id.preview);
        preview.setTextColor(Color.WHITE);
        preview.setBackgroundColor(Color.TRANSPARENT);
        preview.setTextSize(fontSize);

        final int value = getValueAt(index);
        preview.applyEdge(value, Color.BLACK, 4.0f);

        final CharSequence title = getTitleAt(index);
        if (title != null) {
            final TextView summary = (TextView) view.findViewById(R.id.summary);
            summary.setText(title);
        }
    }
}
