/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.cts.deviceinfo;

import android.os.Bundle;

import com.android.compatibility.common.deviceinfo.DeviceInfo;
import com.android.compatibility.common.util.InfoStore;

import java.util.Arrays;

/**
 * Sample device info collector.
 */
public class SampleDeviceInfo extends DeviceInfo {

    @Override
    protected void collectDeviceInfo(InfoStore store) throws Exception {
        boolean[] booleans = {Boolean.TRUE, Boolean.FALSE};
        double[] doubles = {Double.MAX_VALUE, Double.MIN_VALUE};
        int[] ints = {Integer.MAX_VALUE, Integer.MIN_VALUE};
        long[] longs = {Long.MAX_VALUE, Long.MIN_VALUE};

        // Group Foo
        store.startGroup("foo");
        store.addResult("foo_boolean", Boolean.TRUE);

        // Group Bar
        store.startGroup("bar");
        store.addListResult("bar_string", Arrays.asList(new String[] {
                "bar-string-1",
                "bar-string-2",
                "bar-string-3"}));

        store.addArrayResult("bar_boolean", booleans);
        store.addArrayResult("bar_double", doubles);
        store.addArrayResult("bar_int", ints);
        store.addArrayResult("bar_long", longs);
        store.endGroup(); // bar

        store.addResult("foo_double", Double.MAX_VALUE);
        store.addResult("foo_int", Integer.MAX_VALUE);
        store.addResult("foo_long", Long.MAX_VALUE);
        store.addResult("foo_string", "foo-string");
        store.endGroup(); // foo
    }
}

