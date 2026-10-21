package pk.bar.membermanager.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "users", indices = [Index(value=["email"], unique=true)])
data class UserEntity(@PrimaryKey val email:String, val firstName:String, val lastName:String, val passwordHash:String, val photoUri:String?=null, val phoneNumber:String?=null, val phoneVerifiedAt:Long?=null, val emailVerifiedAt:Long?=null, val recoveryHash:String?=null)

@Entity(tableName = "members", indices = [Index(value=["stableKey"], unique=true)])
data class MemberEntity(
    @PrimaryKey val id:String, val stableKey:String, val firstName:String, val lastName:String,
    val cellNumber:String?=null, val enrollmentDate:String?=null, val area:String?=null,
    val chamber:String?=null, val biometric:String?=null, val gender:String?=null,
    val photoPath:String?=null, val parentName:String?=null, val residence:String?=null,
    val memberNumber:String?=null, val called:Boolean=false, val status:String="More Working Required",
    val removed:Boolean=false, val needsReview:Boolean=false, @ColumnInfo(defaultValue="'Independent Voter'") val groupAffiliation:String="Independent Voter",
    val cnic:String?=null, val referenceName:String?=null
)

@Entity(tableName="import_diffs")
data class ImportDiffEntity(@PrimaryKey(autoGenerate=true) val id:Long=0, val importDate:String, val kind:String, val memberName:String, val details:String)

@Dao interface UserDao {
    @Query("SELECT * FROM users WHERE email=:email LIMIT 1") suspend fun byEmail(email:String):UserEntity?
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insert(user:UserEntity)
    @Update suspend fun update(user:UserEntity)
}
@Dao interface MemberDao {
    @Query("SELECT COUNT(*) FROM members") suspend fun countAll():Int
    @Query("SELECT * FROM members WHERE removed=0 ORDER BY firstName COLLATE NOCASE,lastName COLLATE NOCASE") fun observeAll(): kotlinx.coroutines.flow.Flow<List<MemberEntity>>
    @Query("SELECT * FROM members WHERE removed=0 ORDER BY firstName COLLATE NOCASE,lastName COLLATE NOCASE") suspend fun all():List<MemberEntity>
    @Query("SELECT * FROM members ORDER BY firstName COLLATE NOCASE,lastName COLLATE NOCASE") suspend fun allRecords():List<MemberEntity>
    @Query("SELECT * FROM members WHERE stableKey=:key LIMIT 1") suspend fun byKey(key:String):MemberEntity?
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun put(member:MemberEntity)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putAll(members:List<MemberEntity>)
    @Query("UPDATE members SET called=:called WHERE id=:id") suspend fun setCalled(id:String,called:Boolean)
    @Query("UPDATE members SET status=:status WHERE id=:id") suspend fun setStatus(id:String,status:String)
    @Query("UPDATE members SET area=:area, gender=:gender, groupAffiliation=:groupAffiliation, cnic=:cnic, referenceName=:referenceName WHERE id=:id") suspend fun setProfileDetails(id:String,area:String?,gender:String?,groupAffiliation:String,cnic:String?,referenceName:String?)
    @Query("DELETE FROM members") suspend fun clear()
    @Query("SELECT * FROM members WHERE needsReview=1 AND removed=0") suspend fun reviewRows():List<MemberEntity>
}
@Dao interface DiffDao {
    @Query("SELECT * FROM import_diffs ORDER BY id DESC") fun observe():kotlinx.coroutines.flow.Flow<List<ImportDiffEntity>>
    @Query("SELECT * FROM import_diffs ORDER BY id DESC") suspend fun all():List<ImportDiffEntity>
    @Insert suspend fun insertAll(rows:List<ImportDiffEntity>)
    @Query("DELETE FROM import_diffs") suspend fun clear()
}
@Database(entities=[UserEntity::class,MemberEntity::class,ImportDiffEntity::class],version=7,exportSchema=true)
abstract class AppDatabase:RoomDatabase(){abstract fun users():UserDao;abstract fun members():MemberDao;abstract fun diffs():DiffDao
    companion object {
        val MIGRATION_6_7=object:Migration(6,7){override fun migrate(db:SupportSQLiteDatabase){db.execSQL("ALTER TABLE users ADD COLUMN recoveryHash TEXT")}}
        val MIGRATION_5_6=object:Migration(5,6){override fun migrate(db:SupportSQLiteDatabase){db.execSQL("ALTER TABLE users ADD COLUMN emailVerifiedAt INTEGER")}}
        val MIGRATION_4_5=object:Migration(4,5){override fun migrate(db:SupportSQLiteDatabase){db.execSQL("ALTER TABLE users ADD COLUMN phoneNumber TEXT");db.execSQL("ALTER TABLE users ADD COLUMN phoneVerifiedAt INTEGER")}}
        val MIGRATION_1_2=object:Migration(1,2){override fun migrate(db:SupportSQLiteDatabase){db.execSQL("ALTER TABLE members ADD COLUMN groupAffiliation TEXT NOT NULL DEFAULT 'Independent Voter'")}}
        val MIGRATION_2_3=object:Migration(2,3){override fun migrate(db:SupportSQLiteDatabase){db.execSQL("ALTER TABLE members ADD COLUMN cnic TEXT")}}
        val MIGRATION_3_4=object:Migration(3,4){override fun migrate(db:SupportSQLiteDatabase){db.execSQL("ALTER TABLE members ADD COLUMN referenceName TEXT")}}
    }
}
