package pk.bar.membermanager

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
internal fun BackendDirectoryButton(busy:Boolean,onDownload:(String,String)->Unit){
    var show by remember{mutableStateOf(false)}
    OutlinedButton(onClick={show=true},enabled=!busy,modifier=Modifier.fillMaxWidth()){
        Text(if(busy)"Downloading directory…" else "Download from SQLite backend")
    }
    if(show){
        var endpoint by rememberSaveable{mutableStateOf("http://127.0.0.1:8080")}
        var token by remember{mutableStateOf("")}
        AlertDialog(onDismissRequest={show=false},title={Text("SQLite directory backend")},text={
            Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
                Text("Download the PDF directory through your server API. Local campaign status, called flags, references, and profile edits are kept. Members absent from the server are kept too.")
                OutlinedTextField(endpoint,{endpoint=it},label={Text("API base URL")},singleLine=true)
                OutlinedTextField(token,{token=it},label={Text("Access token")},singleLine=true,visualTransformation=PasswordVisualTransformation())
                Text("Use HTTPS for a hosted server. The local PC address requires USB forwarding. Your access token is used only for this download and is not saved.",style=MaterialTheme.typography.bodySmall)
            }
        },confirmButton={TextButton(onClick={show=false;onDownload(endpoint.trim(),token.trim())},enabled=endpoint.isNotBlank()&&token.length>=32){Text("Download")}},dismissButton={TextButton(onClick={show=false}){Text("Cancel")}})
    }
}
