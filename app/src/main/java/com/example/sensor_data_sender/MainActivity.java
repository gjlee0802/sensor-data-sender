package com.example.sensor_data_sender;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;


import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.LegendRenderer;

import org.jtransforms.fft.DoubleFFT_1D;


public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager smanager;
    private Sensor accSensor;
    private Sensor gyroSensor;
    private TextView acctext;
    private TextView gyrotext;
    private TextView ispeaktext;
    private SensorEventListener acclistener;
    private SensorEventListener gyrolistener;

    //graph
    private LineGraphSeries<DataPoint> mSeriesAccelX, mSeriesAccelY, mSeriesAccelZ;
    Queue<Float> acc_x_knock_sample = new SizeLimitedQueue<>(32);
    Queue<Float> gyro_z_knock_sample = new SizeLimitedQueue<>(32);
    private LineGraphSeries<DataPoint> mSeriesGyroX, mSeriesGyroY, mSeriesGyroZ;

    //400Hz로 가속도 센서 데이터가 수집된다면, 1초에 400개의 데이터가 수집됩니다. 이를 80밀리초로 환산하면:
    //1초에 400개의 데이터가 수집되므로,80밀리초에는 (400 * 80) / 1000 = 32개의 데이터가 수집됩니다.
    //따라서, 80밀리초 동안 수집되는 데이터 수는 32개입니다.
    private final int max_dp = 32;  // 400Hz 일 때, 80ms에 해당하는 샘플링 크기
    private int acc_knocksample_cnt = 0; //피크 감지 후 축적한 노크 데이터 샘플 개수를 카운트
    private int gyro_knocksample_cnt = 0;

    private double graphLastAccelXValue = 10d;
    private double graphLastGyroZValue = 10d;

    private boolean peak_detected = false;

    /*********************AUDIO******************/
    private static final int SAMPLING_RATE_IN_HZ = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    /**
     * Factor by that the minimum buffer size is multiplied. The bigger the factor is the less
     * likely it is that samples will be dropped, but more memory will be used. The minimum buffer
     * size is determined by {@link AudioRecord#getMinBufferSize(int, int, int)} and depends on the
     * recording settings.
     */
    private static final int BUFFER_SIZE_FACTOR = 2;
    /**
     * Size of the buffer where the audio data is stored by Android
     */
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLING_RATE_IN_HZ,
            CHANNEL_CONFIG, AUDIO_FORMAT) * BUFFER_SIZE_FACTOR;
    /**
     * Signals whether a recording is in progress (true) or not (false).
     */
    private final AtomicBoolean recordingInProgress = new AtomicBoolean(false);
    private AudioRecord recorder = null;
    private Thread recordingThread = null;

    //*****************사용자 설정값****************/
    // frequency domain에서 인덱스를 기준으로 주파수 성분 sum값 간의 비율을 결정할 경계선이 되는 인덱스를 설정함.
    // (size - freq_threshold_index)가 경계가 됨.
    private static final int freq_threshold_index = 43;
    // 주파수 비율이 아래 임계 수치를 초과하면 노크 감지.
    // 위의 주파수 비율을 결정하는 경계선 값에 따라 아래의 임계 수치를 조정해야 함.
    private static final float freq_ratio_threshold = 1.7f;


    public static class SizeLimitedQueue<E>
            extends LinkedList<E> {

        // Variable which store the
        // SizeLimitOfQueue of the queue
        private int SizeLimitOfQueue;

        // Constructor method for initializing
        // the SizeLimitOfQueue variable
        public SizeLimitedQueue(int SizeLimitOfQueue) {
            this.SizeLimitOfQueue = SizeLimitOfQueue;
        }

        // Override the method add() available
        // in LinkedList class so that it allow
        // addition  of element in queue till
        // queue size is less than
        // SizeLimitOfQueue otherwise it remove
        // the front element of queue and add
        // new element
        @Override
        public boolean add(E o) {

            // If queue size become greater
            // than SizeLimitOfQueue then
            // front of queue will be removed
            while (this.size() == SizeLimitOfQueue) {
                super.remove();
            }
            super.add(o);
            return true;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        smanager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accSensor = smanager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroSensor = smanager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        acctext = (TextView) findViewById(R.id.textView1);
        gyrotext = (TextView) findViewById(R.id.textView2);
        ispeaktext = (TextView) findViewById(R.id.textView3);
        Button button1 = (Button) findViewById(R.id.button1);

        if (Build.VERSION.SDK_INT >= 30){
            if (!Environment.isExternalStorageManager()){
                Intent getpermission = new Intent();
                getpermission.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(getpermission);
            }
        }

        button1.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                graphLastAccelXValue = 10d;
                graphLastGyroZValue = 10d;
                mSeriesAccelX.resetData(new DataPoint[]{});
                mSeriesGyroZ.resetData(new DataPoint[]{});
                peak_detected = false;
                acc_knocksample_cnt = 0;
                gyro_knocksample_cnt = 0;
            }
        });
        acclistener = new SensorEventListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onSensorChanged(SensorEvent sensorEvent) {
                if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER && !peak_detected) {
                    acctext.setText("Accelerometer value. \n" + "\n x : " + sensorEvent.values[0] +
                            "\n y : " + sensorEvent.values[1] + "\n z : " + sensorEvent.values[2]);

                    graphLastAccelXValue += 0.05d;
                    mSeriesAccelX.appendData(new DataPoint(graphLastAccelXValue, sensorEvent.values[0]), true, max_dp);
                    acc_x_knock_sample.add((float) sensorEvent.values[0]);


                    Iterator<DataPoint> dataiter = mSeriesAccelX.getValues(0, mSeriesAccelX.getHighestValueX());
                    double[] data = new double[256]; // 32개 샘플 뒤에 zero-padding을 적용하여 256 길이로 만듦
                    Arrays.fill(data, 0d);
                    int i = 0;
                    while (dataiter.hasNext()) {
                        DataPoint dp = dataiter.next();
                        data[i] = dp.getY();
                        i++;
                    }

                    if (i >= 32) { // 32크기 이상의 샘플이 모였을 때
                        float freq_ratio = calcFreqRatio(data, data.length); // 주파수 비율을 계산
                        ispeaktext.setText("ratio:" + freq_ratio);
                        //Log.d("fft", "ratio:" + freq_ratio);

                        if (freq_ratio > freq_ratio_threshold) { // 주파수 비율이 임계치를 넘는다면
                            ispeaktext.setText("[knock detected] ratio:" + freq_ratio);
                            peak_detected = true; // 노크를 감지
                            startRecording();
                        }
                    }
                } else if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER && peak_detected && acc_knocksample_cnt < 29) { // Collecting
                    graphLastAccelXValue += 0.05d;
                    mSeriesAccelX.appendData(new DataPoint(graphLastAccelXValue, sensorEvent.values[0]), true, max_dp);
                    acc_x_knock_sample.add(sensorEvent.values[0]);
                    acc_knocksample_cnt++;
                } else if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER && peak_detected && acc_knocksample_cnt >=29) { // All collected
                    stopRecording();
                }

            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {

            }
        };

        gyrolistener = new SensorEventListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onSensorChanged(SensorEvent sensorEvent) {
                if (sensorEvent.sensor.getType() == Sensor.TYPE_GYROSCOPE && !peak_detected) {
                    gyrotext.setText("Gyroscope value. \n" + "\n x : " + sensorEvent.values[0] +
                            "\n y : " + sensorEvent.values[1] + "\n z : " + sensorEvent.values[2]);

                    graphLastGyroZValue += 0.05d;

                    mSeriesGyroZ.appendData(new DataPoint(graphLastGyroZValue, sensorEvent.values[2]), true, max_dp);
                } else if (sensorEvent.sensor.getType() == Sensor.TYPE_GYROSCOPE && peak_detected && gyro_knocksample_cnt < 29) { // Collecting
                    graphLastGyroZValue += 0.05d;
                    mSeriesGyroZ.appendData(new DataPoint(graphLastGyroZValue, sensorEvent.values[0]), true, max_dp);
                    gyro_z_knock_sample.add(sensorEvent.values[0]);
                    gyro_knocksample_cnt++;
                } else if (sensorEvent.sensor.getType() == Sensor.TYPE_GYROSCOPE && peak_detected && gyro_knocksample_cnt >=29) { // All collected
                    stopRecording();
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {

            }
        };

        mSeriesAccelX = initSeries(Color.BLUE, "acc_X"); //라인 그래프를 그림
        mSeriesGyroZ = initSeries(Color.RED, "gyro_Z");
        GraphView mGraphAccel = initGraph(R.id.accgraph, "X direction Acceleration", -100, 100, LegendRenderer.LegendAlign.TOP);
        GraphView mGraphGyro = initGraph(R.id.gyrograph, "Z direction Gyroscope", -20, 20, LegendRenderer.LegendAlign.TOP);

        //그래프에 x,y,z 추가
        mGraphAccel.addSeries(mSeriesAccelX);
        mGraphGyro.addSeries(mSeriesGyroZ);
    }

    protected void onResume() {
        super.onResume();
        smanager.registerListener(acclistener, accSensor, SensorManager.SENSOR_DELAY_FASTEST);
        smanager.registerListener(gyrolistener, gyroSensor, SensorManager.SENSOR_DELAY_FASTEST);
    }

    protected void onPause() {
        super.onPause();
        smanager.unregisterListener(this); // 센서 반납
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER && !peak_detected) {
            acctext.setText("Accelerometer value. \n" + "\n x : " + sensorEvent.values[0] +
                    "\n y : " + sensorEvent.values[1] + "\n z : " + sensorEvent.values[2]);

            graphLastAccelXValue += 0.05d;
            mSeriesAccelX.appendData(new DataPoint(graphLastAccelXValue, sensorEvent.values[0]), true, max_dp);
            acc_x_knock_sample.add((float) sensorEvent.values[0]);

            Iterator<DataPoint> dataiter = mSeriesAccelX.getValues(0, mSeriesAccelX.getHighestValueX());
            double[] data = new double[256];
            Arrays.fill(data, 0d);
            int i = 0;
            while (dataiter.hasNext()) {
                DataPoint dp = dataiter.next();
                data[i] = dp.getY();
                i++;
            }

            if (i >= 32) {
                float freq_ratio = calcFreqRatio(data, data.length);

                ispeaktext.setText("ratio:" + freq_ratio);
                //Log.d("fft", "ratio:" + freq_ratio);
                if (freq_ratio > freq_ratio_threshold) {
                    ispeaktext.setText("[knock detected] ratio:" + freq_ratio);
                    peak_detected = true;
                    startRecording();
                }
            }
        } else if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER && peak_detected && acc_knocksample_cnt < 29) { // Collecting
            graphLastAccelXValue += 0.05d;
            mSeriesAccelX.appendData(new DataPoint(graphLastAccelXValue, sensorEvent.values[0]), true, max_dp);
            acc_x_knock_sample.add(sensorEvent.values[0]);
            acc_knocksample_cnt++;
        } else if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER && peak_detected && acc_knocksample_cnt >=29) { // All collected
            stopRecording();
        }


        if (sensorEvent.sensor.getType() == Sensor.TYPE_GYROSCOPE && !peak_detected) {
            gyrotext.setText("Gyroscope value. \n" + "\n x : " + sensorEvent.values[0] +
                    "\n y : " + sensorEvent.values[1] + "\n z : " + sensorEvent.values[2]);

            graphLastGyroZValue += 0.05d;

            mSeriesGyroZ.appendData(new DataPoint(graphLastGyroZValue, sensorEvent.values[2]), true, max_dp);
        } else if (sensorEvent.sensor.getType() == Sensor.TYPE_GYROSCOPE && peak_detected && gyro_knocksample_cnt < 29) { // Collecting
            graphLastGyroZValue += 0.05d;
            mSeriesGyroZ.appendData(new DataPoint(graphLastGyroZValue, sensorEvent.values[0]), true, max_dp);
            gyro_z_knock_sample.add(sensorEvent.values[0]);
            gyro_knocksample_cnt++;
        } else if (sensorEvent.sensor.getType() == Sensor.TYPE_GYROSCOPE && peak_detected && gyro_knocksample_cnt >=29) { // All collected
            stopRecording();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }


    //그래프 초기화
    public GraphView initGraph(int id, String title, int minY, int maxY, LegendRenderer.LegendAlign align) {
        GraphView graph = findViewById(id);
        //데이터가 늘어날때 x축 scroll이 생기도록
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(1.5);
        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setMinY(minY);
        graph.getViewport().setMaxY(maxY);
        graph.getGridLabelRenderer().setLabelVerticalWidth(100);
        graph.setTitle(title);
        graph.getGridLabelRenderer().setHorizontalLabelsVisible(false);
        graph.getLegendRenderer().setVisible(true);
        graph.getLegendRenderer().setAlign(align);
        return graph;
    }

    //x,y,z 데이터 그래프 추가
    public LineGraphSeries<DataPoint> initSeries(int color, String title) {
        LineGraphSeries<DataPoint> series;
        series = new LineGraphSeries<>();
        series.setDrawDataPoints(true);
        series.setDrawBackground(true);
        series.setColor(color);
        series.setTitle(title);
        return series;
    }


    // 주파수 계산 함수 정의
    public float calcFreqRatio(double[] data, int series_size) {


        //FFT Transform
        double[] fftData = performFFT(data);
        double[] mag = new double[fftData.length / 2];

        //String magstr = "[";
        for (int k = 0; k < fftData.length / 2; k++) {
            mag[k] = Math.sqrt(Math.pow(fftData[2 * k], 2) + Math.pow(fftData[2 * k + 1], 2));
            //if (mag.length/2 <= k && k < mag.length) {
            //    magstr = magstr + mag[k]+", ";
            //}
        }
        //magstr = magstr + "]";
        //Log.d("test", "mag:"+magstr);

        double[] mag_slice = Arrays.copyOfRange(mag, 0, mag.length / 2 + 1);

        return (float) (sumHighFrequencies(mag_slice) / sumLowFrequencies(mag_slice));
    }

    // FFT 변환 수행
    private double[] performFFT(double[] data) {
        DoubleFFT_1D fft = new DoubleFFT_1D(data.length);

        //-> JTransform 부분
        //Jtransform 은 입력에 실수부 허수부가 들어가야하므로 허수부 임의로 0으로 채워서 생성해줌
        double y[] = new double[data.length];
        for (int i = 0; i < data.length; i++) {
            y[i] = 0;
        }
        //실수 허수를 넣으므로 연산에는 blockSize의 2배인 배열 필요
        double[] summary = new double[2 * data.length];

        // 입력 데이터 복사
        //Arrays.fill(summary, 0d);
        //System.arraycopy(data, 0, summary, 0, data.length);

        for (int k = 0; k < data.length; k++) {
            summary[2 * k] = data[k]; //실수부
            summary[2 * k + 1] = y[k]; //허수부 0으로 채워넣음.
        }

        fft.realForward(summary);

        return summary;
    }

    // 주파수 대역에서 특정 주파수 이상의 성분을 합산
    //private double sumHighFrequencies(double[] fftData) {
    private double sumHighFrequencies(double[] mag_slice) {
        double sum = 0;

        //400Hz -> 32
        for (int k = 0; k < mag_slice.length; k++) {
            if (mag_slice.length - freq_threshold_index >= k) { // len-10 <= k 영역 저주파 신호가 많음
                sum += mag_slice[k];
            }
        }

        //Log.d("test", "hfreq sum: " + sum);

        return sum;
    }

    private static double sumLowFrequencies(double[] mag_slice) {
        double sum = 0;

        // 주파수 대역 계산
        //double frequencyResolution = sampleRate / fftData.length;
        //int endIndex = (int) Math.floor(15 / frequencyResolution);

        for (int k = 0; k < mag_slice.length; k++) {
            if (k > mag_slice.length - freq_threshold_index) {
                sum += mag_slice[k];
            }
        }
        return sum;
    }

    private void startRecording() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            //When permission is not granted by user, show them message why this permission is needed.
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    android.Manifest.permission.RECORD_AUDIO)) {
                Toast.makeText(this, "Please grant permissions to record audio", Toast.LENGTH_LONG).show();

                //Give user option to still opt-in the permissions
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.RECORD_AUDIO},
                        321);

            } else {
                // Show user dialog to grant permission to record audio
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.RECORD_AUDIO},
                        321);
            }
            return;
        }

        checkingStoragePermission();
        recorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, SAMPLING_RATE_IN_HZ,
                CHANNEL_CONFIG, AUDIO_FORMAT, BUFFER_SIZE);

        recorder.startRecording();

        recordingInProgress.set(true);

        Log.d("audio", "Start recording thread");
        recordingThread = new Thread(new MainActivity.RecordingRunnable(), "Recording Thread");
        recordingThread.start();
    }

    private void stopRecording() {
        if (null == recorder) {
            return;
        }

        recordingInProgress.set(false);

        recorder.stop();

        recorder.release();

        recorder = null;

        recordingThread = null;
    }

    private void checkingStoragePermission(){

        final int REQUEST_EXTERNAL_STORAGE = 1;
        final String[] PERMISSIONS_STORAGE = {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };

        int permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int permission2 = ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    this,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }

        if (permission2 != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }

    private class RecordingRunnable implements Runnable {
        @Override
        public void run() {
            //final File file = new File("/sdcard/Recordings", "audio_data.pcm");
            File file = new File(Environment.getExternalStorageDirectory(), "audio_data.pcm");
            Log.d("audio", "audio_data file saved into " + Environment.getExternalStorageDirectory());

            final ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);

            try (final FileOutputStream outStream = new FileOutputStream(file)) {
                while (recordingInProgress.get()) {
                    int result = recorder.read(buffer, BUFFER_SIZE);
                    if (result < 0) {
                        throw new RuntimeException("Reading of audio buffer failed: " +
                                getBufferReadFailureReason(result));
                    }
                    outStream.write(buffer.array(), 0, BUFFER_SIZE);
                    buffer.clear();
                }
            } catch (IOException e) {
                throw new RuntimeException("Writing of recorded audio failed", e);
            }
        }

        private String getBufferReadFailureReason(int errorCode) {
            switch (errorCode) {
                case AudioRecord.ERROR_INVALID_OPERATION:
                    return "ERROR_INVALID_OPERATION";
                case AudioRecord.ERROR_BAD_VALUE:
                    return "ERROR_BAD_VALUE";
                case AudioRecord.ERROR_DEAD_OBJECT:
                    return "ERROR_DEAD_OBJECT";
                case AudioRecord.ERROR:
                    return "ERROR";
                default:
                    return "Unknown (" + errorCode + ")";
            }
        }
    }
}