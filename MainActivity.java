package com.example.angus.goooglefit1;

import android.content.Intent;
import android.content.IntentSender;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Result;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessStatusCodes;
import com.google.android.gms.fitness.data.BleDevice;
import com.google.android.gms.fitness.data.Bucket;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.data.Subscription;
import com.google.android.gms.fitness.data.Value;
import com.google.android.gms.fitness.request.BleScanCallback;
import com.google.android.gms.fitness.request.DataDeleteRequest;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.request.DataSourcesRequest;
import com.google.android.gms.fitness.request.DataUpdateRequest;
import com.google.android.gms.fitness.request.OnDataPointListener;
import com.google.android.gms.fitness.request.SensorRequest;
import com.google.android.gms.fitness.request.StartBleScanRequest;
import com.google.android.gms.fitness.result.DailyTotalResult;
import com.google.android.gms.fitness.result.DataReadResult;
import com.google.android.gms.fitness.result.DataSourcesResult;
import com.google.android.gms.fitness.result.ListSubscriptionsResult;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;


public class MainActivity extends AppCompatActivity implements
        OnDataPointListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        View.OnClickListener {

    //Recording Api Part
    private Button mCancelSubscriptionsBtn;
    private Button mShowSubscriptionsBtn;
    private ResultCallback<Status> mSubscribeResultCallback;
    private ResultCallback<Status> mCancelSubscriptionResultCallback;
    private ResultCallback<ListSubscriptionsResult> mListSubscriptionsResultCallback;
    private GoogleApiClient mApiClient;

    //Sensor Api Part
    private static final int REQUEST_OAUTH = 1;
    private static final String AUTH_PENDING = "auth_state_pending";
    private boolean authInProgress = false;

    //Ble Api Part
    private static final int REQUEST_BLUETOOTH = 1001;

    private ResultCallback mResultCallback = new ResultCallback() {
        @Override
        public void onResult(@NonNull Result result) {
            Status status = result.getStatus();
            if (!status.isSuccess()) {
                switch (status.getStatusCode()) {
                    case FitnessStatusCodes.DISABLED_BLUETOOTH:
                        try {
                            status.startResolutionForResult(
                                    MainActivity.this, REQUEST_BLUETOOTH);
                        } catch (IntentSender.SendIntentException e) {

                        }
                        break;
                }
            }
        }
    };

    //History Api Part
    private Button mButtonViewWeek;
    private Button mButtonViewToday;
//    private Button mButtonAddSteps;
//    private Button mButtonUpdateSteps;
    private Button mButtonDeleteSteps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Recording Api Part
        initViews();
        initCallbacks();

        //Sensor Api Part
        if (savedInstanceState != null) {
            authInProgress = savedInstanceState.getBoolean(AUTH_PENDING);
        }

        //Sensor + Recording + Ble + History Api Part
        mApiClient = new GoogleApiClient.Builder(this)
                .addApi(Fitness.RECORDING_API)
                .addApi(Fitness.SENSORS_API)
                .addApi(Fitness.BLE_API)
                .addApi(Fitness.HISTORY_API)
                .addScope(new Scope(Scopes.FITNESS_ACTIVITY_READ_WRITE))
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .enableAutoManage(this, 0, this)
                .build();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mApiClient.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();

        Fitness.SensorsApi.remove( mApiClient, this )
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            mApiClient.disconnect();
                        }
                    }
                });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(AUTH_PENDING, authInProgress);
    }

    private void registerFitnessDataListener(DataSource dataSource, DataType dataType) {

        SensorRequest request = new SensorRequest.Builder()
                .setDataSource( dataSource )
                .setDataType( dataType )
                .setSamplingRate( 1, TimeUnit.SECONDS )
                .build();

        Fitness.SensorsApi.add(mApiClient, request, this)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            Log.e("GoogleFit", "SensorApi successfully added");
                        } else {
                            Log.e("GoogleFit", "adding status: " + status.getStatusMessage());
                        }
                    }
                });
    }

    @Override
    public void onConnected(Bundle bundle) {
        //History Api Part
        Log.e("HistoryAPI", "onConnected");

        //startBleScan
        startBleScan ();

        //Sensor Api Part
        DataSourcesRequest dataSourceRequest = new DataSourcesRequest.Builder()
                .setDataTypes( DataType.TYPE_STEP_COUNT_CUMULATIVE )
                .setDataSourceTypes( DataSource.TYPE_RAW )
                .build();

        ResultCallback<DataSourcesResult> dataSourcesResultCallback = new ResultCallback<DataSourcesResult>() {
            @Override
            public void onResult(DataSourcesResult dataSourcesResult) {
                for( DataSource dataSource : dataSourcesResult.getDataSources() ) {
                    if( DataType.TYPE_STEP_COUNT_CUMULATIVE.equals( dataSource.getDataType() ) ) {
                        registerFitnessDataListener(dataSource, DataType.TYPE_STEP_COUNT_CUMULATIVE);
                    }
                }
            }
        };

        Fitness.SensorsApi.findDataSources(mApiClient, dataSourceRequest)
                .setResultCallback(dataSourcesResultCallback);

        //Recording Api Part
        Fitness.RecordingApi.subscribe(mApiClient, DataType.TYPE_STEP_COUNT_DELTA)
                .setResultCallback(mSubscribeResultCallback);
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        //History Api Part
        Log.e("HistoryAPI", "onConnectionFailed");

        //Sensor Api Part
        if( !authInProgress ) {
            try {
                authInProgress = true;
                connectionResult.startResolutionForResult( MainActivity.this, REQUEST_OAUTH );
            } catch(IntentSender.SendIntentException e ) {
                Log.e( "GoogleFit", "sendingIntentException " + e.getMessage() );
            }
        } else {
            Log.e( "GoogleFit", "authInProgress" );
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_BLUETOOTH:
                startBleScan();
                Log.e( "onActivityResult", "REQUEST_BLUETOOTH" );
                break;
            case REQUEST_OAUTH:
                authInProgress = false;
                if( resultCode == RESULT_OK ) {
                    if( !mApiClient.isConnecting() && !mApiClient.isConnected() ) {
                        mApiClient.connect();
                    }
                } else if( resultCode == RESULT_CANCELED ) {
                    Log.e( "onActivityResult", "RESULT_CANCELED" );
                }
                break;
            default:
                Log.e("onActivityResult", "problem");
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.e("RecordingAPI", "onConnectionSuspended");
        Log.e("HistoryAPI", "onConnectionSuspended");
    }

    @Override
    public void onDataPoint(DataPoint dataPoint) {
        for( final Field field : dataPoint.getDataType().getFields() ) {
            final Value value = dataPoint.getValue( field );
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), "Field: " + field.getName() + " Value: " + value, Toast.LENGTH_SHORT).show();
                    ((TextView)findViewById(R.id.textView)).setText(value.asString());
                }
            });
        }
    }

    @Override
    public void onClick(View v) {
        switch(v.getId()) {
            //Recording Api Part
            case R.id.btn_cancel_subscriptions: {
                cancelSubscriptions();
                break;
            }
            case R.id.btn_show_subscriptions: {
                showSubscriptions();
                break;
            }

            //History Api Part
            case R.id.btn_view_week: {
                new ViewWeekStepCountTask().execute();
                break;
            }
            case R.id.btn_view_today: {
                new ViewTodaysStepCountTask().execute();
                break;
            }
//            case R.id.btn_add_steps: {
//                new AddStepsToGoogleFitTask().execute();
//                break;
//            }
//            case R.id.btn_update_steps: {
//                new UpdateStepsOnGoogleFitTask().execute();
//                break;
//            }
            case R.id.btn_delete_steps: {
                new DeleteYesterdaysStepsTask().execute();
                break;
            }
        }
    }

    //History Api Part
    private class ViewWeekStepCountTask extends AsyncTask<Void, Void, Void> {
        protected Void doInBackground(Void... params) {
            displayLastWeeksData();
            return null;
        }
    }

    //History Api Part
    private class ViewTodaysStepCountTask extends AsyncTask<Void, Void, Void> {
        protected Void doInBackground(Void... params) {
            displayStepDataForToday();
            return null;
        }
    }

    //History Api Part
    private class AddStepsToGoogleFitTask extends AsyncTask<Void, Void, Void> {
        protected Void doInBackground(Void... params) {
            addStepDataToGoogleFit();
            displayLastWeeksData();
            return null;
        }
    }

    //History Api Part
    private class UpdateStepsOnGoogleFitTask extends AsyncTask<Void, Void, Void> {
        protected Void doInBackground(Void... params) {
            updateStepDataOnGoogleFit();
            displayLastWeeksData();
            return null;
        }
    }

    //History Api Part
    private class DeleteYesterdaysStepsTask extends AsyncTask<Void, Void, Void> {
        protected Void doInBackground(Void... params) {
            deleteStepDataOnGoogleFit();
            displayLastWeeksData();
            return null;
        }
    }

    //History Api Part
    //In use, call this every 30 seconds in active mode, 60 in ambient on watch faces
    private void displayStepDataForToday() {
        DailyTotalResult result = Fitness.HistoryApi.readDailyTotal( mApiClient, DataType.TYPE_STEP_COUNT_DELTA ).await(1, TimeUnit.MINUTES);
        showDataSet(result.getTotal());
    }

    //History Api Part
    private void displayLastWeeksData() {
        Calendar cal = Calendar.getInstance();
        Date now = new Date();
        cal.setTime(now);
        long endTime = cal.getTimeInMillis();
        cal.add(Calendar.WEEK_OF_YEAR, -1);
        long startTime = cal.getTimeInMillis();

        java.text.DateFormat dateFormat = DateFormat.getDateInstance();
        Log.e("History", "Range Start: " + dateFormat.format(startTime));
        Log.e("History", "Range End: " + dateFormat.format(endTime));

        //Check how many steps were walked and recorded in the last 7 days
        DataReadRequest readRequest = new DataReadRequest.Builder()
                .aggregate(DataType.TYPE_STEP_COUNT_DELTA, DataType.AGGREGATE_STEP_COUNT_DELTA)
                .bucketByTime(1, TimeUnit.DAYS)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .build();

        DataReadResult dataReadResult = Fitness.HistoryApi.readData(mApiClient, readRequest).await(1, TimeUnit.MINUTES);

        //Used for aggregated data
        if (dataReadResult.getBuckets().size() > 0) {
            Log.e("History", "Number of buckets: " + dataReadResult.getBuckets().size());
            for (Bucket bucket : dataReadResult.getBuckets()) {
                List<DataSet> dataSets = bucket.getDataSets();
                for (DataSet dataSet : dataSets) {
                    showDataSet(dataSet);
                }
            }
        }
        //Used for non-aggregated data
        else if (dataReadResult.getDataSets().size() > 0) {
            Log.e("History", "Number of returned DataSets: " + dataReadResult.getDataSets().size());
            for (DataSet dataSet : dataReadResult.getDataSets()) {
                showDataSet(dataSet);
            }
        }
    }

    //History Api Part
    private void showDataSet(DataSet dataSet) {
        Log.e("History", "Data returned for Data type: " + dataSet.getDataType().getName());
        DateFormat dateFormat = DateFormat.getDateInstance();
        DateFormat timeFormat = DateFormat.getTimeInstance();

        for (DataPoint dp : dataSet.getDataPoints()) {
            Log.e("History", "Data point:");
            Log.e("History", "\tType: " + dp.getDataType().getName());
            Log.e("History", "\tStart: " + dateFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS)) + " " + timeFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS)));
            Log.e("History", "\tEnd: " + dateFormat.format(dp.getEndTime(TimeUnit.MILLISECONDS)) + " " + timeFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS)));
            for(Field field : dp.getDataType().getFields()) {
                Log.e("History", "\tField: " + field.getName() +
                        " Value: " + dp.getValue(field));
            }
        }
    }

    //History Api Part
    private void addStepDataToGoogleFit() {
        //Adds steps spread out evenly from start time to end time
        Calendar cal = Calendar.getInstance();
        Date now = new Date();
        cal.setTime(now);
        long endTime = cal.getTimeInMillis();
        cal.add(Calendar.HOUR_OF_DAY, -1);
        long startTime = cal.getTimeInMillis();

        DataSource dataSource = new DataSource.Builder()
                .setAppPackageName(this)
                .setDataType(DataType.TYPE_STEP_COUNT_DELTA)
                .setName("Step Count")
                .setType(DataSource.TYPE_RAW)
                .build();

        int stepCountDelta = 1000000;
        DataSet dataSet = DataSet.create(dataSource);

        DataPoint point = dataSet.createDataPoint()
                .setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS);
        point.getValue(Field.FIELD_STEPS).setInt(stepCountDelta);
        dataSet.add(point);

        Status status = Fitness.HistoryApi.insertData(mApiClient, dataSet).await(1, TimeUnit.MINUTES);

        if (!status.isSuccess()) {
            Log.e( "History", "Problem with inserting data: " + status.getStatusMessage());
        } else {
            Log.e( "History", "data inserted" );
        }
    }

    //History Api Part
    private void updateStepDataOnGoogleFit() {
        //If two entries overlap, the new data is dropped when trying to insert. Instead, you need to use update
        Calendar cal = Calendar.getInstance();
        Date now = new Date();
        cal.setTime(now);
        long endTime = cal.getTimeInMillis();
        cal.add(Calendar.HOUR_OF_DAY, -1);
        long startTime = cal.getTimeInMillis();

        DataSource dataSource = new DataSource.Builder()
                .setAppPackageName(this)
                .setDataType(DataType.TYPE_STEP_COUNT_DELTA)
                .setName("Step Count")
                .setType(DataSource.TYPE_RAW)
                .build();

        int stepCountDelta = 2000000;
        DataSet dataSet = DataSet.create(dataSource);

        DataPoint point = dataSet.createDataPoint()
                .setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS);
        point.getValue(Field.FIELD_STEPS).setInt(stepCountDelta);
        dataSet.add(point);

        DataUpdateRequest updateRequest = new DataUpdateRequest.Builder().setDataSet(dataSet).setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS).build();
        Fitness.HistoryApi.updateData(mApiClient, updateRequest).await(1, TimeUnit.MINUTES);
    }

    //History Api Part
    private void deleteStepDataOnGoogleFit() {
        Calendar cal = Calendar.getInstance();
        Date now = new Date();
        cal.setTime(now);
        long endTime = cal.getTimeInMillis();
        cal.add(Calendar.DAY_OF_YEAR, -1);
        long startTime = cal.getTimeInMillis();

        DataDeleteRequest request = new DataDeleteRequest.Builder()
                .setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS)
                .addDataType(DataType.TYPE_STEP_COUNT_DELTA)
                .build();

        Fitness.HistoryApi.deleteData(mApiClient, request).await(1, TimeUnit.MINUTES);
    }

    //Recording Api Part
    private void showSubscriptions() {
        Fitness.RecordingApi.listSubscriptions(mApiClient)
                .setResultCallback(mListSubscriptionsResultCallback);
    }

    //Recording Api Part
    private void cancelSubscriptions() {
        Fitness.RecordingApi.unsubscribe(mApiClient, DataType.TYPE_STEP_COUNT_DELTA)
                .setResultCallback(mCancelSubscriptionResultCallback);
    }


    //Recording + History Api Part
    private void initViews() {
        //Recording Api Part
        mCancelSubscriptionsBtn = (Button) findViewById(R.id.btn_cancel_subscriptions);
        mShowSubscriptionsBtn = (Button) findViewById(R.id.btn_show_subscriptions);

        mCancelSubscriptionsBtn.setOnClickListener(this);
        mShowSubscriptionsBtn.setOnClickListener(this);

        //History ApiPart
        mButtonViewWeek = (Button) findViewById(R.id.btn_view_week);
        mButtonViewToday = (Button) findViewById(R.id.btn_view_today);
//        mButtonAddSteps = (Button) findViewById(R.id.btn_add_steps);
//        mButtonUpdateSteps = (Button) findViewById(R.id.btn_update_steps);
        mButtonDeleteSteps = (Button) findViewById(R.id.btn_delete_steps);

        mButtonViewWeek.setOnClickListener(this);
        mButtonViewToday.setOnClickListener(this);
//        mButtonAddSteps.setOnClickListener(this);
//        mButtonUpdateSteps.setOnClickListener(this);
        mButtonDeleteSteps.setOnClickListener(this);

    }

    //Recording Api Part
    private void initCallbacks() {
        mSubscribeResultCallback = new ResultCallback<Status>() {
            @Override
            public void onResult(@NonNull Status status) {
                if (status.isSuccess()) {
                    if (status.getStatusCode() == FitnessStatusCodes.SUCCESS_ALREADY_SUBSCRIBED) {
                        Log.e( "RecordingAPI", "Already subscribed to the Recording API");
                    } else {
                        Log.e("RecordingAPI", "Subscribed to the Recording API");
                    }
                }
            }
        };

        mCancelSubscriptionResultCallback = new ResultCallback<Status>() {
            @Override
            public void onResult(@NonNull Status status) {
                if (status.isSuccess()) {
                    Log.e( "RecordingAPI", "Canceled subscriptions!");
                } else {
                    // Subscription not removed
                    Log.e("RecordingAPI", "Failed to cancel subscriptions");
                }
            }
        };

        mListSubscriptionsResultCallback = new ResultCallback<ListSubscriptionsResult>() {
            @Override
            public void onResult(@NonNull ListSubscriptionsResult listSubscriptionsResult) {
                for (Subscription subscription : listSubscriptionsResult.getSubscriptions()) {
                    DataType dataType = subscription.getDataType();
                    Log.e( "RecordingAPI", dataType.getName() );
                    for (Field field : dataType.getFields() ) {
                        Log.e( "RecordingAPI", field.toString() );
                    }
                }
            }
        };
    }

    //Ble Part
    private void startBleScan () {
        // 1. Define a callback object
        BleScanCallback callback = new BleScanCallback() {
            @Override
            public void onDeviceFound(BleDevice device) {
                // A device that provides the requested data types is available
                // -> Claim this BLE device (See next example)
                claimBleDevice(device);


            }
            @Override
            public void onScanStopped() {
                // The scan timed out or was interrupted
                Log.e( "startBleScan", "onScanStopped" );
            }
        };

        // 2. Create a scan request object:
        // - Specify the data types you're interested in
        // - Provide the callback object
        StartBleScanRequest request = new StartBleScanRequest.Builder()
                .setDataTypes(DataType.TYPE_STEP_COUNT_CUMULATIVE)
                .setBleScanCallback(callback)
                .build();

        // 3. Invoke the Bluetooth Low Energy API with:
        // - The Google API client
        // - The scan request
        PendingResult<Status> pendingResult =
                Fitness.BleApi.startBleScan(mApiClient, request);

        // 4. Check the result (see other examples)
        pendingResult.setResultCallback(mResultCallback);
    }

    //Ble Part
    private void claimBleDevice (BleDevice bleDevice) {
        // After the platform invokes your callback
        // with a compatible BLE device (bleDevice):

        // 1. Invoke the Bluetooth Low Energy API with:
        // - The Google API client
        // - The BleDevice object provided in the callback
        PendingResult<Status> pendingResult =
                Fitness.BleApi.claimBleDevice(mApiClient, bleDevice);

        // 2. Check the result (see other examples)
        pendingResult.setResultCallback(mResultCallback);
    }

    //Ble Part
    private void unclaimBleDevice (BleDevice bleDevice) {
        // When you no longer need the BLE device

        // 1. Invoke the Bluetooth Low Energy API with:
        // - The Google API client
        // - The BLE device (from the initial scan)
        PendingResult<Status> pendingResult =
                Fitness.BleApi.unclaimBleDevice(mApiClient, bleDevice);

        // 2. Check the result (see other examples)
        pendingResult.setResultCallback(mResultCallback);
    }
}
