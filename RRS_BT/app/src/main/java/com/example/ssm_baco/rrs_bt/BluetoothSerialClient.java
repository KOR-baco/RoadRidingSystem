/*
 * Road Riding Service - Bluetooth Service
 *
 * Copyright (c) 2015 Kim Yi-hyun
 *
 * This file is part of Bluetooth Service.
 *
 * Bluetooth Service. is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * Bluetooth Service. is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Bluetooth Service.; if not, write to the Free Software
 */
package com.example.ssm_baco.rrs_bt;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

/**
 * Created by SSM-baco on 2015-11-28.
 */
public class BluetoothSerialClient {
    static final private String SERIAL_UUID = "00001101-0000-1000-8000-00805F9B34FB";
    static private BluetoothSerialClient sThis = null;

    private BluetoothAdapter mBluetoothAdapter;
    private OnBluetoothEnabledListener mOnBluetoothUpListener;
    private OnScanListener mOnScanListener;
    private BluetoothSocket mBluetoothSocket;
    private UUID mUUID = UUID.fromString(SERIAL_UUID);
    private AtomicBoolean mIsConnection = new AtomicBoolean(false);
    private ExecutorService mReadExecutor;
    private ExecutorService mWriteExecutor;
    private BluetoothStreamingHandler mBluetoothStreamingHandler;
    private Handler mMainHandler = new Handler(Looper.getMainLooper());
    private BluetoothDevice mConnectedDevice = null;
    private InputStream mInputStream;
    private OutputStream mOutputStream;


    /**
     * BluetoothSerialClient �� �̱� �ν��Ͻ��� �����´�.
     * @return BluetoothSerialClient �� �ν��Ͻ�. ���� ��������� ����� �� ���� ����� null.
     */
    public static BluetoothSerialClient getInstance() {
        if(sThis == null) {
            sThis = new BluetoothSerialClient();
        }
        if(sThis.mBluetoothAdapter == null) {
            sThis = null;
            return null;
        }



        return sThis;
    }

    private BluetoothSerialClient() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mReadExecutor = Executors.newSingleThreadExecutor();
        mWriteExecutor = Executors.newSingleThreadExecutor();

    }

    /**
     * ������ �ݰ� �ڿ��� �����Ѵ�.
     * �� ����� �ݵ�� ȣ���� ��� �Ѵ�.
     */
    public void claer() {
        close();
        mReadExecutor.shutdownNow();
        mWriteExecutor.shutdownNow();
        sThis = null;
    }

    /**
     * ��������� ��� ������ ���·� ������ش�. <br/>
     * ���� ��� ������ ������� ����� �����ִٸ�, ����ڷ� �Ͽ� ������� ��뿡 ���� ������ �ϰ� �ϴ� â�� ����Ѵ�.
     * @param context activity
     * @param onBluetoothEnabledListener ������� on/off �� ���� �̺�Ʈ.
     */
    public void enableBluetooth(Context context, OnBluetoothEnabledListener onBluetoothEnabledListener) {
        if(!mBluetoothAdapter.isEnabled()) {
            mOnBluetoothUpListener = onBluetoothEnabledListener;
            Intent intent = new Intent(context, BluetoothUpActivity.class);
            context.startActivity(intent);
        } else {
            onBluetoothEnabledListener.onBluetoothEnabled(true);
        }
    }




    /**
     * ��������� ��� ������ �������� Ȯ��.
     * @return false ��� ��������� off �� ���°ų� ����� �� ����.
     */
    public boolean isEnabled() {
        return mBluetoothAdapter.isEnabled();
    }


    /**
     * ������� ����̽��� �ø���� �����Ѵ�.
     * @param context
     * @param device ������� ����̽�. {@link getPairedDevices} �Ǵ� {@link scanDevices} �� ���Ͽ� ������ ������� ����̽� �ν��Ͻ�.
     * @param bluetoothStreamingHandler ������� ��Ʈ���� �ڵ鷯.
     * @return ���� ��������� ����� �� ���� ���¶�� false. {@link  enableBluetooth} �� ���Ͽ� ��������� ��� ������ ���·� �������� �Ѵ�.
     */
    public boolean connect(final Context context,final BluetoothDevice device, final BluetoothStreamingHandler bluetoothStreamingHandler) {
        if(!isEnabled()) return false;
        mConnectedDevice = device;
        mBluetoothStreamingHandler = bluetoothStreamingHandler;
        if(isConnection()) {
            mWriteExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        mIsConnection.set(false);
                        mBluetoothSocket.close();
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }  catch (IOException e) {
                        e.printStackTrace();
                    }
                    connect(context, device, bluetoothStreamingHandler);
                }
            });
        } else {
            mIsConnection.set(true);
            connectClient();
        }
        return true;
    }


    /**
     * ���ſ� �� �Ǿ��� ������� ����̽� ����� �����´�.
     * @return
     */
    public Set<BluetoothDevice> getPairedDevices() {
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        return pairedDevices;
    }

    /**
     * �ֺ��� �� ������� ����̽��� ��ĵ�Ѵ�.
     * @param context
     * @param OnScanListener ��������� ��ĵ �̺�Ʈ.
     * @return
     */
    public boolean scanDevices(Context context, OnScanListener OnScanListener) {
        if(!mBluetoothAdapter.isEnabled()) return false;
        if(mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
            try {
                context.unregisterReceiver(mDiscoveryReceiver);
            } catch(IllegalArgumentException e) {
                e.printStackTrace();
            }
        }
        mOnScanListener = OnScanListener;
        IntentFilter filterFound = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        filterFound.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filterFound.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        context.registerReceiver(mDiscoveryReceiver, filterFound);
        mBluetoothAdapter.startDiscovery();
        return true;
    }

    /**
     * ��ĵ�� ����Ѵ�.
     * @param context
     */
    public void cancelScan(Context context) {
        if(!mBluetoothAdapter.isEnabled() || !mBluetoothAdapter.isDiscovering()) return;
        mBluetoothAdapter.cancelDiscovery();
        try {
            context.unregisterReceiver(mDiscoveryReceiver);
        } catch(IllegalArgumentException e) {
            e.printStackTrace();
        }
        if(mOnScanListener != null) mOnScanListener.onFinish();
    }

    /**
     * ������� ����̽��� ���� �Ǿ��ִ����� �����´�.
     * @return true/false
     */
    public boolean isConnection() {
        return mIsConnection.get();
    }

    /**
     * ����� ������� ����̽��� �����´�.
     * @return ���� ����� ������� ����̽��� ���ٸ� null.
     */
    public BluetoothDevice getConnectedDevice() {
        return mConnectedDevice;
    }



    private void connectClient() {
        try {
            Method method;

            method = mConnectedDevice.getClass().getMethod("createRfcommSocket", new Class[] { int.class } );
            mBluetoothSocket = (BluetoothSocket) method.invoke(mConnectedDevice, 1);

            //mBluetoothSocket = mConnectedDevice.createInsecureRfcommSocketToServiceRecord(mUUID);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        mWriteExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    mBluetoothAdapter.cancelDiscovery();
                    mBluetoothSocket.connect();
                    manageConnectedSocket(mBluetoothSocket);
                    callConnectedHandlerEvent();
                    mReadExecutor.execute(mReadRunnable);
                } catch (final IOException e) {
                    close();
                    e.printStackTrace();
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mBluetoothStreamingHandler.onError(e);
                        }
                    });
                    mIsConnection.set(false);
                    try {
                        mBluetoothSocket.close();
                    } catch (Exception ec) {
                        ec.printStackTrace();
                    }
                }
            }
        });
    }


    private void manageConnectedSocket(BluetoothSocket socket) throws IOException {
        mInputStream =  socket.getInputStream();
        mOutputStream = socket.getOutputStream();
    }

    private void callConnectedHandlerEvent() {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                mBluetoothStreamingHandler.onConnected();
            }
        });
    }


    private boolean write(final byte[] buffer) {
        if(!mIsConnection.get()) return false;
        mWriteExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    mOutputStream.write(buffer);
                } catch (Exception e) {
                    close();
                    e.printStackTrace();
                    mBluetoothStreamingHandler.onError(e);
                }
            }
        });
        return true;
    }


    private boolean close() {
        mConnectedDevice = null;
        if(mIsConnection.get()) {
            mIsConnection.set(false);
            try {
                mBluetoothSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mMainHandler.post(mCloseRunable);
            return true;
        }
        return false;
    }

    private Runnable mCloseRunable = new Runnable() {
        @Override
        public void run() {
            if(mBluetoothStreamingHandler != null) {
                mBluetoothStreamingHandler.onDisconnected();
            }
        }
    };

    private Runnable mReadRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                final byte[] buffer = new byte[256];
                final int readBytes = mInputStream.read(buffer);
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if(mBluetoothStreamingHandler != null) {
                            mBluetoothStreamingHandler.onData(buffer ,readBytes);
                        }
                    }
                });
                mReadExecutor.execute(mReadRunnable);
            } catch (Exception e) {
                close();
                e.printStackTrace();
            }
        }
    };





    private BroadcastReceiver mDiscoveryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if(mOnScanListener != null) mOnScanListener.onFoundDevice(device);
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                if(mOnScanListener != null) mOnScanListener.onFinish();
                try {
                    context.unregisterReceiver(mDiscoveryReceiver);
                } catch(IllegalArgumentException e) {
                    e.printStackTrace();
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                if(mOnScanListener != null)  mOnScanListener.onStart();
            }
        }
    };



    public static class BluetoothUpActivity extends Activity {
        private static int REQUEST_ENABLE_BT = 2;
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getWindow().getDecorView().postDelayed(new Runnable() {
                @Override
                public void run() {
                    upbluetoothDevice();
                }
            }, 100);
        }
        private void upbluetoothDevice() {
            BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
            if (!btAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT) ;
            }
        }
        @Override
        protected void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if(requestCode == REQUEST_ENABLE_BT) {
                OnBluetoothEnabledListener onBluetoothEnabledListener = getInstance().mOnBluetoothUpListener;
                if (resultCode == Activity.RESULT_OK) {
                    if(onBluetoothEnabledListener != null)
                        onBluetoothEnabledListener.onBluetoothEnabled(true);
                    finish();
                } else {
                    if(onBluetoothEnabledListener != null)
                        onBluetoothEnabledListener.onBluetoothEnabled(false);
                    finish();
                }
            }
        }
    }
    // End BluetoothUpActivity


    public static interface OnBluetoothEnabledListener {
        public void onBluetoothEnabled(boolean success);
    }

    public static interface OnScanListener {
        public void onStart();
        public void onFoundDevice(BluetoothDevice bluetoothDevice);
        public void onFinish();
    }

    public abstract static class BluetoothStreamingHandler {
        public abstract void onError(Exception e);
        public abstract void onConnected();
        public abstract void onDisconnected();
        public abstract void onData(byte[] buffer, int length);
        public final boolean close() {
            BluetoothSerialClient btSet = getInstance();
            if(btSet != null)
                return btSet.close();
            return false;
        }
        public final boolean write(byte[] buffer) {
            BluetoothSerialClient btSet = getInstance();
            if(btSet != null)
                return btSet.write(buffer);
            return false;
        }
    }
}
