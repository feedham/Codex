package pk.bar.membermanager

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import pk.bar.membermanager.data.*

@Composable
internal fun SeniorMembersScreen(members:List<MemberEntity>,modifier:Modifier,onOpenMember:(String)->Unit){
    val rows=seniorMemberRows(members)
    LazyColumn(modifier.fillMaxSize().padding(horizontal=20.dp),contentPadding=PaddingValues(top=18.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{
            Text("Top 50 Bar Members List",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold)
            Text("${rows.size} members ranked by earliest enrollment. Select a member to open their profile.",style=MaterialTheme.typography.bodySmall)
            Text("D.O.E: DD/MM/YYYY",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
        }
        if(rows.isEmpty())item{Text("No active members with valid enrollment dates.",Modifier.padding(vertical=24.dp))}
        itemsIndexed(rows,key={_,member->member.id}){index,member->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).clickable{onOpenMember(member.id)}.padding(14.dp),verticalAlignment=Alignment.CenterVertically){
                Text("${index+1}",Modifier.width(30.dp),color=MaterialTheme.colorScheme.primary)
                AsyncImage(model=member.photoPath?:R.drawable.blank_adv_profile_picture,contentDescription="${member.fullName()} profile picture",contentScale=ContentScale.Crop,placeholder=painterResource(R.drawable.blank_adv_profile_picture),error=painterResource(R.drawable.blank_adv_profile_picture),modifier=Modifier.size(46.dp).clip(CircleShape))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)){
                    Text(member.fullName(),fontWeight=FontWeight.Medium)
                    Text("Member #${member.memberNumber?:"-"}",style=MaterialTheme.typography.bodySmall)
                    val parts=enrollmentParts(member.enrollmentDate)
                    Text("D.O.E: ${parts?.let{"${it[2]}/${it[1]}/${it[0]}"}.orEmpty()}",style=MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
