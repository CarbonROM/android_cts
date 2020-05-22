/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package android.security.cts;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;

import android.platform.test.annotations.SecurityTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import static org.junit.Assert.*;

@RunWith(DeviceJUnit4ClassRunner.class)
public class TestMedia extends SecurityTestCase {


    /******************************************************************************
     * To prevent merge conflicts, add tests for N below this comment, before any
     * existing test methods
     ******************************************************************************/

    /******************************************************************************
     * To prevent merge conflicts, add tests for O below this comment, before any
     * existing test methods
     ******************************************************************************/

    /**
     * b/24346430
     * Vulnerability Behaviour: SIGSEGV in self
     **/
    @SecurityTest(minPatchLevel = "2015-12")
    @Test
    public void testPocCVE_2015_6632() throws Exception {
        String inputFiles[] = {"cve_2015_6632.mp4"};
        AdbUtils.runPocAssertNoCrashesNotVulnerable("CVE-2015-6632",
                AdbUtils.TMP_PATH + inputFiles[0], inputFiles, AdbUtils.TMP_PATH, getDevice());
    }

    /**
     * b/62133227
     * Vulnerability Behaviour: SIGSEGV in self
     **/
    @SecurityTest(minPatchLevel = "2017-09")
    @Test
    public void testPocCVE_2017_0778() throws Exception {
        String inputFiles[] = {"cve_2017_0778.mp4"};
        AdbUtils.runPocAssertNoCrashesNotVulnerable("CVE-2017-0778",
                AdbUtils.TMP_PATH + inputFiles[0], inputFiles, AdbUtils.TMP_PATH, getDevice());
    }

    /**
     * b/112005441
     * Vulnerability Behaviour: EXIT_VULNERABLE (113)
     */
    @SecurityTest(minPatchLevel = "2019-09")
    @Test
    public void testPocCVE_2019_9313() throws Exception {
        AdbUtils.runPocAssertNoCrashesNotVulnerable("CVE-2019-9313", null, getDevice());
    }

    /**
     * b/127702368
     * Vulnerability Behaviour: EXIT_VULNERABLE (113)
     */
    @SecurityTest(minPatchLevel = "2019-08")
    @Test
    public void testPocCVE_2019_2126() throws Exception {
        AdbUtils.runPocAssertNoCrashesNotVulnerable("CVE-2019-2126", null, getDevice());
    }

    /**
     * b/36389123
     * Vulnerability Behaviour: EXIT_VULNERABLE (113)
     */
    @SecurityTest(minPatchLevel = "2017-08")
    @Test
    public void testPocCVE_2017_0726() throws Exception {
        String inputFiles[] = {"cve_2017_0726.mp4"};
        AdbUtils.runPocAssertNoCrashesNotVulnerable("CVE-2017-0726",
                AdbUtils.TMP_PATH + inputFiles[0], inputFiles, AdbUtils.TMP_PATH, getDevice());
    }

    /**
     * b/37239013
     * Vulnerability Behaviour: EXIT_VULNERABLE (113)
     */
    @SecurityTest(minPatchLevel = "2017-07")
    @Test
    public void testPocCVE_2017_0697() throws Exception {
        String inputFiles[] = {"cve_2017_0697.mp4"};
        AdbUtils.runPocAssertNoCrashesNotVulnerable("CVE-2017-0697",
                AdbUtils.TMP_PATH + inputFiles[0], inputFiles, AdbUtils.TMP_PATH, getDevice());
    }

    /**
     * b/112159345
     * Vulnerability Behaviour: SIGSEGV in self
     **/
    @SecurityTest(minPatchLevel = "2018-01")
    @Test
    public void testPocCVE_2018_9527() throws Exception {
        AdbUtils.runPocAssertNoCrashesNotVulnerable("CVE-2018-9527", null, getDevice());
    }

    /******************************************************************************
     * To prevent merge conflicts, add tests for P below this comment, before any
     * existing test methods
     ******************************************************************************/


    /******************************************************************************
     * To prevent merge conflicts, add tests for Q below this comment, before any
     * existing test methods
     ******************************************************************************/

}
