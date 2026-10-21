package pk.bar.membermanager

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pk.bar.membermanager.data.*

@Composable
internal fun WhatsAppRecipientsDialog(members:List<MemberEntity>,selectedIds:List<String>,onSelection:(List<String>)->Unit,onClose:()->Unit){
    var query by rememberSaveable{mutableStateOf("")}
    val selected=remember(selectedIds){selectedIds.toSet()}
    val eligible=remember(members){members.filter{!it.removed&&whatsappNumber(it.cellNumber)!=null}}
    val visible=remember(eligible,query){eligible.filter{it.fullName().contains(query,true)||it.cellNumber.orEmpty().contains(query)}}
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)){
        Surface(Modifier.fillMaxSize().systemBarsPadding()){
            Column(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                Text("Choose broadcast contacts",style=MaterialTheme.typography.titleLarge)
                Text("${selected.size} / $WHATSAPP_BROADCAST_CONTACT_LIMIT selected · ${eligible.size} valid numbers",style=MaterialTheme.typography.bodySmall)
                OutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth(),label={Text("Search name or cell")},singleLine=true)
                Text("Selection prepares a contact file. Create the broadcast list and send the message in WhatsApp.",style=MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.weight(1f)){
                    items(visible,key={it.id}){member->
                        val checked=member.id in selected
                        val enabled=checked||selected.size<WHATSAPP_BROADCAST_CONTACT_LIMIT
                        fun toggle(){onSelection(if(checked)selectedIds-member.id else selectedIds+member.id)}
                        Row(Modifier.fillMaxWidth().clickable(enabled=enabled){toggle()}.padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically){
                            Checkbox(checked,onCheckedChange={toggle()},enabled=enabled)
                            Column(Modifier.weight(1f)){Text(member.fullName());Text(member.cellNumber.orEmpty(),style=MaterialTheme.typography.bodySmall)}
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                    TextButton(onClick={onSelection(emptyList())}){Text("Clear selection")}
                    Button(onClick=onClose){Text("Done")}
                }
            }
        }
    }
}
