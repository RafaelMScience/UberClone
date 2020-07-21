package com.rafaelm.uberclone

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.firebase.ui.auth.AuthMethodPickerLayout
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.IdpResponse
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.rafaelm.uberclone.model.DriverInfoModel
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.android.synthetic.main.activity_splash_screen.*
import java.util.concurrent.TimeUnit

class SplashScreenActivity : AppCompatActivity() {

    companion object {
        private val LOGIN_REQUEST_CODE = 7171
    }

    private lateinit var providers: List<AuthUI.IdpConfig>
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var listener: FirebaseAuth.AuthStateListener

    private lateinit var database: FirebaseDatabase
    private lateinit var driverInfoRef: DatabaseReference

    override fun onStart() {
        super.onStart()
        delaySplashScreen()
    }

    override fun onStop() {
        //firebaseAuth != null && listener != null tem um if mas foi recomendado remover
        firebaseAuth.removeAuthStateListener(listener)
        super.onStop()
    }

    @SuppressLint("CheckResult")
    private fun delaySplashScreen() {
        Completable.timer(3, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
            .subscribe {
                firebaseAuth.addAuthStateListener(listener)
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)

        init()
    }

    private fun init() {

        database = FirebaseDatabase.getInstance()
        driverInfoRef = database.getReference(Common.DRIVER_INFO_REFERENCE)

        providers = mutableListOf(
            AuthUI.IdpConfig.PhoneBuilder().build(),
            AuthUI.IdpConfig.GoogleBuilder().build()
        )

        firebaseAuth = FirebaseAuth.getInstance()
        listener = FirebaseAuth.AuthStateListener { myFirebaseAuth ->
            val user = myFirebaseAuth.currentUser
            if (user != null) {
                checkUserFromFirebase()
            } else {
                showLoginLayout()
            }
        }
    }

    private fun checkUserFromFirebase() {
        driverInfoRef
            .child(FirebaseAuth.getInstance().currentUser!!.uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@SplashScreenActivity, error.message, Toast.LENGTH_SHORT)
                        .show()
                }

                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
//                        Toast.makeText(
//                            this@SplashScreenActivity,
//                            "User already register!",
//                            Toast.LENGTH_SHORT
//                        )
//                            .show()
                        val model = snapshot.getValue(DriverInfoModel::class.java)
                        goToHomeActivity(model)
                    } else {
                        showRegisterLayout()
                    }
                }

            })
    }

    private fun goToHomeActivity(model: DriverInfoModel?) {
        Common.currentUser = model
        startActivity(Intent(this, DriverHomeActivity::class.java))
        finish()
    }

    private fun showRegisterLayout() {
        val builder = AlertDialog.Builder(this, R.style.DialogTheme)
        val itemView = LayoutInflater.from(this).inflate(R.layout.layout_register, null)

        val edtFirstName = itemView.findViewById<View>(R.id.edt_first_name) as TextInputEditText
        val edtLastName = itemView.findViewById<View>(R.id.edt_last_name) as TextInputEditText
        val edtPhoneNumber = itemView.findViewById<View>(R.id.edt_phone_number) as TextInputEditText

        val btnContinue = itemView.findViewById<View>(R.id.btn_register) as Button

        //set Data
        if (FirebaseAuth.getInstance().currentUser?.phoneNumber != null && !TextUtils.isDigitsOnly(
                FirebaseAuth.getInstance().currentUser?.phoneNumber
            )
        ) edtPhoneNumber.setText(FirebaseAuth.getInstance().currentUser?.phoneNumber)

        //view
        builder.setView(itemView)
        val dialog = builder.create()
        dialog.show()

        //Event
        btnContinue.setOnClickListener {
            when {
                TextUtils.isDigitsOnly(edtFirstName.text.toString()) -> {
                    Toast.makeText(this, "Please enter First Name", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                TextUtils.isDigitsOnly(edtLastName.text.toString()) -> {
                    Toast.makeText(this, "Please enter Last Name", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                TextUtils.isDigitsOnly(edtPhoneNumber.text.toString()) -> {
                    Toast.makeText(this, "Please enter Phone Number", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                else -> {
                    val model = DriverInfoModel()
                    model.firstName = edtFirstName.text.toString()
                    model.lastName = edtLastName.text.toString()
                    model.phoneNumber = edtPhoneNumber.text.toString()
                    model.rating = 0.0

                    driverInfoRef.child(FirebaseAuth.getInstance().currentUser!!.uid)
                        .setValue(model)
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "" + e.message, Toast.LENGTH_SHORT).show()
                            dialog.dismiss()

                            progress_bar.visibility = View.GONE

                        }
                        .addOnSuccessListener {
                            Toast.makeText(this, "Register Successfully!!", Toast.LENGTH_SHORT)
                                .show()
                            dialog.dismiss()

                            goToHomeActivity(model)

                            progress_bar.visibility = View.GONE
                        }
                }
            }
        }
    }

    private fun showLoginLayout() {
        val authMethodPickerLayout = AuthMethodPickerLayout.Builder(R.layout.layout_sign_in)
            .setPhoneButtonId(R.id.btn_phone_sign_in)
            .setGoogleButtonId(R.id.btn_google_sign_in)
            .build()

        startActivityForResult(
            AuthUI.getInstance()
                .createSignInIntentBuilder()
                .setAuthMethodPickerLayout(authMethodPickerLayout)
                .setTheme(R.style.LoginTheme)
                .setAvailableProviders(providers)
                .setIsSmartLockEnabled(false)
                .build(),
            LOGIN_REQUEST_CODE
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == LOGIN_REQUEST_CODE) {
            val response = IdpResponse.fromResultIntent(data)
            if (resultCode == Activity.RESULT_OK) {
                val user = FirebaseAuth.getInstance().currentUser
            } else {
                Toast.makeText(this, "" + response!!.error!!.message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}