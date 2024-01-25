package com.honz.itsvisualizer

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.MenuPopupWindow.MenuDropDownListView
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class SettingsFragment : Fragment() {

    private lateinit var ipInput: TextInputEditText
    private lateinit var portInput: TextInputEditText
    private lateinit var timeDropdownLayout: TextInputLayout
    private lateinit var timeDropdown: AutoCompleteTextView
    private lateinit var saveButton: Button

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        ipInput = view.findViewById(R.id.ipInput)
        portInput = view.findViewById(R.id.portInput)
        timeDropdownLayout = view.findViewById(R.id.timeDropdown)
        timeDropdown = timeDropdownLayout.findViewById(R.id.timeDropdownText)
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

        // Dropdown
        val deletionTimeIndex = sharedPreferences.getInt("deletionTimeIndex", 3)
        val deletionTimeOptions = resources.getStringArray(R.array.deletion_time_period)

        if (deletionTimeIndex >= 0 && deletionTimeIndex < deletionTimeOptions.size) {
            timeDropdown.setText(deletionTimeOptions[deletionTimeIndex], false)
        } else {
            timeDropdown.setText(deletionTimeOptions[3], false)
        }

        // Save button
        saveButton.setOnClickListener { 
            saveSettings()
            Toast.makeText(view.context, getString(R.string.saved_successfully), Toast.LENGTH_SHORT).show()
        }

        return view
    }

    private fun saveSettings() {
        synchronized(sharedPreferences) {
            val editor = sharedPreferences.edit()
            editor.putString("ipAddress", ipInput.text.toString())

            // Handle port input
            val portInputText = portInput.text.toString()
            val portValue = if (portInputText.isNotEmpty()) {
                portInputText.toIntOrNull()
            } else {
                null
            }
            editor.putInt("serverPort", portValue ?: -1)

            // Get dropdown selected item index
            val selectedDeletionTime = timeDropdown.text.toString()
            val deletionTimeOptions = resources.getStringArray(R.array.deletion_time_period)
            val deletionTimeIndex = deletionTimeOptions.indexOf(selectedDeletionTime)
            editor.putInt("deletionTimeIndex", deletionTimeIndex)

            editor.apply()
        }
    }

}