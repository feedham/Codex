package pk.bar.membermanager

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pk.bar.membermanager.data.*

@Composable
internal fun WhatsAppComposer(members:List<MemberEntity>,modifier:Modifier,onBack:()->Unit){
    val context=LocalContext.current
    var message by rememberSaveable{mutableStateOf("")}
    var image by rememberSaveable{mutableStateOf<String?>(null)}
    var selectedIds by rememberSaveable{mutableStateOf(listOf<String>())}
    var choosingContacts by rememberSaveable{mutableStateOf(false)}
    var pendingCard by rememberSaveable{mutableStateOf("")}
    var savingContacts by remember{mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    fun notify(text:String)=Toast.makeText(context,text,Toast.LENGTH_LONG).show()
    val saveContacts=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/vcard")){uri->
        if(uri!=null){
            val snapshot=pendingCard
            savingContacts=true
            scope.launch{
                try{
                    withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use{it.write(snapshot)}?:error("Could not open the contact file.")}
                    notify("Contacts saved. Open the VCF file in your Contacts app, then create your WhatsApp broadcast list.")
                }catch(error:kotlinx.coroutines.CancellationException){throw error}
                catch(_:Exception){notify("Could not save contacts. Please choose the file location again.")}
                finally{savingContacts=false;pendingCard=""}
            }
        }else pendingCard=""
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri!=null){
            runCatching{context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}
            image=uri.toString()
        }
    }
    fun share(app:String){
        val photo=image?.let(Uri::parse)
        try{
            val intent=Intent(Intent.ACTION_SEND).setPackage(app)
            if(photo!=null){
                // Verify the selected provider still grants access before handing the URI onward.
                context.contentResolver.openInputStream(photo)?.use{}?:error("Choose the image again.")
                intent.type=context.contentResolver.getType(photo)?.takeIf{it.startsWith("image/")}?:"image/*"
                intent.putExtra(Intent.EXTRA_STREAM,photo)
                intent.clipData=ClipData.newUri(context.contentResolver,"Message image",photo)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }else intent.type="text/plain"
            if(message.isNotBlank())intent.putExtra(Intent.EXTRA_TEXT,message)
            context.startActivity(intent)
        }catch(_:ActivityNotFoundException){notify(if(app=="com.whatsapp")"WhatsApp is not installed." else "WhatsApp Business is not installed.")}
        catch(_:SecurityException){notify("Image access expired. Please choose the image again.")}
        catch(_:Exception){notify("Could not share this message. Please choose the image again and retry.")}
    }
    Column(modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        TextButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,null);Spacer(Modifier.width(6.dp));Text("Back")}
        Text("Compose WhatsApp message",style=MaterialTheme.typography.headlineSmall)
        Text("Write your message, add an image, then choose recipients and confirm sending inside WhatsApp.",style=MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick={choosingContacts=true},modifier=Modifier.fillMaxWidth()){
            Text("Choose member contacts (${selectedIds.size} / $WHATSAPP_BROADCAST_CONTACT_LIMIT)")
        }
        OutlinedButton(onClick={
            pendingCard=whatsappContactCards(members.filter{!it.removed&&it.id in selectedIds&&whatsappNumber(it.cellNumber)!=null})
            saveContacts.launch("bar-members-whatsapp-contacts.vcf")
        },enabled=selectedIds.isNotEmpty()&&!savingContacts,modifier=Modifier.fillMaxWidth()){
            Text(if(savingContacts)"Saving contacts…" else "Save selected contacts (.vcf)")
        }
        Text("Open the saved VCF file with your phone's Contacts app to import it, then create a broadcast list in WhatsApp. Selecting members here does not select recipients in WhatsApp.",style=MaterialTheme.typography.bodySmall)
        OutlinedTextField(message,{message=it.take(4000)},modifier=Modifier.fillMaxWidth(),label={Text("Message / image caption")},minLines=4,maxLines=9,supportingText={Text("${message.length} / 4000")})
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
            OutlinedButton(onClick={picker.launch(arrayOf("image/jpeg","image/png","image/webp"))},modifier=Modifier.weight(1f)){Text(if(image==null)"Add image" else "Change image")}
            if(image!=null)OutlinedButton(onClick={image=null},modifier=Modifier.weight(1f)){Text("Remove image")}
        }
        image?.let{uri->AsyncImage(model=uri,contentDescription="Message image preview",contentScale=ContentScale.Fit,modifier=Modifier.fillMaxWidth().height(220.dp))}
        val ready=message.isNotBlank()||image!=null
        Button(onClick={share("com.whatsapp")},enabled=ready,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text("Share to WhatsApp")}
        OutlinedButton(onClick={share("com.whatsapp.w4b")},enabled=ready,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text("Share to WhatsApp Business")}
        TextButton(onClick={
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Message",message))
            notify("Message copied. You can paste it as the image caption in WhatsApp.")
        },enabled=message.isNotBlank()){Text("Copy message")}
        Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surfaceVariant){
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text("Using a broadcast list",style=MaterialTheme.typography.titleSmall)
                Text("Create and manage broadcast lists inside WhatsApp. Its documented limit is 256 contacts per list, and recipients must have your number saved. The share picker may not offer existing broadcast lists; open the list in WhatsApp if needed.",style=MaterialTheme.typography.bodySmall)
                Text("Review the recipients and caption before sending. If the caption is missing, use Copy message and paste it in WhatsApp. Opening WhatsApp does not mean the message was sent.",style=MaterialTheme.typography.bodySmall)
                TextButton(onClick={
                    try{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://faq.whatsapp.com/861663048350950/")))}catch(_:ActivityNotFoundException){notify("No browser is available.")}
                }){Text("WhatsApp broadcast instructions")}
            }
        }
    }
    if(choosingContacts)WhatsAppRecipientsDialog(members,selectedIds,{selectedIds=it},{choosingContacts=false})
}
