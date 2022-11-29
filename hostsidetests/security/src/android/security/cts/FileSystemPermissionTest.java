package android.security.cts;

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceTestCase;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class FileSystemPermissionTest extends DeviceTestCase {

   /**
    * A reference to the device under test.
    */
    private ITestDevice mDevice;

    /**
     * Used to build the find command for finding insecure file system components
     */
    private static final String INSECURE_DEVICE_ADB_COMMAND = "find %s -type %s -perm /o=rwx 2>/dev/null";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDevice = getDevice();
    }

    public void testAllBlockDevicesAreSecure() throws Exception {
        Set<String> insecure = getAllInsecureDevicesInDirAndSubdir("/dev", "b");
        assertTrue("Found insecure block devices: " + insecure.toString(),
                insecure.isEmpty());
    }

    /**
     * Searches for all world accessable files, note this may need sepolicy to search the desired
     * location and stat files.
     * @path The path to search, must be a directory.
     * @type The type of file to search for, must be a valid find command argument to the type
     *       option.
     * @returns The set of insecure fs objects found.
     */
    private Set<String> getAllInsecureDevicesInDirAndSubdir(String path, String type) throws DeviceNotAvailableException {

        String cmd = getInsecureDeviceAdbCommand(path, type);
        String output = mDevice.executeShellCommand(cmd);
        // Splitting an empty string results in an array of an empty string.
        String [] found = output.length() > 0 ? output.split("\\s") : new String[0];
        return new HashSet<String>(Arrays.asList(found));
    }

    private static String getInsecureDeviceAdbCommand(String path, String type) {
        return String.format(INSECURE_DEVICE_ADB_COMMAND, path, type);
    }

    private static String HW_RNG_DEVICE = "/dev/hw_random";

    public void testDevHwRandomPermissions() throws Exception {
        if (!mDevice.doesFileExist(HW_RNG_DEVICE)) {
            // Hardware RNG device is missing. This is OK because it is not required to be exposed
            // on all devices.
            return;
        }

        // Changed for Android 13 CTS:
        // Ownership of /dev/hw_random has two possibilities depending on whether the
        // prng seeder daemon is present, which ships in the Jan 2023 MPR, so this test
        // allows either possibility using stat rather than ls to make the two possibilities
        // clearer, at the cost of granular failure diagnostics.
        //
        // This test asserts that, if present, /dev/hw_random must:
        //
        // 1. Have ownership root:root or prng_seeder:prng_seeder
        // 2. Have permissions 0600 (the kernel default) or 0400 (prng seeder default)
        // 3. Be a character device with major:minor 10:183 (the kernel
        //    default), which in the hex output by stat corresponds to a:b7
        //
        // That translates to "stat -c %t,%T:%a:%U:%G: output that corresponds to one
        // of the options below.

        String command = "stat -c %t,%T:%a:%U:%G " + HW_RNG_DEVICE;
        String output = mDevice.executeShellCommand(command).trim();
        assertThat("WRONG major, minor, mode or ownership on " + HW_RNG_DEVICE, output,
                anyOf(is("a,b7:600:root:root"),
                      is("a,b7:400:prng_seeder:prng_seeder")));

    }
}
