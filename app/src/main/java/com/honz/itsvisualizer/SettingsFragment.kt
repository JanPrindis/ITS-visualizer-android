package com.honz.itsvisualizer

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText

class SettingsFragment : Fragment() {

    // Server settings
    private lateinit var ipInput: TextInputEditText
    private lateinit var portInput: TextInputEditText
    private lateinit var timeDropdown: AutoCompleteTextView
    private lateinit var timeWarning: TextView

    // Map settings
    private lateinit var themeDropdown: AutoCompleteTextView
    private lateinit var exteriorsToggle: MaterialSwitch
    private lateinit var exteriorsOpacityWrapper: LinearLayout
    private lateinit var exteriorsOpacity: TextInputEditText
    private lateinit var unitsDropdown: AutoCompleteTextView

    // Camera settings
    private lateinit var northAlignToggle: MaterialSwitch
    private lateinit var trackUserSelectedToggle: MaterialSwitch
    private lateinit var defaultZoom: TextInputEditText

    // Visualization settings
    private lateinit var audioToggle: MaterialSwitch
    private lateinit var showDenmToggle: MaterialSwitch
    private lateinit var showMapemToggle: MaterialSwitch
    private lateinit var priorityDropdown: AutoCompleteTextView
    private lateinit var mapemGeometryToggle: MaterialSwitch
    private lateinit var colorsWrapper: LinearLayout

    // UI test
    private lateinit var uiTestToggle: MaterialSwitch

    private lateinit var saveButton: Button
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        // Server
        ipInput = view.findViewById(R.id.ipInput)
        portInput = view.findViewById(R.id.portInput)
        timeDropdown = view.findViewById(R.id.timeDropdownText)
        timeWarning = view.findViewById(R.id.timeNeverWarning)

        // Map
        themeDropdown = view.findViewById(R.id.themeDropdownText)
        exteriorsToggle = view.findViewById(R.id.exteriorsToggle)
        exteriorsOpacityWrapper = view.findViewById(R.id.opacityWrapper)
        exteriorsOpacity = view.findViewById(R.id.buildingOpacity)
        unitsDropdown = view.findViewById(R.id.unitsDropdownText)

        // Camera
        northAlignToggle = view.findViewById(R.id.cameraNorthAlignToggle)
        trackUserSelectedToggle = view.findViewById(R.id.trackUserSelectedToggle)
        defaultZoom = view.findViewById(R.id.cameraZoom)

        // Visualization
        audioToggle = view.findViewById(R.id.notificationAudioToggle)
        showDenmToggle = view.findViewById(R.id.displayDenmToggle)
        showMapemToggle = view.findViewById(R.id.displayMapemToggle)
        priorityDropdown = view.findViewById(R.id.priorityDropdownText)
        mapemGeometryToggle = view.findViewById(R.id.displayMapemGeometryToggle)
        colorsWrapper = view.findViewById(R.id.colorsWrapper)

        // UI test
        uiTestToggle = view.findViewById(R.id.UiTestModeToggle)

        saveButton = view.findViewById(R.id.saveButton)

        // Load settings from Shared Preferences
        sharedPreferences = requireActivity().applicationContext.getSharedPreferences("Settings", Context.MODE_PRIVATE)

        // IP address
        ipInput.setText(
            sharedPreferences.getString("ipAddress", "")
        )

        // Port number
        val port = sharedPreferences.getInt("serverPort", -1)
        if(port != -1)
            portInput.setText(port.toString())

        // Time dropdown
        val deletionTimeIndex = sharedPreferences.getInt("deletionTimeIndex", 2)
        val deletionTimeOptions = resources.getStringArray(R.array.deletion_time_period)

        if (deletionTimeIndex in deletionTimeOptions.indices) {
            timeDropdown.setText(deletionTimeOptions[deletionTimeIndex], false)
        }
        else {
            timeDropdown.setText(deletionTimeOptions[2], false)
        }

        // Warning text - display only if 'Never' is selected
        timeWarning.visibility = if(deletionTimeIndex == deletionTimeOptions.lastIndex)
            View.VISIBLE
        else
            View.GONE

        timeDropdown.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val selectedDeletionTime = timeDropdown.text.toString()
                val index = deletionTimeOptions.indexOf(selectedDeletionTime)

                timeWarning.visibility = if(index == deletionTimeOptions.lastIndex)
                    View.VISIBLE
                else
                    View.GONE
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // Theme dropdown
        val themeIndex = sharedPreferences.getInt("mapThemeIndex", 0)
        val themeOptions = resources.getStringArray(R.array.map_theme_selection)

        if (themeIndex in themeOptions.indices) {
            themeDropdown.setText(themeOptions[themeIndex], false)
        }
        else {
            timeDropdown.setText(themeOptions[0], false)
        }

        // 3D exteriors toggle
        val exteriorsEnabled = sharedPreferences.getBoolean("displayBuildingExteriors", false)
        exteriorsToggle.isChecked = exteriorsEnabled
        exteriorsOpacityWrapper.visibility = if(exteriorsEnabled)
            View.VISIBLE
        else
            View.GONE

        exteriorsToggle.setOnCheckedChangeListener { _, isChecked ->
            exteriorsOpacityWrapper.visibility = if(isChecked)
                View.VISIBLE
            else
                View.GONE

        }

        // 3D exteriors opacity
        val exteriorOpacity = sharedPreferences.getFloat("buildingExteriorsOpacity", 0.5f)
        exteriorsOpacity.setText(exteriorOpacity.toString())

        val opacityInputFilter = InputFilter { source, _, _, dest, _, _ ->
            val inputText = dest.toString() + source.toString()
            if (inputText.isNotEmpty()) {
                try {
                    val inputNumber = inputText.toDouble()
                    if (inputNumber in 0.0..1.0) {
                            null
                    } else {
                        if (inputNumber > 1.0) {
                            exteriorsOpacity.setText(1.0.toString())
                            exteriorsOpacity.setSelection(exteriorsOpacity.length())
                        } else {
                            exteriorsOpacity.setText(0.0.toString())
                            exteriorsOpacity.setSelection(exteriorsOpacity.length())
                        }
                        ""
                    }
                }
                catch(e: Exception) {
                    exteriorsOpacity.setText(exteriorOpacity.toString())
                    exteriorsOpacity.setSelection(exteriorsOpacity.length())
                    ""
                }
            } else {
                exteriorsOpacity.setText(exteriorOpacity.toString())
                exteriorsOpacity.setSelection(exteriorsOpacity.length())
                ""
            }
        }
        exteriorsOpacity.filters = arrayOf(opacityInputFilter)

        // Units dropdown
        val unitsIndex = sharedPreferences.getInt("mapUnitsIndex", 0)
        val unitsOptions = resources.getStringArray(R.array.map_units_selection)

        if (unitsIndex in unitsOptions.indices) {
            unitsDropdown.setText(unitsOptions[unitsIndex], false)
        }
        else {
            unitsDropdown.setText(unitsOptions[0], false)
        }

        // Camera face North
        val cameraFaceNorth = sharedPreferences.getBoolean("cameraFaceNorth", false)
        northAlignToggle.isChecked = cameraFaceNorth

        // Camera track user selected stations
        val trackUserSelected = sharedPreferences.getBoolean("cameraTrackUserSelected", true)
        trackUserSelectedToggle.isChecked = trackUserSelected

        // Camera default zoom
        val zoom = sharedPreferences.getFloat("cameraDefaultZoom", 18.0f)
        defaultZoom.setText(zoom.toString())

        val zoomInputFilter = InputFilter { source, _, _, dest, _, _ ->
            val inputText = dest.toString() + source.toString()
            if (inputText.isNotEmpty()) {
                try {
                    val inputNumber = inputText.toDouble()
                    if (inputNumber in 0.0..22.0) {
                        null
                    } else {
                        if (inputNumber > 1.0) {
                            defaultZoom.setText(0.0.toString())
                            defaultZoom.setSelection(defaultZoom.length())
                        } else {
                            defaultZoom.setText(22.0.toString())
                            defaultZoom.setSelection(defaultZoom.length())
                        }
                        ""
                    }
                }
                catch(e: Exception) {
                    defaultZoom.setText(zoom.toString())
                    defaultZoom.setSelection(defaultZoom.length())
                    ""
                }
            } else {
                defaultZoom.setText(zoom.toString())
                defaultZoom.setSelection(defaultZoom.length())
                ""
            }
        }
        defaultZoom.filters = arrayOf(zoomInputFilter)

        // Audio toggle
        val audioEnabled = sharedPreferences.getBoolean("autoAudioEnabled", false)
        audioToggle.isChecked = audioEnabled

        // Denm toggle
        val autoDenm = sharedPreferences.getBoolean("autoShowDenm", true)
        showDenmToggle.isChecked = autoDenm

        // Mapem toggle
        val autoMapem = sharedPreferences.getBoolean("autoShowMapem", true)
        showMapemToggle.isChecked = autoMapem

        // Auto priority
        val priorityIndex = sharedPreferences.getInt("autoPriorityIndex", 0)
        val priorityOptions = resources.getStringArray(R.array.auto_priority)

        if (priorityIndex in priorityOptions.indices) {
            priorityDropdown.setText(priorityOptions[priorityIndex], false)
        }
        else {
            priorityDropdown.setText(priorityOptions[0], false)
        }

        // Mapem geometry
        val mapemGeometry = sharedPreferences.getBoolean("showMapemGeometry", false)
        mapemGeometryToggle.isChecked = mapemGeometry

        // Expand or hide color list
        colorsWrapper.visibility = if(mapemGeometry) {
             View.VISIBLE
        }
        else {
            View.GONE
        }

        mapemGeometryToggle.setOnCheckedChangeListener { _, isChecked ->
            colorsWrapper.visibility = if(isChecked) {
                View.VISIBLE
            }
            else {
                View.GONE
            }
        }

        // UI test toggle
        val uiTestMode = sharedPreferences.getBoolean("uiTestEnabled", false)
        uiTestToggle.isChecked = uiTestMode

        // Save button
        saveButton.setOnClickListener { 
            saveSettings()
            Toast.makeText(view.context, getString(R.string.saved_successfully), Toast.LENGTH_SHORT).show()
        }

        return view
    }

    private fun saveSettings() {
        val editor = sharedPreferences.edit()

        // IP address
        editor.putString("ipAddress", ipInput.text.toString())

        // Port
        val portInputText = portInput.text.toString()
        val portValue = if (portInputText.isNotEmpty()) {
            portInputText.toIntOrNull()
        } else {
            null
        }
        editor.putInt("serverPort", portValue ?: -1)

        // Deletion time
        val selectedDeletionTime = timeDropdown.text.toString()
        val deletionTimeOptions = resources.getStringArray(R.array.deletion_time_period)
        val deletionTimeIndex = deletionTimeOptions.indexOf(selectedDeletionTime)
        editor.putInt("deletionTimeIndex", deletionTimeIndex)

        // Theme
        val selectedTheme = themeDropdown.text.toString()
        val themeOptions = resources.getStringArray(R.array.map_theme_selection)
        val themeIndex = themeOptions.indexOf(selectedTheme)
        editor.putInt("mapThemeIndex", themeIndex)

        // 3D exteriors toggle
        editor.putBoolean("displayBuildingExteriors", exteriorsToggle.isChecked)

        // 3D exteriors opacity
        editor.putFloat("buildingExteriorsOpacity", exteriorsOpacity.text.toString().toFloat())

        // Units
        val selectedUnit = unitsDropdown.text.toString()
        val unitsOptions = resources.getStringArray(R.array.map_units_selection)
        val unitsIndex = unitsOptions.indexOf(selectedUnit)
        editor.putInt("mapUnitsIndex", unitsIndex)

        // Camera face North
        editor.putBoolean("cameraFaceNorth", northAlignToggle.isChecked)

        // Camera track user selected stations
        editor.putBoolean("cameraTrackUserSelected", trackUserSelectedToggle.isChecked)

        // Camera default zoom
        editor.putFloat("cameraDefaultZoom", defaultZoom.text.toString().toFloat())

        // Audio toggle
        editor.putBoolean("autoAudioEnabled", audioToggle.isChecked)

        // Denm toggle
        editor.putBoolean("autoShowDenm", showDenmToggle.isChecked)

        // Mapem toggle
        editor.putBoolean("autoShowMapem", showMapemToggle.isChecked)

        // Auto priority
        val selectedPriority = priorityDropdown.text.toString()
        val priorityOptions = resources.getStringArray(R.array.auto_priority)
        val priorityIndex = priorityOptions.indexOf(selectedPriority)
        editor.putInt("autoPriorityIndex", priorityIndex)

        // Mapem geometry
        editor.putBoolean("showMapemGeometry", mapemGeometryToggle.isChecked)

        // UI test
        editor.putBoolean("uiTestEnabled", uiTestToggle.isChecked)

        // Apply changes and notify services
        editor.apply()

        val intent = Intent("itsVisualizer.SETTINGS_UPDATED")
        LocalBroadcastManager.getInstance(requireView().context).sendBroadcast(intent)
    }
}