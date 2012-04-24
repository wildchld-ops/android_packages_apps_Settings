/**
 * Copyright (C) 2009 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.android.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListFragment;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.UserDictionary;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AlphabetIndexer;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SectionIndexer;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import com.android.settings.inputmethod.UserDictionaryAddWordActivity;

import java.util.Locale;

public class UserDictionarySettings extends ListFragment {
    private static final String TAG = "UserDictionarySettings";

    private static final String[] QUERY_PROJECTION = {
        UserDictionary.Words._ID, UserDictionary.Words.WORD
    };

    private static final int INDEX_ID = 0;
    private static final int INDEX_WORD = 1;

    // Either the locale is empty (means the word is applicable to all locales)
    // or the word equals our current locale
    private static final String QUERY_SELECTION =
            UserDictionary.Words.LOCALE + "=?";
    private static final String QUERY_SELECTION_ALL_LOCALES =
            UserDictionary.Words.LOCALE + " is null";

    private static final String DELETE_SELECTION = UserDictionary.Words.WORD + "=?";

    private static final int OPTIONS_MENU_ADD = Menu.FIRST;

    private Cursor mCursor;

    protected String mLocale;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(
                com.android.internal.R.layout.preference_list_fragment, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        final Intent intent = getActivity().getIntent();
        final String localeFromIntent =
                null == intent ? null : intent.getStringExtra("locale");

        final Bundle arguments = getArguments();
        final String localeFromArguments =
                null == arguments ? null : arguments.getString("locale");

        final String locale;
        if (null != localeFromArguments) {
            locale = localeFromArguments;
        } else if (null != localeFromIntent) {
            locale = localeFromIntent;
        } else {
            locale = null;
        }

        mLocale = locale;
        mCursor = createCursor(locale);
        TextView emptyView = (TextView) getView().findViewById(android.R.id.empty);
        emptyView.setText(R.string.user_dict_settings_empty_text);

        final ListView listView = getListView();
        listView.setAdapter(createAdapter());
        listView.setFastScrollEnabled(true);
        listView.setEmptyView(emptyView);

        setHasOptionsMenu(true);

    }

    private Cursor createCursor(final String locale) {
        // Locale can be any of:
        // - The string representation of a locale, as returned by Locale#toString()
        // - The empty string. This means we want a cursor returning words valid for all locales.
        // - null. This means we want a cursor for the current locale, whatever this is.
        // Note that this contrasts with the data inside the database, where NULL means "all
        // locales" and there should never be an empty string. The confusion is called by the
        // historical use of null for "all locales".
        // TODO: it should be easy to make this more readable by making the special values
        // human-readable, like "all_locales" and "current_locales" strings, provided they
        // can be guaranteed not to match locales that may exist.
        if ("".equals(locale)) {
            // Case-insensitive sort
            return getActivity().managedQuery(UserDictionary.Words.CONTENT_URI, QUERY_PROJECTION,
                    QUERY_SELECTION_ALL_LOCALES, null,
                    "UPPER(" + UserDictionary.Words.WORD + ")");
        } else {
            final String queryLocale = null != locale ? locale : Locale.getDefault().toString();
            return getActivity().managedQuery(UserDictionary.Words.CONTENT_URI, QUERY_PROJECTION,
                    QUERY_SELECTION, new String[] { queryLocale },
                    "UPPER(" + UserDictionary.Words.WORD + ")");
        }
    }

    private ListAdapter createAdapter() {
        return new MyAdapter(getActivity(),
                R.layout.user_dictionary_item, mCursor,
                new String[] { UserDictionary.Words.WORD, UserDictionary.Words._ID },
                new int[] { android.R.id.text1, R.id.delete_button }, this);
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        String word = getWord(position);
        if (word != null) {
            showAddOrEditDialog(word);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        MenuItem actionItem =
                menu.add(0, OPTIONS_MENU_ADD, 0, R.string.user_dict_settings_add_menu_title)
                .setIcon(R.drawable.ic_menu_add);
        actionItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM |
                MenuItem.SHOW_AS_ACTION_WITH_TEXT);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == OPTIONS_MENU_ADD) {
            showAddOrEditDialog(null);
            return true;
        }
        return false;
    }

    /**
     * Add or edit a word. If editingWord is null, it's an add; otherwise, it's an edit.
     * @param editingWord the word to edit, or null if it's an add.
     */
    private void showAddOrEditDialog(final String editingWord) {
        final Intent intent = new Intent(null == editingWord
                ? UserDictionaryAddWordActivity.MODE_INSERT_ACTION
                : UserDictionaryAddWordActivity.MODE_EDIT_ACTION);
        // The following are fine if they are null
        intent.putExtra(UserDictionaryAddWordActivity.EXTRA_WORD, editingWord);
        intent.putExtra(UserDictionaryAddWordActivity.EXTRA_LOCALE, mLocale);
        startActivity(intent);
    }

    private String getWord(int position) {
        if (null == mCursor) return null;
        mCursor.moveToPosition(position);
        // Handle a possible race-condition
        if (mCursor.isAfterLast()) return null;

        return mCursor.getString(
                mCursor.getColumnIndexOrThrow(UserDictionary.Words.WORD));
    }

    public static void deleteWord(final String word, final ContentResolver resolver) {
        resolver.delete(
                UserDictionary.Words.CONTENT_URI, DELETE_SELECTION, new String[] { word });
    }

    private static class MyAdapter extends SimpleCursorAdapter implements SectionIndexer,
            View.OnClickListener {

        private AlphabetIndexer mIndexer;
        private UserDictionarySettings mSettings;

        private ViewBinder mViewBinder = new ViewBinder() {

            public boolean setViewValue(View v, Cursor c, int columnIndex) {
                if (v instanceof ImageView && columnIndex == INDEX_ID) {
                    v.setOnClickListener(MyAdapter.this);
                    v.setTag(c.getString(INDEX_WORD));
                    return true;
                }

                return false;
            }
        };

        public MyAdapter(Context context, int layout, Cursor c, String[] from, int[] to,
                UserDictionarySettings settings) {
            super(context, layout, c, from, to);

            mSettings = settings;
            if (null != c) {
                final String alphabet = context.getString(
                        com.android.internal.R.string.fast_scroll_alphabet);
                final int wordColIndex = c.getColumnIndexOrThrow(UserDictionary.Words.WORD);
                mIndexer = new AlphabetIndexer(c, wordColIndex, alphabet);
            }
            setViewBinder(mViewBinder);
        }

        public int getPositionForSection(int section) {
            return null == mIndexer ? 0 : mIndexer.getPositionForSection(section);
        }

        public int getSectionForPosition(int position) {
            return null == mIndexer ? 0 : mIndexer.getSectionForPosition(position);
        }

        public Object[] getSections() {
            return null == mIndexer ? null : mIndexer.getSections();
        }

        public void onClick(View v) {
            UserDictionarySettings.deleteWord((String) v.getTag(),
                    mSettings.getActivity().getContentResolver());
        }
    }
}
