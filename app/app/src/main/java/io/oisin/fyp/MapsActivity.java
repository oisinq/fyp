package io.oisin.fyp;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.OnStreetViewPanoramaReadyCallback;
import com.google.android.gms.maps.StreetViewPanorama;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.SupportStreetViewPanoramaFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Dot;
import com.google.android.gms.maps.model.Gap;
import com.google.android.gms.maps.model.JointType;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PatternItem;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.RoundCap;
import com.google.android.gms.maps.model.StreetViewSource;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.model.RectangularBounds;
import com.google.android.libraries.places.widget.AutocompleteSupportFragment;
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.chip.Chip;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.maps.android.MarkerManager;
import com.google.maps.android.clustering.ClusterManager;
import com.google.maps.android.clustering.view.DefaultClusterRenderer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import info.hoang8f.android.segmented.SegmentedGroup;
import io.oisin.fyp.model.Direction;
import io.oisin.fyp.model.RouteType;

/**
 * This version of the map has clustering
 */
public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, ActivityCompat.OnRequestPermissionsResultCallback, GoogleMap.OnMarkerClickListener {

    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    private GoogleMap mMap;
    private StreetViewPanorama mStreetViewPanorama;
    private ClusterManager<StationClusterItem> mClusterManager;
    private Marker selectedStation;
    private RequestQueue queue;
    private Map<String, StationClusterItem> clusterItems = new HashMap<>();
    private Set<Polyline> routeLines = new HashSet<>();
    private RouteType quietestCycleRouteType;
    private RouteType fastestCycleRouteType;
    private RouteType shortestCycleRouteType;
    private StationClusterItem mRouteEndStation;
    private List<Marker> routeMarkers = new ArrayList<>();
    private RecyclerView recyclerView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        if (getIntent().hasExtra("feedbackSubmitted")) {
            Toast.makeText(getApplicationContext(),"Thanks for your feedback!",Toast. LENGTH_LONG).show();
        }

        queue = Volley.newRequestQueue(this);

        createNotificationChannel();
        setUpMapUI();
        setUpAutocomplete();
        setUpBottomSheet();
        setUpStationTypeButtons();
    }

    @Override
    public void onMapReady(final GoogleMap map) {
        map.setOnMarkerClickListener(this);

        mMap = map;

        mClusterManager = new ClusterManager<>(this, map);
        mClusterManager.setRenderer(new StationRenderer());
        map.setOnCameraIdleListener(mClusterManager);

        map.setMapStyle(new MapStyleOptions(getResources()
                .getString(R.string.map_style)));

        displayBikeStations();

        //todo: Move the camera view to O'Connell St – this should go to the user's location instead
        LatLng dublin = new LatLng(53.3498, -6.2603);
        map.moveCamera(CameraUpdateFactory.zoomTo(16f));
        map.moveCamera(CameraUpdateFactory.newLatLng(dublin));

        enableMyLocation();
    }

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Dbikes Planner";
            String description = "Plan your dbikes here!";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel("dbikes-planner", name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void setUpMapUI() {
        Button refreshButton = findViewById(R.id.refresh_icon);
        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                displayBikeStations();
            }
        });
    }

    private void setUpStationTypeButtons() {
        RadioGroup group = findViewById(R.id.station_type_segment_group);
        group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                MarkerManager.Collection markerCollection = mClusterManager.getMarkerCollection();

                for (Marker m : markerCollection.getMarkers()) {
                    if (m.getSnippet().contains("bikes available")) {
                        updateMarkerSnippet(m);
                    } else {
                        updateMarkerSnippet(m);
                    }
                }

                if (selectedStation != null) {
                    TextView text = findViewById(R.id.bottom_sheet_byline);
                    text.setText(selectedStation.getSnippet());
                }

                mClusterManager.cluster();
            }
        });
    }

    private void updateMarkerSnippet(Marker marker) {
        int totalSpaces = getTotalSpacesForMarker(marker);
        int availableBikes = getAvailableBikesForMarker(marker);
        int availableSpaces = getAvailableSpacesForMarker(marker);

        marker.setIcon(BitmapDescriptorFactory.fromResource(getIconResourceForStation(availableBikes, availableSpaces, checkClusterType())));
        marker.setSnippet(generateMarkerSnippet(availableBikes, totalSpaces));
    }

    private int getAvailableBikesForMarker(Marker marker) {
        String snippet = getStationStatus(marker);

        if (marker.getSnippet().contains(" bikes available")) {
            return Integer.parseInt(snippet.split(" out of ")[0]);
        } else {
            int totalSpaces = Integer.parseInt(snippet.split(" out of ")[1]);
            int availableSpaces = Integer.parseInt(snippet.split(" out of ")[0]);

            return totalSpaces - availableSpaces;
        }
    }

    private int getAvailableSpacesForMarker(Marker marker) {
        String stationStatus = getStationStatus(marker);

        if (marker.getSnippet().contains(" bikes available")) {
            int totalSpaces = Integer.parseInt(stationStatus.split(" out of ")[1]);

            int availableBikes = Integer.parseInt(stationStatus.split(" out of ")[0]);
            return totalSpaces - availableBikes;
        } else {
            return Integer.parseInt(stationStatus.split(" out of ")[0]);
        }
    }

    private int getTotalSpacesForMarker(Marker marker) {
        String stationStatus = getStationStatus(marker);
        return Integer.parseInt(stationStatus.split(" out of ")[1]);
    }

    /*
    This method takes a marker and returns a string representation of the status of a station
    For example, if a marker has 5 bikes out of 20 available, it returns "5 out of 20"
     */
    private String getStationStatus(Marker marker) {
        String splitter = marker.getSnippet().contains("bikes available") ? " bikes available" : " spaces available";
        return marker.getSnippet().split(splitter)[0];
    }

    private void setUpBottomSheet() {
        hideBottomSheet();

        SupportStreetViewPanoramaFragment streetViewPanoramaFragment =
                (SupportStreetViewPanoramaFragment)
                        getSupportFragmentManager().findFragmentById(R.id.streetviewpanorama);
        streetViewPanoramaFragment.getStreetViewPanoramaAsync(
                new OnStreetViewPanoramaReadyCallback() {
                    @Override
                    public void onStreetViewPanoramaReady(StreetViewPanorama panorama) {
                        mStreetViewPanorama = panorama;
                    }
                });
    }

    private void hideBottomSheet() {
        View bottomSheet = findViewById(R.id.route_bottom_sheet);
        BottomSheetBehavior bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
    }

    private void hideFloatingActionButton() {
        FloatingActionButton fab = findViewById(R.id.navigation_fab);
        fab.setVisibility(View.INVISIBLE);
    }

    private void setLoadingShimVisibibility(int visibility) {
        RelativeLayout shim = findViewById(R.id.route_loading_shim);
        shim.setVisibility(visibility);
    }

    private void setBlankShimVisibibility(int visibility) {
        RelativeLayout shim = findViewById(R.id.blank_loading_shim);
        shim.setVisibility(visibility);
    }

    private void showTimeDialog(final Place place) {
        final Calendar rightNow = Calendar.getInstance();

        final TimePickerDialog timePickerDialog = new TimePickerDialog(this,
                new TimePickerDialog.OnTimeSetListener() {

                    @Override
                    public void onTimeSet(TimePicker timePicker, int hour, int minute) {
                        Calendar selectedTime = Calendar.getInstance();
                        selectedTime.set(Calendar.HOUR_OF_DAY, hour);
                        selectedTime.set(Calendar.MINUTE, minute);
                        if (selectedTime.getTimeInMillis() >= rightNow.getTimeInMillis()) {
                            //it's after current
                            long milliseconds = selectedTime.getTimeInMillis() - rightNow.getTimeInMillis();
                            int minutes = (int) ((milliseconds/(1000*60))%60);

                            setBlankShimVisibibility(View.INVISIBLE);
                            showRoute(place, minutes);
                        } else {
                            Toast.makeText(getApplicationContext(), "You can't leave be in the past!", Toast.LENGTH_LONG).show();
                        }
                    }
                }, rightNow.get(Calendar.HOUR_OF_DAY), rightNow.get(Calendar.MINUTE), true);

        timePickerDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialogInterface) {
                setBlankShimVisibibility(View.INVISIBLE);
            }
        });

        timePickerDialog.setCanceledOnTouchOutside(false);
        timePickerDialog.show();
    }

    private void setUpAutocomplete() {
        if (!Places.isInitialized()) {
            Places.initialize(getApplicationContext(), getString(R.string.google_maps_key), Locale.ENGLISH);
        }

        AutocompleteSupportFragment autocompleteFragment = (AutocompleteSupportFragment)
                getSupportFragmentManager().findFragmentById(R.id.autocomplete_fragment);

        autocompleteFragment.setPlaceFields(Arrays.asList(Place.Field.NAME, Place.Field.ADDRESS, Place.Field.LAT_LNG));

        // Set the bounds of autocomplete to be Dublin city
        RectangularBounds bounds = RectangularBounds.newInstance(
                new LatLng(53.236989, -6.486053),
                new LatLng(53.445249, -6.016388));
        autocompleteFragment.setLocationRestriction(bounds);

        autocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(Place place) {
                setBlankShimVisibibility(View.VISIBLE);
                hideBottomSheet();

                showJourneyQuestionDialog(place);
                //showTimeDialog(place);
                //showMinutesDialog(place);
            }

            @Override
            public void onError(Status status) {
                Log.i(".MapsActivity", "An error occurred: " + status);
            }
        });

        findViewById(R.id.places_autocomplete_clear_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                EditText text = findViewById(R.id.places_autocomplete_search_input);
                text.setText(null);

                deleteRoute();

                hideBottomSheet();
                hideFloatingActionButton();

                SegmentedGroup group = findViewById(R.id.station_type_segment_group);
                group.setVisibility(View.VISIBLE);

                mRouteEndStation.setEndStation(false);
                mRouteEndStation.setSnippet(generateMarkerSnippet(mRouteEndStation.getAvailableBikes(),
                        mRouteEndStation.getAvailableBikes()));
            }
        });
    }

    private void showJourneyQuestionDialog(final Place place) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle("When are you leaving?");

        builder.setPositiveButton("Now", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                setBlankShimVisibibility(View.INVISIBLE);
                showRoute(place, 0);
            }
        });

        builder.setNegativeButton("Later", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User cancelled the dialog
                dialog.dismiss();
                showTimeDialog(place);
            }
        });

        builder.setNeutralButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User cancelled the dialog
                dialog.cancel();
                setBlankShimVisibibility(View.INVISIBLE);
            }
        });

        AlertDialog dialog = builder.create();

        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }

    private void showRoute(Place place, int minutes) {
        setLoadingShimVisibibility(View.VISIBLE);
        LatLng dublin = new LatLng(53.3499, -6.2603);

        mMap.animateCamera(CameraUpdateFactory.newLatLng(dublin));
        mMap.animateCamera(CameraUpdateFactory.zoomTo(13f));
        mClusterManager.clearItems();

        LocationManager locationManager = (LocationManager)
                getSystemService(Context.LOCATION_SERVICE);
        Criteria criteria = new Criteria();

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e("MapsActivity", "Location services must be activated.");
        }

        routeMarkers.add(mMap.addMarker(new MarkerOptions().position(new LatLng(53.330667, -6.258590))));
        routeMarkers.add(mMap.addMarker(new MarkerOptions().position(place.getLatLng())));

        String start = "53.330667,-6.258590";

        Location location = locationManager.getLastKnownLocation(Objects.requireNonNull(locationManager
                .getBestProvider(criteria, false)));
        //todo: This settings lets us use our real location. Not currently being used.
        // String start = + location.getLatitude() + "," + location.getLongitude();

        String end = place.getLatLng().latitude + "," + place.getLatLng().longitude;

        String url = "https://dbikes-planner.appspot.com/route?start=" + start + "&end="
                + end + "&minutes=" + minutes;

        StringRequest stringRequest = getRouteRequest(url);

        stringRequest.setRetryPolicy(new DefaultRetryPolicy( 50000, 5, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        queue.add(stringRequest);

        SegmentedGroup group = findViewById(R.id.station_type_segment_group);
        group.setVisibility(View.INVISIBLE);

        setUpJourneyTypeChips();
    }

    private void setUpJourneyTypeChips() {
        Chip fastestChip = findViewById(R.id.chip_fastest);

        fastestChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hideAllCycleRouteLines();

                updateUIForRouteType(fastestCycleRouteType);
            }
        });

        Chip quietestChip = findViewById(R.id.chip_quietest);

        quietestChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hideAllCycleRouteLines();

                updateUIForRouteType(quietestCycleRouteType);
            }
        });

        Chip shortestChip = findViewById(R.id.chip_shortest);

        shortestChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hideAllCycleRouteLines();

                updateUIForRouteType(shortestCycleRouteType);
            }
        });
    }

    private void updateUIForRouteType(RouteType routeType) {
        if (!routeLines.contains(routeType.getPolyline())) {
            setJourneyTitleText(routeType);
            setJourneySubtitleText(routeType);

            routeType.getPolyline().setVisible(true);
            routeLines.add(routeType.getPolyline());

            DirectionsAdapter adapter = new DirectionsAdapter(routeType.getDirections());
            recyclerView.setAdapter(adapter);
        }
    }

    //todo: These times don't take into account the walking distance currently.
    private void setJourneyTitleText(RouteType routeType) {
        TextView titleText = findViewById(R.id.route_bottom_sheet_title);

        if (routeType.getDistance() < 1000) {
            titleText.setText(Math.round(routeType.getDuration() / 60) + " mins (" + routeType.getDistance() + "m)");
        } else {
            titleText.setText(Math.round(routeType.getDuration() / 60) + " mins (" + Math.round(routeType.getDistance() / 100) / 10.0 + "km)");
        }
    }

    private void setJourneySubtitleText(RouteType routeType) {
        TextView titleText = findViewById(R.id.route_bottom_sheet_subtitle);

        titleText.setText(routeType.getCalories() + " calories burnt – " + routeType.getCo2saved() + "g of CO2 saved");
    }

    private void deleteRoute() {
        for (Polyline line : routeLines) {
            line.remove();
        }

        for (Marker marker : routeMarkers) {
            marker.remove();
        }

        routeMarkers.clear();
        routeLines.clear();
        mClusterManager.clearItems();

        mClusterManager.addItems(clusterItems.values());
        mClusterManager.cluster();
    }

    private void hideAllCycleRouteLines() {
        shortestCycleRouteType.getPolyline().setVisible(false);
        quietestCycleRouteType.getPolyline().setVisible(false);
        fastestCycleRouteType.getPolyline().setVisible(false);

        routeLines.remove(shortestCycleRouteType.getPolyline());
        routeLines.remove(quietestCycleRouteType.getPolyline());
        routeLines.remove(fastestCycleRouteType.getPolyline());
    }

    private StringRequest getRouteRequest(String url) {
        return new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        try {
                            for (Polyline line : routeLines) {
                                line.remove();
                            }

                            JSONObject route = new JSONObject(response);

                            parseRoute(route);

                            setUpInitialRouteUI(route);
                            setUpRouteBottomSheet(route);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e("RouteActivity", "error with route request");
            }
        });
    }

    private void setUpInitialRouteUI(JSONObject route) throws JSONException {
        // Displays the quietest route
        quietestCycleRouteType.getPolyline().setVisible(true);

        // Hides the shim
        setLoadingShimVisibibility(View.GONE);

        JSONObject startWalkingRoute = route.getJSONObject("start_walking_route");
        JSONObject endWalkingRoute = route.getJSONObject("end_walking_route");

        // Adds the quietest route lines
        routeLines.add(quietestCycleRouteType.getPolyline());
        routeLines.add(mMap.addPolyline(getWalkingRoute(startWalkingRoute)));
        routeLines.add(mMap.addPolyline(getWalkingRoute(endWalkingRoute)));

        // Adds the route stations to the map
        StationClusterItem startStation = clusterItems.get(route.getString("start_station"));
        startStation.setStartStation(true);
        StationClusterItem endStation = clusterItems.get(route.getString("end_station"));
        endStation.setEndStation(true);

        // Adds the appropriate stations to the map
        endStation.setSnippet((endStation.getTotalSpaces() - endStation.getAvailableBikes()) + " out of " + endStation.getTotalSpaces() + " spaces available");
        mRouteEndStation = endStation;
        mClusterManager.addItem(startStation);
        mClusterManager.addItem(endStation);
    }

    private void parseRoute(JSONObject route) throws JSONException {
        JSONObject quietestCycleRoute = route.getJSONObject("quietest_cycle_route");
        JSONObject fastestCycleRoute = route.getJSONObject("fastest_cycle_route");
        JSONObject shortestCycleRoute = route.getJSONObject("shortest_cycle_route");
        String startStationName = route.getString("start_station");
        String endStationName = route.getString("end_station");

        // Extracts the route types from the JSONObjects
        quietestCycleRouteType = extractRouteType(quietestCycleRoute, startStationName, endStationName);
        fastestCycleRouteType = extractRouteType(fastestCycleRoute, startStationName, endStationName);
        shortestCycleRouteType = extractRouteType(shortestCycleRoute, startStationName, endStationName);
    }

    private void setUpRouteBottomSheet(JSONObject route) {
        // Sets up the bottom sheet title
        setJourneyTitleText(quietestCycleRouteType);
        setJourneySubtitleText(quietestCycleRouteType);

        // Sets up the recycler view to display the directions
        DirectionsAdapter adapter = new DirectionsAdapter(quietestCycleRouteType.getDirections());
        recyclerView = findViewById(R.id.bottom_sheet_recycler);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(getApplicationContext()));

        View bottomSheet = findViewById(R.id.route_bottom_sheet);
        final BottomSheetBehavior bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        bottomSheetBehavior.setDraggable(false);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

        // Switches the bottom sheet to show route info
        findViewById(R.id.station_layout_contents).setVisibility(View.GONE);
        findViewById(R.id.route_layout_contents).setVisibility(View.VISIBLE);

        setUpNavigationFAB(bottomSheetBehavior, route);

        bottomSheet.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                LinearLayout layout = findViewById(R.id.route_layout_contents);
                bottomSheetBehavior.setDraggable(false);

                layout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                View hidden = layout.getChildAt(2);
                bottomSheetBehavior.setPeekHeight(hidden.getBottom());
            }
        });
    }

    private void setUpNavigationFAB(final BottomSheetBehavior bottomSheetBehavior,
                                    final JSONObject route) {
        FloatingActionButton navigationFab = findViewById(R.id.navigation_fab);
        navigationFab.setVisibility(View.VISIBLE);

        navigationFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                findViewById(R.id.station_layout_contents).setVisibility(View.GONE);
                findViewById(R.id.route_layout_contents).setVisibility(View.VISIBLE);
                bottomSheetBehavior.setDraggable(true);

                View bottomSheet = findViewById(R.id.route_bottom_sheet);

                BottomSheetBehavior bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);

                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);

                scheduleNotification(10000, (int)System.currentTimeMillis(), route);
            }

//            private void saveRouteAsFile(JSONObject route) throws JSONException, IOException {
//                String filename = route.getString("id") + ".json";
//
//                File file = new File(getApplicationContext().getFilesDir(), filename);
//
//                FileOutputStream stream = new FileOutputStream(file);
//                try {
//                    stream.write(route.toString().getBytes());
//                } finally {
//                    stream.close();
//                }
//            }
        });
    }




    public void scheduleNotification(long delay, int notificationId, JSONObject route) {//delay is after how much time(in millis) from current time you want to schedule the notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "dbikes-planner")
                .setContentTitle("Thanks for using our app!")
                .setContentText("Can you provide some feedback on how busy the station was?")
                .setSmallIcon(R.drawable.ic_my_icon)
                //.setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_MAX);

        Context context = this;

        Intent intent = new Intent(context, FeedbackActivity.class);
        intent.putExtra("route", route.toString());

        PendingIntent activity = PendingIntent.getActivity(context, notificationId, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        builder.setContentIntent(activity);

        Notification notification = builder.build();

        Intent notificationIntent = new Intent(context, MyNotificationPublisher.class);
        notificationIntent.putExtra(MyNotificationPublisher.NOTIFICATION_ID, notificationId);
        notificationIntent.putExtra(MyNotificationPublisher.NOTIFICATION, notification);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, notificationId, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        long futureInMillis = SystemClock.elapsedRealtime() + delay;
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, futureInMillis, pendingIntent);
    }

    private List<Direction> getDirectionsForRoute(JSONObject route, String startStation, String endStation) throws JSONException {
        List<Direction> directions = new ArrayList<>();

        JSONArray routeSteps = route.getJSONArray("marker");

        directions.add(new Direction("grab a bike", -1, -1, startStation));

        for (int i = 1; i < routeSteps.length(); i++) {
            JSONObject directionObj = routeSteps.getJSONObject(i).getJSONObject("@attributes");

            String streetName = directionObj.getString("name");
            String turn;

            if (directionObj.getString("turn").isEmpty()) {
                turn = "Straight on";
            } else {
                turn = directionObj.getString("turn");
            }

            double distance = directionObj.getDouble("distance");
            double time = directionObj.getDouble("time");

            directions.add(new Direction(turn, distance, time, streetName));
        }

        directions.add(new Direction("leave your bike", -1, -1, endStation));

        return directions;
    }

    private RouteType extractRouteType(JSONObject route, String startStation, String endStation) throws JSONException {
        JSONObject object = getAttributesOfCyclePath(route);

        double distance = Double.parseDouble(object.getString("length"));
        double duration = Double.parseDouble(object.getString("time"));
        double calories = Double.parseDouble(object.getString("calories"));
        double co2saved = Double.parseDouble(object.getString("grammesCO2saved"));

        Polyline line = mMap.addPolyline(getCycleRouteLine(route).visible(false));

        return new RouteType(line, distance, duration, calories, co2saved, getDirectionsForRoute(route, startStation, endStation));
    }

    private JSONObject getAttributesOfCyclePath(JSONObject route) throws JSONException {
        return route.getJSONArray("marker").getJSONObject(0).getJSONObject("@attributes");
    }

    private PolylineOptions getCycleRouteLine(JSONObject cycleRoute) throws JSONException {
        String[] coordinates = getAttributesOfCyclePath(cycleRoute).getString("coordinates").split(" ");
        PolylineOptions line = new PolylineOptions();

        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
        for (String coordinate : coordinates) {
            String longitude = coordinate.split(",")[0];
            String latitude = coordinate.split(",")[1];

            LatLng latLng = new LatLng(Double.parseDouble(latitude), Double.parseDouble(longitude));
            line.add(latLng);
            boundsBuilder.include(latLng);
        }

        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 300));

        return line.jointType(JointType.ROUND).endCap(new RoundCap()).startCap(new RoundCap()).color(Color.rgb(134, 122, 214));
    }

    private PolylineOptions getWalkingRoute(JSONObject walkingRoute) throws JSONException {
        JSONArray coordinates = walkingRoute.getJSONArray("features").getJSONObject(0)
                .getJSONObject("geometry").getJSONArray("coordinates");

        PolylineOptions line = new PolylineOptions();

        for (int i = 0; i < coordinates.length(); i++) {
            JSONArray array = coordinates.getJSONArray(i);

            String longitude = array.getString(0);
            String latitude = array.getString(1);

            line.add(new LatLng(Double.parseDouble(latitude), Double.parseDouble(longitude)));
        }

        List<PatternItem> pattern = Arrays.asList(new Dot(), new Gap(10));

        return line.pattern(pattern).jointType(JointType.ROUND).endCap(new RoundCap()).startCap(new RoundCap()).color(Color.rgb(134, 122, 214));
    }

    private int getIconResourceForStation(int availableBikes, int availableSpaces, String stationType) {

        if (stationType.equals("bikes")) {
            switch (availableBikes) {
                case 0:
                    return R.drawable.zero_bikes_medium;
                case 1:
                    return R.drawable.one_bike_medium;
                case 2:
                    return R.drawable.two_bikes_medium;
                case 3:
                    return R.drawable.three_bikes_medium;
                case 4:
                    return R.drawable.four_bikes_medium;
                case 5:
                    return R.drawable.five_bikes_medium;
                case 6:
                    return R.drawable.six_bikes_medium;
                case 7:
                    return R.drawable.seven_bikes_medium;
                case 8:
                    return R.drawable.eight_bikes_medium;
                default:
                    return R.drawable.nine_plus_bikes_medium;
            }
        } else {
            switch (availableSpaces) {
                case 0:
                    return R.drawable.zero_bikestands_marker;
                case 1:
                    return R.drawable.one_bikestand_marker;
                case 2:
                    return R.drawable.two_bikestands_marker;
                case 3:
                    return R.drawable.three_bikestands_marker;
                case 4:
                    return R.drawable.four_bikestands_marker;
                case 5:
                    return R.drawable.five_bikestands_marker;
                case 6:
                    return R.drawable.six_bikestands_marker;
                case 7:
                    return R.drawable.seven_bikestands_marker;
                case 8:
                    return R.drawable.eight_bikestands_marker;
                default:
                    return R.drawable.nine_plus_bikestands_marker;
            }
        }
    }

    private void displayBikeStations() {
        String url = "https://api.jcdecaux.com/vls/v1/stations?contract=dublin&apiKey=6e5c2a98e60a3336ecaede8f8c8688da25144692";

        // Request a string response from the provided URL.
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        mMap.clear();
                        mClusterManager.clearItems();

                        try {
                            JSONArray stations = new JSONArray(response);
                            List<StationClusterItem> items = new ArrayList<>();

                            for (int i = 0; i < stations.length(); i++) {
                                StationClusterItem item = parseStationClusterItem(stations.getJSONObject(i));
                                items.add(item);

                                clusterItems.put(item.getTitle(), item);
                            }

                            mClusterManager.addItems(items);
                            mClusterManager.cluster();
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
            }
        });

        queue.add(stringRequest);
    }

    private StationClusterItem parseStationClusterItem(JSONObject station) throws JSONException {
        return new StationClusterItem(station.getJSONObject("position").getDouble("lat"),
                station.getJSONObject("position").getDouble("lng"),
                station.getString("address"),
                generateMarkerSnippet(station.getInt("available_bikes"),
                        station.getInt("bike_stands")),
                station.getInt("available_bikes"),
                station.getInt("bike_stands"));
    }

    private String generateMarkerSnippet(int availableBikes, int totalBikeStands) {
        if (checkClusterType().equals("bikes")) {
            return availableBikes + " out of " + totalBikeStands + " bikes available";
        } else {
            return (totalBikeStands - availableBikes) + " out of " + totalBikeStands + " spaces available";
        }
    }

    private String checkClusterType() {
        RadioButton bikesButton = findViewById(R.id.bikes_radio_button);

        if (bikesButton.isChecked()) {
            return "bikes";
        } else {
            return "bikestands";
        }
    }

    private void enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(true);
            mMap.getUiSettings().setMapToolbarEnabled(false);
        } else {
            //todo: Show rationale and request permission.
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION) {
            if (permissions.length == 1 &&
                    permissions[0].equals(Manifest.permission.ACCESS_FINE_LOCATION) &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                mMap.setMyLocationEnabled(true);
                mMap.getUiSettings().setMyLocationButtonEnabled(true);
                mMap.getUiSettings().setMapToolbarEnabled(true);
            } else {
                //todo Permission was denied. Display an error message.
            }
        }
    }

    @Override
    public boolean onMarkerClick(final Marker marker) {
        if (marker.getTitle() == null) return false;

        String url = marker.getTitle().contains("/") ? "https://dbikes-planner." +
                "appspot.com/history/Princes Street" :
                "https://dbikes-planner.appspot.com/history/" + marker.getTitle();

        clearGraph();

        // Request a string response from the provided URL.
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        try {
                            JSONArray graphPoints = new JSONObject(response).getJSONArray("graph");

                            setUpGraph(graphPoints, marker);

                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
            }
        });

        queue.add(stringRequest);

        findViewById(R.id.route_layout_contents).setVisibility(View.GONE);
        findViewById(R.id.station_layout_contents).setVisibility(View.VISIBLE);

        View bottomSheet = findViewById(R.id.route_bottom_sheet);
        BottomSheetBehavior bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        bottomSheetBehavior.setDraggable(true);

        TextView title = findViewById(R.id.bottom_sheet_title);
        title.setText(marker.getTitle());

        TextView byline = findViewById(R.id.bottom_sheet_byline);
        byline.setText(marker.getSnippet());

        CameraUpdate yourLocation = CameraUpdateFactory.newLatLngZoom(marker.getPosition(), mMap.getCameraPosition().zoom);
        mMap.animateCamera(yourLocation, 300, null);

        mStreetViewPanorama.setPosition(marker.getPosition(), StreetViewSource.OUTDOOR);
        mStreetViewPanorama.setStreetNamesEnabled(false);
        mStreetViewPanorama.setUserNavigationEnabled(false);

        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

        selectedStation = marker;

        return true;
    }

    private void clearGraph() {
        LineChart chart = findViewById(R.id.availability_chart);
        chart.clear();
    }

    private void setUpGraph(JSONArray points, Marker marker) throws JSONException {
        LineChart chart = findViewById(R.id.availability_chart);

        chart.setBackgroundColor(Color.WHITE);
        chart.setDescription(null);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.TOP_INSIDE);
        xAxis.setTextSize(10f);
        xAxis.setTextColor(Color.WHITE);
        xAxis.setDrawAxisLine(false);
        xAxis.setDrawGridLines(true);
        xAxis.setTextColor(Color.rgb(255, 192, 56));
        xAxis.setGranularity(1f); // one hour
        xAxis.setValueFormatter(new ValueFormatter() {

            private final SimpleDateFormat mFormat = new SimpleDateFormat("h aa", Locale.ENGLISH);

            @Override
            public String getFormattedValue(float value) {

                return mFormat.format(new Date((long) (value * 1000)));
            }
        });
        xAxis.setLabelCount(11, true);

        YAxis yAxis = chart.getAxisLeft();
        yAxis.setPosition(YAxis.YAxisLabelPosition.INSIDE_CHART);
        yAxis.setTextColor(ColorTemplate.getHoloBlue());
        yAxis.setDrawGridLines(true);
        yAxis.setGranularityEnabled(true);
        yAxis.setYOffset(-9f);
        yAxis.setTextColor(Color.rgb(255, 192, 56));

        yAxis.setAxisMinimum(0);
        yAxis.setAxisMaximum(getMarkerTotalSpaces(marker));

        YAxis rightAxis = chart.getAxisRight();
        rightAxis.setEnabled(false);

        ArrayList<Entry> values = new ArrayList<>();

        for (int i = 0; i < points.length(); i++) {
            JSONArray currentPoints = points.getJSONArray(i);

            values.add(new Entry((float) currentPoints.getDouble(0), (float) currentPoints.getDouble(1)));
        }

        LineDataSet set1 = new LineDataSet(values, "Bike availability");
        set1.setAxisDependency(YAxis.AxisDependency.LEFT);
        set1.setColor(ColorTemplate.getHoloBlue());
        set1.setValueTextColor(ColorTemplate.getHoloBlue());
        set1.setLineWidth(1.5f);
        set1.setDrawCircles(false);
        set1.setDrawValues(false);
        set1.setFillAlpha(65);
        set1.setFillColor(ColorTemplate.getHoloBlue());
        set1.setHighLightColor(Color.rgb(244, 117, 117));
        set1.setDrawCircleHole(false);
        set1.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        LineData lineData = new LineData(set1);
        chart.setData(lineData);
        chart.invalidate();
    }

    private int getMarkerTotalSpaces(Marker marker) {
        String splitter = marker.getSnippet().contains("bikes available") ? " bikes available" : " spaces available";
        String snippet = marker.getSnippet().split(splitter)[0];
        return Integer.parseInt(snippet.split(" out of ")[1]);
    }

    private class StationRenderer extends DefaultClusterRenderer<StationClusterItem> {

        public StationRenderer() {
            super(getApplicationContext(), mMap, mClusterManager);
        }

        @Override
        protected void onBeforeClusterItemRendered(StationClusterItem item, MarkerOptions markerOptions) {
            //Todo: with some refactoring, this logic can be combined with how Markers are rendered
            String splitter;
            if (item.getSnippet().contains("bikes available")) {
                splitter = " bikes available";
            } else {
                splitter = " spaces available";
            }

            int totalSpaces = Integer.parseInt(item.getSnippet().split(splitter)[0].split(" out of ")[1]);
            String snippet = item.getSnippet();
            int availableBikes;
            int availableSpaces;

            if (splitter.equals(" bikes available")) {
                availableBikes = Integer.parseInt(snippet.split(" out of ")[0]);
                availableSpaces = totalSpaces - availableBikes;
            } else {
                availableSpaces = Integer.parseInt(snippet.split(" out of ")[0]);
                availableBikes = totalSpaces - availableSpaces;
            }

            String stationType;

            if (item.isStartStation()) {
                stationType = "bikes";
            } else if (item.isEndStation()) {
                stationType = "bikestands";
            } else {
                stationType = checkClusterType();
            }

            markerOptions.icon(BitmapDescriptorFactory.fromResource(getIconResourceForStation(availableBikes, availableSpaces, stationType)));

            super.onBeforeClusterItemRendered(item, markerOptions);
        }
    }
}
