package com.schreiaj.servo2040controller

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.felhr.usbserial.UsbSerialDevice
import com.felhr.usbserial.UsbSerialInterface
import com.felhr.usbserial.UsbSerialInterface.UsbReadCallback
import java.util.*

class MainActivity : AppCompatActivity() {

    lateinit var m_usbManager: UsbManager
    var m_device: UsbDevice? = null
    var m_serial: UsbSerialDevice? = null
    var m_connection: UsbDeviceConnection? = null
    var m_connected = false

    val ACTION_USB_PERMISSION = "permission"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        m_usbManager = getSystemService(Context.USB_SERVICE) as UsbManager

        val filter = IntentFilter()
        filter.addAction(ACTION_USB_PERMISSION)
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        registerReceiver(broadcastReceiver, filter)

        val on_btn: Button = findViewById(R.id.center)
        on_btn.setOnClickListener {
            sendHexData("5302005D0C5D0C")

            Log.i("Serial", "Sending Neutral")
        }

        val off_btn: Button = findViewById(R.id.move_forward)
        off_btn.setOnClickListener {
//            sendData("S 2 0 1800 1800\n")

            sendHexData("53020007080708")
//            sendData("Test")
            Log.i("Serial", "Sending Test")
        }
    }

    private val mCallback = UsbReadCallback {
        // Code here :)
        Log.i("Serial", "Out"+it.toHex())
    }

    private fun startUsbConnecting() {
        val usbDevices: HashMap<String, UsbDevice>? = m_usbManager.deviceList
        if (!usbDevices?.isEmpty()!!) {
            var keep = true
            usbDevices.forEach{ entry ->
                m_device = entry.value
                val deviceVendorId: Int? = m_device?.vendorId
                Log.i("serial", "vendorId: "+deviceVendorId)
                if (deviceVendorId == 0x2E8A) {
                    val intent: PendingIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), 0)
                    m_usbManager.requestPermission(m_device, intent)
                    keep = false
                    Log.i("serial", "connection successful")
                    m_connected = true
                } else {
                    m_connection = null
                    m_device = null
                    Log.i("serial", "unable to connect")
                }
                if (!keep) {
                    return
                }
            }
        } else {
            Log.i("serial", "no usb device connected")
        }
    }

    private fun sendData(input: String) {
        if(!m_connected) {
            startUsbConnecting()
        }
        m_serial?.write(input.toByteArray())
        Log.i("serial", "sending data: "+input.toByteArray().toHex())
    }

    private fun sendHexData(input: String) {
        if(!m_connected) {
            startUsbConnecting()
        }
        m_serial?.write(input.decodeHex());
        Log.i("Serial", "Sending: $input: ${input.decodeHex()}")
    }

    fun String.decodeHex(): ByteArray {
        check(length % 2 == 0) { "Must have an even length" }

        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

    private fun sendData(input: UByteArray) {
        m_serial?.write(input.toByteArray())
    }

    private fun disconnect() {
        m_serial?.close()
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action!! == ACTION_USB_PERMISSION) {
                val granted: Boolean = intent.extras!!.getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED)
                if (granted) {
                    m_connection = m_usbManager.openDevice(m_device)
                    m_serial = UsbSerialDevice.createUsbSerialDevice(m_device, m_connection)
                    if (m_serial != null) {
                        if (m_serial!!.open()) {
                            m_serial!!.setBaudRate(9600)
                            m_serial!!.setDataBits(UsbSerialInterface.DATA_BITS_8)
                            m_serial!!.setStopBits(UsbSerialInterface.STOP_BITS_1)
                            m_serial!!.setParity(UsbSerialInterface.PARITY_NONE)
                            m_serial!!.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF)
                            m_serial!!.setDTR(true)
                            m_serial!!.read(mCallback)
                        } else {
                            Log.i("Serial", "port not open")
                        }
                    } else {
                        Log.i("Serial", "port is null")
                    }
                } else {
                    Log.i("Serial","permission not granted")
                }
            } else if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
                startUsbConnecting()
            } else if (intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                disconnect()
            }
        }
    }

}