package com.honz.itsvisualizer.cards

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.honz.itsvisualizer.R
import utils.storage.data.Denm

class DenmCard(private val denm: Denm) : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_denm, container, false)

        val text = view.findViewById<TextView>(R.id.denm_text)
        "This is a DENM ${denm.stationID}".also { text.text = it }

        return view
    }
}