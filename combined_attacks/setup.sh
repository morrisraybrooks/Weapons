#!/bin/bash
# Combined Attack Framework Setup

echo "[+] Setting up combined attack framework..."

# Check if running as root
if [ "$(id -u)" -ne 0 ]; then
    echo "[!] This script must be run as root"
    exit 1
fi

# Install dependencies
echo "[+] Installing dependencies..."
apt-get update && apt-get install -y python3 python3-pip

# USB HID setup (from existing setup.sh)
echo "[+] Configuring USB HID device..."

# Enable dwc2 overlay
if ! grep -q "dtoverlay=dwc2" /boot/config.txt; then
    echo "dtoverlay=dwc2" | tee -a /boot/config.txt
fi

# Enable dwc2 module
if ! grep -q "dwc2" /etc/modules; then
    echo "dwc2" | tee -a /etc/modules
fi

# Create gadget configuration
mkdir -p /sys/kernel/config/usb_gadget/combined_attack
cd /sys/kernel/config/usb_gadget/combined_attack

# Set vendor and product IDs
echo 0x1d6b > idVendor  # Linux Foundation
echo 0x0104 > idProduct # Multifunction Composite Gadget

# Set device information
mkdir -p strings/0x409
echo "12345678" > strings/0x409/serialnumber
echo "Offensive Security" > strings/0x409/manufacturer
echo "Combined Attack" > strings/0x409/product

# Create HID function
mkdir -p functions/hid.usb0
echo 1 > functions/hid.usb0/protocol
echo 1 > functions/hid.usb0/subclass
echo 8 > functions/hid.usb0/report_length

# Create HID report descriptor
cat > functions/hid.usb0/report_desc <<HID_REPORT
0x05, 0x01,        // Usage Page (Generic Desktop Ctrls)
0x09, 0x06,        // Usage (Keyboard)
0xA1, 0x01,        // Collection (Application)
0x05, 0x07,        //   Usage Page (Kbrd/Keypad)
0x19, 0xE0,        //   Usage Minimum (0xE0)
0x29, 0xE7,        //   Usage Maximum (0xE7)
0x15, 0x00,        //   Logical Minimum (0)
0x25, 0x01,        //   Logical Maximum (1)
0x75, 0x01,        //   Report Size (1)
0x95, 0x08,        //   Report Count (8)
0x81, 0x02,        //   Input (Data,Var,Abs,No Wrap,Linear,Preferred State,No Null Position)
0x95, 0x01,        //   Report Count (1)
0x75, 0x08,        //   Report Size (8)
0x81, 0x03,        //   Input (Const,Var,Abs,No Wrap,Linear,Preferred State,No Null Position)
0x95, 0x05,        //   Report Count (5)
0x75, 0x01,        //   Report Size (1)
0x05, 0x08,        //   Usage Page (LEDs)
0x19, 0x01,        //   Usage Minimum (Num Lock)
0x29, 0x05,        //   Usage Maximum (Kana)
0x91, 0x02,        //   Output (Data,Var,Abs,No Wrap,Linear,Preferred State,No Null Position,Non-volatile)
0x95, 0x01,        //   Report Count (1)
0x75, 0x03,        //   Report Size (3)
0x91, 0x03,        //   Output (Const,Var,Abs,No Wrap,Linear,Preferred State,No Null Position,Non-volatile)
0x95, 0x06,        //   Report Count (6)
0x75, 0x08,        //   Report Size (8)
0x15, 0x00,        //   Logical Minimum (0)
0x25, 0x65,        //   Logical Maximum (101)
0x05, 0x07,        //   Usage Page (Kbrd/Keypad)
0x19, 0x00,        //   Usage Minimum (0x00)
0x29, 0x65,        //   Usage Maximum (0x65)
0x81, 0x00,        //   Input (Data,Array,Abs,No Wrap,Linear,Preferred State,No Null Position)
0xC0               // End Collection
HID_REPORT

# Create configuration
mkdir -p configs/c.1/strings/0x409
echo "Combined Attack Configuration" > configs/c.1/strings/0x409/configuration
ln -s functions/hid.usb0 configs/c.1/

# Enable gadget
ls /sys/class/udc > UDC

echo "[+] Combined attack framework setup complete"
echo "[+] Connect device to target computer via USB"
