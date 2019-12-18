package com.example.wellingtoncesar.chatblue

import android.app.Activity
import android.app.Dialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.support.design.widget.NavigationView
import android.support.v4.view.GravityCompat
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.app_bar_main.*
import java.util.ArrayList

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    private var status: TextView? = null
    private var btnConnect: Button? = null
    private var listView: ListView? = null
    private var dialog: Dialog? = null
    private var menssagem: TextView? = null
    private var chatAdapter: ArrayAdapter<String>? = null
    private var chatMessages: ArrayList<String>? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var chatController: ChatController? = null
    private var connectingDevice: BluetoothDevice? = null
    private var discoveredDevicesAdapter: ArrayAdapter<String>? = null

    private val handler = Handler(Handler.Callback { msg ->
        when (msg.what) {
            MESSAGE_STATE_CHANGE -> when (msg.arg1) {
                chatController!!.STATE_CONNECTED -> {
                    setStatus("Conectado ao: " + connectingDevice!!.name)
                    btnConnect!!.isEnabled = false
                }
                chatController!!.STATE_CONNECTING -> {
                    setStatus("Conectando...")
                    btnConnect!!.isEnabled = false
                }
                chatController!!.STATE_LISTEN, chatController!!.STATE_NONE -> setStatus("Não Conectado")
            }
            MESSAGE_WRITE -> {
                val writeBuf = msg.obj as ByteArray

                val writeMessage = String(writeBuf)
                chatMessages!!.add("Eu: $writeMessage")
                chatAdapter!!.notifyDataSetChanged()
            }
            MESSAGE_READ -> {
                val readBuf = msg.obj as ByteArray

                val readMessage = String(readBuf, 0, msg.arg1)
                chatMessages!!.add(connectingDevice!!.name + ":  " + readMessage)
                chatAdapter!!.notifyDataSetChanged()
            }
            MESSAGE_DEVICE_OBJECT -> {
                connectingDevice = msg.data.getParcelable(DEVICE_OBJECT)
                Toast.makeText(
                    applicationContext, "Conectado ao  " + connectingDevice!!.name,
                    Toast.LENGTH_SHORT
                ).show()
            }
            MESSAGE_TOAST -> Toast.makeText(
                applicationContext, msg.data.getString("toast"),
                Toast.LENGTH_SHORT
            ).show()
        }
        false
    })

    private val discoveryFinishReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action

            if (BluetoothDevice.ACTION_FOUND == action) {
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                if (device.bondState != BluetoothDevice.BOND_BONDED) {
                    discoveredDevicesAdapter!!.add(device.name + "\n" + device.address)
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED == action) {
                if (discoveredDevicesAdapter!!.count == 0) {
                    discoveredDevicesAdapter!!.add(getString(R.string.none_found))
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)


        val toggle = ActionBarDrawerToggle(
            this, drawer_layout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawer_layout.addDrawerListener(toggle)
        toggle.syncState()

        nav_view.setNavigationItemSelectedListener(this)

        findViewsByIds()

        //verificar se o dispositivo tem bluetooth
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth não está disponível!", Toast.LENGTH_SHORT).show()
            finish()
        }

        btnConnect!!.setOnClickListener{showPrinterPickDialog()}

        //setar o chat adapter
        chatMessages = ArrayList()
        chatAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, chatMessages!!)
        listView!!.adapter = chatAdapter

    }

    private fun showPrinterPickDialog() {
        dialog = Dialog(this)
        dialog!!.setContentView(R.layout.layout_bluetooth)
        dialog!!.setTitle("Bluetooth Devices")

        if (bluetoothAdapter!!.isDiscovering) {
            bluetoothAdapter!!.cancelDiscovery()
        }
        bluetoothAdapter!!.startDiscovery()

        //Inicialiar o adapter
        val pairedDevicesAdapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1)
        discoveredDevicesAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1)

        //setar a lista no adapter
        val listView = dialog!!.findViewById<View>(R.id.pairedDeviceList) as ListView
        val listView2 = dialog!!.findViewById<View>(R.id.discoveredDeviceList) as ListView
        listView.adapter = pairedDevicesAdapter
        listView2.adapter = discoveredDevicesAdapter

        // Registrar a transmissão quando um dispositivo é descoberto
        var filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(discoveryFinishReceiver, filter)

        // Registrar a transmissão quando um dispositivo é finalizado
        filter = IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        registerReceiver(discoveryFinishReceiver, filter)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val pairedDevices = bluetoothAdapter!!.bondedDevices

        // If there are paired devices, add each one to the ArrayAdapter
        if (pairedDevices.size > 0) {
            for (device in pairedDevices) {
                pairedDevicesAdapter.add(device.name + "\n" + device.address)
            }
        } else {
            pairedDevicesAdapter.add(getString(R.string.none_paired))
        }

        //manipular o listview
        listView.onItemClickListener = AdapterView.OnItemClickListener { parent, view, position, id ->
            bluetoothAdapter!!.cancelDiscovery()
            val info = (view as TextView).text.toString()
            val address = info.substring(info.length - 17)

            connectToDevice(address)
            dialog!!.dismiss()
        }

        listView2.onItemClickListener = AdapterView.OnItemClickListener { adapterView, view, i, l ->
            bluetoothAdapter!!.cancelDiscovery()
            val info = (view as TextView).text.toString()
            val address = info.substring(info.length - 17)

            connectToDevice(address)
            dialog!!.dismiss()
        }

        dialog!!.findViewById<View>(R.id.cancelButton).setOnClickListener { dialog!!.dismiss() }
        dialog!!.setCancelable(false)
        dialog!!.show()
    }

    private fun setStatus(s: String) {
        status!!.text = s
    }

    private fun connectToDevice(deviceAddress: String) {
        bluetoothAdapter!!.cancelDiscovery()
        val device = bluetoothAdapter!!.getRemoteDevice(deviceAddress)
        chatController!!.connect(device)
    }

    private fun findViewsByIds() {
        status = findViewById<View>(R.id.status) as TextView
        btnConnect = findViewById<View>(R.id.btn_Conectar) as Button
        listView = findViewById<View>(R.id.list) as ListView
        menssagem = findViewById<View>(R.id.edit_mensagem) as TextView
        //inputLayout = (TextInputLayout) findViewById(R.id.input_layout);
        val btnEnviar = findViewById<View>(R.id.btn_Enviar)

        btnEnviar.setOnClickListener {
            if (menssagem!!.text.toString() == "") {
                Toast.makeText(this@MainActivity, "Por favor insira algum texto", Toast.LENGTH_SHORT).show()
            } else {
                //TODO: here
                sendMessage(menssagem!!.text.toString())
                menssagem!!.text = ""
            }
        }
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_ENABLE_BLUETOOTH -> if (resultCode == Activity.RESULT_OK) {
                chatController = ChatController (this, handler)
            } else {
                Toast.makeText(this, "Bluetooth ainda desativado", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun sendMessage(message: String) {
        if (chatController!!.getState() !== chatController!!.STATE_CONNECTED) {
            Toast.makeText(this, "A conexão foi perdida", Toast.LENGTH_SHORT).show()
            return
        }

        if (message.length > 0) {
            val send = message.toByteArray()
            chatController!!.write(send)
        }
    }

    public override fun onStart() {
        super.onStart()
        if (!bluetoothAdapter!!.isEnabled) {
            val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableIntent, REQUEST_ENABLE_BLUETOOTH)
        } else {
            chatController = ChatController(this,handler)
        }
    }

    public override fun onResume() {
        super.onResume()

        if (chatController != null) {
            if (chatController!!.getState() === chatController?.STATE_NONE) {
                chatController!!.start()
            }
        }
    }

    public override fun onDestroy() {
        super.onDestroy()
        if (chatController != null)
            chatController!!.stop()
    }
    //variaveis para ser usada em outra classe
    companion object {

        val MESSAGE_STATE_CHANGE = 1
        val MESSAGE_READ = 2
        val MESSAGE_WRITE = 3
        var MESSAGE_DEVICE_OBJECT = 4
        val MESSAGE_TOAST = 5
        val DEVICE_OBJECT = "nome do dispositivo"

        private val REQUEST_ENABLE_BLUETOOTH = 1
    }

    override fun onBackPressed() {
        if (drawer_layout.isDrawerOpen(GravityCompat.START)) {
            drawer_layout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        when (item.itemId) {
            R.id.action_settings -> return true
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation view item clicks here.
        when (item.itemId) {
            R.id.nav_conexao -> {
                showPrinterPickDialog()
            }

        }

        drawer_layout.closeDrawer(GravityCompat.START)
        return true
    }
}
