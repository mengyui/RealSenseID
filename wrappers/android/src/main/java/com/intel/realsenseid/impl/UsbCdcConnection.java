package com.intel.realsenseid.impl;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;


import static android.hardware.usb.UsbConstants.USB_CLASS_CDC_DATA;
import static android.hardware.usb.UsbConstants.USB_CLASS_COMM;
import static android.hardware.usb.UsbConstants.USB_DIR_IN;
import static android.hardware.usb.UsbConstants.USB_DIR_OUT;
import static android.hardware.usb.UsbConstants.USB_ENDPOINT_XFER_BULK;

class UsbCdcConnectionDetails {

    public void ResetConnectionDetails(){
        m_ControlInterface = null;
        m_ControlInterfaceIndex = -1;
        m_ControlEndpointAddress = -1;

        m_DataInterface = null;
        m_ReadEndpointAddress= -1;
        m_WriteEndpointAddress= -1;
        m_FileDescriptor= -1;

        if(null != m_usbDeviceConnection) {
            m_usbDeviceConnection.close();
            m_usbDeviceConnection = null;
        }

        m_usbManager = null;
        m_UsbDevice = null;
    }

    public UsbInterface m_ControlInterface = null;
    public int m_ControlInterfaceIndex = -1;
    public int m_ControlEndpointAddress = -1;

    public UsbInterface m_DataInterface = null;
    public int m_ReadEndpointAddress= -1;
    public int m_WriteEndpointAddress= -1;
    public int m_FileDescriptor= -1;

    public UsbDeviceConnection  m_usbDeviceConnection = null;
    public UsbManager           m_usbManager = null;
    public UsbDevice            m_UsbDevice = null;
}

public class UsbCdcConnection {

    public int GetReadEndpointAddress(){
        if(false == m_bConnectionIsOpened) {
            Log.e(TAG, "Connection to the device is not opened");
            return -1;
        }

        return m_UsbCdcConnectionDetails.m_ReadEndpointAddress;
    }

    public int GetWriteEndpointAddress(){
        if(false == m_bConnectionIsOpened) {
            Log.e(TAG, "Connection to the device is not opened");
            return -1;
        }

        return m_UsbCdcConnectionDetails.m_WriteEndpointAddress;
    }

    public int GetFileDescriptor(){
        if(false == m_bConnectionIsOpened) {
            Log.e(TAG, "Connection to the device is not opened");
            return -1;
        }
        return m_UsbCdcConnectionDetails.m_FileDescriptor;
    }

    public boolean SetConnectionConfiguration(UsbCdcConnectionConfiguration usbCdcConnectionConfiguration) {
        if(null == usbCdcConnectionConfiguration){
            Log.i(TAG, "Failed to update the connection configuration. The configuration is null");
            return false;
        }

        if(m_bConnectionIsOpened){
            CloseConnection();
        }

        m_UsbCdcConnectionConfiguration = usbCdcConnectionConfiguration;

        return true;
    }

    public boolean FindSupportedDevice(Context context) {
        HashMap<String, UsbDevice> usbDevicesList = GetUsbDevicesList(context);
        if(usbDevicesList == null || usbDevicesList.isEmpty()){
            Log.i(TAG, "USB Devices List is Empty");
            return false;
        }

        if(false == FindUsbDevice(usbDevicesList)) {
            Log.e(TAG, "Requested USB device was not found");
            PrintAllDevicesDetails(usbDevicesList);
            return false;
        }
        return true;
    }

    public interface PermissionCallback {
        void Response(boolean permissionGranted);
    }

    public void RequestDevicePermission(Context context, final PermissionCallback callback) {
        final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
        PendingIntent permissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_UPDATE_CURRENT);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        final UsbManager manager = m_UsbCdcConnectionDetails.m_usbManager;
        BroadcastReceiver usbPermissionReceiver = new BroadcastReceiver(){
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (ACTION_USB_PERMISSION.equals(action)) {
                    UsbDevice device = (UsbDevice) intent.getParcelableExtra(manager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(manager.EXTRA_PERMISSION_GRANTED, false) && (device != null)) {
                        if (device.equals(m_UsbCdcConnectionDetails.m_UsbDevice)) {
                            Log.d(TAG, "GRANTED permission to access USB device");
                            callback.Response(true);
                        } else {
                            Log.w(TAG, "Permission granted to a different USB device");
                        }
                    } else {
                        Log.e(TAG, "DENIED permission to access USB device");
                        callback.Response(false);
                    }
                }
            }
        };
        context.registerReceiver(usbPermissionReceiver, filter);
        m_UsbCdcConnectionDetails.m_usbManager.requestPermission(m_UsbCdcConnectionDetails.m_UsbDevice, permissionIntent);
    }

    public boolean OpenConnection() {
        if(m_bConnectionIsOpened) {
            Log.i(TAG, "Device connection already open");
            return true;
        }

        if(false == OpenConnectionWithTheDevice()){
            Log.e(TAG, "Failed to open device connection");
            return false;
        }

        if(false == GetDeviceInterfaces()){
            Log.e(TAG, "Error reaching the device interfaces");
            return false;
        }


        if(false == ConfigureTheConnection()){
            Log.e(TAG, "Failed to configure device connection");
            return false;
        }

        Log.i(TAG, "Device connection established successfully");
        m_bConnectionIsOpened = true;
        return true;
    }

    public void CloseConnection() {
        if(m_bConnectionIsOpened) {
            m_UsbCdcConnectionDetails.ResetConnectionDetails();
            m_bConnectionIsOpened = false;
        }
    }

    public UsbCdcConnection(){
        InitializeDeviceInformationList();
    }

    private void InitializeDeviceInformationList() {
        m_UsbCdcDeviceInformationList = new LinkedList<>();

        UsbCdcDeviceInformation usbCdcDeviceInformation = new UsbCdcDeviceInformation();
        usbCdcDeviceInformation.m_DeviceVendorId = 10925;
        usbCdcDeviceInformation.m_ProductId = 25462;
        m_UsbCdcDeviceInformationList.add(usbCdcDeviceInformation);

        usbCdcDeviceInformation = new UsbCdcDeviceInformation();
        usbCdcDeviceInformation.m_DeviceVendorId = 10925;
        usbCdcDeviceInformation.m_ProductId = 25459;
        m_UsbCdcDeviceInformationList.add(usbCdcDeviceInformation);
    }

    private boolean ConfigureTheConnection() {
        byte[] configurationBuffer = {
                (byte) ( m_UsbCdcConnectionConfiguration.m_BaudRate & 0xff),
                (byte) ((m_UsbCdcConnectionConfiguration.m_BaudRate >> 8 ) & 0xff),
                (byte) ((m_UsbCdcConnectionConfiguration.m_BaudRate >> 16) & 0xff),
                (byte) ((m_UsbCdcConnectionConfiguration.m_BaudRate >> 24) & 0xff),
                m_UsbCdcConnectionConfiguration.m_StopBits,
                m_UsbCdcConnectionConfiguration.m_Parity,
                (byte) m_UsbCdcConnectionConfiguration.m_DataBits};

        final int SET_LINE_CODING = 0x20;
        final int USB_INTERFACE_REQUEST_TYPE = 0x21;
        // 0x21 --> 100001 --> 3 fields
        //           0                  01              00001
        //           Host to Device     Type == Class   Recipient == Interface

        final int USB_ENDPOINT_REQUEST_TYPE = 0x22;
        // 0x21 --> 100001 --> 3 fields
        //           0                  01              00010
        //           Host to Device     Type == Class   Recipient == Interface

        int returnValue = m_UsbCdcConnectionDetails.m_usbDeviceConnection.controlTransfer(USB_INTERFACE_REQUEST_TYPE, SET_LINE_CODING, 0, m_UsbCdcConnectionDetails.m_ControlInterfaceIndex, configurationBuffer, configurationBuffer.length, 5000);
        if(-1 == returnValue){
            Log.e(TAG, "Failed configure the connection with the device");
            return false;
        }
        return true;
    }

    private boolean OpenConnectionWithTheDevice(){
        UsbDeviceConnection usbDeviceConnection = m_UsbCdcConnectionDetails.m_usbManager.openDevice(m_UsbCdcConnectionDetails.m_UsbDevice);
        if(usbDeviceConnection == null){
            return false;
        }

        m_UsbCdcConnectionDetails.m_usbDeviceConnection = usbDeviceConnection;
        m_UsbCdcConnectionDetails.m_FileDescriptor = usbDeviceConnection.getFileDescriptor();
        return true;
    }

    private boolean GetDeviceInterfaces(){
        if(null == m_UsbCdcConnectionDetails.m_usbDeviceConnection){
            Log.e(TAG, "Null device connection");
            return false;
        }
        if(null == m_UsbCdcConnectionDetails.m_UsbDevice){
            Log.e(TAG, "Null USB device");
            return false;
        }

        UsbInterface controlInterface = null;
        int controlInterfaceIndex = -1;
        int controlEndpointAddress = -1;
        UsbInterface dataInterface = null;
        int readEndpointAddress= -1;
        int writeEndpointAddress= -1;

        for(int interfaceIndex = 0; interfaceIndex < m_UsbCdcConnectionDetails.m_UsbDevice.getInterfaceCount(); ++interfaceIndex) {
            UsbInterface usbInterface = m_UsbCdcConnectionDetails.m_UsbDevice.getInterface(interfaceIndex);

            if (USB_CLASS_COMM == usbInterface.getInterfaceClass()) {
                Log.d(TAG, "This is a control interface");
                controlInterface = usbInterface;
                controlInterfaceIndex = interfaceIndex;
                UsbEndpoint controlEndpoint = usbInterface.getEndpoint(0);
                controlEndpointAddress =  controlEndpoint.getAddress();
                continue;
            }

            if (USB_CLASS_CDC_DATA == usbInterface.getInterfaceClass()) {
                Log.d(TAG, "This is a cdc data interface");
                int numberOfEndpoints = usbInterface.getEndpointCount();
                Log.d(TAG, "This interface has " + numberOfEndpoints + " endpoints");
                for (int endpointIndex = 0; endpointIndex < numberOfEndpoints; ++endpointIndex) {
                    UsbEndpoint usbEndpoint = usbInterface.getEndpoint(endpointIndex);

                    if (USB_ENDPOINT_XFER_BULK == usbEndpoint.getType()) {
                        Log.d(TAG, "This is a BULK endpoint");
                        dataInterface = usbInterface;
                        if (USB_DIR_IN == usbEndpoint.getDirection()) {
                            readEndpointAddress = usbEndpoint.getAddress();
                        }
                        if (USB_DIR_OUT == usbEndpoint.getDirection()) {
                            writeEndpointAddress = usbEndpoint.getAddress();
                        }
                    }
                }
                continue;
            }
        }

        if( readEndpointAddress != -1 &&
            writeEndpointAddress != -1 &&
            controlEndpointAddress != -1) {

            if (false == m_UsbCdcConnectionDetails.m_usbDeviceConnection.claimInterface(controlInterface, true)) {
                Log.e(TAG, "Failed to claim the control interface");
                return false;
            }

            if (false == m_UsbCdcConnectionDetails.m_usbDeviceConnection.claimInterface(dataInterface, true)) {
                Log.e(TAG, "Failed to claim the data interface");
                return false;
            }

            m_UsbCdcConnectionDetails.m_ControlInterface = controlInterface;
            m_UsbCdcConnectionDetails.m_ControlInterfaceIndex = controlInterfaceIndex;
            m_UsbCdcConnectionDetails.m_ControlEndpointAddress = controlEndpointAddress;
            m_UsbCdcConnectionDetails.m_DataInterface = dataInterface;
            m_UsbCdcConnectionDetails.m_ReadEndpointAddress = readEndpointAddress;
            m_UsbCdcConnectionDetails.m_WriteEndpointAddress = writeEndpointAddress;
            return true;
        }

        return false;
    }

    private HashMap<String, UsbDevice> GetUsbDevicesList(Context context){
        HashMap<String, UsbDevice> usbDevicesList = null;

        m_UsbCdcConnectionDetails.m_usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        if(m_UsbCdcConnectionDetails.m_usbManager == null){
            Log.e(TAG, "Error getting the usbManager");
        } else {
            usbDevicesList = m_UsbCdcConnectionDetails.m_usbManager.getDeviceList();
        }

        return usbDevicesList;
    }

    private boolean FindUsbDevice(HashMap<String, UsbDevice> usbDevicesList){
        boolean bDeviceWasFound = false;
        UsbDevice usbDevice = null;

        Iterator<UsbDevice> devicesIterator = usbDevicesList.values().iterator();

        while (false == bDeviceWasFound && devicesIterator.hasNext()) {
            usbDevice = devicesIterator.next();

            for (UsbCdcDeviceInformation usbCdcDeviceInformation : m_UsbCdcDeviceInformationList) {
                if (usbDevice.getVendorId() == usbCdcDeviceInformation.m_DeviceVendorId &&
                        usbDevice.getProductId() == usbCdcDeviceInformation.m_ProductId) {

                    bDeviceWasFound = true;
                    m_UsbCdcConnectionDetails.m_UsbDevice = usbDevice;
                    Log.i(TAG, "The requested USB device was found");
                    PrintDeviceDetails(usbDevice);
                    break;
                }
            }
        }

        return bDeviceWasFound;
    }

    private void PrintDeviceDetails(UsbDevice usbDevice){
        if(null != usbDevice){
            Log.i(TAG, usbDevice.toString());
        }
    }

    private void PrintAllDevicesDetails(HashMap<String, UsbDevice> usbDevicesList){
        Iterator<UsbDevice> devicesIterator = usbDevicesList.values().iterator();

        Log.i(TAG, "The following USB devices were detected");
        while (devicesIterator.hasNext()) {
            UsbDevice usbDevice = devicesIterator.next();
            Log.i(TAG, usbDevice.toString());
        }
    }

    private UsbCdcConnectionConfiguration  m_UsbCdcConnectionConfiguration = new UsbCdcConnectionConfiguration();
    private List<UsbCdcDeviceInformation> m_UsbCdcDeviceInformationList;
    private UsbCdcConnectionDetails m_UsbCdcConnectionDetails = new UsbCdcConnectionDetails();

    private boolean m_bConnectionIsOpened = false;
    private static final String TAG = "UsbCdcConnection";
}

