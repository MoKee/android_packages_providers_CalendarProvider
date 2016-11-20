/*
 * Copyright (C) 2016 The MoKee Open Source Project
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

package com.android.providers.calendar;

import android.app.backup.BackupManager;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Alarm;
import android.util.Log;
import android.net.Uri;

public class CalendarAlarmProvider extends ContentProvider {

    private static final String TAG = "CalendarAlarmProvider";
    private static final boolean DEBUG = false;

    private static final String DATABASE_NAME = "alarm.db";
    private static final int DATABASE_VERSION = 1;

    private static final String HOLIDAY_TABLE = "holiday";
    private static final String WORKDAY_TABLE = "workday";

    private static final int CA_HOLIDAY = 0;
    private static final int CA_WORKDAY = 1;

    private static final UriMatcher sURIMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        sURIMatcher.addURI(Alarm.AUTHORITY, "holiday/*", CA_HOLIDAY);
        sURIMatcher.addURI(Alarm.AUTHORITY, "workday/*", CA_WORKDAY);
    }

    private static class DatabaseHelper extends SQLiteOpenHelper {

        private Context mContext;

        public DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
            mContext = context;
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            // Set up the database schema
            db.execSQL("CREATE TABLE " + HOLIDAY_TABLE +
                    "(_id INTEGER PRIMARY KEY," +
                    "date TEXT UNIQUE," +
                    "state INTEGER);");
            db.execSQL("CREATE TABLE " + WORKDAY_TABLE +
                    "(_id INTEGER PRIMARY KEY," +
                    "date TEXT UNIQUE," +
                    "state INTEGER);");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        }

    }

    private DatabaseHelper mOpenHelper;
    private BackupManager mBackupManager;

    @Override
    public boolean onCreate() {
        mOpenHelper = new DatabaseHelper(getContext());
        mBackupManager = new BackupManager(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

        // Generate the body of the query.
        if (uri.equals(Alarm.CONTENT_FILTER_HOLIDAY_URI)) {
            qb.setTables(HOLIDAY_TABLE);
        } else {
            qb.setTables(WORKDAY_TABLE);
        }

        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        Cursor ret = null;
        try {
            ret = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
        } catch (SQLiteException e) {
            Log.e(TAG, "returning NULL cursor, query: " + uri, e);
        }

        // TODO: Does this need to be a URI for this provider.
        if (ret != null)
            ret.setNotificationUri(getContext().getContentResolver(), uri);
        return ret;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();

        long rowID = 0;
        if (uri.equals(Alarm.CONTENT_FILTER_HOLIDAY_URI)) {
            rowID = db.insertWithOnConflict(HOLIDAY_TABLE, null ,values, SQLiteDatabase.CONFLICT_IGNORE);
        }
        else {
            rowID = db.insertWithOnConflict(WORKDAY_TABLE, null, values, SQLiteDatabase.CONFLICT_IGNORE);
        }

        if (rowID <= 0) {
            return null;
        }

        if (DEBUG) Log.d(TAG, "inserted " + values + "rowID = " + rowID);
        notifyChange(uri);

        return ContentUris.withAppendedId(uri.equals(Alarm.CONTENT_FILTER_HOLIDAY_URI) ?
                Alarm.CONTENT_FILTER_HOLIDAY_URI : Alarm.CONTENT_FILTER_WORKDAY_URI, rowID);
    }

    @Override
    public String getType(Uri uri) {
        int match = sURIMatcher.match(uri);
        switch (match) {
            case CA_HOLIDAY:
                return "vnd.android.cursor.item/holiday";
            case CA_WORKDAY:
                return "vnd.android.cursor.item/workday";
            default:
                throw new IllegalArgumentException("Unknown URL " + uri);
        }
    }

    @Override
    public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {
        return 0;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    private void notifyChange(Uri changeUri) {
        if (changeUri == null) {
            getContext().getContentResolver().notifyChange(Alarm.CONTENT_URI, null);
        } else {
            getContext().getContentResolver().notifyChange(changeUri, null);
        }
        mBackupManager.dataChanged();
    }

}
