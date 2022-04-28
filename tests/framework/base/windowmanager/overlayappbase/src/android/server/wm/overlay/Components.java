/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.server.wm.overlay;

import android.content.ComponentName;
import android.os.Bundle;
import android.server.wm.component.ComponentsBase;

public class Components extends ComponentsBase {

    public interface TranslucentFloatingActivity {
        String ACTION_FINISH =
                "android.server.wm.overlay.TranslucentFloatingActivity.ACTION_FINISH";
        ComponentName BASE_COMPONENT = component("TranslucentFloatingActivity");
        static ComponentName getComponent(String packageName) {
            return new ComponentName(packageName, BASE_COMPONENT.getClassName());
        }
    }

    public interface TrampolineActivity {
        ComponentName BASE_COMPONENT = component("TrampolineActivity");
        String COMPONENTS_EXTRA = "components_extra";

        static ComponentName getComponent(String packageName) {
            return new ComponentName(packageName, BASE_COMPONENT.getClassName());
        }

        static Bundle buildTrampolineExtra(ComponentName... componentNames) {
            Bundle trampolineTarget = new Bundle();
            trampolineTarget.putParcelableArray(COMPONENTS_EXTRA, componentNames);
            return trampolineTarget;
        }

    }

    private static ComponentName component(String className) {
        return component(Components.class, className);
    }
}
