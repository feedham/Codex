package pk.bar.membermanager

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pk.bar.membermanager.data.MemberEntity
import pk.bar.membermanager.data.whatsappNumber

@Composable
internal fun ContactCallActions(member:MemberEntity,onCallStarted:()->Unit){
    if(member.cellNumber.isNullOrBlank())return
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
            DirectCallButton(member,Modifier.weight(1f),onCallStarted)
            WhatsAppCallButton(member,Modifier.weight(1f))
        }
        Text("WhatsApp opens the member's chat. Tap the phone icon there to call.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WhatsAppCallButton(member:MemberEntity,modifier:Modifier){
    val context=LocalContext.current
    fun message(text:String)=Toast.makeText(context,text,Toast.LENGTH_LONG).show()
    Button(onClick={
        val number=whatsappNumber(member.cellNumber)
        if(number==null){message("This member's cell number is not valid for WhatsApp.");return@Button}
        var opened=false
        for(app in listOf("com.whatsapp","com.whatsapp.w4b")){
            try{
                context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://wa.me/$number")).setPackage(app))
                opened=true
                break
            }catch(_:ActivityNotFoundException){/* Try WhatsApp Business next. */}
            catch(_:SecurityException){message("This device does not allow opening WhatsApp.");opened=true;break}
        }
        if(!opened)message("Install WhatsApp or WhatsApp Business to use this option.")
    },modifier=modifier.height(56.dp),shape=RoundedCornerShape(15.dp),contentPadding=PaddingValues(horizontal=8.dp)){
        Icon(Icons.Default.Chat,null,Modifier.size(20.dp));Spacer(Modifier.width(6.dp))
        Text("WhatsApp Call",maxLines=2,textAlign=TextAlign.Center)
    }
}
