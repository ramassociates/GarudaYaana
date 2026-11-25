package com.example.garudayaana;

import android.Manifest;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.speech.SpeechRecognizer;
import android.text.Editable;
import android.text.TextWatcher;
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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Scanner;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_MASTER_CODE = 105;
    private static final String PREFS_NAME = "GarudaYaanaCabPrefsV2";

    private static final String DISCLAIMER_TEXT =
            "DISCLAIMER: GarudaYaana is provided 'AS-IS' for informational and operational estimation only. " +
            "The developer/author assumes NO responsibility or liability for any financial loss, trip acceptance decisions, " +
            "app crashes, data loss, or mobile hardware/GPS issues. The driver/user bears sole responsibility for " +
            "safe driving, traffic compliance, and business decisions.";

    // UI Elements Mapping
    private TextView tvVehicleStatus, tvDaysLeft, tvMinReqAvg, tvCurrentAvg, tvOdoMeter, tvTotalDist, tvOrdersToday, tvWaitTime, tvDryRun, tvAvgFuelCons, tvAvailFuel, tvEarnings, tvTerminalLog, tvTrainData;
    private TextView tvLiveNetProfit, tvTargetGap, tvDailyGrossTarget, tvMonthlyGrossTarget, tvYearlyGrossTarget;
    private TextView tvPlatformFeasibility, tvFlashOfferGuide, tvFlashFareResult, tvRange1to3, tvRange4to6, tvRange7to10;
    private EditText etTotalRevenueInput, etFlashFareCheck;
    private Spinner spnQuickPlatform;
    private Button btnAppToggle, btnTripToggle, btnUpdateRevenue;
    private ScrollView svTerminal;

    // Core Operational Engines
    private LocationManager locationManager;
    private LocationListener locationListener;
    private Location previousLocation = null;
    private SpeechRecognizer speechRecognizer;
    private SharedPreferences shiftPrefs;

    // Fluid Mathematical Trackers
    private boolean isAppStarted = false;
    private boolean isTripStarted = false;
    private double accumulatedDistanceKm = 0.0;
    private double dailyEarningsSum = 0.0;
    private int dailyOrdersCount = 0;
    private double dryRunDistanceKm = 0.0;
    private double dailySpotExpenses = 0.0;

    // Real-Time Profit & Fuel Value Engine Variables (30-Day Split)
    private double dailyNetIncomeGoal = 2000.0;
    private double shiftStartFuelValueRs = 0.0;
    private double currentFuelPercent = 100.0;
    private double minOperatingExpenditure = 0.0;
    private double currentRatePerKm = 0.0;

    // Time Interlock Parameters
    private long tripStopTimestamp = 0;
    private long totalWaitingTimeMs = 0;
    private long lastMoveTimestamp = 0;
    private Location lastIdleRecordedLocation = null;

    // Vehicle + Tyre Cost Core Memory Variables
    private String regNumber = "";
    private String vehicleName = "";
    private String vehicleType = "Cab-CNG";
    private String ownership = "Commercial";
    private double fuelCapacity = 9.0;
    private String fuelType = "CNG";
    private double fuelRatePerUnit = 92.0;
    private double exShowroomPrice = 750000.0;
    private double insuranceAmount = 24000.0;
    private String insuranceExpiry = "Select Date";
    private double roadTaxAmount = 8000.0;
    private String roadTaxExpiry = "Select Date";
    private double pollutionAmount = 500.0;
    private String pollutionExpiry = "Select Date";
    private int depreciationPercent = 10;
    private String batteryInstallDate = "Select Date";
    private double vehicleOdoBaseline = 0.0;
    private double balanceInHandNow = 0.0;
    private double maxExpectedRangeKm = 210.0;
    private double fuelResetOdoMarker = 0.0;

    // Tyre Cost Configuration (Default: 5 tyres incl. Stepney, 30,000 km life)
    private int tyreCount = 5;
    private double tyreUnitPrice = 5500.0;
    private double tyreReplacementKm = 30000.0;

    // Dynamic Nodes & Platform Trackers
    private final ArrayList<String> speechPlatformsList = new ArrayList<>();
    private final ArrayList<CustomExpense> expenseFleetList = new ArrayList<>();
    private final HashMap<String, Double> platformRevenueMap = new HashMap<>();
    private final HashMap<String, Double> platformExpenseMap = new HashMap<>();
    private final HashMap<String, String> platformSubInfoMap = new HashMap<>();
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
                displayData.append(columns[1]).append("\n");
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
        try {
            setContentView(R.layout.activity_main);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            shiftPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

            // Map Views
            tvVehicleStatus = findViewById(R.id.tvVehicleStatus);
            tvDaysLeft = findViewById(R.id.tvDaysLeft);
            tvLiveNetProfit = findViewById(R.id.tvLiveNetProfit);
            tvTargetGap = findViewById(R.id.tvTargetGap);
            tvDailyGrossTarget = findViewById(R.id.tvDailyGrossTarget);
            tvMonthlyGrossTarget = findViewById(R.id.tvMonthlyGrossTarget);
            tvYearlyGrossTarget = findViewById(R.id.tvYearlyGrossTarget);
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

            tvPlatformFeasibility = findViewById(R.id.tvPlatformFeasibility);
            tvFlashOfferGuide = findViewById(R.id.tvFlashOfferGuide);
            tvFlashFareResult = findViewById(R.id.tvFlashFareResult);
            tvRange1to3 = findViewById(R.id.tvRange1to3);
            tvRange4to6 = findViewById(R.id.tvRange4to6);
            tvRange7to10 = findViewById(R.id.tvRange7to10);

            spnQuickPlatform = findViewById(R.id.spnQuickPlatform);
            etTotalRevenueInput = findViewById(R.id.etTotalRevenueInput);
            etFlashFareCheck = findViewById(R.id.etFlashFareCheck);
            btnUpdateRevenue = findViewById(R.id.btnUpdateRevenue);

            btnAppToggle = findViewById(R.id.btnAppToggle);
            Button btnExitApp = findViewById(R.id.btnExitApp);
            btnTripToggle = findViewById(R.id.btnTripToggle);
            Button btnVehicleConfig = findViewById(R.id.btnVehicleConfig);
            Button btnSubPlan = findViewById(R.id.btnSubPlan);
            Button btnShareCSV = findViewById(R.id.btnShareCSV);
            Button btnTips = findViewById(R.id.btnTips);
            Button btnExpense = findViewById(R.id.btnExpense);

            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            initializeSpeechRecognizerEngine();

            calculateRemainingWorkingDays();
            restoreShiftStateFromStorage();
            refreshQuickPlatformSpinner();

            if (isAppStarted) {
                startIdlePassiveTrackingEngine();
                if (isTripStarted) {
                    resumeActiveTripListener();
                }
            }
            updateWaitingTimeDisplayField();
            updateLiveDashboardMetrics();
            refreshSystemButtonUI();

            // Post initialization dialogs safely after layout attaches
            findViewById(android.R.id.content).post(() -> {
                triggerDynamicSystemPermissionsScan();
                checkDisclaimerAndProfileInitialize();
            });

            // Instant One-Flash Fare Checker Watcher
            etFlashFareCheck.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    updateFlashFareInstantResult();
                }
                @Override public void afterTextChanged(Editable s) {}
            });

            // 1) APP ENGINE SWITCH CONTROL
            btnAppToggle.setOnClickListener(v -> {
                if (regNumber.isEmpty()) {
                    Toast.makeText(this, "Configure vehicle profile or Use Demo first!", Toast.LENGTH_LONG).show();
                    showFirstTimeSetupChoiceDialog();
                    return;
                }
                if (!isAppStarted) {
                    showShiftStartDialog();
                } else {
                    if (isTripStarted) {
                        stopGPSTrackingEngine();
                    }
                    double shiftTotalKm = getTotalShiftKm();
                    appendTripRecordToCSVFile("DAY_SHIFT_END", dailyEarningsSum);
                    vehicleOdoBaseline += shiftTotalKm;

                    isAppStarted = false;
                    isTripStarted = false;
                    accumulatedDistanceKm = 0.0;
                    dryRunDistanceKm = 0.0;
                    dailyEarningsSum = 0.0;
                    dailyOrdersCount = 0;
                    dailySpotExpenses = 0.0;
                    totalWaitingTimeMs = 0;
                    tripStopTimestamp = 0;
                    previousLocation = null;
                    platformRevenueMap.clear();
                    platformExpenseMap.clear();
                    platformSubInfoMap.clear();

                    saveAdvancedProfileToCSV();
                    clearPersistedShiftState();
                    stopIdlePassiveTrackingEngine();
                    updateWaitingTimeDisplayField();
                    updateLiveDashboardMetrics();
                    refreshSystemButtonUI();
                    logToTerminal("APP STOP: Day closed & saved.");
                }
            });

            // 2) PLATFORM-SPECIFIC REVENUE UPDATE
            btnUpdateRevenue.setOnClickListener(v -> {
                String rawRev = etTotalRevenueInput.getText().toString().trim();
                if (rawRev.isEmpty()) {
                    Toast.makeText(this, "Enter platform revenue collected!", Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    double platformAmt = Double.parseDouble(rawRev);
                    String selectedPlatform = (spnQuickPlatform.getSelectedItem() != null)
                            ? spnQuickPlatform.getSelectedItem().toString() : "DIRECT";

                    platformRevenueMap.put(selectedPlatform, platformAmt);

                    double sum = 0.0;
                    for (double val : platformRevenueMap.values()) {
                        sum += val;
                    }
                    dailyEarningsSum = sum;
                    dailyOrdersCount++;
                    etTotalRevenueInput.setText("");

                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.hideSoftInputFromWindow(etTotalRevenueInput.getWindowToken(), 0);

                    updateLiveDashboardMetrics();
                    persistShiftStateToStorage();
                    appendTripRecordToCSVFile("REV_" + selectedPlatform, platformAmt);
                    logToTerminal(selectedPlatform + " Rev: ₹" + String.format(Locale.US, "%.0f", platformAmt)
                            + " | Tot: ₹" + String.format(Locale.US, "%.0f", dailyEarningsSum));
                } catch (Exception e) {
                    Toast.makeText(this, "Invalid revenue amount.", Toast.LENGTH_SHORT).show();
                }
            });

            // 3) EXIT APPLICATION COMMAND
            btnExitApp.setOnClickListener(v -> {
                persistShiftStateToStorage();
                finishAffinity();
            });

            // 4) TRIP SATELLITE CONTROL
            btnTripToggle.setOnClickListener(v -> {
                if (!isAppStarted) {
                    Toast.makeText(this, "Press App Start first!", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!isTripStarted) {
                    startTripSequence();
                } else {
                    stopGPSTrackingEngine();
                }
            });

            // 5) SUBSCRIPTION PLAN ACTIVATION SHORTCUT
            btnSubPlan.setOnClickListener(v -> showSubscriptionPlanDialog());

            // 6) QUICK LIVE SPOT INCOME (TIPS)
            btnTips.setOnClickListener(v -> {
                AlertDialog.Builder b = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
                b.setTitle("Add Tip / Bonus Received (₹)");
                final EditText etTip = new EditText(this);
                etTip.setHint("Tip Amount (₹)");
                etTip.setTextColor(0xFFFFFFFF);
                etTip.setHintTextColor(0xFFB0BEC5);
                etTip.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
                b.setView(etTip);
                b.setPositiveButton("Add", (d, w) -> {
                    try {
                        double tip = Double.parseDouble(etTip.getText().toString().trim());
                        double curTips = platformRevenueMap.containsKey("TIPS") ? platformRevenueMap.get("TIPS") : 0.0;
                        platformRevenueMap.put("TIPS", curTips + tip);
                        dailyEarningsSum += tip;
                        updateLiveDashboardMetrics();
                        persistShiftStateToStorage();
                        logToTerminal("TIP: +₹" + String.format(Locale.US, "%.0f", tip));
                        appendTripRecordToCSVFile("TIP_INCOME", tip);
                    } catch (Exception ignored) {}
                });
                b.show();
            });

            // 7) SPOT / HIDDEN PLATFORM EXPENSES SHORTCUT
            btnExpense.setOnClickListener(v -> showSpotOrHiddenExpenseDialog());

            // 8) CENTRAL CONFIG HUB & SHARE
            btnVehicleConfig.setOnClickListener(v -> showCentralConfigLandingMenu());
            btnShareCSV.setOnClickListener(v -> dispatchShareMasterSheet());
        } catch (Exception fatalEx) {
            Toast.makeText(this, "Startup Notice: " + fatalEx.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        persistShiftStateToStorage();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        persistShiftStateToStorage();
    }

    // ==========================================
    // ALL FILES ACCESS & RUNTIME PERMISSIONS
    // ==========================================
    private boolean hasFullFileAccessPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void promptForAllFilesAccessPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                    .setTitle("Allow File Management Permission")
                    .setMessage("To read, write, rename, backup, and delete vehicle CSV files in Documents/GarudaYaana, please enable 'Allow access to manage all files' for GarudaYaana.")
                    .setPositiveButton("Open File Settings", (d, w) -> {
                        try {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                            intent.setData(Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        } catch (Exception e) {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                            startActivity(intent);
                        }
                    })
                    .setNegativeButton("Use App Storage", null)
                    .show();
        }
    }

    private void triggerDynamicSystemPermissionsScan() {
        ArrayList<String> perms = new ArrayList<>();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.RECORD_AUDIO);
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }
        if (!perms.isEmpty()) {
            ActivityCompat.requestPermissions(this, perms.toArray(new String[0]), PERMISSION_MASTER_CODE);
        }
    }

    // ==========================================
    // DISCLAIMER & FIRST-TIME DEMO / SETUP WIZARD
    // ==========================================
    private void checkDisclaimerAndProfileInitialize() {
        boolean accepted = shiftPrefs.getBoolean("disclaimerAccepted", false);
        if (!accepted) {
            AlertDialog.Builder b = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
            b.setTitle("Terms & Disclaimer");
            b.setMessage(DISCLAIMER_TEXT);
            b.setCancelable(false);
            b.setPositiveButton("I Understand & Accept", (d, w) -> {
                shiftPrefs.edit().putBoolean("disclaimerAccepted", true).apply();
                if (!hasFullFileAccessPermission()) {
                    promptForAllFilesAccessPermission();
                }
                loadExistingOrPromptSetup();
            });
            b.setNegativeButton("Exit App", (d, w) -> finishAffinity());
            b.show();
        } else {
            loadExistingOrPromptSetup();
        }
    }

    private void loadExistingOrPromptSetup() {
        try {
            File dir = getStorageDirectory();
            File[] files = dir.listFiles((dir1, name) -> name.startsWith("garuda_yana_") && name.endsWith(".csv"));
            if (files == null || files.length == 0) {
                regNumber = "";
                vehicleName = "";
                showFirstTimeSetupChoiceDialog();
            } else {
                parseAdvancedVehicleCSV(files[0]);
            }
        } catch (Exception e) {
            regNumber = "";
            showFirstTimeSetupChoiceDialog();
        }
    }

    private void showFirstTimeSetupChoiceDialog() {
        AlertDialog.Builder b = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        b.setTitle("GarudaYaana Quick Setup");
        b.setMessage("Choose 'Use Demo' for instant cab testing (KL01DEMO01 with EMI, CNG, 5 Tyres) or register your own vehicle.");
        b.setCancelable(false);
        b.setPositiveButton("Use Demo", (d, w) -> applyDemoProfileSetup());
        b.setNegativeButton("Register Vehicle", (d, w) -> showVehicleManagerWizard(true));
        b.show();
    }

    private void applyDemoProfileSetup() {
        regNumber = "KL01DEMO01";
        vehicleName = "Demo WagonR CNG";
        vehicleType = "Cab-CNG";
        ownership = "Commercial";
        fuelCapacity = 9.0;
        fuelType = "CNG";
        fuelRatePerUnit = 92.0;
        exShowroomPrice = 750000.0;
        insuranceAmount = 24000.0;
        insuranceExpiry = "31/12/2027";
        roadTaxAmount = 8000.0;
        roadTaxExpiry = "31/12/2027";
        pollutionAmount = 500.0;
        pollutionExpiry = "30/06/2027";
        depreciationPercent = 10;
        batteryInstallDate = "01/01/2026";
        vehicleOdoBaseline = 45000.0;
        balanceInHandNow = 5000.0;
        maxExpectedRangeKm = 210.0;
        fuelResetOdoMarker = 45000.0;

        tyreCount = 5;
        tyreUnitPrice = 5500.0;
        tyreReplacementKm = 30000.0;
        dailyNetIncomeGoal = 2000.0;

        expenseFleetList.clear();
        expenseFleetList.add(new CustomExpense("Cab EMI", 14500.0, "Monthly"));
        expenseFleetList.add(new CustomExpense("Mobile & Data", 600.0, "Monthly"));
        expenseFleetList.add(new CustomExpense("Routine Service", 1500.0, "Monthly"));

        speechPlatformsList.clear();
        speechPlatformsList.add("UBER");
        speechPlatformsList.add("RAPIDO");
        speechPlatformsList.add("KERALA SAVARI");
        speechPlatformsList.add("DIRECT");

        shiftStartFuelValueRs = fuelCapacity * fuelRatePerUnit;
        saveAdvancedProfileToCSV();
        refreshQuickPlatformSpinner();
        updateLiveDashboardMetrics();
        persistShiftStateToStorage();
        refreshSystemButtonUI();
        logToTerminal("DEMO LOADED: KL01DEMO01");
        Toast.makeText(this, "Demo Cab Profile Loaded & Saved!", Toast.LENGTH_SHORT).show();
    }

    // ==========================================
    // SUBSCRIPTION PLAN & HIDDEN EXPENSE DIALOGS
    // ==========================================
    private void showSubscriptionPlanDialog() {
        AlertDialog.Builder b = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        b.setTitle("Activate Platform Subscription Plan");

        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(35, 15, 35, 15);

        TextView tvP = new TextView(this);
        tvP.setText("Select Platform:");
        tvP.setTextColor(0xFFFFFFFF);
        l.addView(tvP);

        final Spinner spnPlat = new Spinner(this);
        ArrayAdapter<String> adp = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, speechPlatformsList);
        spnPlat.setAdapter(adp);
        l.addView(spnPlat);

        TextView tvA = new TextView(this);
        tvA.setText("Subscription Plan Fee (₹) [e.g. 165 for 24/7 Pass]:");
        tvA.setTextColor(0xFFFFFFFF);
        tvA.setPadding(0, 12, 0, 0);
        l.addView(tvA);

        final EditText etFee = new EditText(this);
        etFee.setHint("Amount (₹)");
        etFee.setText("165");
        etFee.setTextColor(0xFFFFFFFF);
        etFee.setHintTextColor(0xFFB0BEC5);
        etFee.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        l.addView(etFee);

        TextView tvD = new TextView(this);
        tvD.setText("Validity / Note (e.g. 24 Hrs Unlimited):");
        tvD.setTextColor(0xFFFFFFFF);
        tvD.setPadding(0, 12, 0, 0);
        l.addView(tvD);

        final EditText etNote = new EditText(this);
        etNote.setHint("Plan Validity / Note");
        etNote.setText("24H Pass");
        etNote.setTextColor(0xFFFFFFFF);
        etNote.setHintTextColor(0xFFB0BEC5);
        l.addView(etNote);

        b.setView(l);
        b.setPositiveButton("Activate Plan Now", (d, w) -> {
            try {
                String plat = spnPlat.getSelectedItem().toString();
                double fee = Double.parseDouble(etFee.getText().toString().trim());
                String note = etNote.getText().toString().trim();
                String actTime = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date());

                double curExp = platformExpenseMap.containsKey(plat) ? platformExpenseMap.get(plat) : 0.0;
                platformExpenseMap.put(plat, curExp + fee);
                platformSubInfoMap.put(plat, "Active @" + actTime + " (" + note + ")");
                dailySpotExpenses += fee;

                updateLiveDashboardMetrics();
                persistShiftStateToStorage();
                appendTripRecordToCSVFile("SUB_PLAN_" + plat + "_" + actTime, fee);
                logToTerminal("SUB [" + plat + "] ₹" + String.format(Locale.US, "%.0f", fee) + " @" + actTime);
            } catch (Exception e) {
                Toast.makeText(this, "Check subscription fee input.", Toast.LENGTH_SHORT).show();
            }
        });
        b.setNegativeButton("Cancel", (d, w) -> d.dismiss());
        b.show();
    }

    private void showSpotOrHiddenExpenseDialog() {
        AlertDialog.Builder b = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        b.setTitle("Add Expense / Hidden Platform Charge (₹)");

        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(35, 15, 35, 15);

        TextView tvP = new TextView(this);
        tvP.setText("Platform / Category:");
        tvP.setTextColor(0xFFFFFFFF);
        l.addView(tvP);

        ArrayList<String> cats = new ArrayList<>(speechPlatformsList);
        cats.add("GENERAL / TOLL / TEA");
        final Spinner spnCat = new Spinner(this);
        spnCat.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, cats));
        l.addView(spnCat);

        TextView tvHeadLbl = new TextView(this);
        tvHeadLbl.setText("Expense Head (e.g. Uber Hidden Charge, Toll, Tea):");
        tvHeadLbl.setTextColor(0xFFFFFFFF);
        tvHeadLbl.setPadding(0, 10, 0, 0);
        l.addView(tvHeadLbl);

        final EditText etLabel = new EditText(this);
        etLabel.setHint("e.g. Hidden Charge / Toll / Tea");
        etLabel.setTextColor(0xFFFFFFFF);
        etLabel.setHintTextColor(0xFFB0BEC5);
        l.addView(etLabel);

        TextView tvAmtLbl = new TextView(this);
        tvAmtLbl.setText("Amount (₹):");
        tvAmtLbl.setTextColor(0xFFFFFFFF);
        tvAmtLbl.setPadding(0, 10, 0, 0);
        l.addView(tvAmtLbl);

        final EditText etAmt = new EditText(this);
        etAmt.setHint("Enter Amount (₹)");
        etAmt.setTextColor(0xFFFFFFFF);
        etAmt.setHintTextColor(0xFFB0BEC5);
        etAmt.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        l.addView(etAmt);

        b.setView(l);
        b.setPositiveButton("Save Expense", (d, w) -> {
            try {
                String cat = spnCat.getSelectedItem().toString();
                String lbl = etLabel.getText().toString().trim();
                if (lbl.isEmpty()) lbl = "HIDDEN_CHARGE";
                double amt = Double.parseDouble(etAmt.getText().toString().trim());

                dailySpotExpenses += amt;
                if (speechPlatformsList.contains(cat)) {
                    double curPExp = platformExpenseMap.containsKey(cat) ? platformExpenseMap.get(cat) : 0.0;
                    platformExpenseMap.put(cat, curPExp + amt);
                }

                updateLiveDashboardMetrics();
                persistShiftStateToStorage();
                logToTerminal("EXP [" + cat + ":" + lbl + "] ₹" + String.format(Locale.US, "%.0f", amt));
                appendTripRecordToCSVFile("EXP_" + cat + "_" + lbl.toUpperCase().replaceAll("\\s+", "_"), amt);
            } catch (Exception ignored) {}
        });
        b.show();
    }

    // ==========================================
    // SHIFT START DIALOG
    // ==========================================
    private void showShiftStartDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        builder.setTitle("Start Cab Shift: Odometer, Fuel & Target");
        builder.setCancelable(false);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        double estimatedFuelPct = estimateCurrentFuelPercentage();

        final EditText etStartOdo = createDialogEditText(this, "Start Odometer Reading (km):",
                String.format(Locale.US, "%.0f", vehicleOdoBaseline), layout);
        etStartOdo.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);

        final EditText etFuelLevelPct = createDialogEditText(this, "Current Tank Level % (100 = Full Tank):",
                String.format(Locale.US, "%.0f", estimatedFuelPct), layout);
        etFuelLevelPct.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);

        final EditText etTargetProfit = createDialogEditText(this, "Daily Proposed Net Income Goal (₹):",
                String.format(Locale.US, "%.0f", dailyNetIncomeGoal), layout);
        etTargetProfit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);

        builder.setView(layout);
        builder.setPositiveButton("Start Live Engine", (dialog, which) -> {
            try {
                vehicleOdoBaseline = Double.parseDouble(etStartOdo.getText().toString().trim());
                currentFuelPercent = Double.parseDouble(etFuelLevelPct.getText().toString().trim());
                if (currentFuelPercent > 100.0) currentFuelPercent = 100.0;
                if (currentFuelPercent < 0.0) currentFuelPercent = 0.0;
                dailyNetIncomeGoal = Double.parseDouble(etTargetProfit.getText().toString().trim());

                double fullTankValueRs = fuelCapacity * fuelRatePerUnit;
                shiftStartFuelValueRs = fullTankValueRs * (currentFuelPercent / 100.0);
                double usedRangeKm = maxExpectedRangeKm * (1.0 - (currentFuelPercent / 100.0));
                fuelResetOdoMarker = vehicleOdoBaseline - usedRangeKm;

                accumulatedDistanceKm = 0.0;
                dryRunDistanceKm = 0.0;
                dailyEarningsSum = 0.0;
                dailyOrdersCount = 0;
                dailySpotExpenses = 0.0;
                totalWaitingTimeMs = 0;
                previousLocation = null;
                platformRevenueMap.clear();
                platformExpenseMap.clear();
                platformSubInfoMap.clear();
                isAppStarted = true;
                isTripStarted = false;
                lastMoveTimestamp = System.currentTimeMillis();

                saveAdvancedProfileToCSV();
                startIdlePassiveTrackingEngine();
                updateLiveDashboardMetrics();
                persistShiftStateToStorage();
                refreshSystemButtonUI();

                logToTerminal("SHIFT START | Odo:" + String.format(Locale.US, "%.0f", vehicleOdoBaseline)
                        + " | Fuel:₹" + String.format(Locale.US, "%.0f", shiftStartFuelValueRs));
            } catch (Exception e) {
                Toast.makeText(this, "Please check numeric inputs.", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    // ==========================================
    // 30-DAY FINANCIAL, TYRE & FEASIBILITY ENGINE
    // ==========================================
    private double getTotalShiftKm() {
        return accumulatedDistanceKm + dryRunDistanceKm;
    }

    private double getPerKmFuelCost() {
        if (maxExpectedRangeKm <= 0) return 0.0;
        return (fuelCapacity * fuelRatePerUnit) / maxExpectedRangeKm;
    }

    private double getPerKmTyreCost() {
        if (tyreReplacementKm <= 0) return 0.0;
        return (tyreCount * tyreUnitPrice) / tyreReplacementKm;
    }

    private double getTotalPerKmWearAndFuelCost() {
        return getPerKmFuelCost() + getPerKmTyreCost();
    }

    private double estimateCurrentFuelPercentage() {
        if (fuelCapacity <= 0 || maxExpectedRangeKm <= 0) return 100.0;
        double distanceSinceRefill = vehicleOdoBaseline - fuelResetOdoMarker;
        if (distanceSinceRefill < 0) distanceSinceRefill = 0;
        double remainingFuelL = fuelCapacity - (distanceSinceRefill * (fuelCapacity / maxExpectedRangeKm));
        if (remainingFuelL < 0) remainingFuelL = 0;
        if (remainingFuelL > fuelCapacity) remainingFuelL = fuelCapacity;
        return (remainingFuelL / fuelCapacity) * 100.0;
    }

    private void calculateMathematicalDailyStandingCost30Day() {
        double dailyInsurance = insuranceAmount / 360.0;
        double dailyRoadTax = roadTaxAmount / 360.0;
        double dailyPollution = pollutionAmount / 360.0;
        double dailyDepreciation = (exShowroomPrice * ((double) depreciationPercent / 100.0)) / 360.0;

        double customExpensesSum = 0.0;
        for (CustomExpense entry : expenseFleetList) {
            if (entry.amount <= 0) continue;
            if (entry.period.equalsIgnoreCase("Daily")) customExpensesSum += entry.amount;
            else if (entry.period.equalsIgnoreCase("Monthly")) customExpensesSum += entry.amount / 30.0;
            else if (entry.period.equalsIgnoreCase("Yearly")) customExpensesSum += entry.amount / 360.0;
        }

        dailyStandingCost = dailyInsurance + dailyRoadTax + dailyPollution + dailyDepreciation + customExpensesSum;
    }

    private void updateLiveDashboardMetrics() {
        calculateMathematicalDailyStandingCost30Day();

        double totalShiftKm = getTotalShiftKm();
        double perKmFuelCost = getPerKmFuelCost();
        double perKmTyreCost = getPerKmTyreCost();
        double totalRunCostPerKm = perKmFuelCost + perKmTyreCost;

        double liveFuelExpense = totalShiftKm * perKmFuelCost;
        double liveTyreWearExpense = totalShiftKm * perKmTyreCost;

        double remainingFuelValueRs = shiftStartFuelValueRs - liveFuelExpense;
        if (remainingFuelValueRs < 0) remainingFuelValueRs = 0;

        minOperatingExpenditure = dailyStandingCost + liveFuelExpense + liveTyreWearExpense + dailySpotExpenses;

        double liveNetProfit = dailyEarningsSum - minOperatingExpenditure;
        double targetGap = dailyNetIncomeGoal - liveNetProfit;

        double dailyGrossTarget = dailyStandingCost + dailyNetIncomeGoal + liveFuelExpense + liveTyreWearExpense + dailySpotExpenses;
        double monthlyGrossTarget = (dailyStandingCost + dailyNetIncomeGoal) * 30.0;
        double yearlyGrossTarget = monthlyGrossTarget * 12.0;

        tvDailyGrossTarget.setText("Daily Tgt: ₹" + String.format(Locale.US, "%.0f", dailyGrossTarget));
        tvMonthlyGrossTarget.setText("Monthly: ₹" + String.format(Locale.US, "%.0f", monthlyGrossTarget));
        tvYearlyGrossTarget.setText("Yearly: ₹" + String.format(Locale.US, "%.0f", yearlyGrossTarget));

        currentRatePerKm = (totalShiftKm > 0.05) ? (dailyEarningsSum / totalShiftKm) : 0.0;

        tvLiveNetProfit.setText("Net Profit: ₹" + String.format(Locale.US, "%.0f", liveNetProfit));
        tvLiveNetProfit.setTextColor(liveNetProfit >= 0 ? 0xFF00E676 : 0xFFFF5252);

        if (targetGap > 0) {
            tvTargetGap.setText("Target Gap: ₹" + String.format(Locale.US, "%.0f", targetGap));
            tvTargetGap.setTextColor(0xFFFFAB40);
        } else {
            tvTargetGap.setText("Target Hit: +₹" + String.format(Locale.US, "%.0f", Math.abs(targetGap)));
            tvTargetGap.setTextColor(0xFF00E676);
        }

        tvMinReqAvg.setText("Min Op Exp: ₹" + String.format(Locale.US, "%.0f", minOperatingExpenditure));
        tvCurrentAvg.setText("Curr Rate: ₹" + String.format(Locale.US, "%.1f/km", currentRatePerKm));

        tvOdoMeter.setText("Odo: " + String.format(Locale.US, "%.0f", vehicleOdoBaseline + totalShiftKm));
        tvTotalDist.setText("Shift Km: " + String.format(Locale.US, "%.1f", totalShiftKm));
        tvDryRun.setText("Dry Km: " + String.format(Locale.US, "%.1f", dryRunDistanceKm));
        tvOrdersToday.setText("Updates: " + dailyOrdersCount);
        tvEarnings.setText("Total Rev: ₹" + String.format(Locale.US, "%.0f", dailyEarningsSum));
        tvAvgFuelCons.setText("Run (F+T): ₹" + String.format(Locale.US, "%.1f/km", totalRunCostPerKm));
        tvAvailFuel.setText("Rem Fuel Val: ₹" + String.format(Locale.US, "%.0f", remainingFuelValueRs));

        updatePlatformFeasibilityBanner(totalShiftKm, totalRunCostPerKm);
        updateFeasibilityRangeChart();
        updateFlashFareInstantResult();
    }

    private void updatePlatformFeasibilityBanner(double totalShiftKm, double totalRunCostPerKm) {
        if (platformRevenueMap.isEmpty() && platformExpenseMap.isEmpty()) {
            tvPlatformFeasibility.setText("PLATFORM FEASIBILITY: Add Subscription or Revenue Update");
            tvPlatformFeasibility.setTextColor(0xFFFFF59D);
            return;
        }

        StringBuilder sb = new StringBuilder();
        double totalPlatformRev = 0.0;
        for (double r : platformRevenueMap.values()) totalPlatformRev += r;

        for (String plat : speechPlatformsList) {
            double rev = platformRevenueMap.containsKey(plat) ? platformRevenueMap.get(plat) : 0.0;
            double exp = platformExpenseMap.containsKey(plat) ? platformExpenseMap.get(plat) : 0.0;
            if (rev <= 0 && exp <= 0) continue;

            double shareRatio = (totalPlatformRev > 0) ? (rev / totalPlatformRev) : 1.0;
            double allocatedRunCost = (totalShiftKm * totalRunCostPerKm) * shareRatio;
            double platNet = rev - exp - allocatedRunCost;
            String verdict = platNet >= 0 ? "PROFIT" : "LOSS";

            if (sb.length() > 0) sb.append(" | ");
            sb.append(plat).append(": ")
              .append(verdict).append(" (₹").append(String.format(Locale.US, "%.0f", platNet)).append(")");
        }

        if (sb.length() == 0) {
            tvPlatformFeasibility.setText("PLATFORM FEASIBILITY: Ready");
        } else {
            tvPlatformFeasibility.setText(sb.toString());
        }
    }

    private double getMinBreakEvenRatePerKm() {
        double totalRunCostPerKm = getTotalPerKmWearAndFuelCost();
        double standingPerKm = dailyStandingCost / 120.0;
        return totalRunCostPerKm + standingPerKm;
    }

    private void updateFeasibilityRangeChart() {
        double minBreakEvenPerKm = getMinBreakEvenRatePerKm();

        double maxKm150 = minBreakEvenPerKm > 0 ? (150.0 / minBreakEvenPerKm) : 0;
        double maxKm280 = minBreakEvenPerKm > 0 ? (280.0 / minBreakEvenPerKm) : 0;
        double maxKm480 = minBreakEvenPerKm > 0 ? (480.0 / minBreakEvenPerKm) : 0;
        double maxKm500 = minBreakEvenPerKm > 0 ? (500.0 / minBreakEvenPerKm) : 0;

        tvFlashOfferGuide.setText(String.format(Locale.US,
                "INCLUDING PICKUP & DROP MAX KM -> ₹150: %.1fkm | ₹280: %.1fkm | ₹480: %.1fkm | ₹500: %.1fkm",
                maxKm150, maxKm280, maxKm480, maxKm500));

        tvRange1to3.setText(String.format(Locale.US, "1–3 km\nMin: ₹%.0f–%.0f\nCur: ₹%.0f–%.0f",
                Math.ceil(1.0 * minBreakEvenPerKm), Math.ceil(3.0 * minBreakEvenPerKm),
                Math.round(1.0 * currentRatePerKm), Math.round(3.0 * currentRatePerKm)));

        tvRange4to6.setText(String.format(Locale.US, "4–6 km\nMin: ₹%.0f–%.0f\nCur: ₹%.0f–%.0f",
                Math.ceil(4.0 * minBreakEvenPerKm), Math.ceil(6.0 * minBreakEvenPerKm),
                Math.round(4.0 * currentRatePerKm), Math.round(6.0 * currentRatePerKm)));

        tvRange7to10.setText(String.format(Locale.US, "7–10 km\nMin: ₹%.0f–%.0f\nCur: ₹%.0f–%.0f",
                Math.ceil(7.0 * minBreakEvenPerKm), Math.ceil(10.0 * minBreakEvenPerKm),
                Math.round(7.0 * currentRatePerKm), Math.round(10.0 * currentRatePerKm)));
    }

    private void updateFlashFareInstantResult() {
        if (etFlashFareCheck == null || tvFlashFareResult == null) return;
        String raw = etFlashFareCheck.getText().toString().trim();
        if (raw.isEmpty()) {
            tvFlashFareResult.setText("INCLUDING PICKUP & DROP: Max -- km");
            return;
        }
        try {
            double offerFare = Double.parseDouble(raw);
            double minRate = getMinBreakEvenRatePerKm();
            double maxSafeKm = minRate > 0 ? (offerFare / minRate) : 0.0;
            tvFlashFareResult.setText(String.format(Locale.US, "INCLUDING PICKUP & DROP: Max %.1f km", maxSafeKm));
        } catch (Exception e) {
            tvFlashFareResult.setText("INCLUDING PICKUP & DROP: Max -- km");
        }
    }

    // ==========================================
    // CENTRAL CONFIG HUB (CSV FILE RENAME / BACKUP / DELETE + USE DEMO + RESET)
    // ==========================================
    private void showCentralConfigLandingMenu() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        builder.setTitle("GarudaYaana: Configurations Hub");

        final AlertDialog[] landingDialog = new AlertDialog[1];

        ScrollView scroll = new ScrollView(this);
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(40, 25, 40, 25);
        scroll.addView(mainLayout);

        Button btnVeh = new Button(this);
        btnVeh.setText("1. Vehicle, Tyres & Refill");
        btnVeh.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF006064));
        btnVeh.setTextColor(0xFFFFFFFF);
        btnVeh.setOnClickListener(v -> {
            if (landingDialog[0] != null) landingDialog[0].dismiss();
            showVehicleManagerWizard(false);
        });
        mainLayout.addView(btnVeh);

        Button btnExp = new Button(this);
        btnExp.setText("2. Fixed Monthly/Daily Expenses & EMIs");
        btnExp.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF388E3C));
        btnExp.setTextColor(0xFFFFFFFF);
        btnExp.setOnClickListener(v -> {
            if (landingDialog[0] != null) landingDialog[0].dismiss();
            showExpensesManagerWizard();
        });
        mainLayout.addView(btnExp);

        Button btnApps = new Button(this);
        btnApps.setText("3. Manage Platforms (Uber, Rapido...)");
        btnApps.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFE65100));
        btnApps.setTextColor(0xFFFFFFFF);
        btnApps.setOnClickListener(v -> {
            if (landingDialog[0] != null) landingDialog[0].dismiss();
            showPlatformsManagerWizard();
        });
        mainLayout.addView(btnApps);

        Button btnFileMgr = new Button(this);
        btnFileMgr.setText("4. CSV File Manager (Rename / Backup / Delete)");
        btnFileMgr.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF4527A0));
        btnFileMgr.setTextColor(0xFFFFFFFF);
        btnFileMgr.setOnClickListener(v -> {
            if (landingDialog[0] != null) landingDialog[0].dismiss();
            showCsvFileManagerDialog();
        });
        mainLayout.addView(btnFileMgr);

        Button btnFilePerm = new Button(this);
        btnFilePerm.setText(hasFullFileAccessPermission()
                ? "5. File Storage Permission: GRANTED"
                : "5. Grant File Storage Permission (All Files)");
        btnFilePerm.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                hasFullFileAccessPermission() ? 0xFF2E7D32 : 0xFFD84315));
        btnFilePerm.setTextColor(0xFFFFFFFF);
        btnFilePerm.setOnClickListener(v -> {
            if (landingDialog[0] != null) landingDialog[0].dismiss();
            promptForAllFilesAccessPermission();
        });
        mainLayout.addView(btnFilePerm);

        Button btnDemo = new Button(this);
        btnDemo.setText("6. Use Demo Vehicle Setup");
        btnDemo.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF283593));
        btnDemo.setTextColor(0xFFFFFFFF);
        btnDemo.setOnClickListener(v -> {
            if (landingDialog[0] != null) landingDialog[0].dismiss();
            applyDemoProfileSetup();
        });
        mainLayout.addView(btnDemo);

        Button btnFullReset = new Button(this);
        btnFullReset.setText("7. Full Reset (Delete All CSV & Shift Data)");
        btnFullReset.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFB71C1C));
        btnFullReset.setTextColor(0xFFFFFFFF);
        btnFullReset.setOnClickListener(v -> {
            if (landingDialog[0] != null) landingDialog[0].dismiss();
            confirmAndExecuteFullReset();
        });
        mainLayout.addView(btnFullReset);

        Button btnDisclaimer = new Button(this);
        btnDisclaimer.setText("8. View Terms & Disclaimer");
        btnDisclaimer.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF424242));
        btnDisclaimer.setTextColor(0xFFFFFFFF);
        btnDisclaimer.setOnClickListener(v -> {
            new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                    .setTitle("Terms & Disclaimer")
                    .setMessage(DISCLAIMER_TEXT)
                    .setPositiveButton("OK", null)
                    .show();
        });
        mainLayout.addView(btnDisclaimer);

        builder.setView(scroll);
        builder.setNegativeButton("Close", (dialog, which) -> dialog.dismiss());

        landingDialog[0] = builder.create();
        landingDialog[0].show();
    }

    private void showCsvFileManagerDialog() {
        File dir = getStorageDirectory();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".csv"));
        if (files == null || files.length == 0) {
            Toast.makeText(this, "No CSV files found in " + dir.getAbsolutePath(), Toast.LENGTH_LONG).show();
            return;
        }

        String[] names = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            names[i] = files[i].getName();
        }

        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Select CSV File (" + dir.getName() + ")")
                .setItems(names, (d, idx) -> showSingleCsvActionDialog(files[idx]))
                .setNegativeButton("Close", null)
                .show();
    }

    private void showSingleCsvActionDialog(final File targetFile) {
        String[] actions = {"Backup CSV (Create Timestamped Copy)", "Rename CSV File", "Delete CSV File", "Share / Export CSV"};
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(targetFile.getName())
                .setItems(actions, (d, which) -> {
                    if (which == 0) {
                        backupCsvFile(targetFile);
                    } else if (which == 1) {
                        renameCsvFilePrompt(targetFile);
                    } else if (which == 2) {
                        deleteCsvFilePrompt(targetFile);
                    } else if (which == 3) {
                        shareSpecificFile(targetFile);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void backupCsvFile(File sourceFile) {
        try {
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String baseName = sourceFile.getName().replace(".csv", "");
            File backupFile = new File(sourceFile.getParentFile(), baseName + "_BACKUP_" + stamp + ".csv");
            try (FileInputStream in = new FileInputStream(sourceFile);
                 FileOutputStream out = new FileOutputStream(backupFile)) {
                byte[] buf = new byte[4096];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }
            logToTerminal("BACKUP: " + backupFile.getName());
            Toast.makeText(this, "Backup created: " + backupFile.getName(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Backup failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void renameCsvFilePrompt(File sourceFile) {
        final EditText etNewName = new EditText(this);
        etNewName.setText(sourceFile.getName().replace(".csv", ""));
        etNewName.setTextColor(0xFFFFFFFF);
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Rename CSV File")
                .setView(etNewName)
                .setPositiveButton("Rename", (d, w) -> {
                    String clean = etNewName.getText().toString().trim().replaceAll("[^A-Za-z0-9_-]", "");
                    if (clean.isEmpty()) return;
                    File destFile = new File(sourceFile.getParentFile(), clean + ".csv");
                    if (sourceFile.renameTo(destFile)) {
                        if (clean.startsWith("garuda_yana_")) {
                            regNumber = clean.replace("garuda_yana_", "").toUpperCase();
                            refreshSystemButtonUI();
                        }
                        logToTerminal("RENAMED: " + destFile.getName());
                        Toast.makeText(this, "Renamed to " + destFile.getName(), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Rename failed. Check File Permission.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteCsvFilePrompt(File targetFile) {
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Delete File?")
                .setMessage("Permanently delete " + targetFile.getName() + "?")
                .setPositiveButton("Delete", (d, w) -> {
                    if (targetFile.delete()) {
                        logToTerminal("DELETED: " + targetFile.getName());
                        Toast.makeText(this, "Deleted " + targetFile.getName(), Toast.LENGTH_SHORT).show();
                        if (targetFile.getName().equals("garuda_yana_" + regNumber + ".csv")) {
                            regNumber = "";
                            vehicleName = "";
                            refreshSystemButtonUI();
                        }
                    } else {
                        Toast.makeText(this, "Delete failed. Grant All Files Access in Config.", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmAndExecuteFullReset() {
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Confirm Full Reset")
                .setMessage("This will delete all saved vehicle CSV files and reset all shift metrics. Continue?")
                .setPositiveButton("Reset Everything", (d, w) -> {
                    if (isTripStarted) stopGPSTrackingEngine();
                    stopIdlePassiveTrackingEngine();
                    isAppStarted = false;
                    isTripStarted = false;
                    accumulatedDistanceKm = 0.0;
                    dryRunDistanceKm = 0.0;
                    dailyEarningsSum = 0.0;
                    dailyOrdersCount = 0;
                    dailySpotExpenses = 0.0;
                    totalWaitingTimeMs = 0;
                    platformRevenueMap.clear();
                    platformExpenseMap.clear();
                    platformSubInfoMap.clear();
                    expenseFleetList.clear();

                    File dir = getStorageDirectory();
                    File[] files = dir.listFiles((dir1, name) -> name.endsWith(".csv"));
                    if (files != null) {
                        for (File f : files) {
                            //noinspection ResultOfMethodCallIgnored
                            f.delete();
                        }
                    }
                    clearPersistedShiftState();
                    regNumber = "";
                    vehicleName = "";
                    tvTerminalLog.setText("Full Reset Completed.\n");
                    updateLiveDashboardMetrics();
                    refreshSystemButtonUI();
                    showFirstTimeSetupChoiceDialog();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showVehicleManagerWizard(final boolean forceNewRegistration) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        builder.setTitle(forceNewRegistration ? "Register Cab / Vehicle" : "Vehicle, Tyres & Refill");
        builder.setCancelable(!regNumber.isEmpty());

        final AlertDialog[] subHolder = new AlertDialog[1];

        ScrollView scrollWrapper = new ScrollView(this);
        LinearLayout mainContainer = new LinearLayout(this);
        mainContainer.setOrientation(LinearLayout.VERTICAL);
        mainContainer.setPadding(40, 25, 40, 25);
        scrollWrapper.addView(mainContainer);

        File dir = getStorageDirectory();
        File[] files = dir.listFiles((dir1, name) -> name.startsWith("garuda_yana_") && !name.contains("_BACKUP_") && name.endsWith(".csv"));
        final ArrayList<String> registeredVehicles = new ArrayList<>();
        if (files != null) {
            for (File f : files) {
                String id = f.getName().replace("garuda_yana_", "").replace(".csv", "");
                if (!id.isEmpty()) registeredVehicles.add(id);
            }
        }

        if (!forceNewRegistration && registeredVehicles.size() > 1) {
            Button btnSwitch = new Button(this);
            btnSwitch.setText("Switch Vehicle Profile (" + registeredVehicles.size() + " Saved)");
            btnSwitch.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF006064));
            btnSwitch.setTextColor(0xFFFFFFFF);
            btnSwitch.setOnClickListener(v -> {
                AlertDialog.Builder selectBox = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
                selectBox.setTitle("Select Vehicle CSV Profile");
                String[] array = registeredVehicles.toArray(new String[0]);
                selectBox.setItems(array, (dialogInterface, index) -> {
                    parseAdvancedVehicleCSV(new File(getStorageDirectory(), "garuda_yana_" + array[index] + ".csv"));
                    if (subHolder[0] != null) subHolder[0].dismiss();
                    Toast.makeText(this, "Switched to: " + array[index], Toast.LENGTH_SHORT).show();
                });
                selectBox.show();
            });
            mainContainer.addView(btnSwitch);
        }

        if (!forceNewRegistration && !regNumber.isEmpty()) {
            Button btnCreateNew = new Button(this);
            btnCreateNew.setText("+ Register Another Vehicle");
            btnCreateNew.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF388E3C));
            btnCreateNew.setTextColor(0xFFFFFFFF);
            btnCreateNew.setOnClickListener(v -> {
                if (subHolder[0] != null) subHolder[0].dismiss();
                showVehicleManagerWizard(true);
            });
            mainContainer.addView(btnCreateNew);
        }

        Button btnRefill = new Button(this);
        btnRefill.setText("REFILL TANK (RESET FUEL TO 100%)");
        btnRefill.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFE65100));
        btnRefill.setTextColor(0xFFFFFFFF);
        btnRefill.setOnClickListener(v -> {
            fuelResetOdoMarker = vehicleOdoBaseline + getTotalShiftKm();
            currentFuelPercent = 100.0;
            shiftStartFuelValueRs = fuelCapacity * fuelRatePerUnit;
            updateLiveDashboardMetrics();
            persistShiftStateToStorage();
            saveAdvancedProfileToCSV();
            appendTripRecordToCSVFile("REFILL_100_PCT", shiftStartFuelValueRs);
            logToTerminal("REFILL: ₹" + String.format(Locale.US, "%.0f", shiftStartFuelValueRs));
            Toast.makeText(this, "Tank refilled! Fuel Value calibrated to ₹" + String.format(Locale.US, "%.0f", shiftStartFuelValueRs), Toast.LENGTH_SHORT).show();
        });
        mainContainer.addView(btnRefill);

        final EditText etReg = createDialogEditText(this, "Registration Number (CSV Filename):", forceNewRegistration ? "" : regNumber, mainContainer);
        final EditText etName = createDialogEditText(this, "Vehicle Name:", forceNewRegistration ? "" : vehicleName, mainContainer);
        final EditText etOdoBase = createDialogEditText(this, "Current Odometer Base (km):", forceNewRegistration ? "0" : String.format(Locale.US, "%.0f", vehicleOdoBaseline), mainContainer);
        final EditText etMaxRange = createDialogEditText(this, "Expected Range per Full Tank (km):", forceNewRegistration ? "210" : String.valueOf(maxExpectedRangeKm), mainContainer);
        final EditText etFuelCap = createDialogEditText(this, "Fuel/CNG Tank Capacity (Liters/kg):", forceNewRegistration ? "9" : String.valueOf(fuelCapacity), mainContainer);
        final EditText etFuelRate = createDialogEditText(this, "Fuel/CNG Rate per Unit (₹):", forceNewRegistration ? "92" : String.valueOf(fuelRatePerUnit), mainContainer);

        final EditText etTyreCount = createDialogEditText(this, "Total Tyres (incl. Stepney, e.g. 5):", forceNewRegistration ? "5" : String.valueOf(tyreCount), mainContainer);
        final EditText etTyrePrice = createDialogEditText(this, "Price per Tyre (₹):", forceNewRegistration ? "5500" : String.valueOf(tyreUnitPrice), mainContainer);
        final EditText etTyreLifeKm = createDialogEditText(this, "Tyre Replacement Life (km, default 30000):", forceNewRegistration ? "30000" : String.valueOf(tyreReplacementKm), mainContainer);

        final EditText etExShowroom = createDialogEditText(this, "Vehicle Ex-Showroom Price (₹):", forceNewRegistration ? "750000" : String.valueOf(exShowroomPrice), mainContainer);
        final EditText etBalance = createDialogEditText(this, "Balance In Hand Now (₹):", forceNewRegistration ? "0" : String.valueOf(balanceInHandNow), mainContainer);

        final EditText etInsurance = createDialogEditText(this, "Annual Insurance Premium (₹):", forceNewRegistration ? "24000" : String.valueOf(insuranceAmount), mainContainer);
        final Button btnInsDate = new Button(this);
        btnInsDate.setText("Insurance Expiry: " + (forceNewRegistration ? "Select Date" : insuranceExpiry));
        btnInsDate.setOnClickListener(v -> showDatePicker(date -> { insuranceExpiry = date; btnInsDate.setText("Insurance Expiry: " + date); }));
        mainContainer.addView(btnInsDate);

        final EditText etRoadTax = createDialogEditText(this, "Annual Road Tax Amount (₹):", forceNewRegistration ? "8000" : String.valueOf(roadTaxAmount), mainContainer);
        final Button btnRoadTaxDate = new Button(this);
        btnRoadTaxDate.setText("Road Tax Expiry: " + (forceNewRegistration ? "Select Date" : roadTaxExpiry));
        btnRoadTaxDate.setOnClickListener(v -> showDatePicker(date -> { roadTaxExpiry = date; btnRoadTaxDate.setText("Road Tax Expiry: " + date); }));
        mainContainer.addView(btnRoadTaxDate);

        final EditText etPollution = createDialogEditText(this, "Annual Pollution Cost (₹):", forceNewRegistration ? "500" : String.valueOf(pollutionAmount), mainContainer);
        final Button btnPollutionDate = new Button(this);
        btnPollutionDate.setText("Pollution Expiry: " + (forceNewRegistration ? "Select Date" : pollutionExpiry));
        btnPollutionDate.setOnClickListener(v -> showDatePicker(date -> { pollutionExpiry = date; btnPollutionDate.setText("Pollution Expiry: " + date); }));
        mainContainer.addView(btnPollutionDate);

        final EditText etDep = createDialogEditText(this, "Annual Depreciation (%):", forceNewRegistration ? "10" : String.valueOf(depreciationPercent), mainContainer);
        final Button btnBatteryDate = new Button(this);
        btnBatteryDate.setText("Battery Installation Date: " + (forceNewRegistration ? "Select Date" : batteryInstallDate));
        btnBatteryDate.setOnClickListener(v -> showDatePicker(date -> { batteryInstallDate = date; btnBatteryDate.setText("Battery Date: " + date); }));
        mainContainer.addView(btnBatteryDate);

        builder.setView(scrollWrapper);
        builder.setPositiveButton(forceNewRegistration ? "Save & Start" : "Save Profile", (dialog, which) -> {
            String inputReg = etReg.getText().toString().trim().toUpperCase().replaceAll("[^A-Z0-9_-]", "");
            String inputName = etName.getText().toString().trim();
            if (inputReg.isEmpty() || inputName.isEmpty()) {
                Toast.makeText(this, "Registration Number & Vehicle Name Required!", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                if (forceNewRegistration) {
                    expenseFleetList.clear();
                }
                regNumber = inputReg;
                vehicleName = inputName;
                vehicleOdoBaseline = Double.parseDouble(etOdoBase.getText().toString().trim());
                maxExpectedRangeKm = Double.parseDouble(etMaxRange.getText().toString().trim());
                fuelCapacity = Double.parseDouble(etFuelCap.getText().toString().trim());
                fuelRatePerUnit = Double.parseDouble(etFuelRate.getText().toString().trim());
                tyreCount = Integer.parseInt(etTyreCount.getText().toString().trim());
                tyreUnitPrice = Double.parseDouble(etTyrePrice.getText().toString().trim());
                tyreReplacementKm = Double.parseDouble(etTyreLifeKm.getText().toString().trim());
                exShowroomPrice = Double.parseDouble(etExShowroom.getText().toString().trim());
                balanceInHandNow = Double.parseDouble(etBalance.getText().toString().trim());
                insuranceAmount = Double.parseDouble(etInsurance.getText().toString().trim());
                roadTaxAmount = Double.parseDouble(etRoadTax.getText().toString().trim());
                pollutionAmount = Double.parseDouble(etPollution.getText().toString().trim());
                depreciationPercent = Integer.parseInt(etDep.getText().toString().trim());

                shiftStartFuelValueRs = (fuelCapacity * fuelRatePerUnit) * (estimateCurrentFuelPercentage() / 100.0);
                updateLiveDashboardMetrics();
                persistShiftStateToStorage();
                saveAdvancedProfileToCSV();
                refreshSystemButtonUI();
            } catch (Exception e) {
                Toast.makeText(this, "Check numeric inputs.", Toast.LENGTH_SHORT).show();
            }
        });

        subHolder[0] = builder.create();
        subHolder[0].show();
    }

    private void showExpensesManagerWizard() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        builder.setTitle("Fixed Expenses & EMIs (30-Day Split)");

        ScrollView scrollWrapper = new ScrollView(this);
        LinearLayout mainContainer = new LinearLayout(this);
        mainContainer.setOrientation(LinearLayout.VERTICAL);
        mainContainer.setPadding(40, 25, 40, 25);
        scrollWrapper.addView(mainContainer);

        final LinearLayout expenseContainerLayout = new LinearLayout(this);
        expenseContainerLayout.setOrientation(LinearLayout.VERTICAL);
        mainContainer.addView(expenseContainerLayout);

        for (CustomExpense existingExpense : expenseFleetList) {
            addDynamicExpenseRowEntry(this, expenseContainerLayout, existingExpense);
        }

        Button btnAddNewRow = new Button(this);
        btnAddNewRow.setText("+ Add EMI / Fixed Expense Item");
        btnAddNewRow.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF388E3C));
        btnAddNewRow.setTextColor(0xFFFFFFFF);
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
                    if (!n.isEmpty() && !aStr.isEmpty()) {
                        bufferList.add(new CustomExpense(n, Double.parseDouble(aStr), p));
                    }
                }
                expenseFleetList.clear();
                expenseFleetList.addAll(bufferList);

                updateLiveDashboardMetrics();
                persistShiftStateToStorage();
                saveAdvancedProfileToCSV();
                refreshSystemButtonUI();
                Toast.makeText(this, "Fixed Expenses saved!", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Error saving expenses.", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Close", (dialog, which) -> dialog.dismiss());
        builder.create().show();
    }

    private void showPlatformsManagerWizard() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        builder.setTitle("Manage Ride-Hailing Platforms");

        final AlertDialog[] localHolder = new AlertDialog[1];

        ScrollView scrollWrapper = new ScrollView(this);
        LinearLayout mainContainer = new LinearLayout(this);
        mainContainer.setOrientation(LinearLayout.VERTICAL);
        mainContainer.setPadding(40, 25, 40, 25);
        scrollWrapper.addView(mainContainer);

        final LinearLayout listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        mainContainer.addView(listContainer);

        for (String node : speechPlatformsList) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 10, 0, 10);
            TextView tvN = new TextView(this);
            tvN.setText(node);
            tvN.setTextColor(0xFFFFFFFF);
            tvN.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
            row.addView(tvN);
            Button btnDelNode = new Button(this);
            btnDelNode.setText("X");
            btnDelNode.setTextColor(0xFFFF5252);
            btnDelNode.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            btnDelNode.setOnClickListener(v -> {
                speechPlatformsList.remove(node);
                listContainer.removeView(row);
                saveAdvancedProfileToCSV();
                refreshQuickPlatformSpinner();
                if (localHolder[0] != null) localHolder[0].dismiss();
                showPlatformsManagerWizard();
            });
            row.addView(btnDelNode);
            listContainer.addView(row);
        }

        final EditText etNewAppInput = new EditText(this);
        etNewAppInput.setHint("Type platform label (e.g. UBER)");
        etNewAppInput.setTextColor(0xFFFFFFFF);
        etNewAppInput.setHintTextColor(0xFFB0BEC5);
        mainContainer.addView(etNewAppInput);
        Button btnAddApp = new Button(this);
        btnAddApp.setText("+ Insert New Platform");
        btnAddApp.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF006064));
        btnAddApp.setTextColor(0xFFFFFFFF);
        mainContainer.addView(btnAddApp);

        btnAddApp.setOnClickListener(v -> {
            String appLabel = etNewAppInput.getText().toString().trim().toUpperCase();
            if (!appLabel.isEmpty() && !speechPlatformsList.contains(appLabel)) {
                speechPlatformsList.add(appLabel);
                saveAdvancedProfileToCSV();
                refreshQuickPlatformSpinner();
                if (localHolder[0] != null) localHolder[0].dismiss();
                showPlatformsManagerWizard();
            }
        });

        builder.setView(scrollWrapper);
        builder.setPositiveButton("Done", (dialog, which) -> dialog.dismiss());

        AlertDialog d = builder.create();
        localHolder[0] = d;
        d.show();
    }

    private void refreshQuickPlatformSpinner() {
        if (spnQuickPlatform == null) return;
        ArrayAdapter<String> adp = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, speechPlatformsList);
        spnQuickPlatform.setAdapter(adp);
    }

    private void addDynamicExpenseRowEntry(Context ctx, LinearLayout container, CustomExpense expenseData) {
        final LinearLayout rowLine = new LinearLayout(ctx);
        rowLine.setOrientation(LinearLayout.HORIZONTAL);
        rowLine.setPadding(0, 8, 0, 8);

        final EditText etNameInput = new EditText(ctx);
        etNameInput.setHint("Label (EMI)");
        etNameInput.setText(expenseData.name);
        etNameInput.setTextColor(0xFFFFFFFF);
        etNameInput.setHintTextColor(0xFFB0BEC5);
        etNameInput.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f));
        rowLine.addView(etNameInput);

        final EditText etAmtInput = new EditText(ctx);
        etAmtInput.setHint("₹");
        etAmtInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etAmtInput.setText(expenseData.amount <= 0 ? "" : String.valueOf(expenseData.amount));
        etAmtInput.setTextColor(0xFFFFFFFF);
        etAmtInput.setHintTextColor(0xFFB0BEC5);
        etAmtInput.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.9f));
        rowLine.addView(etAmtInput);

        final Spinner spnPeriodInput = new Spinner(ctx);
        String[] opts = {"Daily", "Monthly", "Yearly"};
        ArrayAdapter<String> adp = new ArrayAdapter<>(ctx, android.R.layout.simple_spinner_item, opts);
        spnPeriodInput.setAdapter(adp);
        for (int i = 0; i < opts.length; i++) {
            if (opts[i].equalsIgnoreCase(expenseData.period)) spnPeriodInput.setSelection(i);
        }
        spnPeriodInput.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.1f));
        rowLine.addView(spnPeriodInput);

        Button btnDel = new Button(ctx);
        btnDel.setText("X");
        btnDel.setTextColor(0xFFFF5252);
        btnDel.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        btnDel.setOnClickListener(v -> container.removeView(rowLine));
        rowLine.addView(btnDel);
        container.addView(rowLine);
    }

    // ==========================================
    // LOCAL CSV PERSISTENCE (SAFE FALLBACK)
    // ==========================================
    private File getStorageDirectory() {
        try {
            if (hasFullFileAccessPermission()) {
                File docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
                File appDir = new File(docsDir, "GarudaYaana");
                if (!appDir.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    appDir.mkdirs();
                }
                if (appDir.exists() && appDir.canWrite()) {
                    return appDir;
                }
            }
        } catch (Exception ignored) {}
        File extFallback = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (extFallback != null) {
            if (!extFallback.exists()) {
                //noinspection ResultOfMethodCallIgnored
                extFallback.mkdirs();
            }
            return extFallback;
        }
        return getFilesDir();
    }

    private void appendTripRecordToCSVFile(String appLabel, double fare) {
        if (regNumber.isEmpty()) return;
        File targetFile = new File(getStorageDirectory(), "garuda_yana_" + regNumber + ".csv");
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        String dataLine = "TRIP_RECORD," + timestamp + "," + appLabel + "," + fare + "," + (vehicleOdoBaseline + getTotalShiftKm()) + "," + getTotalShiftKm() + "\n";
        try (FileOutputStream fos = new FileOutputStream(targetFile, true)) {
            fos.write(dataLine.getBytes());
        } catch (Exception ignored) {}
    }

    private void recordIdleLocationToCSV(double latitude, double longitude, String categoryHeader) {
        if (regNumber.isEmpty()) return;
        File targetFile = new File(getStorageDirectory(), "garuda_yana_" + regNumber + ".csv");
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        String dataLine = categoryHeader + "," + timestamp + "," + latitude + "," + longitude + "," + (vehicleOdoBaseline + getTotalShiftKm()) + "\n";
        try (FileOutputStream fos = new FileOutputStream(targetFile, true)) {
            fos.write(dataLine.getBytes());
        } catch (Exception ignored) {}
    }

    private void dispatchShareMasterSheet() {
        File targetFile = new File(getStorageDirectory(), "garuda_yana_" + regNumber + ".csv");
        if (!targetFile.exists()) {
            Toast.makeText(this, "No CSV file found for " + regNumber, Toast.LENGTH_SHORT).show();
            return;
        }
        shareSpecificFile(targetFile);
    }

    private void shareSpecificFile(File targetFile) {
        try {
            Uri pathUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", targetFile);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/csv");
            shareIntent.putExtra(Intent.EXTRA_STREAM, pathUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Export GarudaYaana CSV Report"));
        } catch (Exception e) {
            Toast.makeText(this, "CSV stored at: " + targetFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
        }
    }

    private void saveAdvancedProfileToCSV() {
        if (regNumber.isEmpty()) return;
        File targetFile = new File(getStorageDirectory(), "garuda_yana_" + regNumber + ".csv");
        StringBuilder csv = new StringBuilder();
        csv.append("RegNumber,VehicleName,VehicleType,Ownership,FuelCap,FuelType,FuelRate,ExShowroom,VehicleOdoBaseline,MaxRangeKm,BalanceInHand,InsAmt,InsExp,TaxAmt,TaxExp,PolAmt,PolExp,DepPct,BatDate,FuelMarker,AppsList,SerializedExpenses,TyreCount,TyreUnitPrice,TyreLifeKm\n");
        csv.append(regNumber).append(",").append(vehicleName).append(",").append(vehicleType).append(",").append(ownership).append(",")
                .append(fuelCapacity).append(",").append(fuelType).append(",").append(fuelRatePerUnit).append(",").append(exShowroomPrice).append(",")
                .append(vehicleOdoBaseline).append(",").append(maxExpectedRangeKm).append(",").append(balanceInHandNow).append(",")
                .append(insuranceAmount).append(",").append(insuranceExpiry).append(",").append(roadTaxAmount).append(",").append(roadTaxExpiry).append(",")
                .append(pollutionAmount).append(",").append(pollutionExpiry).append(",").append(depreciationPercent).append(",").append(batteryInstallDate).append(",")
                .append(fuelResetOdoMarker).append(",");
        for (String node : speechPlatformsList) {
            csv.append(node).append(";");
        }
        csv.append(speechPlatformsList.isEmpty() ? "EMPTY," : ",");
        for (CustomExpense exp : expenseFleetList) {
            csv.append(exp.name).append("#").append(exp.amount).append("#").append(exp.period).append("|");
        }
        csv.append(expenseFleetList.isEmpty() ? "EMPTY," : ",");
        csv.append(tyreCount).append(",").append(tyreUnitPrice).append(",").append(tyreReplacementKm).append("\n");

        try (FileOutputStream fos = new FileOutputStream(targetFile, false)) {
            fos.write(csv.toString().getBytes());
        } catch (Exception ignored) {}
    }

    private void parseAdvancedVehicleCSV(File file) {
        try (Scanner scanner = new Scanner(file)) {
            if (scanner.hasNextLine()) scanner.nextLine();
            if (scanner.hasNextLine()) {
                String[] tokens = scanner.nextLine().split(",");
                if (tokens.length >= 21) {
                    regNumber = tokens[0].trim();
                    vehicleName = tokens[1].trim();
                    vehicleType = tokens[2].trim();
                    ownership = tokens[3].trim();
                    fuelCapacity = Double.parseDouble(tokens[4].trim());
                    fuelType = tokens[5].trim();
                    fuelRatePerUnit = Double.parseDouble(tokens[6].trim());
                    exShowroomPrice = Double.parseDouble(tokens[7].trim());
                    vehicleOdoBaseline = Double.parseDouble(tokens[8].trim());
                    maxExpectedRangeKm = Double.parseDouble(tokens[9].trim());
                    balanceInHandNow = Double.parseDouble(tokens[10].trim());
                    insuranceAmount = Double.parseDouble(tokens[11].trim());
                    insuranceExpiry = tokens[12].trim();
                    roadTaxAmount = Double.parseDouble(tokens[13].trim());
                    roadTaxExpiry = tokens[14].trim();
                    pollutionAmount = Double.parseDouble(tokens[15].trim());
                    pollutionExpiry = tokens[16].trim();
                    depreciationPercent = Integer.parseInt(tokens[17].trim());
                    batteryInstallDate = tokens[18].trim();
                    fuelResetOdoMarker = Double.parseDouble(tokens[19].trim());
                    speechPlatformsList.clear();
                    String rawApps = tokens[20].trim();
                    if (!rawApps.equals("EMPTY") && !rawApps.isEmpty()) {
                        for (String node : rawApps.split(";")) {
                            if (!node.trim().isEmpty()) speechPlatformsList.add(node.trim());
                        }
                    }
                    expenseFleetList.clear();
                    if (tokens.length >= 22) {
                        String rawExpenses = tokens[21].trim();
                        if (!rawExpenses.equals("EMPTY") && !rawExpenses.isEmpty()) {
                            for (String item : rawExpenses.split("\\|")) {
                                String[] sub = item.split("#");
                                if (sub.length >= 3) {
                                    expenseFleetList.add(new CustomExpense(sub[0], Double.parseDouble(sub[1]), sub[2]));
                                }
                            }
                        }
                    }
                    if (tokens.length >= 25) {
                        tyreCount = Integer.parseInt(tokens[22].trim());
                        tyreUnitPrice = Double.parseDouble(tokens[23].trim());
                        tyreReplacementKm = Double.parseDouble(tokens[24].trim());
                    }
                    shiftStartFuelValueRs = (fuelCapacity * fuelRatePerUnit) * (estimateCurrentFuelPercentage() / 100.0);
                    refreshQuickPlatformSpinner();
                    updateLiveDashboardMetrics();
                    refreshSystemButtonUI();
                    return;
                }
            }
        } catch (Exception ignored) {}
        regNumber = "";
        showFirstTimeSetupChoiceDialog();
    }

    // ==========================================
    // PERSISTENT SHIFT STORAGE & TELEMETRY
    // ==========================================
    private void persistShiftStateToStorage() {
        if (shiftPrefs == null) return;
        shiftPrefs.edit()
                .putBoolean("isAppStarted", isAppStarted)
                .putBoolean("isTripStarted", isTripStarted)
                .putString("accumulatedDistanceKm", String.valueOf(accumulatedDistanceKm))
                .putString("dryRunDistanceKm", String.valueOf(dryRunDistanceKm))
                .putString("dailyEarningsSum", String.valueOf(dailyEarningsSum))
                .putInt("dailyOrdersCount", dailyOrdersCount)
                .putString("dailySpotExpenses", String.valueOf(dailySpotExpenses))
                .putString("dailyNetIncomeGoal", String.valueOf(dailyNetIncomeGoal))
                .putString("shiftStartFuelValueRs", String.valueOf(shiftStartFuelValueRs))
                .putString("currentFuelPercent", String.valueOf(currentFuelPercent))
                .putString("vehicleOdoBaseline", String.valueOf(vehicleOdoBaseline))
                .putString("fuelResetOdoMarker", String.valueOf(fuelResetOdoMarker))
                .putLong("totalWaitingTimeMs", totalWaitingTimeMs)
                .putLong("tripStopTimestamp", tripStopTimestamp)
                .putString("terminalLog", tvTerminalLog != null ? tvTerminalLog.getText().toString() : "")
                .apply();
    }

    private void restoreShiftStateFromStorage() {
        if (shiftPrefs == null) return;
        boolean savedAppStarted = shiftPrefs.getBoolean("isAppStarted", false);
        if (!savedAppStarted) return;

        isAppStarted = true;
        isTripStarted = shiftPrefs.getBoolean("isTripStarted", false);
        accumulatedDistanceKm = parseDoubleSafe(shiftPrefs.getString("accumulatedDistanceKm", "0.0"), 0.0);
        dryRunDistanceKm = parseDoubleSafe(shiftPrefs.getString("dryRunDistanceKm", "0.0"), 0.0);
        dailyEarningsSum = parseDoubleSafe(shiftPrefs.getString("dailyEarningsSum", "0.0"), 0.0);
        dailyOrdersCount = shiftPrefs.getInt("dailyOrdersCount", 0);
        dailySpotExpenses = parseDoubleSafe(shiftPrefs.getString("dailySpotExpenses", "0.0"), 0.0);
        dailyNetIncomeGoal = parseDoubleSafe(shiftPrefs.getString("dailyNetIncomeGoal", "2000.0"), 2000.0);
        shiftStartFuelValueRs = parseDoubleSafe(shiftPrefs.getString("shiftStartFuelValueRs", "0.0"), 0.0);
        currentFuelPercent = parseDoubleSafe(shiftPrefs.getString("currentFuelPercent", "100.0"), 100.0);
        vehicleOdoBaseline = parseDoubleSafe(shiftPrefs.getString("vehicleOdoBaseline", String.valueOf(vehicleOdoBaseline)), vehicleOdoBaseline);
        fuelResetOdoMarker = parseDoubleSafe(shiftPrefs.getString("fuelResetOdoMarker", String.valueOf(fuelResetOdoMarker)), fuelResetOdoMarker);
        totalWaitingTimeMs = shiftPrefs.getLong("totalWaitingTimeMs", 0L);
        tripStopTimestamp = shiftPrefs.getLong("tripStopTimestamp", 0L);

        String savedLog = shiftPrefs.getString("terminalLog", "");
        if (savedLog != null && !savedLog.isEmpty() && tvTerminalLog != null) {
            tvTerminalLog.setText(savedLog);
        }
    }

    private void clearPersistedShiftState() {
        if (shiftPrefs == null) return;
        shiftPrefs.edit()
                .putBoolean("isAppStarted", false)
                .putBoolean("isTripStarted", false)
                .putString("accumulatedDistanceKm", "0.0")
                .putString("dryRunDistanceKm", "0.0")
                .putString("dailyEarningsSum", "0.0")
                .putInt("dailyOrdersCount", 0)
                .putString("dailySpotExpenses", "0.0")
                .putLong("totalWaitingTimeMs", 0L)
                .putLong("tripStopTimestamp", 0L)
                .apply();
    }

    private double parseDoubleSafe(String val, double fallback) {
        try {
            if (val == null || val.trim().isEmpty()) return fallback;
            return Double.parseDouble(val.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private void initializeSpeechRecognizerEngine() {
        try {
            if (SpeechRecognizer.isRecognitionAvailable(this)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            }
        } catch (Exception ignored) {}
        if (speechPlatformsList.isEmpty()) {
            speechPlatformsList.add("UBER");
            speechPlatformsList.add("RAPIDO");
            speechPlatformsList.add("KERALA SAVARI");
            speechPlatformsList.add("DIRECT");
        }
    }

    private void startTripSequence() {
        isTripStarted = true;
        if (tripStopTimestamp > 0) {
            totalWaitingTimeMs += (System.currentTimeMillis() - tripStopTimestamp);
            updateWaitingTimeDisplayField();
        }
        persistShiftStateToStorage();
        logToTerminal("TRIP START");
        refreshSystemButtonUI();
        resumeActiveTripListener();
    }

    private void resumeActiveTripListener() {
        try {
            if (locationManager != null && locationListener != null) {
                locationManager.removeUpdates(locationListener);
            }
            locationListener = new LocationListener() {
                @Override public void onLocationChanged(@NonNull Location location) { processLiveLocationTelemetry(location, true); }
                @Override public void onProviderDisabled(@NonNull String p) {}
                @Override public void onProviderEnabled(@NonNull String p) {}
            };
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2500, 5, locationListener);
            }
        } catch (Exception e) {
            logToTerminal("[ERR] GPS");
        }
    }

    private void stopGPSTrackingEngine() {
        isTripStarted = false;
        tripStopTimestamp = System.currentTimeMillis();
        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
        }
        persistShiftStateToStorage();
        logToTerminal("TRIP STOP");
        refreshSystemButtonUI();
        saveAdvancedProfileToCSV();
    }

    private void startIdlePassiveTrackingEngine() {
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.removeUpdates(passiveIdleListener);
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 4000, 5, passiveIdleListener);
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 8000, 10, passiveIdleListener);
            }
        } catch (Exception ignored) {}
    }

    private void stopIdlePassiveTrackingEngine() {
        if (locationManager != null) {
            locationManager.removeUpdates(passiveIdleListener);
        }
    }

    private final LocationListener passiveIdleListener = new LocationListener() {
        @Override public void onLocationChanged(@NonNull Location loc) { processLiveLocationTelemetry(loc, false); }
        @Override public void onProviderDisabled(@NonNull String p) {}
        @Override public void onProviderEnabled(@NonNull String p) {}
    };

    private void processLiveLocationTelemetry(Location location, boolean isInsideActiveTrip) {
        if (!isAppStarted) return;
        if (!isInsideActiveTrip && isTripStarted) return;

        double lat = location.getLatitude();
        double lon = location.getLongitude();
        String locStamp = String.format(Locale.US, "%.4f,%.4f", lat, lon);

        if (previousLocation != null) {
            double deltaMeters = location.distanceTo(previousLocation);
            double deltaKm = deltaMeters / 1000.0;

            if (deltaMeters >= 5.0 && deltaKm <= 25.0) {
                if (isInsideActiveTrip) {
                    accumulatedDistanceKm += deltaKm;
                } else {
                    dryRunDistanceKm += deltaKm;
                }
                previousLocation = location;
                updateLiveDashboardMetrics();
                persistShiftStateToStorage();
                logToTerminal((isInsideActiveTrip ? "TR:" : "DR:") + locStamp + " +" + String.format(Locale.US, "%.2f", deltaKm));
            }
        } else {
            previousLocation = location;
            logToTerminal("GPS:" + locStamp);
        }

        if (lastIdleRecordedLocation == null) {
            lastIdleRecordedLocation = location;
            lastMoveTimestamp = System.currentTimeMillis();
        } else {
            if (location.distanceTo(lastIdleRecordedLocation) > 12) {
                lastIdleRecordedLocation = location;
                lastMoveTimestamp = System.currentTimeMillis();
            } else {
                long idleDelta = System.currentTimeMillis() - lastMoveTimestamp;
                if (idleDelta >= 60000) {
                    totalWaitingTimeMs += idleDelta;
                    updateWaitingTimeDisplayField();
                    persistShiftStateToStorage();
                    recordIdleLocationToCSV(lat, lon, isInsideActiveTrip ? "DURING_TRIP_IDLE" : "WITHOUT_TRIP_IDLE");
                    logToTerminal("IDL:" + locStamp);
                    lastMoveTimestamp = System.currentTimeMillis();
                }
            }
        }
    }

    private void updateWaitingTimeDisplayField() {
        long min = (totalWaitingTimeMs / 1000) / 60;
        tvWaitTime.setText("Idle: " + (min / 60) + "H " + (min % 60) + "M");
    }

    private void refreshSystemButtonUI() {
        if (isAppStarted) {
            btnAppToggle.setText("App Stop");
            btnAppToggle.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFCC0000));
            btnTripToggle.setEnabled(true);
            btnTripToggle.setAlpha(1.0f);
        } else {
            btnAppToggle.setText("App Start");
            btnAppToggle.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF008800));
            btnTripToggle.setEnabled(false);
            btnTripToggle.setAlpha(0.4f);
        }
        if (isTripStarted) {
            btnTripToggle.setText("TRIP STOP");
            btnTripToggle.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFF3300));
        } else {
            btnTripToggle.setText("TRIP START");
            btnTripToggle.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFF8C00));
        }
        if (!regNumber.isEmpty()) {
            tvVehicleStatus.setText(vehicleName + " [" + regNumber + "] | Fix: ₹" + String.format(Locale.US, "%.0f/d", dailyStandingCost));
            tvVehicleStatus.setTextColor(isAppStarted ? 0xFF00E676 : 0xFFFF5252);
        }
    }

    private void logToTerminal(String message) {
        String timestamp = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        tvTerminalLog.append(timestamp + " " + message + "\n");
        svTerminal.post(() -> svTerminal.fullScroll(View.FOCUS_DOWN));
    }

    private EditText createDialogEditText(Context ctx, String labelText, String text, LinearLayout container) {
        TextView tvLabel = new TextView(ctx);
        tvLabel.setText(labelText);
        tvLabel.setTextColor(0xFFFFFFFF);
        tvLabel.setPadding(10, 12, 10, 2);
        container.addView(tvLabel);
        EditText et = new EditText(ctx);
        et.setText(text);
        et.setTextColor(0xFFFFFFFF);
        et.setHintTextColor(0xFFB0BEC5);
        container.addView(et);
        return et;
    }

    private void showDatePicker(DateCallback callback) {
        Calendar c = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, dayOfMonth) ->
                callback.onDateSelected(String.format(Locale.US, "%02d/%02d/%d", dayOfMonth, (month + 1), year)),
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    interface DateCallback {
        void onDateSelected(String date);
    }

    private void calculateRemainingWorkingDays() {
        LocalDate today = java.time.LocalDate.now();
        LocalDate endOfMonth = today.withDayOfMonth(today.lengthOfMonth());
        int workingDaysLeft = 0;
        LocalDate current = today;
        while (!current.isAfter(endOfMonth)) {
            if (current.getDayOfWeek() != DayOfWeek.SUNDAY) workingDaysLeft++;
            current = current.plusDays(1);
        }
        tvDaysLeft.setText("Days Left: " + workingDaysLeft);
    }

    @Override
    protected void onDestroy() {
        if (locationManager != null) {
            if (locationListener != null) locationManager.removeUpdates(locationListener);
            locationManager.removeUpdates(passiveIdleListener);
        }
        if (speechRecognizer != null) speechRecognizer.destroy();
        super.onDestroy();
    }
}