package com.davidmascharka.lips;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.mascharka.indoorlocalization.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;

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
 * Main activity for the IndoorLocalization application
 * 
 * Presents the user a grid of specifiable size to allow the user to collect data
 * Hardcoded WiFi access points for the building/room/area of interest are all that
 * needs to be changed to customize this class for another building
 * 
 * Some legacy code present that allows the user to switch buildings -> won't affect
 * the function of the code at all. Choices still come up but don't do anything important
 *
 */
public class MainActivity extends AppCompatActivity implements SensorEventListener,
	SelectBuildingDialogFragment.SelectBuildingDialogListener,
	SelectRoomSizeDialogFragment.SelectRoomSizeDialogListener,
	ActivityCompat.OnRequestPermissionsResultCallback {
	
	// Preferences for storing user options such as room size and building
	private static final String PREFS_NAME = "IndoorLocalizationPrefs";
	
	// Code used when the user launches an intent to select a map
	private static final int GET_MAP_REQUEST = 0;

	//@author Mahesh Gaya added this tag for debugging purposes
	private String TAG = "Permission Test: ";

	// Class members for each sensor reading of interest
	private float accelerometerX;
	private float accelerometerY;
	private float accelerometerZ;
	private float magneticX;
	private float magneticY;
	private float magneticZ;
	private float light;
	private float rotationX;
	private float rotationY;
	private float rotationZ;
	private float[] rotation;
	private float[] inclination;
	private float[] orientation;
	
	private SensorManager sensorManager;
	private List<Sensor> sensorList;
	
	// Members for taking WiFi scans and storing the results
	private WifiManager wifiManager;
	private List<ScanResult> scanResults;
	private LinkedHashMap<String, Integer> wifiReadings;
	
	// Whether the user initiated a scan -> used to determine whether to store the datapoint
	// since the system or another app can initiate a scan at any time. Don't want to store
	// those points.
	private boolean userInitiatedScan;
	
	// Members for accessing location data
	private LocationManager locationManager;
	private LocationListener locationListener;
	private Location location;

	// User options
	private String building;
	private int roomWidth;
	private int roomLength;
	private boolean displayMap;

    private static final int MY_PERMISSIONS = 12;

	//@author Mahesh Gaya added these for permissions
	private static final int REQUEST_LOCATION = 110;
	private static final int REQUEST_WRITE_STORAGE = 112;
	private static final int REQUEST_WIFI = 114;
	
	// Will listen for broadcasts from the WiFi manager. When a scan has finished, the
	// onReceive method will be called which will store a datapoint if the user initiated
	// the scan.
	BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			updateScanResults();
		}
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestMyPermissions();
        }
		
		getPreferences();

		setContentView(R.layout.activity_main);

		if (savedInstanceState == null) {
			getSupportFragmentManager().beginTransaction()
					.add(R.id.container, new MainFragment()).commit();
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
		
		wifiReadings = new LinkedHashMap<String, Integer>();
		resetWifiReadings(building);
		
		userInitiatedScan = false;
	}
	/**
	 * Callback received when a permissions request has been completed.
	 * @author Mahesh Gaya
	 */
	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
										   @NonNull int[] grantResults) {
		if (requestCode == REQUEST_LOCATION){
			//received permissions for GPS
			Log.i(TAG, "Received permissions for GPS");

		} else if (requestCode == REQUEST_WRITE_STORAGE){
			//received permissions for External storage
			Log.i(TAG, "Received permissions for writing to External Storage");

		} else if (requestCode == REQUEST_WIFI){
			//received permissions for WIFI
			Log.i(TAG, "Received permissions for WIFI");

		} else {
			super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		}

	}

   // @TargetApi(23)
    private void requestMyPermissions() {
        /* //this does not work
		if ((checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED) ||
                (checkSelfPermission(android.Manifest.permission.ACCESS_WIFI_STATE) !=
                        PackageManager.PERMISSION_GRANTED) ||
                (checkSelfPermission(android.Manifest.permission.CHANGE_WIFI_STATE) !=
                        PackageManager.PERMISSION_GRANTED) ||
                (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                        PackageManager.PERMISSION_GRANTED)) {
            requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_WIFI_STATE, android.Manifest.permission.CHANGE_WIFI_STATE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, MY_PERMISSIONS);
        }
        */
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
	
	@Override
	public void onResume() {
		super.onResume();
		
		// Set building textview to the building the user has selected
		TextView buildingText = (TextView) findViewById(R.id.text_building);
		buildingText.setText("Building: " + building);
		
		// Set room size textview to the room size the user has selected
		TextView roomSizeText = (TextView) findViewById(R.id.text_room_size);
		roomSizeText.setText("Room size: " + roomWidth + " x " + roomLength);
		
		// Set grid options
		GridView grid = (GridView) findViewById(R.id.gridView);
		grid.setGridSize(roomWidth, roomLength);
		grid.setDisplayMap(displayMap);
		
		// Register to get sensor updates from all the available sensors
		sensorList = sensorManager.getSensorList(Sensor.TYPE_ALL);
		for (Sensor sensor : sensorList) {
			sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST);
		}
		
		// Enable wifi if it is not
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
			Log.i(TAG, "Permissions have already been granted. Removing Updates from Location Manager");
            locationManager.removeUpdates(locationListener);
        }
		unregisterReceiver(receiver);
		
		savePreferences();
		
		super.onPause();
	}
	
	/* In order to make sure we have up-to-date WiFi readings, start a
	 * scan when user clicks the button. When the scan is finished, the
	 * data will be saved by the updateScanResults() method called from 
	 * the BroadcastReceiver.
	 */
	public void saveReading(View view) {
		userInitiatedScan = true;
		if (wifiManager.startScan()) {
			Toast.makeText(this, "Started WiFi scan", Toast.LENGTH_SHORT).show();
		} else {
			Toast.makeText(this, "Couldn't start WiFi scan", Toast.LENGTH_SHORT).show();
		}
		
		Button button = (Button) findViewById(R.id.button_confirm);
		button.setClickable(false);
	}
	
	// Helper method to keep track of the most up-to-date location
	private void updateLocation(Location location) {
		this.location = location;
	}
	
	/* 
	 * Writes all the information to the file /sdcard/indoor_localization/dataset.txt
	 * This is called from the BroadcastReceiver. There is one awkward situation as
	 * a result of doing it this way. This method will be called any time the application
	 * is running and a WiFi scan is performed. So if another app scans for WiFi or the
	 * system decides to perform a scan, this method will run. It also runs as soon as
	 * the application starts if WiFi is disabled - enabling it initiates a scan.
	 * 
	 * This issue is fixed for now by adding a boolean indicating whether the user initiated
	 * the scan from this application. Set to true on the button click and false at the end
	 * of this method. The results will only be saved if the scan was user-initiated
	 */
	private void updateScanResults() {
		if (userInitiatedScan) {
			//Toast.makeText(this, "Scan finished", Toast.LENGTH_SHORT).show();
			
			resetWifiReadings(building);
	
			scanResults = wifiManager.getScanResults();
			for (ScanResult result : scanResults) {
				if (wifiReadings.get(result.BSSID) != null) {
					wifiReadings.put(result.BSSID, result.level);
				} else { // BSSID wasn't programmed in - notify user
					//Toast.makeText(this, "This BSSID is new: " + result.BSSID,
					//		Toast.LENGTH_SHORT).show();
				}
			}
			
			// Get a filehandle for /sdcard/indoor_localization/dataset_BUILDING.txt
			File root = Environment.getExternalStorageDirectory();
			File dir = new File(root.getAbsolutePath() + "/indoor_localization");
			dir.mkdirs();
			File file = new File(dir, "dataset_" + building + ".txt");
			
			try {
				FileOutputStream outputStream = new FileOutputStream(file, true);
				PrintWriter writer = new PrintWriter(outputStream);
				
				writer.print(accelerometerX + "," + accelerometerY + "," + accelerometerZ +
						"," + magneticX + "," + magneticY + "," + magneticZ + "," + light +
						"," + rotationX + "," + rotationY + "," + rotationZ + "," +
						orientation[0] + "," + orientation[1] + "," + orientation[2]);
	
				for (String key : wifiReadings.keySet()) {
					writer.print("," + wifiReadings.get(key));
    				}
				
				if (location != null) {
					writer.print("," + location.getLatitude() + "," + location.getLongitude() + 
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
						Log.i(TAG, "Permissions have already been granted. Getting last known location from GPS");
                        location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    }

					if (location != null) {
						writer.print("," + location.getLatitude() + "," +
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
							Log.i(TAG, "Permssions have already been granted. Getting last know location from network");
                            location = locationManager.getLastKnownLocation(
                                    LocationManager.NETWORK_PROVIDER);
                        }

						if (location != null) {
							writer.print("," + location.getLatitude() + "," +
									location.getLongitude() + "," + location.getAccuracy());
						} else {
							Toast.makeText(this, "Location was null", Toast.LENGTH_SHORT).show();
							writer.print(",?,?,?");
						}
					}
				}
				
				TextView xposition = (TextView) findViewById(R.id.text_xposition);
				TextView yposition = (TextView) findViewById(R.id.text_yposition);
				writer.print("," + xposition.getText().toString().substring(3));
				writer.print("," + yposition.getText().toString().substring(3));
				
				writer.print(" %" + (new Timestamp(System.currentTimeMillis())).toString());
				
				writer.print("\n\n");

				writer.flush();
				writer.close();
				
				Toast.makeText(this, "Done saving datapoint", Toast.LENGTH_SHORT).show();
				userInitiatedScan = false;
			} catch (Exception e) {
				Toast.makeText(this, "There was an error", Toast.LENGTH_SHORT).show();
				Log.e("ERROR", Log.getStackTraceString(e));
			}
		}
		
		Button button = (Button) findViewById(R.id.button_confirm);
		button.setClickable(true);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);
		
		// Set the display map option to the appropriate check state
		menu.getItem(3).setChecked(displayMap);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		switch (item.getItemId()) {
			case R.id.action_reset:
				resetDatafile();
				break;
			case R.id.action_select_building:
				showSelectBuildingDialog();
				break;
			case R.id.action_select_room_size:
				new SelectRoomSizeDialogFragment().show(getSupportFragmentManager(),
						"RoomSize");
				break;
			case R.id.action_display_map:
				displayMap = !displayMap;
				item.setChecked(displayMap);
				((GridView) findViewById(R.id.gridView)).setDisplayMap(displayMap);
				break;
			case R.id.action_select_map:
				// Launch an intent to select the map the user wants to display
				Intent selectMapIntent = new Intent();
				selectMapIntent.setAction(Intent.ACTION_GET_CONTENT);
				selectMapIntent.setType("image/*");
				selectMapIntent.addCategory(Intent.CATEGORY_OPENABLE);
				
				if (selectMapIntent.resolveActivity(getPackageManager()) != null) {
					startActivityForResult(selectMapIntent, GET_MAP_REQUEST);
				}
				break;
			case R.id.action_start_tracker:
				Intent intent = new Intent(this, TrackerActivity.class);
				startActivity(intent);
				break;
			default:
				super.onOptionsItemSelected(item);
				break;
		}
		return true;
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == GET_MAP_REQUEST && resultCode == RESULT_OK) {
			// The image request was handled fine. Set the map the user wants
			
			Uri selectedMapUri = data.getData();
			((GridView) findViewById(R.id.gridView)).setMapUri(selectedMapUri);
		}
	}
	
	private void showSelectBuildingDialog() {
		DialogFragment dialog = new SelectBuildingDialogFragment();
		dialog.show(getSupportFragmentManager(), "SelectBuildingDialogFragment");
	}
	
	@Override
	public void onBuildingChanged(String building) {
		this.building = building;
		
		TextView buildingText = (TextView) findViewById(R.id.text_building);
		buildingText.setText("Building: " + building);
	}

	@Override
	public void onRoomSizeChanged(int width, int length) {
		roomWidth = width;
		roomLength = length;
		
		GridView grid = (GridView) findViewById(R.id.gridView);
		grid.setGridSize(roomWidth, roomLength);
		
		TextView roomSizeText = (TextView) findViewById(R.id.text_room_size);
		roomSizeText.setText("Room size: " + roomWidth + " x " + roomLength);
	}
	
	// Resets the data file to blank with the device and order of data as a header
	private void resetDatafile() {
		File root = Environment.getExternalStorageDirectory();
		File dir = new File(root.getAbsolutePath() + "/indoor_localization");
		dir.mkdirs();
		File file = new File(dir, "dataset_" + building + ".txt");
		try {
			FileOutputStream outputStream = new FileOutputStream(file);
			PrintWriter writer = new PrintWriter(outputStream);
			writer.println("%Data collected by " + android.os.Build.MODEL + 
					"\n%Format of data: Accelerometer X, Accelerometer Y, Accelerometer Z, " +
					"Magnetic X, Magnetic Y, Magnetic Z, Light, Rotation X, Rotation Y, " +
					"Rotation Z, Orientation X, Orientation Y, Orientation Z, WIFI NETWORKS " +
					"BSSID, Frequency, Signal level, Latitude, Longitude\n\n");
			writer.flush();
			writer.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static class MainFragment extends Fragment {
		public MainFragment() {
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {
			View rootView = inflater.inflate(R.layout.fragment_main, container, false);
			return rootView;
		}
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		/*
		 * Because Android doesn't let us query a sensor reading whenever we want so
		 * we have to keep track of the readings at all times. Here we just update
		 * the class members with the values associated with each reading we're
		 * interested in.
		 */
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
	
	private void savePreferences() {
		SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
		
		SharedPreferences.Editor editor = prefs.edit();
		
		editor.putInt(getPackageName() + ".width", roomWidth);
		editor.putInt(getPackageName() + ".length", roomLength);
		editor.putString(getPackageName() + ".building", building);
		editor.putBoolean(getPackageName() + ".displayMap", displayMap);
		
		editor.commit();
	}
	
	private void getPreferences() {
		SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
		
		roomWidth = prefs.getInt(getPackageName() + ".width", 7);
		roomLength = prefs.getInt(getPackageName() + ".length", 9);
		building = prefs.getString(getPackageName() + ".building", "Howard");
		displayMap = prefs.getBoolean(getPackageName() + ".displayMap", false);
	}
	
	// TODO make pretty
	private void resetWifiReadings(String building) {
		// Empty out the wifi readings hashmap. Otherwise if you switch buildings in in the
		// middle of a session the access points for both buildings will be stored to the
		// data file and mess up the arff file
		wifiReadings.clear();
		
		// The readings ending in :00, :01, :02, and :03 are in the 2.4 GHz band
		// The readings ending in :0c, :0d, :0e, and :0f are in the 5 GHz band
		switch (building) {
			case "Howard":
				wifiReadings.put("00:00:00:00:00:00", 0);

				wifiReadings.put("00:18:74:88:d4:00", 0);
				wifiReadings.put("00:18:74:88:d4:01", 0);
				wifiReadings.put("00:18:74:88:d4:02", 0);
				wifiReadings.put("00:18:74:88:d4:03", 0);
				wifiReadings.put("fe:ff:a8:cb:ae:ad", 0);

				break;
			case "Cowles":
				wifiReadings.put("00:17:0f:8d:c3:e0", 0);
				wifiReadings.put("00:17:0f:8d:c3:e1", 0);
				wifiReadings.put("00:17:0f:8d:c3:e2", 0);
				wifiReadings.put("00:17:0f:8d:c3:e3", 0);

				break;
			case "Cartwright":

				break;
			case "ramadan":

				break;
			case "lab":

				break;
			case "morb3":

				break;
			default:
				break;
		}
	}
}
