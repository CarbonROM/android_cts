/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.provider.cts;

import static android.provider.cts.MediaStoreTest.TAG;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore.Audio.Media;
import android.provider.cts.MediaStoreAudioTestHelper.Audio1;
import android.support.test.InstrumentationRegistry;
import android.util.Log;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MediaStore_Audio_MediaTest {
    private Context mContext;
    private ContentResolver mContentResolver;

    @Parameter(0)
    public String mVolumeName;

    @Parameters
    public static Iterable<? extends Object> data() {
        return ProviderTestUtils.getSharedVolumeNames();
    }

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mContentResolver = mContext.getContentResolver();

        Log.d(TAG, "Using volume " + mVolumeName);
    }

    @Test
    public void testGetContentUri() {
        Cursor c = null;
        assertNotNull(c = mContentResolver.query(
                Media.getContentUri(mVolumeName), null, null,
                    null, null));
        c.close();
    }

    @Test
    public void testGetContentUriForPath() {
        Cursor c = null;
        String externalPath = Environment.getExternalStorageDirectory().getPath();
        assertNotNull(c = mContentResolver.query(Media.getContentUriForPath(externalPath), null, null,
                null, null));
        c.close();

        String internalPath = mContext.getFilesDir().getAbsolutePath();
        assertNotNull(c = mContentResolver.query(Media.getContentUriForPath(internalPath), null, null,
                null, null));
        c.close();
    }

    @Test
    public void testStoreAudioMedia() {
        Audio1 audio1 = Audio1.getInstance();
        ContentValues values = audio1.getContentValues(mVolumeName);
        //insert
        Uri mediaUri = Media.getContentUri(mVolumeName);
        Uri uri = mContentResolver.insert(mediaUri, values);
        assertNotNull(uri);

        try {
            // query
            // the following columns in the table are generated automatically when inserting:
            // _ID, DATE_ADDED, ALBUM_ID, ALBUM_KEY, ARTIST_ID, ARTIST_KEY, TITLE_KEY
            // the column DISPLAY_NAME will be ignored when inserting
            Cursor c = mContentResolver.query(uri, null, null, null, null);
            assertEquals(1, c.getCount());
            c.moveToFirst();
            long id = c.getLong(c.getColumnIndex(Media._ID));
            assertTrue(id > 0);
            String expected = audio1.getContentValues(mVolumeName).getAsString(Media.DATA);
            assertEquals(expected, c.getString(c.getColumnIndex(Media.DATA)));
            assertTrue(c.getLong(c.getColumnIndex(Media.DATE_ADDED)) > 0);
            assertEquals(Audio1.DATE_MODIFIED, c.getLong(c.getColumnIndex(Media.DATE_MODIFIED)));
            assertEquals(Audio1.DISPLAY_NAME, c.getString(c.getColumnIndex(Media.DISPLAY_NAME)));
            assertEquals(Audio1.MIME_TYPE, c.getString(c.getColumnIndex(Media.MIME_TYPE)));
            assertEquals(Audio1.SIZE, c.getInt(c.getColumnIndex(Media.SIZE)));
            assertEquals(Audio1.TITLE, c.getString(c.getColumnIndex(Media.TITLE)));
            assertEquals(Audio1.ALBUM, c.getString(c.getColumnIndex(Media.ALBUM)));
            String albumKey = c.getString(c.getColumnIndex(Media.ALBUM_KEY));
            assertNotNull(albumKey);
            long albumId = c.getLong(c.getColumnIndex(Media.ALBUM_ID));
            assertTrue(albumId > 0);
            assertEquals(Audio1.ARTIST, c.getString(c.getColumnIndex(Media.ARTIST)));
            String artistKey = c.getString(c.getColumnIndex(Media.ARTIST_KEY));
            assertNotNull(artistKey);
            long artistId = c.getLong(c.getColumnIndex(Media.ARTIST_ID));
            assertTrue(artistId > 0);
            assertEquals(Audio1.COMPOSER, c.getString(c.getColumnIndex(Media.COMPOSER)));
            assertEquals(Audio1.DURATION, c.getLong(c.getColumnIndex(Media.DURATION)));
            assertEquals(Audio1.IS_ALARM, c.getInt(c.getColumnIndex(Media.IS_ALARM)));
            assertEquals(Audio1.IS_MUSIC, c.getInt(c.getColumnIndex(Media.IS_MUSIC)));
            assertEquals(Audio1.IS_NOTIFICATION, c.getInt(c.getColumnIndex(Media.IS_NOTIFICATION)));
            assertEquals(Audio1.IS_RINGTONE, c.getInt(c.getColumnIndex(Media.IS_RINGTONE)));
            assertEquals(Audio1.TRACK, c.getInt(c.getColumnIndex(Media.TRACK)));
            assertEquals(Audio1.YEAR, c.getInt(c.getColumnIndex(Media.YEAR)));
            String titleKey = c.getString(c.getColumnIndex(Media.TITLE_KEY));
            assertNotNull(titleKey);
            c.close();

            // test filtering
            Uri baseUri = Media.getContentUri(mVolumeName);
            Uri filterUri = baseUri.buildUpon()
                .appendQueryParameter("filter", Audio1.ARTIST).build();
            c = mContentResolver.query(filterUri, null, null, null, null);
            assertEquals(1, c.getCount());
            c.moveToFirst();
            long fid = c.getLong(c.getColumnIndex(Media._ID));
            assertTrue(id == fid);
            c.close();

            filterUri = baseUri.buildUpon().appendQueryParameter("filter", "xyzfoo").build();
            c = mContentResolver.query(filterUri, null, null, null, null);
            assertEquals(0, c.getCount());
            c.close();
        } finally {
            // delete
            int result = mContentResolver.delete(uri, null, null);
            assertEquals(1, result);
        }
    }
}
