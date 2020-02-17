package io.oisin.fyp;

import androidx.fragment.app.FragmentActivity;

import android.os.Bundle;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.maps.android.clustering.ClusterItem;
import com.google.maps.android.clustering.ClusterManager;
import com.google.maps.android.clustering.view.DefaultClusterRenderer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * This version of the map has clustering
 */
public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private ClusterManager<MyItem> mClusterManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    /**
     * Draws profile photos inside markers (using IconGenerator).
     * When there are multiple people in the cluster, draw multiple photos (using MultiDrawable).
     */
    private class StationRenderer extends DefaultClusterRenderer {

        public StationRenderer() {
            super(getApplicationContext(), getMap(), mClusterManager);
        }

        @Override
        protected void onBeforeClusterItemRendered(ClusterItem item, MarkerOptions markerOptions) {
            int availableBikes = Integer.parseInt(item.getSnippet().split(" bikes available")[0].split(" out of ")[0]);

            markerOptions.icon(BitmapDescriptorFactory.fromResource(getResourceForAvailableBikes(availableBikes)));

            super.onBeforeClusterItemRendered(item, markerOptions);
        }
    }


    private int getResourceForAvailableBikes(int availableBikes) {

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
    }

    @Override
    public void onMapReady(final GoogleMap map) {
        mMap = map;
        mClusterManager = new ClusterManager<>(this, map);
        mClusterManager.setRenderer(new StationRenderer());
        map.setOnCameraIdleListener(mClusterManager);


        map.setMapStyle(new MapStyleOptions(getResources()
                .getString(R.string.map_style)));

        // Instantiate the RequestQueue.
        RequestQueue queue = Volley.newRequestQueue(this);
        String url = "https://api.jcdecaux.com/vls/v1/stations?contract=dublin&apiKey=6e5c2a98e60a3336ecaede8f8c8688da25144692";

        // Request a string response from the provided URL.
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        try {
                            JSONArray stations = new JSONArray(response);
                            List<MyItem> items = new ArrayList<>();

                            for (int i = 0; i < stations.length(); i++) {
                                JSONObject station = stations.getJSONObject(i);
                                items.add(new MyItem(station.getJSONObject("position").getDouble("lat"),
                                        station.getJSONObject("position").getDouble("lng"),
                                        station.getString("address"),
                                        station.getInt("available_bikes") + " out of " + station.getInt("bike_stands") + " bikes available"));
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

        // Add a marker in Sydney and move the camera
        LatLng dublin = new LatLng(53.3498, -6.2603);
        map.moveCamera(CameraUpdateFactory.zoomTo(16f));
        map.moveCamera(CameraUpdateFactory.newLatLng(dublin));
    }

    public GoogleMap getMap() {
        return mMap;
    }
}
