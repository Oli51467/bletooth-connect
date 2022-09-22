package com.sdu.bletoothconnect;

import static com.sdu.bletoothconnect.uitls.HexUtils.bytesToHexString;
import static com.sdu.bletoothconnect.uitls.HexUtils.hexString2ByteArray;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.sdu.bletoothconnect.service.BluetoothLeService;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Ble_Activity extends BasActivity implements OnClickListener {

    private final static String TAG = Ble_Activity.class.getSimpleName();
    //蓝牙4.0的UUID,其中0000ffe1-0000-1000-8000-00805f9b34fb
    public static String HEART_RATE_MEASUREMENT = "0000ffe1-0000-1000-8000-00805f9b34fb";
    public static String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    ;
    public static String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    public static String EXTRAS_DEVICE_RSSI = "RSSI";
    //蓝牙连接状态
    private boolean mConnected = false;
    private String status = "disconnected";
    //蓝牙名字
    private String mDeviceName;
    //蓝牙地址
    private String mDeviceAddress;
    //蓝牙信号值
    private String mRssi;
    private Bundle b;
    private String rev_str = "";
    //蓝牙service,负责后台的蓝牙服务
    private static BluetoothLeService mBluetoothLeService;
    //文本框，显示接受的内容
    private TextView rev_tv, connect_state;
    //发送按钮
    private Button send_btn;
    //前后左右控制按钮

    private Button bt_clear;

    //文本编辑框
    private EditText send_et, send_et_hex;
    private TextView command_show;
    private ScrollView rev_sv;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    //蓝牙特征值
    private static BluetoothGattCharacteristic target_chara = null;
    public static byte[] revDataForCharacteristic;
    private Handler mhandler = new Handler();

    private boolean sendHex = false;
    private boolean receptionHex = false;

    private boolean speed_adj = false;
    private boolean control_mode = false;

    private CheckBox sendCheckBox, receptionCheckBox, control;

    private Handler myHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                // 判断发送的消息
                case 1: {
                    // 更新View
                    String state = msg.getData().getString("connect_state");
                    connect_state.setText(state);
                    break;
                }
                case 2: {
                    command_show.setText("");
                }
            }
            return false;
        }
    });


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ble_activity);
        b = getIntent().getExtras();
        //从意图获取显示的蓝牙信息
        mDeviceName = b.getString(EXTRAS_DEVICE_NAME);
        mDeviceAddress = b.getString(EXTRAS_DEVICE_ADDRESS);
        mRssi = b.getString(EXTRAS_DEVICE_RSSI);

        /* 启动蓝牙service */
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        init();

        new Thread(new sendCommandThread()).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //解除广播接收器
        unregisterReceiver(mGattUpdateReceiver);
        mBluetoothLeService = null;
    }

    // Activity出来时候，绑定广播接收器，监听蓝牙连接服务传过来的事件
    @Override
    protected void onResume() {
        super.onResume();
        //绑定广播接收器
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            //根据蓝牙地址，建立连接
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    /**
     * @return void
     * @throws
     * @Title: init
     * @Description: TODO(初始化UI控件)
     */
    private void init() {
        rev_sv = this.findViewById(R.id.rev_sv);
        rev_tv = this.findViewById(R.id.rev_tv);
        connect_state = this.findViewById(R.id.connect_state);
        send_btn = this.findViewById(R.id.send_btn);
        send_et = this.findViewById(R.id.send_et);
        send_et_hex = findViewById(R.id.send_et_hex);

        command_show = findViewById(R.id.command_show);

        sendCheckBox = findViewById(R.id.button_group_send);
        receptionCheckBox = findViewById(R.id.button_group_reception);

        bt_clear = this.findViewById(R.id.bt_clear);

        control = this.findViewById(R.id.control);

        connect_state.setText(status);
        send_btn.setOnClickListener(this);
        receptionCheckBox.setOnClickListener(this);
        sendCheckBox.setOnClickListener(this);

        control.setOnClickListener(this);
        bt_clear.setOnClickListener(this);

        receptionHex = false;
        sendHex = false;

    }

    /* BluetoothLeService绑定的回调函数 */
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName,
                                       IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service)
                    .getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up
            // initialization.
            // 根据蓝牙地址，连接设备
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }

    };

    /**
     * 广播接收器，负责接收BluetoothLeService类发送的数据
     */
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action))//Gatt连接成功
            {
                mConnected = true;
                status = "connected";
                //更新连接状态
                updateConnectionState(status);
                System.out.println("BroadcastReceiver :" + "device connected");

            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED//Gatt连接失败
                    .equals(action)) {
                mConnected = false;
                status = "disconnected";
                //更新连接状态
                updateConnectionState(status);
                System.out.println("BroadcastReceiver :"
                        + "device disconnected");

            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED//发现GATT服务器
                    .equals(action)) {
                // Show all the supported services and characteristics on the
                // user interface.
                //获取设备的所有蓝牙服务
                displayGattServices(mBluetoothLeService
                        .getSupportedGattServices());
                System.out.println("BroadcastReceiver :"
                        + "device SERVICES_DISCOVERED");
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action))//有效数据
            {
                //处理发送过来的数据
                try {
                    if (intent.getExtras().getString(
                            BluetoothLeService.EXTRA_DATA) != null) {
                        displayData(intent.getExtras().getString(
                                BluetoothLeService.EXTRA_DATA), intent);
                        System.out.println("BroadcastReceiver onData:"
                                + intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    };

    /* 更新连接状态 */
    private void updateConnectionState(String status) {
        Message msg = new Message();
        msg.what = 1;
        Bundle b = new Bundle();
        b.putString("connect_state", status);
        msg.setData(b);
        //将连接状态更新的UI的textview上
        myHandler.sendMessage(msg);
        System.out.println("connect_state:" + status);

    }

    /* 意图过滤器 */
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter
                .addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    private void displayData(String rev_string, Intent intent) {
        try {
            byte[] data = intent.getByteArrayExtra("BLE_BYTE_DATA");
            if (data == null)
                System.out.println("data is null!!!!!!");
            if (receptionHex)
                rev_string = bytesToHexString(data);
            else
                rev_string = new String(data, 0, data.length, "GB2312");//GB2312编码
        } catch (UnsupportedEncodingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        rev_str += rev_string;
        //更新UI
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                rev_tv.setText(rev_str);
                rev_sv.scrollTo(0, rev_tv.getMeasuredHeight());
                System.out.println("rev:" + rev_str);
            }
        });

    }

    private void displayGattServices(List<BluetoothGattService> gattServices) {

        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = "unknown_service";
        String unknownCharaString = "unknown_characteristic";

        // 服务数据,可扩展下拉列表的第一级数据
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();

        // 特征数据（隶属于某一级服务下面的特征值集合）
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData = new ArrayList<ArrayList<HashMap<String, String>>>();

        // 部分层次，所有特征值集合
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {

            // 获取服务列表
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();

            // 查表，根据该uuid获取对应的服务名称。SampleGattAttributes这个表需要自定义。
            gattServiceData.add(currentServiceData);
            ArrayList<HashMap<String, String>> gattCharacteristicGroupData = new ArrayList<HashMap<String, String>>();

            // 从当前循环所指向的服务中读取特征值列表
            List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics();

            ArrayList<BluetoothGattCharacteristic> charas = new ArrayList<BluetoothGattCharacteristic>();

            // Loops through available Characteristics.
            // 对于当前循环所指向的服务中的每一个特征值
            for (final BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();

                if (gattCharacteristic.getUuid().toString().equals(HEART_RATE_MEASUREMENT)) {

                    // 测试读取当前Characteristic数据，会触发mOnDataAvailable.onCharacteristicRead()
                    mhandler.postDelayed(() -> {
                        mBluetoothLeService.setCharacteristicNotification(gattCharacteristic, true);
                        mBluetoothLeService.readCharacteristic(gattCharacteristic);
                    }, 200);
                    // 接受Characteristic被写的通知,收到蓝牙模块的数据后会触发mOnDataAvailable.onCharacteristicWrite()
                    mBluetoothLeService.setCharacteristicNotification(gattCharacteristic, true);
                    target_chara = gattCharacteristic;

                    // 设置数据内容
                    // 往蓝牙模块写入数据
                    //mBluetoothLeService.writeCharacteristic(gattCharacteristic);
                }
                List<BluetoothGattDescriptor> descriptors = gattCharacteristic
                        .getDescriptors();
                for (BluetoothGattDescriptor descriptor : descriptors) {
                    System.out.println("---descriptor UUID:"
                            + descriptor.getUuid());
                    // 获取特征值的描述
                    mBluetoothLeService.getCharacteristicDescriptor(descriptor);
                    mBluetoothLeService.setCharacteristicNotification(gattCharacteristic, true);
                }

                gattCharacteristicGroupData.add(currentCharaData);
            }
            // 按先后顺序，分层次放入特征值集合中，只有特征值
            mGattCharacteristics.add(charas);
            // 构件第二级扩展列表（服务下面的特征值）
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }
    }

    /**
     * 将数据分包
     **/
    public int[] dataSeparate(int len) {
        int[] lens = new int[2];
        lens[0] = len / 20;
        lens[1] = len % 20;
        return lens;
    }


    /*
     * 数据发送线程
     *
     * */
    public class sendCommandThread implements Runnable {
        public sendCommandThread() {
            super();
        }

        @Override
        public void run() {
            while (true) {
                control_mode = control.isChecked();
                if (!control_mode) {
                    Message msg = new Message();
                    msg.what = 2;
                    msg.setData(b);
                    //将连接状态更新的UI的textview上
                    myHandler.sendMessage(msg);
                }
                byte[] buff = null;
                try {
                    buff = command_show.getText().toString().getBytes("GB2312");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                int[] sendDatalens = dataSeparate(buff.length);
                Log.d("AppRunArrayLenght", "buff.length:" + buff.length);
                int length = 0;
                for (int i = 0; i < sendDatalens[0]; i++) {
                    byte[] dataFor20 = new byte[20];
                    for (int j = 0; j < 20; j++) {
                        dataFor20[j] = buff[i * 20 + j];
                        ++length;
                    }
                    Log.d("AppRunArrayLenght", "超出20");
                    target_chara.setValue(dataFor20);
                    mBluetoothLeService.writeCharacteristic(dataFor20);
                }
                if (sendDatalens[1] != 0) {
                    System.out.println("here2");
                    byte[] lastData = new byte[buff.length % 20];
                    for (int i = 0; i < sendDatalens[1]; i++) {
                        lastData[i] = buff[sendDatalens[0] * 20 + i];
                        ++length;
                    }
                    String str = null;
                    try {
                        str = new String(lastData, 0, sendDatalens[1], "GB2312");
                    } catch (UnsupportedEncodingException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }

                    if (target_chara == null) {
                        runOnUiThread(() -> Toast.makeText(Ble_Activity.this, "没建立连接，请检查设备...", Toast.LENGTH_SHORT).show());
                        return;
                    }
                    Log.d("AppRunArrayLenght", "总发送长: " + length);
                    mBluetoothLeService.writeCharacteristic(lastData);
                    mBluetoothLeService.startSend(target_chara);

                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    /*
     * 数据发送线程
     *
     * */
    public class sendDateThread implements Runnable {
        public sendDateThread() {
            super();
        }

        @Override
        public void run() {
            control_mode = control.isChecked();
            byte[] buff = null;
            try {
                if (!sendHex) {
                    buff = send_et.getText().toString().getBytes("GB2312");
                } else
                    buff = hexString2ByteArray(send_et_hex.getText().toString());
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

            int[] sendDatalens = dataSeparate(buff.length);
            Log.d("AppRunArrayLenght", "buff.length:" + buff.length);
            int length = 0;
            for (int i = 0; i < sendDatalens[0]; i++) {
                byte[] dataFor20 = new byte[20];
                for (int j = 0; j < 20; j++) {
                    dataFor20[j] = buff[i * 20 + j];
                    ++length;
                }
                mBluetoothLeService.writeCharacteristic(dataFor20);
            }
            if (sendDatalens[1] != 0) {
                byte[] lastData = new byte[buff.length % 20];
                for (int i = 0; i < sendDatalens[1]; i++) {
                    lastData[i] = buff[sendDatalens[0] * 20 + i];
                    ++length;
                }
                String str = null;
                try {
                    str = new String(lastData, 0, sendDatalens[1], "GB2312");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                if (target_chara == null) {
                    runOnUiThread(() -> Toast.makeText(Ble_Activity.this, "没建立连接，请检查设备...", Toast.LENGTH_SHORT).show());
                    return;
                }
                mBluetoothLeService.writeCharacteristic(lastData);
                mBluetoothLeService.startSend(target_chara);

            }
        }
    }

    /*
     * 发送按键的响应事件，主要发送文本框的数据
     */
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.send_btn:
                new Thread(new sendDateThread()).start();
                break;
            case R.id.button_group_reception:
                receptionHex = receptionCheckBox.isChecked();
                break;
            case R.id.button_group_send:
                sendHex = sendCheckBox.isChecked();
                initEdit(sendHex);
                break;
            case R.id.bt_clear:
                rev_str = " ";
                break;
            case R.id.control:
                send_et.setText("");
        }

    }

    private void initEdit(boolean isHex) {
        if (isHex) {
            Toast.makeText(mBluetoothLeService, "只能输入0到F的字符", Toast.LENGTH_SHORT).show();
            send_et.setVisibility(View.GONE);
            send_et_hex.setVisibility(View.VISIBLE);
            send_et_hex.setText("");
            send_et_hex.setFocusable(true);
            send_et_hex.setFocusableInTouchMode(true);
            send_et_hex.requestFocus();
            this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            send_et_hex.setSelection(send_et_hex.getText().toString().length());
        } else {
            send_et.setVisibility(View.VISIBLE);
            send_et_hex.setVisibility(View.GONE);
            send_et.setText("");
            send_et.setFocusable(true);
            send_et.setFocusableInTouchMode(true);
            send_et.requestFocus();
            this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            send_et.setSelection(send_et.getText().toString().length());
        }
    }
}

