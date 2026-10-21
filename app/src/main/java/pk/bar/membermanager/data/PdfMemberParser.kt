package pk.bar.membermanager.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.PDFTextStripperByArea
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import java.util.Locale

data class ParsedPdf(val records:List<MemberEntity>,val warnings:List<String>)
object PdfMemberParser {
    suspend fun parse(context:Context,resolver:ContentResolver,uri:Uri):ParsedPdf {
        PDFBoxResourceLoader.init(context)
        val records=mutableListOf<MemberEntity>();val warnings=mutableListOf<String>()
        resolver.openInputStream(uri)?.use { stream->PDDocument.load(stream).use { doc->
            val renderer=PDFRenderer(doc);val photos=File(context.filesDir,"member_photos").apply{mkdirs()}
            for(pageIndex in 0 until doc.numberOfPages){
                val page=doc.getPage(pageIndex);val width=page.mediaBox.width.toInt();val height=page.mediaBox.height.toInt()
                val columns=listOf(android.graphics.RectF(0f,0f,width/2f,height.toFloat()),android.graphics.RectF(width/2f,0f,width.toFloat(),height.toFloat()))
                val anchorStripper=HeaderAnchorStripper().apply{sortByPosition=true;startPage=pageIndex+1;endPage=pageIndex+1};anchorStripper.getText(doc)
                // Bound page raster memory to about 8 MB even for unusually large PDF pages.
                val renderScale=minOf(1.6f,kotlin.math.sqrt(2_000_000f/(page.mediaBox.width*page.mediaBox.height)))
                require(renderScale.isFinite()&&renderScale>0){"Invalid PDF page dimensions."}
                val bitmap=renderer.renderImage(pageIndex,renderScale)
                try{
                columns.forEach { area->
                    val stripper=PDFTextStripperByArea();stripper.sortByPosition=true;stripper.addRegion("card",area);stripper.extractRegions(page)
                    val parsed=parseCards(stripper.getTextForRegion("card"),pageIndex+1,warnings)
                    val left=area.left;val right=area.right
                    val ys=anchorStripper.anchors.filter{it.x>=left&&it.x<right&&it.name.matches(Regex("[A-Z][A-Z .'-]{1,65}"))}.sortedBy{it.y}.map{it.y}.distinct()
                    parsed.forEachIndexed{index,member->
                        // Assumption: in this directory each card portrait is in the upper-right frame; the landscape position aligns with each uppercase member-name heading.
                        val y=ys.getOrNull(index);val photo=if(y!=null){
                            val scale=bitmap.width.toFloat()/page.mediaBox.width
                            val x0=((area.right-58)*scale).toInt().coerceAtLeast(0)
                            val y0=((y-25)*scale).toInt().coerceAtLeast(0)
                            val w=(49*scale).toInt().coerceAtMost(bitmap.width-x0);val h=(59*scale).toInt().coerceAtMost(bitmap.height-y0)
                            if(w>5&&h>5){val crop=android.graphics.Bitmap.createBitmap(bitmap,x0,y0,w,h);val path=File(photos,"${member.stableKey.hashCode().toUInt()}.jpg");val has=hasPortrait(crop);if(has)path.outputStream().use{crop.compress(android.graphics.Bitmap.CompressFormat.JPEG,86,it)};crop.recycle();if(has)path.absolutePath else null}else null
                        }else null
                        records+=member.copy(photoPath=photo)
                    }
                }
                }finally{bitmap.recycle()}
            }
        } } ?: error("Could not open the selected PDF.")
        return ParsedPdf(records.distinctBy{it.stableKey},warnings)
    }
    private fun hasPortrait(bitmap:android.graphics.Bitmap):Boolean {var varied=0;val step=maxOf(1,bitmap.width/24);for(y in 0 until bitmap.height step step)for(x in 0 until bitmap.width step step){val c=bitmap.getPixel(x,y);if(android.graphics.Color.red(c)<220||android.graphics.Color.green(c)<220||android.graphics.Color.blue(c)<220)varied++};return varied>130}
    private data class Anchor(val name:String,val x:Float,val y:Float)
    private class HeaderAnchorStripper:PDFTextStripper(){val anchors=mutableListOf<Anchor>();override fun writeString(text:String,textPositions:MutableList<TextPosition>){val t=text.trim();if(t.matches(Regex("[A-Z][A-Z .'-]{1,65}"))&&textPositions.isNotEmpty())anchors+=Anchor(t,textPositions.first().xDirAdj,textPositions.first().yDirAdj);super.writeString(text,textPositions)}}

    private fun parseCards(text:String,page:Int,warnings:MutableList<String>):List<MemberEntity>{
        val lines=text.lines().map{it.replace(Regex("\\s+")," ").trim()}.filter{it.isNotBlank()}
        // The list prints each advocate's uppercase name immediately before "s/d/w of:". "Off:" is office/chamber and "Res:" is residence; the PDF has no dedicated gender or area field.
        val marker=Regex("(?i)s\\s*/\\s*d\\s*/\\s*w\\s+of\\s*:");val starts=lines.indices.filter{marker.containsMatchIn(lines[it])};if(starts.isEmpty())return emptyList()
        return starts.mapIndexedNotNull { idx,markerLine->
            val begin=if(idx==0)maxOf(0,markerLine-1) else maxOf(starts[idx-1]+1,markerLine-2);val end=starts.getOrNull(idx+1)?:lines.size
            val block=lines.subList(begin,end).joinToString(" ");val before=lines[markerLine].substringBefore(marker.find(lines[markerLine])?.value?:"").trim()
            val name=(if(before.isNotBlank())before else lines.getOrNull(markerLine-1).orEmpty()).replace(Regex("[^\\p{L}\\p{N} .'-]"),"").trim()
            if(name.isBlank()||name.contains("DISTRICT BAR",true)||name.length>70)return@mapIndexedNotNull null
            val parts=name.split(Regex("\\s+")).filter{it.isNotBlank()};val doe=Regex("(?i)D\\.?O\\.?E\\s*:\\s*(\\d{1,2}/\\d{1,2}/\\d{4})").find(block)?.groupValues?.get(1)
            val phone=Regex("(?i)Cell\\s*:\\s*([+\\d][\\d\\s()-]{6,})").find(block)?.groupValues?.get(1)?.trim()
            val memberNo=Regex("(?i)Comp\\.?\\s*#\\s*:\\s*(\\d+)").find(block)?.groupValues?.get(1)
            val cnic=Regex("(?i)(?:CNIC|C\\.?N\\.?I\\.?C\\.?|National\\s+ID)(?:\\s*(?:No\\.?|Number))?\\s*:\\s*([0-9][0-9 -]{11,16}[0-9])").find(block)?.groupValues?.get(1)?.trim()
            val biometric=Regex("(?i)Biometric\\s*:\\s*(Yes|No)").find(block)?.groupValues?.get(1)?.replaceFirstChar{it.uppercase()}
            val parent=lines[markerLine].substringAfter(marker.find(lines[markerLine])?.value?:"","").trim().ifBlank{null}
            val office=Regex("(?i)Off\\s*:\\s*(.*?)(?=\\s+Res\\s*:|\\s+Comp\\.?\\s*#|\\s+D\\.?O\\.?E|$)").find(block)?.groupValues?.get(1)?.trim()?.takeIf{it.isNotBlank()}
            val residence=Regex("(?i)Res\\s*:\\s*(.*?)(?=\\s+Comp\\.?\\s*#|\\s+D\\.?O\\.?E|\\s+Biometric|$)").find(block)?.groupValues?.get(1)?.trim()?.takeIf{it.isNotBlank()}
            val chamber=office?.takeIf{it.contains("chamber",true)||it.contains("complex",true)||it.contains("bar association",true)}
            val key=memberNo?.let{"member:$it"}?:"name:${parts.joinToString(" ").lowercase(Locale.ROOT)}|${phone?.filter(Char::isDigit).orEmpty()}"
            val member=MemberEntity(id=newId(),stableKey=key,firstName=parts.firstOrNull().orEmpty(),lastName=parts.drop(1).joinToString(" "),cellNumber=phone,enrollmentDate=doe,area=null,chamber=chamber,biometric=biometric,gender=null,parentName=parent,residence=residence,memberNumber=memberNo,needsReview=doe==null||memberNo==null,cnic=cnic)
            if(member.needsReview)warnings+="Page $page: ${member.fullName()} (member number or enrollment date was not found)";member
        }
    }
}
