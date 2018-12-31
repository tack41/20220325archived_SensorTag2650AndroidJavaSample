package local.teamc.sensortagsample;

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
import java.text.ParsePosition;
import java.util.Date;
import java.util.List;
import android.Manifest;
import java.text.SimpleDateFormat;
import android.content.pm.PackageManager;

import org.w3c.dom.Text;

public class MainActivity extends AppCompatActivity {

    TextView _textViewStatusValue;
    TextView _textViewButtonValue;
    TextView[] _textViewHistory = new TextView[3];
    Handler _handler;
    BluetoothManager _bluetoothManager;
    BluetoothAdapter _bluetoothAdapter;
    BluetoothLeScanner _bleScanner;

    String[] _statusHistory = new String[3+1]; // 現在の状態+3履歴
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

        _textViewStatusValue = (TextView)findViewById(R.id.textview_status_value);
        _textViewButtonValue = (TextView)findViewById(R.id.textview_button_value);
        _textViewHistory[0] = (TextView)findViewById(R.id.textview_history1);
        _textViewHistory[1] = (TextView)findViewById(R.id.textview_history2);
        _textViewHistory[2] = (TextView)findViewById(R.id.textview_history3);

        _bluetoothManager = (BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
        _bluetoothAdapter = _bluetoothManager.getAdapter();
        _bleScanner = _bluetoothAdapter.getBluetoothLeScanner();

        findViewById(R.id.button_connect).setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                showStatus("connectボタンが押されました");
                scan();
            }
        });

        initializeHistory();
    }

    private void initializeHistory(){
        for(int i=0; i<_statusHistory.length; i++){
            _statusHistory[i] = null;
        }
        _statusCurrentIndex = -1;

        for(int i=0; i<_textViewHistory.length; i++){
            _textViewHistory[i].setText("");
        }
    }

    private void showStatus(String message){
        _textViewStatusValue.setText(message);
        _statusCurrentIndex++;
        if(_statusCurrentIndex >= _statusHistory.length){
            _statusCurrentIndex = 0;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        _statusHistory[_statusCurrentIndex] = "(" + sdf.format(new Date()) + ")" + message;

        _handler.post(new Runnable() {
            @Override
            public void run() {
                for(int i=0; i<_textViewHistory.length; i++){
                    int statusHistoryIndex = _statusCurrentIndex - 1 - i;
                    if(statusHistoryIndex < 0){
                        statusHistoryIndex += _statusHistory.length;
                    }
                    if(_statusHistory[statusHistoryIndex] != null){
                        _textViewHistory[i].setText(_statusHistory[statusHistoryIndex]);
                    }
                }
            }
        });
    }
    private void scan(){
        _bleScanStopping = false;

        //SCAN_PERIODで指定した時間が過ぎたらscanを停止する
        _handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                _bleScanStopping = true;
                _bleScanner.stopScan(_scanCallback);
            };
        }, SCAN_PERIOD);

        _bleScanner.startScan(_scanCallback);
        showStatus("scanを開始しました");
    }

    private ScanCallback _scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            if(_bleScanStopping){
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

                    //connect
//                        connect(result.getDevice());
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
}
