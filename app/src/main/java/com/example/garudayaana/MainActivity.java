package com.example.garudayaana;

import android.Manifest;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Scanner;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.io.IOException;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_MASTER_CODE = 105;

    // UI Elements Mapping
    private TextView tvVehicleStatus, tvDaysLeft, tvMinReqAvg, tvCurrentAvg, tvOdoMeter, tvTotalDist, tvOrdersToday, tvWaitTime, tvDryRun, tvAvgFuelCons, tvAvailFuel, tvEarnings, tvTerminalLog, tvTrainData;
    private Button btnAppToggle;
    private Button btnTripToggle;
    private ScrollView svTerminal;

    // Core Operational Engines
    private LocationManager locationManager;
    private LocationListener locationListener;
    private Location previousLocation = null;
    private SpeechRecognizer speechRecognizer;

    // Fluid Mathematical Trackers
    private boolean isAppStarted = false;
    private boolean isTripStarted = false;
    private double accumulatedDistanceKm = 0.0;
    private double dailyEarningsSum = 0.0;
    private int dailyOrdersCount = 0;
    private double dryRunDistanceKm = 0.0;
    private double dailySpotExpenses = 0.0;

    // Time Interlock Parameters
    private long tripStopTimestamp = 0;
    private long totalWaitingTimeMs = 0;
    private long lastMoveTimestamp = 0;
    private Location lastIdleRecordedLocation = null;

    // 19-Field Advanced Configurations Core Memory variables
    private String regNumber = "";
    private String vehicleName = "";
    private String vehicleType = "2-wheeler";
    private String ownership = "private";
    private double fuelCapacity = 5.0;
    private String fuelType = "Petrol";
    private double fuelRatePerUnit = 105.0;
    private double exShowroomPrice = 78000.0;
    private double insuranceAmount = 3000.0;
    private String insuranceExpiry = "Select Date";
    private double roadTaxAmount = 6500.0;
    private String roadTaxExpiry = "Select Date";
    private double pollutionAmount = 300.0;
    private String pollutionExpiry = "Select Date";
    private int depreciationPercent = 10;
    private String batteryInstallDate = "Select Date";
    private double vehicleOdoBaseline = 0.0;
    private double balanceInHandNow = 0.0;
    private double maxExpectedRangeKm = 200.0;
    private double fuelResetOdoMarker = 0.0;

    // ==========================================
    // PHASE 1: FINANCIAL TARGET & DEFICIT ENGINE
    // ==========================================
    private double monthlyTakeHomeGoal = 10000.0; 
    private double monthlyEMI = 6000.0;          
    private double monthlyMaintenanceEst = 2000.0; 
    private double monthlyInsuranceAndTax = 1500.0; 
    private double accumulatedDeficitAmount = 0.0; 
    
    private int totalWorkingDaysPerMonth = 26;     
    private int remainingWorkingDays = 26;       

    // Dynamic Nodes Storage
    private final ArrayList<String> speechPlatformsList = new ArrayList<>();
    private final ArrayList<CustomExpense> expenseFleetList = new ArrayList<>();
    private double dailyStandingCost = 0.0;

    private void fetchTrainData(String url) {
        Executors.newSingleThreadExecutor().execute(() -> {
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(url).build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String csvData = response.body().string();
                    runOnUiThread(() -> processCsvData(csvData));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void processCsvData(String data) {
        String[] lines = data.split("\n");
        StringBuilder displayData = new StringBuilder();

        for (int i = 1; i < lines.length; i++) {
            String[] columns = lines[i].split(",");
            if (columns.length > 1) {
                String trainName = columns[1]; 
                displayData.append(trainName).append("\n"); 
            }
        }
        tvTrainData.setText(displayData.toString());
    }

    public static class CustomExpense {
        String name; double amount; String period;
        public CustomExpense(String name, double amount, String period) {
            this.name = name; this.amount = amount; this.period = period;
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        String myCsvUrl = "https://docs.google.com/spreadsheets/d/e/2PACX-1vSho6MGWOOUHiY3LSUGvIQFC6HB-3SCNL3P_rKz-FuEKpq9yHCbnhScT6-QbsWJM4msyYi_EDo06Umu/pub?gid=0&single=true&output=csv";
        fetchTrainData(myCsvUrl);

        // Map Views
        tvVehicleStatus = findViewById(R.id.tvVehicleStatus);
        tvDaysLeft = findViewById(R.id.tvDaysLeft);
        tvMinReqAvg = findViewById(R.id.tvMinReqAvg);
        tvCurrentAvg = findViewById(R.id.tvCurrentAvg);
        tvOdoMeter = findViewById(R.id.tvOdoMeter);
        tvTotalDist = findViewById(R.id.tvTotalDist);
        tvOrdersToday = findViewById(R.id.tvOrdersToday);
        tvWaitTime = findViewById(R.id.tvWaitTime);
        tvDryRun = findViewById(R.id.tvDryRun);
        tvAvgFuelCons = findViewById(R.id.tvAvgFuelCons);
        tvAvailFuel = findViewById(R.id.tvAvailFuel);
        tvEarnings = findViewById(R.id.tvEarnings);
        tvTerminalLog = findViewById(R.id.tvTerminalLog);
        tvTrainData = findViewById(R.id.tvTrainData);
        svTerminal = findViewById(R.id.svTerminal);

        btnAppToggle = findViewById(R.id.btnAppToggle);
        Button btnExitApp = findViewById(R.id.btnExitApp);
        btnTripToggle = findViewById(R.id.btnTripToggle);
        Button btnVehicleConfig = findViewById(R.id.btnVehicleConfig);
        Button btnShareCSV = findViewById(R.id.btnShareCSV);
        Button btnTips = findViewById(R.id.btnTips);
        Button btnExpense = findViewById(R.id.btnExpense);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        initializeSpeechRecognizerEngine();

        calculateRemainingWorkingDays();
        checkProfileAndInitialize();
        triggerDynamicSystemPermissionsScan();

        // Display Daily Target right after creation
        double todayTarget = calculateDailyFinancialTarget();
        tvMinReqAvg.setText("₹" + String.format(Locale.US, "%.0f", todayTarget));

        // 1) APP ENGINE SWITCH CONTROL
        btnAppToggle.setOnClickListener(v -> {
            if (regNumber.isEmpty()) {
                Toast.makeText(this, "Please configure vehicle profile mapping first!", Toast.LENGTH_LONG).show();
                showVehicleManagerWizard(false);
                return;
            }
            if (!isAppStarted) {
                isAppStarted = true;
                logToTerminal("App Status: STARTED");
                lastMoveTimestamp = System.currentTimeMillis();
                startIdlePassiveTrackingEngine();
            } else {
                if (isTripStarted) {
                    Toast.makeText(this, "Stop the active trip logger first!", Toast.LENGTH_SHORT).show();
                    return;
                }
                isAppStarted = false;
                logToTerminal("App Status: STOPPED");
                stopIdlePassiveTrackingEngine();
            }
            refreshSystemButtonUI();
        });

        // 2) EXIT APPLICATION COMMAND
        btnExitApp.setOnClickListener(v -> {
            if (isAppStarted) {
                Toast.makeText(this, "Stop the App Engine execution before exiting!", Toast.LENGTH_SHORT).show();
                return;
            }
            finishAffinity();
        });

        // 3) TRIP SATELLITE CONTROL
        btnTripToggle.setOnClickListener(v -> {
            if (!isAppStarted) return;
            if (!isTripStarted) {
                startTripSequence();
            } else {
                stopGPSTrackingEngineAndTriggerVoice();
            }
        });

        // 4) QUICK LIVE SPOT INCOME (TIPS)
        btnTips.setOnClickListener(v -> {
            AlertDialog.Builder b = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
            b.setTitle("Add Tip Received / ബോണസ് തുക (₹)");
            final EditText etTip = new EditText(this);
            etTip.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            b.setView(etTip);
            b.setPositiveButton("Add", (d, w) -> {
                try {
                    double tip = Double.parseDouble(etTip.getText().toString().trim());
                    dailyEarningsSum += tip;
                    tvEarnings.setText("₹" + String.format(Locale.US, "%.0f", dailyEarningsSum));
                    calculateMathematicalDailyStandingCost();
                    refreshSystemButtonUI();
                    logToTerminal("Tip Income Logged: ₹" + tip);
                } catch(Exception ignored) {}
            });
            b.show();
        });

        // 5) DEDICATED RECURRING EXPENSES SHORTCUT
        btnExpense.setOnClickListener(v -> {
            AlertDialog.Builder b = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
            b.setTitle("Spot Expense / പഞ്ച്, ചായ കുടി (₹)");
            LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(30,10,30,10);
            final EditText etLabel = new EditText(this); etLabel.setHint("Reason / ചെലവ് വിവരങ്ങൾ (e.g. Snacks)"); l.addView(etLabel);
            final EditText etAmt = new EditText(this); etAmt.setHint("Amount / തുക (₹)"); etAmt.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); l.addView(etAmt);
            b.setView(l);
            b.setPositiveButton("Save Expense", (d, w) -> {
                try {
                    String lbl = etLabel.getText().toString().trim();
                    double amt = Double.parseDouble(etAmt.getText().toString().trim());
                    dailySpotExpenses += amt;
                    calculateMathematicalDailyStandingCost(); refreshSystemButtonUI();
                    logToTerminal("Spot Expense Logged: " + lbl + " - ₹" + amt);
                    appendTripRecordToCSVFile("SPOT_EXPENSE_" + lbl.toUpperCase(), amt);
                } catch(Exception ignored) {}
            });
            b.show();
        });

        // 6) CENTRAL CONFIG HUB CONTROL
        btnVehicleConfig.setOnClickListener(v -> showCentralConfigLandingMenu());
        btnShareCSV.setOnClickListener(v -> dispatchShareMasterSheet());
    }

    // Financial Calculation Methods Implementation
    private double calculateDailyFinancialTarget() {
        double totalMonthlyFixedCost = monthlyTakeHomeGoal + monthlyEMI + monthlyMaintenanceEst + monthlyInsuranceAndTax;
        double baseDailyTarget = totalMonthlyFixedCost / totalWorkingDaysPerMonth;

        double deficitAdjustment = 0.0;
        if (remainingWorkingDays > 0 && accumulatedDeficitAmount > 0) {
            deficitAdjustment = accumulatedDeficitAmount / remainingWorkingDays;
        }

        return baseDailyTarget + deficitAdjustment;
    }

    private void processEndOfDayFinancialSettlement(double actualEarningsToday) {
        double targetToday = calculateDailyFinancialTarget();
        double difference = targetToday - actualEarningsToday;

        if (difference > 0) {
            accumulatedDeficitAmount += difference;
            logToTerminal("EOD Status: Deficit of ₹" + String.format(Locale.US, "%.0f", difference) + " added to pool.");
        } else {
            double surplus = Math.abs(difference);
            if (accumulatedDeficitAmount >= surplus) {
                accumulatedDeficitAmount -= surplus;
            } else {
                accumulatedDeficitAmount = 0.0;
            }
            logToTerminal("EOD Status: Surplus of ₹" + String.format(Locale.US, "%.0f", surplus) + " achieved!");
        }

        if (remainingWorkingDays > 1) {
            remainingWorkingDays--;
        } else {
            remainingWorkingDays = 26;
            accumulatedDeficitAmount = 0.0;
        }
    }

    private void initializeSpeechRecognizerEngine() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        }
        if (speechPlatformsList.isEmpty()) {
            speechPlatformsList.add("PORTER");
            speechPlatformsList.add("UBER");
            speechPlatformsList.add("RAPIDO");
            speechPlatformsList.add("BLINKIT");
        }
    }

    private void triggerDynamicSystemPermissionsScan() {
        ArrayList<String> perms = new ArrayList<>();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.RECORD_AUDIO);
        if (!perms.isEmpty()) ActivityCompat.requestPermissions(this, perms.toArray(new String[0]), PERMISSION_MASTER_CODE);
    }

    private void startTripSequence() {
        isTripStarted = true;
        accumulatedDistanceKm = 0.0;
        previousLocation = null;
        tvTotalDist.setText("0.0");
        if (tripStopTimestamp > 0) {
            totalWaitingTimeMs += (System.currentTimeMillis() - tripStopTimestamp);
            updateWaitingTimeDisplayField();
        }
        logToTerminal("Trip Started. Telemetry engaged.");
        refreshSystemButtonUI();

        try {
            locationListener = new LocationListener() {
                @Override public void onLocationChanged(@NonNull Location location) { processLiveLocationTelemetry(location, true); }
                @Override public void onProviderDisabled(@NonNull String p) {}
                @Override public void onProviderEnabled(@NonNull String p) {}
            };
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000, 3, locationListener);
            }
        } catch (Exception e) { logToTerminal("[ERROR] Satellite linkage failure."); }
    }

    private void stopGPSTrackingEngineAndTriggerVoice() {
        isTripStarted = false;
        tripStopTimestamp = System.currentTimeMillis();
        vehicleOdoBaseline += accumulatedDistanceKm;
        if (locationManager != null && locationListener != null) locationManager.removeUpdates(locationListener);
        logToTerminal("Trip Stopped. Opening voice diagnostics...");
        refreshSystemButtonUI();
        saveAdvancedProfileToCSV();

        findViewById(android.R.id.content).postDelayed(this::executeVoiceGuidedTripCaptureIntent, 800);
    }

    private void startIdlePassiveTrackingEngine() {
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 5, passiveIdleListener);
            }
        } catch (Exception e) {}
    }

    private void stopIdlePassiveTrackingEngine() {
        if (locationManager != null) locationManager.removeUpdates(passiveIdleListener);
    }

    private final LocationListener passiveIdleListener = new LocationListener() {
        @Override public void onLocationChanged(@NonNull Location loc) { processLiveLocationTelemetry(loc, false); }
        @Override public void onProviderDisabled(@NonNull String p) {}
        @Override public void onProviderEnabled(@NonNull String p) {}
    };

    private void processLiveLocationTelemetry(Location location, boolean isInsideActiveTrip) {
        double lat = location.getLatitude();
        double lon = location.getLongitude();

        if (isInsideActiveTrip) {
            logToTerminal("[GPS ACTIVE] Lat: " + String.format(Locale.US, "%.5f", lat) + " | Lon: " + String.format(Locale.US, "%.5f", lon));
            if (previousLocation != null) {
                double deltaKm = location.distanceTo(previousLocation) / 1000.0;
                accumulatedDistanceKm += deltaKm;
                tvTotalDist.setText(String.format(Locale.US, "%.1f", accumulatedDistanceKm));
                tvOdoMeter.setText(String.format(Locale.US, "%.0f", vehicleOdoBaseline + accumulatedDistanceKm));
                updateFuelCalculationsMetrics();
            }
            previousLocation = location;
        } else {
            if (previousLocation != null) {
                double deltaKm = location.distanceTo(previousLocation) / 1000.0;
                dryRunDistanceKm += deltaKm;
                tvDryRun.setText(String.format(Locale.US, "%.1f", dryRunDistanceKm));
                updateFuelCalculationsMetrics();
            }
            previousLocation = location;
        }

        if (lastIdleRecordedLocation == null) {
            lastIdleRecordedLocation = location; lastMoveTimestamp = System.currentTimeMillis();
        } else {
            if (location.distanceTo(lastIdleRecordedLocation) > 10) {
                lastIdleRecordedLocation = location; lastMoveTimestamp = System.currentTimeMillis();
            } else {
                if (System.currentTimeMillis() - lastMoveTimestamp >= 60000) {
                    recordIdleLocationToCSV(lat, lon, isInsideActiveTrip ? "DURING_TRIP_IDLE" : "WITHOUT_TRIP_IDLE");
                    lastMoveTimestamp = System.currentTimeMillis();
                }
            }
        }
    }

    private void updateFuelCalculationsMetrics() {
        double currentTotalOdo = vehicleOdoBaseline + (isTripStarted ? accumulatedDistanceKm : 0.0) + dryRunDistanceKm;
        double distanceSinceRefill = currentTotalOdo - fuelResetOdoMarker;
        if (distanceSinceRefill < 0) distanceSinceRefill = 0;

        double remainingFuelL = fuelCapacity - (distanceSinceRefill * (fuelCapacity / maxExpectedRangeKm));
        if (remainingFuelL < 0) remainingFuelL = 0;
        double fuelPercentage = (remainingFuelL / fuelCapacity) * 100.0;

        tvAvailFuel.setText(String.format(Locale.US, "%.0f%%", fuelPercentage));
        tvAvgFuelCons.setText(String.format(Locale.US, "%.1f km/L", maxExpectedRangeKm / fuelCapacity));
    }

    private void updateWaitingTimeDisplayField() {
        long min = (totalWaitingTimeMs / 1000) / 60;
        tvWaitTime.setText((min / 60) + "H " + (min % 60) + "M");
    }

    private void executeVoiceGuidedTripCaptureIntent() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        builder.setTitle("Listening...");
        builder.setMessage("Trip details கேட்க்கുന്നു... தயவுசெய்து സംസാരിക്കുക.");
        builder.setCancelable(false);
        final AlertDialog voiceDialog = builder.create();
        voiceDialog.show();

        if (speechRecognizer == null) initializeSpeechRecognizerEngine();

        Intent speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onError(int error) {
                voiceDialog.dismiss();
            }
            @Override public void onResults(Bundle r) {
                voiceDialog.dismiss();
                ArrayList<String> matches = r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String spokenText = matches.get(0);
                    logToTerminal("Voice Captured: " + spokenText);
                }
            }
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });

        speechRecognizer.startListening(speechIntent);
    }

    // Placeholder helper methods for references used in onCreate/methods
    private void calculateRemainingWorkingDays() {}
    private void checkProfileAndInitialize() {}
    private void showVehicleManagerWizard(boolean isInitial) {}
    private void calculateMathematicalDailyStandingCost() {}
    private void appendTripRecordToCSVFile(String tag, double amount) {}
    private void showCentralConfigLandingMenu() {}
    private void dispatchShareMasterSheet() {}
    private void saveAdvancedProfileToCSV() {}
    private void recordIdleLocationToCSV(double lat, double lon, String type) {}
    private void refreshSystemButtonUI() {}
    private void logToTerminal(String message) {
        if (tvTerminalLog != null) {
            tvTerminalLog.append("\n" + message);
            svTerminal.post(() -> svTerminal.fullScroll(View.FOCUS_DOWN));
        }
    }
}
