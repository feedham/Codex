package pk.bar.membermanager.data

/** Source directory refreshes must not reset locally entered campaign details. */
fun mergeDirectoryMember(fresh:MemberEntity,prior:MemberEntity?):MemberEntity =
    if(prior==null)fresh else fresh.copy(
        id=prior.id,
        area=prior.area?:fresh.area,
        gender=prior.gender?:fresh.gender,
        cnic=prior.cnic?:fresh.cnic,
        referenceName=prior.referenceName,
        groupAffiliation=prior.groupAffiliation,
        called=prior.called,
        status=prior.status,
        photoPath=prior.photoPath?:fresh.photoPath,
        removed=prior.removed
    )
