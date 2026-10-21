package pk.bar.membermanager.data

/** The supplied bar directory is Pakistani; explicit international prefixes are retained. */
fun whatsappNumber(value:String?):String? {
    val text=value?.trim()?.takeIf{it.isNotEmpty()}?:return null
    if(!Regex("^\\+?[0-9 ()-]+$").matches(text))return null
    val digits=text.filter{it in '0'..'9'}
    val number=when{
        text.startsWith("+")->digits
        digits.startsWith("00")->digits.drop(2)
        digits.startsWith("0")&&digits.length==11->"92"+digits.drop(1)
        digits.startsWith("3")&&digits.length==10->"92$digits"
        else->digits
    }
    return number.takeIf{it.length in 8..15&&it.first()!='0'}
}
