package com.davidmascharka.lips;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.NavUtils;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.mascharka.indoorlocalization.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import weka.classifiers.functions.RBFRegressor;
import weka.classifiers.lazy.KStar;
import weka.classifiers.trees.RandomForest;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;

/**
 *  Copyright 2015 David Mascharka
 * 
 * This file is part of LIPS (Learning-based Indoor Positioning System).
 *
 *  LIPS is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  any later version.
 *
 *  LIPS is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with LIPS.  If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * @author David Mascharka (david.mascharka@drake.edu)
 * 
 * Tracking side of the application. This is what would be implemented as part of an
 * enterprise-scale application that actually wanted to perform live localization.
 * 
 * Most of this is unnecessary in an application that just wants to keep track of user
 * position. Take out the buttons, the view, etc. The only part we really care about
 * in getting user position is the sensor values, the attributes/instances,
 * and the updateScanResults method
 *
 * It may be desirable to break this logic off to run in a service so that
 * localization can be performed in the background so the user experiences
 * no delay on re-opening the application or to notify the user when
 * they reach an area of interest in, for example, navigation in a mall
 *
 */

/*
* Last Updated: April 23, 2016
* author: Mahesh Gaya
* Description:
* --added M permission implementations
* --added some TODOs for buildings
* --enabled home button (arrow on top left of Tracker Activity
 */
public class TrackerActivity extends AppCompatActivity implements SensorEventListener,
SelectPartitionDialogFragment.SelectPartitionDialogListener,
		ActivityCompat.OnRequestPermissionsResultCallback{
	//TODO: Need to actually test the app in Marshmallow
	//@author Mahesh Gaya added this tag for debugging purposes
	private String TAG = "Permission Test (TrackerActivity): ";
    //private static final int MY_PERMISSIONS = 13;
	//@author Mahesh Gaya added these for permissions
	private static final int REQUEST_LOCATION = 110;
	private static final int REQUEST_WRITE_STORAGE = 112;
	private static final int REQUEST_WIFI = 114;
	
	/**
	 * The accelerometer reading in the X direction
	 */
	private float accelerometerX;

	/**
	 * The accelerometer reading in the Y direction
	 */
	private float accelerometerY;

	/**
	 * The accelerometer reading in the Z direction
	 */
	private float accelerometerZ;

	/**
	 * Reading from the X direction of the magnetic sensor
	 */
	private float magneticX;

	/**
	 * Reading from the Y direction of the magnetic sensor
	 */
	private float magneticY;

	/**
	 * Reading from the Z direction of the magnetic sensor
	 */
	private float magneticZ;

	/**
	 * Reading of the intensity from the light sensor
	 */
	private float light;

	/**
	 * Amount of rotation in the X direction
	 */
	private float rotationX;

	/**
	 * Amount of rotation in the Y direction
	 */
	private float rotationY;

	/**
	 * Amount of rotation in the Z direction
	 */
	private float rotationZ;

	/**
	 * Array holding rotation values - for querying
	 */
	private float[] rotation;

	/**
	 * Array holding inclination values - for querying
	 */
	private float[] inclination;

	/**
	 * Array holding orientation values - for querying
	 */
	private float[] orientation;

	/**
	 * Displays the user's x coordinate (predicted)
	 */
	private TextView xText;

	/**
	 * Displays the user's y coordinate (predicted)
	 */
	private TextView yText;

	/**
	 * Overlays a grid so the user can more easily see where they are
	 */
	private GridView grid;

	/**
	 * Lets us query the sensors in the device
	 */
	private SensorManager sensorManager;

	/**
	 * Holds what sensors are embedded in the device
	 */
	private List<Sensor> sensorList;

	/**
	 * Lets us actively scan for WiFi signals
	 */
	private WifiManager wifiManager;

	/**
	 * Holds the results of an active scan for WiFi signals
	 */
	private List<ScanResult> scanResults;

	/**
	 * Holds each BSSID of interest and its signal strength
	 */
	private LinkedHashMap<String, Integer> wifiReadings;

	/**
	 * Lets us set up a listener for location changes
	 */
	private LocationManager locationManager;

	/**
	 * Listens for changes to reported location
	 */
	private LocationListener locationListener;

	/**
	 * The user's current location, as reported by GPS/Network
	 */
	private Location location;

	/**
	 * Worker thread for performing localization
	 */
	Thread t;

	/**
	 * Will listen for broadcasts from the WiFi manager. When a scan has finished, the
	 *onReceive method will be called which will recalculate the user's position
	 */
	BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			updateScanResults();
		}
	};

	/**
	 * Which building is the user in?
	 */
	private String building;

	/**
	 * X coordinate to display to the user to walk to (for evaluating)
	 */
	private float nextX = 0.0f;

	/**
	 * Y coordinate to display to the user to walk to (for evaluating)
	 */
	private float nextY = 0.0f;

	/**
	 * Predicted X position of the user
	 */
	private float predictedX;

	/**
	 * Predicted Y position of the user
	 */
	private float predictedY;

	private Timestamp time;
	
	File file;
	File valuesFile;
	FileOutputStream outputStream;
	FileOutputStream valuesOutputStream;
	PrintWriter writer;
	PrintWriter valuesWriter;

//	private double predictedPartition;

	/**
	 * K* classifier for predicting x position
	 */
	KStar classifierXKStar;

	/**
	 * RBF regression classifier for predicting x position
	 */
	RBFRegressor classifierXRBFRegressor;

	/**
	 * K* classifier for predicting y position
	 */
	KStar classifierYKStar;

	/**
	 * RBF regression classifier for predicting y position
	 */
	RBFRegressor classifierYRBFRegressor;

	/**
	 * K* classifier for just the lower left portion of the building, predicting x
	 */
//	KStar partitionLowerLeftX = null;
//
//	/**
//	 * K* classifier for just the lower right portion of the building, predicting x
//	 */
//	KStar partitionLowerRightX = null;
//
//	/**
//	 * K* classifier for just the upper left portion of the building, predicting x
//	 */
//	KStar partitionUpperLeftX = null;
//
//	/**
//	 * K* classifier for just the upper right portion of the building, predicting x
//	 */
//	KStar partitionUpperRightX = null;
//
//	/**
//	 * K* classifier for just the middle portion of the building, predicting x
//	 */
//	KStar partitionMiddleX = null;
//
//	/**
//	 * K* classifier for just the lower left portion of the building, predicting y
//	 */
//	KStar partitionLowerLeftY = null;
//
//	/**
//	 * K* classifier for just the lower right portion of the building, predicting y
//	 */
//	KStar partitionLowerRightY = null;
//
//	/**
//	 * K* classifier for just the upper left portion of the building, predicting y
//	 */
//	KStar partitionUpperLeftY = null;
//
//	/**
//	 * K* classifier for just the upper right portion of the building, predicting y
//	 */
//	KStar partitionUpperRightY = null;
//
//	/**
//	 * K* classifier for just the middle portion of the building, predicting y
//	 */
//	KStar partitionMiddleY = null;
//
//	/**
//	 * Random forest model to predict which portion of the building the user is in
//	 * Determines if the user is in upper left, lower left, upper right, lower right, middle
//	 */
//	RandomForest partitionClassifier;

	/**
	 * Instance for classifying X position
	 */
	Instances xInstances;

	/**
	 * Instance for classifying Y position
	 */
	Instances yInstances;

	/**
	 * Instance for classifying partition
	 */
//	Instances partitionInstances;

	// List of attributes we use for prediction
	// TODO: make this not be ugly
	Attribute attrAccelX = new Attribute("accelerometerX");
	Attribute attrAccelY = new Attribute("accelerometerY");
	Attribute attrAccelZ = new Attribute("accelerometerZ");
	Attribute attrMagneticX = new Attribute("magneticX");
	Attribute attrMagneticY = new Attribute("magneticY");
	Attribute attrMagneticZ = new Attribute("magneticZ");
	Attribute attrLight = new Attribute("light");
	Attribute attrRotationX = new Attribute("rotationX");
	Attribute attrRotationY = new Attribute("rotationY");
	Attribute attrRotationZ = new Attribute("rotationZ");
	Attribute attrOrientationX = new Attribute("orientationX");
	Attribute attrOrientationY = new Attribute("orientationY");
	Attribute attrOrientationZ = new Attribute("orientationZ");
	Attribute attrBSSID1 = new Attribute("BSSID1");
	Attribute attrBSSID2 = new Attribute("BSSID2");
	Attribute attrBSSID3 = new Attribute("BSSID3");
	Attribute attrBSSID4 = new Attribute("BSSID4");
	Attribute attrBSSID5 = new Attribute("BSSID5");
	Attribute attrBSSID6 = new Attribute("BSSID6");
	Attribute attrBSSID7 = new Attribute("BSSID7");
	Attribute attrBSSID8 = new Attribute("BSSID8");
	Attribute attrBSSID9 = new Attribute("BSSID9");
	Attribute attrBSSID10 = new Attribute("BSSID10");
	//ramadanDO: we use no more than 10 bssids

	Attribute attrLatitude = new Attribute("latitude");
	Attribute attrLongitude = new Attribute("longitude");
	Attribute attrLocationAccuracy = new Attribute("locationAccuracy");
	Attribute attrXPosition = new Attribute("xPosition");
	Attribute attrYPosition = new Attribute("yPosition");
	ArrayList<Attribute> xClass = new ArrayList<Attribute>(173);
	ArrayList<Attribute> yClass = new ArrayList<Attribute>(173);

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		//allow users to go back to Main Activity
		//TODO: test if this works on Marshmallow
		if (Build.VERSION.SDK_INT >= 23){
			getActionBar().setDisplayHomeAsUpEnabled(true);
			getActionBar().setDisplayShowHomeEnabled(true);
		} else {
			getSupportActionBar().setDisplayHomeAsUpEnabled(true);
			getSupportActionBar().setDisplayShowHomeEnabled(true);
		}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestMyPermissions();
        }

		setContentView(R.layout.activity_tracker);

		if (savedInstanceState == null) {
			getSupportFragmentManager().beginTransaction()
			.add(R.id.container, new TrackerFragment()).commit();
		}

		rotation = new float[9];
		inclination = new float[9];
		orientation = new float[3];

		sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

		locationListener = new LocationListener() {

			@Override
			public void onLocationChanged(Location location) {
				updateLocation(location);
			}

			@Override
			public void onStatusChanged(String provider, int status,
					Bundle extras) {}

			@Override
			public void onProviderEnabled(String provider) {}

			@Override
			public void onProviderDisabled(String provider) {}
		};

		loadXClassifierModels();
		loadYClassifierModels();
//		loadPartitionClassifierModels();

		setUpXInstances();
		setUpYInstances();
//		setUpPartitionInstances();

		wifiReadings = new LinkedHashMap<String, Integer>();
		// Set grid options
		GridView grid = (GridView) findViewById(R.id.tracker_gridView);
		//grid.setGridSize(roomWidth, roomLength);
		grid.setGridSize(102, 64);
		grid.setCatchInput(false);
		//grid.setDisplayMap(displayMap)
		t = new Thread();
		t.start();

		//load5PartitionClassifiers = new Thread();
		//load5PartitionClassifiers.start();
	}


    private void requestMyPermissions() {
		//@author Mahesh Gaya added new permission statement
		if (ActivityCompat.shouldShowRequestPermissionRationale(this,
				Manifest.permission.ACCESS_FINE_LOCATION)
				|| ActivityCompat.shouldShowRequestPermissionRationale(this,
				Manifest.permission.ACCESS_WIFI_STATE)
				|| ActivityCompat.shouldShowRequestPermissionRationale(this,
				Manifest.permission.CHANGE_WIFI_STATE)
				|| ActivityCompat.shouldShowRequestPermissionRationale(this,
				Manifest.permission.WRITE_EXTERNAL_STORAGE)){
			Toast.makeText(this, "GPS, WIFI, and Storage permissions are required for this app.", Toast.LENGTH_LONG).show();
		} else {
			ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION );
			ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_WIFI_STATE,
					Manifest.permission.CHANGE_WIFI_STATE}, REQUEST_WIFI);
			ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_STORAGE);
		}
    }
	// TODO: make pretty (i.e. hide this in another class)
	/**
	 * Adds the attributes to the x classification, sets up the xInstance to
	 * allow the classifier to predict x position from these attributes
	 */
	private void setUpXInstances() {
		xClass.add(attrAccelX);
		xClass.add(attrAccelY);
		xClass.add(attrAccelZ);
		xClass.add(attrMagneticX);
		xClass.add(attrMagneticY);
		xClass.add(attrMagneticZ);
		xClass.add(attrLight);
		xClass.add(attrRotationX);
		xClass.add(attrRotationY);
		xClass.add(attrRotationZ);
		xClass.add(attrOrientationX);
		xClass.add(attrOrientationY);
		xClass.add(attrOrientationZ);
		xClass.add(attrBSSID1);
		xClass.add(attrBSSID2);
		xClass.add(attrBSSID3);
		xClass.add(attrBSSID4);
		xClass.add(attrBSSID5);
		xClass.add(attrBSSID6);
		xClass.add(attrBSSID7);
		xClass.add(attrBSSID8);
		xClass.add(attrBSSID9);
		xClass.add(attrBSSID10);
		xClass.add(attrLatitude);
		xClass.add(attrLongitude);
		xClass.add(attrLocationAccuracy);
		xClass.add(attrXPosition);

		xInstances = new Instances("xPos", xClass, 1);
		xInstances.setClassIndex(172);
		xInstances.add(new DenseInstance(173));
	}

	// TODO this one too
	/**
	 * Adds the attributes to the y classification, sets up the yInstance to
	 * allow the classifier to predict y position from these attributes
	 */
	private void setUpYInstances() {
		yClass.add(attrAccelX);
		yClass.add(attrAccelY);
		yClass.add(attrAccelZ);
		yClass.add(attrMagneticX);
		yClass.add(attrMagneticY);
		yClass.add(attrMagneticZ);
		yClass.add(attrLight);
		yClass.add(attrRotationX);
		yClass.add(attrRotationY);
		yClass.add(attrRotationZ);
		yClass.add(attrOrientationX);
		yClass.add(attrOrientationY);
		yClass.add(attrOrientationZ);
		yClass.add(attrBSSID1);
		yClass.add(attrBSSID2);
		yClass.add(attrBSSID3);
		yClass.add(attrBSSID4);
		yClass.add(attrBSSID5);
		yClass.add(attrBSSID6);
		yClass.add(attrBSSID7);
		yClass.add(attrBSSID8);
		yClass.add(attrBSSID9);
		yClass.add(attrBSSID10);
		yClass.add(attrLatitude);
		yClass.add(attrLongitude);
		yClass.add(attrLocationAccuracy);
		yClass.add(attrYPosition);

		yInstances = new Instances("yPos", yClass, 1);
		yInstances.setClassIndex(172);
		yInstances.add(new DenseInstance(173));
	}

	// TODO and this one
	/**
	 * Adds the attributes to the partition classification, set partition up to
	 * allow the classifier to predict partition from these attributes
	 */

	@Override
	public void onResume() {
		super.onResume();

		File root = Environment.getExternalStorageDirectory();
		File dir = new File(root.getAbsolutePath() + "/indoor_localization");
		dir.mkdirs();
		file = new File(dir, "livetest_" + building + ".txt");
		valuesFile = new File(dir, "livetest_" + building + "_values.txt");
		try {
			outputStream = new FileOutputStream(file, true);
			valuesOutputStream = new FileOutputStream(valuesFile, true);
			writer = new PrintWriter(outputStream);
			valuesWriter = new PrintWriter(valuesOutputStream);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		// Set building textview to the building the user has selected
		//TextView buildingText = (TextView) findViewById(R.id.text_building);
		//buildingText.setText("Building: " + building);

		// Set room size textview to the room size the user has selected
		//TextView roomSizeText = (TextView) findViewById(R.id.text_room_size);
		//roomSizeText.setText("Room size: " + roomWidth + " x " + roomLength);

		// Set grid options
		GridView grid = (GridView) findViewById(R.id.tracker_gridView);
		//grid.setGridSize(roomWidth, roomLength);
		grid.setGridSize(102, 64);
		grid.setCatchInput(false);
		//grid.setDisplayMap(displayMap);

		// Register to get sensor updates from all the available sensors
		sensorList = sensorManager.getSensorList(Sensor.TYPE_ALL);
		for (Sensor sensor : sensorList) {
			sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST);
		}

		// Enable wifi if it is disabled
		if (!wifiManager.isWifiEnabled()) {
			Toast.makeText(this, "WiFi not enabled. Enabling...", Toast.LENGTH_SHORT).show();
			wifiManager.setWifiEnabled(true);
		}


            // Request location updates from gps and the network
		//@author Mahesh Gaya added permission if-statment
		if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
				!= PackageManager.PERMISSION_GRANTED
				|| ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_WIFI_STATE)
				!= PackageManager.PERMISSION_GRANTED
				|| ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CHANGE_WIFI_STATE)
				!= PackageManager.PERMISSION_GRANTED
				|| ActivityCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
				!= PackageManager.PERMISSION_GRANTED) {
			Log.i(TAG, "Permissions have NOT been granted. Requesting permissions.");
			requestMyPermissions();
		} else  {
			Log.i(TAG, "Permissions have already been granted. Getting location from GPS and Network");
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                    0, 0, locationListener);
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                    0, 0, locationListener);
        }

		registerReceiver(receiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

		wifiManager.startScan();
		Toast.makeText(this, "Initiated scan", Toast.LENGTH_SHORT).show();	

		xText = (TextView) findViewById(R.id.tracker_text_xcoord);
		yText = (TextView) findViewById(R.id.tracker_text_ycoord);
	}

	@Override
	public void onPause() {
		// Stop receiving updates
		sensorManager.unregisterListener(this);

		//@author Mahesh Gaya added permission if-statment
		if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
				!= PackageManager.PERMISSION_GRANTED
				|| ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_WIFI_STATE)
				!= PackageManager.PERMISSION_GRANTED
				|| ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CHANGE_WIFI_STATE)
				!= PackageManager.PERMISSION_GRANTED
				|| ActivityCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
				!= PackageManager.PERMISSION_GRANTED) {
			Log.i(TAG, "Permissions have NOT been granted. Requesting permissions.");
			requestMyPermissions();
		} else  {
			Log.i(TAG, "Permissions have already been granted. Removing Updates.");
            locationManager.removeUpdates(locationListener);
        }
		unregisterReceiver(receiver);
		
		writer.close();
		valuesWriter.close();

		//savePreferences();

		super.onPause();
	}

	/**
	 * Helper method to keep track of the most up-to-date location
	 */
	private void updateLocation(Location location) {
		this.location = location;
	}

	/**
	 * When a new WiFi scan comes in, get sensor values and predict position
	 */
	private void updateScanResults() {
		resetWifiReadings();

		scanResults = wifiManager.getScanResults();

		// Start another scan to recalculate user position
		wifiManager.startScan();
		
		time = new Timestamp(System.currentTimeMillis());
		
		for (ScanResult result : scanResults) {
			if (wifiReadings.get(result.BSSID) != null) {
				wifiReadings.put(result.BSSID, result.level);
			} // else BSSID wasn't programmed in
		}
	//@author Mahesh Gaya added permission if-statment
		if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
				!= PackageManager.PERMISSION_GRANTED
				|| ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_WIFI_STATE)
				!= PackageManager.PERMISSION_GRANTED
				|| ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CHANGE_WIFI_STATE)
				!= PackageManager.PERMISSION_GRANTED
				|| ActivityCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
				!= PackageManager.PERMISSION_GRANTED) {
			Log.i(TAG, "Permissions have NOT been granted. Requesting permissions.");
			requestMyPermissions();
		} else {
			Log.i(TAG, "Permissions have already been granted. Getting last known location from GPS and Network");
			if (location == null) {
				location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
			}
			if (location == null) {
				locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
			}
		}
		setInstanceValues();
		
		printValues();

		// this is where the magic happens
		// TODO clean up
		if (!t.isAlive()) {
			t = new Thread(new Runnable() {
				public void run() {
					Timestamp myTime = time;
					try{
						predictedX = (float) classifierXKStar.classifyInstance(xInstances.get(0));
					} catch (Exception e){
						Log.e(TAG, Log.getStackTraceString(e));
					}
					try{
						predictedY = (float) classifierYKStar.classifyInstance(yInstances.get(0));
					} catch (Exception e){
						Log.e(TAG, Log.getStackTraceString(e));
					}
					// This doesn't do anything -> classifierXKStar is null -> not loaded
					/*try {
						predictedX = (float) classifierXRBFRegressor.classifyInstance(xInstances.get(0));
					} catch (Exception e) {
						e.printStackTrace();
					}
					// Likewise, doesn't happen
					try {
						predictedY = (float) classifierYRBFRegressor.classifyInstance(yInstances.get(0));
					} catch (Exception e) {
						e.printStackTrace();
					}*/

					// Get the partition that the new instance is in
					// Use the classifier of the predicted partition to predict an x and y value for
					// the new instance if the classifier is loaded (not null)
//					try {
//						predictedPartition = partitionClassifier.classifyInstance(partitionInstances.get(0));
//						//double[] dist = partitionClassifier.distributionForInstance(partitionInstances.get(0)); // gets the probability distribution for the instance
//					} catch (Exception e) {
//						e.printStackTrace();
//					}
//
//					String partitionString = partitionInstances.classAttribute().value((int) predictedPartition);
//					if (partitionString.equals("upperleft")) {
//						if (partitionUpperLeftX != null) {
//							try {
//								predictedX = (float) partitionUpperLeftX.classifyInstance(xInstances.get(0));
//							} catch (Exception e) {
//								e.printStackTrace();
//							}
//						}
//						if (partitionUpperLeftY != null) {
//							try {
//								predictedY = (float) partitionUpperLeftY.classifyInstance(yInstances.get(0));
//							} catch (Exception e) {
//								e.printStackTrace();
//							}
//						}
//					} else if (partitionString.equals("upperright")) {
//						if (partitionUpperRightX != null) {
//							try {
//								predictedX = (float) partitionUpperRightX.classifyInstance(xInstances.get(0));
//							} catch (Exception e) {
//								e.printStackTrace();
//							}
//						}
//						if (partitionUpperRightY != null) {
//							try {
//								predictedY = (float) partitionUpperRightY.classifyInstance(yInstances.get(0));
//							} catch (Exception e) {
//								e.printStackTrace();
//							}
//						}
//					} else if (partitionString.equals("lowerleft")) {
//						if (partitionLowerLeftX != null) {
//							try {
//								predictedX = (float) partitionLowerLeftX.classifyInstance(xInstances.get(0));
//							} catch (Exception e) {
//								e.printStackTrace();
//							}
//						}
//						if (partitionLowerLeftY != null) {
//							try {
//								predictedY = (float) partitionLowerLeftY.classifyInstance(yInstances.get(0));
//							} catch (Exception e) {
//								e.printStackTrace();
//							}
//						}
//					} else if (partitionString.equals("lowerright")) {
//						if (partitionLowerRightX != null) {
//							try {
//								predictedX = (float) partitionLowerRightX.classifyInstance(xInstances.get(0));
//							} catch (Exception e) {
//								e.printStackTrace();
//							}
//						}
//						if (partitionLowerRightY != null) {
//							try {
//								predictedY = (float) partitionLowerRightY.classifyInstance(yInstances.get(0));
//							} catch (Exception e) {
//								e.printStackTrace();
//							}
//						}
//					} else if (partitionString.equals("middle")) {
//						if (partitionMiddleX != null) {
//							try {
//								predictedX = (float) partitionMiddleX.classifyInstance(xInstances.get(0));
//							} catch (Exception e) {
//								e.printStackTrace();
//							}
//						}
//						if (partitionMiddleX != null) {
//							try {
//								predictedY = (float) partitionMiddleY.classifyInstance(yInstances.get(0));
//							} catch (Exception e) {
//								e.printStackTrace();
//							}
//						}
//					}

					xText.post(new Runnable() {
						public void run() {
							xText.setText("X Position: " + predictedX);
						}
					});

					yText.post(new Runnable() {
						public void run() {
							yText.setText("Y Position: " + predictedY);
						}
					});
					
					// TODO: make this work -> grid is apparently null here. For whatever reason.
					runOnUiThread(new Runnable() {
						public void run() {
							grid.setUserPointCoords(predictedX, predictedY);
						}
					});
					
					
					// Unnecessary if you're not testing
					writer.print("(" + predictedX + "," + predictedY + ")");
					writer.print(" %" + myTime.toString() + "\t " + time.toString() +
							"\t" + new Timestamp(System.currentTimeMillis()) + "\n");
					writer.flush();
				}
			});
			t.setPriority(Thread.MIN_PRIORITY); // run in the background
			t.start();
		}
	}

	/**
	 * Take this out if you're not evaluating the classifier
	 * Displays to the user where the next point in the building is
	 * that they should walk to, takes a time for when the user got to
	 * this point
	 */
	private int pointCounter = 0;
	public void nextPoint(View view) {
		pointCounter++;

		File root = Environment.getExternalStorageDirectory();
		File dir = new File(root.getAbsolutePath() + "/indoor_localization");
		dir.mkdirs();
		File file = new File(dir, "livetest_" + building + ".txt");
		try {
			FileOutputStream outputStream = new FileOutputStream(file, true);
			PrintWriter writer = new PrintWriter(outputStream);
			writer.print("DONE: (" + nextX + "," + nextY + ")");
			writer.print(" %" + (new Timestamp(System.currentTimeMillis())).toString() + "\n\n");
			writer.flush();
			writer.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		switch (pointCounter) {
		case 1:
			nextX = 4.5f;
			nextY = 1.5f;
			break;
		case 2:
			nextX = 4.5f;
			nextY = 52.5f;
			break;
		case 3:
			nextX = 21.5f;
			nextY = 52.5f;
			break;
		case 4:
			nextX = 21.5f;
			nextY = 29.5f;
			break;
		case 5:
			nextX = 4.5f;
			nextY = 29.5f;
			break;
		case 6:
			nextX = 4.5f;
			nextY = 15.5f;
			break;
		case 7:
			nextX = 22.5f;
			nextY = 15.5f;
			break;
		case 8:
			nextX = 22.5f;
			nextY = 21.5f;
			break;
		case 9:
			nextX = 37.5f;
			nextY = 21.5f;
			break;
		case 10:
			nextX = 37.5f;
			nextY = 26.5f;
			break;
		case 11:
			nextX = 65.5f;
			nextY = 26.5f;
			break;
		case 12:
			nextX = 65.5f;
			nextY = 9.5f;
			break;
		case 13:
			nextX = 98.5f;
			nextY = 9.5f;
			break;
		case 14:
			nextX = 98.5f;
			nextY = 47.5f;
			break;
		case 15:
			nextX = 84.5f;
			nextY = 47.5f;
			break;
		case 16:
			nextX = 84.5f;
			nextY = 28.5f;
			break;
		case 17:
			nextX = 32.5f;
			nextY = 28.5f;
			break;
		}

		TextView tv = (TextView) findViewById(R.id.tracker_text_goto);
		tv.setText("Go to (" + nextX + "," + nextY + ")");
	}

	public static class TrackerFragment extends Fragment {
		public TrackerFragment() {
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {
			View rootView = inflater.inflate(R.layout.fragment_tracker, container, false);
			return rootView;
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.tracker, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		switch (item.getItemId()) {
			case R.id.action_select_algorithm:
				// open x algorithm selection dialog
				// todo
				break;
			case R.id.action_select_partitioning:
				// open partition selection
				// todo something with this
				showSelectPartitionDialog();
				break;
			case R.id.action_start_data_collection:
				// start main activity
				Intent intent = new Intent(this, MainActivity.class);
				startActivity(intent);
				break;
			//allow user to go one level up(return to Main Activity)
			case android.R.id.home:
				NavUtils.navigateUpFromSameTask(this);
				return true;


			default:
				super.onOptionsItemSelected(item);
				break;
		}
		return true;
	}

	/*
	 * Because Android doesn't let us query a sensor reading whenever we want
	 * we have to keep track of the readings at all times. Here we just update
	 * the class members with the values associated with each reading we're
	 * interested in.
	 */
	@Override
	public void onSensorChanged(SensorEvent event) {
		switch (event.sensor.getType()) {
			case Sensor.TYPE_ACCELEROMETER:
				accelerometerX = event.values[0];
				accelerometerY = event.values[1];
				accelerometerZ = event.values[2];
				break;
			case Sensor.TYPE_MAGNETIC_FIELD:
				magneticX = event.values[0];
				magneticY = event.values[1];
				magneticZ = event.values[2];
				break;
			case Sensor.TYPE_LIGHT:
				light = event.values[0];
				break;
			case Sensor.TYPE_ROTATION_VECTOR:
				rotationX = event.values[0];
				rotationY = event.values[1];
				rotationZ = event.values[2];
				break;
			default:
				break;
		}

		SensorManager.getRotationMatrix(rotation, inclination, 
				new float[] {accelerometerX, accelerometerY, accelerometerZ}, 
				new float[] {magneticX, magneticY, magneticZ});
		orientation = SensorManager.getOrientation(rotation, orientation);
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {}

	/**
	 * Loads the classifiers for predicting the X position
	 */
	private void loadXClassifierModels() {
		try {
			classifierXKStar = (KStar) weka.core.SerializationHelper.read(
					getAssets().open("classifier_x_kstar.model"));
//			partitionLowerLeftX = (KStar) weka.core.SerializationHelper.read(
//					getAssets().open("5partition/model_x_lowerleft.model"));
//			partitionLowerRightX = (KStar) weka.core.SerializationHelper.read(
//					getAssets().open("5partition/model_x_lowerright.model"));
//			partitionUpperLeftX = (KStar) weka.core.SerializationHelper.read(
//					getAssets().open("5partition/model_x_upperleft.model"));
//			partitionUpperRightX = (KStar) weka.core.SerializationHelper.read(
//					getAssets().open("5partition/model_x_upperright.model"));
//			partitionMiddleX = (KStar) weka.core.SerializationHelper.read(
//					getAssets().open("5partition/model_x_middle.model"));
		} catch (Exception e) {
			e.printStackTrace();
			Toast.makeText(this, "KStar x classifier did not load", Toast.LENGTH_LONG).show();
		}

		try {
			classifierXRBFRegressor = (RBFRegressor) weka.core.SerializationHelper.read(
					getAssets().open("classifier_x_rbfreg.model"));
		} catch (Exception e) {
			e.printStackTrace();
			Toast.makeText(this, "RBFRegressor x classifier did not load", Toast.LENGTH_LONG).show();
		}
	}

	/**
	 * Loads the classifiers to predict Y position
	 */
	private void loadYClassifierModels() {
		try {
			classifierYKStar = (KStar) weka.core.SerializationHelper.read(
					getAssets().open("classifier_y_kstar.model"));
//			partitionLowerLeftY = (KStar) weka.core.SerializationHelper.read(
//					getAssets().open("5partition/model_y_lowerleft.model"));
//			partitionLowerRightY = (KStar) weka.core.SerializationHelper.read(
//					getAssets().open("5partition/model_y_lowerright.model"));
//			partitionUpperLeftY = (KStar) weka.core.SerializationHelper.read(
//					getAssets().open("5partition/model_y_upperleft.model"));
//			partitionUpperRightY = (KStar) weka.core.SerializationHelper.read(
//					getAssets().open("5partition/model_y_upperright.model"));
//			partitionMiddleY = (KStar) weka.core.SerializationHelper.read(
//					getAssets().open("5partition/model_y_middle.model"));
		} catch (Exception e) {
			e.printStackTrace();
			Toast.makeText(this, "KStar y classifier did not load", Toast.LENGTH_LONG).show();
		}

		try {
			classifierYRBFRegressor = (RBFRegressor) weka.core.SerializationHelper.read(
					getAssets().open("classifier_y_rbfreg.model"));
		} catch (Exception e) {
			e.printStackTrace();
			Toast.makeText(this, "RBFRegressor y classifier did not load", Toast.LENGTH_LONG).show();
		}
	}

	/**
	 * Loads the classifier to predict the partition
	 */
	private void loadPartitionClassifierModels() {
		try {
//			partitionClassifier = (RandomForest) weka.core.SerializationHelper.read(
//					getAssets().open("5partition/model_randomforest.model"));
		} catch (Exception e) {
			e.printStackTrace();
			Toast.makeText(this, "Partition classifier did not load", Toast.LENGTH_LONG).show();
		}
	}

	/**
	 * Let the user pick what partitioning scheme they want to use
	 */
	private void showSelectPartitionDialog() {
		DialogFragment dialog = new SelectPartitionDialogFragment();
		dialog.show(getSupportFragmentManager(), "SelectPartitionDialogFragment");
	}

	/**
	 * When the partitioning scheme changes, we should update the classifiers
	 * to reflect this
     *
     * TODO fix this
	 */
	@Override
	public void onPartitionChanged(String partitioning) {
		//TextView buildingText = (TextView) findViewById(R.id.text_building);
		//buildingText.setText("Building: " + building);
		//Toast.makeText(this, "New partitioning: " + partitioning, Toast.LENGTH_SHORT).show();
		/*switch (partitioning) {
			case "Full":
				partitionClassifier = null;
				break;
			case "3Partition":
				try {
					partitionClassifier = (RandomForest) weka.core.SerializationHelper.read(
							getAssets().open("3partition/model_randomforest.model"));
				} catch (Exception e) {
					e.printStackTrace();
					Toast.makeText(this,  "Partition classifier did not load", Toast.LENGTH_LONG).show();
				}
				break;
			case "5Partition":
				try {
					partitionClassifier = (RandomForest) weka.core.SerializationHelper.read(
							getAssets().open("5partition/model_randomforest.model"));
				} catch (Exception e) {
					e.printStackTrace();
					Toast.makeText(this, "Partition classifier did not load", Toast.LENGTH_LONG).show();
				}
				load5PartitionClassifiers();
				break;
			case "7Partition":
				try {
					partitionClassifier = (RandomForest) weka.core.SerializationHelper.read(
							getAssets().open("7partition/model_randomforest.model"));
				} catch (Exception e) {
					e.printStackTrace();
					Toast.makeText(this, "Partition classifier did not load", Toast.LENGTH_LONG).show();
				}
				break;
		}
		 */
	}

	/**
	 * Empty out the wifi readings hashmap. Otherwise if you switch buildings in in the
	 * middle of a session the access points for both buildings will be stored to the
	 * data file and mess up the arff file
	 *
	 * TODO: add other buildings here as well
	 * TODO: Get approximate building name from GPS
	 * TODO: Can we have an automated reading? Need to remove these hardcoded MAC addresses
	 * NOTE: Wifi access points are going to be replaced with new ones
	 */
	private void resetWifiReadings() {
		wifiReadings.clear();

		// The readings ending in :00, :01, :02, and :03 are in the 2.4 GHz band
		// The readings ending in :0c, :0d, :0e, and :0f are in the 5 GHz band
		//ramadanDO: add current access points
	}
	
	/**
	 * Unnecessary if you're not testing/evaluating
	 * Prints out the sensor values and time at each data point
	 */
	private void printValues() {
		valuesWriter.print(accelerometerX + "," + accelerometerY + "," + accelerometerZ +
				"," + magneticX + "," + magneticY + "," + magneticZ + "," + light +
				"," + rotationX + "," + rotationY + "," + rotationZ + "," +
				orientation[0] + "," + orientation[1] + "," + orientation[2]);

		for (String key : wifiReadings.keySet()) {
			valuesWriter.print("," + wifiReadings.get(key));
			}
		
		if (location != null) {
			valuesWriter.print("," + location.getLatitude() + "," + location.getLongitude() + 
				"," + location.getAccuracy());
		} else {
			//@author Mahesh Gaya added permission if-statment
			if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
					!= PackageManager.PERMISSION_GRANTED
					|| ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_WIFI_STATE)
					!= PackageManager.PERMISSION_GRANTED
					|| ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CHANGE_WIFI_STATE)
					!= PackageManager.PERMISSION_GRANTED
					|| ActivityCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
					!= PackageManager.PERMISSION_GRANTED) {
				Log.i(TAG, "Permissions have NOT been granted. Requesting permissions.");
				requestMyPermissions();
			} else  {
				Log.i(TAG, "Permissions have already been granted. Getting location from GPS and Network");
                location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
			if (location != null) {
				valuesWriter.print("," + location.getLatitude() + "," +
						location.getLongitude() + "," + location.getAccuracy());
			} else {
				//@author Mahesh Gaya added permission if-statment
				if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
						!= PackageManager.PERMISSION_GRANTED
						|| ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_WIFI_STATE)
						!= PackageManager.PERMISSION_GRANTED
						|| ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CHANGE_WIFI_STATE)
						!= PackageManager.PERMISSION_GRANTED
						|| ActivityCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
						!= PackageManager.PERMISSION_GRANTED) {
					Log.i(TAG, "Permissions have NOT been granted. Requesting permissions.");
					requestMyPermissions();
				} else  {
					Log.i(TAG, "Permissions have already been granted. Getting location from GPS and Network");
                    location = locationManager.getLastKnownLocation(
                            LocationManager.NETWORK_PROVIDER);
                }
				if (location != null) {
					valuesWriter.print("," + location.getLatitude() + "," +
							location.getLongitude() + "," + location.getAccuracy());
				} else {
					valuesWriter.print(",?,?,?");
				}
			}
		}
		
		valuesWriter.print(" %" + (new Timestamp(System.currentTimeMillis())).toString());
		
		valuesWriter.print("\n\n");
		valuesWriter.flush();

	}

	// TODO clean all this up (hide it in another class or something)
	/**
	 * Add the value for each attribute of the data to the instance so that the
	 * classifier can predict a position
	 */
	private void setInstanceValues() {
		xInstances.get(0).setValue(attrAccelX, accelerometerX);
		xInstances.get(0).setValue(attrAccelY, accelerometerY);
		xInstances.get(0).setValue(attrAccelZ, accelerometerZ);
		xInstances.get(0).setValue(attrMagneticX, magneticX);
		xInstances.get(0).setValue(attrMagneticY, magneticY);
		xInstances.get(0).setValue(attrMagneticZ, magneticZ);
		xInstances.get(0).setValue(attrLight, light);
		xInstances.get(0).setValue(attrRotationX, rotationX);
		xInstances.get(0).setValue(attrRotationY, rotationY);
		xInstances.get(0).setValue(attrRotationZ, rotationZ);
		xInstances.get(0).setValue(attrOrientationX, orientation[0]);
		xInstances.get(0).setValue(attrOrientationY, orientation[1]);
		xInstances.get(0).setValue(attrOrientationZ, orientation[2]);

		//ramadanDO: set access points here
		xInstances.get(0).setValue(attrBSSID1, wifiReadings.get("00:17:0f:8d:c3:e0"));
		xInstances.get(0).setValue(attrBSSID2, wifiReadings.get("00:17:0f:8d:c3:e1"));
		xInstances.get(0).setValue(attrBSSID3, wifiReadings.get("00:17:0f:8d:c3:e2"));
		xInstances.get(0).setValue(attrBSSID4, wifiReadings.get("00:17:0f:8d:c3:e3"));
		xInstances.get(0).setValue(attrBSSID5, wifiReadings.get("00:17:0f:8d:c3:e3"));


		if (location != null) {
			xInstances.get(0).setValue(attrLatitude, location.getLatitude());
			xInstances.get(0).setValue(attrLongitude, location.getLongitude());
			xInstances.get(0).setValue(attrLocationAccuracy, location.getAccuracy());
		} else {
			Toast.makeText(this, "Location was null", Toast.LENGTH_SHORT).show();
			xInstances.get(0).setMissing(attrLatitude);
			xInstances.get(0).setMissing(attrLongitude);
			xInstances.get(0).setMissing(attrLocationAccuracy);
		}

		yInstances.get(0).setValue(attrAccelX, accelerometerX);
		yInstances.get(0).setValue(attrAccelY, accelerometerY);
		yInstances.get(0).setValue(attrAccelZ, accelerometerZ);
		yInstances.get(0).setValue(attrMagneticX, magneticX);
		yInstances.get(0).setValue(attrMagneticY, magneticY);
		yInstances.get(0).setValue(attrMagneticZ, magneticZ);
		yInstances.get(0).setValue(attrLight, light);
		yInstances.get(0).setValue(attrRotationX, rotationX);
		yInstances.get(0).setValue(attrRotationY, rotationY);
		yInstances.get(0).setValue(attrRotationZ, rotationZ);
		yInstances.get(0).setValue(attrOrientationX, orientation[0]);
		yInstances.get(0).setValue(attrOrientationY, orientation[1]);
		yInstances.get(0).setValue(attrOrientationZ, orientation[2]);
		yInstances.get(0).setValue(attrBSSID1, wifiReadings.get("00:17:0f:8d:c3:e0"));
		yInstances.get(0).setValue(attrBSSID2, wifiReadings.get("00:17:0f:8d:c3:e1"));
		yInstances.get(0).setValue(attrBSSID3, wifiReadings.get("00:17:0f:8d:c3:e2"));
		yInstances.get(0).setValue(attrBSSID4, wifiReadings.get("00:17:0f:8d:c3:e3"));

		yInstances.get(0).setValue(attrBSSID5, wifiReadings.get("00:17:0f:8d:c3:f0"));
		//ramadanDO: set access points here
		if (location != null) {
			yInstances.get(0).setValue(attrLatitude, location.getLatitude());
			yInstances.get(0).setValue(attrLongitude, location.getLongitude());
			yInstances.get(0).setValue(attrLocationAccuracy, location.getAccuracy());
		} else {
			yInstances.get(0).setMissing(attrLatitude);
			yInstances.get(0).setMissing(attrLongitude);
			yInstances.get(0).setMissing(attrLocationAccuracy);
		}


	}
}
