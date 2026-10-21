package pk.bar.membermanager

import android.app.Application
import androidx.room.Room
import pk.bar.membermanager.data.AppDatabase

class AdvocateApplication:Application(){
    val database by lazy { Room.databaseBuilder(this,AppDatabase::class.java,"bar-members.db").addMigrations(AppDatabase.MIGRATION_1_2,AppDatabase.MIGRATION_2_3,AppDatabase.MIGRATION_3_4,AppDatabase.MIGRATION_4_5,AppDatabase.MIGRATION_5_6,AppDatabase.MIGRATION_6_7).build() }
    val repository by lazy { pk.bar.membermanager.data.Repository(this,database) }
}
