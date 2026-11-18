package fr.mybourse.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Opération d'achat ou de vente du portefeuille virtuel. */
@Entity(tableName = "operations")
data class OperationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** « perso » : le portefeuille de Fred (le portefeuille automatique est calculé par l'analyse). */
    val portefeuille: String = PERSO,
    val ticker: String,
    val nom: String,
    val devise: String,
    val sens: String,
    val quantite: Int,
    val prix: Double,
    val change: Double,
    val frais: Double,
    val date: Long,
) {
    fun versOperation() = Operation(id, ticker, nom, devise, Sens.valueOf(sens), quantite, prix, change, frais, date)

    companion object {
        const val PERSO = "perso"
        fun depuis(op: Operation) = OperationEntity(
            id = op.id, ticker = op.ticker, nom = op.nom, devise = op.devise, sens = op.sens.name,
            quantite = op.quantite, prix = op.prix, change = op.change, frais = op.frais, date = op.date,
        )
    }
}

/** Dernier cours connu d'un titre (ou taux de change « FX:USD » = euros pour 1 dollar). */
@Entity(tableName = "cours")
data class CoursEntity(
    @PrimaryKey val ticker: String,
    val nom: String,
    val devise: String,
    val prix: Double,
    /** Heure du cours (millisecondes). */
    val heure: Long,
)

@Dao
interface PortefeuilleDao {
    @Query("SELECT * FROM operations WHERE portefeuille = :p ORDER BY date, id")
    fun operations(p: String = OperationEntity.PERSO): Flow<List<OperationEntity>>

    @Query("SELECT * FROM operations WHERE portefeuille = :p ORDER BY date, id")
    suspend fun operationsListe(p: String = OperationEntity.PERSO): List<OperationEntity>

    @Insert
    suspend fun ajouter(op: OperationEntity): Long

    @Insert
    suspend fun ajouterTout(ops: List<OperationEntity>)

    @Query("DELETE FROM operations WHERE id = :id")
    suspend fun supprimer(id: Long)

    @Query("DELETE FROM operations WHERE portefeuille = :p")
    suspend fun vider(p: String = OperationEntity.PERSO)

    @Transaction
    suspend fun remplacer(ops: List<OperationEntity>) {
        vider()
        ajouterTout(ops)
    }

    @Query("SELECT * FROM cours")
    fun cours(): Flow<List<CoursEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enregistrerCours(c: List<CoursEntity>)
}

@Database(entities = [OperationEntity::class, CoursEntity::class], version = 1, exportSchema = true)
abstract class BaseMyBourse : RoomDatabase() {
    abstract fun dao(): PortefeuilleDao

    companion object {
        @Volatile private var instance: BaseMyBourse? = null
        fun get(context: Context): BaseMyBourse = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, BaseMyBourse::class.java, "mybourse.db")
                .build().also { instance = it }
        }
    }
}
