package com.example.glassapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothAdapter;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.util.Log;
import android.widget.TextView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.IgnoreExtraProperties;
import com.google.firebase.database.ValueEventListener;
import com.instacart.library.truetime.TrueTime;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class MainActivity extends AppCompatActivity  {

    private Accelerometer accelerometer;
    private Gyroscope gyroscope;

    Button buttonBlue;
    boolean recording = false;
    public static final int BLUETOOTH_REQ_CODE = 1;
    BluetoothAdapter bluetoothAdapter;

    // Firestore db setup
    FirebaseDatabase database = FirebaseDatabase.getInstance();
    DatabaseReference myRef = database.getReference();

    public MainActivity() throws IOException {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        accelerometer = new Accelerometer(this);
        gyroscope = new Gyroscope(this);

        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    TrueTime.build().initialize();
                    Log.d("TAG", "True Time Initizalized");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        // Listen for changes in accelerometer
//        accelerometer.setListener(new Accelerometer.Listener() {
//            @Override
//            public void onTranslation(float tx, float ty, float tz)  {
//
//                if(recording==true) {
//                    // If the data is being recorded, write the values to a hashmap and save them to the array list
//                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("hh:mm:ss aa");
//                    String format = simpleDateFormat.format(TrueTime.now());
//                    Log.d("MainActivity", "Current Acc Timestamp: " + format );
//                    writeNewAccReading(format, tx, ty, tz);
//                 }
//                // Changes background color based on acceleromater readings
//
//            }
//        });

        // Listen for changes in gyroscope
        gyroscope.setListener(new Gyroscope.Listener() {
            @Override
            public void onRotation(float rx, float ry, float rz)  {
               if(recording == true) {
                   if (rz < -0.2 || rz > 0.2) {
                       // If the data is being recorded, write the values to a hashmap and save them to the array list
                       SimpleDateFormat simpleDateFormat = new SimpleDateFormat("hh:mm:ss aa");
                       String format = simpleDateFormat.format(TrueTime.now());
                       Log.d("MainActivity", "Current Gyro Timestamp: " + format);
//                       writeNewGyroReading(format, rx, ry, rz);
                       myRef.child("Phone Gyroscope").child(format).setValue("CT");
                   }
               }
            }
        });
    }



    @Override
    protected void onResume() {
        super.onResume();

        accelerometer.register();
        gyroscope.register();
    }

    @Override
    protected void onPause() {
        super.onPause();

        accelerometer.unregister();
        gyroscope.unregister();
    }

    // On Click Method for btnBlue
    public void toggleRecording(View view){

        buttonBlue = (Button)findViewById(R.id.buttonBlue);
        if(recording == false)
        {
            // User has pressed "Start recording"
            recording = true;
            Log.d("TAG", "toggleRecording: " + recording);
            buttonBlue.setText("Stop Driving");
            clearPreviousData();
            myRef.child("Write Data").setValue("True");
        }
        else if(recording == true)
        {
            // User has pressed "Stop recording" and data will be written to firebase
            recording = false;
            Log.d("TAG", "toggleRecording: " + recording);
            buttonBlue.setText("Start Driving");
            myRef.child("Write Data").setValue("False");
            ProcessData();
        }


    }


    @IgnoreExtraProperties
    public class AccReading {

        public String timestamp;
        public Float x;
        public Float y;
        public Float z;

        public AccReading() {
            // Default constructor required for calls to DataSnapshot.getValue(User.class)
        }

        public AccReading(String timestamp, Float x, Float y, Float z) {
            this.timestamp = timestamp;
            this.x = x;
            this.y = y;
            this.z = z;
        }

    }

    public void writeNewAccReading(String timestamp, Float x, Float y, Float z) {
        AccReading reading = new AccReading(timestamp, x, y, z);

        myRef.child("Phone Accelerometer").child(timestamp).setValue(reading);
    }

    @IgnoreExtraProperties
    public class GyroReading {

        public String timestamp;
        public Float x;
        public Float y;
        public Float z;

        public GyroReading() {
            // Default constructor required for calls to DataSnapshot.getValue(User.class)
        }

        public GyroReading(String timestamp, Float x, Float y, Float z) {
            this.timestamp = timestamp;
            this.x = x;
            this.y = y;
            this.z = z;
        }

    }

    public void writeNewGyroReading(String timestamp, Float x, Float y, Float z) {
        GyroReading reading = new GyroReading(timestamp, x, y, z);

        myRef.child("Phone Gyroscope").child(timestamp).setValue(reading);
    }

    public void clearPreviousData(){
        myRef.child("Glass Gyroscope").setValue(null);
        //myRef.child("Glass Accelerometer").setValue(null);
        myRef.child("Phone Gyroscope").setValue(null);
        //myRef.child("Phone Accelerometer").setValue(null);
        Log.d("TAG", "clearPreviousData called");
    }

    public void ProcessData() {
        HashMap<String, String> temp_glassMap = new HashMap<>();
        ArrayList<HashMap<String, String>> map = new ArrayList<>();


        //      ND  D
        //  ND
        //   D
        Double[][] transitions = {{0.8888888888888888, 0.1111111111111111},
                {0.2777777777777778, 0.7222222222222222}};

        Double[] initial_probs = {0.5, 0.5};

        //      CT&HT   HT    CT
        //  ND
        //   D
        Double[][] emissions = {{0.5217391304347826, 0.08695652173913043, 0.391304347826087},
                {0.0, 1.0, 0.0}};

        String[] possible_states = {"ND", "D"};
        getData(new MyCallback() {
            @Override
            public void onCallback(ArrayList<HashMap<String, String>> data) throws ParseException {
                ArrayList<HashMap<String, String>> maps = data;
                HashMap<String, String> glass_map = maps.get(0);
                HashMap<String, String> phone_map = maps.get(1);

                Log.d("MapVals", "Glass: " + glass_map);
                Log.d("MapVals", "Phone: " + phone_map);

                ArrayList<String> glass_times = new ArrayList<>();
                ArrayList<String> glass_readings = new ArrayList<>();
                ArrayList<String> phone_times = new ArrayList<>();
                ArrayList<String> phone_readings = new ArrayList<>();

                DateTimeFormatter inputFormat = DateTimeFormatter.ofPattern("hh:mm:ss a");

                // Convert the time strings to LocalTime
                HashMap<LocalTime, String> dates_glassMap = new HashMap<>();
                for (Map.Entry<String, String> entry : glass_map.entrySet()) {
                    LocalTime date = LocalTime.parse(entry.getKey(), inputFormat);
                    dates_glassMap.put(date, entry.getValue());
                }
                HashMap<LocalTime, String> dates_phoneMap = new HashMap<>();
                for (Map.Entry<String, String> entry : phone_map.entrySet()) {
                    LocalTime date = LocalTime.parse(entry.getKey(), inputFormat);
                    dates_phoneMap.put(date, entry.getValue());
                }

                // Sort the maps based on the date
                TreeMap<LocalTime, String> sorted_glassMap = new TreeMap<>();
                sorted_glassMap.putAll(dates_glassMap);

                TreeMap<LocalTime, String> sorted_phoneMap = new TreeMap<>();
                sorted_phoneMap.putAll(dates_phoneMap);


                // Get the list of all timestamps
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("hh:mm:ss aa");
                DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("hh:mm:ss a");
                //String format = simpleDateFormat.format();
                ArrayList<LocalTime> all_timestamps = new ArrayList<>();
                for (LocalTime key : sorted_glassMap.keySet()) {
                    all_timestamps.add(key);
                }
                for (LocalTime key : sorted_phoneMap.keySet()) {
                    if (all_timestamps.contains(key)) {
                        continue;
                    } else {
                        all_timestamps.add(key);
                    }
                }

                Log.d("AllTimestamps", "Combined Timestamps = " + all_timestamps);
                for (Map.Entry<LocalTime, String> entry : sorted_glassMap.entrySet()) {
                    String format = timeFormat.format(entry.getKey());
                    //Log.d("SortedGlassMap", "Key: " + format);
                    //Log.d("SortedGlassMap", "Value: " + entry.getValue());
                }
                for (Map.Entry<LocalTime, String> entry : sorted_phoneMap.entrySet()) {
                    String format = timeFormat.format(entry.getKey());
                    //Log.d("SortedPhoneMap", "Key: " + format);
                    //Log.d("SortedPhoneMap", "Value: " + entry.getValue());
                }
                TreeMap<LocalTime, String> all_values = new TreeMap<>();
                for (int i = 0; i < all_timestamps.size(); i++) {
                    //String format = timeFormat.format(all_timestamps.get(i));
                    if (sorted_glassMap.containsKey(all_timestamps.get(i)) && sorted_phoneMap.containsKey(all_timestamps.get(i))) {
                        all_values.put(all_timestamps.get(i), "HT&CT");
                    } else if (sorted_glassMap.containsKey(all_timestamps.get(i)) && !sorted_phoneMap.containsKey(all_timestamps.get(i))) {
                        all_values.put(all_timestamps.get(i), "HT");
                    } else if (!sorted_glassMap.containsKey(all_timestamps.get(i)) && sorted_phoneMap.containsKey(all_timestamps.get(i))) {
                        all_values.put(all_timestamps.get(i), "CT");
                    }
                    //Log.d("AllTimestamps", "Timestamp: " + format);
                }

                Integer iterator = 0;
                for (Map.Entry<LocalTime, String> entry : all_values.entrySet()) {
                    String format = timeFormat.format(entry.getKey());
                     //Log.d("AllValues", "Key: " + format);

                    Log.d("AllValues", "Value " + iterator + ": " + entry.getValue());
                    iterator++;
                }

                Double[][] observation_deltas = new Double[2][all_values.size()];

                // Iterate through observations
                String current_state = "";
                String previous_state = "";
                Integer current_observation = null;
                ArrayList<String> states = new ArrayList<>();
                ArrayList<String> observations = new ArrayList<>();
                ArrayList<Integer> observations_as_int = new ArrayList<>();


                for (String value : all_values.values()) {
                    observations.add(value);
                    if (value.equals("HT&CT")) {
                        observations_as_int.add(0);
                    } else if (value.equals("HT")) {
                        observations_as_int.add(1);
                    } else if (value.equals("CT")) {
                        observations_as_int.add(2);
                    }
                }


                for (int i = 0; i < possible_states.length; i++) {
                    observation_deltas[i][0] = initial_probs[i] * emissions[i][observations_as_int.get(0)] * 1.0;
                }
                Double max_obs = 0.0;
                for (int z = 0; z < initial_probs.length; z++) {
                    //Log.d("ObservationDeltas", "Obs " + z + "," + 0 + " " + observation_deltas[z][0]);
                    if (observation_deltas[z][0] > max_obs) {

                        //Log.d("ObservationDeltas", "Obs: " + observation_deltas[z][0]);
                        max_obs = observation_deltas[z][0];

                        if (z == 0) {
                            current_state = "ND";
                        } else if (z == 1) {
                            current_state = "D";
                        }
                    }
                }
                states.add(current_state);

                Integer previous_observation = observations_as_int.get(0);
                previous_state = current_state;
                for (int i = 1; i < observations_as_int.size(); i++) {

                    current_observation = observations_as_int.get(i);

                    for (int j = 0; j < possible_states.length; j++) {

                        //Log.d("JTrack", "j: " + j);

                        ArrayList<Double> calculated_deltas = new ArrayList<>();
                        for (int k = 0; k < possible_states.length; k++) {
                            //Log.d("CaclDeltas", "k: " + k);
                            //Log.d("Check previous ob", "previous ob: " + previous_observation);
                            Double calculation = transitions[k][j] * emissions[k][previous_observation] * observation_deltas[k][i - 1];
                            calculated_deltas.add(calculation);
                        }

                        //Log.d("CalcDeltas", "Deltas: " + calculated_deltas);
                        observation_deltas[j][i] = Collections.max(calculated_deltas);


                          //Log.d("ObservationDeltas", "Obs: " + observation_deltas[0][iterator]);
                          //Log.d("ObservationDeltas", "Obs: " + observation_deltas[1][iterator]);

                    }
                    Double new_max_obs = 0.0;
                    for (int z = 0; z < initial_probs.length; z++) {
                        //Log.d("ObservationDeltas", "Obs " + z + "," + i + " " + observation_deltas[z][i]);
                        if (observation_deltas[z][i] > new_max_obs) {

                            //Log.d("ObservationDeltas", "Obs: " + observation_deltas[z][i]);
                            new_max_obs = observation_deltas[z][i];

                            if (z == 0) {
                                current_state = "ND";
                            } else if (z == 1) {
                                current_state = "D";
                            }
                        }
                    }

                    states.add(current_state);
                    previous_observation = current_observation;
                    previous_state = current_state;
                }

                Log.d("States", "Estimated States: " + states);
                for(int i = 0; i < states.size(); i++){
                    Log.d("S", i + " : " + states.get(i));
                }
                Log.d("States", "States Len: " + states.size());

                Integer number_of_distractions = Collections.frequency(states, "D");
                TextView textView = (TextView) findViewById(R.id.distractions);
                textView.setText(number_of_distractions.toString());
            }


        });

    }

    public interface MyCallback {
        void onCallback(ArrayList<HashMap<String, String>> data) throws ParseException;
    }

    private void getData(MyCallback myCallback) {

        ArrayList<HashMap<String, String>> maps = new ArrayList<>();

        DatabaseReference glassRef = database.getReference().child("Glass Gyroscope");
        DatabaseReference phoneRef = database.getReference().child("Phone Gyroscope");

        glassRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot glass_snapshot) {
                HashMap<String, String> gsnapshot = (HashMap<String, String>) glass_snapshot.getValue();
                maps.add(gsnapshot);

                phoneRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot phone_snapshot) {
                        HashMap<String, String> psnapshot = (HashMap<String, String>) phone_snapshot.getValue();
                        maps.add(psnapshot);

                        try {
                            myCallback.onCallback(maps);
                        } catch (ParseException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {

                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });


    }

}

