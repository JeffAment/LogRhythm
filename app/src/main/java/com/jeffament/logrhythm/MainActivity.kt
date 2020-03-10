package com.jeffament.logrhythm

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlinx.android.synthetic.main.activity_main.*
import java.text.DateFormat
import java.util.*
import kotlin.collections.ArrayList

// todo: We are using a HashSet to find unique phone numbers; would like to try and add 'DISTINCT' to the query to delegate this work to SQL
// todo: gracefully handle restricted calls like the one I got on feb 26 which is an empty string
// todo: format date time
// todo: modify list_item.xml
// todo: add app icon
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        supportActionBar?.setDisplayHomeAsUpEnabled(true)
//        supportActionBar?.setLogo(R.mipmap.ic_launcher)
//        supportActionBar?.setDisplayUseLogoEnabled(true)
        setContentView(R.layout.activity_main)
        setupPermissions()
        configureEditTextChangeListener()
        configureCheckboxListeners()
        configureListViewOnLongClickListener()
        populateListView(editText.text.toString()) // empty string
    }

    private fun setupPermissions() {
        // todo: check if permissions were denied and handle it; consider only handling phone call permission when user requests making a call
        val permissions = arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.CALL_PHONE)
        ActivityCompat.requestPermissions(this, permissions,0)
    }

    private fun configureEditTextChangeListener() {
        editText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {}

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                val possibleNumbers: ArrayList<String>? = executeQuery(p0.toString())
                val adapter = possibleNumbers?.let {
                    ArrayAdapter<String>(
                            this@MainActivity, android.R.layout.simple_list_item_1,
                            it
                    )
                }
                listView.adapter = adapter
            }
        })
    }

    private fun configureCheckboxListeners() {
        checkBoxIncoming.setOnCheckedChangeListener { _, _ ->
            populateListView(editText.text.toString())
        }
        checkBoxOutgoing.setOnCheckedChangeListener { _, _ ->
            populateListView(editText.text.toString())
        }
        checkBoxUnique.setOnCheckedChangeListener { _, _ ->
            populateListView(editText.text.toString())
        }
    }

    private fun configureListViewOnLongClickListener() {
        listView.setOnItemLongClickListener { _, _, pos, _ ->
            val number = listView.getItemAtPosition(pos).toString().substringBefore(" ") // todo: meh
            launchAlertBox(number)
            true
        }
    }

    private fun populateListView(searchTerm: String) {
        val possibleNumbers: ArrayList<String>? = executeQuery(searchTerm)
        val adapter = possibleNumbers?.let {
            ArrayAdapter<String>(
                    this@MainActivity, android.R.layout.simple_list_item_1, it
            )
        }
        listView.adapter = adapter
    }

    @SuppressLint("MissingPermission")
    private fun launchAlertBox(number: String) {
        val dialogBuilder = AlertDialog.Builder(this)

        dialogBuilder.setMessage(number)
                .setCancelable(true)
                .setPositiveButton("Call") { _, _ ->
                    val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
                    startActivity(intent)
                }
                .setNegativeButton("Text") { dialog, _ ->
                    dialog.cancel()
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.data = Uri.parse("sms:$number")

                    if (intent.resolveActivity(packageManager) != null) {
                        startActivity(intent)
                    }
                }

        val alert = dialogBuilder.create()
        alert.setTitle(number)
        alert.show()
    }

    // todo: function too big
    @SuppressLint("MissingPermission")
    private fun executeQuery(searchTerm: String): ArrayList<String>? {
        val list = ArrayList<String>()
        val hashSet = HashSet<String>()

        val displayIncomingOnly = checkBoxIncoming.isChecked && !checkBoxOutgoing.isChecked
        val displayOutgoingOnly = !checkBoxIncoming.isChecked && checkBoxOutgoing.isChecked
        val displayBoth = checkBoxIncoming.isChecked && checkBoxOutgoing.isChecked

        // todo: see if DISTINCT can be put in query to make less wok for the hashset to do

        val selection = "${CallLog.Calls.NUMBER} LIKE '%$searchTerm%'" + // remove single quotes from here to get a log error that shows the query!
                when { // are MISSED calls and DECLINED calls subsets of INCOMING?  It seems so.
                    displayIncomingOnly -> " AND ${CallLog.Calls.TYPE} in (${CallLog.Calls.INCOMING_TYPE}, ${CallLog.Calls.MISSED_TYPE})"
                    displayOutgoingOnly -> " AND ${CallLog.Calls.TYPE} in (${CallLog.Calls.OUTGOING_TYPE})"
                    displayBoth -> " AND ${CallLog.Calls.TYPE} in (${CallLog.Calls.INCOMING_TYPE}, ${CallLog.Calls.MISSED_TYPE}, ${CallLog.Calls.OUTGOING_TYPE})"
                    else -> " AND 0 = 1" // show nothing
                }

        val projections = arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE) // columns to be returned

        val sortOrder = "${CallLog.Calls.DATE} DESC" // Add LIMIT here maybe

        contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projections,
                selection, // string to search for
                null,
                sortOrder // sort order for returned rows
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val number: Int = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                //val type: Int = cursor.getColumnIndex(CallLog.Calls.TYPE)
                val date: Int = cursor.getColumnIndex(CallLog.Calls.DATE)

                do {
                    val phNumber: String = cursor.getString(number)
                    val callDate: String = cursor.getString(date)
                    //val df = DateFormat.getDateInstance(DateFormat.LONG, Locale.US)
                    val callDayTime = Date(callDate.toLong())

                    if (checkBoxUnique.isChecked) {
                        if (!hashSetContainsNumber(hashSet, phNumber)) {
                            list.add(phNumber + "   " + callDayTime) // todo: this is temporary!
                        }
                        hashSet.add(phNumber)
                    } else {
                        list.add(phNumber + "   " + callDayTime)
                    }
                } while (cursor.moveToNext())
                cursor.close()
            }
        }
        return list
    }

    // Checks if number exists already, disregarding country code and '+' char since these are not always present
    private fun hashSetContainsNumber(hashSet: HashSet<String>, number: String): Boolean {
        // todo make this work better to use the length of the country code to determine what substrings to use (start index 1..countryCode.length+1)
        val countryCode = "1" // change this to your Country Code
        if (number.length < 10) return false
        return (hashSet.contains(number) || hashSet.contains("$countryCode$number") || hashSet.contains("+$number") || hashSet.contains("+$countryCode$number") ||
                hashSet.contains(number.substring(1)) || hashSet.contains(number.substring(2)))
    }
}
