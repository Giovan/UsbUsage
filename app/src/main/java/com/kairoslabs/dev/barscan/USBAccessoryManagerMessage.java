package com.kairoslabs.dev.barscan;

import android.hardware.usb.UsbAccessory;

public class USBAccessoryManagerMessage {
    /* Types of messages that can be sent */
    public enum MessageType {
        READ,
        ERROR,
        CONNECTED,
        DISCONNECTED,
        READY
    };
    /* The MessageType for this message instance */
    public MessageType type;
    /* Any text information that needs to be sent with data */
    public String text = null;
    /* Data send in the read MessageType */
    public byte[] data = null;
    /* A USB accessory that attached */
    public UsbAccessory accessory = null;

    public USBAccessoryManagerMessage(MessageType type) {
        this.type = type;
    }

    public USBAccessoryManagerMessage(MessageType type, byte[] data) {
        this.type = type;
        this.data = data;
    }

    public USBAccessoryManagerMessage(MessageType type, byte[] data, UsbAccessory accessory) {
        this.type = type;
        this.data = data;
        this.accessory = accessory;
    }

    public USBAccessoryManagerMessage(MessageType type, UsbAccessory accessory) {
        this.type = type;
        this.accessory = accessory;
    }
}