package pk.bar.membermanager

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pk.bar.membermanager.data.*

class AppViewModel(private val repo:Repository):ViewModel(){
    val session=repo.session.catch{_message.value="Could not read sign-in session: ${it.message}";emit(null)}.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),null)
    val members=repo.members().catch{_message.value="Could not load members: ${it.message}";emit(emptyList())}.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val diffs=repo.diffs().catch{_message.value="Could not load import history: ${it.message}";emit(emptyList())}.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    private val _user=MutableStateFlow<UserEntity?>(null);val user=_user.asStateFlow()
    private val _pending=MutableStateFlow<List<MemberEntity>?>(null);val pending=_pending.asStateFlow()
    private val _warnings=MutableStateFlow<List<String>>(emptyList());val warnings=_warnings.asStateFlow()
    private val _message=MutableStateFlow<String?>(null);val message=_message.asStateFlow()
    init{viewModelScope.launch(Dispatchers.IO){runCatching{repo.seedDefaultMembersIfEmpty()}.onFailure{_message.value="Could not load the built-in member list: ${it.message}"}}}
    private val _syncing=MutableStateFlow(false);val syncing=_syncing.asStateFlow()
    fun syncDirectory(endpoint:String,token:String){
        if(_syncing.value)return
        _syncing.value=true
        viewModelScope.launch{
            try{
                val rows=DirectoryApi.download(endpoint,token)
                repo.mergeDirectory(rows)
                _message.value="Downloaded "+rows.size+" members. Local campaign details were kept."
            }catch(error:kotlinx.coroutines.CancellationException){throw error}
            catch(error:Exception){_message.value="Could not download directory: "+error.message}
            finally{_syncing.value=false}
        }
    }
    fun clearMessage(){_message.value=null}
    private val _authBusy=MutableStateFlow(false);val authBusy=_authBusy.asStateFlow()
    private val _recoveryCode=MutableStateFlow<String?>(null);val recoveryCode=_recoveryCode.asStateFlow()
    private var pendingAuthEmail:String?=null
    fun authenticate(signUp:Boolean,first:String,last:String,email:String,password:String,refreshRecovery:Boolean=false){
        if(_authBusy.value)return
        _authBusy.value=true
        viewModelScope.launch{
            try{
                val result=repo.authenticate(signUp,first,last,email,password,refreshRecovery)
                if(result.recoveryCode!=null){pendingAuthEmail=result.email;_recoveryCode.value=result.recoveryCode}
                else{repo.startSession(result.email);loadUser(result.email)}
            }
            catch(error:kotlinx.coroutines.CancellationException){throw error}
            catch(error:Exception){_message.value=error.message?:"Could not sign in."}
            finally{_authBusy.value=false}
        }
    }
    fun finishRecovery(){
        if(_authBusy.value)return
        _authBusy.value=true
        viewModelScope.launch{
            try{pendingAuthEmail?.let{repo.startSession(it);loadUser(it)};pendingAuthEmail=null;_recoveryCode.value=null}
            catch(error:Exception){_message.value=error.message?:"Could not continue."}
            finally{_authBusy.value=false}
        }
    }
    fun resetPassword(email:String,code:String,password:String,confirmation:String){
        if(_authBusy.value)return
        _authBusy.value=true
        viewModelScope.launch{
            try{val next=repo.resetPassword(email,code,password,confirmation);pendingAuthEmail=null;_recoveryCode.value=next;_message.value="Password reset. Save your new recovery code, then sign in."}
            catch(error:kotlinx.coroutines.CancellationException){throw error}
            catch(error:Exception){_message.value=error.message?:"Could not reset password."}
            finally{_authBusy.value=false}
        }
    }
    private suspend fun loadUser(email:String){_user.value=repo.currentUser(email)}
    fun watchUser(email:String?){if(email!=null)viewModelScope.launch{runCatching{loadUser(email)}.onFailure{_message.value="Could not load account: ${it.message}"}}else _user.value=null}
    fun signOut(){viewModelScope.launch{runCatching{repo.signOut();_user.value=null}.onFailure{_message.value="Could not sign out: ${it.message}"}}}
    fun photo(uri:Uri){val email=_user.value?.email?:return;viewModelScope.launch{runCatching{repo.setPhoto(email,uri.toString());loadUser(email)}.onFailure{_message.value=it.message}}}
    fun importPdf(uri:Uri){viewModelScope.launch{runCatching{withContext(Dispatchers.IO){PdfMemberParser.parse(appContext,resolver,uri)}.also{require(it.records.isNotEmpty()){"No member cards were found. Choose a selectable-text member directory PDF."}}}.onSuccess{_pending.value=it.records;_warnings.value=it.warnings;_message.value="${it.records.size} records found. Review the import before saving."}.onFailure{_message.value="Could not read PDF: ${it.message}"}}}
    lateinit var resolver:android.content.ContentResolver
    lateinit var appContext:android.content.Context
    fun confirmImport(){val rows=_pending.value?:return;viewModelScope.launch{runCatching{repo.applyImport(rows)}.onSuccess{_message.value="Import complete: ${it.added} added, ${it.changed} changed, ${it.removed} removed. ${it.review} rows need review.";_pending.value=null}.onFailure{_message.value=it.message}}}
    fun cancelImport(){_pending.value=null}
    fun called(id:String,value:Boolean)=viewModelScope.launch{runCatching{repo.setCalled(id,value)}.onFailure{_message.value="Could not save called status: ${it.message}"}}
    fun status(id:String,value:String)=viewModelScope.launch{runCatching{repo.setStatus(id,value)}.onFailure{_message.value="Could not save campaign status: ${it.message}"}}
    fun profileDetails(id:String,area:String?,gender:String?,groupAffiliation:String,cnic:String?,referenceName:String?)=viewModelScope.launch{runCatching{repo.setProfileDetails(id,area,gender,groupAffiliation,cnic,referenceName)}.onSuccess{_message.value="Advocate profile saved. Insights have been updated."}.onFailure{_message.value="Could not save advocate profile: ${it.message}"}}
    fun exportBackup(uri:Uri){viewModelScope.launch{runCatching{withContext(Dispatchers.IO){resolver.openOutputStream(uri)?.bufferedWriter()?.use{it.write(repo.backupJson())}?:error("Could not create backup file.")}}.onSuccess{_message.value="Member list backup saved."}.onFailure{_message.value="Could not save backup: ${it.message}"}}}
    fun restoreBackup(uri:Uri){viewModelScope.launch{runCatching{withContext(Dispatchers.IO){resolver.openInputStream(uri)?.bufferedReader()?.use{repo.restoreBackup(it.readText())}?:error("Could not open backup file.")}}.onSuccess{_message.value="Restored ${it} member records and their group affiliations."}.onFailure{_message.value="Could not restore backup: ${it.message}"}}}
    suspend fun member(id:String)=repo.member(id)
    suspend fun reviewRows()=repo.reviewRows()
}
