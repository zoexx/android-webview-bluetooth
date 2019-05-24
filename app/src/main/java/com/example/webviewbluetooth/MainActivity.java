package com.example.webviewbluetooth;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.content.Context;
import android.net.Uri;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.util.Log;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.github.lzyzsd.jsbridge.BridgeHandler;
import com.github.lzyzsd.jsbridge.BridgeWebView;
import com.github.lzyzsd.jsbridge.CallBackFunction;
import com.github.lzyzsd.jsbridge.DefaultHandler;

import com.google.gson.Gson;

import com.inuker.bluetooth.library.BluetoothClient;
import com.inuker.bluetooth.library.Constants;
import com.inuker.bluetooth.library.beacon.Beacon;
import com.inuker.bluetooth.library.connect.listener.BleConnectStatusListener;
import com.inuker.bluetooth.library.connect.listener.BluetoothStateListener;
import com.inuker.bluetooth.library.connect.options.BleConnectOptions;
import com.inuker.bluetooth.library.connect.response.BleConnectResponse;
import com.inuker.bluetooth.library.connect.response.BleWriteResponse;
import com.inuker.bluetooth.library.model.BleGattCharacter;
import com.inuker.bluetooth.library.model.BleGattProfile;
import com.inuker.bluetooth.library.model.BleGattService;
import com.inuker.bluetooth.library.receiver.listener.BleConnectStatusChangeListener;
import com.inuker.bluetooth.library.receiver.listener.BluetoothBondListener;
import com.inuker.bluetooth.library.search.SearchRequest;
import com.inuker.bluetooth.library.search.SearchResult;
import com.inuker.bluetooth.library.search.response.SearchResponse;
import com.inuker.bluetooth.library.utils.BluetoothLog;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, AdapterView.OnItemClickListener {

    private final String TAG = "MainActivity";

    BridgeWebView webView;

    BluetoothClient mBluetooth;
    String BDeviceMACAddress;
    String BTempData;
    CallBackFunction BCallBackFun;
    LinkedList<SearchResult> deviceList = new LinkedList<SearchResult>();
    HashMap<String, SearchResult> deviceTempMap = new HashMap<>();
    BluetoothDeviceListAdapter BAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = (BridgeWebView) findViewById(R.id.webView);

        webView.setDefaultHandler(new DefaultHandler());

        webView.loadUrl("file:///android_asset/demo.html");


        webView.registerHandler("sendDataViaBluetooth", new BridgeHandler() {

            @Override
            public void handler(String data, CallBackFunction function) {
                Log.i(TAG, "chooseColdWallet. data:" + data);
                // 如果数据大于1m 考虑保存在文件里面

                runOnUiThread(() -> {
                    BTempData = data;
                    showBluetoothView();
                });

                BCallBackFun = function ;
            }

        });

        mBluetooth = new BluetoothClient(this.getBaseContext());


        mBluetooth.registerBluetoothStateListener(mBluetoothStateListener);
        mBluetooth.registerBluetoothBondListener(mBluetoothBondListener);

        findViewById(R.id.open_bluetooth_button).setOnClickListener(this);
        findViewById(R.id.search_device_button).setOnClickListener(this);
        ListView deviceList = findViewById(R.id.device_list);
        deviceList.setOnItemClickListener(this);


    }

    @Override
    public void onClick(View view) {
        Log.d(TAG, "onClick: " + view.getId());
        switch (view.getId()) {
            case R.id.open_bluetooth_button:
                askForBluetoothPermission();
                mBluetooth.openBluetooth();
                break;
            case R.id.search_device_button:
                bluetoothSearchDevice();
                break;
                default:
                    break;
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Log.d(TAG, "onItemClick: "+ id);

        SearchResult device = deviceList.get(position);
        BDeviceMACAddress = device.getAddress();

        if (mBluetooth.getConnectStatus(BDeviceMACAddress) == Constants.STATUS_CONNECTED) {
            sendBLEMessage(BDeviceMACAddress, BTempData);
            return;
        }

        mBluetooth.registerConnectStatusListener(BDeviceMACAddress, mBluetoothConnectStatusListener);

        BleConnectOptions options = new BleConnectOptions.Builder()
                .setConnectRetry(3)   // 连接如果失败重试3次
                .setConnectTimeout(30000)   // 连接超时30s
                .setServiceDiscoverRetry(3)  // 发现服务如果失败重试3次
                .setServiceDiscoverTimeout(20000)  // 发现服务超时20s
                .build();

        mBluetooth.connect(BDeviceMACAddress, options, new BleConnectResponse() {
            @Override
            public void onResponse(int code, BleGattProfile profile) {
                Log.d(TAG, "connect Response: "+ code );
                List<BleGattService> bleServices = profile.getServices();
                for(int i = 0; i < bleServices.size(); i++) {
                    BleGattService mService = bleServices.get(i);
                    List<BleGattCharacter> characters = mService.getCharacters();
                    Log.d(TAG, "bleService uuid: "+ mService.getUUID() );
                    for(int n = 0; n < characters.size(); n++) {
                        Log.d(TAG, "bleService characters uuid: "+ characters.get(n).getUuid() );
                    }
                }
                if (code == Constants.REQUEST_SUCCESS) {
                    // update view
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "连接成功", Toast.LENGTH_SHORT).show();
                    });
                } else {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "连接失败：" + String.valueOf(code), Toast.LENGTH_SHORT).show());
                }
            }
        });
    }


        @Override
    protected void onDestroy() {
        super.onDestroy();
        mBluetooth.unregisterBluetoothStateListener(mBluetoothStateListener);
        mBluetooth.unregisterBluetoothBondListener(mBluetoothBondListener);
    }

    private void sendBLEMessage (String MAC, String msg) {
        String str = "\u001b\u002d\u0000";
        byte[] bytes = str.getBytes(StandardCharsets.ISO_8859_1);

//        mBluetooth.writeNoRsp(MAC, serviceUUID, characterUUID, bytes, new BleWriteResponse() {
//            @Override
//            public void onResponse(int code) {
//                if (code == Constants.REQUEST_SUCCESS) {
//                    // send success
//
//                }
//            }
//        });

    }

    public void showBluetoothView () {
        // step 1. open
            // open button
        // step 2. connect
            // device list, search button
        // step 3. choose one
        // show dialog
        LinearLayout mBluetoothDialog = findViewById(R.id.bluetooth_view);
        mBluetoothDialog.setVisibility(View.VISIBLE);
        Boolean isBluetoothOpened = mBluetooth.isBluetoothOpened();
        TextView bluetoothTips = findViewById(R.id.bluetooth_tips);
        if (!isBluetoothOpened) {
            bluetoothTips.setText("请打开蓝牙");
        } else {
            int status = mBluetooth.getConnectStatus(BDeviceMACAddress);
            if ( BDeviceMACAddress != null && status == Constants.STATUS_CONNECTED ){
                    bluetoothTips.setText("已连接");
            } else {
                bluetoothTips.setText("未连接到设备，点击 搜索 开始查找设备，点击设备开始连接");
            }
        }
        ListView device_list = findViewById(R.id.device_list);
        BluetoothDeviceListAdapter adapter = new BluetoothDeviceListAdapter(deviceList, this.getBaseContext());
        device_list.setAdapter(adapter);
        BAdapter = adapter;
    };

    private final BleConnectStatusListener mBluetoothConnectStatusListener = new BleConnectStatusListener () {

        @Override
        public void onConnectStatusChanged(String mac, int status) {
            BluetoothLog.v(String.format("onConnectStatusChanged: %s\n%s", mac, status));
            if (status == Constants.STATUS_CONNECTED) {
                runOnUiThread(() -> {
                    TextView bluetoothTips = findViewById(R.id.bluetooth_tips);
                    bluetoothTips.setText("已连接设备：" + mac);
                    if (BTempData.length() > 0) {
                        sendBLEMessage(mac, BTempData);
                    }
                });
            } else if (status ==Constants.STATUS_DISCONNECTED) {
                runOnUiThread(() -> {
                    TextView bluetoothTips = findViewById(R.id.bluetooth_tips);
                    bluetoothTips.setText("已断开连接：" + mac);
                });
            }
        }
    };

    private final BluetoothBondListener mBluetoothBondListener = new BluetoothBondListener() {
        @Override
        public void onBondStateChanged(String mac, int bondState) {
            // bondState = Constants.BOND_NONE, BOND_BONDING, BOND_BONDED
            BluetoothLog.v(String.format("onBondStateChanged: %s\n%s", mac, bondState));
        }
    };


    private final BluetoothStateListener mBluetoothStateListener = new BluetoothStateListener() {
        @Override
        public void onBluetoothStateChanged(boolean openOrClosed) {
            // update view
            BluetoothLog.v(String.format("onBluetoothStateChanged openOrClosed: %s", openOrClosed));
            if (openOrClosed) {

                runOnUiThread(() -> {
                    Button openbtn = findViewById(R.id.open_bluetooth_button);
                    openbtn.setText("蓝牙已开启");
                    bluetoothSearchDevice();
                });
            } else {
                runOnUiThread(() -> {
                    Button openbtn = findViewById(R.id.open_bluetooth_button);
                    openbtn.setText("打开蓝牙");
                    TextView bluetoothTips = findViewById(R.id.bluetooth_tips);
                    bluetoothTips.setText("请打开蓝牙");
                });
            }

        }

    };

    public void askForBluetoothPermission () {
        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED){
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)){
                Toast.makeText(this, "The permission to get BLE location data is required", Toast.LENGTH_SHORT).show();
            }else{
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 1);
                }
            }
        }else{
            Toast.makeText(this, "Location permissions already granted", Toast.LENGTH_SHORT).show();
        }
    }

    // click search
    public void bluetoothSearchDevice () {
        if (!mBluetooth.isBluetoothOpened()) {
            Toast.makeText(this, "蓝牙未开启", Toast.LENGTH_SHORT).show();
            return;
        }
        SearchRequest request = new SearchRequest.Builder()
                .searchBluetoothLeDevice(3000, 3)   // 先扫BLE设备3次，每次3s
                .searchBluetoothClassicDevice(5000) // 再扫经典蓝牙5s
                .searchBluetoothLeDevice(2000)      // 再扫BLE设备2s
                .build();

        mBluetooth.search(request, new SearchResponse() {
            @Override
            public void onSearchStarted() {
                runOnUiThread(() -> {
                    deviceTempMap.clear();
                    Button searchBtn = findViewById(R.id.search_device_button);
                    searchBtn.setText("正在搜索...");
                });
            }

            @Override
            public void onDeviceFounded(SearchResult device) {
                Beacon beacon = new Beacon(device.scanRecord);
                BluetoothLog.v(String.format("beacon for %s\n%s\n%s", device.getAddress(), beacon.toString(), device.getName()));
                // update view
                // test: connect the first one
                runOnUiThread(()-> deviceTempMap.put(device.getAddress(), device));
            }

            @Override
            public void onSearchStopped() {
                BluetoothLog.v("onSearchStopped");
                runOnUiThread(() -> {
                    deviceList.clear();
                    for (Object mac : deviceTempMap.keySet()) {
                        deviceList.add((SearchResult) deviceTempMap.get(mac));
                    }
                    BAdapter.notifyDataSetChanged();
                    deviceTempMap.clear();
                    Button searchBtn = findViewById(R.id.search_device_button);
                    searchBtn.setText("搜索设备");
                });
            }

            @Override
            public void onSearchCanceled() {
                BluetoothLog.v("onSearchCanceled");
            }
        });
    }
}
