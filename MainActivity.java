import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity implements LocationListener {

    private static final int LOCATION_PERMISSION_CODE = 100;

    // UI组件
    private TextView speedKmhTextView;
    private TextView speedMsTextView;
    private TextView accelerationTextView;
    private TextView satelliteTextView;
    private TextView maxSpeedTextView;
    private TextView minSpeedTextView;

    // 位置相关
    private LocationManager locationManager;
    private GnssStatus.Callback gnssCallback;

    // 状态记录
    private float maxSpeed = 0f;
    private float minSpeed = Float.MAX_VALUE;
    private int satelliteCount = 0;
    private long previousTime = 0;
    private float previousSpeed = 0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化视图
        speedKmhTextView = findViewById(R.id.speedKmhTextView);
        speedMsTextView = findViewById(R.id.speedMsTextView);
        accelerationTextView = findViewById(R.id.accelerationTextView);
        satelliteTextView = findViewById(R.id.satelliteTextView);
        maxSpeedTextView = findViewById(R.id.maxSpeedTextView);
        minSpeedTextView = findViewById(R.id.minSpeedTextView);

        // 初始化位置服务
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        // 设置卫星状态监听（Android 7.0+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            setupGnssListener();
        }

        checkLocationPermission();
    }

    @SuppressLint("MissingPermission")
    @RequiresApi(api = Build.VERSION_CODES.N)
    private void setupGnssListener() {
        gnssCallback = new GnssStatus.Callback() {
            @Override
            public void onSatelliteStatusChanged(GnssStatus status) {
                updateSatelliteCount(status);
            }
        };
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        locationManager.registerGnssStatusCallback(gnssCallback);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void updateSatelliteCount(GnssStatus status) {
        int count = 0;
        for (int i = 0; i < status.getSatelliteCount(); i++) {
            if (status.usedInFix(i)) {
                count++;
            }
        }
        satelliteCount = count;
        runOnUiThread(() -> satelliteTextView.setText("当前卫星数: " + satelliteCount));
    }

    private void checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_CODE);
        } else {
            startLocationUpdates();
        }
    }

    private void startLocationUpdates() {
        try {
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000,  // 1秒更新间隔
                    1,     // 1米距离变化
                    this);
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        if (location.hasSpeed()) {
            final float currentSpeedMs = location.getSpeed();
            final float currentSpeedKmh = currentSpeedMs * 3.6f;
            final long currentTime = System.currentTimeMillis();

            // 更新速度显示
            runOnUiThread(() -> {
                speedKmhTextView.setText(String.format("速度: %.1f km/h", currentSpeedKmh));
                speedMsTextView.setText(String.format("速度: %.1f m/s", currentSpeedMs));
            });

            // 计算加速度（需要有效的时间差）
            if (previousTime != 0) {
                float timeDelta = (currentTime - previousTime) / 1000f; // 转换为秒
                if (timeDelta > 0) {
                    float acceleration = (currentSpeedMs - previousSpeed) / timeDelta;
                    runOnUiThread(() -> accelerationTextView.setText(
                            String.format("加速度: %.2f m/s²", acceleration)));
                }
            }

            // 保存当前值供下次计算使用
            previousSpeed = currentSpeedMs;
            previousTime = currentTime;

            // 更新极值记录
            if (currentSpeedKmh > maxSpeed) {
                maxSpeed = currentSpeedKmh;
                maxSpeedTextView.setText(String.format("最大: %.1f km/h", maxSpeed));
            }
            if (currentSpeedKmh < minSpeed) {
                minSpeed = currentSpeedKmh;
                minSpeedTextView.setText(String.format("最小: %.1f km/h", minSpeed));
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
            } else {
                Toast.makeText(this, "需要位置权限才能使用所有功能", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        locationManager.removeUpdates(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && gnssCallback != null) {
            locationManager.unregisterGnssStatusCallback(gnssCallback);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // 每次恢复应用时检查权限
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && gnssCallback != null) {
                locationManager.registerGnssStatusCallback(gnssCallback);
            }
            startLocationUpdates(); // 开始位置更新
        } else {
            checkLocationPermission(); // 请求权限
        }
    }

    // 其他必须实现的LocationListener方法
    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    @Override public void onProviderEnabled(@NonNull String provider) {}
    @Override public void onProviderDisabled(@NonNull String provider) {}
}
