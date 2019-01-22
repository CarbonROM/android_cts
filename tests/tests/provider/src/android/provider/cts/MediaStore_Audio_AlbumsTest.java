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
import static org.junit.Assert.fail;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio.Albums;
import android.provider.MediaStore.Audio.Media;
import android.provider.cts.MediaStoreAudioTestHelper.Audio1;
import android.provider.cts.MediaStoreAudioTestHelper.Audio2;
import android.support.test.InstrumentationRegistry;
import android.util.Log;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.io.File;
import java.io.FileNotFoundException;

@RunWith(Parameterized.class)
public class MediaStore_Audio_AlbumsTest {
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
                Albums.getContentUri(mVolumeName), null, null,
                null, null));
        c.close();
    }

    @Test
    public void testStoreAudioAlbums() {
        // do not support direct insert operation of the albums
        Uri audioAlbumsUri = Albums.getContentUri(mVolumeName);
        try {
            mContentResolver.insert(audioAlbumsUri, new ContentValues());
            fail("Should throw UnsupportedOperationException!");
        } catch (UnsupportedOperationException e) {
            // expected
        }

        // the album item is inserted when inserting audio media
        Audio1 audio1 = Audio1.getInstance();
        Uri audioMediaUri = audio1.insert(mContentResolver, mVolumeName);

        String selection = Albums.ALBUM +"=?";
        String[] selectionArgs = new String[] { Audio1.ALBUM };
        try {
            // query
            Cursor c = mContentResolver.query(audioAlbumsUri, null, selection, selectionArgs,
                    null);
            assertEquals(1, c.getCount());
            c.moveToFirst();
            long id = c.getLong(c.getColumnIndex(Albums._ID));
            assertTrue(id > 0);
            assertEquals(Audio1.ALBUM, c.getString(c.getColumnIndex(Albums.ALBUM)));
            assertNull(c.getString(c.getColumnIndex(Albums.ALBUM_ART)));
            assertNotNull(c.getString(c.getColumnIndex(Albums.ALBUM_KEY)));
            assertEquals(Audio1.ARTIST, c.getString(c.getColumnIndex(Albums.ARTIST)));
            assertEquals(Audio1.YEAR, c.getInt(c.getColumnIndex(Albums.FIRST_YEAR)));
            assertEquals(Audio1.YEAR, c.getInt(c.getColumnIndex(Albums.LAST_YEAR)));
            assertEquals(1, c.getInt(c.getColumnIndex(Albums.NUMBER_OF_SONGS)));
            // the ALBUM_ID column does not exist
            try {
                c.getColumnIndexOrThrow(Albums.ALBUM_ID);
                fail("Should throw IllegalArgumentException because there is no column with name "
                        + "\"Albums.ALBUM_ID\" in the table");
            } catch (IllegalArgumentException e) {
                // expected
            }
            // the NUMBER_OF_SONGS_FOR_ARTIST column does not exist
            try {
                c.getColumnIndexOrThrow(Albums.NUMBER_OF_SONGS_FOR_ARTIST);
                fail("Should throw IllegalArgumentException because there is no column with name "
                        + "\"Albums.NUMBER_OF_SONGS_FOR_ARTIST\" in the table");
            } catch (IllegalArgumentException e) {
                // expected
            }
            c.close();

            // do not support update operation of the albums
            ContentValues albumValues = new ContentValues();
            albumValues.put(Albums.ALBUM, Audio2.ALBUM);
            try {
                mContentResolver.update(audioAlbumsUri, albumValues, selection, selectionArgs);
                fail("Should throw UnsupportedOperationException!");
            } catch (UnsupportedOperationException e) {
                // expected
            }

            // do not support delete operation of the albums
            try {
                mContentResolver.delete(audioAlbumsUri, selection, selectionArgs);
                fail("Should throw UnsupportedOperationException!");
            } catch (UnsupportedOperationException e) {
                // expected
            }

            // test filtering
            Uri filterUri = audioAlbumsUri.buildUpon()
                .appendQueryParameter("filter", Audio1.ARTIST).build();
            c = mContentResolver.query(filterUri, null, null, null, null);
            assertEquals(1, c.getCount());
            c.moveToFirst();
            long fid = c.getLong(c.getColumnIndex(Albums._ID));
            assertTrue(id == fid);
            c.close();

            filterUri = audioAlbumsUri.buildUpon().appendQueryParameter("filter", "xyzfoo").build();
            c = mContentResolver.query(filterUri, null, null, null, null);
            assertEquals(0, c.getCount());
            c.close();
        } finally {
            mContentResolver.delete(audioMediaUri, null, null);
        }
        // the album items are deleted when deleting the audio media which belongs to the album
        Cursor c = mContentResolver.query(audioAlbumsUri, null, selection, selectionArgs, null);
        assertEquals(0, c.getCount());
        c.close();
    }

    @Test
    public void testAlbumArt() throws Exception {
        final File dir = ProviderTestUtils.stageDir(mVolumeName);
        final File path = new File(dir, "test" + System.currentTimeMillis() + ".mp3");
        Uri uri = null;
        try {
            ProviderTestUtils.stageFile(R.raw.testmp3, path);

            ContentValues v = new ContentValues();
            v.put(Media.DATA, path.getAbsolutePath());
            v.put(Media.TITLE, "testing");
            v.put(Albums.ALBUM, "test" + System.currentTimeMillis());
            uri = mContentResolver.insert(MediaStore.Audio.Media.getContentUri(mVolumeName), v);
            AssetFileDescriptor afd = mContentResolver.openAssetFileDescriptor(
                    uri.buildUpon().appendPath("albumart").build(), "r");
            assertNotNull(afd);
            afd.close();

            Cursor c = mContentResolver.query(uri, null, null, null, null);
            c.moveToFirst();
            long aid = c.getLong(c.getColumnIndex(Albums.ALBUM_ID));
            c.close();

            // TODO: migrate this to using public API
            Uri albumartDir = MediaStore.AUTHORITY_URI.buildUpon().appendPath(mVolumeName)
                    .appendPath("audio").appendPath("albumart").build();
            Uri albumart = ContentUris.withAppendedId(albumartDir, aid);
            try {
                mContentResolver.delete(albumart, null, null);
                afd = mContentResolver.openAssetFileDescriptor(albumart, "r");
            } catch (FileNotFoundException e) {
                fail("no album art");
            }

            c = mContentResolver.query(albumart, null, null, null, null);
            c.moveToFirst();
            String albumartfile = c.getString(c.getColumnIndex("_data"));
            c.close();
            new File(albumartfile).delete();
            try {
                afd = mContentResolver.openAssetFileDescriptor(albumart, "r");
            } catch (FileNotFoundException e) {
                fail("no album art");
            }

        } finally {
            path.delete();
            if (uri != null) {
                mContentResolver.delete(uri, null, null);
            }
        }
    }
}
