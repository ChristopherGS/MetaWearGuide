package com.christophergs.metawearguide;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.content.*;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import com.mbientlab.metawear.AsyncOperation;
import com.mbientlab.metawear.Message;
import com.mbientlab.metawear.MetaWearBleService;
import com.mbientlab.metawear.MetaWearBoard;
import static com.mbientlab.metawear.MetaWearBoard.ConnectionStateHandler;
import static com.mbientlab.metawear.AsyncOperation.CompletionHandler;

import com.mbientlab.metawear.RouteManager;
import com.mbientlab.metawear.UnsupportedModuleException;
import com.mbientlab.metawear.data.CartesianFloat;
import com.mbientlab.metawear.module.Bmi160Gyro;
import com.mbientlab.metawear.module.Bmi160Accelerometer;
import com.mbientlab.metawear.module.Led;
import com.mbientlab.metawear.module.Accelerometer;
import com.mbientlab.metawear.module.Bmi160Gyro.*;
import com.mbientlab.metawear.module.Logging;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MyActivity extends AppCompatActivity implements ServiceConnection {

    //CONSTANTS
    private final String MW_MAC_ADDRESS= "D5:9C:DC:37:BA:AE"; //update with your board's MAC address
    private static final String TAG = "MetaWear";
    private Button connect;
    private Button led_on;
    private Button led_off;
    private Switch accel_switch;
    private static final float ACC_RANGE = 8.f, ACC_FREQ = 50.f;
    private static final String STREAM_KEY = "accel_stream";
    private static final String LOG_KEY = "accel_log";
    private static final String GYRO_STREAM_KEY = "gyro_stream";
    TextView accelData;
    TextView gyroData;
    public static final String EXTRA_BT_DEVICE = "com.christophergs.metawearguide.EXTRA_BT_DEVICE";
    private BluetoothDevice btDevice;


    //METAWEAR OBJECTS
    private MetaWearBleService.LocalBinder serviceBinder;
    private Led ledModule;
    private Bmi160Accelerometer accelModule;
    private Bmi160Gyro gyroModule;

    private MetaWearBoard mwBoard;
    private Logging loggingModule;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my);

        ///< Bind the service when the activity is created
        getApplicationContext().bindService(new Intent(this, MetaWearBleService.class),
                this, Context.BIND_AUTO_CREATE);

        Log.i(TAG, "log test");
        connect=(Button)findViewById(R.id.disconnect);
        connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "Clicked disconnect");
                mwBoard.setConnectionStateHandler(null);
                mwBoard.disconnect();
                finish();
            }
        });
        accelData = (TextView) findViewById(R.id.textAccel);
        gyroData = (TextView) findViewById(R.id.textGyro);

        btDevice= getIntent().getParcelableExtra(EXTRA_BT_DEVICE);
        getApplicationContext().bindService(new Intent(this, MetaWearBleService.class), this, BIND_AUTO_CREATE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        ///< Unbind the service when the activity is destroyed
        getApplicationContext().unbindService(this);
    }

    public final ConnectionStateHandler stateHandler;

    {
        stateHandler = new ConnectionStateHandler() {
            @Override
            public void connected() {
                Log.i(TAG, "Connected");
            }

            @Override
            public void disconnected() {
                Log.i(TAG, "Connected Lost");
            }

            @Override
            public void failure(int status, Throwable error) {
                Log.e(TAG, "Error connecting", error);
            }
        };
    }



    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        ///< Typecast the binder to the service's LocalBinder class
        mwBoard= ((MetaWearBleService.LocalBinder) service).getMetaWearBoard(btDevice);
        mwBoard.setConnectionStateHandler(stateHandler);
        try {
            ledModule = mwBoard.getModule(Led.class);
            accelModule = mwBoard.getModule(Bmi160Accelerometer.class);
            loggingModule = mwBoard.getModule(Logging.class);
            gyroModule = mwBoard.getModule(Bmi160Gyro.class);
        } catch (UnsupportedModuleException e) {
            e.printStackTrace();
        }

        led_on = (Button) findViewById(R.id.led_on);
        led_on.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "Turn on LED");
                ledModule.configureColorChannel(Led.ColorChannel.BLUE)
                        .setRiseTime((short) 0).setPulseDuration((short) 1000)
                        .setRepeatCount((byte) -1).setHighTime((short) 500)
                        .setHighIntensity((byte) 16).setLowIntensity((byte) 16)
                        .commit();
                ledModule.play(true);
            }
        });

        led_off = (Button) findViewById(R.id.led_off);
        led_off.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "Turn off LED");
                ledModule.stop(true);
            }
        });

        Switch accel_switch = (Switch) findViewById(R.id.accel_switch);
        accel_switch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Log.i("Switch State=", "" + isChecked);
                final String CSV_HEADER = String.format("sensor,x_axis,y_axis,z_axis");
                final String filename = "METAWEAR.csv";
                final File path = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS), filename);
                if (isChecked) {
                    //delete the csv file if it already exists (will be from older recordings)
                    boolean is_deleted = path.delete();
                    Log.i(TAG, "deleted: " + is_deleted);

                    OutputStream out;
                    try {
                        out = new BufferedOutputStream(new FileOutputStream(path, true));
                        out.write(CSV_HEADER.getBytes());
                        out.write("\n".getBytes());
                        out.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    accelModule.setOutputDataRate(ACC_FREQ);
                    accelModule.setAxisSamplingRange(ACC_RANGE);
                    gyroModule.configure()
                            .setOutputDataRate(OutputDataRate.ODR_50_HZ)
                            .setFullScaleRange(FullScaleRange.FSR_500)
                            .commit();

                    AsyncOperation<RouteManager> routeManagerResultAccel = accelModule.routeData().fromAxes().stream(STREAM_KEY).commit();
                    AsyncOperation<RouteManager> routeManagerResultGyro = gyroModule.routeData().fromAxes().stream(GYRO_STREAM_KEY).commit();

                    routeManagerResultAccel.onComplete(new CompletionHandler<RouteManager>() {
                        @Override
                        public void success(RouteManager result) {
                            result.subscribe(STREAM_KEY, new RouteManager.MessageHandler() {
                                @Override
                                public void process(Message msg) {
                                    final CartesianFloat axes = msg.getData(CartesianFloat.class);
                                    Log.i(TAG, String.format("Accelerometer: %s", axes.toString()));
                                    sensorMsg(String.format(axes.toString()), "accel");
                                    //CSV CODE
                                    String accel_entry = String.format("Accel, %s", axes.toString());
                                    String csv_accel_entry = accel_entry + ",";
                                    OutputStream out;
                                    try {
                                        out = new BufferedOutputStream(new FileOutputStream(path, true));
                                        out.write(csv_accel_entry.getBytes());
                                        out.write("\n".getBytes());
                                        out.close();
                                    } catch (Exception e) {
                                        Log.e(TAG, "CSV creation error", e);
                                    }
                                }
                            });
                        }
                    });

                    routeManagerResultGyro.onComplete(new CompletionHandler<RouteManager>() {
                        @Override
                        public void success(RouteManager result) {
                            result.subscribe(GYRO_STREAM_KEY, new RouteManager.MessageHandler() {
                                @Override
                                public void process(Message msg) {
                                    final CartesianFloat spinData = msg.getData(CartesianFloat.class);
                                    Log.i(TAG, String.format("Gyroscope: %s", spinData.toString()));
                                    sensorMsg(String.format(spinData.toString()), "gyro");
                                    //CSV CODE
                                    String gyro_entry = String.format("Gyro, %s", spinData.toString());
                                    String csv_gyro_entry = gyro_entry + ",";
                                    OutputStream out;
                                    try {
                                        out = new BufferedOutputStream(new FileOutputStream(path, true));
                                        out.write(csv_gyro_entry.getBytes());
                                        out.write("\n".getBytes());
                                        out.close();
                                    } catch (Exception e) {
                                        Log.e(TAG, "CSV creation error", e);
                                    }
                                }
                            });
                        }
                    });
                    accelModule.enableAxisSampling();
                    accelModule.start();
                    gyroModule.start();
                } else

                {
                    gyroModule.stop();
                    accelModule.disableAxisSampling();
                    accelModule.stop();
                }
            }
        });
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) { }

    public void sensorMsg(String msg, final String sensor) {
        final String reading = msg;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (sensor == "accel") {
                    accelData.setText("Accel: " + reading);
                } else {
                    gyroData.setText("Gyro: " + reading);
                }
            }
        });
    }

}
