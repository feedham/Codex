package pk.bar.membermanager

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import pk.bar.membermanager.data.MemberEntity

@Composable
internal fun DirectCallButton(member: MemberEntity, modifier: Modifier = Modifier, onCallStarted: () -> Unit) {
    val context = LocalContext.current
    val cell = member.cellNumber?.trim()?.takeIf { it.isNotEmpty() } ?: return
    var pendingMember by rememberSaveable(member.id) { mutableStateOf<String?>(null) }
    var showRationale by rememberSaveable(member.id) { mutableStateOf(false) }
    fun message(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    fun placeCall() {
        if (!Regex("^\\+?[0-9 ()-]+$").matches(cell) || cell.count(Char::isDigit) < 3) {
            message("This member's cell number is not valid for calling.")
            return
        }
        val number = cell.filter { it.isDigit() || it == '+' }
        try {
            context.startActivity(Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null)))
            onCallStarted()
        } catch (_: ActivityNotFoundException) {
            message("No phone app is available to make calls on this device.")
        } catch (_: SecurityException) {
            message("Phone permission is required. Enable it in the app's permission settings.")
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val requestedMember = pendingMember
        pendingMember = null
        if (requestedMember == member.id) {
            if (granted) placeCall()
            else message("Call not started. Allow Phone permission to call directly from the app.")
        }
    }
    fun requestPermission() {
        pendingMember = member.id
        permission.launch(Manifest.permission.CALL_PHONE)
    }
    if (showRationale) {
        AlertDialog(onDismissRequest = { showRationale = false }, title = { Text("Allow direct calls") },
            text = { Text("Phone permission lets the Call button call this member directly and mark them as called.") },
            confirmButton = { TextButton(onClick = { showRationale = false; requestPermission() }) { Text("Continue") } },
            dismissButton = { TextButton(onClick = { showRationale = false }) { Text("Cancel") } })
    }
    Button(onClick = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) placeCall()
        else if ((context as? Activity)?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CALL_PHONE) } == true) showRationale = true
        else requestPermission()
    }, enabled = pendingMember == null, modifier = modifier.height(56.dp), shape = RoundedCornerShape(15.dp), contentPadding = PaddingValues(horizontal=8.dp)) {
        Icon(Icons.Default.Call, null,Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text("Call")
    }
}
