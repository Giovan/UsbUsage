package com.kairoslabs.dev.barscan;

import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import android.widget.ToggleButton;

public class MainActivity extends AppCompatActivity {
    ToggleButton led;

    private USBAccessoryManager accessoryManager;
    private boolean deviceAttached = false;
    int firmwareProtocol = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        accessoryManager = new USBAccessoryManager(handler, 0);
        led = (ToggleButton)findViewById(R.id.led);
        led.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final byte[] commandPacket = new byte[2];
                if (led.isChecked()) {
                    commandPacket[0] = 0x01; //For LED1 For others use 0x02,0x04,0x08,0x10,0x20,0x40,0x80)
                    commandPacket[1] |= 1;
                    accessoryManager.write(commandPacket);
                } else {
                    commandPacket[0] = 0x01; //For LED1 For others use 0x02,0x04,0x08,0x10,0x20,0x40,0x80)
                    commandPacket[1] |= 0;
                    accessoryManager.write(commandPacket);
                }
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        accessoryManager.enable(this, getIntent());
    }

    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            final byte[] commandPacket = new byte[2];

            switch (msg.what) {
                case 1:
                    if (accessoryManager.isConnected() == false) {
                        return;
                    }

                    commandPacket[0] = 1;
                    commandPacket[1] = 0;

                    accessoryManager.write(commandPacket);
                    break;

                case 0:
                    switch (((USBAccessoryManagerMessage) msg.obj).type) {
                        case READ:
                            if (accessoryManager.isConnected() == false) {
                                return;
                            }

                            while (true) {
                                if (accessoryManager.available() < 2) {
                                    break;
                                }

                                accessoryManager.read(commandPacket);

                                switch (commandPacket[0]) {
                                    case 3: //Potentiometer

                                        break;
                                    case 2: //Push Buttons
                                        runOnUiThread(new Runnable() {
                                            public void run() {
                                                Toast.makeText(MainActivity.this, "Push Button Pressed", Toast.LENGTH_LONG).show();
                                            }
                                        });

                                        break;
                                }

                            }
                            break;
                        case CONNECTED:
                            break;
                        case READY:
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    Toast.makeText(MainActivity.this, "Accessory Connected", Toast.LENGTH_SHORT).show();
                                }
                            });
                            String version = ((USBAccessoryManagerMessage)msg.obj).accessory.getVersion();
                            firmwareProtocol = getFirmwareProtocol(version);
                            switch (firmwareProtocol) {
                                case 1:
                                    deviceAttached = true;
                                    break;
                                case 2:
                                    deviceAttached = true;
                                    commandPacket[0] = (byte) 0xFE;
                                    commandPacket[1] = 0;
                                    accessoryManager.write(commandPacket);
                                    break;
                                default:

                                    break;
                            }
                            break;
                        case DISCONNECTED:
                            if (deviceAttached == false) {
                                return;
                            }
                            break;
                    }

                    break;
                default:
                    break;
            }
        }
    };

    private int getFirmwareProtocol(String version) {
        String major = "0";
        int positionOfDot;
        positionOfDot = version.indexOf('.');
        if(positionOfDot != -1) {
            major = version.substring(0, positionOfDot);
        }
        return Integer.valueOf(major);
        // return new Integer(major).intValue();
    }
}
