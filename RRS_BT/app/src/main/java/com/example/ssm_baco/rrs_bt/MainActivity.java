/*
 * Road Riding Service
 *
 * Copyright (c) 2015 Kim Yi-hyun
 *
 * This file is part of Bluetooth Service.
 *
 * This is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Bluetooth Service.; if not, write to the Free Software
 */

package com.example.ssm_baco.rrs_bt;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.support.v7.app.ActionBarActivity;
import android.telephony.PhoneStateListener;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Set;


public class MainActivity extends ActionBarActivity implements TextToSpeech.OnInitListener {

    private static final String TAG = "MainActivity";

    //Intent Eable Code
    private static final int REQUEST_ENABLE_BT = 3;

    //Activity
    private TextView mTextView;
    private Button mButtonSend;
    private EditText mEditTextInput;

    //Menu
    private Menu mMenu;

    //Dialog
    private ProgressDialog mLoadingDialog;
    private AlertDialog mDeviceListDialog;

    //Bluetooth Device List
    private LinkedList<BluetoothDevice> mBluetoothDevices = new LinkedList<BluetoothDevice>();
    private ArrayAdapter<String> mDeviceArrayAdapter;

    //Bluetooth Client
    private BluetoothSerialClient mClient;

    /**
     *TTS
     */
    TextToSpeech _tts;
    boolean _ttsActive = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mClient = BluetoothSerialClient.getInstance();
        if(mClient == null){
            Toast.makeText(this,"Fail : Get BluetoothAdapter",Toast.LENGTH_LONG).show();
            finish();
        }else{
            Toast.makeText(this,"Get BluetoothAdapter",Toast.LENGTH_LONG).show();
        }

        overflowMenuInActionBar();
        initProgressDialog();
        initDeviceListDialog();
        initWidget();

        TelephonyManager myTM = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
        myTM.listen(new PhoneStateListener() {

            public void onCallStateChanged(int state, String incommingNumber) {
                switch (state) {
                    case TelephonyManager.CALL_STATE_RINGING:
                        Log.d(TAG, " CALL_STATE_RINGING.:" + incommingNumber);
                        Toast.makeText(getApplicationContext(), incommingNumber, Toast.LENGTH_LONG).show();
                        sendStringData("#" + "0" + incommingNumber);
                        mEditTextInput.setText("");
                        break;
                    case TelephonyManager.CALL_STATE_IDLE:
                        Log.d(TAG, "CALL_STATE_IDLE ");
                        Toast.makeText(getApplicationContext(), "IDLE state", Toast.LENGTH_LONG).show();
                        sendStringData("#" + "0" + "!");
                        mEditTextInput.setText("");
                        break;

                    case TelephonyManager.CALL_STATE_OFFHOOK:
                        Toast.makeText(getApplicationContext(), "OFFHOOK state", Toast.LENGTH_LONG).show();
                        sendStringData("#" + "0" + "!");
                        Log.d(TAG, "CALL_STATE_OFFHOOK ");
                        break;
                }
            }
        }, PhoneStateListener.LISTEN_CALL_STATE);
    }

    /**
     * Uses Life Cycle
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        mClient.claer();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onPause() {
        mClient.cancelScan(getApplicationContext());

        /**
         * BroadcastReceiver
         */
        unregisterReceiver(MySMSReceiver);

        /**
         * TTS
         */
        try{
            if(_tts != null){
                _tts.stop();
                _ttsActive = false;
            }
        }catch (Exception e){

        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        /**
         * Enable BT
         */
        enableBluetooth();

        /**
         * BroadcastReceiver
         */
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.provider.Telephony.SMS_RECEIVED");
        registerReceiver(MySMSReceiver, filter);

        /**
         * TTS
         */
        _tts = new TextToSpeech(getApplicationContext(), this);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

        @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
            mMenu = menu;
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.

        /**basic**/
//        int id = item.getItemId();
//
//        //noinspection SimplifiableIfStatement
//        if (id == R.id.action_settings) {
//            return true;
//        }
//
//        return super.onOptionsItemSelected(item);
        boolean connect = mClient.isConnection();
        if(item.getItemId() == R.id.action_connect) {
            if (!connect) {
                mDeviceListDialog.show();
            } else {
                mBTHandler.close();
            }
            return true;
        } else {

            return true;
        }
    }

    /**
     * Activity
     */
    private void overflowMenuInActionBar(){
        ViewConfiguration config = ViewConfiguration.get(this);
        try {
            Field menuKeyField = ViewConfiguration.class.getDeclaredField("sHasPermanentMenuKey");
            if(menuKeyField != null){
                menuKeyField.setAccessible(true);
                menuKeyField.setBoolean(config,false);
            }
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private void initProgressDialog(){
        mLoadingDialog = new ProgressDialog(this);
        mLoadingDialog.setCancelable(false);
    }

    private void initWidget(){
        //Termainal Text View
        mTextView = (TextView)findViewById(R.id.textViewTerminal);
        mTextView.setMovementMethod(new ScrollingMovementMethod());

        //EditText
        mEditTextInput = (EditText)findViewById(R.id.editTextInput);

        //Send Button
        mButtonSend = (Button)findViewById(R.id.buttonSend);
        mButtonSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // sendStringData()
                sendStringData(mEditTextInput.getText().toString());
                mEditTextInput.setText("");
            }
        });
    }

    private void addText(String text){
        mTextView.append(text);
        final int scrollAmount = mTextView.getLayout().getLineTop(mTextView.getLineCount()) - mTextView.getHeight();
        if(scrollAmount > 0) {
            mTextView.scrollTo(0, scrollAmount);
        }else {
            mTextView.scrollTo(0, 0);
        }
    }

    public void onFrontButtonClicked(View v){
        sendStringData("#2F" + "100");
        String ttsText = "안녕하세요";
        mEditTextInput.setText("");
        _tts.setLanguage(Locale.KOREA);
        //int result = TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE;
        _ttsActive = true;
        _tts.speak(ttsText, TextToSpeech.QUEUE_FLUSH, null);
        Toast.makeText(this,ttsText,Toast.LENGTH_LONG).show();

    }

    public void onLeftButtonClicked(View v){
        sendStringData("#2L" + "300");
        mEditTextInput.setText("");
    }

    public void onRightButtonClicked(View v){
        sendStringData("#2R" + "300");
        mEditTextInput.setText("");
    }


    /**
     * SSM-BACO : Bluetooth
     */

    private void enableBluetooth(){
        BluetoothSerialClient btSet = mClient;
        btSet.enableBluetooth(this, new BluetoothSerialClient.OnBluetoothEnabledListener() {
            @Override
            public void onBluetoothEnabled(boolean success) {
                if(success){
                    getPairedDevices();
                }else {
                    finish();
                }
            }
        });
    }

    private void initDeviceListDialog(){
        mDeviceArrayAdapter = new ArrayAdapter<String>(getApplicationContext(), R.layout.item_device);
        ListView listView = new ListView(getApplicationContext());
        listView.setAdapter(mDeviceArrayAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                String item = (String) adapterView.getItemAtPosition(position);
                for (BluetoothDevice device : mBluetoothDevices) {
                    if (item.contains(device.getAddress())) {
                        connect(device);
                        mDeviceListDialog.cancel();
                    }
                }
            }
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select bluetooth device");
        builder.setView(listView);
        builder.setPositiveButton("Scan",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        /**
                         * ScanDevice
                         */
                        scanDevices();
                    }
                });
        mDeviceListDialog = builder.create();
        mDeviceListDialog.setCanceledOnTouchOutside(false);
    }

    private void scanDevices(){
        BluetoothSerialClient btSet = mClient;
        btSet.scanDevices(getApplicationContext(), new BluetoothSerialClient.OnScanListener() {
            String message = "";
            @Override
            public void onStart() {
                message = "Scan Start.";
                Log.d(TAG,message);
                Toast.makeText(getApplicationContext(),message,Toast.LENGTH_LONG).show();
                mLoadingDialog.show();
                message = "Scanning...";
                mLoadingDialog.setMessage(message);
                mLoadingDialog.setCancelable(true);
                mLoadingDialog.setCanceledOnTouchOutside(false);
                mLoadingDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        BluetoothSerialClient btSet = mClient;
                        btSet.cancelScan(getApplicationContext());
                    }
                });
            }

            @Override
            public void onFoundDevice(BluetoothDevice bluetoothDevice) {
                addDeviceToArrayAdapter(bluetoothDevice);
                message += "\n" + bluetoothDevice.getName() + "\n" + bluetoothDevice.getAddress();
                mLoadingDialog.setMessage(message);
            }

            @Override
            public void onFinish() {
                message="Scan finish.";
                Log.d(TAG,message);
                message = "";
                mLoadingDialog.cancel();
                mLoadingDialog.setCancelable(false);
                mLoadingDialog.setOnCancelListener(null);
                mDeviceListDialog.show();
            }
        });
    }

    private void connect(BluetoothDevice device){
        mLoadingDialog.setMessage("Connecting...");
        mLoadingDialog.setCancelable(false);
        mLoadingDialog.show();
        BluetoothSerialClient btSet = mClient;
        btSet.connect(getApplicationContext(), device, mBTHandler);
    }

    private BluetoothSerialClient.BluetoothStreamingHandler mBTHandler  = new BluetoothSerialClient.BluetoothStreamingHandler(){

        ByteBuffer mmByteBuffer = ByteBuffer.allocate(1024);

        @Override
        public void onError(Exception e) {
            mLoadingDialog.cancel();
            addText("Msg : Connection Error - "+ e.toString() + "\n");
            mMenu.getItem(0).setTitle("Connect"); //??
        }

        @Override
        public void onConnected() {
            addText("Msg : Connected. " + mClient.getConnectedDevice().getName() + "\n");
            mLoadingDialog.cancel();
            mMenu.getItem(0).setTitle("Disconnect");
        }

        @Override
        public void onDisconnected() {
            mMenu.getItem(0).setTitle("Connect");
            mLoadingDialog.cancel();
            addText("Msg : Disconnected.\n");
        }

        @Override
        public void onData(byte[] buffer, int length) {
            if(length == 0) return;
            if(mmByteBuffer.position() + length >= mmByteBuffer.capacity()){
                ByteBuffer newBuffer = ByteBuffer.allocate(mmByteBuffer.capacity() *2);
                newBuffer.put(mmByteBuffer.array(), 0, mmByteBuffer.position());
                mmByteBuffer = newBuffer;
            }
            mmByteBuffer.put(buffer, 0, length);
            if(buffer[length - 1] == '\0'){
                addText(mClient.getConnectedDevice().getName() + " : " + new String(mmByteBuffer.array(), 0, mmByteBuffer.position()) + '\n');
                mmByteBuffer.clear();
            }
        }
    };


    private void getPairedDevices() {
        Set<BluetoothDevice> devices =  mClient.getPairedDevices();
        for(BluetoothDevice device: devices) {
            addDeviceToArrayAdapter(device);
        }
    }

    private void addDeviceToArrayAdapter(BluetoothDevice device){
        if(mBluetoothDevices.contains(device)){
            mBluetoothDevices.remove(device);
            mDeviceArrayAdapter.remove(device.getName()+"\n"+device.getAddress());
        }
        mBluetoothDevices.add(device);
        mDeviceArrayAdapter.add(device.getName()+"\n"+device.getAddress());
        mDeviceArrayAdapter.notifyDataSetChanged();
    }

    //Bluetooth DataStream
    public void sendStringData(String data){
        data += '\0';
        byte[] buffer = data.getBytes();
        if(mBTHandler.write(buffer)){
            addText("Send : " + data + '\n');
        }

    }

    /**
     * SMS
     */
    BroadcastReceiver MySMSReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intentReceived) {
            String action = intentReceived.getAction();
            Bundle bundle = intentReceived.getExtras();
            if (intentReceived.getAction().equals("android.provider.Telephony.SMS_RECEIVED")) {
                Log.i(TAG, "SMS瑜� �닔�떊�븯���뒿�땲�떎.");

                // �슦�꽑�닚�쐞媛� �궙�� �떎瑜� SMS �닔�떊 �빋�뱾�씠 臾몄옄瑜� �쟾�떖諛쏆� 紐삵븯�룄濡� �쟾�떖�쓣 痍⑥냼�빀�땲�떎.
                abortBroadcast();

                // SMS 硫붿떆吏�瑜� �뙆�떛�빀�땲�떎.
                bundle = intentReceived.getExtras();
                Object messages[] = (Object[])bundle.get("pdus");
                SmsMessage smsMessage[] = new SmsMessage[messages.length];

                int smsCount = messages.length;
                for(int i = 0; i < smsCount; i++) {
                    // PDU �룷留룹쑝濡� �릺�뼱 �엳�뒗 硫붿떆吏�瑜� 蹂듭썝�빀�땲�떎.
                    smsMessage[i] = SmsMessage.createFromPdu((byte[])messages[i]);
                }

                // SMS �닔�떊 �떆媛� �솗�씤
                Date curDate = new Date(smsMessage[0].getTimestampMillis());
                Log.i(TAG, "SMS Timestamp : " + curDate.toString());

                // SMS 諛쒖떊 踰덊샇 �솗�씤
                String origNumber = smsMessage[0].getOriginatingAddress();
                Toast.makeText(getApplicationContext(), "SMS received : "+ origNumber, Toast.LENGTH_LONG).show();
                sendStringData("#" + "1" + origNumber);
                mEditTextInput.setText("");

                // SMS 硫붿떆吏� �솗�씤
                String message = smsMessage[0].getMessageBody().toString();
                Log.i(TAG, "SMS : " + origNumber + ", " + message);

            }
        }
    };

    @Override
    public void onInit(int status) {
        _ttsActive = status == TextToSpeech.SUCCESS;
        String msg = _ttsActive ? "Success init" : "Fail_init";
        Toast.makeText(this,msg,Toast.LENGTH_LONG).show();
    }
}
