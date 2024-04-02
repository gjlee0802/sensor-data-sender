package com.example.sensor_data_sender;

import android.graphics.Color;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.List;
import java.util.ArrayList;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import android.widget.TextView;

import com.github.mikephil.charting.data.LineData;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.LegendRenderer;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager smanager;
    private Sensor accSensor;
    private Sensor gyroSensor;
    private TextView acctext;
    private TextView gyrotext;
    private SensorEventListener acclistener;
    private SensorEventListener gyrolistener;

    private Thread thread;

    //graph
    private LineGraphSeries<DataPoint> mSeriesAccelX,mSeriesAccelY,mSeriesAccelZ;
    private GraphView mGraphAccel;
    private double graphLastAccelXValue = 10d;
    private GraphView line_graph;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        smanager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accSensor = smanager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroSensor = smanager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        acctext = (TextView) findViewById(R.id.textView1);
        gyrotext = (TextView) findViewById(R.id.textView2);
        acclistener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent sensorEvent) {
                if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                    acctext.setText("Accelerometer value. \n" + "\n x : " + sensorEvent.values[0] +
                            "\n y : " + sensorEvent.values[1] + "\n z : " + sensorEvent.values[2]);

                    graphLastAccelXValue += 0.05d;
                    mSeriesAccelX.appendData(new DataPoint(graphLastAccelXValue,sensorEvent.values[0]),true,100);
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {

            }
        };

        gyrolistener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent sensorEvent) {
                if (sensorEvent.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                    gyrotext.setText("Gyroscope value. \n" + "\n x : " + sensorEvent.values[0] +
                            "\n y : " + sensorEvent.values[1] + "\n z : " + sensorEvent.values[2]);
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {

            }
        };

        mSeriesAccelX = initSeries(Color.BLUE, "X"); //라인 그래프를 그림
        mGraphAccel = initGraph(R.id.graph, "X direction Acceleration");

        //그래프에 x,y,z 추가
        mGraphAccel.addSeries(mSeriesAccelX);
    }

    protected void onResume() {
        super.onResume();
        smanager.registerListener(acclistener, accSensor, SensorManager.SENSOR_DELAY_UI);
        smanager.registerListener(gyrolistener, gyroSensor, SensorManager.SENSOR_DELAY_UI);;
    }

    protected void onPause(){
        super.onPause();
        if(thread!=null){
            thread.interrupt();
        }
        smanager.unregisterListener(this); // 센서 반납
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            acctext.setText("Accelerometer value. \n" + "\n x : " + sensorEvent.values[0] +
                    "\n y : " + sensorEvent.values[1] + "\n z : " + sensorEvent.values[2]);

            graphLastAccelXValue += 0.05d;
            mSeriesAccelX.appendData(new DataPoint(graphLastAccelXValue,sensorEvent.values[0]),true,100);
        }
        if (sensorEvent.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            gyrotext.setText("Gyroscope value. \n" + "\n x : " + sensorEvent.values[0] +
                    "\n y : " + sensorEvent.values[1] + "\n z : " + sensorEvent.values[2]);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }


    //그래프 초기화
    public GraphView initGraph(int id, String title) {
        GraphView graph = findViewById(id);
        //데이터가 늘어날때 x축 scroll이 생기도록
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(5);
        graph.getGridLabelRenderer().setLabelVerticalWidth(100);
        graph.setTitle(title);
        graph.getGridLabelRenderer().setHorizontalLabelsVisible(false);
        graph.getLegendRenderer().setVisible(true);
        graph.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.TOP);
        return graph;
    }

    //x,y,z 데이터 그래프 추가
    public LineGraphSeries<DataPoint> initSeries(int color, String title){
        LineGraphSeries<DataPoint> series;
        series = new LineGraphSeries<>();
        series.setDrawDataPoints(true);
        series.setDrawBackground(true);
        series.setColor(color);
        series.setTitle(title);
        return series;
    }
}