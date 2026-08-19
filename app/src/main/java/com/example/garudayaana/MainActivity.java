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
import android.view.LayoutInflater;
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

        // 3) TRIP SATELITE CONTROL
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
            b.setTitle("Spot Expense / പഞ്ച്വർ, ചായ കുടി (₹)");
            LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(30,10,30,10);
            final EditText etLabel = new EditText(this); etLabel.setHint("Reason / ചെലവ് വിവരം (e.g. Snacks)"); l.addView(etLabel);
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

        // 6) CENTRAL CONFIG HUB CONTROL (Updated to use modular XML dialog view)
        btnVehicleConfig.setOnClickListener(v -> openVehicleConfigDialog());
        
        btnShareCSV.setOnClickListener(v -> dispatchShareMasterSheet());
    }

    // ==========================================
    // CONFIG DIALOG & LAYOUT INFLATION METHOD
    // ==========================================
    private void openVehicleConfigDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        builder.setTitle("Vehicle Configuration Hub");

        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_config, null);
        builder.setView(dialogView);

        // ഇവിടെ ആവശ്യമെങ്കിൽ dialogView വഴി XML-ലെ ഫീൽഡുകൾ ആക്സസ് ചെയ്യാം

        builder.setPositiveButton("Open Config Menu", (dialog, which) -> {
            dialog.dismiss();
            showCentralConfigLandingMenu(); // നിലവിലുള്ള പഴയ മാസ്റ്റർ കൺട്രോൾ മെനുവിലേക്ക് തുറക്കുന്നു
        });

        builder.setNegativeButton("Close", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
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
        builder.setMessage("Trip details കേൾക്കുന്നു... ദയവായി സംസാരിക്കുക.");
        builder.setCancelable(false);
        final AlertDialog voiceDialog = builder.create();
        voiceDialog.show();

        if (speechRecognizer == null) initializeSpeechRecognizerEngine();

        Intent speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onResults(Bundle r) {
                voiceDialog.dismiss();
                ArrayList<String> matches = r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) processVoiceHypothesisData(matches.get(0));
                else spawnManualTripFallbackDialog("");
            }
            @Override public void onError(int error) {
                voiceDialog.dismiss();
                logToTerminal("[SPEECH ERROR CODE]: " + error);
                spawnManualTripFallbackDialog("");
            }
            @Override public void onReadyForSpeech(Bundle p) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float f) {}
            @Override public void onBufferReceived(byte[] b) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onPartialResults(Bundle p) {}
            @Override public void onEvent(int e, Bundle b) {}
        });

        try {
            speechRecognizer.startListening(speechIntent);
        } catch (Exception e) {
            voiceDialog.dismiss();
            spawnManualTripFallbackDialog("");
        }
    }

    private void processVoiceHypothesisData(String utterance) {
        logToTerminal("[VOICE] Heard: \"" + utterance + "\"");
        String cleanInput = utterance.toUpperCase().trim();
        String identifiedPlatform = "MANUAL";

        for (String platform : speechPlatformsList) {
            String pUpper = platform.toUpperCase();
            if (cleanInput.contains(pUpper) || pUpper.contains(cleanInput)) {
                identifiedPlatform = platform;
                break;
            }
        }

        String numberOnly = cleanInput.replaceAll("[^0-9]", "");
        double fare = 0.0;
        try {
            if (!numberOnly.isEmpty()) {
                fare = Double.parseDouble(numberOnly);
            }
        } catch (Exception e) {
            fare = 0.0;
        }

        if (fare > 0) {
            executeCommitTripEarnings(identifiedPlatform, fare);
        } else {
            spawnManualTripFallbackDialog(utterance);
        }
    }

    private void executeCommitTripEarnings(String clientLabel, double fareValue) {
        dailyOrdersCount++; dailyEarningsSum += fareValue;
        tvOrdersToday.setText(String.valueOf(dailyOrdersCount));
        tvEarnings.setText("₹" + String.format(Locale.US, "%.0f", dailyEarningsSum));
        tvCurrentAvg.setText(String.format(Locale.US, "%.1f", dailyEarningsSum / dailyOrdersCount));
        calculateMathematicalDailyStandingCost();
        refreshSystemButtonUI();
        logToTerminal("[TRIP LOGGED] App: " + clientLabel + " | Fare: ₹" + fareValue);
        appendTripRecordToCSVFile(clientLabel, fareValue);
    }

    private void spawnManualTripFallbackDialog(String raw) {
        AlertDialog.Builder b = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        b.setTitle("Log Trip Details"); b.setCancelable(false);
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(40,20,40,20);

        final Spinner spn = new Spinner(this);
        ArrayAdapter<String> adp = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, speechPlatformsList);
        spn.setAdapter(adp); l.addView(spn);

        final EditText etFare = new EditText(this); etFare.setHint("Fare (₹)");
        etFare.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); etFare.setText(raw.replaceAll("[^0-9]", ""));
        l.addView(etFare);

        b.setView(l);
        b.setPositiveButton("Commit", (d, w) -> {
            String p = spn.getSelectedItem() != null ? spn.getSelectedItem().toString() : "MANUAL";
            double f = 0.0; try { f = Double.parseDouble(etFare.getText().toString().trim()); } catch(Exception e){}
            if (f > 0) executeCommitTripEarnings(p, f);
            d.dismiss();
        });
        b.show();
    }

    private void calculateMathematicalDailyStandingCost() {
        LocalDate now = java.time.LocalDate.now();
        double totalDaysInYear = now.isLeapYear() ? 366.0 : 365.0;
        int workingDaysLeft = Integer.parseInt(tvDaysLeft.getText().toString());

        double dailyInsurance = insuranceAmount / totalDaysInYear;
        double dailyRoadTax = roadTaxAmount / totalDaysInYear;
        double dailyPollution = pollutionAmount / totalDaysInYear;
        double dailyDepreciation = (exShowroomPrice * ((double) depreciationPercent / 100.0)) / totalDaysInYear;

        double customExpensesSum = 0.0;
        for (CustomExpense entry : expenseFleetList) {
            if (entry.amount <= 0) continue;
            if (entry.period.equalsIgnoreCase("Daily")) customExpensesSum += entry.amount;
            else if (entry.period.equalsIgnoreCase("Monthly")) customExpensesSum += entry.amount / now.lengthOfMonth();
            else if (entry.period.equalsIgnoreCase("Yearly")) customExpensesSum += entry.amount / totalDaysInYear;
        }

        dailyStandingCost = dailyInsurance + dailyRoadTax + dailyPollution + dailyDepreciation + customExpensesSum;

        if (workingDaysLeft > 0) {
            double formulaResult = ((dailyStandingCost * now.lengthOfMonth()) + dailySpotExpenses - dailyEarningsSum) / workingDaysLeft;
            if (formulaResult < 0) formulaResult = 0;
            tvMinReqAvg.setText(String.format(Locale.US, "%.0f", formulaResult));
        } else {
            tvMinReqAvg.setText("0");
        }
    }

    private void showCentralConfigLandingMenu() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        builder.setTitle("Garuda Yana: Configurations Hub");

        final AlertDialog[] landingDialog = new AlertDialog[1];

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(40, 30, 40, 30);

        Button btnVeh = new Button(this);
        btnVeh.setText("🚗 1. വാഹന വിവരങ്ങൾ (Vehicle Details)");
        btnVeh.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF006064));
        btnVeh.setOnClickListener(v -> {
            if (landingDialog[0] != null) landingDialog[0].dismiss();
            showVehicleManagerWizard(false);
        });
        mainLayout.addView(btnVeh);

        Button btnExp = new Button(this);
        btnExp.setText("⚙️ 2. സ്ഥിര ചെലവുകൾ (Fixed Expenses)");
        btnExp.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF388E3C));
        btnExp.setOnClickListener(v -> {
            if (landingDialog[0] != null) landingDialog[0].dismiss();
            showExpensesManagerWizard();
        });
        mainLayout.addView(btnExp);

        Button btnApps = new Button(this);
        btnApps.setText("📦 3. ഓർഡർ ആപ്പുകൾ (Manage Spinner Apps)");
        btnApps.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFE65100));
        btnApps.setOnClickListener(v -> {
            if (landingDialog[0] != null) landingDialog[0].dismiss();
            showPlatformsManagerWizard();
        });
        mainLayout.addView(btnApps);

        builder.setView(mainLayout);
        builder.setNegativeButton("Close / അടയ്ക്കുക", (dialog, which) -> dialog.dismiss());

        landingDialog[0] = builder.create();
        landingDialog[0].show();
    }

    private void showVehicleManagerWizard(final boolean forceNewRegistration) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        builder.setTitle(forceNewRegistration ? "Register New Fleet Vehicle" : "Vehicle Profiles Control");
        builder.setCancelable(!regNumber.isEmpty());

        final AlertDialog[] subHolder = new AlertDialog[1];

        ScrollView scrollWrapper = new ScrollView(this);
        LinearLayout mainContainer = new LinearLayout(this);
        mainContainer.setOrientation(LinearLayout.VERTICAL); mainContainer.setPadding(40, 25, 40, 25);
        scrollWrapper.addView(mainContainer);

        File dir = getFilesDir();
        File[] files = dir.listFiles((dir1, name) -> name.startsWith("garuda_yana_") && name.endsWith(".csv"));
        final ArrayList<String> registeredVehicles = new ArrayList<>();
        if (files != null) {
            for (File f : files) {
                String id = f.getName().replace("garuda_yana_", "").replace(".csv", "");
                if(!id.isEmpty()) registeredVehicles.add(id);
            }
        }

        if (!forceNewRegistration && registeredVehicles.size() > 1) {
            Button btnSwitch = new Button(this);
            btnSwitch.setText("🔄 Switch Active Vehicle Profile (" + registeredVehicles.size() + " Saved)");
            btnSwitch.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF006064));
            btnSwitch.setOnClickListener(v -> {
                AlertDialog.Builder selectBox = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
                selectBox.setTitle("Select Target Fleet Profile");
                String[] array = registeredVehicles.toArray(new String[0]);
                selectBox.setItems(array, (dialogInterface, index) -> {
                    parseAdvancedVehicleCSV(new File(getFilesDir(), "garuda_yana_" + array[index] + ".csv"));
                    if(subHolder[0] != null) subHolder[0].dismiss();
                    Toast.makeText(this, "Active profile switched to: " + array[index], Toast.LENGTH_SHORT).show();
                });
                selectBox.show();
            });
            mainContainer.addView(btnSwitch);
        }

        if (!forceNewRegistration && !regNumber.isEmpty()) {
            Button btnCreateNew = new Button(this);
            btnCreateNew.setText("➕ Register A <New> Fleet Vehicle");
            btnCreateNew.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF388E3C));
            btnCreateNew.setOnClickListener(v -> {
                if(subHolder[0] != null) subHolder[0].dismiss();
                showVehicleManagerWizard(true);
            });
            mainContainer.addView(btnCreateNew);
        }

        Button btnRefill = new Button(this);
        btnRefill.setText("⛽ REFILL TANK (RESET FUEL STATUS)");
        btnRefill.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFE65100));
        btnRefill.setOnClickListener(v -> {
            fuelResetOdoMarker = vehicleOdoBaseline + (isTripStarted ? accumulatedDistanceKm : 0.0) + dryRunDistanceKm;
            updateFuelCalculationsMetrics(); saveAdvancedProfileToCSV();
            Toast.makeText(this, "Fuel indicators calibrated to 100%!", Toast.LENGTH_SHORT).show();
        });
        mainContainer.addView(btnRefill);

        final EditText etReg = createDialogEditText(this, "Registration Number / വണ്ടി നമ്പർ:", forceNewRegistration ? "" : regNumber, mainContainer);
        final EditText etName = createDialogEditText(this, "Vehicle Name / വണ്ടിയുടെ പേര്:", forceNewRegistration ? "" : vehicleName, mainContainer);
        final EditText etOdoBase = createDialogEditText(this, "Current Odometer Base / ഒഡോമീറ്റർ റീഡിങ് (km):", forceNewRegistration ? "0" : String.format(Locale.US, "%.0f", vehicleOdoBaseline), mainContainer);
        final EditText etMaxRange = createDialogEditText(this, "Expected Kilometers per Refill / മാക്സ് റേഞ്ച് (km):", forceNewRegistration ? "200" : String.valueOf(maxExpectedRangeKm), mainContainer);
        final EditText etBalance = createDialogEditText(this, "Balance In Hand Now / കയ്യിലുള്ള തുക (₹):", forceNewRegistration ? "0" : String.valueOf(balanceInHandNow), mainContainer);
        final EditText etFuelCap = createDialogEditText(this, "Fuel Tank Capacity / ടാങ്ക് ശേഷി (Liters):", forceNewRegistration ? "5" : String.valueOf(fuelCapacity), mainContainer);
        final EditText etFuelRate = createDialogEditText(this, "Fuel Rate / ഇന്ധന നിരക്ക് (₹):", forceNewRegistration ? "105" : String.valueOf(fuelRatePerUnit), mainContainer);
        final EditText etExShowroom = createDialogEditText(this, "Vehicle Ex-Showroom Price / വണ്ടിയുടെ വില (₹):", forceNewRegistration ? "75000" : String.valueOf(exShowroomPrice), mainContainer);

        final EditText etInsurance = createDialogEditText(this, "Insurance Premium Amount / ഇൻഷുറൻസ് തുക (₹):", forceNewRegistration ? "3000" : String.valueOf(insuranceAmount), mainContainer);
        final Button btnInsDate = new Button(this); btnInsDate.setText("Insurance Expiry: " + (forceNewRegistration ? "Select Date" : insuranceExpiry));
        btnInsDate.setOnClickListener(v -> showDatePicker(date -> { insuranceExpiry = date; btnInsDate.setText("Insurance Expiry: " + date); }));
        mainContainer.addView(btnInsDate);

        final EditText etRoadTax = createDialogEditText(this, "Road Tax Amount / റോഡ് ടാക്സ് തുക (₹):", forceNewRegistration ? "6500" : String.valueOf(roadTaxAmount), mainContainer);
        final Button btnRoadTaxDate = new Button(this); btnRoadTaxDate.setText("Road Tax Expiry: " + (forceNewRegistration ? "Select Date" : roadTaxExpiry));
        btnRoadTaxDate.setOnClickListener(v -> showDatePicker(date -> { roadTaxExpiry = date; btnRoadTaxDate.setText("Road Tax Expiry: " + date); }));
        mainContainer.addView(btnRoadTaxDate);

        final EditText etPollution = createDialogEditText(this, "Pollution Cost / പുകപരിശോധന തുക (₹):", forceNewRegistration ? "300" : String.valueOf(pollutionAmount), mainContainer);
        final Button btnPollutionDate = new Button(this); btnPollutionDate.setText("Pollution Expiry: " + (forceNewRegistration ? "Select Date" : pollutionExpiry));
        btnPollutionDate.setOnClickListener(v -> showDatePicker(date -> { pollutionExpiry = date; btnPollutionDate.setText("Pollution Expiry: " + date); }));
        mainContainer.addView(btnPollutionDate);

        final EditText etDep = createDialogEditText(this, "Annual Depreciation / വണ്ടിയുടെ തേയ്മാനം (%):", forceNewRegistration ? "10" : String.valueOf(depreciationPercent), mainContainer);
        final Button btnBatteryDate = new Button(this); btnBatteryDate.setText("Battery Installation Date: " + (forceNewRegistration ? "Select Date" : batteryInstallDate));
        btnBatteryDate.setOnClickListener(v -> showDatePicker(date -> { batteryInstallDate = date; btnBatteryDate.setText("Battery Date: " + date); }));
        mainContainer.addView(btnBatteryDate);

        builder.setView(scrollWrapper);
        builder.setPositiveButton(forceNewRegistration ? "Register This Vehicle" : "Save Profile Mappings", (dialog, which) -> {
            String inputReg = etReg.getText().toString().trim().toUpperCase();
            String inputName = etName.getText().toString().trim();
            if (inputReg.isEmpty() || inputName.isEmpty()) { Toast.makeText(this, "Fields Required!", Toast.LENGTH_SHORT).show(); return; }
            try {
                if (forceNewRegistration) {
                    expenseFleetList.clear();
                }
                regNumber = inputReg; vehicleName = inputName;
                vehicleOdoBaseline = Double.parseDouble(etOdoBase.getText().toString().trim());
                maxExpectedRangeKm = Double.parseDouble(etMaxRange.getText().toString().trim());
                balanceInHandNow = Double.parseDouble(etBalance.getText().toString().trim());
                fuelCapacity = Double.parseDouble(etFuelCap.getText().toString().trim());
                fuelRatePerUnit = Double.parseDouble(etFuelRate.getText().toString().trim());
                exShowroomPrice = Double.parseDouble(etExShowroom.getText().toString().trim());
                insuranceAmount = Double.parseDouble(etInsurance.getText().toString().trim());
                roadTaxAmount = Double.parseDouble(etRoadTax.getText().toString().trim());
                pollutionAmount = Double.parseDouble(etPollution.getText().toString().trim());
                depreciationPercent = Integer.parseInt(etDep.getText().toString().trim());

                calculateMathematicalDailyStandingCost(); updateFuelCalculationsMetrics();
                saveAdvancedProfileToCSV(); refreshSystemButtonUI();
            } catch (Exception e) { Toast.makeText(this, "Check formatting inputs.", Toast.LENGTH_SHORT).show(); }
        });

        subHolder[0] = builder.create();
        subHolder[0].show();
        if (subHolder[0].getWindow() != null) {
            subHolder[0].getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
            subHolder[0].getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
    }

    private void showExpensesManagerWizard() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        builder.setTitle("Garuda Yana: Fixed Expenses Manager");

        ScrollView scrollWrapper = new ScrollView(this);
        LinearLayout mainContainer = new LinearLayout(this);
        mainContainer.setOrientation(LinearLayout.VERTICAL); mainContainer.setPadding(40, 25, 40, 25);
        scrollWrapper.addView(mainContainer);

        final LinearLayout expenseContainerLayout = new LinearLayout(this);
        expenseContainerLayout.setOrientation(LinearLayout.VERTICAL); mainContainer.addView(expenseContainerLayout);

        for (CustomExpense existingExpense : expenseFleetList) { addDynamicExpenseRowEntry(this, expenseContainerLayout, existingExpense); }

        Button btnAddNewRow = new Button(this);
        btnAddNewRow.setText("➕ Add Custom Expense Item");
        btnAddNewRow.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF388E3C));
        btnAddNewRow.setOnClickListener(v -> addDynamicExpenseRowEntry(this, expenseContainerLayout, new CustomExpense("", 0.0, "Monthly")));
        mainContainer.addView(btnAddNewRow);

        builder.setView(scrollWrapper);
        builder.setPositiveButton("Save Expense Changes", (dialog, which) -> {
            try {
                ArrayList<CustomExpense> bufferList = new ArrayList<>();
                for (int i = 0; i < expenseContainerLayout.getChildCount(); i++) {
                    LinearLayout row = (LinearLayout) expenseContainerLayout.getChildAt(i);
                    String n = ((EditText) row.getChildAt(0)).getText().toString().trim();
                    String aStr = ((EditText) row.getChildAt(1)).getText().toString().trim();
                    String p = ((Spinner) row.getChildAt(2)).getSelectedItem().toString();
                    if (!n.isEmpty() && !aStr.isEmpty()) bufferList.add(new CustomExpense(n, Double.parseDouble(aStr), p));
                }
                expenseFleetList.clear(); expenseFleetList.addAll(bufferList);

                calculateMathematicalDailyStandingCost(); saveAdvancedProfileToCSV(); refreshSystemButtonUI();
                Toast.makeText(this, "Fixed Expenses successfully synchronized!", Toast.LENGTH_SHORT).show();
            } catch (Exception e) { Toast.makeText(this, "Error syncing expenses array.", Toast.LENGTH_SHORT).show(); }
        });
        builder.setNegativeButton("Close", (dialog, which) -> dialog.dismiss());

        AlertDialog d = builder.create();
        d.show();
        if (d.getWindow() != null) {
            d.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
            d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
    }

    private void showPlatformsManagerWizard() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        builder.setTitle("Garuda Yana: Delivery Platforms Spinner Node");

        final AlertDialog[] localHolder = new AlertDialog[1];

        ScrollView scrollWrapper = new ScrollView(this);
        LinearLayout mainContainer = new LinearLayout(this);
        mainContainer.setOrientation(LinearLayout.VERTICAL); mainContainer.setPadding(40, 25, 40, 25);
        scrollWrapper.addView(mainContainer);

        final LinearLayout listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL); mainContainer.addView(listContainer);

        for (String node : speechPlatformsList) {
            LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setPadding(0,10,0,10);
            TextView tvN = new TextView(this); tvN.setText(node); tvN.setTextColor(0xFFFFFFFF); tvN.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)); row.addView(tvN);
            Button btnDelNode = new Button(this); btnDelNode.setText("❌"); btnDelNode.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            btnDelNode.setOnClickListener(v -> {
                speechPlatformsList.remove(node); listContainer.removeView(row); saveAdvancedProfileToCSV();
                if (localHolder[0] != null) localHolder[0].dismiss(); showPlatformsManagerWizard();
            });
            row.addView(btnDelNode); listContainer.addView(row);
        }

        final EditText etNewAppInput = new EditText(this); etNewAppInput.setHint("Type platform label (e.g. KUMAR)"); mainContainer.addView(etNewAppInput);
        Button btnAddApp = new Button(this); btnAddApp.setText("➕ Insert New Node App"); btnAddApp.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF006064)); mainContainer.addView(btnAddApp);

        btnAddApp.setOnClickListener(v -> {
            String appLabel = etNewAppInput.getText().toString().trim().toUpperCase();
            if(!appLabel.isEmpty() && !speechPlatformsList.contains(appLabel)) {
                speechPlatformsList.add(appLabel); saveAdvancedProfileToCSV();
                if (localHolder[0] != null) localHolder[0].dismiss(); showPlatformsManagerWizard();
            }
        });

        builder.setView(scrollWrapper);
        builder.setPositiveButton("Done / പൂർത്തിയായി", (dialog, which) -> dialog.dismiss());

        AlertDialog d = builder.create(); localHolder[0] = d;
        d.show();
        if (d.getWindow() != null) {
            d.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
            d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
    }

    private void addDynamicExpenseRowEntry(Context ctx, LinearLayout container, CustomExpense expenseData) {
        final LinearLayout rowLine = new LinearLayout(ctx); rowLine.setOrientation(LinearLayout.HORIZONTAL); rowLine.setPadding(0, 8, 0, 8);

        final EditText etNameInput = new EditText(ctx); etNameInput.setHint("Label (EMI)"); etNameInput.setText(expenseData.name); etNameInput.setTextColor(0xFFFFCC00); etNameInput.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f));
        etNameInput.setOnClickListener(v -> {
            etNameInput.requestFocus();
            InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(etNameInput, InputMethodManager.SHOW_FORCED);
        });
        rowLine.addView(etNameInput);

        final EditText etAmtInput = new EditText(ctx); etAmtInput.setHint("₹"); etAmtInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); etAmtInput.setText(expenseData.amount <= 0 ? "" : String.valueOf(expenseData.amount)); etAmtInput.setTextColor(0xFFFFCC00); etAmtInput.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.9f));
        etAmtInput.setOnClickListener(v -> {
            etAmtInput.requestFocus();
            InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(etAmtInput, InputMethodManager.SHOW_FORCED);
        });
        rowLine.addView(etAmtInput);

        final Spinner spnPeriodInput = new Spinner(ctx); String[] opts = {"Daily", "Monthly", "Yearly"}; ArrayAdapter<String> adp = new ArrayAdapter<>(ctx, android.R.layout.simple_spinner_item, opts); spnPeriodInput.setAdapter(adp);
        for (int i = 0; i < opts.length; i++) { if (opts[i].equalsIgnoreCase(expenseData.period)) spnPeriodInput.setSelection(i); } spnPeriodInput.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.1f)); rowLine.addView(spnPeriodInput);
        Button btnDel = new Button(ctx); btnDel.setText("❌"); btnDel.setBackgroundColor(android.graphics.Color.TRANSPARENT); btnDel.setOnClickListener(v -> container.removeView(rowLine)); rowLine.addView(btnDel);
        container.addView(rowLine);
    }

    private void appendTripRecordToCSVFile(String appLabel, double fare) {
        if (regNumber.isEmpty()) return;
        String filename = "garuda_yana_" + regNumber + ".csv";
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        String dataLine = "TRIP_RECORD," + timestamp + "," + appLabel + "," + fare + "," + (vehicleOdoBaseline + accumulatedDistanceKm) + "," + accumulatedDistanceKm + "\n";
        try (FileOutputStream fos = openFileOutput(filename, Context.MODE_APPEND)) { fos.write(dataLine.getBytes()); } catch (Exception e) {}
    }

    private void recordIdleLocationToCSV(double latitude, double longitude, String categoryHeader) {
        if (regNumber.isEmpty()) return;
        String filename = "garuda_yana_" + regNumber + ".csv";
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        String dataLine = categoryHeader + "," + timestamp + "," + latitude + "," + longitude + "," + (vehicleOdoBaseline + accumulatedDistanceKm) + "\n";
        try (FileOutputStream fos = openFileOutput(filename, Context.MODE_APPEND)) { fos.write(dataLine.getBytes()); logToTerminal("[IDLE] Stationary spot locked to CSV."); } catch (Exception e) {}
    }

    private void dispatchShareMasterSheet() {
        File targetFile = new File(getFilesDir(), "garuda_yana_" + regNumber + ".csv"); if (!targetFile.exists()) return;
        try {
            Uri pathUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", targetFile);
            Intent shareIntent = new Intent(Intent.ACTION_SEND); shareIntent.setType("text/csv");
            shareIntent.putExtra(Intent.EXTRA_STREAM, pathUri); shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Export Garuda Yana CSV Report"));
        } catch (Exception e) {}
    }

    private void refreshSystemButtonUI() {
        if (isAppStarted) {
            btnAppToggle.setText("App Stop"); btnAppToggle.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFCC0000));
            btnTripToggle.setEnabled(true); btnTripToggle.setAlpha(1.0f);
        } else {
            btnAppToggle.setText("App Start"); btnAppToggle.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF008800));
            btnTripToggle.setEnabled(false); btnTripToggle.setAlpha(0.4f);
        }
        if (isTripStarted) { btnTripToggle.setText("TRIP STOP"); btnTripToggle.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFF3300)); }
        else { btnTripToggle.setText("TRIP START"); btnTripToggle.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFF8C00)); }
        tvOdoMeter.setText(String.format(Locale.US, "%.0f", vehicleOdoBaseline + (isTripStarted ? accumulatedDistanceKm : 0.0) + dryRunDistanceKm));
        if (!regNumber.isEmpty()) tvVehicleStatus.setText("Vehicle: " + vehicleName + " [" + regNumber + "] | Fixed Cost/Day: ₹" + String.format(Locale.US, "%.2f", dailyStandingCost));
    }

    private void checkProfileAndInitialize() {
        File dir = getFilesDir(); File[] files = dir.listFiles((dir1, name) -> name.startsWith("garuda_yana_") && name.endsWith(".csv"));
        if (files == null || files.length == 0) { regNumber = ""; vehicleName = ""; findViewById(android.R.id.content).post(() -> showVehicleManagerWizard(true)); }
        else { parseAdvancedVehicleCSV(files[0]); }
    }

    private void saveAdvancedProfileToCSV() {
        if (regNumber.isEmpty()) return;
        String filename = "garuda_yana_" + regNumber + ".csv";
        StringBuilder csv = new StringBuilder();
        csv.append("RegNumber,VehicleName,VehicleType,Ownership,FuelCap,FuelType,FuelRate,ExShowroom,VehicleOdoBaseline,MaxRangeKm,BalanceInHand,InsAmt,InsExp,TaxAmt,TaxExp,PolAmt,PolExp,DepPct,BatDate,FuelMarker,AppsList,SerializedExpenses\n");
        csv.append(regNumber).append(",").append(vehicleName).append(",").append(vehicleType).append(",").append(ownership).append(",")
                .append(fuelCapacity).append(",").append(fuelType).append(",").append(fuelRatePerUnit).append(",").append(exShowroomPrice).append(",")
                .append(vehicleOdoBaseline).append(",").append(maxExpectedRangeKm).append(",").append(balanceInHandNow).append(",")
                .append(insuranceAmount).append(",").append(insuranceExpiry).append(",").append(roadTaxAmount).append(",").append(roadTaxExpiry).append(",")
                .append(pollutionAmount).append(",").append(pollutionExpiry).append(",").append(depreciationPercent).append(",").append(batteryInstallDate).append(",")
                .append(fuelResetOdoMarker).append(",");
        for (String node : speechPlatformsList) { csv.append(node).append(";"); } csv.append(speechPlatformsList.isEmpty() ? "EMPTY," : ",");
        for (CustomExpense exp : expenseFleetList) { csv.append(exp.name).append("#").append(exp.amount).append("#").append(exp.period).append("|"); }
        csv.append(expenseFleetList.isEmpty() ? "EMPTY\n" : "\n");
        try (FileOutputStream fos = openFileOutput(filename, Context.MODE_PRIVATE)) { fos.write(csv.toString().getBytes()); } catch (Exception e) {}
    }

    private void parseAdvancedVehicleCSV(File file) {
        try (Scanner scanner = new Scanner(file)) {
            if (scanner.hasNextLine()) scanner.nextLine();
            if (scanner.hasNextLine()) {
                String[] tokens = scanner.nextLine().split(",");
                if (tokens.length >= 21) {
                    regNumber = tokens[0].trim(); vehicleName = tokens[1].trim(); vehicleType = tokens[2].trim(); ownership = tokens[3].trim();
                    fuelCapacity = Double.parseDouble(tokens[4].trim()); fuelType = tokens[5].trim(); fuelRatePerUnit = Double.parseDouble(tokens[6].trim()); exShowroomPrice = Double.parseDouble(tokens[7].trim());
                    vehicleOdoBaseline = Double.parseDouble(tokens[8].trim()); maxExpectedRangeKm = Double.parseDouble(tokens[9].trim()); balanceInHandNow = Double.parseDouble(tokens[10].trim());
                    insuranceAmount = Double.parseDouble(tokens[11].trim()); insuranceExpiry = tokens[12].trim(); roadTaxAmount = Double.parseDouble(tokens[13].trim()); roadTaxExpiry = tokens[14].trim();
                    pollutionAmount = Double.parseDouble(tokens[15].trim()); pollutionExpiry = tokens[16].trim(); depreciationPercent = Integer.parseInt(tokens[17].trim()); batteryInstallDate = tokens[18].trim();
                    fuelResetOdoMarker = Double.parseDouble(tokens[19].trim());
                    speechPlatformsList.clear(); String rawApps = tokens[20].trim();
                    if (!rawApps.equals("EMPTY") && !rawApps.isEmpty()) { for (String node : rawApps.split(";")) { speechPlatformsList.add(node); } }
                    expenseFleetList.clear(); String rawExpenses = tokens[21].trim();
                    if (!rawExpenses.equals("EMPTY") && !rawExpenses.isEmpty()) {
                        for (String item : rawExpenses.split("\\|")) { String[] sub = item.split("#"); if (sub.length >= 3) expenseFleetList.add(new CustomExpense(sub[0], Double.parseDouble(sub[1]), sub[2])); }
                    }
                    calculateMathematicalDailyStandingCost(); updateFuelCalculationsMetrics(); refreshSystemButtonUI(); return;
                }
            }
        } catch (Exception e) {}
        regNumber = ""; findViewById(android.R.id.content).post(() -> showVehicleManagerWizard(true));
    }

    private void logToTerminal(String message) {
        String timestamp = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date());
        tvTerminalLog.append("[" + timestamp + "] " + message + "\n");
        svTerminal.post(() -> svTerminal.fullScroll(View.FOCUS_DOWN));
    }

    private EditText createDialogEditText(Context ctx, String labelText, String text, LinearLayout container) {
        TextView tvLabel = new TextView(ctx); tvLabel.setText(labelText); tvLabel.setTextColor(0xFFFFFFFF); tvLabel.setPadding(10, 16, 10, 2); container.addView(tvLabel);
        EditText et = new EditText(ctx); et.setText(text); et.setTextColor(0xFFFFCC00); container.addView(et); return et;
    }

    private void showDatePicker(DateCallback callback) {
        Calendar c = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> callback.onDateSelected(String.format(Locale.US, "%02d/%02d/%d", dayOfMonth, (month + 1), year)), c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }
    interface DateCallback { void onDateSelected(String date); }

    private void calculateRemainingWorkingDays() {
        LocalDate today = java.time.LocalDate.now(); LocalDate endOfMonth = today.withDayOfMonth(today.lengthOfMonth()); int workingDaysLeft = 0; LocalDate current = today;
        while (!current.isAfter(endOfMonth)) { if (current.getDayOfWeek() != DayOfWeek.SUNDAY) workingDaysLeft++; current = current.plusDays(1); }
        tvDaysLeft.setText(String.valueOf(workingDaysLeft));
    }

    @Override protected void onDestroy() { if (speechRecognizer != null) speechRecognizer.destroy(); super.onDestroy(); }
}
