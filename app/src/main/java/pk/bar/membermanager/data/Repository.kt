package pk.bar.membermanager.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.withTransaction
import android.util.Base64
import java.io.File
import org.json.JSONObject
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import org.json.JSONArray

val GROUP_AFFILIATIONS=listOf("Independent Voter","Ittefaq Group of Lawyers","Ittehad Group of Lawyers","Azad Group of Lawyers")

data class LocalAuthResult(val email:String,val recoveryCode:String?=null)

private val Context.sessionStore by preferencesDataStore("session")
class Repository(private val context:Context,private val db:AppDatabase){
    private val sessionKey=stringPreferencesKey("email")
    val session=context.sessionStore.data.catch{if(it is IOException)emit(emptyPreferences())else throw it}.map{it[sessionKey]?.takeIf{email->db.users().byEmail(email)!=null}}
    fun members()=db.members().observeAll()
    fun diffs()=db.diffs().observe()
    suspend fun seedDefaultMembersIfEmpty(){
        val dao=db.members();if(dao.countAll()>0)return
        val json=context.assets.open("default-members.json").bufferedReader().use{JSONArray(it.readText())}
        val rows=(0 until json.length()).map{index->
            val item=json.getJSONObject(index)
            fun optional(name:String):String?=if(item.isNull(name))null else item.optString(name).takeIf{it.isNotBlank()}
            // The source PDF has no dedicated Area or Gender columns, so those fields stay unknown instead of being guessed from addresses or names.
            val photo=optional("photo")?.let{"file:///android_asset/$it"}
            val id=item.getString("id")
            MemberEntity(id=id,stableKey=id,firstName=item.getString("firstName"),lastName=item.optString("lastName"),cellNumber=optional("cell"),enrollmentDate=optional("doe"),area=optional("area"),chamber=optional("chamber"),biometric=optional("biometric"),gender=optional("gender"),photoPath=photo,parentName=optional("parent"),residence=optional("residence"),memberNumber=optional("memberNo"),called=false,status="More Working Required",removed=false,needsReview=item.optBoolean("needsReview"),cnic=optional("cnic"))
        }
        dao.putAll(rows)
    }
    suspend fun mergeDirectory(rows:List<MemberEntity>)=db.withTransaction{
        val old=db.members().allRecords().associateBy{it.stableKey}
        db.members().putAll(rows.map{fresh->mergeDirectoryMember(fresh,old[fresh.stableKey])})
    }
    suspend fun currentUser(email:String)=db.users().byEmail(email)
    suspend fun authenticate(signUp:Boolean,first:String,last:String,email:String,password:String,refreshRecovery:Boolean=false):LocalAuthResult {
        val normalized=email.trim().lowercase(Locale.ROOT)
        require(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(normalized)){"Enter a valid email address."}
        require(password.length>=6){"Enter a password of at least 6 characters."}
        val existing=db.users().byEmail(normalized)
        var account=existing
        if(signUp){
            require(first.isNotBlank()&&last.isNotBlank()){"Enter your first and last name."}
            require(existing==null){"An account already exists. Please sign in."}
            account=UserEntity(normalized,first.trim(),last.trim(),withContext(Dispatchers.Default){hash(password)})
        }else{
            require(existing!=null&&withContext(Dispatchers.Default){verify(password,existing.passwordHash)}){"Email or password is incorrect."}
        }
        var saved=requireNotNull(account)
        val code=if(saved.recoveryHash==null||refreshRecovery)newRecoveryCode() else null
        if(code!=null)saved=saved.copy(recoveryHash=withContext(Dispatchers.Default){hash(normalizeRecoveryCode(code))})
        if(signUp)db.users().insert(saved)else if(code!=null)db.users().update(saved)
        return LocalAuthResult(normalized,code)
    }
    suspend fun startSession(email:String){context.sessionStore.edit{it[sessionKey]=email;it[stringPreferencesKey("local_login")]=UUID.randomUUID().toString()}}
    private fun newRecoveryCode()=ByteArray(20).also{SecureRandom().nextBytes(it)}.joinToString(""){"%02x".format(it)}.uppercase(Locale.ROOT).chunked(4).joinToString("-")
    private fun normalizeRecoveryCode(code:String)=code.replace(Regex("[-\\s]"),"").lowercase(Locale.ROOT)
    suspend fun resetPassword(email:String,code:String,password:String,confirmation:String):String {
        require(password.length>=6){"Enter a password of at least 6 characters."}
        require(password==confirmation){"Passwords do not match."}
        val normalized=email.trim().lowercase(Locale.ROOT)
        val account=db.users().byEmail(normalized)
        val secret=normalizeRecoveryCode(code)
        require(secret.matches(Regex("^[a-f0-9]{40}$"))&&account?.recoveryHash!=null&&withContext(Dispatchers.Default){verify(secret,account.recoveryHash)}){"Email or recovery code is incorrect, or recovery was never set up."}
        val next=newRecoveryCode()
        val passwordHash=withContext(Dispatchers.Default){hash(password)}
        val recoveryHash=withContext(Dispatchers.Default){hash(normalizeRecoveryCode(next))}
        db.withTransaction{
            val current=db.users().byEmail(normalized)
            require(current!=null&&current.recoveryHash==account.recoveryHash){"Recovery code has already changed. Use the latest code."}
            db.users().update(current.copy(passwordHash=passwordHash,recoveryHash=recoveryHash))
        }
        signOut()
        return next
    }
    suspend fun signOut(){context.sessionStore.edit{it.remove(sessionKey)}}
    suspend fun setPhoto(email:String,uri:String){db.users().byEmail(email)?.let{db.users().update(it.copy(photoUri=uri))}}
    suspend fun setCalled(id:String,value:Boolean)=db.members().setCalled(id,value)
    suspend fun setStatus(id:String,value:String)=db.members().setStatus(id,value)
    suspend fun setProfileDetails(id:String,area:String?,gender:String?,groupAffiliation:String,cnic:String?,referenceName:String?)=db.members().setProfileDetails(id,area?.trim()?.takeIf{it.isNotEmpty()},gender?.trim()?.takeIf{it.isNotEmpty()},groupAffiliation.takeIf{it in GROUP_AFFILIATIONS}?:"Independent Voter",cnic?.trim()?.takeIf{it.isNotEmpty()},referenceName?.trim()?.take(120)?.takeIf{it.isNotEmpty()})
    suspend fun applyImport(incoming:List<MemberEntity>):ImportReport{
        val dao=db.members();val old=dao.all().associateBy{it.stableKey};val newKeys=incoming.map{it.stableKey}.toSet()
        val now=LocalDate.now().toString();val differences=mutableListOf<ImportDiffEntity>();var added=0;var changed=0
        val merged=incoming.map { fresh->
            val prior=old[fresh.stableKey]
            if(prior==null){added++;differences+=ImportDiffEntity(importDate=now,kind="Added",memberName=fresh.fullName(),details="New member (member #${fresh.memberNumber?:"unknown"})");fresh}
            else{
                val updated=fresh.copy(area=fresh.area?:prior.area?.takeIf{fresh.residence==prior.residence},gender=prior.gender?:fresh.gender,groupAffiliation=prior.groupAffiliation.ifBlank{"Independent Voter"},cnic=fresh.cnic?:prior.cnic,referenceName=prior.referenceName)
                val changes=mutableListOf<String>()
                listOf("First name" to (prior.firstName to updated.firstName),"Last name" to (prior.lastName to updated.lastName),"Cell" to (prior.cellNumber to updated.cellNumber),"CNIC" to (prior.cnic to updated.cnic),"D.O.E" to (prior.enrollmentDate to updated.enrollmentDate),"Area Zone" to (prior.area to updated.area),"Chamber" to (prior.chamber to updated.chamber),"Biometric" to (prior.biometric to updated.biometric),"Gender" to (prior.gender to updated.gender),"Group affiliation" to (prior.groupAffiliation to updated.groupAffiliation),"Parent" to (prior.parentName to updated.parentName),"Residence" to (prior.residence to updated.residence)).forEach{(name,pair)->if(pair.first!=pair.second)changes+="$name: ${pair.first?:"—"} → ${pair.second?:"—"}"}
                if(changes.isNotEmpty()){changed++;differences+=ImportDiffEntity(importDate=now,kind="Changed",memberName=fresh.fullName(),details=changes.joinToString("; "))}
                updated.copy(id=prior.id,called=prior.called,status=prior.status,photoPath=fresh.photoPath?:prior.photoPath,removed=false)
            }
        }
        val removed=old.values.filter{it.stableKey !in newKeys&&!it.removed};removed.forEach{differences+=ImportDiffEntity(importDate=now,kind="Removed",memberName=it.fullName(),details="Not present in the latest imported list")}
        if(removed.isNotEmpty())dao.putAll(removed.map{it.copy(removed=true)})
        dao.putAll(merged);db.diffs().clear();if(differences.isNotEmpty())db.diffs().insertAll(differences)
        return ImportReport(added,removed.size,changed,incoming.count{it.needsReview})
    }
    suspend fun member(id:String)=db.members().all().firstOrNull{it.id==id}
    suspend fun reviewRows()=db.members().reviewRows()
    suspend fun backupJson():String {
        val memberRows=db.members().allRecords();val diffRows=db.diffs().all()
        val root=JSONObject().put("format","bar-member-manager-backup").put("version",1).put("createdAt",LocalDate.now().toString())
        root.put("members",JSONArray().apply{memberRows.forEach{member->put(member.toBackupJson())}})
        root.put("diffs",JSONArray().apply{diffRows.forEach{row->put(JSONObject().put("id",row.id).put("importDate",row.importDate).put("kind",row.kind).put("memberName",row.memberName).put("details",row.details))}})
        return root.toString(2)
    }
    suspend fun restoreBackup(json:String):Int {
        require(json.length<=120_000_000){"Backup file is too large."}
        val root=JSONObject(json);require(root.optString("format")=="bar-member-manager-backup"&&root.optInt("version")==1){"This is not a supported Bar Member Manager backup."}
        val memberArray=root.optJSONArray("members")?:error("The backup has no member list.")
        val diffArray=root.optJSONArray("diffs")?:JSONArray();require(memberArray.length()<=100_000){"The backup has too many member records."}
        val photos=File(context.filesDir,"member_photos").apply{mkdirs()}
        fun nullable(o:JSONObject,key:String)=o.optString(key).takeUnless{it.isBlank()||it=="null"}
        val rows=(0 until memberArray.length()).map{index->val o=memberArray.getJSONObject(index);val id=o.optString("id");val stableKey=o.optString("stableKey").ifBlank{id};require(id.isNotBlank()&&stableKey.isNotBlank()){"A member record is missing its identifier."}
            val nameParts=o.optString("name").trim().split(Regex("\\s+"),limit=2);val firstName=nullable(o,"firstName")?:nameParts.firstOrNull().orEmpty();val lastName=nullable(o,"lastName")?:nameParts.getOrNull(1).orEmpty()
            val photoField=nullable(o,"photo");val encodedPhoto=nullable(o,"photoData")?:photoField?.takeIf{it.startsWith("data:")}?.substringAfter(",")
            val inlinePhoto=encodedPhoto?.let{encoded->val bytes=Base64.decode(encoded,Base64.DEFAULT);require(bytes.size<=20_000_000){"A member photo is too large."};val file=File(photos,"restore_${UUID.randomUUID()}.jpg");file.writeBytes(bytes);file.absolutePath}
            val photoFromAssets=nullable(o,"photoPath")?.takeIf{it.startsWith("file:///android_asset/")}?:photoField?.takeIf{it.startsWith("member-photos/")}?.let{"file:///android_asset/$it"}
            val savedPhoto=inlinePhoto?:photoFromAssets
            MemberEntity(id=id,stableKey=stableKey,firstName=firstName,lastName=lastName,cellNumber=nullable(o,"cellNumber")?:nullable(o,"cell"),enrollmentDate=nullable(o,"enrollmentDate")?:nullable(o,"doe"),area=nullable(o,"area"),chamber=nullable(o,"chamber"),biometric=nullable(o,"biometric"),gender=nullable(o,"gender"),photoPath=savedPhoto,parentName=nullable(o,"parentName")?:nullable(o,"parent"),residence=nullable(o,"residence"),memberNumber=nullable(o,"memberNumber")?:nullable(o,"memberNo"),called=o.optBoolean("called"),status=o.optString("status","More Working Required").takeIf{it in listOf("My Voter","50/50","More Working Required")}?:"More Working Required",removed=o.optBoolean("removed"),needsReview=o.optBoolean("needsReview"),groupAffiliation=o.optString("groupAffiliation","Independent Voter").takeIf{it in GROUP_AFFILIATIONS}?:"Independent Voter",cnic=nullable(o,"cnic"),referenceName=nullable(o,"referenceName")?.take(120))
        }
        require(rows.map{it.stableKey}.distinct().size==rows.size){"The backup contains duplicate member identifiers."}
        val differences=(0 until diffArray.length()).map{index->val o=diffArray.getJSONObject(index);ImportDiffEntity(id=o.optLong("id",0),importDate=o.optString("importDate"),kind=o.optString("kind"),memberName=o.optString("memberName",o.optString("member")),details=o.optString("details"))}
        db.withTransaction{db.members().clear();if(rows.isNotEmpty())db.members().putAll(rows);db.diffs().clear();if(differences.isNotEmpty())db.diffs().insertAll(differences)}
        return rows.size
    }
    private fun MemberEntity.toBackupJson():JSONObject {
        val o=JSONObject().put("id",id).put("stableKey",stableKey).put("name",fullName()).put("firstName",firstName).put("lastName",lastName).put("called",called).put("status",status).put("removed",removed).put("needsReview",needsReview).put("groupAffiliation",groupAffiliation.ifBlank{"Independent Voter"})
        listOf("cellNumber" to cellNumber,"cell" to cellNumber,"cnic" to cnic,"referenceName" to referenceName,"enrollmentDate" to enrollmentDate,"doe" to enrollmentDate,"area" to area,"chamber" to chamber,"biometric" to biometric,"gender" to gender,"photoPath" to photoPath,"parentName" to parentName,"parent" to parentName,"residence" to residence,"memberNumber" to memberNumber,"memberNo" to memberNumber).forEach{(key,value)->o.put(key,value?:JSONObject.NULL)}
        photoPath?.let{path->runCatching{val bytes=File(path).takeIf{it.isFile}?.readBytes()?:if(path.startsWith("file:///android_asset/"))context.assets.open(path.removePrefix("file:///android_asset/")).use{it.readBytes()}else null;bytes?.let{o.put("photoData",Base64.encodeToString(bytes,Base64.NO_WRAP))}}}
        return o
    }
    private fun hash(password:String):String {val salt=ByteArray(16).also{SecureRandom().nextBytes(it)};val spec=PBEKeySpec(password.toCharArray(),salt,120_000,256);val key=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded;return salt.joinToString(""){"%02x".format(it)}+":"+key.joinToString(""){"%02x".format(it)}}
    private fun verify(password:String,encoded:String):Boolean=runCatching{val parts=encoded.split(":");require(parts.size==2);val salt=parts[0].chunked(2).map{it.toInt(16).toByte()}.toByteArray();val expected=parts[1];val spec=PBEKeySpec(password.toCharArray(),salt,120_000,256);val actual=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded.joinToString(""){"%02x".format(it)};java.security.MessageDigest.isEqual(actual.toByteArray(),expected.toByteArray())}.getOrDefault(false)
}
data class ImportReport(val added:Int,val removed:Int,val changed:Int,val review:Int)
fun MemberEntity.fullName()="$firstName $lastName".trim()
fun parseDateKey(value:String?):String {if(value.isNullOrBlank())return "9999-99-99";return try{val f=DateTimeFormatter.ofPattern("M/d/yyyy",Locale.US);LocalDate.parse(value.substringBefore(' '),f).toString()}catch(_:Exception){value}}
fun newId()=UUID.randomUUID().toString()
