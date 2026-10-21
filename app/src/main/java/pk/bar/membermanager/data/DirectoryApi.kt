package pk.bar.membermanager.data

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Read-only directory download. MySQL credentials never go into the APK. */
object DirectoryApi {
    suspend fun download(endpoint:String,token:String):List<MemberEntity> = withContext(Dispatchers.IO){
        val base=URL(endpoint.trim().trimEnd('/')+"/")
        require(base.userInfo==null&&base.query==null&&base.ref==null){"Enter the API base URL without credentials or a query."}
        require(base.protocol=="https"||(base.protocol=="http"&&base.host in listOf("127.0.0.1","localhost","10.0.2.2"))){"Use HTTPS, or http://127.0.0.1:8080 with USB forwarding."}
        require(token.length>=32&&!token.contains('\n')&&!token.contains('\r')){"Enter the backend access token (at least 32 characters)."}
        fun get(path:String):JSONObject{
            val connection=URL(base,path).openConnection() as HttpURLConnection
            try{
                connection.requestMethod="GET";connection.connectTimeout=10_000;connection.readTimeout=20_000
                connection.instanceFollowRedirects=false
                connection.setRequestProperty("Authorization","Bearer $token")
                connection.setRequestProperty("Accept","application/json")
                val status=connection.responseCode
                require(status==200){when(status){401->"Backend access token was rejected.";409->"Directory changed during download. Please try again.";else->"Backend returned HTTP $status."}}
                val text=connection.inputStream.bufferedReader().use{reader->
                    val result=StringBuilder();val buffer=CharArray(8192)
                    while(true){val count=reader.read(buffer);if(count<0)break;require(result.length+count<=2_000_000){"Backend page is too large."};result.append(buffer,0,count)}
                    result.toString()
                }
                return JSONObject(text)
            }finally{connection.disconnect()}
        }
        val metadata=get("v1/directory");val revision=metadata.getString("revision");val count=metadata.getInt("memberCount")
        require(Regex("^[a-f0-9]{64}$").matches(revision)&&count in 1..20_000){"Invalid directory metadata or too many members."}
        val result=ArrayList<MemberEntity>(count);val ids=HashSet<String>();var cursor=""
        fun nullable(o:JSONObject,key:String)=if(o.isNull(key))null else o.optString(key).takeIf{it.isNotBlank()}
        do{
            val page=get("v1/members?limit=100&revision=$revision&after="+java.net.URLEncoder.encode(cursor,"UTF-8"))
            require(page.getString("revision")==revision){"Directory changed during download."}
            val rows=page.getJSONArray("members");require(rows.length() in 1..100){"Invalid or empty directory page."}
            for(index in 0 until rows.length()){
                val o=rows.getJSONObject(index);val id=o.getString("id");require(id.length in 1..160&&ids.add(id)){"Duplicate or invalid member ID."}
                val first=o.getString("firstName");require(first.isNotBlank()){"A member has no name."}
                val photo=nullable(o,"photo")?.takeIf{Regex("^member-photos/[A-Za-z0-9_.-]+\\.(jpg|jpeg|png)$").matches(it)}?.let{"file:///android_asset/$it"}
                result+=MemberEntity(id=id,stableKey=id,firstName=first,lastName=o.optString("lastName"),memberNumber=nullable(o,"memberNo"),cellNumber=nullable(o,"cell"),enrollmentDate=nullable(o,"doe")?:nullable(o,"enrollmentSource"),area=nullable(o,"area"),chamber=nullable(o,"chamber"),biometric=nullable(o,"biometric"),gender=nullable(o,"gender"),parentName=nullable(o,"parent"),residence=nullable(o,"residence"),cnic=nullable(o,"cnic"),photoPath=photo,needsReview=o.optBoolean("needsReview"))
                require(result.size<=count){"Directory contains more members than advertised."}
            }
            val next=nullable(page,"next")
            require(next==null||next!=cursor&&next==result.last().stableKey){"Invalid directory cursor."}
            cursor=next.orEmpty()
        }while(cursor.isNotEmpty())
        require(result.size==count){"Directory download was incomplete. No local records were changed."}
        result
    }
}
