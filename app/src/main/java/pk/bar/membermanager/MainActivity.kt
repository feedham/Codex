package pk.bar.membermanager

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import androidx.compose.material3.windowsizeclass.*
import coil.compose.AsyncImage
import androidx.compose.ui.res.painterResource
import pk.bar.membermanager.data.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Bg=Color(0xFF080D16);private val Surface=Color(0xFF111A27);private val Accent=Color(0xFF57E6D0);private val Muted=Color(0xFF91A0B3)
class MainActivity:ComponentActivity(){@OptIn(ExperimentalMaterial3WindowSizeClassApi::class) override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);window.statusBarColor=android.graphics.Color.rgb(8,13,22);window.navigationBarColor=android.graphics.Color.rgb(8,13,22);val repo=(application as AdvocateApplication).repository;setContent{val vm:AppViewModel= viewModel(factory=object:androidx.lifecycle.ViewModelProvider.Factory{override fun <T:androidx.lifecycle.ViewModel> create(c:Class<T>):T{@Suppress("UNCHECKED_CAST") return AppViewModel(repo) as T}});vm.resolver=contentResolver;vm.appContext=applicationContext;val wc=calculateWindowSizeClass(this);AppShell(vm,wc.widthSizeClass)}}}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable private fun AppShell(vm:AppViewModel,width:WindowWidthSizeClass){
    val syncing by vm.syncing.collectAsState();val session by vm.session.collectAsState();val user by vm.user.collectAsState();val members by vm.members.collectAsState();val diffs by vm.diffs.collectAsState();val pending by vm.pending.collectAsState();val warning by vm.warnings.collectAsState();val message by vm.message.collectAsState();val context= LocalContext.current
    LaunchedEffect(session){vm.watchUser(session)}
    LaunchedEffect(message){if(message!=null){android.widget.Toast.makeText(context,message,android.widget.Toast.LENGTH_LONG).show();vm.clearMessage()}}
    val nav= rememberNavController();var startupHandled by remember{mutableStateOf(false)}
    LaunchedEffect(session){if(!startupHandled&&session!=null){nav.navigate("home"){popUpTo(0)};startupHandled=true}else if(session==null&&startupHandled){nav.navigate("signin"){popUpTo(0)};startupHandled=false}}
    val profilePicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->uri?.let{runCatching{context.contentResolver.takePersistableUriPermission(it,Intent.FLAG_GRANT_READ_URI_PERMISSION)};vm.photo(it)}}
    val pdfPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){it?.let(vm::importPdf)}
    val backupWriter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){it?.let(vm::exportBackup)}
    val backupReader=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){it?.let(vm::restoreBackup)}
    MaterialTheme(colorScheme=darkColorScheme(primary=Accent,secondary=Color(0xFF8D9BFF),background=Bg,surface=Surface,onBackground=Color.White,onSurface=Color.White)){
        if(pending!=null){ImportReview(pending!!,warning,vm,width)}else NavHost(navController=nav,startDestination="signin",modifier=Modifier.fillMaxSize(),enterTransition={fadeIn()},exitTransition={fadeOut()}){
            composable("signin"){AuthScreen(false,width,vm){nav.navigate("signup")}}
            composable("signup"){AuthScreen(true,width,vm){nav.navigate("signin")}}
            composable("home"){AdaptiveScaffold(nav,width){modifier->HomeScreen(user,members,width,modifier,onPhoto={profilePicker.launch(arrayOf("image/*"))},onDetail={nav.navigate("member/$it")},onInsights={nav.navigate("insights")},onCompose={nav.navigate("compose-message")},onSignOut=vm::signOut)}}
            composable("insights"){AdaptiveScaffold(nav,width){modifier->InsightsScreen(members,diffs,width,modifier,onOpenMember={nav.navigate("member/$it")})}}
            composable("compose-message"){AdaptiveScaffold(nav,width){modifier->WhatsAppComposer(members,modifier,onBack={nav.popBackStack()})}}
            composable("seniors"){AdaptiveScaffold(nav,width){modifier->SeniorMembersScreen(members,modifier,onOpenMember={nav.navigate("member/$it")})}}
            composable("credit"){AdaptiveScaffold(nav,width){modifier->CreditScreen(modifier)}}
            composable("backup"){AdaptiveScaffold(nav,width){modifier->BackupScreen(members.count{!it.removed},modifier,syncing=syncing,onBackendSync=vm::syncDirectory,onBackup={backupWriter.launch("respected-bar-members-backup.json")},onRestore={backupReader.launch(arrayOf("application/json"))},onImportPdf={pdfPicker.launch(arrayOf("application/pdf"))})}}
            composable("member/{id}",arguments=listOf(navArgument("id"){type=NavType.StringType})){entry->val id=entry.arguments?.getString("id").orEmpty();val member=members.find{it.id==id};if(member!=null)AdaptiveScaffold(nav,width){modifier->DetailScreen(member,vm,modifier,width,onBack={nav.popBackStack()})}}
        }
    }
}

@Composable private fun Input(value:String,onValue:(String)->Unit,label:String,keyboardType:androidx.compose.ui.text.input.KeyboardType=androidx.compose.ui.text.input.KeyboardType.Text){OutlinedTextField(value,onValue,Modifier.fillMaxWidth(),label={Text(label)},singleLine=true,shape=RoundedCornerShape(16.dp),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=keyboardType),colors=fieldColors())}
@Composable private fun fieldColors()=OutlinedTextFieldDefaults.colors(focusedBorderColor=Accent,unfocusedBorderColor=Color(0xFF293849),focusedLabelColor=Accent,unfocusedLabelColor=Muted,cursorColor=Accent)

@Composable private fun HomeScreen(user:UserEntity?,members:List<MemberEntity>,width:WindowWidthSizeClass,modifier:Modifier,onPhoto:()->Unit,onDetail:(String)->Unit,onInsights:()->Unit,onCompose:()->Unit,onSignOut:()->Unit){
    val visible=remember(members){members.filter{!it.removed}}
    var search by rememberSaveable{mutableStateOf("")}
    var sort by rememberSaveable{mutableStateOf(MEMBER_SORT_OPTIONS[0])}
    var sortExpanded by remember{mutableStateOf(false)}
    val filtered=remember(visible,search,sort){memberListRows(visible,search,sort)}
    LazyColumn(modifier.fillMaxSize().padding(horizontal=if(width==WindowWidthSizeClass.Compact)18.dp else 30.dp),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=18.dp)){
        item(key="member-controls"){Column(Modifier.fillMaxWidth()){
        Row(Modifier.fillMaxWidth().padding(top=14.dp,bottom=16.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Welcome,",color=Muted,style=MaterialTheme.typography.bodyMedium);Text("${user?.firstName.orEmpty()} ${user?.lastName.orEmpty()}".trim(),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold)};AsyncImage(model=user?.photoUri?:R.drawable.blank_adv_profile_picture,contentDescription="Upload profile picture",contentScale=ContentScale.Crop,placeholder=painterResource(R.drawable.blank_adv_profile_picture),error=painterResource(R.drawable.blank_adv_profile_picture),modifier=Modifier.size(50.dp).clip(CircleShape).background(Surface).clickable(onClick=onPhoto));IconButton(onClick=onSignOut){Icon(Icons.Default.Logout,"Sign out",tint=Muted)}}
        Spacer(Modifier.height(14.dp));Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onInsights,modifier=Modifier.size(48.dp).background(Surface,RoundedCornerShape(14.dp))){Icon(Icons.Default.Insights,"Insights",tint=Accent)};OutlinedButton(onClick=onCompose,modifier=Modifier.heightIn(min=48.dp)){Icon(Icons.Default.Chat,null);Spacer(Modifier.width(8.dp));Text("Compose message")}}
        Spacer(Modifier.height(12.dp))
        Surface(shape=RoundedCornerShape(18.dp),color=Color(0xFF142638),border=BorderStroke(1.dp,Accent.copy(alpha=0.6f))){
            Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text("Search Bar Members",color=Accent,style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold)
                OutlinedTextField(search,{search=it},Modifier.fillMaxWidth(),singleLine=true,
                    leadingIcon={Icon(Icons.Default.Search,null,tint=Accent)},
                    trailingIcon={if(search.isNotEmpty())IconButton(onClick={search=""}){Icon(Icons.Default.Close,"Clear search",tint=Accent)}},
                    label={Text("Name / Cell No. / Date of Enrollment")},
                    placeholder={Text("Enter a name, cell, date or year",color=Muted)},
                    shape=RoundedCornerShape(12.dp),
                    colors=OutlinedTextFieldDefaults.colors(focusedContainerColor=Bg,unfocusedContainerColor=Bg,focusedBorderColor=Accent,unfocusedBorderColor=Muted,focusedLabelColor=Accent,unfocusedLabelColor=Muted,cursorColor=Accent))
                Text("Search as you type | D.O.E: DD/MM/YYYY or year",color=Color(0xFFBCCBD9),style=MaterialTheme.typography.bodySmall)
            }
        }
        Box{OutlinedButton(onClick={sortExpanded=true},Modifier.fillMaxWidth().padding(top=8.dp),shape=RoundedCornerShape(14.dp)){Text("Sort list by: $sort",Modifier.weight(1f));Icon(Icons.Default.ArrowDropDown,null)};DropdownMenu(sortExpanded,{sortExpanded=false}){MEMBER_SORT_OPTIONS.forEach{option->DropdownMenuItem(text={Text(option)},onClick={sort=option;sortExpanded=false})}}}
        Text("MEMBERS · ${filtered.size}",Modifier.padding(top=16.dp,bottom=8.dp),color=Muted,style=MaterialTheme.typography.labelMedium,letterSpacing=1.4.sp)
        }}
        if(filtered.isEmpty())item{EmptyState("No matching members",if(visible.isEmpty())"Import the registered members PDF to get started." else "Try another name, cell number, or enrollment date.")} else items(filtered,key={it.id}){m->MemberRow(m){onDetail(m.id)}}
    }
}

@Composable private fun MemberRow(m:MemberEntity,onClick:()->Unit){Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(Surface).clickable(onClick=onClick).padding(horizontal=14.dp,vertical=11.dp),verticalAlignment=Alignment.CenterVertically){AsyncImage(model=m.photoPath?:R.drawable.blank_adv_profile_picture,contentDescription="Advocate profile picture",contentScale=ContentScale.Crop,modifier=Modifier.size(46.dp).clip(CircleShape).background(Color(0xFF1A2635)),placeholder=painterResource(R.drawable.blank_adv_profile_picture),error=painterResource(R.drawable.blank_adv_profile_picture));Spacer(Modifier.width(13.dp));Column(Modifier.weight(1f)){Text(m.fullName(),fontWeight=FontWeight.Medium);Text("${m.memberNumber?.let{"# $it · "}.orEmpty()}${m.status}",color=Muted,style=MaterialTheme.typography.labelSmall);Text("D.O.E: ${m.enrollmentDate?:"Not provided"}",color=Muted,style=MaterialTheme.typography.labelSmall)};if(m.called)Icon(Icons.Default.CheckCircle,null,tint=Accent,modifier=Modifier.size(19.dp));Icon(Icons.Default.ChevronRight,null,tint=Muted)}}
@Composable private fun InfoCard(value:String,label:String,modifier:Modifier){Column(modifier.clip(RoundedCornerShape(17.dp)).background(Surface).padding(15.dp)){Text(value,color=Accent,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text(label,color=Muted,style=MaterialTheme.typography.labelMedium)}}
@Composable private fun AdaptiveScaffold(nav:androidx.navigation.NavController,width:WindowWidthSizeClass,content:@Composable (Modifier)->Unit){
    Scaffold(containerColor=Bg,topBar={
        Surface(color=Surface){
            Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top+WindowInsetsSides.Horizontal))){
                Row(Modifier.fillMaxWidth().padding(horizontal=if(width==WindowWidthSizeClass.Compact)18.dp else 30.dp).padding(top=12.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically){AsyncImage(model=R.drawable.dbaksr_logo,contentDescription="District Bar Association, Kasur logo",contentScale=ContentScale.Crop,modifier=Modifier.size(48.dp).clip(CircleShape).background(Color.White));Spacer(Modifier.width(12.dp));Column{Text("Bar Members List",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,color=Accent);Text("District Bar Association, Kasur",style=MaterialTheme.typography.labelMedium,color=Muted)}}
                NavigationChoices(nav)
            }
        }
    }){padding->content(Modifier.padding(padding).consumeWindowInsets(padding))}
}
@Composable private fun NavigationChoices(nav:androidx.navigation.NavController){
    val current=nav.currentBackStackEntryAsState().value?.destination?.route
    val memberRoute=current?.startsWith("member/")==true
    val seniorSelected=current=="seniors"||(memberRoute&&nav.previousBackStackEntry?.destination?.route=="seniors")
    val memberSelected=current=="home"||current=="compose-message"||(memberRoute&&!seniorSelected)
    val destinations=listOf(Triple("home","Bar Member List",R.drawable.advocate_icon),Triple("seniors","Top 50 Bar Members List",R.drawable.sr_advocate_icon),Triple("insights","Insights",R.drawable.insight_icon),Triple("backup","Backup / Restore",R.drawable.backup_icon),Triple("credit","Credit",R.drawable.credit_icon))
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal=8.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically){
        destinations.forEach{(route,label,image)->
            val active=when(route){"home"->memberSelected;"seniors"->seniorSelected;else->current==route}
            val title=when(route){"home"->"Bar Members";"seniors"->"Top 50";"backup"->"Backup / Restore";else->label}
            Column(Modifier.weight(1f).fillMaxHeight().heightIn(min=84.dp).semantics{selected=active}.background(if(active)Accent.copy(alpha=0.18f)else Color.Transparent,RoundedCornerShape(14.dp)).clickable(role=Role.Tab){nav.navigate(route){popUpTo("home"){inclusive=false};launchSingleTop=true}}.padding(horizontal=2.dp,vertical=6.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
                AsyncImage(model=image,contentDescription=null,contentScale=ContentScale.Fit,modifier=Modifier.size(32.dp).background(Color(0xFFF3F6F8),RoundedCornerShape(7.dp)).padding(3.dp))
                Spacer(Modifier.height(4.dp))
                Text(title,color=if(active)Accent else Muted,style=MaterialTheme.typography.labelSmall,textAlign=TextAlign.Center,maxLines=3,modifier=Modifier.heightIn(min=32.dp))
            }
        }
    }
}
@Composable private fun BackupScreen(memberCount:Int,modifier:Modifier,syncing:Boolean,onBackendSync:(String,String)->Unit,onBackup:()->Unit,onRestore:()->Unit,onImportPdf:()->Unit){Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=22.dp,vertical=24.dp)){Text("Backup / Restore the List",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold);Text("$memberCount active members",color=Muted,modifier=Modifier.padding(top=6.dp,bottom=18.dp));Column(Modifier.fillMaxWidth().widthIn(max=640.dp).clip(RoundedCornerShape(18.dp)).background(Surface).padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("Protect your member list",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold);Text("A backup includes all member records, CNIC, Area Zone, Gender, group affiliations, called flags, support statuses, member portraits, and import differences. Account passwords and your sign-in session are excluded.",color=Muted,style=MaterialTheme.typography.bodySmall);Button(onClick=onBackup,Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(14.dp)){Icon(Icons.Default.FileDownload,null);Spacer(Modifier.width(8.dp));Text("Download backup")};OutlinedButton(onClick=onRestore,Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(14.dp)){Icon(Icons.Default.FileUpload,null);Spacer(Modifier.width(8.dp));Text("Restore backup")};OutlinedButton(onClick=onImportPdf,Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(14.dp)){Icon(Icons.Default.PictureAsPdf,null);Spacer(Modifier.width(8.dp));Text("Import updated PDF")};BackendDirectoryButton(syncing,onBackendSync)}}}

@Composable private fun DetailScreen(m:MemberEntity,vm:AppViewModel,modifier:Modifier,width:WindowWidthSizeClass,onBack:()->Unit){
    val context=LocalContext.current
    var area by remember(m.id){mutableStateOf(m.area.orEmpty())}
    var cnic by remember(m.id){mutableStateOf(m.cnic.orEmpty())}
    var referenceName by remember(m.id){mutableStateOf(m.referenceName.orEmpty())}
    var editingReference by remember(m.id){mutableStateOf(false)}
    var gender by remember(m.id){mutableStateOf(m.gender.orEmpty())}
    val genderSuggestion=remember(m.id,m.firstName,m.lastName,m.gender){if(m.gender.isNullOrBlank())NameGender.suggest(context,m.fullName()) else null}
    var groupAffiliation by remember(m.id){mutableStateOf(m.groupAffiliation.ifBlank{"Independent Voter"})}
    var genderExpanded by remember{mutableStateOf(false)}
    var groupExpanded by remember{mutableStateOf(false)}
    var statusExpanded by remember{mutableStateOf(false)}
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=22.dp,vertical=24.dp),verticalArrangement=Arrangement.spacedBy(13.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){AsyncImage(model=m.photoPath?:R.drawable.blank_adv_profile_picture,contentDescription="Advocate profile picture",contentScale=ContentScale.Crop,modifier=Modifier.size(70.dp).clip(CircleShape).background(Surface),placeholder=painterResource(R.drawable.blank_adv_profile_picture),error=painterResource(R.drawable.blank_adv_profile_picture));Spacer(Modifier.width(16.dp));Column{Text(m.fullName(),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold);Text("Member ${m.memberNumber?.let{"# $it"}?:"record"}",color=Muted)}}
        TextButton(onClick=onBack){Icon(Icons.Default.ArrowBack,null);Spacer(Modifier.width(6.dp));Text("Back")}
        ContactCallActions(m){vm.called(m.id,true)}
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Surface).clickable{vm.called(m.id,!m.called)}.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.CheckCircle,null,tint=if(m.called)Accent else Muted);Spacer(Modifier.width(12.dp));Text("Called",Modifier.weight(1f));Switch(m.called,{vm.called(m.id,it)})}
        Text("My Support Status",color=Muted,style=MaterialTheme.typography.labelMedium,letterSpacing=1.2.sp)
        Box{OutlinedButton(onClick={statusExpanded=true},Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)){Text(m.status,Modifier.weight(1f));Icon(Icons.Default.ArrowDropDown,null)};DropdownMenu(statusExpanded,{statusExpanded=false}){listOf("My Voter","50/50","More Working Required").forEach{DropdownMenuItem(text={Text(it)},onClick={statusExpanded=false;vm.status(m.id,it)})}}}
        Text("ADVOCATE PROFILE",color=Accent,style=MaterialTheme.typography.labelLarge,letterSpacing=1.15.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=4.dp))
        OutlinedTextField(value=cnic,onValueChange={cnic=it},modifier=Modifier.fillMaxWidth(),label={Text("CNIC")},placeholder={Text("Enter CNIC")},singleLine=true,shape=RoundedCornerShape(14.dp),colors=fieldColors())
        OutlinedTextField(value=area,onValueChange={area=it},modifier=Modifier.fillMaxWidth(),label={Text("Area Zone")},placeholder={Text("Area Zone")},singleLine=true,shape=RoundedCornerShape(14.dp),colors=fieldColors())
        Box{OutlinedButton(onClick={genderExpanded=true},Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)){Text(gender.ifBlank{"Select gender"},Modifier.weight(1f));Icon(Icons.Default.ArrowDropDown,null)};DropdownMenu(genderExpanded,{genderExpanded=false}){listOf("Not provided","Female","Male","Other").forEach{option->DropdownMenuItem(text={Text(option)},onClick={genderExpanded=false;gender=if(option=="Not provided")"" else option})}}}
        if(m.gender.isNullOrBlank()){
            Text(if(genderSuggestion!=null)"Name-based suggestion: $genderSuggestion (estimate). Review before saving." else "No reliable name-based suggestion. Select gender manually.",color=Muted,style=MaterialTheme.typography.bodySmall)
            if(genderSuggestion!=null)TextButton(onClick={gender=genderSuggestion}){Text("Use $genderSuggestion suggestion")}
        }
        Text("You can correct gender above, then press Submit. Saved choices take priority over suggestions.",color=Muted,style=MaterialTheme.typography.bodySmall)
        Text("GROUP AFFILIATION",color=Muted,style=MaterialTheme.typography.labelMedium,letterSpacing=1.2.sp)
        Box{OutlinedButton(onClick={groupExpanded=true},Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)){Text(groupAffiliation,Modifier.weight(1f),maxLines=1);Icon(Icons.Default.ArrowDropDown,null)};DropdownMenu(groupExpanded,{groupExpanded=false}){GROUP_AFFILIATIONS.forEach{option->DropdownMenuItem(text={Text(option)},onClick={groupExpanded=false;groupAffiliation=option})}}}
        DetailField("Date of enrollment",m.enrollmentDate);DetailField("Cell number",m.cellNumber);DetailField("Chamber / office",m.chamber);DetailField("Biometric",m.biometric);DetailField("Parent / guardian",m.parentName);DetailField("Residence",m.residence)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Surface).padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
            Text("Reference Name",color=Muted,style=MaterialTheme.typography.labelMedium)
            Text(referenceName.ifBlank{"No Reference Yet"},color=if(referenceName.isBlank())Muted else MaterialTheme.colorScheme.onSurface)
            TextButton(onClick={editingReference=true}){Text(if(referenceName.isBlank())"Add Reference" else "Edit Reference")}
            if(editingReference)OutlinedTextField(value=referenceName,onValueChange={referenceName=it.take(120)},modifier=Modifier.fillMaxWidth(),label={Text("Reference Name")},placeholder={Text("Add Reference")},singleLine=true,colors=fieldColors(),supportingText={Text("Press Submit to save. Leave blank to clear the reference.")})
        }
        Text("Submit to save profile details and update Area Zone, Gender, and group insights.",color=Muted,style=MaterialTheme.typography.bodySmall)
        Button(onClick={vm.profileDetails(m.id,area,gender.ifBlank{null},groupAffiliation,cnic,referenceName)},Modifier.fillMaxWidth().height(50.dp),shape=RoundedCornerShape(14.dp)){Text("Submit")}
        if(m.needsReview)Text("This record needs review. Some fields were missing from the PDF.",color=Color(0xFFFFC66D))
    }
}
@Composable private fun DetailField(label:String,value:String?){Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Surface).padding(14.dp)){Text(label,color=Muted,style=MaterialTheme.typography.labelMedium);Text(value?.takeIf{it.isNotBlank()}?:"Not provided",Modifier.padding(top=3.dp))}}

@Composable private fun InsightsScreen(members:List<MemberEntity>,diffs:List<ImportDiffEntity>,width:WindowWidthSizeClass,modifier:Modifier,onOpenMember:(String)->Unit){
    val active=members.filter{!it.removed};val senior=seniorMemberRows(active,20)
    var drill by remember{mutableStateOf<Pair<String,String>?>(null)}
    val chartValues=mapOf(
        "Area Zone" to active.map{it.area?.takeIf(String::isNotBlank)?:"Not provided"},
        "Chamber" to active.map{it.chamber?.takeIf(String::isNotBlank)?:"Not provided"},
        "Biometric" to active.map{it.biometric?.takeIf(String::isNotBlank)?:"Not provided"},
        "Gender" to active.map{it.gender?.takeIf(String::isNotBlank)?:"Not provided"},
        "Group affiliation" to active.map{it.groupAffiliation.ifBlank{"Independent Voter"}},
        "My Support Status" to active.map{it.status},
        "Call" to active.map{if(it.called)"Called" else "Not called"}
    )
    var enrollmentOpen by rememberSaveable{mutableStateOf(false)}
    var enrollmentPath by rememberSaveable{mutableStateOf(listOf<String>())}
    if(enrollmentOpen){EnrollmentInsights(active,enrollmentPath,modifier,onPath={enrollmentPath=it},onClose={enrollmentOpen=false},onOpenMember=onOpenMember);return}
    val selected=drill
    if(selected!=null){
        val (dimension,value)=selected
        val filtered=active.filter{member->when(dimension){"Area Zone"->(member.area?.takeIf(String::isNotBlank)?:"Not provided")==value;"Chamber"->(member.chamber?.takeIf(String::isNotBlank)?:"Not provided")==value;"Biometric"->(member.biometric?.takeIf(String::isNotBlank)?:"Not provided")==value;"Gender"->(member.gender?.takeIf(String::isNotBlank)?:"Not provided")==value;"Group affiliation"->member.groupAffiliation.ifBlank{"Independent Voter"}==value;"My Support Status"->member.status==value;"Call"->(if(member.called)"Called" else "Not called")==value;else->false}}
        LazyColumn(modifier.fillMaxSize().padding(horizontal=20.dp),contentPadding=PaddingValues(top=18.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(11.dp)){
            item{TextButton(onClick={drill=null}){Icon(Icons.Default.ArrowBack,null);Spacer(Modifier.width(6.dp));Text("All insights")}}
            item{Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface).padding(17.dp)){Text(dimension.uppercase(),color=Accent,style=MaterialTheme.typography.labelLarge,letterSpacing=1.1.sp);Text(value,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold);Text("${filtered.size} members · open a profile for full details",color=Muted,style=MaterialTheme.typography.bodySmall)}}
            if(filtered.isEmpty())item{EmptyState("No matching members","Choose another chart category.")}else items(filtered,key={it.id}){member->MemberRow(member){onOpenMember(member.id)}}
        }
        return
    }
    val dimensions=chartValues.toList().filterNot{it.first=="Biometric"||it.first=="Group affiliation"}
    LazyVerticalStaggeredGrid(columns=StaggeredGridCells.Fixed(if(width==WindowWidthSizeClass.Compact)1 else 2),modifier=modifier.fillMaxSize().padding(horizontal=20.dp),contentPadding=PaddingValues(top=18.dp,bottom=24.dp),verticalItemSpacing=12.dp,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        item(span=StaggeredGridItemSpan.FullLine){Column{Text("Insights",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.SemiBold);Text("${active.size} active members - tap a graph to drill down",color=Muted)}}
        item{GraphBreakdown("Biometric",chartValues.getValue("Biometric")){label->drill="Biometric" to label}}
        item{GraphBreakdown("Group affiliation",chartValues.getValue("Group affiliation")){label->drill="Group affiliation" to label}}
        item{Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface).padding(16.dp)){SectionTitle("TOP 20 · EARLIEST ENROLLMENT");if(senior.isEmpty())Text("No enrollment dates available",color=Muted)else senior.forEachIndexed{index,m->Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable{onOpenMember(m.id)}.padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically){Text("${index+1}",color=Accent,modifier=Modifier.width(30.dp));AsyncImage(model=m.photoPath?:R.drawable.blank_adv_profile_picture,contentDescription="${m.fullName()} profile picture",contentScale=ContentScale.Crop,placeholder=painterResource(R.drawable.blank_adv_profile_picture),error=painterResource(R.drawable.blank_adv_profile_picture),modifier=Modifier.size(38.dp).clip(CircleShape).background(Surface));Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(m.fullName());Text("Member #${m.memberNumber?:"—"}",color=Muted,style=MaterialTheme.typography.labelSmall)};Text(m.enrollmentDate.orEmpty(),color=Muted,style=MaterialTheme.typography.labelSmall)}}}}
        item{EnrollmentGraph(active,emptyList()){key->enrollmentPath=listOf(key);enrollmentOpen=true}}
        dimensions.forEach{(title,values)->item{GraphBreakdown(title,values){label->drill=title to label}}}
        item{Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface).padding(16.dp)){SectionTitle("LATEST IMPORT DIFFERENCES · ${diffs.size}");if(diffs.isEmpty())Text("No import changes yet.",color=Muted)else diffs.take(8).forEach{d->Column(Modifier.padding(vertical=5.dp)){Row(verticalAlignment=Alignment.CenterVertically){Text(d.kind,color=if(d.kind=="Added")Accent else if(d.kind=="Removed")Color(0xFFFF8895) else Color(0xFFFFC66D),fontWeight=FontWeight.SemiBold);Spacer(Modifier.width(9.dp));Text(d.memberName,fontWeight=FontWeight.Medium)};Text(d.details,color=Muted,style=MaterialTheme.typography.bodySmall);Text(d.importDate,color=Muted,style=MaterialTheme.typography.labelSmall)}}}}
    }
}
@Composable private fun EnrollmentGraph(members:List<MemberEntity>,path:List<String>,onSelect:(String)->Unit){
    val counts=enrollmentGroups(members,path);val maximum=(counts.maxOfOrNull{it.second}?:1).coerceAtLeast(1)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface).padding(15.dp)){
        SectionTitle(when(path.size){0->"D.O.E · YEAR-WISE ENROLLMENTS";1->"ENROLLMENT MONTHS";else->"ENROLLMENT DATES"})
        Text("Year → Month → Date → Members → Profile",color=Muted,style=MaterialTheme.typography.bodySmall)
        Text("${enrollmentRows(members,path).size} members",color=Muted,style=MaterialTheme.typography.bodySmall)
        if(counts.isEmpty())Text("No enrollment data available",color=Muted)
        Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState())){
            counts.forEach{(key,count)->
                val label=if(key==UNKNOWN_ENROLLMENT)key else when(path.size){1->ENROLLMENT_MONTHS[key.toInt()-1];2->"${path[0]}-${path[1]}-$key";else->key}
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).clickable{onSelect(key)}.padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                    Row{Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall);Text("$count",color=Accent,style=MaterialTheme.typography.labelMedium)}
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(Color(0xFF253244))){Box(Modifier.fillMaxWidth(count.toFloat()/maximum).fillMaxHeight().background(Accent))}
                }
            }
        }
    }
}
@Composable private fun EnrollmentInsights(members:List<MemberEntity>,path:List<String>,modifier:Modifier,onPath:(List<String>)->Unit,onClose:()->Unit,onOpenMember:(String)->Unit){
    androidx.activity.compose.BackHandler{if(path.isEmpty())onClose()else onPath(path.dropLast(1))}
    val rows=enrollmentRows(members,path);val leaf=path.size==3||path.firstOrNull()==UNKNOWN_ENROLLMENT
    LazyColumn(modifier.fillMaxSize().padding(horizontal=20.dp),contentPadding=PaddingValues(top=18.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Row{TextButton(onClick=onClose){Text("All insights")};TextButton(onClick={if(path.isEmpty())onClose()else onPath(path.dropLast(1))}){Icon(Icons.Default.ArrowBack,null);Text("Back one level")}}}
        item{Text("D.O.E · Date of enrollment",color=Accent);Text(if(path.isEmpty())"All enrollment years" else path.mapIndexed{i,key->if(i==1)ENROLLMENT_MONTHS[key.toInt()-1]else key}.joinToString(" / "),style=MaterialTheme.typography.headlineSmall);Text("${rows.size} members",color=Muted)}
        if(!leaf)item{EnrollmentGraph(members,path){onPath(path+it)}}
        else if(rows.isEmpty())item{EmptyState("No matching members","Choose another enrollment date.")}
        else items(rows.sortedBy{it.fullName()},key={it.id}){m->Column{MemberRow(m){onOpenMember(m.id)};Text("D.O.E ${m.enrollmentDate?:"Not provided"}",Modifier.padding(start=14.dp,top=3.dp),color=Muted,style=MaterialTheme.typography.bodySmall)}}
    }
}
@Composable private fun GraphBreakdown(title:String,values:List<String>,onSelect:(String)->Unit){val counts=values.groupingBy{it.trim().ifBlank{"Not provided"}}.eachCount().toList().sortedByDescending{it.second}.take(8);val maximum=(counts.maxOfOrNull{it.second}?:1).coerceAtLeast(1);Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface).padding(15.dp)){SectionTitle(title.uppercase());if(counts.isEmpty())Text("No data",color=Muted)else counts.forEach{(label,count)->Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).clickable{onSelect(label)}.padding(vertical=5.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){Row(verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f),maxLines=1,style=MaterialTheme.typography.bodySmall);Text("$count",color=Accent,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.labelMedium)};Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(Color(0xFF253244))){Box(Modifier.fillMaxWidth(count.toFloat()/maximum).fillMaxHeight().background(Accent))}}}}}
@Composable private fun SectionTitle(text:String){Text(text,color=Accent,style=MaterialTheme.typography.labelLarge,letterSpacing=1.15.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(bottom=8.dp))}
@Composable private fun EmptyState(title:String,subtitle:String){Column(Modifier.fillMaxWidth().padding(vertical=36.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.People,null,tint=Muted,modifier=Modifier.size(34.dp));Spacer(Modifier.height(10.dp));Text(title,fontWeight=FontWeight.SemiBold);Text(subtitle,color=Muted,style=MaterialTheme.typography.bodySmall)}}

@Composable private fun ImportReview(rows:List<MemberEntity>,warnings:List<String>,vm:AppViewModel,width:WindowWidthSizeClass){Column(Modifier.fillMaxSize().background(Bg).padding(horizontal=20.dp,vertical=18.dp)){
    Text("Review member import",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold);Text("${rows.size} records · ${rows.count{it.needsReview}} need review",color=Muted,modifier=Modifier.padding(top=5.dp,bottom=14.dp))
    if(warnings.isNotEmpty())Text("${warnings.size} records have missing identifiers or enrollment dates. These are flagged for follow-up.",color=Color(0xFFFFC66D),style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(bottom=10.dp))
    Text("Members missing from this list will be flagged as removed when you apply it.",color=Muted,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(bottom=10.dp))
    LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)){items(rows){m->Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Surface).padding(12.dp)){Row{Text(m.fullName(),Modifier.weight(1f),fontWeight=FontWeight.Medium);if(m.needsReview)Text("REVIEW",color=Color(0xFFFFC66D),style=MaterialTheme.typography.labelSmall)};Text("#${m.memberNumber?:"—"} · ${m.cellNumber?:"No cell"} · D.O.E ${m.enrollmentDate?:"—"}",color=Muted,style=MaterialTheme.typography.bodySmall);if(m.chamber!=null)Text(m.chamber, color=Muted,style=MaterialTheme.typography.bodySmall)}}}
    Row(Modifier.padding(top=12.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)){OutlinedButton(onClick=vm::cancelImport,Modifier.weight(1f).height(50.dp)){Text("Cancel")};Button(onClick=vm::confirmImport,Modifier.weight(1f).height(50.dp)){Text("Apply import")}}
}}
