package com.intel.realsenseid.f450androidexample;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.text.Layout;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.TextureView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.intel.realsenseid.api.AndroidSerialConfig;
import com.intel.realsenseid.api.AuthFaceprintsExtractionCallback;
import com.intel.realsenseid.api.AuthenticateStatus;
import com.intel.realsenseid.api.AuthenticationCallback;
import com.intel.realsenseid.api.DBFaceprintsElement;
import com.intel.realsenseid.api.DeviceConfig;
import com.intel.realsenseid.api.EnrollFaceprintsExtractionCallback;
import com.intel.realsenseid.api.EnrollStatus;
import com.intel.realsenseid.api.EnrollmentCallback;
import com.intel.realsenseid.api.ExtractedFaceprints;
import com.intel.realsenseid.api.ExtractedFaceprintsElement;
import com.intel.realsenseid.api.FaceAuthenticator;
import com.intel.realsenseid.api.FacePose;
import com.intel.realsenseid.api.FaceRect;
import com.intel.realsenseid.api.FaceRectVector;
import com.intel.realsenseid.api.Faceprints;
import com.intel.realsenseid.api.FaceprintsVector;
import com.intel.realsenseid.api.MatchElement;
import com.intel.realsenseid.api.MatchResultHost;
import com.intel.realsenseid.api.Preview;
import com.intel.realsenseid.api.PreviewConfig;
import com.intel.realsenseid.api.PreviewMode;
import com.intel.realsenseid.api.Status;
import com.intel.realsenseid.api.UserFaceprints;
import com.intel.realsenseid.api.UserFaceprintsVector;
import com.intel.realsenseid.impl.UsbCdcConnection;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;


public class MainActivity extends AppCompatActivity {
    private static final String TAG = "AndroidF450Sample";
    private static final FacePose[] enrollmentRequiredPoses = new FacePose[]{FacePose.Center,
            FacePose.Left, FacePose.Right};
    public static final int ID_MAX_LENGTH = 30;
    private UsbCdcConnection m_UsbCdcConnection;
    FaceAuthenticator m_faceAuthenticator;
    Preview m_preview;
    AndroidPreviewImageReadyCallback m_previewCallback;

    private TextView m_messagesTV;
    private List<Integer> m_buttonIds;
    private boolean m_flipOrientation;
    private boolean m_allowMasks;
    private ArrayList<String> m_userIds;
    private String m_selectedId;
    private TextureView m_previewTxv;
    private static final int MAX_NUMBER_OF_USERS_TO_SHOW = 25;
    private Menu m_optionsMenu;
    private FaceAuthenticatorCreator m_faceAuthenticatorCreator;
    private boolean m_loopRunning;

    private ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    launchFilesListActivity();
                } else {
                    // Explain to the user that the feature is unavailable because the
                    // features requires a permission that the user has denied. At the
                    // same time, respect the user's decision. Don't link to system
                    // settings in an effort to convince the user to change their
                    // decision.
                    new AlertDialog.Builder(this)
                            .setMessage("Firmware update functionality is disabled due to " +
                                    "insufficient permissions.\nRead external storage permission" +
                                    " is required.");
                }
            });


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Log.d(TAG, "onCreate");
        ShowMainUI();
        m_messagesTV = (TextView) findViewById(R.id.txt_messages);
        m_messagesTV.setMovementMethod(new ScrollingMovementMethod());
        m_buttonIds = new ArrayList<>();
        m_buttonIds.add(R.id.btn_authenticate);
        m_buttonIds.add(R.id.btn_authenticateLoop);
        m_buttonIds.add(R.id.btn_enroll);
        m_buttonIds.add(R.id.btn_remove);
        m_buttonIds.add(R.id.btn_remove_all);
        m_optionsMenu = null;
        m_flipOrientation = false;
        m_allowMasks = false;
        m_userIds = new ArrayList<>();
        m_selectedId = null;
        m_previewTxv = (TextureView) findViewById(R.id.txv_preview);
        m_loopRunning = false;
        System.loadLibrary("RealSenseIDSwigJNI");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        m_optionsMenu = menu;
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        menu.findItem(R.id.action_flip_camera).setChecked(m_flipOrientation);
        menu.findItem(R.id.action_allow_mask).setChecked(m_allowMasks);
        return true;
    }

    private void setAuthSettingsToUI() {
        if (null == m_optionsMenu) {
            // Menu not created yet. Items will get the correct values when inflated
            // We are done here
            return;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                m_optionsMenu.findItem(R.id.action_flip_camera).setChecked(m_flipOrientation);
                m_optionsMenu.findItem(R.id.action_allow_mask).setChecked(m_allowMasks);
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        item.setChecked(!item.isChecked());
        switch (id) {
            case R.id.action_flip_camera:
                m_flipOrientation = item.isChecked();
                ApplyAuthenticationSettings();
                break;
            case R.id.action_allow_mask:
                m_allowMasks = item.isChecked();
                ApplyAuthenticationSettings();
                break;
            case R.id.action_standby:
                new Thread(new Runnable() {
                    public void run() {
                        DoWhileConnected(() -> {
                            setEnableToList(false);
                            Status s = m_faceAuthenticator.Standby();
                            Log.d(TAG, String.format("Standby action status: %s", s.toString()));
                            setEnableToList(true);
                        });
                    }
                }).start();
                break;
            case R.id.action_firmware_update:
                switchToFirmwareUpdateUI();
                break;
            case R.id.action_host_mode: {
                OnClickHostMode(item);
            }
                break;
            case R.id.action_export_db:
                OnClickExportDB();
                break;
            case R.id.action_import_db:
                OnClickImportDB();
                break;
            default:
                /**
                 * Very un-elegant, but this is a way to make the code that calls Pair and Unpair
                 * and is supported only in "secured" mode exist in secure mode, and not break
                 * the compilation of "unsecured" mode, without keeping a clone of
                 * "MainActivity.java" for each build flavor.
                 *
                 *  TODO: Consider re-designing this
                 */
                new Thread(new Runnable() {
                    public void run() {
                        DoWhileConnected(() -> {
                            setEnableToList(false);
                            MenuHelper.HandleSelection(id, m_faceAuthenticator);
                            setEnableToList(true);
                        });
                    }
                }).start();
        }
        return true;
    }

    private void OnClickHostMode(MenuItem item) {
        String[] permissions = { Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE };
        boolean hasPermissions = true;
        for (int i = 0; i < permissions.length; ++i) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                hasPermissions = false; break;
            }
        }
        if (!hasPermissions) {
            item.setChecked(false);
            AppendToTextView(this, getResources().getString(R.string.permission_not_granted));
            AppendToTextView(this, "Please click the button again.");
            ActivityCompat.requestPermissions(this, permissions, 65536);
            return;
        }

        m_hostMode = item.isChecked();

        MenuItem export_db = m_optionsMenu.findItem(R.id.action_export_db);
        if (export_db != null) export_db.setEnabled(!m_hostMode);
        MenuItem import_db = m_optionsMenu.findItem(R.id.action_import_db);
        if (import_db != null) import_db.setEnabled(!m_hostMode);
        if (m_hostMode) {
            LoadHostModeDB();
            AppendToTextView(this, String.format("Host Mode: ON"));
        } else {
            m_hostModeDB.clear();
            AppendToTextView(this, String.format("Host Mode: OFF"));
        }
    }

    private void switchToFirmwareUpdateUI() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            launchFilesListActivity();
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
            new AlertDialog.Builder(this)
                    .setMessage("Permission to read external storage is required in order to " +
                            "browse for and read firmware file.")
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            requestPermissionLauncher.launch(
                                    Manifest.permission.READ_EXTERNAL_STORAGE);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null).show();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
    }

    private void launchFilesListActivity() {
        Intent intent = new Intent(this, FilesListActivity.class);
        startActivity(intent);
    }

    private void ApplyAuthenticationSettings() {
        new Thread(new Runnable() {
            public void run() {
                DoWhileConnected(() -> {
                    setEnableToList(false);
                    Status s = m_faceAuthenticator.SetDeviceConfig(getUIDeviceConfig());
                    Log.d(TAG, String.format("Setting authentication settings status: %s", s.toString()));
                    setEnableToList(true);
                });
            }
        }).start();
    }

    private DeviceConfig getUIDeviceConfig() {
        DeviceConfig dc = new DeviceConfig();
        dc.setCamera_rotation(m_flipOrientation ? DeviceConfig.CameraRotation.Rotation_180_Deg : DeviceConfig.CameraRotation.Rotation_0_Deg);
        dc.setSecurity_level(m_allowMasks ? DeviceConfig.SecurityLevel.Medium : DeviceConfig.SecurityLevel.High);
        return dc;
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "!!!!! onStart");
        super.onStart();
        buildFaceBiometricsOnThread();
        m_keepFAAlive = false;
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "!!!!! onStop");
        if (!m_keepFAAlive) DeconstructFaceAuthenticator();
        super.onStop();
    }

    private  void buildPreview(){
        final Activity activity = this;
        PreviewConfig previewConfig= new PreviewConfig();
        AppendToTextView(activity,"Preview starting...");
        previewConfig.setCameraNumber(m_UsbCdcConnection.GetFileDescriptor());
        previewConfig.setPreviewMode(PreviewMode.MJPEG_720P);
        m_preview = new Preview(previewConfig);
        m_previewCallback = new AndroidPreviewImageReadyCallback(m_previewTxv);
        m_preview.StartPreview(m_previewCallback);
        AppendToTextView(activity,"Preview started");
    }

    private void buildFaceBiometricsOnThread() {
        final Activity activity = this;
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "AsyncTask: buildFaceBiometricsOnThread");
                buildFaceBiometrics(activity);
            }
        });
    }

    private void buildFaceBiometrics(Activity activity) {
        m_UsbCdcConnection = new UsbCdcConnection();
        if (m_UsbCdcConnection != null) {
            Context applicationContext = MainActivity.this.getApplicationContext();
            Log.d(TAG, "The class UsbCdcConnection was created");
            if (!m_UsbCdcConnection.FindSupportedDevice(applicationContext)) {
                AppendToTextView(activity, getResources().getString(R.string.device_not_found));
                Log.e(TAG, "Supported USB device not found");
                DeconstructFaceAuthenticator();
                return;
            }
            m_UsbCdcConnection.RequestDevicePermission(applicationContext, new UsbCdcConnection.PermissionCallback() {

                @Override
                public void Response(boolean permissionGranted) {
                    if (permissionGranted && m_UsbCdcConnection.OpenConnection()) {
                        m_faceAuthenticatorCreator = new FaceAuthenticatorCreator();
                        AndroidSerialConfig androidSerialConfig = new AndroidSerialConfig();
                        androidSerialConfig.setFileDescriptor(m_UsbCdcConnection.GetFileDescriptor());
                        androidSerialConfig.setReadEndpoint(m_UsbCdcConnection.GetReadEndpointAddress());
                        androidSerialConfig.setWriteEndpoint(m_UsbCdcConnection.GetWriteEndpointAddress());
                        m_faceAuthenticator = m_faceAuthenticatorCreator.Create(androidSerialConfig);
                        if (null != m_faceAuthenticator) {
                            Log.d(TAG, "FaceAuthenticator class was created");
                            DoWhileConnected(() -> {
                                updateUIAuthSettingsFromDevice();
                            });
                            setEnableToList(true);
                            buildPreview();
                            return;
                        }
                    } else {
                        AppendToTextView(activity, getResources().getString(R.string.permission_not_granted));
                    }
                    Log.e(TAG, "Error creating the class FaceAuthenticator");
                    DeconstructFaceAuthenticator();
                }
            });
        }
    }

    private void updateUIAuthSettingsFromDevice() {
        DeviceConfig dc = new DeviceConfig();
        m_faceAuthenticator.QueryDeviceConfig(dc);
        m_flipOrientation = dc.getCamera_rotation() == DeviceConfig.CameraRotation.Rotation_180_Deg;
        m_allowMasks = dc.getSecurity_level() == DeviceConfig.SecurityLevel.Medium;
        setAuthSettingsToUI();
    }

    private void setEnableToList(boolean b) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                for (int button : m_buttonIds) {
                    findViewById(button).setEnabled(b);
                }
            }
        });
    }

    private void DeconstructFaceAuthenticator() {
        if (m_loopRunning) {
            StopAuthenticationLoop();
            m_loopRunning = false;
        }

        if (m_preview != null) {
            m_preview.StopPreview();
        }

        if (m_faceAuthenticator != null) {
            m_faceAuthenticator.Disconnect();
            m_faceAuthenticator = null;
        }

        if (m_UsbCdcConnection != null) {
            m_UsbCdcConnection.CloseConnection();
            m_UsbCdcConnection = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "!!!!! onResume");
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "!!!!! onPause");
        super.onPause();
    }

    public void ExecuteEnrollment(View view) {
        ShowEnrollmentUI();
    }

    public void AppendToTextView(final Activity activity, final String msg) {
        final TextView tv = m_messagesTV;
        activity.runOnUiThread(new Runnable() {
            public void run() {
                tv.append(msg + "\n");
                final Layout layout = tv.getLayout();
                if (layout != null) {
                    int delta = layout.getLineBottom(tv.getLineCount() - 1) - tv.getScrollY()
                            - tv.getHeight();
                    if (delta > 0)
                        tv.scrollBy(0, delta);
                }
            }
        });
    }

    public void ExecuteEnrollOk(View view) {
        Log.d(TAG, "ExecuteEnrollment");
        if (m_faceAuthenticator == null) {
            Log.d(TAG, "faceAuthenticator is null");
            return;
        }
        setEnableToList(false);
        final Activity activity = this;
        new Thread(new Runnable() {
            public void run() {
                EditText userIdText = (EditText) findViewById(R.id.txt_user_id);
                String UserId = userIdText.getText().toString();

                EnrollmentPoseTracker poseTracker = new EnrollmentPoseTracker(enrollmentRequiredPoses);
                EnrollmentCallback enrollmentCallback = new EnrollmentCallback() {
                    @Override
                    public void OnResult(EnrollStatus status) {
                        if (status == EnrollStatus.Success) {
                            Log.d(TAG, "Enrollment completed successfully");
                            AppendToTextView(activity, getResources().getString(R.string.enroll_success));
                        } else {
                            String msg = String.format("Enrollment failed with status %s",
                                    status.toString());
                            Log.d(TAG, msg);
                            AppendToTextView(activity, msg);
                        }
                    }

                    @Override
                    public void OnProgress(FacePose pose) {
                        poseTracker.markPoseCheck(pose);
                        FacePose nextPose = poseTracker.getNext();
                        if (nextPose != null) {
                            ToastOnUIThread(activity, "Turn face towards: " + nextPose.toString(), Toast.LENGTH_SHORT);
                            Log.d(TAG, String.format("Enrollment progress. pose: %s", pose.toString()));
                        }
                    }

                    @Override
                    public void OnHint(EnrollStatus hint) {
                        Log.d(TAG, String.format("Enrollment hint: %s", hint.toString()));
                        if (hint != EnrollStatus.CameraStarted && hint != EnrollStatus.CameraStopped && hint != EnrollStatus.FaceDetected && hint != EnrollStatus.LedFlowSuccess)
                            AppendToTextView(activity, hint.toString());
                    }

                    @Override
                    public void OnFaceDetected(FaceRectVector faces, long ts) {
                        //super.OnFaceDetected(faces, ts);
                        MainActivity.this.LogInfoFaceRectVector(faces, ts);
                    }
                };
                EnrollFaceprintsExtractionCallback enrollFaceprintsExtractionCallback = new EnrollFaceprintsExtractionCallback() {
                    @Override
                    public void OnResult(EnrollStatus status, ExtractedFaceprints faceprints) {
                        //super.OnResult(status, faceprints);
                        //Log.d(TAG, String.format("OnResult: %s %s", status.toString(), faceprints.toString()));
                        if (status == EnrollStatus.Success) {
                            Log.d(TAG, "Enrollment completed successfully");
                            HostModeDBItem item = new HostModeDBItem(UserId);
                            item.setFeatures(faceprints.getData());
                            short[] desc = item.features.getData().getEnrollmentDescriptor();
                            Log.d(TAG, desc.toString());
                            m_hostModeDB.add(item);
                            AppendToTextView(activity, getResources().getString(R.string.enroll_success));
                            SaveHodeModeDB();
                        } else {
                            String msg = String.format("Enrollment failed with status %s", status.toString());
                            Log.d(TAG, msg);
                            AppendToTextView(activity, msg);
                        }
                    }

                    @Override
                    public void OnProgress(FacePose pose) {
                        //super.OnProgress(pose);
                        poseTracker.markPoseCheck(pose);
                        FacePose nextPose = poseTracker.getNext();
                        if (nextPose != null) {
                            ToastOnUIThread(activity, "Turn face towards: " + nextPose.toString(), Toast.LENGTH_SHORT);
                            Log.d(TAG, String.format("OnProgress: Enrollment progress. pose: %s", pose.toString()));
                        }
                    }

                    @Override
                    public void OnHint(EnrollStatus hint) {
                        //super.OnHint(hint);
                        Log.d(TAG, String.format("OnHint: Enrollment hint: %s", hint.toString()));
                        if (hint != EnrollStatus.CameraStarted && hint != EnrollStatus.CameraStopped && hint != EnrollStatus.FaceDetected && hint != EnrollStatus.LedFlowSuccess)
                            AppendToTextView(activity, hint.toString());
                    }

                    @Override
                    public void OnFaceDetected(FaceRectVector faces, long ts) {
                        //super.OnFaceDetected(faces, ts);
                        MainActivity.this.LogInfoFaceRectVector(faces, ts);
                    }
                };
                DoWhileConnected(() -> {
                    Status enrollStatus = Status.Error;
                    if (MainActivity.this.m_hostMode) {
                        enrollStatus = m_faceAuthenticator.ExtractFaceprintsForEnroll(enrollFaceprintsExtractionCallback);
                    } else {
                        enrollStatus = m_faceAuthenticator.Enroll(enrollmentCallback, UserId);
                    }
                    Log.d(TAG, "Enrollment done with status: " + enrollStatus.toString());
                    setEnableToList(true);
                });
            }
        }).start();
        ShowMainUI();
    }

    void DoWhileConnected(ConnectionWrappedFunction f) {
        AndroidSerialConfig config = new AndroidSerialConfig();
        config.setFileDescriptor(m_UsbCdcConnection.GetFileDescriptor());
        config.setReadEndpoint(m_UsbCdcConnection.GetReadEndpointAddress());
        config.setWriteEndpoint(m_UsbCdcConnection.GetWriteEndpointAddress());
        m_faceAuthenticator.Connect(config);
        f.Do();
        if (m_faceAuthenticator != null)
            m_faceAuthenticator.Disconnect();
    }

    public void ExecuteEnrollCancel(View view) {
        ShowMainUI();
    }

    private void ShowEnrollmentUI() {
        findViewById(R.id.ll_authenticate).setVisibility(View.GONE);
        findViewById(R.id.btn_authenticate).setVisibility(View.GONE);
        findViewById(R.id.btn_authenticateLoop).setVisibility(View.GONE);
        findViewById(R.id.btn_enroll).setVisibility(View.GONE);
        findViewById(R.id.btn_remove).setVisibility(View.GONE);
        EditText et = (EditText) findViewById(R.id.txt_user_id);
        et.setText("");
        et.setVisibility(View.VISIBLE);
        findViewById(R.id.btn_enroll_ok).setVisibility(View.VISIBLE);
        findViewById(R.id.btn_enroll_cancel).setVisibility(View.VISIBLE);
    }

    private void ShowMainUI() {
        findViewById(R.id.txv_preview).setVisibility(View.VISIBLE);
        findViewById(R.id.ll_authenticate).setVisibility(View.VISIBLE);
        findViewById(R.id.btn_authenticate).setVisibility(View.VISIBLE);
        findViewById(R.id.btn_authenticateLoop).setVisibility(View.VISIBLE);
        findViewById(R.id.btn_enroll).setVisibility(View.VISIBLE);
        findViewById(R.id.btn_remove).setVisibility(View.VISIBLE);
        findViewById(R.id.txt_user_id).setVisibility(View.GONE);
        findViewById(R.id.btn_enroll_ok).setVisibility(View.GONE);
        findViewById(R.id.btn_enroll_cancel).setVisibility(View.GONE);
        findViewById(R.id.lst_enrolled_ids).setVisibility(View.GONE);
        findViewById(R.id.btn_remove_selected).setVisibility(View.GONE);
        findViewById(R.id.btn_remove_all).setVisibility(View.GONE);
        findViewById(R.id.btn_back_from_remove).setVisibility(View.GONE);
    }

    private void ShowRemoveUI() {
        findViewById(R.id.txv_preview).setVisibility(View.GONE);
        findViewById(R.id.ll_authenticate).setVisibility(View.GONE);
        findViewById(R.id.btn_authenticate).setVisibility(View.GONE);
        findViewById(R.id.btn_authenticateLoop).setVisibility(View.GONE);
        findViewById(R.id.btn_enroll).setVisibility(View.GONE);
        findViewById(R.id.btn_remove).setVisibility(View.GONE);
        findViewById(R.id.lst_enrolled_ids).setVisibility(View.VISIBLE);
        findViewById(R.id.btn_remove_selected).setVisibility(View.VISIBLE);
        findViewById(R.id.btn_remove_all).setVisibility(View.VISIBLE);
        findViewById(R.id.btn_back_from_remove).setVisibility(View.VISIBLE);
    }

    public void ExecuteAuthentication(View view) {
        Log.d(TAG, "ExecuteAuthentication");
        if (m_faceAuthenticator == null) {
            Log.d(TAG, "faceAuthenticator is null");
            return;
        }
        setEnableToList(false);
        final Activity activity = this;
        new Thread(new Runnable() {
            @Override
            public void run() {
                // a potentially time consuming task
                AuthenticationCallback authenticationCallback = new AuthenticationCallback() {
                    @Override
                    public void OnResult(AuthenticateStatus status, String userId) {
                        if (status == AuthenticateStatus.Success) {
                            Log.d(TAG, String.format("Authentication allowed. UserId %s", userId));
                            AppendToTextView(activity, getResources().getString(R.string.authenticate_greet) + " " + userId);
                        } else {
                            Log.d(TAG, "Authentication forbidden");
                            AppendToTextView(activity, getResources().getString(R.string.authenticate_forbidden));
                        }
                    }

                    @Override
                    public void OnHint(AuthenticateStatus hint) {
                        Log.d(TAG, String.format("Authentication hint: %s", hint.toString()));
                        if (hint != AuthenticateStatus.CameraStarted && hint != AuthenticateStatus.CameraStopped && hint != AuthenticateStatus.FaceDetected && hint != AuthenticateStatus.LedFlowSuccess)
                            AppendToTextView(activity, hint.toString());
                    }

                    @Override
                    public void OnFaceDetected(FaceRectVector faces, long ts) {
                        //super.OnFaceDetected(faces, ts);
                        MainActivity.this.LogInfoFaceRectVector(faces, ts);
                    }
                };
                AuthFaceprintsExtractionCallback authFaceprintsExtractionCallback = new AuthFaceprintsExtractionCallback() {
                    @Override
                    public void OnResult(AuthenticateStatus status, ExtractedFaceprints faceprints) {
                        //super.OnResult(status, faceprints);
                        if (status == AuthenticateStatus.Success) {
                            String userId = MainActivity.this.MatchFaceprintsInDB(faceprints);
                            if (userId != null && !userId.isEmpty()) {
                                Log.d(TAG, String.format("Authentication allowed. UserId %s", userId));
                                AppendToTextView(activity, getResources().getString(R.string.authenticate_greet) + " " + userId);
                            } else {
                                Log.d(TAG, String.format("Authentication FAILED. NO_USER"));
                                AppendToTextView(activity, "NO USER found in DB. " + getResources().getString(R.string.authenticate_forbidden));
                            }
                        } else {
                            Log.d(TAG, "Authentication forbidden");
                            AppendToTextView(activity, getResources().getString(R.string.authenticate_forbidden));
                        }
                    }

                    @Override
                    public void OnHint(AuthenticateStatus hint) {
                        //super.OnHint(hint);
                        Log.d(TAG, String.format("Authentication hint: %s", hint.toString()));
                        if (hint != AuthenticateStatus.CameraStarted && hint != AuthenticateStatus.CameraStopped && hint != AuthenticateStatus.FaceDetected && hint != AuthenticateStatus.LedFlowSuccess)
                            AppendToTextView(activity, hint.toString());

                    }

                    @Override
                    public void OnFaceDetected(FaceRectVector faces, long ts) {
                        //super.OnFaceDetected(faces, ts);
                        MainActivity.this.LogInfoFaceRectVector(faces, ts);
                    }
                };
                DoWhileConnected(() -> {
                    Status authenticateStatus = Status.Error;
                    if (MainActivity.this.m_hostMode) {
                        authenticateStatus = m_faceAuthenticator.ExtractFaceprintsForAuth(authFaceprintsExtractionCallback);
                    } else {
                        authenticateStatus = m_faceAuthenticator.Authenticate(authenticationCallback);
                    }
                    Log.d(TAG, "Authentication done with status: " + authenticateStatus.toString());
                    setEnableToList(true);
                });
            }
        }).start();
    }

    private void StopAuthenticationLoop() {
        Status status = m_faceAuthenticator.Cancel();
        Log.d(TAG, "Authentication cancel done with status: " + status.toString());
    }

    public void ExecuteAuthenticationLoop(View view) {
        Log.d(TAG, "ExecuteAuthenticationLoop");
        if (m_faceAuthenticator == null) {
            Log.d(TAG, "faceAuthenticator is null");
            return;
        }

        if (m_loopRunning) {
            StopAuthenticationLoop();
            m_loopRunning = false;
            return;
        }

        setEnableToList(false);
        m_loopRunning = true;
        findViewById(R.id.btn_authenticateLoop).setEnabled(true);
        final Activity activity = this;
        new Thread(new Runnable() {
            @Override
            public void run() {
                // a potentially time consuming task
                AuthenticationCallback authenticationCallback = new AuthenticationCallback() {
                    @Override
                    public void OnResult(AuthenticateStatus status, String userId) {
                        if (status == AuthenticateStatus.Success) {
                            Log.d(TAG, String.format("Authentication allowed. UserId %s", userId));
                            AppendToTextView(activity, getResources().getString(R.string.authenticate_greet) + " " + userId);
                        }
                    }
                    @Override
                    public void OnHint(AuthenticateStatus hint) {
                        Log.d(TAG, String.format("Authentication hint: %s", hint.toString()));
                    }

                    @Override
                    public void OnFaceDetected(FaceRectVector faces, long ts) {
                        super.OnFaceDetected(faces, ts);
                        MainActivity.this.LogInfoFaceRectVector(faces, ts);
                    }
                };
                AuthFaceprintsExtractionCallback authFaceprintsExtractionCallback = new AuthFaceprintsExtractionCallback() {
                    @Override
                    public void OnResult(AuthenticateStatus status, ExtractedFaceprints faceprints) {
                        //super.OnResult(status, faceprints);
                        if (status == AuthenticateStatus.Success) {
                            String userId = MainActivity.this.MatchFaceprintsInDB(faceprints);
                            if (userId != null) {
                                Log.d(TAG, String.format("== Authentication allowed. UserId %s", userId));
                                AppendToTextView(activity, getResources().getString(R.string.authenticate_greet) + " " + userId);
                            } else {
                                Log.d(TAG, String.format("== Authentication FAILED. NO_USER"));
                                AppendToTextView(activity, "NO matched USER in DB. " + getResources().getString(R.string.authenticate_forbidden));
                            }
                        } else {
                            Log.d(TAG, "== status is NOT Success");
                            Log.d(TAG, status.toString());
                            AppendToTextView(activity, getResources().getString(R.string.authenticate_forbidden));
                        }
                    }

                    @Override
                    public void OnHint(AuthenticateStatus hint) {
                        //super.OnHint(hint);
                        Log.d(TAG, String.format("Authentication hint: %s", hint.toString()));
                    }

                    @Override
                    public void OnFaceDetected(FaceRectVector faces, long ts) {
                        //super.OnFaceDetected(faces, ts);
                        MainActivity.this.LogInfoFaceRectVector(faces, ts);
                    }
                };
                DoWhileConnected(() -> {
                    Status authenticateStatus = Status.Error;
                    Log.d(TAG, "== HOST MODE: " + (MainActivity.this.m_hostMode? "ON": "OFF"));
                    if (MainActivity.this.m_hostMode) {
                        authenticateStatus = m_faceAuthenticator.ExtractFaceprintsForAuthLoop(authFaceprintsExtractionCallback);
                    } else {
                        authenticateStatus = m_faceAuthenticator.AuthenticateLoop(authenticationCallback);
                    }
                    Log.d(TAG, "AuthenticationLoop done with status: " + authenticateStatus.toString());
                    setEnableToList(true);
                });

            }
        }).start();
    }

    public void ExecuteShowRemoveUI(View view) {
        Log.d(TAG, "ExecuteShowRemoveUI");
        if (m_faceAuthenticator == null) {
            Log.d(TAG, "faceAuthenticator is null");
            return;
        }
        m_userIds.clear();
        findViewById(R.id.btn_remove_selected).setEnabled(false);
        setEnableToList(false);
        ShowRemoveUI();
        new Thread(new Runnable() {
            @Override
            public void run() {
                DoWhileConnected(() -> {
                    FetchUserIds();
                    UpdateUsersList();
                    setEnableToList(true);
                });
            }
        }).start();
    }

    private AdapterView.OnItemClickListener userIdClickedHandler = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView parent, View v, int position, long id) {
            m_selectedId = m_userIds.get(position);
            findViewById(R.id.btn_remove_selected).setEnabled(true);
        }
    };

    private void UpdateUsersList() {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, m_userIds);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ListView listView = (ListView) findViewById(R.id.lst_enrolled_ids);
                listView.setAdapter(adapter);
                listView.setOnItemClickListener(userIdClickedHandler);
            }
        });

    }

    public void ExecuteRemoveAll(View view) {
        if (m_hostMode) {
            ExecuteRemoveAllHostMode();
            return;
        }
        Log.d(TAG, "ExecuteRemoveAll");
        if (m_faceAuthenticator == null) {
            Log.d(TAG, "faceAuthenticator is null");
            return;
        }

        setEnableToList(false);
        final Activity activity = this;
        new Thread(new Runnable() {
            @Override
            public void run() {
                DoWhileConnected(() -> {
                    Status res = m_faceAuthenticator.RemoveAll();
                    if (res.equals(Status.Ok)) {
                        AppendToTextView(activity, getResources().getString(R.string.remove_all_success));
                        m_userIds.clear();
                    }
                    else {
                        AppendToTextView(activity, getResources().getString(R.string.remove_all_fail));
                    }
                    Log.d(TAG, "RemoveAll done with res: " + res);
                    FetchUserIds();
                    setEnableToList(true);
                });
                UpdateUsersList();
            }
        }).start();
    }

    public void ExecuteClearMessages(View view) {
        m_messagesTV.setText("");
        m_messagesTV.scrollTo(0, 0);
    }

    public void ExecuteBackFromRemove(View view) {
        ShowMainUI();
    }

    private void NotifyAboutDeviceQuery() {
        final Activity activity = this;
        ToastOnUIThread(activity, "Querying device for user IDs...", Toast.LENGTH_SHORT);
    }

    private void ToastOnUIThread(Activity activity, String text, int toastLength) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(activity, text, toastLength).show();
            }
        });
    }

    private void FetchUserIds() {
        NotifyAboutDeviceQuery();
        if (m_hostMode) {
            FetchUserIdsHostMode();
            return;
        }

        long[] numberOfUsers = new long[] {0};
        Status s = m_faceAuthenticator.QueryNumberOfUsers(numberOfUsers);
        if (s != Status.Ok) {
            AppendToTextView(this, "Failed to query number of users");
            return;
        }
        m_userIds.clear();
        if (numberOfUsers[0] <= 0) {
            return;
        }
        String[] ids = new String[(int) numberOfUsers[0]];
        for (int i = 0 ; i < ids.length ; ++i) {
            ids[i] = new String(new char[ID_MAX_LENGTH]); // string of ID_MAX_LENGTH characters as a placeholder. not including \0 at the end that is required in char[]
        }
        long[] numOfIds = new long[]{ids.length};
        s = m_faceAuthenticator.QueryUserIds(ids, numOfIds);
        if (s == Status.Ok) {
            for (int i = 0 ; i<numOfIds[0] ; ++i) {
                m_userIds.add(ids[i]);
            }
        } else  {
            AppendToTextView(this, "Failed to query users");
        }
    }

    public void RemoveSelected(View view) {
        if (m_hostMode) {
            RemoveSelectedHostMode();
            return;
        }
        setEnableToList(false);
        findViewById(R.id.btn_remove_selected).setEnabled(false);
        final Activity activity = this;
        new Thread(new Runnable() {
            @Override
            public void run() {
                DoWhileConnected(() -> {
                    Status res = m_faceAuthenticator.RemoveUser(m_selectedId);
                    if (res.equals(Status.Ok)) {
                        AppendToTextView(activity, "Removed user: " + m_selectedId);
                        m_userIds.clear();
                    }
                    else {
                        AppendToTextView(activity, "Failed to remove user");
                    }
                    Log.d(TAG, "Remove selected done with res: " + res);
                    FetchUserIds();
                    setEnableToList(true);
                });
                UpdateUsersList();
            }
        }).start();
    }

    ////////////////
    public void LogInfoFaceRectVector(FaceRectVector faces, long ts) {
        Log.d(TAG, String.format("OnFaceDetected: %s ts: %d", faces.toString(), ts));
        for (int i = 0; i < faces.size(); ++i) {
            FaceRect fr = faces.get(i);
            Log.d(TAG, String.format("\tFace:%d (%d, %d) - (%d, %d)", i, fr.getX(), fr.getY(), fr.getW(), fr.getH()));
        }
    }

    ////////////////
    class HostModeDBItem {
        public String     user_id;
        public Faceprints features;

        public HostModeDBItem(String id) {
            this.user_id = id;
            this.features = new Faceprints();
        }

        public void setFeatures(ExtractedFaceprintsElement faceprints) {
            DBFaceprintsElement data = new DBFaceprintsElement();
            data.setVersion(faceprints.getVersion());
            data.setFeaturesType(faceprints.getFeaturesType());
            data.setFlags(faceprints.getFlags());
            data.setEnrollmentDescriptor(faceprints.getFeaturesVector());
            data.setAdaptiveDescriptorWithoutMask(faceprints.getFeaturesVector());
            data.setAdaptiveDescriptorWithMask(faceprints.getFeaturesVector());
            this.features.setData(data);
        }
    };
    boolean m_hostMode = false;
    List<HostModeDBItem> m_hostModeDB = new ArrayList<>();

    public String MatchFaceprintsInDB(ExtractedFaceprints faceprints) {
        for (HostModeDBItem item : m_hostModeDB) {
            MatchElement new_faceprints = new MatchElement();
            new_faceprints.setData(faceprints.getData());

            Faceprints existing_faceprints = item.features;
            Faceprints updated_faceprints = new Faceprints();

            MatchResultHost matchResultHost = m_faceAuthenticator.MatchFaceprints(new_faceprints, existing_faceprints, updated_faceprints);
            if (matchResultHost.getSuccess()) {
                return item.user_id;
            }
        }
        return new String("");
    }

    void LoadHostModeDB() {
        // /sdcard/RSID_hostmode.db
        File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "RSID_hostmode.db");
        try {
            FileInputStream fi = new FileInputStream(file);
            String content = new String(getBytes(fi), StandardCharsets.UTF_8);
            fi.close();
            LoadHostModeDBFromJSON(content);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void SaveHodeModeDB() {
        // /sdcard/RSID_hostmode.db
        String content = SaveHostModeDBToJSON();
        File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "RSID_hostmode.db");
        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(file));
            bw.write(content);
            bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {

        }
    }
    void FetchUserIdsHostMode() {
        int numberOfUsers = m_hostModeDB.size();

        m_userIds.clear();
        if (numberOfUsers <= 0) {
            return;
        }
        for (int i = 0 ; i < numberOfUsers; ++i) {
            m_userIds.add(m_hostModeDB.get(i).user_id);
        }
    }
    void RemoveSelectedHostMode() {
        setEnableToList(false);
        int target = -1;
        for (int i = 0; i < m_hostModeDB.size(); ++i) {
            if (m_hostModeDB.get(i).user_id == m_selectedId) {
                target = i;
                break;
            }
        }
        m_hostModeDB.remove(target);
        FetchUserIds();
        setEnableToList(true);

        UpdateUsersList();

        SaveHodeModeDB();
    }
    void ExecuteRemoveAllHostMode() {
        setEnableToList(false);
        AppendToTextView(this, getResources().getString(R.string.remove_all_success));

        m_hostModeDB.clear();
        SaveHodeModeDB();

        FetchUserIds();
        setEnableToList(true);

        UpdateUsersList();
    }

        ////////////////
    boolean m_keepFAAlive = false;
    final int REQUEST_CODE_EXPORT_DB = 0;
    final int REQUEST_CODE_IMPORT_DB = 1;
    void OnClickExportDB() {
        Toast.makeText(MainActivity.this, "Exportint user DB ...", Toast.LENGTH_LONG);
//        m_keepFAAlive = true;
//        String timestamp = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
//        String filename = "F450-" + timestamp + ".json";
//        OpenDocumentPicker(Intent.ACTION_CREATE_DOCUMENT, REQUEST_CODE_EXPORT_DB, filename);

        // SAVE TO /sdcard/Download/RSID_exported.json
        String content = ExecExportDBToJSON();
        File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "RSID_exported.json");
        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(file));
            bw.write(content);
            bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {

        }
        AppendToTextView(this, "Export User DB to /sdcard/RSID_exported.json");
    }

    void OnClickImportDB() {
        Toast.makeText(MainActivity.this, "Import user DB ...", Toast.LENGTH_LONG);
//        m_keepFAAlive = true;
//        OpenDocumentPicker(Intent.ACTION_OPEN_DOCUMENT, REQUEST_CODE_IMPORT_DB, null);

        // LOAD FROM /sdcard/Download/RSID_exported.json
        File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "RSID_exported.json");
        try {
            FileInputStream fi = new FileInputStream(file);
            String content = new String(getBytes(fi), StandardCharsets.UTF_8);
            fi.close();
            ExecImportDBFromJSON(content);
        } catch (IOException e) {
            e.printStackTrace();
        }
        AppendToTextView(this, "Import User DB from /sdcard/RSID_exported.json");
    }

    void OpenDocumentPicker(String action, int requestCode, String defaultName) {
        Intent intent = new Intent(action).setType("*/*")
                .addCategory(Intent.CATEGORY_OPENABLE);
        if (defaultName != null) {
            intent.putExtra(Intent.EXTRA_TITLE, defaultName);
        }
        startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable @org.jetbrains.annotations.Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK) return;
        if ((data == null) || (data.getData() == null)) return;

        ToastOnUIThread(MainActivity.this, "Please waiting ...", Toast.LENGTH_LONG);

        Log.d(TAG, "!!!! onActivityResult?");
        if (true) {

            Uri uri = data.getData();
            getContentResolver().takePersistableUriPermission(uri, requestCode);

            if (REQUEST_CODE_EXPORT_DB == requestCode) {
                try {
                    String content = ExecExportDBToJSON();
                    Log.d(TAG, "!! S " + content);
                    OutputStream outputStream = getContentResolver().openOutputStream(uri);
                    outputStream.write(content.getBytes());
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else if (REQUEST_CODE_IMPORT_DB == requestCode) {
                try {
                    String content = new String(getBytes(getContentResolver().openInputStream(uri)), StandardCharsets.UTF_8);
                    Log.d(TAG, "!! L " + content);
                    ExecImportDBFromJSON(content);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            ToastOnUIThread(MainActivity.this, "Done!", Toast.LENGTH_LONG);
        }
    }

    public byte[] getBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];

        int len = 0;
        while ((len = inputStream.read(buffer)) != -1) {
            byteBuffer.write(buffer, 0, len);
        }
        return byteBuffer.toByteArray();
    }

    String ExecExportDBToJSON() {
        final String[] ret = {""};

        DoWhileConnected(() -> {
            long[] number_of_users = new long[] {0};
            m_faceAuthenticator.QueryNumberOfUsers(number_of_users);

            String[] ids = new String[(int) number_of_users[0]];
            // string of ID_MAX_LENGTH characters as a placeholder. not including \0 at the end that is required in char[]
            Arrays.fill(ids, new String(new char[ID_MAX_LENGTH]));

            m_faceAuthenticator.QueryUserIds(ids, number_of_users);

            FaceprintsVector faceprints = new FaceprintsVector((int) number_of_users[0], new Faceprints());
            m_faceAuthenticator.GetUsersFaceprints(faceprints, number_of_users);

            try {
                JSONArray users = new JSONArray();
                for(int i = 0; i < number_of_users[0]; ++i) {
                    users.put(i, FaceprintsToJSON(faceprints.get(i).getData(), ids[i]));
                }
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("version", 8);
                jsonObject.put("db", users);

                ret[0] = jsonObject.toString();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });

        return ret[0];
    }

    void ExecImportDBFromJSON(String json) {
        try {
            JSONObject jsonObject = new JSONObject(json);
            JSONArray users = jsonObject.getJSONArray("db");

            UserFaceprintsVector faceprints = new UserFaceprintsVector(users.length(), new UserFaceprints());
            for (int i = 0; i < users.length(); ++i) {
                faceprints.set(i, JSONToUserFaceprints(users.getJSONObject(i)));
            }

            DoWhileConnected(() -> {
                m_faceAuthenticator.SetUsersFaceprints(faceprints, users.length());
            });

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    String SaveHostModeDBToJSON() {
        String ret = "";

        try {
            JSONArray users = new JSONArray();
            for(int i = 0; i < m_hostModeDB.size(); ++i) {
                users.put(i, FaceprintsToJSON(m_hostModeDB.get(i).features.getData(), m_hostModeDB.get(i).user_id));
            }
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("version", 8);
            jsonObject.put("db", users);

            ret = jsonObject.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return ret;
    }
    void LoadHostModeDBFromJSON(String json) {
        try {
            JSONObject jsonObject = new JSONObject(json);
            JSONArray users = jsonObject.getJSONArray("db");

            UserFaceprintsVector faceprints = new UserFaceprintsVector(users.length(), new UserFaceprints());
            for (int i = 0; i < users.length(); ++i) {
                //faceprints.set(i, JSONToUserFaceprints(users.getJSONObject(i)));
                UserFaceprints jsonuserfaceprints = JSONToUserFaceprints(users.getJSONObject(i));
                HostModeDBItem item = new HostModeDBItem(jsonuserfaceprints.getUser_id());
                item.features = new Faceprints();
                item.features.setData(jsonuserfaceprints.getFaceprints().getData());
                m_hostModeDB.add(item);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    JSONObject FaceprintsToJSON(DBFaceprintsElement faceprints, String id) {
        JSONObject jsonObject = new JSONObject();
        try {
            JSONObject jsonFaceprints = new JSONObject();
            jsonFaceprints.put("reserved", IntArrayToJSONArray(faceprints.getReserved()));
            jsonFaceprints.put("version", faceprints.getVersion());
            jsonFaceprints.put("featuresType", faceprints.getFeaturesType());
            jsonFaceprints.put("flags", faceprints.getFlags());
            jsonFaceprints.put("enrollmentDescriptor", ShortArrayToJSONArray(faceprints.getEnrollmentDescriptor()));
            jsonFaceprints.put("adaptiveDescriptorWithMask", ShortArrayToJSONArray(faceprints.getAdaptiveDescriptorWithMask()));
            jsonFaceprints.put("adaptiveDescriptorWithoutMask", ShortArrayToJSONArray(faceprints.getAdaptiveDescriptorWithoutMask()));

            jsonObject.put("userID", id);
            jsonObject.put("faceprints", jsonFaceprints);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject;
    }

    UserFaceprints JSONToUserFaceprints(JSONObject jsonObject) {
        UserFaceprints userFaceprints = new UserFaceprints();
        try {
            JSONObject jsonFaceprints = jsonObject.getJSONObject("faceprints");
            DBFaceprintsElement faceprints = new DBFaceprintsElement();
            faceprints.setReserved(JSONToIntArray((JSONArray) jsonFaceprints.get("reserved")));
            faceprints.setVersion(jsonFaceprints.getInt("version"));
            faceprints.setFeaturesType(jsonFaceprints.getInt("featuresType"));
            faceprints.setFlags(jsonFaceprints.getInt("flags"));
            faceprints.setEnrollmentDescriptor(JSONToShortArray((JSONArray) jsonFaceprints.get("enrollmentDescriptor")));
            faceprints.setAdaptiveDescriptorWithMask(JSONToShortArray((JSONArray) jsonFaceprints.get("adaptiveDescriptorWithMask")));
            faceprints.setAdaptiveDescriptorWithoutMask(JSONToShortArray((JSONArray) jsonFaceprints.get("adaptiveDescriptorWithoutMask")));

            Faceprints faceprintsObject = new Faceprints();
            faceprintsObject.setData(faceprints);
            userFaceprints.setUser_id(jsonObject.getString("userID"));
            userFaceprints.setFaceprints(faceprintsObject);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return userFaceprints;
    }

    JSONArray IntArrayToJSONArray(int[] arr) {
        JSONArray ret = new JSONArray();
        try {
            for (int i = 0; i < arr.length; ++i) {
                ret.put(i, arr[i]);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return ret;
    }
    JSONArray ShortArrayToJSONArray(short[] arr) {
        JSONArray ret = new JSONArray();
        try {
            for (int i = 0; i < arr.length; ++i) {
                ret.put(i, arr[i]);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return ret;
    }
    int[] JSONToIntArray(JSONArray jsonArray) {
        int[] arr = new int[jsonArray.length()];
        try {
            for (int i = 0; i < arr.length; ++i) {
                arr[i] = jsonArray.getInt(i);
           }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return arr;
    }
    short[] JSONToShortArray(JSONArray jsonArray) {
        short[] arr = new short[jsonArray.length()];
        try {
            for (int i = 0; i < arr.length; ++i) {
                arr[i] = (short) jsonArray.getInt(i);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return arr;
    }
}