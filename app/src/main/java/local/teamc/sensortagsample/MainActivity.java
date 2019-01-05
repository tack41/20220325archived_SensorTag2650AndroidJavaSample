package local.teamc.sensortagsample;

import android.bluetooth.BluetoothDevice;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.os.Handler;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothProfile;
import java.util.Date;
import java.util.List;
import android.Manifest;
import java.text.SimpleDateFormat;
import java.util.UUID;

import android.content.pm.PackageManager;

import static java.lang.Math.pow;
import java.util.TimerTask;
import java.util.Timer;

public class MainActivity extends AppCompatActivity {

    final UUID UUID_CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    final UUID UUID_KEY_SERV = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    final UUID UUID_KEY_DATA = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");

    final UUID UUID_OPT_SERV = UUID.fromString("f000aa70-0451-4000-b000-000000000000");
    final UUID UUID_OPT_DATA = UUID.fromString("f000aa71-0451-4000-b000-000000000000");
    final UUID UUID_OPT_CONF = UUID.fromString("f000aa72-0451-4000-b000-000000000000"); // 0: disable, 1: enable
    final UUID UUID_OPT_PERI = UUID.fromString("f000aa73-0451-4000-b000-000000000000"); // Period in tens of milliseconds

    final UUID UUID_MOV_SERV = UUID.fromString("f000aa80-0451-4000-b000-000000000000");
    final UUID UUID_MOV_DATA = UUID.fromString("f000aa81-0451-4000-b000-000000000000");
    final UUID UUID_MOV_CONF = UUID.fromString("f000aa82-0451-4000-b000-000000000000"); // 0: disable, bit 0: enable x, bit 1: enable y, bit 2: enable z
    final UUID UUID_MOV_PERI = UUID.fromString("f000aa83-0451-4000-b000-000000000000"); // Period in tens of milliseconds

    TextView _textViewStatusValue;
    TextView _textViewButtonValue;
    TextView _textViewOpticalValue;
    TextView _textViewMovementValue;
    TextView[] _textViewHistory = new TextView[3];
    Handler _handler;
    BluetoothManager _bluetoothManager;
    BluetoothAdapter _bluetoothAdapter;
    BluetoothLeScanner _bleScanner;
    BluetoothGatt _bluetoothGatt;
    boolean _bleDeviceConnected = false;

    double opticalValue = 0;
    double accX,accY,accZ;


    String[] _statusHistory = new String[3 + 1]; // 現在の状態+3履歴
    int _statusCurrentIndex = -1;

    //Bluetooth scanのタイムアウト時間(ms)
    final int SCAN_PERIOD = 10000;

    boolean _bleScanStopping = false;

    final String DEVICE_NAME = "SensorTag";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 権限がない場合はリクエスト
        if (checkSelfPermission(Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH}, 1);
        }
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_ADMIN}, 1);
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
        }

        _handler = new Handler();

        _textViewStatusValue = (TextView) findViewById(R.id.textview_status_value);
        _textViewButtonValue = (TextView) findViewById(R.id.textview_button_value);
        _textViewOpticalValue = (TextView) findViewById(R.id.textview_optical_value);
        _textViewMovementValue = (TextView) findViewById(R.id.textview_movement_value);
        _textViewHistory[0] = (TextView) findViewById(R.id.textview_history1);
        _textViewHistory[1] = (TextView) findViewById(R.id.textview_history2);
        _textViewHistory[2] = (TextView) findViewById(R.id.textview_history3);

        _bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        _bluetoothAdapter = _bluetoothManager.getAdapter();
        _bleScanner = _bluetoothAdapter.getBluetoothLeScanner();

        findViewById(R.id.button_connect).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showStatus("connectボタンが押されました");
                scan();
            }
        });
        findViewById(R.id.button_disconnect).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showStatus("disconnectボタンが押されました");
                        disconnect();
                    }
                }
        );

        initializeHistory();
    }

    private void initializeHistory() {
        for (int i = 0; i < _statusHistory.length; i++) {
            _statusHistory[i] = null;
        }
        _statusCurrentIndex = -1;

        for (int i = 0; i < _textViewHistory.length; i++) {
            _textViewHistory[i].setText("");
        }
    }

    private void showStatus(String message) {
        _textViewStatusValue.setText(message);
        _statusCurrentIndex++;
        if (_statusCurrentIndex >= _statusHistory.length) {
            _statusCurrentIndex = 0;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        _statusHistory[_statusCurrentIndex] = "(" + sdf.format(new Date()) + ")" + message;

        _handler.post(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < _textViewHistory.length; i++) {
                    int statusHistoryIndex = _statusCurrentIndex - 1 - i;
                    if (statusHistoryIndex < 0) {
                        statusHistoryIndex += _statusHistory.length;
                    }
                    if (_statusHistory[statusHistoryIndex] != null) {
                        _textViewHistory[i].setText(_statusHistory[statusHistoryIndex]);
                    }
                }
            }
        });
    }

    private void scan() {
        _bleScanStopping = false;

        //SCAN_PERIODで指定した時間が過ぎたらscanを停止する
        _handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                _bleScanStopping = true;
                _bleScanner.stopScan(_scanCallback);
            }

            ;
        }, SCAN_PERIOD);

        _bleScanner.startScan(_scanCallback);
        showStatus("scanを開始しました");
    }

    private ScanCallback _scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            if (_bleScanStopping) {
                _handler.post(new Runnable() {
                    @Override
                    public void run() {
                        showStatus("scanを停止しました");
                    }
                });
                return;
            }

            // デバイスが見つかった！
            if (result != null && result.getDevice() != null && result.getDevice().getName() != null) {
                if (result.getDevice().getName().contains(DEVICE_NAME)) {
                    showStatus("SensorTagが見つかりました: " + result.getDevice().getAddress());
                    _bleScanStopping = true;
                    _bleScanner.stopScan(_scanCallback);

                    connect(result.getDevice());
                }
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
        }
    };

    private void connect(BluetoothDevice device) {
        _bluetoothGatt = device.connectGatt(this, false, _gattCallback);

    }

    private BluetoothGattCallback _gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status,
                                            int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            // 接続成功
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _bleDeviceConnected = true;
                showStatus("接続成功");

                discoverService(gatt);

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _bleDeviceConnected = false;
                showStatus("接続が切断されました");
            }

        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);

            if (status == BluetoothGatt.GATT_SUCCESS) {
/*                BluetoothGattService buttonService = gatt.getService(UUID_KEY_SERV);
                if (buttonService != null) {
                    BluetoothGattCharacteristic buttonChar = buttonService.getCharacteristic(UUID_KEY_DATA);
                    if (buttonChar != null) {
                        // Notification を要求する
                        boolean registered = gatt.setCharacteristicNotification(buttonChar, true);
                        // Characteristic の Notification 有効化
                        BluetoothGattDescriptor descriptor = buttonChar.getDescriptor(UUID_CLIENT_CHARACTERISTIC_CONFIG);
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        gatt.writeDescriptor(descriptor);
                        showStatus("KeyのNotificationを有効にしました");
                    }
                }*/
                BluetoothGattService opticalService = gatt.getService(UUID_OPT_SERV);
                if (opticalService != null) {
                    BluetoothGattCharacteristic opticalConfChar = opticalService.getCharacteristic(UUID_OPT_CONF);
                    if(opticalConfChar != null){
                        byte[] val = new byte[1];
                        val[0] = (byte)1;
                        opticalConfChar.setValue(val);
                        gatt.writeCharacteristic(opticalConfChar);
                    }
                }
            }
        }
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status){
            if(descriptor.getCharacteristic().getUuid().equals(UUID_KEY_DATA)){
                showStatus("KeyのNotificationを有効にしました");
            }else if(descriptor.getCharacteristic().getUuid().equals(UUID_OPT_DATA)){
                showStatus("OpticalのNotificationを有効にしました");

                showStatus("MOVを有効化します");
                BluetoothGattService movingService = gatt.getService(UUID_MOV_SERV);
                if (movingService != null) {
                    BluetoothGattCharacteristic movingConfChar = movingService.getCharacteristic(UUID_MOV_CONF);
                    if(movingConfChar != null){
                        byte[] val = new byte[]{(byte)0x7f,(byte)0xff};
                        movingConfChar.setValue(val);
                        gatt.writeCharacteristic(movingConfChar);
                    }
                }
            }else if(descriptor.getCharacteristic().getUuid().equals(UUID_MOV_DATA)){
                showStatus("MovementのNoticationを有効にしました");
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            if (UUID_KEY_DATA.equals(characteristic.getUuid())) {
                // SensorTag からは 2bit の値が渡される
                Byte value = characteristic.getValue()[0];
                final boolean left = (0 < (value & 0x02));
                final boolean right = (0 < (value & 0x01));
                _handler.post(new Runnable() {
                    @Override
                    public void run() {
                        _textViewButtonValue.setText("左:" + left + ", 右:" + right);
                    }
                });
            } else if(UUID_OPT_DATA.equals(characteristic.getUuid())){
                byte[] value = characteristic.getValue();
                Integer sfloat= shortUnsignedAtOffset(value, 0);

                int mantissa;
                int exponent;
                mantissa = sfloat & 0x0FFF;
                exponent = (sfloat >> 12) & 0xFF;

                double output;
                double magnitude = pow(2.0f, exponent);
                output = (mantissa * magnitude);

                opticalValue = output;
                _handler.post(new Runnable() {
                    @Override
                    public void run() {
                        _textViewOpticalValue.setText((opticalValue/100.0f) +" lux");
                    }
                });
            }else if(UUID_MOV_DATA.equals(characteristic.getUuid())){
                byte[] value = characteristic.getValue();
                {
                    // Range 8G
                    final float SCALE = (float) 4096.0;

                    accX = ((value[7]<<8) + value[6])/SCALE * -1;
                    accY = ((value[9]<<8) + value[8])/SCALE;
                    accZ = ((value[11]<<8) + value[10])/SCALE * -1;

                    _handler.post(new Runnable() {
                        @Override
                        public void run() {
                            _textViewMovementValue.setText(String.format("accX:%.3f, accY:%.3f, accZ:%.3f",accX,accY,accZ));
                        }
                    });
                }
            }

        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
        }

        private Integer shortUnsignedAtOffset(byte[] c, int offset) {
            Integer lowerByte = (int) c[offset] & 0xFF;
            Integer upperByte = (int) c[offset+1] & 0xFF;
            return (upperByte << 8) + lowerByte;
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic,
                                          int status)
        {
            if(characteristic.getUuid().equals(UUID_OPT_CONF)){
                if(status == BluetoothGatt.GATT_SUCCESS){
                    showStatus("Opticalを有効にしました");
                }

                // Notification を要求する
                BluetoothGattService opticalService = gatt.getService(UUID_OPT_SERV);
                BluetoothGattCharacteristic opticalDataChar = opticalService.getCharacteristic(UUID_OPT_DATA);
                boolean registered = gatt.setCharacteristicNotification(opticalDataChar, true);
                if(registered){
                    // Characteristic の Notification 有効化
                    BluetoothGattDescriptor descriptor = opticalDataChar.getDescriptor(UUID_CLIENT_CHARACTERISTIC_CONFIG);
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    gatt.writeDescriptor(descriptor);
                }
            }else if(characteristic.getUuid().equals(UUID_MOV_CONF)){
                showStatus("MOVを有効化しました");

                // Notification を要求する
                BluetoothGattService movementService = gatt.getService(UUID_MOV_SERV);
                BluetoothGattCharacteristic movementDataChar = movementService.getCharacteristic(UUID_MOV_DATA);
                boolean registered = gatt.setCharacteristicNotification(movementDataChar, true);
                if(registered){
                    // Characteristic の Notification 有効化
                    BluetoothGattDescriptor descriptor = movementDataChar.getDescriptor(UUID_CLIENT_CHARACTERISTIC_CONFIG);
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    gatt.writeDescriptor(descriptor);
                }

            }
        }
    };

    private void disconnect(){
        if(_bleDeviceConnected){
            _bluetoothGatt.disconnect();
        }
    }

    private void discoverService(BluetoothGatt gatt){
        gatt.discoverServices();
    }

}

