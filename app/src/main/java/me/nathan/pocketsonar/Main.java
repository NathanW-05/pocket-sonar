package me.nathan.pocketsonar;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.lang.ref.WeakReference;
import java.text.DecimalFormat;

import me.nathan.pocketsonar.interpretation.Doppler;
import me.nathan.pocketsonar.sonar.DopplerDetector;
import me.nathan.pocketsonar.sonar.PitchGenerator;

public class Main extends AppCompatActivity implements SensorEventListener, LocationListener {

    public static Main INSTANCE;

    // audio recording config
    public static final int SAMPLE_RATE = 48000;
    public static final int BASE_FREQUENCY = 17500;
    public static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    public static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    public static final int AUDIO_SOURCE = MediaRecorder.AudioSource.MIC;
    public static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);

    // sensors
    private LocationManager locationManager;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor magnetometer;

    private final float[] accelerometerReading = new float[3];
    private final float[] magnetometerReading = new float[3];
    private final float[] rotationMatrix = new float[9];
    private final float[] orientationAngles = new float[3];

    // sonar
    private DopplerDetector dopplerDetector = null;
    public static int sonarMinimumMagnitude = 250;
    private static boolean beganGeneratingTone = false;

    public static WeakReference<TextView> viewWeakReference;
    public static double currentOrientation;

    public Main() {
        INSTANCE = this;
    }

    //todo: make sure to auto add permissions
    //todo: make sure to reset volume to its original state from max
    @Override
    @SuppressLint("SetTextI18n")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        //setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);

        findViewById(R.id.captureButton).setOnClickListener(v -> capture());
        findViewById(R.id.calibrationButton).setOnClickListener(v -> calibrate());

        SeekBar sensitivitySeekBar = findViewById(R.id.sensitivitySlider);
        sensitivitySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                TextView view = findViewById(R.id.sensitivityLabel);
                sonarMinimumMagnitude = progress * 50;
                view.setText("Sensitivity (" + sonarMinimumMagnitude + ")");
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        TextView textView = findViewById(R.id.speedText);
        viewWeakReference = new WeakReference<>(textView);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if(!checkPermissions()) requestPermissions();

        if (!beganGeneratingTone) {
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

            //set volume to maximum

            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
                    audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
            0);
            beganGeneratingTone = true;
            PitchGenerator.generateTone(BASE_FREQUENCY);
        }
    }

    private void capture() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            if (dopplerDetector != null) {
                dopplerDetector.end();
            }
            AudioRecord recorder = new AudioRecord(AUDIO_SOURCE, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, BUFFER_SIZE);
            dopplerDetector = new DopplerDetector(recorder);
            Main.viewWeakReference.get().setText(String.valueOf(0));
            dopplerDetector.start();
        }
    }

    @SuppressLint("SetTextI18n")
    private void calibrate() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            AudioRecord recorder = new AudioRecord(AUDIO_SOURCE, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, BUFFER_SIZE);
            Main.viewWeakReference.get().setText("calibrating...");

            // todo: calibrating during a running doppler detection thread should kill it and update
            // todo: probably should find a better way to do the calibration
            Runnable calibrationTimer = () -> {

                double orientationSum = 0;
                long startTime = System.currentTimeMillis();
                int i = 0;
                while(System.currentTimeMillis() - startTime < 3000) {
                    i++;
                    orientationSum += currentOrientation;
                }
                Doppler.calibrationAngle = orientationSum / i;

                Main.INSTANCE.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Main.viewWeakReference.get().setText("Calibrated");
                    }
                });
            };
            Thread thread = new Thread(calibrationTimer);
            thread.start();
        }
    }

    public static void changeSpeedText(double speed) {
        DecimalFormat df1 = new DecimalFormat("0.#");
        Runnable r = () -> {
            Main.viewWeakReference.get().setText(
                    df1.format(speed));
        };
        Main.INSTANCE.runOnUiThread(r);
    }

    @Override
    protected void onPause() {
        super.onPause();

        sensorManager.unregisterListener(this);
        locationManager.removeUpdates(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        //setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor == accelerometer) {
            System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.length);
        } else if (event.sensor == magnetometer) {
            System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.length);
        }

        SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading);
        SensorManager.getOrientation(rotationMatrix, orientationAngles);
        float azimuth = orientationAngles[0];
        float azimuthDegrees = (float)Math.toDegrees(azimuth);
        if (azimuthDegrees < 0) {
            azimuthDegrees += 360;
        }
        currentOrientation = azimuthDegrees;
    }

    private boolean checkPermissions() {
        int audioPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        int locationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
        return audioPermission == PackageManager.PERMISSION_GRANTED &&
                locationPermission == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        String[] permissions = {Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION};
        ActivityCompat.requestPermissions(this, permissions, 100);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
    }
}