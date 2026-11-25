package com.example.garudayaana;

import android.Manifest;
import android.app.AlertDialog;
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
import android.view.ViewGroup;
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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_MASTER_CODE = 105;
    private static final String PREFS_NAME = "GarudaYaanaCabPrefsV3";
    private static final String TEMPLATE_FILE_NAME = "garuda_yana_TEMPLATE.csv";

    private static final String DISCLAIMER_TEXT =
            "DISCLAIMER: GarudaYaana is provided 'AS-IS' for informational and operational estimation only. " +
            "The developer/author assumes NO responsibility or liability for any financial loss, trip acceptance decisions, " +
            "app crashes, data loss, or mobile hardware/GPS issues. The driver/user bears sole responsibility for " +
            "safe driving, traffic compliance, and business decisions.";

    private TextView tvVehicleStatus, tvDaysLeft, tvMinReqAvg, tvCurrentAvg, tvOdoMeter, tvTotalDist, tvOrdersToday, tvWaitTime, tvDryRun, tvAvgFuelCons, tvAvailFuel, tvEarnings, tvTerminalLog, tvTrainData;
    private TextView tvLiveNetProfit, tvTargetGap, tvDailyGrossTarget, tvMonthlyGrossTarget, tvYearlyGrossTarget;
    private TextView tvPlatformFeasibility, tvFlashOfferGuide, tvFlashFareResult, tvRange1to3, tvRange4to6, tvRange7to10;
    private EditText etTotalRevenueInput, etFlashFareCheck;
    private Spinner spnQuickPlatform;
    private Button btnAppToggle, btnTripToggle, btnUpdateRevenue;
    private ScrollView svTerminal;

    private LocationManager locationManager;
    private LocationListener locationListener;
    private Location previousLocation = null;
    private SpeechRecognizer speechRecognizer;
    private SharedPreferences shiftPrefs;
    private AlertDialog activeDialog = null;

    private boolean isAppStarted = false;
    private boolean isTripStarted = false;
    private double accumulatedDistanceKm = 0.0;
    private double dailyEarningsSum = 0.0;
    private int dailyOrdersCount = 0;
    private double dryRunDistanceKm = 0.0;
    private double dailySpotExpenses = 0.0;

    private double dailyNetIncomeGoal = 2000.0;
    private double shiftStartFuelValueRs = 0.0;
    private double currentFuelPercent = 100.0;
    private double minOperatingExpenditure = 0.0;
    private double currentRatePerKm = 0.0;

    private long tripStopTimestamp = 0;
    private long totalWaitingTimeMs = 0;
    private long lastMoveTimestamp = 0;
    private Location lastIdleRecordedLocation = null;

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

    private int tyreCount = 5;
    private double tyreUnitPrice = 5500.0;
    private double tyreReplacementKm = 30000.0;

    private final ArrayList<String> speechPlatformsList = new ArrayList<>();
    private final ArrayList<CustomExpense> expenseFleetList = new ArrayList<>();
    private final HashMap<String, Double> platformRevenueMap = new HashMap<>();
    private final HashMap<String, Double> platformExpenseMap = new HashMap<>();
    private final HashMap<String, String> platformSubInfoMap = new HashMap<>();
    private double dailyStandingCost = 0.0;

    private ArrayAdapter<String> createWhiteSpinnerAdapter(List<String> items) {
        return new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                if (v instanceof TextView) {
                    ((TextView) v).setTextColor(0xFFFFFFFF);
                }
                return v;
            }

            @Override
            public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
                View v = super.getDropDownView(position, convertView, parent);
                if (v instanceof TextView) {
                    ((TextView) v).setTextColor(0xFFFFFFFF);
                    v.setBackgroundColor(0xFF1E1E1E);
                }
                return v;
            }
        };
    }

    private void fetchTrainData(String url) {
        Executors.newSingleThreadExecutor().execute(() -> {
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(url).build();
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String csvData = response.body().string();
                    runOnUiThread(() -> processCsvData(csvData));
                }
            } catch (IOException ignored) {}
        });
    }

    private void processCsvData(String data) {
        if (tvTrainData == null || data == null) return;
        tvTrainData.setTextColor(0xFFFFFFFF);
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

    private void dismissActiveDialogSafely() {
        try {
            if (activeDialog != null && activeDialog.isShowing()) {
                activeDialog.dismiss();
            }
        } catch (Exception ignored) {}
        activeDialog = null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        try {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } catch (Exception ignored) {}

        try {
            shiftPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        } catch (Exception ignored) {}

        mapViewsSafely();

        try {
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        } catch (Exception ignored) {}

        try {
            initializeSpeechRecognizerEngine();
        } catch (Exception ignored) {}

        try {
            calculateRemainingWorkingDaysSafe();
        } catch (Exception ignored) {}

        try {
            restoreShiftStateFromStorage();
        } catch (Exception ignored) {}

        try {
            refreshQuickPlatformSpinner();
        } catch (Exception ignored) {}

        try {
            ensureTemplateAndLoadVehicleProfile();
        } catch (Exception ignored) {
            setInMemoryDefaultProfile();
        }

        try {
            if (isAppStarted) {
                startIdlePassiveTrackingEngine();
                if (isTripStarted) {
                    resumeActiveTripListener();
                }
            }
            updateWaitingTimeDisplayField();
            updateLiveDashboardMetrics();
            refreshSystemButtonUI();
        } catch (Exception ignored) {}

        attachClickListenersSafely();

        View rootView = findViewById(android.R.id.content);
        if (rootView != null) {
            rootView.post(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    try {
                        triggerDynamicSystemPermissionsScan();
                    } catch (Exception ignored) {}
                }
            });
        }
    }

    private void mapViewsSafely() {
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
        btnTripToggle = findViewById(R.id.btnTripToggle);

        TextView[] whiteViews = {
                tvVehicleStatus, tvDaysLeft, tvDailyGrossTarget, tvMonthlyGrossTarget,
                tvYearlyGrossTarget, tvMinReqAvg, tvCurrentAvg, tvOdoMeter, tvTotalDist,
                tvOrdersToday, tvWaitTime, tvDryRun, tvAvgFuelCons, tvAvailFuel,
                tvEarnings, tvTerminalLog, tvTrainData, tvFlashOfferGuide, tvFlashFareResult,
                tvRange1to3, tvRange4to6, tvRange7to10
        };
        for (TextView tv : whiteViews) {
            if (tv != null) tv.setTextColor(0xFFFFFFFF);
        }
        if (etTotalRevenueInput != null) {
            etTotalRevenueInput.setTextColor(0xFFFFFFFF);
            etTotalRevenueInput.setHintTextColor(0xFFCFD8DC);
        }
        if (etFlashFareCheck != null) {
            etFlashFareCheck.setTextColor(0xFFFFFFFF);
            etFlashFareCheck.setHintTextColor(0xFFCFD8DC);
        }
    }

    private void attachClickListenersSafely() {
        Button btnExitApp = findViewById(R.id.btnExitApp);
        Button btnVehicleConfig = findViewById(R.id.btnVehicleConfig);
        Button btnSubPlan = findViewById(R.id.btnSubPlan);
        Button btnShareCSV = findViewById(R.id.btnShareCSV);
        Button btnTips = findViewById(R.id.btnTips);
        Button btnExpense = findViewById(R.id.btnExpense);

        Button[] allButtons = {btnExitApp, btnVehicleConfig, btnSubPlan, btnShareCSV, btnTips, btnExpense, btnUpdateRevenue, btnAppToggle, btnTripToggle};
        for (Button b : allButtons) {
            if (b != null) b.setTextColor(0xFFFFFFFF);
        }

        if (etFlashFareCheck != null) {
            etFlashFareCheck.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    updateFlashFareInstantResult();
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        if (btnAppToggle != null) {
            btnAppToggle.setOnClickListener(v -> {
                try {
                    if (regNumber.isEmpty()) {
                        setInMemoryDefaultProfile();
                    }
                    if (!isAppStarted) {
                        showShiftStartDialog();
                    } else {
                        if (isTripStarted) stopGPSTrackingEngine();
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
                } catch (Exception ignored) {}
            });
        }

        if (btnUpdateRevenue != null) {
            btnUpdateRevenue.setOnClickListener(v -> {
                try {
                    String rawRev = etTotalRevenueInput != null ? etTotalRevenueInput.getText().toString().trim() : "";
                    if (rawRev.isEmpty()) {
                        Toast.makeText(this, "Enter platform revenue collected!", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    double platformAmt = Double.parseDouble(rawRev);
                    String selectedPlatform = (spnQuickPlatform != null && spnQuickPlatform.getSelectedItem() != null)
                            ? spnQuickPlatform.getSelectedItem().toString() : "DIRECT";

                    platformRevenueMap.put(selectedPlatform, platformAmt);
                    double sum = 0.0;
                    for (double val : platformRevenueMap.values()) sum += val;
                    dailyEarningsSum = sum;
                    dailyOrdersCount++;
                    if (etTotalRevenueInput != null) etTotalRevenueInput.setText("");

                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null && etTotalRevenueInput != null) {
                        imm.hideSoftInputFromWindow(etTotalRevenueInput.getWindowToken(), 0);
                    }

                    updateLiveDashboardMetrics();
                    persistShiftStateToStorage();
                    appendTripRecordToCSVFile("REV_" + selectedPlatform, platformAmt);
                    logToTerminal(selectedPlatform + " Rev: ₹" + String.format(Locale.US, "%.0f", platformAmt));
                } catch (Exception e) {
                    Toast.makeText(this, "Invalid revenue amount.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnExitApp != null) {
            btnExitApp.setOnClickListener(v -> {
                dismissActiveDialogSafely();
                try {
                    saveAdvancedProfileToCSV();
                    persistShiftStateToStorage();
                } catch (Exception ignored) {}
                finishAffinity();
            });
        }

        if (btnTripToggle != null) {
            btnTripToggle.setOnClickListener(v -> {
                if (!isAppStarted) {
                    Toast.makeText(this, "Press App Start first!", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!isTripStarted) startTripSequence();
                else stopGPSTrackingEngine();
            });
        }

        if (btnSubPlan != null) btnSubPlan.setOnClickListener(v -> showSubscriptionPlanDialog());
        if (btnExpense != null) btnExpense.setOnClickListener(v -> showSpotOrHiddenExpenseDialog());
        if (btnVehicleConfig != null) btnVehicleConfig.setOnClickListener(v -> showCentralConfigLandingMenu());
        if (btnShareCSV != null) btnShareCSV.setOnClickListener(v -> dispatchShareMasterSheet());

        if (btnTips != null) {
            btnTips.setOnClickListener(v -> {
                dismissActiveDialogSafely();
                AlertDialog.Builder b = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
                b.setTitle("Add Tip / Bonus Received (₹)");
                final EditText etTip = new EditText(this);
                etTip.setHint("Tip Amount (₹)");
                etTip.setTextColor(0xFFFFFFFF);
                etTip.setHintTextColor(0xFFCFD8DC);
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
                b.setNegativeButton("Cancel", (d, w) -> d.dismiss());
                activeDialog = b.create();
                activeDialog.show();
            });
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            saveAdvancedProfileToCSV();
            persistShiftStateToStorage();
        } catch (Exception ignored) {}
    }

    private boolean hasFullFileAccessPermission() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return Environment.isExternalStorageManager();
            }
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return false;
        }
    }

    private void promptForAllFilesAccessPermission() {
        dismissActiveDialogSafely();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            AlertDialog.Builder b = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                    .setTitle("Allow File Management Permission")
                    .setMessage("To read, write, rename, backup, and delete vehicle CSV files in Documents/GarudaYaana, enable 'Allow access to manage all files' for GarudaYaana.")
                    .setPositiveButton("Open File Settings", (d, w) -> {
                        try {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                            intent.setData(Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        } catch (Exception e) {
                            try {
                                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                                startActivity(intent);
                            } catch (Exception ignored) {}
                        }
                    })
                    .setNegativeButton("Close", (d, w) -> d.dismiss());
            activeDialog = b.create();
            activeDialog.show();
        } else {
            Toast.makeText(this, "File storage access is already active.", Toast.LENGTH_SHORT).show();
        }
    }

    private void triggerDynamicSystemPermissionsScan() {
        try {
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
        } catch (Exception ignored) {}
    }

    private void ensureTemplateAndLoadVehicleProfile() {
        try {
            File dir = getStorageDirectory();
            File templateFile = new File(dir, TEMPLATE_FILE_NAME);
            if (!templateFile.exists() || templateFile.length() == 0) {
                writeDefaultTemplateCsv(templateFile);
            }

            File[] vehicleFiles = dir.listFiles((d, name) ->
                    name.startsWith("garuda_yana_")
                            && name.endsWith(".csv")
                            && !name.contains("_BACKUP_")
                            && !name.equals(TEMPLATE_FILE_NAME));

            if (vehicleFiles != null && vehicleFiles.length > 0) {
                String preferredReg = readPrefAsString("selectedVehicleReg", "").trim().toUpperCase();
                File chosen = vehicleFiles[0];
                if (!preferredReg.isEmpty()) {
                    for (File vf : vehicleFiles) {
                        if (vf.getName().equals("garuda_yana_" + preferredReg + ".csv")) {
                            chosen = vf;
                            break;
                        }
                    }
                }
                parseAdvancedVehicleCSV(chosen);
            } else if (templateFile.exists() && templateFile.length() > 0) {
                parseAdvancedVehicleCSV(templateFile);
            }
        } catch (Exception ignored) {}

        if (regNumber.isEmpty()) {
            setInMemoryDefaultProfile();
        }
    }

    private void writeDefaultTemplateCsv(File targetFile) {
        String header = "RegNumber,VehicleName,VehicleType,Ownership,FuelCap,FuelType,FuelRate,ExShowroom,VehicleOdoBaseline,MaxRangeKm,BalanceInHand,InsAmt,InsExp,TaxAmt,TaxExp,PolAmt,PolExp,DepPct,BatDate,FuelMarker,AppsList,SerializedExpenses,TyreCount,TyreUnitPrice,TyreLifeKm,DailyNetTarget\n";
        String defaultRow = "KL01DEMO01,Demo WagonR CNG,Cab-CNG,Commercial,9.0,CNG,92.0,750000.0,45000.0,210.0,5000.0,24000.0,31/12/2027,8000.0,31/12/2027,500.0,30/06/2027,10,01/01/2026,45000.0,UBER;RAPIDO;KERALA SAVARI;DIRECT,Cab EMI#14500.0#Monthly|Mobile & Data#600.0#Monthly|Routine Service#1500.0#Monthly,5,5500.0,30000.0,2000.0\n";
        try (FileOutputStream fos = new FileOutputStream(targetFile, false)) {
            fos.write((header + defaultRow).getBytes());
        } catch (Exception ignored) {}
    }

    private void setInMemoryDefaultProfile() {
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

        if (speechPlatformsList.isEmpty()) {
            speechPlatformsList.add("UBER");
            speechPlatformsList.add("RAPIDO");
            speechPlatformsList.add("KERALA SAVARI");
            speechPlatformsList.add("DIRECT");
        }

        if (shiftStartFuelValueRs <= 0) {
            shiftStartFuelValueRs = fuelCapacity * fuelRatePerUnit;
        }
    }

    private void applyDemoProfileSetup() {
        try {
            setInMemoryDefaultProfile();
            if (shiftPrefs != null) {
                shiftPrefs.edit().putString("selectedVehicleReg", regNumber).apply();
            }
            saveAdvancedProfileToCSV();
            refreshQuickPlatformSpinner();
            updateLiveDashboardMetrics();
            persistShiftStateToStorage();
            refreshSystemButtonUI();
            logToTerminal("DEMO LOADED: KL01DEMO01");
            Toast.makeText(this, "Demo Cab Profile Loaded!", Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {}
    }

    private void showSubscriptionPlanDialog() {
        dismissActiveDialogSafely();
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
        spnPlat.setAdapter(createWhiteSpinnerAdapter(speechPlatformsList));
        l.addView(spnPlat);

        final EditText etFee = createDialogEditText(this, "Subscription Plan Fee (₹):", "165", l);
        etFee.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);

        final EditText etNote = createDialogEditText(this, "Validity / Note:", "24H Pass", l);

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
                appendTripRecordToCSVFile("SUB_PLAN_" + plat, fee);
                logToTerminal("SUB [" + plat + "] ₹" + String.format(Locale.US, "%.0f", fee));
            } catch (Exception e) {
                Toast.makeText(this, "Check subscription fee input.", Toast.LENGTH_SHORT).show();
            }
        });
        b.setNegativeButton("Cancel", (d, w) -> d.dismiss());
        activeDialog = b.create();
        activeDialog.show();
    }

    private void showSpotOrHiddenExpenseDialog() {
        dismissActiveDialogSafely();
        AlertDialog.Builder b = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        b.setTitle("Add Expense / Hidden Platform Charge (₹)");

        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(35, 15, 35, 15);

        ArrayList<String> cats = new ArrayList<>(speechPlatformsList);
        cats.add("GENERAL / TOLL / TEA");
        final Spinner spnCat = new Spinner(this);
        spnCat.setAdapter(createWhiteSpinnerAdapter(cats));
        l.addView(spnCat);

        final EditText etLabel = createDialogEditText(this, "Expense Head (e.g. Toll, Tea):", "", l);
        final EditText etAmt = createDialogEditText(this, "Amount (₹):", "", l);
        etAmt.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);

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
                appendTripRecordToCSVFile("EXP_" + cat, amt);
            } catch (Exception ignored) {}
        });
        b.setNegativeButton("Cancel", (d, w) -> d.dismiss());
        activeDialog = b.create();
        activeDialog.show();
    }

    private void showShiftStartDialog() {
        dismissActiveDialogSafely();
        AlertDialog.Builder builder = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        builder.setTitle("Start Cab Shift: Odometer, Fuel & Target");
        builder.setCancelable(true);

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
                vehicleOdoBaseline = parseDoubleSafe(etStartOdo.getText().toString().trim(), vehicleOdoBaseline);
                currentFuelPercent = parseDoubleSafe(etFuelLevelPct.getText().toString().trim(), estimatedFuelPct);
                if (currentFuelPercent > 100.0) currentFuelPercent = 100.0;
                if (currentFuelPercent < 0.0) currentFuelPercent = 0.0;
                dailyNetIncomeGoal = parseDoubleSafe(etTargetProfit.getText().toString().trim(), dailyNetIncomeGoal);

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

                logToTerminal("SHIFT START | Odo:" + String.format(Locale.US, "%.0f", vehicleOdoBaseline));
            } catch (Exception e) {
                Toast.makeText(this, "Please check numeric inputs.", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        activeDialog = builder.create();
        activeDialog.show();
    }

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
        try {
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

            if (tvDailyGrossTarget != null) {
                tvDailyGrossTarget.setTextColor(0xFFFFFFFF);
                tvDailyGrossTarget.setText("Daily Tgt: ₹" + String.format(Locale.US, "%.0f", dailyGrossTarget));
            }
            if (tvMonthlyGrossTarget != null) {
                tvMonthlyGrossTarget.setTextColor(0xFFFFFFFF);
                tvMonthlyGrossTarget.setText("Monthly: ₹" + String.format(Locale.US, "%.0f", monthlyGrossTarget));
            }
            if (tvYearlyGrossTarget != null) {
                tvYearlyGrossTarget.setTextColor(0xFFFFFFFF);
                tvYearlyGrossTarget.setText("Yearly: ₹" + String.format(Locale.US, "%.0f", yearlyGrossTarget));
            }

            currentRatePerKm = (totalShiftKm > 0.05) ? (dailyEarningsSum / totalShiftKm) : getMinBreakEvenRatePerKm();

            if (tvLiveNetProfit != null) {
                tvLiveNetProfit.setText("Net Profit: ₹" + String.format(Locale.US, "%.0f", liveNetProfit));
                tvLiveNetProfit.setTextColor(liveNetProfit >= 0 ? 0xFF00E676 : 0xFFFF5252);
            }

            if (tvTargetGap != null) {
                if (targetGap > 0) {
                    tvTargetGap.setText("Target Gap: ₹" + String.format(Locale.US, "%.0f", targetGap));
                    tvTargetGap.setTextColor(0xFFFFAB40);
                } else {
                    tvTargetGap.setText("Target Hit: +₹" + String.format(Locale.US, "%.0f", Math.abs(targetGap)));
                    tvTargetGap.setTextColor(0xFF00E676);
                }
            }

            if (tvMinReqAvg != null) {
                tvMinReqAvg.setTextColor(0xFFFFFFFF);
                tvMinReqAvg.setText("Min Op Exp: ₹" + String.format(Locale.US, "%.0f", minOperatingExpenditure));
            }
            if (tvCurrentAvg != null) {
                tvCurrentAvg.setTextColor(0xFFFFFFFF);
                tvCurrentAvg.setText("Rate/km: ₹" + String.format(Locale.US, "%.1f/km", currentRatePerKm));
            }

            if (tvOdoMeter != null) {
                tvOdoMeter.setTextColor(0xFFFFFFFF);
                tvOdoMeter.setText("Odo: " + String.format(Locale.US, "%.0f", vehicleOdoBaseline + totalShiftKm));
            }
            if (tvTotalDist != null) {
                tvTotalDist.setTextColor(0xFFFFFFFF);
                tvTotalDist.setText("Shift Km: " + String.format(Locale.US, "%.1f", totalShiftKm));
            }
            if (tvDryRun != null) {
                tvDryRun.setTextColor(0xFFFFFFFF);
                tvDryRun.setText("Dry Km: " + String.format(Locale.US, "%.1f", dryRunDistanceKm));
            }
            if (tvOrdersToday != null) {
                tvOrdersToday.setTextColor(0xFFFFFFFF);
                tvOrdersToday.setText("Updates: " + dailyOrdersCount);
            }
            if (tvEarnings != null) {
                tvEarnings.setTextColor(0xFFFFFFFF);
                tvEarnings.setText("Total Rev: ₹" + String.format(Locale.US, "%.0f", dailyEarningsSum));
            }
            if (tvAvgFuelCons != null) {
                tvAvgFuelCons.setTextColor(0xFFFFFFFF);
                tvAvgFuelCons.setText("Run (F+T): ₹" + String.format(Locale.US, "%.1f/km", totalRunCostPerKm));
            }
            if (tvAvailFuel != null) {
                tvAvailFuel.setTextColor(0xFFFFFFFF);
                tvAvailFuel.setText("Rem Fuel Val: ₹" + String.format(Locale.US, "%.0f", remainingFuelValueRs));
            }

            updatePlatformFeasibilityBanner(totalShiftKm, totalRunCostPerKm);
            updateFeasibilityRangeChart();
            updateFlashFareInstantResult();
        } catch (Exception ignored) {}
    }

    private void updatePlatformFeasibilityBanner(double totalShiftKm, double totalRunCostPerKm) {
        if (tvPlatformFeasibility == null) return;
        if (platformRevenueMap.isEmpty() && platformExpenseMap.isEmpty()) {
            tvPlatformFeasibility.setText("PLATFORM FEASIBILITY: Add Subscription or Revenue Update");
            tvPlatformFeasibility.setTextColor(0xFFFFFFFF);
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

        tvPlatformFeasibility.setTextColor(0xFFFFFFFF);
        tvPlatformFeasibility.setText(sb.length() == 0 ? "PLATFORM FEASIBILITY: Ready" : sb.toString());
    }

    private double getMinBreakEvenRatePerKm() {
        calculateMathematicalDailyStandingCost30Day();
        double totalRunCostPerKm = getTotalPerKmWearAndFuelCost();
        double standingPerKm = dailyStandingCost / 120.0;
        double rate = totalRunCostPerKm + standingPerKm;
        return rate > 0.1 ? rate : 12.0;
    }

    private void updateFeasibilityRangeChart() {
        double minBreakEvenPerKm = getMinBreakEvenRatePerKm();
        double effectivePerKm = (getTotalShiftKm() > 0.05 && dailyEarningsSum > 0)
                ? (dailyEarningsSum / getTotalShiftKm())
                : minBreakEvenPerKm;

        double maxKm150 = 150.0 / minBreakEvenPerKm;
        double maxKm280 = 280.0 / minBreakEvenPerKm;
        double maxKm480 = 480.0 / minBreakEvenPerKm;
        double maxKm500 = 500.0 / minBreakEvenPerKm;

        if (tvFlashOfferGuide != null) {
            tvFlashOfferGuide.setTextColor(0xFFFFFFFF);
            tvFlashOfferGuide.setText(String.format(Locale.US,
                    "RATE: ₹%.1f/km | MAX KM -> ₹150: %.1fkm | ₹280: %.1fkm | ₹480: %.1fkm | ₹500: %.1fkm",
                    minBreakEvenPerKm, maxKm150, maxKm280, maxKm480, maxKm500));
        }

        double min1to3 = Math.max(1.0, Math.ceil(1.0 * minBreakEvenPerKm));
        double max1to3 = Math.max(min1to3 + 1.0, Math.ceil(3.0 * minBreakEvenPerKm));
        double cur1to3Low = Math.max(1.0, Math.round(1.0 * effectivePerKm));
        double cur1to3High = Math.max(cur1to3Low + 1.0, Math.round(3.0 * effectivePerKm));

        double min4to6 = Math.max(1.0, Math.ceil(4.0 * minBreakEvenPerKm));
        double max4to6 = Math.max(min4to6 + 1.0, Math.ceil(6.0 * minBreakEvenPerKm));
        double cur4to6Low = Math.max(1.0, Math.round(4.0 * effectivePerKm));
        double cur4to6High = Math.max(cur4to6Low + 1.0, Math.round(6.0 * effectivePerKm));

        double min7to10 = Math.max(1.0, Math.ceil(7.0 * minBreakEvenPerKm));
        double max7to10 = Math.max(min7to10 + 1.0, Math.ceil(10.0 * minBreakEvenPerKm));
        double cur7to10Low = Math.max(1.0, Math.round(7.0 * effectivePerKm));
        double cur7to10High = Math.max(cur7to10Low + 1.0, Math.round(10.0 * effectivePerKm));

        if (tvRange1to3 != null) {
            tvRange1to3.setTextColor(0xFFFFFFFF);
            tvRange1to3.setText(String.format(Locale.US, "1-3 km (₹%.1f/km)\nMin: ₹%.0f–%.0f | Est: ₹%.0f–%.0f",
                    minBreakEvenPerKm, min1to3, max1to3, cur1to3Low, cur1to3High));
        }
        if (tvRange4to6 != null) {
            tvRange4to6.setTextColor(0xFFFFFFFF);
            tvRange4to6.setText(String.format(Locale.US, "4-6 km (₹%.1f/km)\nMin: ₹%.0f–%.0f | Est: ₹%.0f–%.0f",
                    minBreakEvenPerKm, min4to6, max4to6, cur4to6Low, cur4to6High));
        }
        if (tvRange7to10 != null) {
            tvRange7to10.setTextColor(0xFFFFFFFF);
            tvRange7to10.setText(String.format(Locale.US, "7-10 km (₹%.1f/km)\nMin: ₹%.0f–%.0f | Est: ₹%.0f–%.0f",
                    minBreakEvenPerKm, min7to10, max7to10, cur7to10Low, cur7to10High));
        }
    }

    private void updateFlashFareInstantResult() {
        if (etFlashFareCheck == null || tvFlashFareResult == null) return;
        tvFlashFareResult.setTextColor(0xFFFFFFFF);
        String raw = etFlashFareCheck.getText().toString().trim();
        if (raw.isEmpty()) {
            tvFlashFareResult.setText("INCLUDING PICKUP & DROP: Max -- km");
            return;
        }
        try {
            double offerFare = Double.parseDouble(raw);
            double minRate = getMinBreakEvenRatePerKm();
            double maxSafeKm = offerFare / minRate;
            tvFlashFareResult.setText(String.format(Locale.US, "INCLUDING PICKUP & DROP: Max %.1f km (@₹%.1f/km)", maxSafeKm, minRate));
        } catch (Exception e) {
            tvFlashFareResult.setText("INCLUDING PICKUP & DROP: Max -- km");
        }
    }

    private void showCentralConfigLandingMenu() {
        dismissActiveDialogSafely();
        AlertDialog.Builder builder = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        builder.setTitle("GarudaYaana: Configurations Hub");

        ScrollView scroll = new ScrollView(this);
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(40, 25, 40, 25);
        scroll.addView(mainLayout);

        addConfigButton(mainLayout, "1. Vehicle, Tyres & Refill", 0xFF006064, v -> showVehicleManagerWizard(false));
        addConfigButton(mainLayout, "2. Fixed Monthly/Daily Expenses & EMIs", 0xFF388E3C, v -> showExpensesManagerWizard());
        addConfigButton(mainLayout, "3. Manage Platforms (Uber, Rapido...)", 0xFFE65100, v -> showPlatformsManagerWizard());
        addConfigButton(mainLayout, "4. CSV File Manager (Load / Backup / Rename / Del)", 0xFF4527A0, v -> showCsvFileManagerDialog());
        addConfigButton(mainLayout, hasFullFileAccessPermission() ? "5. File Storage Permission: GRANTED" : "5. Grant File Storage Permission (All Files)",
                hasFullFileAccessPermission() ? 0xFF2E7D32 : 0xFFD84315, v -> promptForAllFilesAccessPermission());
        addConfigButton(mainLayout, "6. Use Demo Vehicle Setup", 0xFF283593, v -> {
            dismissActiveDialogSafely();
            applyDemoProfileSetup();
        });
        addConfigButton(mainLayout, "7. Full Reset (Delete All CSV & Shift Data)", 0xFFB71C1C, v -> confirmAndExecuteFullReset());
        addConfigButton(mainLayout, "8. View Terms & Disclaimer", 0xFF424242, v -> {
            dismissActiveDialogSafely();
            activeDialog = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                    .setTitle("Terms & Disclaimer")
                    .setMessage(DISCLAIMER_TEXT)
                    .setPositiveButton("OK", (d, w) -> d.dismiss())
                    .create();
            activeDialog.show();
        });

        builder.setView(scroll);
        builder.setNegativeButton("Close", (dialog, which) -> dialog.dismiss());
        activeDialog = builder.create();
        activeDialog.show();
    }

    private void addConfigButton(LinearLayout parent, String text, int colorHex, View.OnClickListener listener) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(colorHex));
        btn.setTextColor(0xFFFFFFFF);
        btn.setOnClickListener(listener);
        parent.addView(btn);
    }

    private void showCsvFileManagerDialog() {
        dismissActiveDialogSafely();
        File dir = getStorageDirectory();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".csv"));
        if (files == null || files.length == 0) {
            Toast.makeText(this, "No CSV files found.", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[files.length];
        for (int i = 0; i < files.length; i++) names[i] = files[i].getName();

        activeDialog = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Select CSV File (" + dir.getName() + ")")
                .setItems(names, (d, idx) -> showSingleCsvActionDialog(files[idx]))
                .setNegativeButton("Close", (d, w) -> d.dismiss())
                .create();
        activeDialog.show();
    }

    private void showSingleCsvActionDialog(final File targetFile) {
        dismissActiveDialogSafely();
        String[] actions = {"Load Vehicle From This CSV", "Backup CSV", "Rename CSV File", "Delete CSV File", "Share / Export CSV"};
        activeDialog = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(targetFile.getName())
                .setItems(actions, (d, which) -> {
                    if (which == 0) {
                        parseAdvancedVehicleCSV(targetFile);
                        Toast.makeText(this, "Loaded profile: " + regNumber, Toast.LENGTH_SHORT).show();
                    } else if (which == 1) {
                        backupCsvFile(targetFile);
                    } else if (which == 2) {
                        renameCsvFilePrompt(targetFile);
                    } else if (which == 3) {
                        deleteCsvFilePrompt(targetFile);
                    } else if (which == 4) {
                        shareSpecificFile(targetFile);
                    }
                })
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .create();
        activeDialog.show();
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
                while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
            }
            logToTerminal("BACKUP: " + backupFile.getName());
            Toast.makeText(this, "Backup created: " + backupFile.getName(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Backup failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void renameCsvFilePrompt(File sourceFile) {
        dismissActiveDialogSafely();
        final EditText etNewName = new EditText(this);
        etNewName.setText(sourceFile.getName().replace(".csv", ""));
        etNewName.setTextColor(0xFFFFFFFF);
        activeDialog = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Rename CSV File")
                .setView(etNewName)
                .setPositiveButton("Rename", (d, w) -> {
                    String clean = etNewName.getText().toString().trim().replaceAll("[^A-Za-z0-9_-]", "");
                    if (clean.isEmpty()) return;
                    File destFile = new File(sourceFile.getParentFile(), clean + ".csv");
                    if (sourceFile.renameTo(destFile)) {
                        if (clean.startsWith("garuda_yana_") && !clean.equals("garuda_yana_TEMPLATE")) {
                            regNumber = clean.replace("garuda_yana_", "").toUpperCase();
                            if (shiftPrefs != null) shiftPrefs.edit().putString("selectedVehicleReg", regNumber).apply();
                            refreshSystemButtonUI();
                        }
                        Toast.makeText(this, "Renamed to " + destFile.getName(), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .create();
        activeDialog.show();
    }

    private void deleteCsvFilePrompt(File targetFile) {
        dismissActiveDialogSafely();
        activeDialog = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Delete File?")
                .setMessage("Permanently delete " + targetFile.getName() + "?")
                .setPositiveButton("Delete", (d, w) -> {
                    if (targetFile.delete()) {
                        Toast.makeText(this, "Deleted " + targetFile.getName(), Toast.LENGTH_SHORT).show();
                        if (targetFile.getName().equals("garuda_yana_" + regNumber + ".csv")) {
                            regNumber = "";
                            vehicleName = "";
                            ensureTemplateAndLoadVehicleProfile();
                        }
                    }
                })
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .create();
        activeDialog.show();
    }

    private void confirmAndExecuteFullReset() {
        dismissActiveDialogSafely();
        activeDialog = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Confirm Full Reset")
                .setMessage("Delete all CSV files and reset shift metrics?")
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
                    ensureTemplateAndLoadVehicleProfile();
                })
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .create();
        activeDialog.show();
    }

    private void showVehicleManagerWizard(final boolean forceNewRegistration) {
        dismissActiveDialogSafely();
        AlertDialog.Builder builder = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        builder.setTitle(forceNewRegistration ? "Register Cab / Vehicle" : "Vehicle, Tyres & Refill");
        builder.setCancelable(true);

        ScrollView scrollWrapper = new ScrollView(this);
        LinearLayout mainContainer = new LinearLayout(this);
        mainContainer.setOrientation(LinearLayout.VERTICAL);
        mainContainer.setPadding(40, 25, 40, 25);
        scrollWrapper.addView(mainContainer);

        final EditText etReg = createDialogEditText(this, "Registration Number:", forceNewRegistration ? "" : regNumber, mainContainer);
        final EditText etName = createDialogEditText(this, "Vehicle Name:", forceNewRegistration ? "" : vehicleName, mainContainer);
        final EditText etOdoBase = createDialogEditText(this, "Current Odometer Base (km):", String.format(Locale.US, "%.0f", vehicleOdoBaseline), mainContainer);
        final EditText etMaxRange = createDialogEditText(this, "Expected Range per Full Tank (km):", String.valueOf(maxExpectedRangeKm), mainContainer);
        final EditText etFuelCap = createDialogEditText(this, "Fuel/CNG Tank Capacity:", String.valueOf(fuelCapacity), mainContainer);
        final EditText etFuelRate = createDialogEditText(this, "Fuel/CNG Rate per Unit (₹):", String.valueOf(fuelRatePerUnit), mainContainer);
        final EditText etTyreCount = createDialogEditText(this, "Total Tyres:", String.valueOf(tyreCount), mainContainer);
        final EditText etTyrePrice = createDialogEditText(this, "Price per Tyre (₹):", String.valueOf(tyreUnitPrice), mainContainer);
        final EditText etTyreLifeKm = createDialogEditText(this, "Tyre Replacement Life (km):", String.valueOf(tyreReplacementKm), mainContainer);
        final EditText etDailyGoal = createDialogEditText(this, "Daily Net Income Goal (₹):", String.format(Locale.US, "%.0f", dailyNetIncomeGoal), mainContainer);
        final EditText etExShowroom = createDialogEditText(this, "Ex-Showroom Price (₹):", String.valueOf(exShowroomPrice), mainContainer);
        final EditText etInsurance = createDialogEditText(this, "Annual Insurance (₹):", String.valueOf(insuranceAmount), mainContainer);
        final EditText etRoadTax = createDialogEditText(this, "Annual Road Tax (₹):", String.valueOf(roadTaxAmount), mainContainer);
        final EditText etPollution = createDialogEditText(this, "Annual Pollution (₹):", String.valueOf(pollutionAmount), mainContainer);
        final EditText etDep = createDialogEditText(this, "Annual Depreciation (%):", String.valueOf(depreciationPercent), mainContainer);

        builder.setView(scrollWrapper);
        builder.setPositiveButton("Save Profile", (dialog, which) -> {
            String inputReg = etReg.getText().toString().trim().toUpperCase().replaceAll("[^A-Z0-9_-]", "");
            String inputName = etName.getText().toString().trim();
            if (inputReg.isEmpty()) inputReg = "KL01CAB01";
            if (inputName.isEmpty()) inputName = "Cab Vehicle";

            if (forceNewRegistration) expenseFleetList.clear();
            regNumber = inputReg;
            vehicleName = inputName;
            vehicleOdoBaseline = parseDoubleSafe(etOdoBase.getText().toString().trim(), vehicleOdoBaseline);
            maxExpectedRangeKm = parseDoubleSafe(etMaxRange.getText().toString().trim(), maxExpectedRangeKm);
            fuelCapacity = parseDoubleSafe(etFuelCap.getText().toString().trim(), fuelCapacity);
            fuelRatePerUnit = parseDoubleSafe(etFuelRate.getText().toString().trim(), fuelRatePerUnit);
            tyreCount = parseIntSafe(etTyreCount.getText().toString().trim(), tyreCount);
            tyreUnitPrice = parseDoubleSafe(etTyrePrice.getText().toString().trim(), tyreUnitPrice);
            tyreReplacementKm = parseDoubleSafe(etTyreLifeKm.getText().toString().trim(), tyreReplacementKm);
            dailyNetIncomeGoal = parseDoubleSafe(etDailyGoal.getText().toString().trim(), dailyNetIncomeGoal);
            exShowroomPrice = parseDoubleSafe(etExShowroom.getText().toString().trim(), exShowroomPrice);
            insuranceAmount = parseDoubleSafe(etInsurance.getText().toString().trim(), insuranceAmount);
            roadTaxAmount = parseDoubleSafe(etRoadTax.getText().toString().trim(), roadTaxAmount);
            pollutionAmount = parseDoubleSafe(etPollution.getText().toString().trim(), pollutionAmount);
            depreciationPercent = parseIntSafe(etDep.getText().toString().trim(), depreciationPercent);

            if (shiftPrefs != null) shiftPrefs.edit().putString("selectedVehicleReg", regNumber).apply();
            shiftStartFuelValueRs = (fuelCapacity * fuelRatePerUnit) * (estimateCurrentFuelPercentage() / 100.0);
            updateLiveDashboardMetrics();
            persistShiftStateToStorage();
            saveAdvancedProfileToCSV();
            refreshSystemButtonUI();
            Toast.makeText(this, "Saved: " + regNumber, Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        activeDialog = builder.create();
        activeDialog.show();
    }

    private void showExpensesManagerWizard() {
        dismissActiveDialogSafely();
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
            ArrayList<CustomExpense> bufferList = new ArrayList<>();
            for (int i = 0; i < expenseContainerLayout.getChildCount(); i++) {
                LinearLayout row = (LinearLayout) expenseContainerLayout.getChildAt(i);
                String n = ((EditText) row.getChildAt(0)).getText().toString().trim();
                String aStr = ((EditText) row.getChildAt(1)).getText().toString().trim();
                String p = ((Spinner) row.getChildAt(2)).getSelectedItem().toString();
                if (!n.isEmpty() && !aStr.isEmpty()) {
                    bufferList.add(new CustomExpense(n, parseDoubleSafe(aStr, 0.0), p));
                }
            }
            expenseFleetList.clear();
            expenseFleetList.addAll(bufferList);
            updateLiveDashboardMetrics();
            persistShiftStateToStorage();
            saveAdvancedProfileToCSV();
            refreshSystemButtonUI();
        });
        builder.setNegativeButton("Close", (dialog, which) -> dialog.dismiss());
        activeDialog = builder.create();
        activeDialog.show();
    }

    private void showPlatformsManagerWizard() {
        dismissActiveDialogSafely();
        AlertDialog.Builder builder = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        builder.setTitle("Manage Ride-Hailing Platforms");

        ScrollView scrollWrapper = new ScrollView(this);
        LinearLayout mainContainer = new LinearLayout(this);
        mainContainer.setOrientation(LinearLayout.VERTICAL);
        mainContainer.setPadding(40, 25, 40, 25);
        scrollWrapper.addView(mainContainer);

        final EditText etNewAppInput = createDialogEditText(this, "Add Platform (e.g. UBER):", "", mainContainer);
        Button btnAddApp = new Button(this);
        btnAddApp.setText("+ Insert New Platform");
        btnAddApp.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF006064));
        btnAddApp.setTextColor(0xFFFFFFFF);
        btnAddApp.setOnClickListener(v -> {
            String appLabel = etNewAppInput.getText().toString().trim().toUpperCase();
            if (!appLabel.isEmpty() && !speechPlatformsList.contains(appLabel)) {
                speechPlatformsList.add(appLabel);
                saveAdvancedProfileToCSV();
                refreshQuickPlatformSpinner();
                etNewAppInput.setText("");
                Toast.makeText(this, "Added " + appLabel, Toast.LENGTH_SHORT).show();
            }
        });
        mainContainer.addView(btnAddApp);

        builder.setView(scrollWrapper);
        builder.setPositiveButton("Done", (dialog, which) -> dialog.dismiss());
        activeDialog = builder.create();
        activeDialog.show();
    }

    private void refreshQuickPlatformSpinner() {
        if (spnQuickPlatform == null) return;
        spnQuickPlatform.setAdapter(createWhiteSpinnerAdapter(speechPlatformsList));
    }

    private void addDynamicExpenseRowEntry(Context ctx, LinearLayout container, CustomExpense expenseData) {
        final LinearLayout rowLine = new LinearLayout(ctx);
        rowLine.setOrientation(LinearLayout.HORIZONTAL);
        rowLine.setPadding(0, 8, 0, 8);

        final EditText etNameInput = new EditText(ctx);
        etNameInput.setHint("Label (EMI)");
        etNameInput.setText(expenseData.name);
        etNameInput.setTextColor(0xFFFFFFFF);
        etNameInput.setHintTextColor(0xFFCFD8DC);
        etNameInput.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f));
        rowLine.addView(etNameInput);

        final EditText etAmtInput = new EditText(ctx);
        etAmtInput.setHint("₹");
        etAmtInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etAmtInput.setText(expenseData.amount <= 0 ? "" : String.valueOf(expenseData.amount));
        etAmtInput.setTextColor(0xFFFFFFFF);
        etAmtInput.setHintTextColor(0xFFCFD8DC);
        etAmtInput.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.9f));
        rowLine.addView(etAmtInput);

        final Spinner spnPeriodInput = new Spinner(ctx);
        ArrayList<String> opts = new ArrayList<>();
        opts.add("Daily");
        opts.add("Monthly");
        opts.add("Yearly");
        spnPeriodInput.setAdapter(createWhiteSpinnerAdapter(opts));
        for (int i = 0; i < opts.size(); i++) {
            if (opts.get(i).equalsIgnoreCase(expenseData.period)) spnPeriodInput.setSelection(i);
        }
        spnPeriodInput.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.1f));
        rowLine.addView(spnPeriodInput);

        container.addView(rowLine);
    }

    private File getStorageDirectory() {
        try {
            if (hasFullFileAccessPermission()) {
                File docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
                File appDir = new File(docsDir, "GarudaYaana");
                if (!appDir.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    appDir.mkdirs();
                }
                if (appDir.exists() && appDir.canWrite()) return appDir;
            }
        } catch (Exception ignored) {}
        File extFallback = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (extFallback != null) {
            if (!extFallback.exists()) {
                //noinspection ResultOfMethodCallIgnored
                extFallback.mkdirs();
            }
            if (extFallback.exists() && extFallback.canWrite()) return extFallback;
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
            Toast.makeText(this, "No CSV file found.", Toast.LENGTH_SHORT).show();
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
        csv.append("RegNumber,VehicleName,VehicleType,Ownership,FuelCap,FuelType,FuelRate,ExShowroom,VehicleOdoBaseline,MaxRangeKm,BalanceInHand,InsAmt,InsExp,TaxAmt,TaxExp,PolAmt,PolExp,DepPct,BatDate,FuelMarker,AppsList,SerializedExpenses,TyreCount,TyreUnitPrice,TyreLifeKm,DailyNetTarget\n");
        csv.append(regNumber).append(",").append(vehicleName).append(",").append(vehicleType).append(",").append(ownership).append(",")
                .append(fuelCapacity).append(",").append(fuelType).append(",").append(fuelRatePerUnit).append(",").append(exShowroomPrice).append(",")
                .append(vehicleOdoBaseline).append(",").append(maxExpectedRangeKm).append(",").append(balanceInHandNow).append(",")
                .append(insuranceAmount).append(",").append(insuranceExpiry).append(",").append(roadTaxAmount).append(",").append(roadTaxExpiry).append(",")
                .append(pollutionAmount).append(",").append(pollutionExpiry).append(",").append(depreciationPercent).append(",").append(batteryInstallDate).append(",")
                .append(fuelResetOdoMarker).append(",");
        for (String node : speechPlatformsList) csv.append(node).append(";");
        csv.append(speechPlatformsList.isEmpty() ? "EMPTY," : ",");
        for (CustomExpense exp : expenseFleetList) csv.append(exp.name).append("#").append(exp.amount).append("#").append(exp.period).append("|");
        csv.append(expenseFleetList.isEmpty() ? "EMPTY," : ",");
        csv.append(tyreCount).append(",").append(tyreUnitPrice).append(",").append(tyreReplacementKm).append(",")
                .append(dailyNetIncomeGoal).append("\n");

        try (FileOutputStream fos = new FileOutputStream(targetFile, false)) {
            fos.write(csv.toString().getBytes());
        } catch (Exception ignored) {}
    }

    private void parseAdvancedVehicleCSV(File file) {
        if (file == null || !file.exists()) return;
        try (Scanner scanner = new Scanner(file)) {
            if (scanner.hasNextLine()) scanner.nextLine();
            if (scanner.hasNextLine()) {
                String[] tokens = scanner.nextLine().split(",");
                if (tokens.length >= 21) {
                    regNumber = tokens[0].trim();
                    vehicleName = tokens[1].trim();
                    vehicleType = tokens[2].trim();
                    ownership = tokens[3].trim();
                    fuelCapacity = parseDoubleSafe(tokens[4].trim(), fuelCapacity);
                    fuelType = tokens[5].trim();
                    fuelRatePerUnit = parseDoubleSafe(tokens[6].trim(), fuelRatePerUnit);
                    exShowroomPrice = parseDoubleSafe(tokens[7].trim(), exShowroomPrice);
                    vehicleOdoBaseline = parseDoubleSafe(tokens[8].trim(), vehicleOdoBaseline);
                    maxExpectedRangeKm = parseDoubleSafe(tokens[9].trim(), maxExpectedRangeKm);
                    balanceInHandNow = parseDoubleSafe(tokens[10].trim(), balanceInHandNow);
                    insuranceAmount = parseDoubleSafe(tokens[11].trim(), insuranceAmount);
                    insuranceExpiry = tokens[12].trim();
                    roadTaxAmount = parseDoubleSafe(tokens[13].trim(), roadTaxAmount);
                    roadTaxExpiry = tokens[14].trim();
                    pollutionAmount = parseDoubleSafe(tokens[15].trim(), pollutionAmount);
                    pollutionExpiry = tokens[16].trim();
                    depreciationPercent = parseIntSafe(tokens[17].trim(), depreciationPercent);
                    batteryInstallDate = tokens[18].trim();
                    fuelResetOdoMarker = parseDoubleSafe(tokens[19].trim(), fuelResetOdoMarker);
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
                                    expenseFleetList.add(new CustomExpense(sub[0], parseDoubleSafe(sub[1], 0.0), sub[2]));
                                }
                            }
                        }
                    }
                    if (tokens.length >= 25) {
                        tyreCount = parseIntSafe(tokens[22].trim(), tyreCount);
                        tyreUnitPrice = parseDoubleSafe(tokens[23].trim(), tyreUnitPrice);
                        tyreReplacementKm = parseDoubleSafe(tokens[24].trim(), tyreReplacementKm);
                    }
                    if (tokens.length >= 26) {
                        dailyNetIncomeGoal = parseDoubleSafe(tokens[25].trim(), dailyNetIncomeGoal);
                    }
                    if (!regNumber.isEmpty() && shiftPrefs != null) {
                        shiftPrefs.edit().putString("selectedVehicleReg", regNumber).apply();
                    }
                    shiftStartFuelValueRs = (fuelCapacity * fuelRatePerUnit) * (estimateCurrentFuelPercentage() / 100.0);
                    refreshQuickPlatformSpinner();
                    updateLiveDashboardMetrics();
                    refreshSystemButtonUI();
                }
            }
        } catch (Exception ignored) {}
    }

    private void persistShiftStateToStorage() {
        if (shiftPrefs == null) return;
        try {
            shiftPrefs.edit()
                    .putString("isAppStarted", String.valueOf(isAppStarted))
                    .putString("isTripStarted", String.valueOf(isTripStarted))
                    .putString("accumulatedDistanceKm", String.valueOf(accumulatedDistanceKm))
                    .putString("dryRunDistanceKm", String.valueOf(dryRunDistanceKm))
                    .putString("dailyEarningsSum", String.valueOf(dailyEarningsSum))
                    .putString("dailyOrdersCount", String.valueOf(dailyOrdersCount))
                    .putString("dailySpotExpenses", String.valueOf(dailySpotExpenses))
                    .putString("dailyNetIncomeGoal", String.valueOf(dailyNetIncomeGoal))
                    .putString("shiftStartFuelValueRs", String.valueOf(shiftStartFuelValueRs))
                    .putString("currentFuelPercent", String.valueOf(currentFuelPercent))
                    .putString("vehicleOdoBaseline", String.valueOf(vehicleOdoBaseline))
                    .putString("fuelResetOdoMarker", String.valueOf(fuelResetOdoMarker))
                    .putString("totalWaitingTimeMs", String.valueOf(totalWaitingTimeMs))
                    .putString("tripStopTimestamp", String.valueOf(tripStopTimestamp))
                    .putString("terminalLog", tvTerminalLog != null ? tvTerminalLog.getText().toString() : "")
                    .apply();
        } catch (Exception ignored) {}
    }

    private void restoreShiftStateFromStorage() {
        if (shiftPrefs == null) return;
        try {
            String appStartedStr = readPrefAsString("isAppStarted", "false").trim().toLowerCase(Locale.US);
            boolean savedAppStarted = "true".equals(appStartedStr);
            if (!savedAppStarted) return;

            isAppStarted = true;
            String tripStartedStr = readPrefAsString("isTripStarted", "false").trim().toLowerCase(Locale.US);
            isTripStarted = "true".equals(tripStartedStr);

            accumulatedDistanceKm = parseDoubleSafe(readPrefAsString("accumulatedDistanceKm", "0.0"), 0.0);
            dryRunDistanceKm = parseDoubleSafe(readPrefAsString("dryRunDistanceKm", "0.0"), 0.0);
            dailyEarningsSum = parseDoubleSafe(readPrefAsString("dailyEarningsSum", "0.0"), 0.0);
            dailyOrdersCount = parseIntSafe(readPrefAsString("dailyOrdersCount", "0"), 0);
            dailySpotExpenses = parseDoubleSafe(readPrefAsString("dailySpotExpenses", "0.0"), 0.0);
            dailyNetIncomeGoal = parseDoubleSafe(readPrefAsString("dailyNetIncomeGoal", "2000.0"), 2000.0);
            shiftStartFuelValueRs = parseDoubleSafe(readPrefAsString("shiftStartFuelValueRs", "0.0"), 0.0);
            currentFuelPercent = parseDoubleSafe(readPrefAsString("currentFuelPercent", "100.0"), 100.0);
            vehicleOdoBaseline = parseDoubleSafe(readPrefAsString("vehicleOdoBaseline", String.valueOf(vehicleOdoBaseline)), vehicleOdoBaseline);
            fuelResetOdoMarker = parseDoubleSafe(readPrefAsString("fuelResetOdoMarker", String.valueOf(fuelResetOdoMarker)), fuelResetOdoMarker);
            totalWaitingTimeMs = parseLongSafe(readPrefAsString("totalWaitingTimeMs", "0"), 0L);
            tripStopTimestamp = parseLongSafe(readPrefAsString("tripStopTimestamp", "0"), 0L);

            String savedLog = readPrefAsString("terminalLog", "");
            if (!savedLog.isEmpty() && tvTerminalLog != null) {
                tvTerminalLog.setTextColor(0xFFFFFFFF);
                tvTerminalLog.setText(savedLog);
            }
        } catch (Exception ignored) {
            clearPersistedShiftState();
        }
    }

    private void clearPersistedShiftState() {
        if (shiftPrefs == null) return;
        try {
            shiftPrefs.edit()
                    .putString("isAppStarted", "false")
                    .putString("isTripStarted", "false")
                    .putString("accumulatedDistanceKm", "0.0")
                    .putString("dryRunDistanceKm", "0.0")
                    .putString("dailyEarningsSum", "0.0")
                    .putString("dailyOrdersCount", "0")
                    .putString("dailySpotExpenses", "0.0")
                    .putString("totalWaitingTimeMs", "0")
                    .putString("tripStopTimestamp", "0")
                    .apply();
        } catch (Exception ignored) {}
    }

    private String readPrefAsString(String key, String fallback) {
        try {
            if (shiftPrefs == null || shiftPrefs.getAll() == null) return fallback;
            Object val = shiftPrefs.getAll().get(key);
            return val != null ? String.valueOf(val) : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private int parseIntSafe(String val, int fallback) {
        try {
            if (val == null || val.trim().isEmpty()) return fallback;
            return (int) Double.parseDouble(val.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private long parseLongSafe(String val, long fallback) {
        try {
            if (val == null || val.trim().isEmpty()) return fallback;
            return (long) Double.parseDouble(val.trim());
        } catch (Exception e) {
            return fallback;
        }
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
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && locationManager != null) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2500, 5, locationListener);
            }
        } catch (Exception ignored) {}
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
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && locationManager != null) {
                locationManager.removeUpdates(passiveIdleListener);
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 4000, 5, passiveIdleListener);
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 8000, 10, passiveIdleListener);
            }
        } catch (Exception ignored) {}
    }

    private void stopIdlePassiveTrackingEngine() {
        try {
            if (locationManager != null) {
                locationManager.removeUpdates(passiveIdleListener);
            }
        } catch (Exception ignored) {}
    }

    private final LocationListener passiveIdleListener = new LocationListener() {
        @Override public void onLocationChanged(@NonNull Location loc) { processLiveLocationTelemetry(loc, false); }
        @Override public void onProviderDisabled(@NonNull String p) {}
        @Override public void onProviderEnabled(@NonNull String p) {}
    };

    private void processLiveLocationTelemetry(Location location, boolean isInsideActiveTrip) {
        try {
            if (!isAppStarted || location == null) return;
            if (!isInsideActiveTrip && isTripStarted) return;

            double lat = location.getLatitude();
            double lon = location.getLongitude();
            String locStamp = String.format(Locale.US, "%.4f,%.4f", lat, lon);

            if (previousLocation != null) {
                double deltaMeters = location.distanceTo(previousLocation);
                double deltaKm = deltaMeters / 1000.0;
                if (deltaMeters >= 5.0 && deltaKm <= 25.0) {
                    if (isInsideActiveTrip) accumulatedDistanceKm += deltaKm;
                    else dryRunDistanceKm += deltaKm;
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
        } catch (Exception ignored) {}
    }

    private void updateWaitingTimeDisplayField() {
        if (tvWaitTime == null) return;
        tvWaitTime.setTextColor(0xFFFFFFFF);
        long min = (totalWaitingTimeMs / 1000) / 60;
        tvWaitTime.setText("Idle: " + (min / 60) + "H " + (min % 60) + "M");
    }

    private void refreshSystemButtonUI() {
        try {
            if (btnAppToggle != null && btnTripToggle != null) {
                btnAppToggle.setTextColor(0xFFFFFFFF);
                btnTripToggle.setTextColor(0xFFFFFFFF);
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
            }
            if (tvVehicleStatus != null && !regNumber.isEmpty()) {
                tvVehicleStatus.setText(vehicleName + " [" + regNumber + "] | Fix: ₹" + String.format(Locale.US, "%.0f/d", dailyStandingCost));
                tvVehicleStatus.setTextColor(isAppStarted ? 0xFF00E676 : 0xFFFFFFFF);
            }
        } catch (Exception ignored) {}
    }

    private void logToTerminal(String message) {
        try {
            if (tvTerminalLog == null) return;
            tvTerminalLog.setTextColor(0xFFFFFFFF);
            String timestamp = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
            tvTerminalLog.append(timestamp + " " + message + "\n");
            if (svTerminal != null) {
                svTerminal.post(() -> svTerminal.fullScroll(View.FOCUS_DOWN));
            }
        } catch (Exception ignored) {}
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
        et.setHintTextColor(0xFFCFD8DC);
        container.addView(et);
        return et;
    }

    private void calculateRemainingWorkingDaysSafe() {
        if (tvDaysLeft == null) return;
        tvDaysLeft.setTextColor(0xFFFFFFFF);
        Calendar cal = Calendar.getInstance();
        int todayDay = cal.get(Calendar.DAY_OF_MONTH);
        int maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        int workingDaysLeft = 0;
        for (int d = todayDay; d <= maxDay; d++) {
            cal.set(Calendar.DAY_OF_MONTH, d);
            if (cal.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
                workingDaysLeft++;
            }
        }
        tvDaysLeft.setText("Days Left: " + workingDaysLeft);
    }

    @Override
    protected void onDestroy() {
        dismissActiveDialogSafely();
        try {
            if (locationManager != null) {
                if (locationListener != null) locationManager.removeUpdates(locationListener);
                locationManager.removeUpdates(passiveIdleListener);
            }
        } catch (Exception ignored) {}
        try {
            if (speechRecognizer != null) speechRecognizer.destroy();
        } catch (Exception ignored) {}
        super.onDestroy();
    }
}