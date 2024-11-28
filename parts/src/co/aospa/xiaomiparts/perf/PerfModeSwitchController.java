/*
 * Copyright (C) 2024 Paranoid Android
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
 * limitations under the License
 */

package co.aospa.xiaomiparts.perf;

import static com.android.settingslib.drawer.CategoryKey.CATEGORY_BATTERY;

import android.content.Context;
import android.os.PowerManager;

import co.aospa.xiaomiparts.R;
import com.android.settingslib.drawer.EntryController;
import com.android.settingslib.drawer.ProviderSwitch;

public class PerfModeSwitchController extends EntryController implements ProviderSwitch {

    private final Context mContext;
    private final PowerManager mPowerManager;

    public PerfModeSwitchController(Context context) {
        super();
        mContext = context;
        mPowerManager = context.getSystemService(PowerManager.class);
    }

    @Override
    public String getKey() {
        return PerfModeUtils.PREF_KEY;
    }

    @Override
    protected MetaData getMetaData() {
        return new MetaData(CATEGORY_BATTERY)
                .setTitle(R.string.perf_mode_title)
                .setSummary(R.string.perf_mode_summary)
                .setOrder(1);
    }

    @Override
    public boolean isSwitchChecked() {
        return PerfModeUtils.getInstance(mContext).isPerformanceModeOn()
                && !mPowerManager.isPowerSaveMode();
    }

    @Override
    public boolean onSwitchCheckedChanged(boolean checked) {
        final PerfModeUtils utils = PerfModeUtils.getInstance(mContext);
        return checked ? utils.turnOnPerformanceMode() : utils.turnOffPerformanceMode();
    }

    @Override
    public String getSwitchErrorMessage(boolean attemptedChecked) {
        return mContext.getString(mPowerManager.isPowerSaveMode()
                ? R.string.perf_mode_battery_saver_on : R.string.perf_mode_error);
    }
}
