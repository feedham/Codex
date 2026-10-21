package pk.bar.membermanager.data

const val WHATSAPP_BROADCAST_CONTACT_LIMIT=256

fun whatsappContactCards(members:List<MemberEntity>):String {
    require(members.size<=WHATSAPP_BROADCAST_CONTACT_LIMIT){"Choose at most 256 members."}
    fun escaped(value:String)=value.replace("\\","\\\\").replace("\r\n","\n").replace("\r","\n").replace("\n","\\n").replace(";","\\;").replace(",","\\,")
    return members.mapNotNull{member->whatsappNumber(member.cellNumber)?.let{member to it}}
        .distinctBy{it.second}.joinToString(""){(member,phone)->
            "BEGIN:VCARD\r\nVERSION:3.0\r\nN:${escaped(member.lastName)};${escaped(member.firstName)};;;\r\nFN:${escaped((member.firstName+" "+member.lastName).trim())}\r\nTEL;TYPE=CELL:+$phone\r\nEND:VCARD\r\n"
        }
}
