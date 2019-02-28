/*
 * Copyright (C) 2018 Nafundi
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.nobi.forms.android.location.activities;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.nobi.forms.android.activities.GeoTraceActivity;
import org.nobi.forms.android.location.client.FakeLocationClient;
import org.nobi.forms.android.location.client.LocationClients;
import org.nobi.forms.android.map.GoogleMapFragment;
import org.nobi.forms.android.map.MapPoint;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class GeoTraceActivityTest extends BaseGeoActivityTest {
    @Rule public MockitoRule rule = MockitoJUnit.rule();
    private ActivityController<GeoTraceActivity> controller;
    private GeoTraceActivity activity;
    private FakeLocationClient fakeLocationClient;

    @Before public void setUp() throws Exception {
        super.setUp();
        fakeLocationClient = new FakeLocationClient();
        LocationClients.setTestClient(fakeLocationClient);
        GoogleMapFragment.testMode = true;
        controller = Robolectric.buildActivity(GeoTraceActivity.class);
    }

    @Test public void testLocationClientLifecycle() {
        // Starting the activity should start the location client.
        activity = controller.create().start().resume().visible().get();
        assertTrue(fakeLocationClient.isRunning());

        // Acquiring a fix should set the location on the map.
        fakeLocationClient.receiveFix(createLocation("GPS", 11, 12, 13, 14f));
        assertEquals(new MapPoint(11, 12, 13, 14), activity.getMapFragment().getGpsLocation());

        // Acquiring a second fix should update the map with the new location.
        fakeLocationClient.receiveFix(createLocation("GPS", 21, 22, 23, 24f));
        assertEquals(new MapPoint(21, 22, 23, 24), activity.getMapFragment().getGpsLocation());

        // Stopping the activity should stop the location client.
        controller.stop();
        assertFalse(fakeLocationClient.isRunning());
    }

    @Test public void shouldEnablePlayButtonOnLocationFix() {
        activity = controller.create().start().resume().visible().get();

        // The "Play" button should be initially disabled.
        assertFalse(activity.getPlayButton().isEnabled());

        // Acquiring a fix should cause the "Play" button to become enabled.
        fakeLocationClient.receiveFix(createLocation("GPS", 1, 2, 3, 4f));
        assertTrue(activity.getPlayButton().isEnabled());
    }
}
