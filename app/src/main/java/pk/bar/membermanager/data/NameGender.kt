package pk.bar.membermanager.data

import android.content.Context
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/** Local name-pattern estimate, never a confirmed demographic record. */
object NameGender {
    private var model:JSONObject?=null
    @Synchronized private fun load(context:Context):JSONObject = model?:context.assets.open("name-gender-model.json").bufferedReader().use{JSONObject(it.readText())}.also{require(it.getInt("version")==1);model=it}
    fun suggest(context:Context,name:String):String? = runCatching{
        val config=load(context)
        fun words(key:String)=config.getJSONArray(key).let{array->(0 until array.length()).map{array.getString(it)}.toSet()}
        val skip=words("skip")
        val token=Normalizer.normalize(name,Normalizer.Form.NFKD).replace(Regex("[\\u0300-\\u036f]"),"").lowercase(Locale.ROOT).replace(Regex("[^a-z\\s.'-]")," ").split(Regex("[\\s.'-]+")).firstOrNull{it.length>1&&it !in skip}?:return null
        if(token.length>30||token in words("ambiguous")||token in words("surnames"))return null
        val dictionary=config.getJSONObject("dictionary")
        if(dictionary.has(token))return dictionary.getString(token)
        if(token.length<4)return null
        val s="^$token$";val features=mutableSetOf<String>()
        for(n in 2..4)for(i in 0..s.length-n)features+=s.substring(i,i+n)
        val weights=config.getJSONObject("weights");var known=0;var score=0.0
        features.forEach{if(weights.has(it)){known++;score+=weights.getDouble(it)}}
        if(known.toDouble()/features.size<config.getDouble("minCoverage")||abs(score)<config.getDouble("minMargin"))null else if(score>0)"Male" else "Female"
    }.getOrNull()
}
