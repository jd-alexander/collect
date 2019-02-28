/*
 * Copyright (C) 2016 Nafundi
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

package org.nobi.forms.android.activities;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.VisibleForTesting;
import android.view.Window;
import android.widget.ImageButton;

import org.nobi.forms.android.R;
import org.nobi.forms.android.map.GoogleMapFragment;
import org.nobi.forms.android.map.MapFragment;
import org.nobi.forms.android.map.MapPoint;
import org.nobi.forms.android.map.OsmMapFragment;
import org.nobi.forms.android.preferences.GeneralKeys;
import org.nobi.forms.android.spatial.MapHelper;
import org.nobi.forms.android.utilities.ToastUtils;
import org.nobi.forms.android.widgets.GeoShapeWidget;
import org.osmdroid.tileprovider.IRegisterReceiver;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.nobi.forms.android.utilities.PermissionUtils.areLocationPermissionsGranted;

/** Activity for entering or editing a polygon on a map. */
public class GeoShapeActivity extends BaseGeoMapActivity implements IRegisterReceiver {
    public static final String PREF_VALUE_GOOGLE_MAPS = "google_maps";
    public static final String MAP_CENTER_KEY = "map_center";
    public static final String MAP_ZOOM_KEY = "map_zoom";
    public static final String POINTS_KEY = "points";

    private MapFragment map;
    private int featureId = -1;  // will be a positive featureId once map is ready
    private ImageButton zoomButton;
    private ImageButton clearButton;
    private ImageButton backspaceButton;
    private String originalShapeString = "";

    // restored from savedInstanceState
    private MapPoint restoredMapCenter;
    private Double restoredMapZoom;
    private List<MapPoint> restoredPoints;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            restoredMapCenter = savedInstanceState.getParcelable(MAP_CENTER_KEY);
            restoredMapZoom = savedInstanceState.getDouble(MAP_ZOOM_KEY);
            restoredPoints = savedInstanceState.getParcelableArrayList(POINTS_KEY);
        }

        if (!areLocationPermissionsGranted(this)) {
            finish();
            return;
        }

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setTitle(getString(R.string.geoshape_title));
        setContentView(R.layout.geoshape_layout);
        createMapFragment().addTo(this, R.id.map_container, this::initMap);
    }

    public MapFragment createMapFragment() {
        String mapSdk = getIntent().getStringExtra(GeneralKeys.KEY_MAP_SDK);
        return (mapSdk == null || mapSdk.equals(PREF_VALUE_GOOGLE_MAPS)) ?
            new GoogleMapFragment() : new OsmMapFragment();
    }

    @Override protected void onStart() {
        super.onStart();
        // initMap() is called asynchronously, so map might not be initialized yet.
        if (map != null) {
            map.setGpsLocationEnabled(true);
        }
    }

    @Override protected void onStop() {
        // To avoid a memory leak, we have to shut down GPS when the activity
        // quits for good. But if it's only a screen rotation, we don't want to
        // stop/start GPS and make the user wait to get a GPS lock again.
        if (!isChangingConfigurations()) {
            // initMap() is called asynchronously, so map can be null if the activity
            // is stopped (e.g. by screen rotation) before initMap() gets to run.
            if (map != null) {
                map.setGpsLocationEnabled(false);
            }
        }
        super.onStop();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (map == null) {
            // initMap() is called asynchronously, so map can be null if the activity
            // is stopped (e.g. by screen rotation) before initMap() gets to run.
            // In this case, preserve any provided instance state.
            if (previousState != null) {
                state.putAll(previousState);
            }
            return;
        }
        state.putParcelable(MAP_CENTER_KEY, map.getCenter());
        state.putDouble(MAP_ZOOM_KEY, map.getZoom());
        state.putParcelableArrayList(POINTS_KEY, new ArrayList<>(map.getPolyPoints(featureId)));
    }

    @Override public void onBackPressed() {
        if (!formatPoints(map.getPolyPoints(featureId)).equals(originalShapeString)) {
            showBackDialog();
        } else {
            finish();
        }
    }

    @Override public void destroy() { }

    private void initMap(MapFragment newMapFragment) {
        if (newMapFragment == null) {  // could not create the map
            finish();
            return;
        }
        if (newMapFragment.getFragment().getActivity() == null) {
            // If the screen is rotated just after the activity starts but
            // before initMap() is called, then when the activity is re-created
            // in the new orientation, initMap() can sometimes be called on the
            // old, dead Fragment that used to be attached to the old activity.
            // Touching the dead Fragment will cause a crash; discard it.
            return;
        }

        map = newMapFragment;
        map.setLongPressListener(this::onLongPress);

        if (map instanceof GoogleMapFragment) {
            helper = new MapHelper(this, ((GoogleMapFragment) map).getGoogleMap(), selectedLayer);
        } else if (map instanceof OsmMapFragment) {
            helper = new MapHelper(this, ((OsmMapFragment) map).getMapView(), this, selectedLayer);
        }
        helper.setBasemap();

        zoomButton = findViewById(R.id.gps);
        zoomButton.setOnClickListener(v -> map.zoomToPoint(map.getGpsLocation(), true));

        backspaceButton = findViewById(R.id.backspace);
        backspaceButton.setOnClickListener(v -> removeLastPoint());

        clearButton = findViewById(R.id.clear);
        clearButton.setOnClickListener(v -> showClearDialog());

        ImageButton saveButton = findViewById(R.id.save);
        saveButton.setOnClickListener(v -> finishWithResult());

        ImageButton layersButton = findViewById(R.id.layers);
        layersButton.setOnClickListener(v -> helper.showLayersDialog());

        List<MapPoint> points = new ArrayList<>();
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra(GeoShapeWidget.SHAPE_LOCATION)) {
            originalShapeString = intent.getStringExtra(GeoShapeWidget.SHAPE_LOCATION);
            points = parsePoints(originalShapeString);
        }
        if (restoredPoints != null) {
            points = restoredPoints;
        }
        featureId = map.addDraggablePoly(points, true);

        map.setGpsLocationEnabled(true);
        if (restoredMapCenter != null && restoredMapZoom != null) {
            map.zoomToPoint(restoredMapCenter, restoredMapZoom, false);
        } else if (!points.isEmpty()) {
            map.zoomToBoundingBox(points, 0.6, false);
        } else {
            map.runOnGpsLocationReady(this::onGpsLocationReady);
        }
        updateUi();
    }

    @SuppressWarnings("unused")  // the "map" parameter is intentionally unused
    private void onGpsLocationReady(MapFragment map) {
        if (getWindow().isActive()) {
            map.zoomToPoint(map.getGpsLocation(), true);
            updateUi();
        }
    }

    private void onLongPress(MapPoint point) {
        map.appendPointToPoly(featureId, point);
        updateUi();
    }

    private void removeLastPoint() {
        if (featureId != -1) {
            map.removePolyLastPoint(featureId);
            updateUi();
        }
    }

    private void clear() {
        map.clearFeatures();
        featureId = map.addDraggablePoly(new ArrayList<>(), true);
        updateUi();
    }

    private void showClearDialog() {
        if (!map.getPolyPoints(featureId).isEmpty()) {
            new AlertDialog.Builder(this)
                .setMessage(R.string.geo_clear_warning)
                .setPositiveButton(R.string.clear, (dialog, id) -> clear())
                .setNegativeButton(R.string.cancel, null)
                .show();
        }
    }

    private void showBackDialog() {
        new AlertDialog.Builder(this)
            .setMessage(getString(R.string.geo_exit_warning))
            .setPositiveButton(R.string.discard, (dialog, id) -> finish())
            .setNegativeButton(R.string.cancel, null)
            .show();

    }

    private void finishWithResult() {
        List<MapPoint> points = map.getPolyPoints(featureId);

        // Allow an empty result (no points), or a polygon with at least
        // 3 points, but no degenerate 1-point or 2-point polygons.
        if (!points.isEmpty() && points.size() < 3) {
            ToastUtils.showShortToastInMiddle(getString(R.string.polygon_validator));
            return;
        }

        setResult(RESULT_OK, new Intent().putExtra(
            FormEntryActivity.GEOSHAPE_RESULTS, formatPoints(points)));
        finish();
    }

    /** Updates the state of various UI widgets to reflect internal state. */
    private void updateUi() {
        final int numPoints = map.getPolyPoints(featureId).size();
        final MapPoint location = map.getGpsLocation();

        zoomButton.setEnabled(location != null);
        backspaceButton.setEnabled(numPoints > 0);
        clearButton.setEnabled(numPoints > 0);
    }

    /**
     * Parses a form result string, as previously formatted by formatPoints,
     * into a list of polygon vertices.
     */
    private List<MapPoint> parsePoints(String coords) {
        List<MapPoint> points = new ArrayList<>();
        for (String vertex : (coords == null ? "" : coords).split(";")) {
            String[] words = vertex.trim().split(" ");
            if (words.length >= 2) {
                double lat;
                double lon;
                try {
                    lat = Double.parseDouble(words[0]);
                    lon = Double.parseDouble(words[1]);
                } catch (NumberFormatException e) {
                    continue;
                }
                points.add(new MapPoint(lat, lon));
            }
        }
        // Polygons are stored with a last point that duplicates the first
        // point.  To prepare the polygon for display and editing, we need
        // to remove this duplicate point.
        int count = points.size();
        if (count > 1 && points.get(0).equals(points.get(count - 1))) {
            points.remove(count - 1);
        }
        return points;
    }

    /**
     * Serializes a list of polygon vertices into a string, in the format
     * appropriate for storing as the result of this form question.
     */
    private String formatPoints(List<MapPoint> points) {
        // Polygons are stored with a last point that duplicates the
        // first point.  Add this extra point if it's not already present.
        if (points.size() > 1 && !points.get(0).equals(points.get(points.size() - 1))) {
            points.add(points.get(0));
        }
        String result = "";
        for (MapPoint point : points) {
            // TODO(ping): Remove excess precision when we're ready for the output to change.
            result += String.format(Locale.US, "%s %s 0.0 0.0;",
                Double.toString(point.lat), Double.toString(point.lon));
        }
        return result.trim();
    }

    @VisibleForTesting public boolean isZoomButtonEnabled() {
        return zoomButton != null && zoomButton.isEnabled();
    }

    @VisibleForTesting public MapFragment getMapFragment() {
        return map;
    }
}
