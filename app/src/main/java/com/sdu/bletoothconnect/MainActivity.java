package com.sdu.bletoothconnect;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.sdu.bletoothconnect.uitls.BluetoothMessage;
import com.sdu.bletoothconnect.uitls.ParseLeAdvData;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;


public class MainActivity extends BasActivity implements OnClickListener {

    // 扫描蓝牙按钮
    private Button scan_btn;
    // 蓝牙适配器
    private BluetoothAdapter mBluetoothAdapter;

    //21以上的扫描回调
    private ScanCallback mScanCallback;  //扫描回调类

    //扫描装置
    private BluetoothLeScanner mBluetoothLeScanner;

    // 蓝牙信号强度
    private ArrayList<Integer> rssis;
    // 自定义Adapter
    LeDeviceListAdapter mleDeviceListAdapter;
    // listview显示扫描到的蓝牙信息
    ListView lv;
    // 描述扫描蓝牙的状态
    private boolean mScanning;
    private boolean scan_flag;
    private Handler mHandler; //操作类
    int REQUEST_ENABLE_BT = 1;
    // 蓝牙扫描时间
    private static final long SCAN_PERIOD = 4000;

    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //申请位置权限
        initPermissions();
        //初始化控件
        init();
        // 初始化蓝牙
        init_ble();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            setScanCallBack();
        scan_flag = true;
        // 自定义适配器
        mleDeviceListAdapter = new LeDeviceListAdapter();
        // 为listview指定适配器
        lv.setAdapter(mleDeviceListAdapter);
        /* listview点击函数 */
        lv.setOnItemClickListener((arg0, v, position, id) -> {
            // TODO Auto-generated method stub
            final BluetoothMessage bluetoothMessage = mleDeviceListAdapter
                    .getDevice(position);
            if (bluetoothMessage == null)
                return;
            final Intent intent = new Intent(MainActivity.this,
                    Ble_Activity.class);
            intent.putExtra(Ble_Activity.EXTRAS_DEVICE_NAME,
                    bluetoothMessage.getName() != null ? bluetoothMessage.getName() : bluetoothMessage.getDevice().getName());
            intent.putExtra(Ble_Activity.EXTRAS_DEVICE_ADDRESS,
                    bluetoothMessage.getDevice().getAddress());
            intent.putExtra(Ble_Activity.EXTRAS_DEVICE_RSSI,
                    rssis.get(position).toString());
            if (mScanning) {
                /* 停止扫描设备 */
                mBluetoothLeScanner.stopScan(mScanCallback);
                mScanning = false;
            }
            try {
                startActivity(intent);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void onClick(View v) {
        if (!isOpenGPS(this)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT);
            builder.setTitle("提示")
                    .setMessage("请前往打开手机的位置权限!")
                    .setCancelable(false)
                    .setPositiveButton("确定", (dialog, which) -> {
                        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        startActivityForResult(intent, 10);
                    }).show();
            return;
        }

        if (scan_flag) {
            mleDeviceListAdapter = new LeDeviceListAdapter();  //自定义适配器
            lv.setAdapter(mleDeviceListAdapter);  //设置listview为自定义适配器
            scanLeDevice(true);  //点击按键开始扫描设备
        } else {
            scanLeDevice(false);
            scan_btn.setText("扫描设备");
        }
    }

    @SuppressLint("MissingPermission")
    private void init_ble() {
        // 手机硬件支持蓝牙
        if (!getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "不支持BLE", Toast.LENGTH_SHORT).show();
            finish();
        }
        // 获取手机本地的蓝牙适配器
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        else
            mBluetoothAdapter = bluetoothManager.getAdapter();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        // 打开蓝牙权限
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(
                    BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    private void init() {
        scan_btn = this.findViewById(R.id.scan_dev_btn);
        scan_btn.setOnClickListener(this);  //按键监听
        lv = this.findViewById(R.id.lv);
        mHandler = new Handler();
    }

    private void initPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission_group.LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // 获取wifi连接需要定位权限,没有获取权限
            ActivityCompat.requestPermissions((Activity) this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_WIFI_STATE,
            }, 1);
        }
    }

    @SuppressLint("MissingPermission")
    private void scanLeDevice(boolean enable) {

        //将定义的蓝牙适配器进行版本适配操作
        if (mBluetoothAdapter == null) {
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        }

        //将蓝牙扫描器进行适配操作
        if (mBluetoothLeScanner == null) {
            mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        }


        if (enable) {
            // 10s后停止扫描
            mHandler.postDelayed(() -> {
                mScanning = false;
                scan_flag = true;
                scan_btn.setText("扫描设备");
                Log.i("SCAN", "stop.....................");
                mBluetoothLeScanner.stopScan(mScanCallback);
            }, SCAN_PERIOD);//10s后启动停止线程

            /* 开始扫描蓝牙设备，带mLeScanCallback 回调函数 */
            Log.i("SCAN", "begin.....................");
            mScanning = true;
            scan_flag = false;
            scan_btn.setText("停止扫描");
            mBluetoothLeScanner.startScan(mScanCallback);
        } else {
            Log.i("Stop", "stoping................");
            mScanning = false;
            mBluetoothLeScanner.stopScan(mScanCallback);
            scan_flag = true;
        }
    }

    /**
     * 低版本
     * 蓝牙扫描回调函数 实现扫描蓝牙设备，回调蓝牙BluetoothDevice，可以获取name MAC等信息
     **/
    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi, byte[] scanRecord) {
            runOnUiThread(() -> {
                // 讲扫描到设备的信息输出到listview的适配器
                BluetoothMessage bluetoothMessage = new BluetoothMessage(device);
                mleDeviceListAdapter.addDevice(bluetoothMessage, rssi);
                mleDeviceListAdapter.notifyDataSetChanged();
            });
        }
    };

    //高版本蓝牙回调函数
    private void setScanCallBack() {
        mScanCallback = new ScanCallback() {
            @SuppressLint("MissingPermission")
            @Override   //重写扫描结果函数
            public void onScanResult(int callbackType, final ScanResult result) {
                final BluetoothDevice device = result.getDevice();   //获取蓝牙设备
                final BluetoothMessage bluetoothMessage = new BluetoothMessage(device);   //获取蓝牙设备信息
                if (null != device && null != result.getScanRecord()) {
                    try {
                        if (device.getName() != null) {
                            byte[] name = ParseLeAdvData.adv_report_parse(ParseLeAdvData.BLE_GAP_AD_TYPE_COMPLETE_LOCAL_NAME, result.getScanRecord().getBytes());
                            if (name != null)
                                bluetoothMessage.setName(new String(name, "GBK"));
                        }
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                }
                runOnUiThread(() -> {
                    /* 讲扫描到设备的信息输出到listview的适配器 */
                    mleDeviceListAdapter.addDevice(bluetoothMessage, result.getRssi());
                    mleDeviceListAdapter.notifyDataSetChanged();
                });
            }

            @Override
            public void onScanFailed(final int errorCode) {
                super.onScanFailed(errorCode);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "扫描出错:" + errorCode, Toast.LENGTH_SHORT).show());
            }
        };
    }

    private boolean isOpenGPS(final Context context) {
        LocationManager locationManager
                = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        // GPS定位
        boolean gps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        // 网络服务定位
        boolean network = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        if (gps || network) {
            return true;
        }
        return false;

    }

    private class LeDeviceListAdapter extends BaseAdapter {
        private ArrayList<BluetoothMessage> mLeDevices;
        private LayoutInflater mInflator;
        public LeDeviceListAdapter() {
            super();
            rssis = new ArrayList<>();
            mLeDevices = new ArrayList<>();
            mInflator = getLayoutInflater();
        }


        public void addDevice(BluetoothMessage device, int rssi) {
            for (BluetoothMessage mLeDevice : mLeDevices) {
                if (mLeDevice.getDevice().getAddress().equals(device.getDevice().getAddress())) {
                    return;
                }
            }
            if (device.getName() != null) {
                mLeDevices.add(device);
                rssis.add(rssi);
            }
        }

        public BluetoothMessage getDevice(int position) {
            return mLeDevices.get(position);
        }

        public void clear() {
            mLeDevices.clear();
            rssis.clear();
        }

        @Override
        public int getCount() {
            return mLeDevices.size();
        }

        @Override
        public Object getItem(int i) {
            return mLeDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        /**
         * 重写getview
         **/
        @SuppressLint("MissingPermission")
        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {

            // General ListView optimization code.
            // 加载listview每一项的视图
            view = mInflator.inflate(R.layout.listitem, null);
            // 初始化三个textview显示蓝牙信息
            TextView deviceAddress = view
                    .findViewById(R.id.tv_deviceAddr);
            TextView deviceName = view
                    .findViewById(R.id.tv_deviceName);
            TextView rssi = view.findViewById(R.id.tv_rssi);

            BluetoothMessage bluetoothMessage = mLeDevices.get(i);
            deviceAddress.setText(bluetoothMessage.getDevice().getAddress());
            deviceName.setText(bluetoothMessage.getName() != null ? bluetoothMessage.getName() : bluetoothMessage.getDevice().getName());
            rssi.setText("" + rssis.get(i));

            return view;
        }
    }

}
