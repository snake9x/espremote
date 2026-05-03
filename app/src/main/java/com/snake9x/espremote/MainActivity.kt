package com.snake9x.espremote

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.roundToInt

@SuppressLint("MissingPermission", "ClickableViewAccessibility")
class MainActivity : AppCompatActivity() {

    private lateinit var ble: BLEKeyboardBridge
    private lateinit var tvStatus: TextView
    private lateinit var tvLog: TextView
    private lateinit var etInput: EditText
    private lateinit var btnConnect: Button
    private lateinit var tabKeyboard: TextView
    private lateinit var tabTrackpad: TextView
    private lateinit var tabHotkeys: TextView
    private lateinit var panelKeyboard: View
    private lateinit var panelTrackpad: View
    private lateinit var panelHotkeys: View
    private lateinit var trackpadView: View
    private lateinit var btnMouseLeft: Button
    private lateinit var btnMouseRight: Button
    private lateinit var btnScrollUp: Button
    private lateinit var btnScrollDown: Button
    private lateinit var btnLangSwitch: Button
    private lateinit var tvLayoutHint: TextView

    private var lastSentText = ""
    private var currentLayout: KeyboardLayout = LayoutEN

    // Trackpad state
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var isTap = false
    private val TAP_SLOP = 10f
    private val TAP_MAX_MS = 200L
    private var touchDownTime = 0L
    private var sensitivity = 1.5f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()

        ble = BLEKeyboardBridge(this)
        ble.onStatusChanged = { _, msg -> tvStatus.text = msg; log(msg) }

        setupTabs()
        setupInputField()
        setupKeyboardButtons()
        setupLangSwitcher()
        setupTrackpad()
        setupHotkeyButtons()

        if (hasPermissions()) startBle() else requestPermissions()
        btnConnect.setOnClickListener { if (hasPermissions()) startBle() else requestPermissions() }
    }

    private fun bindViews() {
        tvStatus      = findViewById(R.id.tvStatus)
        tvLog         = findViewById(R.id.tvLog)
        etInput       = findViewById(R.id.etInput)
        btnConnect    = findViewById(R.id.btnConnect)
        tabKeyboard   = findViewById(R.id.tabKeyboard)
        tabTrackpad   = findViewById(R.id.tabTrackpad)
        tabHotkeys    = findViewById(R.id.tabHotkeys)
        panelKeyboard = findViewById(R.id.panelKeyboard)
        panelTrackpad = findViewById(R.id.panelTrackpad)
        panelHotkeys  = findViewById(R.id.panelHotkeys)
        trackpadView  = findViewById(R.id.trackpadView)
        btnMouseLeft  = findViewById(R.id.btnMouseLeft)
        btnMouseRight = findViewById(R.id.btnMouseRight)
        btnScrollUp   = findViewById(R.id.btnScrollUp)
        btnScrollDown = findViewById(R.id.btnScrollDown)
        btnLangSwitch = findViewById(R.id.btnLangSwitch)
        tvLayoutHint  = findViewById(R.id.tvLayoutHint)
    }

    // ── Tabs ─────────────────────────────────────────────────

    private fun setupTabs() {
        showTab(0)
        tabKeyboard.setOnClickListener { showTab(0) }
        tabTrackpad.setOnClickListener { showTab(1) }
        tabHotkeys.setOnClickListener  { showTab(2) }
    }

    private fun showTab(idx: Int) {
        panelKeyboard.visibility = if (idx == 0) View.VISIBLE else View.GONE
        panelTrackpad.visibility = if (idx == 1) View.VISIBLE else View.GONE
        panelHotkeys.visibility  = if (idx == 2) View.VISIBLE else View.GONE
        val active = "#e94560"; val inactive = "#333355"
        tabKeyboard.setBackgroundColor(android.graphics.Color.parseColor(if (idx == 0) active else inactive))
        tabTrackpad.setBackgroundColor(android.graphics.Color.parseColor(if (idx == 1) active else inactive))
        tabHotkeys.setBackgroundColor (android.graphics.Color.parseColor(if (idx == 2) active else inactive))
    }

    // ── Language switcher ────────────────────────────────────

    private fun setupLangSwitcher() {
        updateLangButton()
        btnLangSwitch.setOnClickListener {
            // Циклически переключаем раскладку
            val idx = HID.layouts.indexOf(currentLayout)
            currentLayout = HID.layouts[(idx + 1) % HID.layouts.size]
            updateLangButton()
            // Очищаем поле — старый текст мог быть в другой раскладке
            etInput.setText("")
            lastSentText = ""
            log("Layout: ${currentLayout.flag} ${currentLayout.name}")
        }
    }

    private fun updateLangButton() {
        btnLangSwitch.text = "${currentLayout.flag} ${currentLayout.name}"
        tvLayoutHint.text = when (currentLayout.name) {
            "RU" -> "⚠ Switch PC to RU layout first (Win+Space)"
            else -> ""
        }
        tvLayoutHint.visibility = if (currentLayout.name == "EN") View.GONE else View.VISIBLE
    }

    // ── Keyboard input ───────────────────────────────────────

    private fun setupInputField() {
        etInput.setRawInputType(
            android.text.InputType.TYPE_CLASS_TEXT or
            android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
            android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        )
        etInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val newText = s?.toString() ?: ""
                handleTextDiff(lastSentText, newText)
                lastSentText = newText
            }
        })
    }

    private fun handleTextDiff(old: String, new: String) {
        if (ble.status != BleStatus.READY) return
        val commonLen = old.zip(new).takeWhile { (a, b) -> a == b }.count()
        val deletions = old.length - commonLen
        val additions = new.substring(commonLen)
        if (deletions > 0) {
            ble.sendKeyTaps(List(deletions) { Pair(0x00.toByte(), HID.KEY_BACKSPACE) })
            log("⌫ ×$deletions")
        }
        if (additions.isNotEmpty()) {
            val taps = mutableListOf<Pair<Byte, Byte>>()
            val unmapped = mutableListOf<Char>()
            for (ch in additions) {
                val cmd = currentLayout.mapChar(ch)
                if (cmd != null) taps.add(Pair(cmd.modifiers, cmd.keycode))
                else unmapped.add(ch)
            }
            if (taps.isNotEmpty()) {
                ble.sendKeyTaps(taps)
                log("→ \"$additions\"")
            }
            if (unmapped.isNotEmpty()) {
                log("? Unmapped: ${unmapped.joinToString("")}")
            }
        }
    }

    private fun setupKeyboardButtons() {
        fun btn(id: Int, kc: Byte, label: String, mod: Byte = 0x00) =
            findViewById<Button>(id).setOnClickListener { tap(kc, mod, label) }
        btn(R.id.btnEnter,     HID.KEY_ENTER,       "Enter")
        btn(R.id.btnBackspace, HID.KEY_BACKSPACE,    "⌫")
        btn(R.id.btnTab,       HID.KEY_TAB,          "Tab")
        btn(R.id.btnEsc,       HID.KEY_ESCAPE,       "Esc")
        btn(R.id.btnUp,        HID.KEY_UP_ARROW,     "↑")
        btn(R.id.btnDown,      HID.KEY_DOWN_ARROW,   "↓")
        btn(R.id.btnLeft,      HID.KEY_LEFT_ARROW,   "←")
        btn(R.id.btnRight,     HID.KEY_RIGHT_ARROW,  "→")
        btn(R.id.btnDelete,    HID.KEY_DELETE,       "Del")
        btn(R.id.btnHome,      HID.KEY_HOME,         "Home")
        btn(R.id.btnEnd,       HID.KEY_END,          "End")
        btn(R.id.btnPageUp,    HID.KEY_PAGE_UP,      "PgUp")
        btn(R.id.btnPageDown,  HID.KEY_PAGE_DOWN,    "PgDn")
    }

    // ── Trackpad ─────────────────────────────────────────────

    private fun setupTrackpad() {
        trackpadView.setOnTouchListener { _, event ->
            if (ble.status == BleStatus.READY) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        lastTouchX = event.x; lastTouchY = event.y
                        touchStartX = event.x; touchStartY = event.y
                        touchDownTime = System.currentTimeMillis(); isTap = true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.x - lastTouchX) * sensitivity
                        val dy = (event.y - lastTouchY) * sensitivity
                        if (abs(dx) > 0.5f || abs(dy) > 0.5f) {
                            ble.sendMouseMove(
                                dx.roundToInt().coerceIn(-127, 127).toByte(),
                                dy.roundToInt().coerceIn(-127, 127).toByte()
                            )
                            lastTouchX = event.x; lastTouchY = event.y
                        }
                        if (abs(event.x - touchStartX) > TAP_SLOP || abs(event.y - touchStartY) > TAP_SLOP) isTap = false
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isTap && System.currentTimeMillis() - touchDownTime < TAP_MAX_MS) {
                            ble.sendMouseClick(0x01); log("🖱 Tap")
                        }
                    }
                }
            }
            true
        }
        btnMouseLeft.setOnClickListener  { ble.sendMouseClick(0x01); log("🖱 Left") }
        btnMouseRight.setOnClickListener { ble.sendMouseClick(0x02); log("🖱 Right") }
        btnScrollUp.setOnClickListener   { repeat(3) { ble.sendMouseScroll(0, 1) };             log("⬆ Scroll") }
        btnScrollDown.setOnClickListener { repeat(3) { ble.sendMouseScroll(0, (-1).toByte()) }; log("⬇ Scroll") }
        findViewById<SeekBar>(R.id.sbSensitivity).apply {
            max = 40; progress = 15
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, u: Boolean) { sensitivity = 0.5f + p * 0.1f }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
    }

    // ── Hotkeys ──────────────────────────────────────────────

    private fun setupHotkeyButtons() {
        val CTRL  = HID.MOD_LEFT_CTRL.toInt()
        val SHIFT = HID.MOD_LEFT_SHIFT.toInt()
        val ALT   = HID.MOD_LEFT_ALT.toInt()
        val GUI   = HID.MOD_LEFT_GUI.toInt()

        hk(R.id.btnCopy,         CTRL,              0x06, "Copy")
        hk(R.id.btnPaste,        CTRL,              0x19, "Paste")
        hk(R.id.btnCut,          CTRL,              0x1B, "Cut")
        hk(R.id.btnUndo,         CTRL,              0x1D, "Undo")
        hk(R.id.btnRedo,         CTRL or SHIFT,     0x1D, "Redo")
        hk(R.id.btnSelectAll,    CTRL,              0x04, "Select All")
        hk(R.id.btnFind,         CTRL,              0x09, "Find")
        hk(R.id.btnSave,         CTRL,              0x16, "Save")

        hk(R.id.btnWinD,         GUI,               0x07, "Win+D")
        hk(R.id.btnAltTab,       ALT,               0x2B, "Alt+Tab")
        hk(R.id.btnAltF4,        ALT,               0x3D, "Alt+F4")
        hk(R.id.btnWinL,         GUI,               0x0F, "Win+L")
        hk(R.id.btnWinE,         GUI,               0x08, "Win+E")
        hk(R.id.btnWinR,         GUI,               0x15, "Win+R")
        hk(R.id.btnWinTab,       GUI,               0x2B, "Win+Tab")
        hk(R.id.btnWinLeft,      GUI,               0x50, "Win+←")
        hk(R.id.btnWinRight,     GUI,               0x4F, "Win+→")

        hk(R.id.btnCtrlAltDel,  CTRL or ALT,       0x4C, "Ctrl+Alt+Del")
        hk(R.id.btnTaskMgr,     CTRL or SHIFT,      0x29, "Task Manager")
        hk(R.id.btnWinX,        GUI,                0x1B, "Win+X")
        hk(R.id.btnPrintScreen, 0,                  0x46, "PrintScreen")
        hk(R.id.btnTerminal,    CTRL or ALT,        0x17, "Terminal")
        hk(R.id.btnCtrlShiftN,  CTRL or SHIFT,      0x11, "Ctrl+Shift+N")

        // Win+Space — переключение языка на ПК (используется с кнопкой языка)
        hk(R.id.btnWinSpace,    GUI,                0x2C, "Win+Space (Lang)")

        hk(R.id.btnMediaPlay, 0, 0xCD.toByte(), "Play/Pause")
        hk(R.id.btnMediaNext, 0, 0xB5.toByte(), "Next Track")
        hk(R.id.btnMediaPrev, 0, 0xB6.toByte(), "Prev Track")
        hk(R.id.btnVolUp,     0, 0xE9.toByte(), "Vol+")
        hk(R.id.btnVolDown,   0, 0xEA.toByte(), "Vol-")
        hk(R.id.btnMute,      0, 0xE2.toByte(), "Mute")
    }

    private fun hk(id: Int, mod: Byte, kc: Byte, label: String) =
        findViewById<Button>(id).setOnClickListener { tap(kc, mod, label) }
    private fun hk(id: Int, mod: Int, kc: Byte, label: String) = hk(id, mod.toByte(), kc, label)

    private fun tap(keycode: Byte, modifiers: Byte, label: String) {
        if (ble.status != BleStatus.READY) { log("Not connected"); return }
        ble.sendKeyTap(modifiers, keycode)
        log("⌨ $label")
    }

    private fun startBle() { ble.disconnect(); ble.start() }

    private fun log(msg: String) {
        val lines = tvLog.text.toString().lines().takeLast(12)
        tvLog.text = (lines + msg).joinToString("\n")
    }

    private fun hasPermissions(): Boolean {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        return perms.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun requestPermissions() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        ActivityCompat.requestPermissions(this, perms, 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) startBle()
        else tvStatus.text = "Bluetooth permission denied"
    }

    override fun onDestroy() { super.onDestroy(); ble.disconnect() }
}
