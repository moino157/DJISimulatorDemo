package com.dji.simulatorDemo;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import dji.common.error.DJISDKError;
import dji.common.flightcontroller.virtualstick.FlightControlData;
import dji.common.flightcontroller.virtualstick.FlightCoordinateSystem;
import dji.common.flightcontroller.virtualstick.RollPitchControlMode;
import dji.common.flightcontroller.virtualstick.VerticalControlMode;
import dji.common.flightcontroller.virtualstick.YawControlMode;
import dji.common.util.CommonCallbacks;
import dji.log.DJILog;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.products.Aircraft;
import dji.common.error.DJIError;
import dji.sdk.sdkmanager.DJISDKInitEvent;
import dji.sdk.sdkmanager.DJISDKManager;

public class MainActivity extends Activity implements View.OnClickListener {

    //FLAG
    private YawControlMode yawControlMode = YawControlMode.ANGULAR_VELOCITY;
    private FlightCoordinateSystem flightCoordinateSystem = FlightCoordinateSystem.BODY;
    private RollPitchControlMode rollPitchControlMode = RollPitchControlMode.VELOCITY;
    private VerticalControlMode verticalControlMode = VerticalControlMode.POSITION;
    private boolean virtualStickAdvancedModeEnabled = false;

    private static final String TAG = MainActivity.class.getName();

    private static final String[] REQUIRED_PERMISSION_LIST = new String[]{
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.READ_PHONE_STATE,
    };
    private List<String> missingPermission = new ArrayList<>();
    private AtomicBoolean isRegistrationInProgress = new AtomicBoolean(false);
    private static final int REQUEST_PERMISSION_CODE = 12345;

    private FlightController mFlightController;
    protected TextView mConnectStatusTextView;
    private Button mBtnTakeOff;
    private Button mBtnLand;

    private Timer parkourTimer;
    private Timer mSendVirtualStickDataTimer;
    private SendVirtualStickDataTask mSendVirtualStickDataTask;

    private double pitch;
    private double roll;
    private double yaw;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkAndRequestPermissions();
        setContentView(R.layout.activity_main);

        initUI();

        // Register the broadcast receiver for receiving the device connection's changes.
        IntentFilter filter = new IntentFilter();
        filter.addAction(DJISimulatorApplication.FLAG_CONNECTION_CHANGE);
        registerReceiver(mReceiver, filter);
    }

    /**
     * Checks if there is any missing permissions, and
     * requests runtime permission if needed.
     */
    private void checkAndRequestPermissions() {
        // Check for permissions
        for (String eachPermission : REQUIRED_PERMISSION_LIST) {
            if (ContextCompat.checkSelfPermission(this, eachPermission) != PackageManager.PERMISSION_GRANTED) {
                missingPermission.add(eachPermission);
            }
        }
        // Request for missing permissions
        if (!missingPermission.isEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this,
                    missingPermission.toArray(new String[missingPermission.size()]),
                    REQUEST_PERMISSION_CODE);
        }

    }

    /**
     * Result of runtime permission request
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Check for granted permission and remove from missing list
        if (requestCode == REQUEST_PERMISSION_CODE) {
            for (int i = grantResults.length - 1; i >= 0; i--) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    missingPermission.remove(permissions[i]);
                }
            }
        }
        // If there is enough permission, we will start the registration
        if (missingPermission.isEmpty()) {
            startSDKRegistration();
        } else {
            showToast("Missing permissions!!!");
        }
    }

    private void startSDKRegistration() {
        if (isRegistrationInProgress.compareAndSet(false, true)) {
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    showToast( "registering, pls wait...");
                    DJISDKManager.getInstance().registerApp(getApplicationContext(), new DJISDKManager.SDKManagerCallback() {
                        @Override
                        public void onRegister(DJIError djiError) {
                            if (djiError == DJISDKError.REGISTRATION_SUCCESS) {
                                DJILog.e("App registration", DJISDKError.REGISTRATION_SUCCESS.getDescription());
                                DJISDKManager.getInstance().startConnectionToProduct();
                                //loginAccount();
                                showToast("Register Success");
                            } else {
                                showToast( "Register sdk fails, check network is available");
                            }
                            Log.v(TAG, djiError.getDescription());
                        }

                        @Override
                        public void onProductDisconnect() {
                            Log.d(TAG, "onProductDisconnect");
                            showToast("Product Disconnected");

                        }
                        @Override
                        public void onProductConnect(BaseProduct baseProduct) {
                            Log.d(TAG, String.format("onProductConnect newProduct:%s", baseProduct));
                            showToast("Product Connected");

                        }

                        @Override
                        public void onProductChanged(BaseProduct baseProduct) {

                        }

                        @Override
                        public void onComponentChange(BaseProduct.ComponentKey componentKey, BaseComponent oldComponent,
                                                      BaseComponent newComponent) {

                            if (newComponent != null) {
                                newComponent.setComponentListener(new BaseComponent.ComponentListener() {

                                    @Override
                                    public void onConnectivityChange(boolean isConnected) {
                                        Log.d(TAG, "onComponentConnectivityChanged: " + isConnected);
                                    }
                                });
                            }
                            Log.d(TAG,
                                    String.format("onComponentChange key:%s, oldComponent:%s, newComponent:%s",
                                            componentKey,
                                            oldComponent,
                                            newComponent));

                        }
                        @Override
                        public void onInitProcess(DJISDKInitEvent djisdkInitEvent, int i) {

                        }

                        @Override
                        public void onDatabaseDownloadProgress(long l, long l1) {

                        }
                    });
                }
            });
        }
    }

    protected BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            updateTitleBar();
        }
    };

    public void showToast(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateTitleBar() {
        if(mConnectStatusTextView == null) return;
        boolean ret = false;
        BaseProduct product = DJISimulatorApplication.getProductInstance();
        if (product != null) {
            if(product.isConnected()) {
                //The product is connected
                mConnectStatusTextView.setText(DJISimulatorApplication.getProductInstance().getModel() + " Connected");
                initFlightController();
                ret = true;
            } else {
                if(product instanceof Aircraft) {
                    Aircraft aircraft = (Aircraft)product;
                    if(aircraft.getRemoteController() != null && aircraft.getRemoteController().isConnected()) {
                        // The product is not connected, but the remote controller is connected
                        mConnectStatusTextView.setText("only RC Connected");
                        ret = true;
                    }
                }
            }
        }

        if(!ret) {
            // The product or the remote controller are not connected.
            mConnectStatusTextView.setText("Disconnected");
        }
    }

    @Override
    public void onResume() {
        Log.e(TAG, "onResume");
        super.onResume();
        showToast("onResume");
        if(mFlightController == null && mSendVirtualStickDataTimer == null && parkourTimer == null) {
            initFlightController();     //#################  Maybe Take Off ?  #####################//
        }
    }

    @Override
    public void onPause() {
        Log.e(TAG, "onPause");
        super.onPause();
    }

    @Override
    public void onStop() {
        Log.e(TAG, "onStop");
        super.onStop();
    }

    public void onReturn(View view){
        Log.e(TAG, "onReturn");
        showToast("onReturn");
        this.finish();
    }

    @Override
    protected void onDestroy() {
        Log.e(TAG, "onDestroy");
        showToast("onDestroy");
        if(parkourTimer != null || mSendVirtualStickDataTimer != null){ clearGarbage();}
        super.onDestroy();
    }

    private void clearGarbage(){
        showToast("Clearing Garbage");
        unregisterReceiver(mReceiver);
        if (null != mSendVirtualStickDataTimer) {
            mSendVirtualStickDataTask.cancel();     //Really necessary??
            mSendVirtualStickDataTask = null;
            mSendVirtualStickDataTimer.cancel();    //Terminates this timer, discarding any currently scheduled tasks.
            mSendVirtualStickDataTimer.purge();     //Removes all cancelled tasks from this timer's task queue.
            mSendVirtualStickDataTimer = null;
            parkourTimer.cancel();      //Terminates this timer, discarding any currently scheduled tasks.
            parkourTimer.purge();       //Removes all cancelled tasks from this timer's task queue.
            parkourTimer = null;
        }
    }

    private void initFlightController() {

        Aircraft aircraft = DJISimulatorApplication.getAircraftInstance();
        if (aircraft == null || !aircraft.isConnected()) {
            mFlightController = null;
            return;
        } else {
            mFlightController = aircraft.getFlightController();
            iniFlag();      //Initialisation du flag
        }
    }

    private void iniFlag(){
        mFlightController.setRollPitchControlMode(rollPitchControlMode);
        mFlightController.setYawControlMode(yawControlMode);
        mFlightController.setVerticalControlMode(verticalControlMode);
        mFlightController.setRollPitchCoordinateSystem(flightCoordinateSystem);
        mFlightController.setVirtualStickAdvancedModeEnabled(virtualStickAdvancedModeEnabled);
        showToast("Flag is set");
    }

    private void initUI() {

        mBtnTakeOff = (Button) findViewById(R.id.btn_take_off);
        mBtnLand = (Button) findViewById(R.id.btn_go);
        mConnectStatusTextView = (TextView) findViewById(R.id.ConnectStatusTextView);

        mBtnTakeOff.setOnClickListener(this);
        mBtnLand.setOnClickListener(this);

    }

    @Override
    public void onClick(View v) {

        switch (v.getId()) {
            case R.id.btn_take_off:
                if (mFlightController != null){
                    mFlightController.startTakeoff(
                            new CommonCallbacks.CompletionCallback() {
                                @Override
                                public void onResult(DJIError djiError) {
                                    if (djiError != null) {
                                        showToast(djiError.getDescription());
                                    } else {
                                        showToast("Take off Success");
                                    }
                                }
                            }
                    );
                }

                break;

            case R.id.btn_go:
                if (mFlightController != null){
                    showToast("Go!");
                    enableVirtualStick();
                    startParkour();
                }else{
                    showToast("mFlightController == null");
                }

                break;
            default:
                break;
        }
    }

    private void enableVirtualStick(){
        //Task pour controller les virtualsticks
        if (null == mSendVirtualStickDataTimer) {
            mSendVirtualStickDataTask = new SendVirtualStickDataTask();
            mSendVirtualStickDataTimer = new Timer();
            mSendVirtualStickDataTimer.schedule(mSendVirtualStickDataTask, 100, 200);
        }

        if (mFlightController != null){

            mFlightController.setVirtualStickModeEnabled(true, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError != null){
                        showToast(djiError.getDescription());
                    }else
                    {
                        showToast("Enable Virtual Stick Success");
                    }
                }
            });

        }
    }

    public void startParkour(){

        int t = 8000;  //time
        int d = 3000;   //delay

        if (parkourTimer == null) {

            parkourTimer = new Timer();
            parkourTimer.schedule(new TakeOff(), 1000); //Décollage

            parkourTimer.schedule(new Forward(), t);  //Avance de 350cm vers l'avant
            /* Forward */ t+=4550;
            parkourTimer.schedule(new StopMovement(),t);
            t+=d;
            parkourTimer.schedule(new RotateClockWise(), t);  //Rotation de 90 degrée sens horaire
            /* RotateClockWise */ t+=5100;
            parkourTimer.schedule(new StopMovement(),t);
            t+=d;
            parkourTimer.schedule(new Forward(),t);   //Avance de 135cm vers l'avant
            /* Forward */ t+=2720;
            parkourTimer.schedule(new StopMovement(),t);
            t+=d;
            parkourTimer.schedule(new Task1(),t);//Rotation autour du poto
            /* Task 1 */ t+=7280;
            parkourTimer.schedule(new StopMovement(),t);    //à 1m de distance
            t+=d;
            parkourTimer.schedule(new Backward(),t);   //Recule de 636cm
            /* Backward */ t+=11000;
            parkourTimer.schedule(new StopMovement(),t);
            t+=d;
            parkourTimer.schedule(new RotateCounterClockWise(),t);   //Rotation pour faire dos au poto #2
            /* RotateCounterClock */ t+=8000;
            parkourTimer.schedule(new StopMovement(),t);
            t+=d;
            parkourTimer.schedule(new Task2(),t);   //Rotation autour du poto à 190cm de
            /* Task 2 */ t+=9200;
            parkourTimer.schedule(new StopMovement(),t);                //distance
            t+=d;
            parkourTimer.schedule(new RotateCounterClockWise(), t);  //Rotation de 135 degrée sens horaire
            /* RotateCounterClock */ t+=2650;
            parkourTimer.schedule(new StopMovement(),t);
            t+=d;

            //####################### Début du S ################################################
            parkourTimer.schedule(new Forward(), t);  //Se place pour poto #3
            /* Forward */ t+=6000;
            parkourTimer.schedule(new Task3(), t);  //Début du S
            /* Task 3 */ t+=4500;
            parkourTimer.schedule(new Forward(),t); //Milieu du S
            /* Forward */ t+=3900;
            parkourTimer.schedule(new Task4(),t);   //Fin du S
            /* Task 4 */ t+=2025;
            parkourTimer.schedule(new Forward(), t);  //Se place pour poto #3
            /* Forward */ t+=8400;
            parkourTimer.schedule(new StopMovement(),t);
            t+=d;
            //######################### Fin du S ################################################

            parkourTimer.schedule(new RotateClockWise(),t);
            /* RotateClockWise */ t+=5250;
            parkourTimer.schedule(new StopMovement(),t);
            t+=d;
            parkourTimer.schedule(new Task1(),t);//Rotation autour du poto
            /* Task 1 */ t+=6616;
            parkourTimer.schedule(new StopMovement(),t);    //à 1m de distance
            t+=d;

            /*  Back to Start  */
            parkourTimer.schedule(new RotateCounterClockWise(),t);
            /* RotateCounterClock */ t+=1500;
            parkourTimer.schedule(new StopMovement(),t);
            t+=d;
            parkourTimer.schedule(new Forward(),t);//Rotation autour du poto
            /* Forward */ t+=15500;
            parkourTimer.schedule(new StopMovement(),t);    //à 1m de distance
            t+=d;
            parkourTimer.schedule(new RotateCounterClockWise(),t);
            /* RotateCounterClock */ t+=9200;
            parkourTimer.schedule(new StopMovement(),t);
            t+=d;

            parkourTimer.schedule(new StartLanding(), t);   //Atterrissage
            t+=d;
            parkourTimer.schedule(new ConfirmLanding(), t);
            t+=d;
            parkourTimer.schedule(new EndParkour(), t);

        }
    }

    class SendVirtualStickDataTask extends TimerTask {

        @Override
        public void run() {

            if (mFlightController != null) {
                mFlightController.sendVirtualStickFlightControlData(new FlightControlData((float)roll,
                                (float)pitch,
                                (float)yaw,
                                (float)1.5),
                        new CommonCallbacks.CompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {

                            }
                        });
            }
        }
    }

    private class TakeOff extends TimerTask {
        @Override
        public void run() {
            mFlightController.startTakeoff(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                }
            });
        }
    }

    //Mouvement vers l'avant
    private class Forward extends TimerTask {
        @Override
        public void run() {
            mFlightController.setVirtualStickModeEnabled(true, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                }
            });
            roll = 0;   //Requis pour le S
            pitch = 0.5;
            yaw = 0;    //Requis pour le S
        }
    }

    //Mouvement recule
    private class Backward extends TimerTask {
        @Override
        public void run() {
            mFlightController.setVirtualStickModeEnabled(true, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                }
            });
            pitch = -0.5;
        }
    }

    //Rotation horaire
    private class RotateClockWise extends TimerTask {
        @Override
        public void run() {
            mFlightController.setVirtualStickModeEnabled(true, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                }
            });
            yaw = 18;
        }
    }

    //Rotation antihoraire
    private class RotateCounterClockWise extends TimerTask {
        @Override
        public void run() {
            mFlightController.setVirtualStickModeEnabled(true, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                }
            });
            yaw = -18;
        }
    }

    //Mouvement rotation autour du poto #1
    private class Task1 extends TimerTask {
        @Override
        public void run() {
            mFlightController.setVirtualStickModeEnabled(true, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                }
            });
            roll = -0.5;
            yaw = 28.5;
        }
    }

    //Rotation autour du deuxième poto
    private class Task2 extends TimerTask {
        @Override
        public void run() {
            mFlightController.setVirtualStickModeEnabled(true, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                }
            });
            roll = -0.7;
            yaw = -16;
        }
    }

    //Première partie du S
    private class Task3 extends TimerTask {
        @Override
        public void run() {
            mFlightController.setVirtualStickModeEnabled(true, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                }
            });
            pitch = 0.5;
            yaw = -22.5;
        }
    }

    //Deuxième partie du S
    private class Task4 extends TimerTask {
        @Override
        public void run() {
            mFlightController.setVirtualStickModeEnabled(true, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                }
            });
            pitch = 0.5;
            yaw = 22.5;
        }
    }

    //Arrêt du mouvement en cours
    private class StopMovement extends TimerTask {
        @Override
        public void run() {
            roll = 0;
            pitch = 0;
            yaw = 0;
            mFlightController.setVirtualStickModeEnabled(false, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                }
            });
        }
    }

    //Début de l'atterrissage
    private class StartLanding extends TimerTask {
        @Override
        public void run() {
            mFlightController.startLanding(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                }
            });
        }
    }

    //Fin de l'atterrissage
    private class ConfirmLanding extends TimerTask {
        @Override
        public void run() {
            mFlightController.confirmLanding(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                }
            });
        }
    }

    //Fin du parcours
    private class EndParkour extends TimerTask {
        @Override
        public void run() {
            roll = 0;
            pitch = 0;
            yaw = 0;

            mFlightController.setVirtualStickModeEnabled(false, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {

                }
            });

            clearGarbage();
        }
    }

}
