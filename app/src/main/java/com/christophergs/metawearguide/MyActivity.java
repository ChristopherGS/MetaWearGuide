package com.christophergs.metawearguide;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
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

import com.mbientlab.metawear.Message;
import com.mbientlab.metawear.MetaWearBleService;
import com.mbientlab.metawear.MetaWearBoard;
import static com.mbientlab.metawear.MetaWearBoard.ConnectionStateHandler;
import static com.mbientlab.metawear.AsyncOperation.CompletionHandler;

import com.mbientlab.metawear.RouteManager;
import com.mbientlab.metawear.UnsupportedModuleException;
import com.mbientlab.metawear.data.CartesianFloat;
import com.mbientlab.metawear.module.Led;
import com.mbientlab.metawear.module.Accelerometer;

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

    //METAWEAR OBJECTS
    private MetaWearBleService.LocalBinder serviceBinder;
    private Led ledModule;
    private Accelerometer accelModule;
    private MetaWearBoard mwBoard;

    private final ConnectionStateHandler stateHandler= new ConnectionStateHandler() {
        @Override
        public void connected() {
            Log.i(TAG, "Connected");
            try {
                ledModule = mwBoard.getModule(Led.class);
                accelModule = mwBoard.getModule(Accelerometer.class);
            } catch (UnsupportedModuleException e) {
                e.printStackTrace();
            }

            led_on=(Button)findViewById(R.id.led_on);
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

            led_off=(Button)findViewById(R.id.led_off);
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
                    if (isChecked) {
                        accelModule.setOutputDataRate(ACC_FREQ);
                        accelModule.setAxisSamplingRange(ACC_RANGE);

                        accelModule.routeData()
                                .fromAxes().stream(STREAM_KEY)
                                .commit().onComplete(new CompletionHandler<RouteManager>() {
                            @Override
                            public void success(RouteManager result) {
                                result.subscribe(STREAM_KEY, new RouteManager.MessageHandler() {
                                    @Override
                                    public void process(Message message) {
                                        CartesianFloat axes = message.getData(CartesianFloat.class);
                                        Log.i(TAG, axes.toString());
                                    }

                                });
                            }

                            @Override
                            public void failure(Throwable error) {
                                Log.e(TAG, "Error committing route", error);
                            }
                        });
                        accelModule.enableAxisSampling();
                        accelModule.start();
                    } else {
                        accelModule.disableAxisSampling();
                        accelModule.stop();
                    }
                }
            });

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

    public void retrieveBoard() {
        final BluetoothManager btManager=
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothDevice remoteDevice=
                btManager.getAdapter().getRemoteDevice(MW_MAC_ADDRESS);

        // Create a MetaWear board object for the Bluetooth Device
        mwBoard= serviceBinder.getMetaWearBoard(remoteDevice);
        mwBoard.setConnectionStateHandler(stateHandler);


    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my);

        ///< Bind the service when the activity is created
        getApplicationContext().bindService(new Intent(this, MetaWearBleService.class),
                this, Context.BIND_AUTO_CREATE);

        Log.i(TAG, "log test");
        connect=(Button)findViewById(R.id.connect);
        connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "Clicked connect");
                mwBoard.connect();
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        ///< Unbind the service when the activity is destroyed
        getApplicationContext().unbindService(this);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        ///< Typecast the binder to the service's LocalBinder class
        serviceBinder = (MetaWearBleService.LocalBinder) service;
        retrieveBoard();
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) { }



}
