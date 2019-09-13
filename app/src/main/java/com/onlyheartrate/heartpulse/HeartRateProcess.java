package com.onlyheartrate.heartpulse;

import android.net.wifi.hotspot2.pps.HomeSp;
import android.os.Bundle;
import android.support.v4.view.GravityCompat;
import android.support.v7.app.ActionBarDrawerToggle;
import android.view.MenuItem;
import android.support.design.widget.NavigationView;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.ProgressBar;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import static java.lang.Math.ceil;

public class HeartRateProcess extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    private static final String TAG="HeartRateMonitor";
    private static final AtomicBoolean processing=new AtomicBoolean();
    private SurfaceView preview=null;
    private static SurfaceHolder previewHolder=null;
    private static WakeLock wakeLock=null;
    private Toast mainToast;
    public int Beats=0;
    public double bufferAvgB=0;
    public String user;
    private ProgressBar ProgHeart;
    public int ProgP=0;
    public int inc=0;
    private static long startTime=0;
    private double SamplingFreq;
    public ArrayList<Double> GreenAvgList=new ArrayList<Double>();
    public int counter=0;
    private static Camera camera;
    public ArrayList<Double> RedAvgList=new ArrayList<Double>();

    DrawerLayout drawer;
    NavigationView navigationView;
    Toolbar toolbar=null;


    @SuppressLint("InvalidWakeLockTag")
    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_heart_rate_process);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
         drawer = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        navigationView.setNavigationItemSelectedListener(this);

        preview= findViewById(R.id.preview);
        previewHolder=preview.getHolder();
        previewHolder.addCallback(surfaceCallback);
        previewHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        ProgHeart = findViewById(R.id.progressBar);
        ProgHeart.setProgress(0);

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK , "KeepScreenOn");

       }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.heart_rate_process, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
     if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    @Override

    public boolean onNavigationItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch (id) {
            case R.id.nav_home:
                Intent c = new Intent(HeartRateProcess.this, HeartRateProcess.class);
                startActivity(c);
                break;

            case R.id.nav_help:
                Intent g = new Intent(HeartRateProcess.this, help.class);
                startActivity(g);
                break;

            case R.id.nav_about:
                Intent s = new Intent(HeartRateProcess.this, detail.class);
                startActivity(s);
                break;
        }
          return true;
        }
        @Override
        public void onConfigurationChanged (Configuration newConfig){
            super.onConfigurationChanged(newConfig);
        }

        @Override
        public void onResume () {
            super.onResume();

            wakeLock.acquire();

            camera = Camera.open();

            camera.setDisplayOrientation(90);

            startTime = System.currentTimeMillis();
        }
        @Override
        public void onPause () {
            super.onPause();
            wakeLock.release();
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
            camera = null;

        }



        private  PreviewCallback previewCallback = new PreviewCallback() {

            @Override
            public void onPreviewFrame(byte[] data, Camera cam) {
                if (data == null) throw new NullPointerException();
                Camera.Size size = cam.getParameters().getPreviewSize();
                if (size == null) throw new NullPointerException();


                if (!processing.compareAndSet(false, true)) return;


                int width = size.width;
                int height = size.height;

                double GreenAvg;
                double RedAvg;

                GreenAvg=ImageProcessing.decodeYUV420SPtoRedBlueGreenAvg(data.clone(), height, width,3);
                RedAvg=ImageProcessing.decodeYUV420SPtoRedBlueGreenAvg(data.clone(), height, width,1);

                GreenAvgList.add(GreenAvg);
                RedAvgList.add(RedAvg);

                ++counter;

                if (RedAvg < 200) {
                    inc=0;
                    ProgP=inc;
                    counter=0;
                    ProgHeart.setProgress(ProgP);
                    processing.set(false);
                }

                long endTime = System.currentTimeMillis();
                double totalTimeInSecs = (endTime - startTime) / 1000d;
                if (totalTimeInSecs >= 30) {

                    Double[] Green = GreenAvgList.toArray(new Double[GreenAvgList.size()]);
                    Double[] Red = RedAvgList.toArray(new Double[RedAvgList.size()]);

                    SamplingFreq =  (counter/totalTimeInSecs);
                    double HRFreq = Fft.FFT(Green, counter, SamplingFreq);
                    double bpm=(int)ceil(HRFreq*85);
                    double HR1Freq = Fft.FFT(Red, counter, SamplingFreq);
                    double bpm1=(int)ceil(HR1Freq*85);


                    if((bpm > 45 || bpm < 200) )
                    {
                        if((bpm1 > 45 || bpm1 < 200)) {

                            bufferAvgB = (bpm+bpm1)/2;
                        }
                        else{
                            bufferAvgB = bpm;
                        }
                    }
                    else if((bpm1 > 45 || bpm1 < 200)){
                        bufferAvgB = bpm1;
                    }

                    if (bufferAvgB < 45 || bufferAvgB > 200) { //if the heart beat wasn't reasonable after all reset the progresspag and restart measuring
                        inc=0;
                        ProgP=inc;
                        ProgHeart.setProgress(ProgP);
                        mainToast = Toast.makeText(getApplicationContext(), "Measurement Failed Try Again", Toast.LENGTH_SHORT);
                        mainToast.show();
                        startTime = System.currentTimeMillis();
                        counter=0;
                        processing.set(false);
                        return;
                    }

                    Beats=(int)bufferAvgB;
                }

                if(Beats != 0 ){                    Intent i=new Intent(HeartRateProcess.this,HeartRateResult.class);
                    i.putExtra("bpm", Beats);
                    i.putExtra("Usr", user);
                    startActivity(i);
                    finish();}


                if(RedAvg!=0){
                    ProgP=inc++/34;
                    ProgHeart.setProgress(ProgP);}

                processing.set(false);

            }
        };

        private SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {

            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                try {
                    camera.setPreviewDisplay(previewHolder);
                    camera.setPreviewCallback(previewCallback);
                } catch (Throwable t) {
                    Log.e("PreviewDemo-surfaceCall", "Exception in setPreviewDisplay()", t);
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

                Camera.Parameters parameters = camera.getParameters();
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);

                Camera.Size size = getSmallestPreviewSize(width, height, parameters);
                if (size != null) {
                    parameters.setPreviewSize(size.width, size.height);
                    Log.d(TAG, "Using width=" + size.width + " height=" + size.height);
                }

                camera.setParameters(parameters);
                camera.startPreview();
            }


            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
            }
        };

        private static Camera.Size getSmallestPreviewSize(int width, int height, Camera.Parameters parameters) {
            Camera.Size result = null;

            for (Camera.Size size : parameters.getSupportedPreviewSizes()) {
                if (size.width <= width && size.height <= height) {
                    if (result == null) {
                        result = size;
                    } else {
                        int resultArea = result.width * result.height;
                        int newArea = size.width * size.height;
                        if (newArea < resultArea) result = size;
                    }
                }
            }

            return result;
        }
    }






