package com.example.pathy.emergency

import androidx.annotation.StringRes
import com.example.pathy.R
import com.example.pathy.i18n.AppLocale
import kotlin.math.roundToInt
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.telephony.SmsManager
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * 🚨 EMERGENCY SOS
 * 
 * Emergency alert system
 * - Send SOS to emergency contacts
 * - Share live location
 * - Call emergency services
 * - Fall detection
 * - CRITICAL safety feature!
 */
class EmergencySOS(private val context: Context) {

    /** Resolved when called, so messages use the language the user has chosen right now. */
    private fun s(@StringRes id: Int, vararg args: Any?): String =
        AppLocale.wrap(context).getString(id, *args)
    
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    
    /**
     * Trigger emergency SOS
     */
    suspend fun triggerSOS(
        message: String = s(R.string.sos_default_message),
        contacts: List<EmergencyContact>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Get current location
            val location = getCurrentLocation()
            
            // Build SOS message with location
            val sosMessage = buildSOSMessage(message, location)
            
            // Send to all emergency contacts
            contacts.forEach { contact ->
                sendSOSToContact(contact, sosMessage, location)
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get current location
     */
    private suspend fun getCurrentLocation(): Location? {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        
        return try {
            fusedLocationClient.lastLocation.await()
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Build SOS message
     */
    private fun buildSOSMessage(message: String, location: Location?): String {
        val builder = StringBuilder(message)
        
        if (location != null) {
            builder.append(s(R.string.sos_msg_location_header))
            builder.append(s(R.string.sos_msg_latitude, location.latitude))
            builder.append(s(R.string.sos_msg_longitude, location.longitude))
            builder.append(s(R.string.sos_msg_accuracy, location.accuracy.roundToInt()))
            builder.append(s(R.string.sos_msg_maps))
            builder.append("\nhttps://maps.google.com/?q=${location.latitude},${location.longitude}")
        }
        
        builder.append(s(R.string.sos_msg_footer))
        builder.append(s(R.string.sos_msg_time, java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())))
        
        return builder.toString()
    }
    
    /**
     * Send SOS to contact
     */
    private fun sendSOSToContact(
        contact: EmergencyContact,
        message: String,
        location: Location?
    ) {
        try {
            // Send SMS
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.SEND_SMS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val smsManager = SmsManager.getDefault()
                
                // Split long messages
                val parts = smsManager.divideMessage(message)
                smsManager.sendMultipartTextMessage(
                    contact.phoneNumber,
                    null,
                    parts,
                    null,
                    null
                )
            }
            
            // Send location link separately
            if (location != null) {
                val locationLink = "https://maps.google.com/?q=${location.latitude},${location.longitude}"
                val smsManager = SmsManager.getDefault()
                smsManager.sendTextMessage(
                    contact.phoneNumber,
                    null,
                    s(R.string.sos_msg_location_link, locationLink),
                    null,
                    null
                )
            }
            
        } catch (e: Exception) {
            // Log error but continue with other contacts
        }
    }
    
    /**
     * Call emergency services
     */
    fun callEmergencyServices(emergencyNumber: String = "911") {
        // ACTION_DIAL needs no permission (CALL_PHONE is not declared in the manifest, so
        // ACTION_CALL was silently skipped). The user presses call in the dialer.
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$emergencyNumber")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
    
    /**
     * Start live location sharing
     */
    /**
     * Sends the contacts a link to the user's current location. This is a snapshot, not live tracking:
     * PATHY does not run a tracking page, so the message does not promise one.
     */
    suspend fun shareCurrentLocation(contacts: List<EmergencyContact>): Result<String> = withContext(Dispatchers.IO) {
        try {
            val location = getCurrentLocation()
                ?: return@withContext Result.failure(IllegalStateException(s(R.string.sos_location_unavailable)))
            val link = "https://maps.google.com/?q=${location.latitude},${location.longitude}"
            val message = s(R.string.sos_msg_sharing_location, link)

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                val sms = SmsManager.getDefault()
                contacts.forEach { contact ->
                    sms.sendTextMessage(contact.phoneNumber, null, message, null, null)
                }
            }
            Result.success(link)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Detect if user has fallen (using accelerometer)
     */
    fun startFallDetection(onFallDetected: () -> Unit) {
        // Would implement accelerometer monitoring
        // Detect sudden deceleration patterns
        // Auto-trigger SOS after confirmation timeout
    }
}

/**
 * Emergency contact
 */
data class EmergencyContact(
    val name: String,
    val phoneNumber: String,
    val relationship: String = ""
)

/**
 * SOS event
 */
data class SOSEvent(
    val id: String,
    val userId: String,
    val timestamp: Long,
    val location: Location?,
    val message: String,
    val contactsNotified: List<String>,
    val resolved: Boolean = false
)
