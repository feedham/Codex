package pk.bar.membermanager

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
internal fun CreditScreen(modifier:Modifier){
    val credits=listOf(Triple(R.drawable.adv_fahmeed_shaukat,"Fahmeed Shaukat Ansari",""),Triple(R.drawable.adv_m_attique,"Mehar Atteeq Ur Rehman","Ex Joint Secretary DBA Kasur"))
    LazyColumn(modifier.fillMaxSize().padding(horizontal=20.dp),contentPadding=PaddingValues(top=20.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Text("Credit",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.SemiBold)}
        credits.forEach{(portrait,name,office)->item{
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)){
                AsyncImage(model=portrait,contentDescription=name,placeholder=painterResource(portrait),error=painterResource(portrait),contentScale=ContentScale.Fit,modifier=Modifier.size(96.dp))
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
                    Text(name,fontSize=21.sp,lineHeight=28.sp,fontWeight=FontWeight.SemiBold)
                    Text("Advocate High Court",fontSize=16.sp,lineHeight=22.sp)
                    if(office.isNotEmpty())Text(office,fontSize=13.sp,lineHeight=18.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }}
    }
}
