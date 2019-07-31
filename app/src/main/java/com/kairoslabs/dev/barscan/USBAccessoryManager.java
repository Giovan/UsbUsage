package com.kairoslabs.dev.barscan;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

public class USBAccessoryManager {

    private String actionString = null;
    private Handler handler;
    private int what;

    private boolean enabled = false;
    private boolean permissionRequested = false;
    private boolean open = false;

    private FileOutputStream outputStream = null;
    private ParcelFileDescriptor parcelFileDescriptor = null;
    private ReadThread readThread = null;
    private ArrayList<byte[]> readData = new ArrayList<byte[]>();
    private String TAG = "MICROCHIP";
    USBAccessoryManager(Handler handler, int what) {
        this.handler = handler;
        this.what = what;
    }

    public enum RETURN_CODES {
        DEVICE_MANAGER_IS_NULL, ACCESSORIES_LIST_IS_EMPTY, FILE_DESCRIPTOR_WOULD_NOT_OPEN, PERMISSION_PENDING, SUCCESS
    }
    void enable(Context context, Intent intent) {
        // Grab the packageName to use for an attach Intent
        actionString = context.getPackageName() + ".action.USB_PERMISSION";
        PendingIntent permissionIntent = PendingIntent.getBroadcast(context, 0,
                new Intent(actionString), 0);
        // If the USB manager isn't already enabled
        if (!enabled) {
            // Create a new filter with the package name (for the accessory
            // attach)

            try {
                Thread.sleep(500);
            } catch (InterruptedException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }

            IntentFilter filter = new IntentFilter(actionString);
            // Also add a few other actions to the intent...
            filter.addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED);
            filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
            // and register the intent with the specified context
            context.registerReceiver(receiver, filter);

            UsbManager deviceManager = null;
            UsbAccessory[] accessories = null;
            UsbAccessory accessory = null;

            // Get a UsbManager object from the specified intent (only works for
            // v3.1+ devices)
            deviceManager = (UsbManager) context
                    .getSystemService(Context.USB_SERVICE);

            // If we were unable to get a UsbManager, return an error
            if (deviceManager == null) {
                return;
            }

            // Get a list of all of the accessories from the UsbManager
            accessories = deviceManager.getAccessoryList();

            // If the list of accessories is empty, then exit
            if (accessories == null) {
                return;
            }

            // Get the first accessory in the list (currently the Android OS
            // only
            // supports one accessory, so this is it)
            accessory = accessories[0];

            // If the accessory isn't null, then let's try to attach to it.
            if (accessory != null) {
                // If we have permission to access the accessory,
                if (deviceManager.hasPermission(accessory)) {
                    // Try to open a ParcelFileDescriptor by opening the
                    // accessory
                    parcelFileDescriptor = deviceManager
                            .openAccessory(accessory);

                    if (parcelFileDescriptor != null) {
                        // Create a new read thread to handle reading data from
                        // the accessory
                        readThread = new ReadThread(parcelFileDescriptor);
                        readThread.start();

                        deviceManager.requestPermission(accessory,
                                permissionIntent);

                        if(parcelFileDescriptor == null) {
                            Log.d(TAG, "USBAccessoryManager:enable() parcelFileDescriptor == null");
                            return;
                        }

                        // Open the output file stream for writing data out to
                        // the accessory
                        outputStream = new FileOutputStream(
                                parcelFileDescriptor.getFileDescriptor());

                        if(outputStream == null) {
                            Log.d(TAG, "USBAccessoryManager:enable() outputStream == null");

                            try {
                                parcelFileDescriptor.close();
                            } catch (IOException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }
                            return;
                        }

                        Log.d(TAG,
                                "USBAccessoryManager:enable() outputStream open");

                        // If the ParcelFileDescriptor was successfully opened,
                        // mark the accessory as enabled and open
                        enabled = true;
                        open = true;

                        handler.obtainMessage(
                                what,
                                new USBAccessoryManagerMessage(
                                        USBAccessoryManagerMessage.MessageType.READY,
                                        accessory)).sendToTarget();

                        Log.d(TAG,
                                "USBAccessoryManager:enable() device ready");

                    }
                } else {
                    /*
                     * If we don't currently have permission to access the
                     * accessory, then we need to request it. If we haven't
                     * requested it already...
                     */
                    if (!permissionRequested) {
                        // Then go ahead and request it...
                        deviceManager.requestPermission(accessory,
                                permissionIntent);

                        permissionRequested = true;
                    }
                }
            }

        }

    }

    /**
     * Disables the USB manager and releases all resources
     *
     * @param context
     *            The context that the manager was enabled with
     */
    public void disable(Context context) {
        // Free up all of the required resources
        closeAccessory();

        // Unregister the broadcast receiver
        try {
            context.unregisterReceiver(receiver);
        } catch (Exception ignored) {
        }
    }

    /**
     * Describes if an accessory is attached or not
     *
     * @return boolean - true if one is attached, false otherwise
     */
    boolean isConnected() {
        return open;
    }


    void ignore(int num) {
        int amountRead = 0;

        // If the accessory is not connected, then we can't really do anything
        if (!isConnected()) {
            // throw new USBAccessoryManagerException(
            // USB_ACCESSORY_NOT_CONNECTED );
            return;
        }

        // Must request to ignore 1 or more bytes, otherwise do nothing
        if (num <= 0) {
            return;
        }

        // synchronizing to the readData object so that the ReadThread and this
        // don't try to access the readData object at the same time. This will
        // block whoever tries to access it second until it is available.
        synchronized (readData) {

            while (true) {
                // If there are no more data objects in the list, exit
                if (readData.size() == 0) {
                    return;
                }

                // If we have ignored the requested amount of data from the
                // buffer, then exit
                if (amountRead == num) {
                    return;
                }

                if ((num - amountRead) >= readData.get(0).length) {
                    // We need to ignore equal to or greater than the size of
                    // data in this entry

                    // then ignore the whole size of the entry
                    amountRead += readData.get(0).length;

                    // and remove it from the list.
                    readData.remove(0);
                } else {
                    // We need to ignore only a portion of the data in this
                    // entry

                    // only remove a portion of the entry, leaving part of the
                    // data
                    int amountRemoved = num - amountRead;
                    byte[] newData = new byte[readData.get(0).length
                            - amountRemoved];

                    // Copy the remaining data in to a new buffer
                    /* for (int i = 0; i < newData.length; i++) {
                        newData[i] = readData.get(0)[i + amountRemoved];
                    } */
                    System.arraycopy(readData.get(0), 0 + amountRemoved, newData, 0, newData.length);

                    // remove the old entry
                    readData.remove(0);

                    // add a new entry to the front of the buffer with the
                    // remaining data
                    readData.add(0, newData);

                    // since we have now read all of the data that we need, exit
                    return;
                }
            }
        }
    }


    int peek(byte[] array) {
        int amountRead = 0;
        int currentNode = 0;

        // If an accessory is not connected, this request is invalid
        if (!isConnected()) {
            // throw new USBAccessoryManagerException(
            // USB_ACCESSORY_NOT_CONNECTED );
            return 0;
        }

        // If there is no data available, then return 0 bytes read
        if (array.length == 0) {
            return 0;
        }


        synchronized (readData) {
            /* While we still have data to read */
            while (true) {
                /*
                 * if we have run out of list objects to peek from... (no data
                 * left to peek)
                 */
                if (currentNode >= readData.size()) {
                    /* then return the amount that we have read */
                    return amountRead;
                }

                /*
                 * if the amount that we have read exactly matches the amount
                 * that we need
                 */
                if (amountRead == array.length) {
                    return amountRead;
                }

                if ((array.length - amountRead) >= readData.get(currentNode).length) {
                    // If the amount we need to read is larger than or equal to
                    // the data buffer for this node
                    for (int i = 0; i < readData.get(currentNode).length; i++) {
                        array[amountRead++] = readData.get(currentNode)[i];
                    }
                    currentNode++;
                } else {
                    // If the amount we need to read is less than the data
                    // buffer for this node
                    for (int i = 0; i < (array.length - amountRead); i++) {
                        array[amountRead++] = readData.get(currentNode)[i];
                    }

                    return amountRead;
                }
            }
        }
    }

    int available() {
        int amount = 0;

        // If the accessory is not connected, then this request is invalid
        if (isConnected() == false) {
            // throw new USBAccessoryManagerException(
            // USB_ACCESSORY_NOT_CONNECTED );
            return 0;
        }

        synchronized (readData) {
            for (byte[] b : readData) {
                amount += b.length;
            }
        }

        return amount;
    }

    void read(byte[] array) {
        int amountRead = 0;

        /* If an accessory is not connected, this request is not valid */
        if (!isConnected()) {
            // throw new USBAccessoryManagerException(
            // USB_ACCESSORY_NOT_CONNECTED );
            return;
        }

        /* if there is no data to read, return 0 instead of blocking */
        if (array.length == 0) {
            return;
        }

        synchronized (readData) {
            /* while we still have data to read */
            while (true) {
                /*
                 * if there is no data left in the list, exit with the amount
                 * read already
                 */
                if (readData.size() == 0) {
                    return;
                }

                /*
                 * if we have read all of the data that we can store in the
                 * buffer provided, then exit.
                 */
                if (amountRead == array.length) {
                    return;
                }

                if ((array.length - amountRead) >= readData.get(0).length) {
                    /*
                     * If the amount we need to read is larger than or equal to
                     * the data buffer for this node, then copy what is here and
                     * remove it from the list
                     */
                    for (int i = 0; i < readData.get(0).length; i++) {
                        array[amountRead++] = readData.get(0)[i];
                    }
                    readData.remove(0);
                } else {
                    // If the amount we need to read is less than the data
                    // buffer for this node
                    int amountRemoved = 0;

                    /* then copy what we need */
                    for (int i = 0; i < (array.length - amountRead); i++) {
                        array[amountRead++] = readData.get(0)[i];
                        amountRemoved++;
                    }

                    /* create a new buffer the size of the remaining data */
                    byte[] newData = new byte[readData.get(0).length
                            - amountRemoved];

                    /* copy the remaining data to that buffer */
                    for (int i = 0; i < newData.length; i++) {
                        newData[i] = readData.get(0)[i + amountRemoved];
                    }

                    /*
                     * remove the old object and add a new object with the
                     * remaining data
                     */
                    readData.remove(0);
                    readData.add(0, newData);

                    return;
                }
            }
        }
    }

    /**
     * Writes data to the accessory
     *
     * @param data
     *            the data to write
     * @throws *InterruptedException
     */
    public void write(byte[] data) {
        int tries = 2;

        if (isConnected() == true) {
            if (outputStream != null) {

                while (tries-- > 0) {
                    try {
                        outputStream.write(data);
                        return;
                    } catch (IOException e) {
                        Log.d(TAG,
                                "USBAccessoryManager:write():IOException: "
                                        + e.toString());
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e1) {
                            // TODO Auto-generated catch block
                            e1.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    public void writeString(String data) {
        int tries = 2;

        if (isConnected()) {
            if (outputStream != null) {
                while (tries-- > 0) {
                    try {
                        PrintWriter out = new PrintWriter(outputStream);
                        out.println(data);
                        return;
                    } catch (Exception e) {
                        Log.d(TAG,
                                "USBAccessoryManager:write():IOException: "
                                        + e.toString());
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e1) {
                            // TODO Auto-generated catch block
                            e1.printStackTrace();
                        }
                    }
                }
            }
        }
    }
    // Create a BroadcastReceiver for Bluetooth events
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            /* get the action for this event */
            String action = intent.getAction();

            /*
             * if it corresponds to the packageName, then it was a permissions
             * grant request
             */
            if (actionString.equals(action)) {
                /* see if we got permission */
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,
                        false)) {
                    /* if so, then try to open the accessory */
                    UsbManager deviceManager = null;
                    UsbAccessory accessory = null;

                    deviceManager = (UsbManager) context
                            .getSystemService(Context.USB_SERVICE);

                    if (deviceManager == null) {
                        // TODO: error. report to user?
                        return;
                    }

                    accessory = (UsbAccessory) intent
                            .getParcelableExtra(UsbManager.EXTRA_ACCESSORY);

                    parcelFileDescriptor = deviceManager
                            .openAccessory(accessory);

                    if (parcelFileDescriptor != null) {
                        enabled = true;
                        open = true;

                        outputStream = new FileOutputStream(
                                parcelFileDescriptor.getFileDescriptor());

                        readThread = new ReadThread(parcelFileDescriptor);
                        readThread.start();

                        Log.d(TAG,
                                "USBAccessoryManager:BroadcastReceiver()-1");
                        handler.obtainMessage(
                                what,
                                new USBAccessoryManagerMessage(
                                        USBAccessoryManagerMessage.MessageType.READY,
                                        accessory)).sendToTarget();
                    } else {
                        // TODO: error. report to user?
                        return;
                    }
                }
            }

            if (UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(action)) {
                /*
                 * if it was a device attach notice, then try to open the
                 * accessory
                 */
                UsbManager deviceManager = null;
                UsbAccessory[] accessories = null;
                UsbAccessory accessory = null;

                deviceManager = (UsbManager) context
                        .getSystemService(Context.USB_SERVICE);

                if (deviceManager == null) {
                    // TODO: error. report to user?
                    return;
                }

                accessories = deviceManager.getAccessoryList();

                if (accessories == null) {
                    // TODO: error. report to user?
                    return;
                }

                accessory = accessories[0];

                parcelFileDescriptor = deviceManager.openAccessory(accessory);

                if (parcelFileDescriptor != null) {
                    enabled = true;
                    open = true;

                    outputStream = new FileOutputStream(
                            parcelFileDescriptor.getFileDescriptor());

                    readThread = new ReadThread(parcelFileDescriptor);
                    readThread.start();

                    Log.d(TAG, "USBAccessoryManager:BroadcastReceiver()-2");
                    handler.obtainMessage(
                            what,
                            new USBAccessoryManagerMessage(
                                    USBAccessoryManagerMessage.MessageType.READY,
                                    accessory)).sendToTarget();
                } else {
                    // TODO: error. report to user?
                    return;
                }
            } else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
                /*
                 * if it was a detach notice, then close the accessory and
                 * notify the user
                 */
                closeAccessory();
                handler.obtainMessage(
                        what,
                        new USBAccessoryManagerMessage(
                                USBAccessoryManagerMessage.MessageType.DISCONNECTED))
                        .sendToTarget();
            } else if (UsbManager.EXTRA_PERMISSION_GRANTED.equals(action)) {

            }
        }

    };

    /**
     * Closes the accessory and cleans up all lose ends
     *
     */
    private void closeAccessory() {

        if (open == false) {
            return;
        }

        open = false;
        enabled = false;
        permissionRequested = false;

        if (readThread != null) {
            readThread.cancel();
        }

        if (outputStream != null) {
            try {
                outputStream.close();
            } catch (IOException e) {
            }
        }

        if (parcelFileDescriptor != null) {
            try {
                parcelFileDescriptor.close();
            } catch (IOException e) {
            }
        }

        outputStream = null;
        // readThread = null;
        parcelFileDescriptor = null;
    }

    public boolean isClosed() {
        boolean isAlive;

        if (readThread != null) {
            isAlive = readThread.isAlive();

            if (isAlive == false) {
                readThread = null;
            }

            return isAlive;
        }

        return true;
    }

    /**
     * The thread for reading data from the FileInputStream so the main thread
     * doesn't get blocked.
     */
    private class ReadThread extends Thread {
        private boolean continueRunning = true;

        private FileInputStream inputStream;
        private ParcelFileDescriptor myparcelFileDescriptor;

        public ReadThread(ParcelFileDescriptor p) {
            myparcelFileDescriptor = p;
            inputStream = new FileInputStream(p.getFileDescriptor());
        }

        @Override
        public void run() {
            byte[] buffer = new byte[1024]; // buffer store for the stream
            int bytes; // bytes returned from read()

            while (continueRunning) {
                try {
                    // Read from the InputStream
                    bytes = inputStream.read(buffer);

                    // Send the obtained bytes to the UI Activity
                    byte[] data = new byte[bytes];
                    System.arraycopy(buffer, 0, data, 0, bytes);

                    /*
                     * Synchronize to the readData object to make sure that no
                     * user API is accessing it at the moment
                     */
                    synchronized (readData) {
                        readData.add(data);
                    }

                    handler.obtainMessage(
                            what,
                            bytes,
                            -1,
                            new USBAccessoryManagerMessage(
                                    USBAccessoryManagerMessage.MessageType.READ,
                                    data)).sendToTarget();
                } catch (IOException e) {
                    break;
                }
            }
        }

        public void cancel() {
            continueRunning = false;
            try {
                inputStream.close();
            } catch (IOException e) {
            }

            try {
                myparcelFileDescriptor.close();
            } catch (IOException e) {
            }
        }

    }


    class USBAccessoryManagerException extends RuntimeException {
        private static final long serialVersionUID = 2329617898883120248L;
        String errorMessage;

        public USBAccessoryManagerException(String message) {
            errorMessage = message;
        }

        public String toString() {
            return errorMessage;
        }
    }
}