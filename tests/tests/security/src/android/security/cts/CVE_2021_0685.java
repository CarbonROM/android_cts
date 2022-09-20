/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.security.cts;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNoException;

import android.content.IntentFilter;
import android.content.pm.parsing.component.ParsedIntentInfo;
import android.os.Parcel;
import android.platform.test.annotations.AsbSecurityTest;
import android.text.TextUtils;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CVE_2021_0685 extends StsExtraBusinessLogicTestCase {
    private static final int VAL_LABEL_RES = 5;
    private static final boolean VAL_HAS_DEFAULT = true;
    private static final String VAL_NONLOCALIZED_LABEL = "CVE_2021_0965";
    private static final int VAL_ICON = 7;
    private static final int PARCELABLE_FLAGS = 0;

    @AsbSecurityTest(cveBugId = 191055353)
    @Test
    public void testPocCVE_2021_0685() {
        Parcel parcel = null;
        try {
            parcel = Parcel.obtain();
            parcel.writeString(ParsedIntentInfo.class.getName());
            new IntentFilter().writeToParcel(parcel, PARCELABLE_FLAGS);
            parcel.writeBoolean(VAL_HAS_DEFAULT);
            parcel.writeInt(VAL_LABEL_RES);
            TextUtils.writeToParcel(VAL_NONLOCALIZED_LABEL, parcel, PARCELABLE_FLAGS);
            parcel.writeInt(VAL_ICON);

            parcel.setDataPosition(0);
            ParsedIntentInfo info = parcel.readParcelable(ParsedIntentInfo.class.getClassLoader());
            if (info.getLabelRes() == VAL_LABEL_RES && info.isHasDefault() == VAL_HAS_DEFAULT
                    && info.getNonLocalizedLabel().equals(VAL_NONLOCALIZED_LABEL)
                    && info.getIcon() == VAL_ICON) {
                fail("Vulnerable to b/191055353!!");
            }
        } catch (Exception e) {
            if (e instanceof ClassCastException) {
                return;
            }
            assumeNoException(e);
        } finally {
            try {
                parcel.recycle();
            } catch (Exception e) {
                // ignore all exceptions.
            }
        }
    }
}
