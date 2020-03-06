/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.cts.appsearch;

import static com.google.common.truth.Truth.assertThat;

import android.app.appsearch.AppSearchBatchResult;
import android.app.appsearch.AppSearchDocument;
import android.app.appsearch.AppSearchEmail;
import android.app.appsearch.AppSearchManager;
import android.app.appsearch.AppSearchSchema;
import android.app.appsearch.AppSearchSchema.PropertyConfig;
import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.common.collect.ImmutableList;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AppSearchManagerTest {
    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private final AppSearchManager mAppSearch = mContext.getSystemService(AppSearchManager.class);

    @Test
    public void testGetService() {
        assertThat(mContext.getSystemService(Context.APP_SEARCH_SERVICE)).isNotNull();
        assertThat(mContext.getSystemService(AppSearchManager.class)).isNotNull();
        assertThat(mAppSearch).isNotNull();
    }

    @Test
    public void testSetSchema() {
        AppSearchSchema emailSchema = new AppSearchSchema.Builder("Email")
                .addProperty(new AppSearchSchema.PropertyConfig.Builder("subject")
                        .setDataType(PropertyConfig.DATA_TYPE_STRING)
                        .setCardinality(PropertyConfig.CARDINALITY_OPTIONAL)
                        .setIndexingType(PropertyConfig.INDEXING_TYPE_PREFIXES)
                        .setTokenizerType(PropertyConfig.TOKENIZER_TYPE_PLAIN)
                        .build()
                ).addProperty(new AppSearchSchema.PropertyConfig.Builder("body")
                        .setDataType(PropertyConfig.DATA_TYPE_STRING)
                        .setCardinality(PropertyConfig.CARDINALITY_OPTIONAL)
                        .setIndexingType(PropertyConfig.INDEXING_TYPE_PREFIXES)
                        .setTokenizerType(PropertyConfig.TOKENIZER_TYPE_PLAIN)
                        .build()
                ).build();
        assertThat(mAppSearch.setSchema(emailSchema).isSuccess()).isTrue();
    }

    @Test
    public void testPutDocuments() throws Exception {
        // Schema registration
        assertThat(mAppSearch.setSchema(AppSearchEmail.SCHEMA).isSuccess()).isTrue();

        // Index a document
        AppSearchEmail email = new AppSearchEmail.Builder("uri1")
                .setFrom("from@example.com")
                .setTo("to1@example.com", "to2@example.com")
                .setSubject("testPut example")
                .setBody("This is the body of the testPut email")
                .build();

        AppSearchBatchResult result = mAppSearch.putDocuments(ImmutableList.of(email));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getSuccesses()).containsExactly("uri1", null);
        assertThat(result.getFailures()).isEmpty();
    }

    @Test
    public void testGetDocuments() throws Exception {
        // Schema registration
        assertThat(mAppSearch.setSchema(AppSearchEmail.SCHEMA).isSuccess()).isTrue();

        // Index a document
        AppSearchEmail inEmail =
                new AppSearchEmail.Builder("uri1")
                        .setFrom("from@example.com")
                        .setTo("to1@example.com", "to2@example.com")
                        .setSubject("testPut example")
                        .setBody("This is the body of the testPut email")
                        .build();
        assertThat(mAppSearch.putDocuments(ImmutableList.of(inEmail)).isSuccess()).isTrue();

        // Get the document
        AppSearchBatchResult<String, AppSearchDocument> getResult =
                mAppSearch.getDocuments(ImmutableList.of("uri1"));
        assertThat(getResult.isSuccess()).isTrue();
        assertThat(getResult.getFailures()).isEmpty();
        assertThat(getResult.getSuccesses()).containsExactly("uri1", inEmail);
    }
}
