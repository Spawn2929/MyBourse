package fr.mybourse.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** Un portefeuille virtuel, avec son capital de départ et ses frais de courtage simulés. */
@Entity(tableName = "portefeuilles")
data class PortefeuilleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nom: String,
    val capital: Double,
    val fraisPct: Double,
    val fraisMin: Double,
    val creeLe: Long,
) {
    val frais: Frais get() = Frais(fraisPct, fraisMin)
}

/** Opération d'achat ou de vente d'un portefeuille virtuel. */
@Entity(tableName = "operations", indices = [Index("portefeuille")])
data class OperationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Identifiant du portefeuille. */
    val portefeuille: Long,
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
    fun versOperation() =
        Operation(id, ticker, nom, devise, Sens.valueOf(sens), quantite, prix, change, frais, date, portefeuille)

    companion object {
        fun depuis(op: Operation) = OperationEntity(
            id = op.id, portefeuille = op.portefeuille, ticker = op.ticker, nom = op.nom, devise = op.devise,
            sens = op.sens.name, quantite = op.quantite, prix = op.prix, change = op.change, frais = op.frais, date = op.date,
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
    // ---- Portefeuilles ----
    @Query("SELECT * FROM portefeuilles ORDER BY creeLe, id")
    fun portefeuilles(): Flow<List<PortefeuilleEntity>>

    @Query("SELECT * FROM portefeuilles ORDER BY creeLe, id")
    suspend fun portefeuillesListe(): List<PortefeuilleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enregistrerPortefeuille(p: PortefeuilleEntity): Long

    @Update
    suspend fun modifierPortefeuille(p: PortefeuilleEntity)

    @Query("DELETE FROM portefeuilles WHERE id = :id")
    suspend fun effacerPortefeuille(id: Long)

    // ---- Opérations ----
    @Query("SELECT * FROM operations ORDER BY date, id")
    fun operations(): Flow<List<OperationEntity>>

    @Query("SELECT * FROM operations WHERE portefeuille = :p ORDER BY date, id")
    suspend fun operationsDe(p: Long): List<OperationEntity>

    @Insert
    suspend fun ajouter(op: OperationEntity): Long

    @Insert
    suspend fun ajouterTout(ops: List<OperationEntity>)

    @Query("DELETE FROM operations WHERE id = :id")
    suspend fun supprimer(id: Long)

    @Query("DELETE FROM operations WHERE portefeuille = :p")
    suspend fun vider(p: Long)

    /** Supprime un portefeuille et ses opérations. */
    @Transaction
    suspend fun supprimerPortefeuille(id: Long) {
        vider(id)
        effacerPortefeuille(id)
    }

    /** Remet un portefeuille supprimé (bouton « Annuler »). */
    @Transaction
    suspend fun restaurer(p: PortefeuilleEntity, ops: List<OperationEntity>) {
        enregistrerPortefeuille(p)
        ajouterTout(ops)
    }

    @Query("DELETE FROM portefeuilles")
    suspend fun effacerPortefeuilles()

    @Query("DELETE FROM operations")
    suspend fun effacerOperations()

    /** Import d'une sauvegarde : tout est remplacé. */
    @Transaction
    suspend fun remplacerTout(contenu: List<Pair<PortefeuilleEntity, List<OperationEntity>>>) {
        effacerOperations()
        effacerPortefeuilles()
        for ((p, ops) in contenu) {
            val id = enregistrerPortefeuille(p.copy(id = 0))
            ajouterTout(ops.map { it.copy(id = 0, portefeuille = id) })
        }
    }

    // ---- Cours ----
    @Query("SELECT * FROM cours")
    fun cours(): Flow<List<CoursEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enregistrerCours(c: List<CoursEntity>)
}

/**
 * Version 1 → 2 : plusieurs portefeuilles. Les opérations existantes passent dans le portefeuille n° 1
 * (« Principal », créé au démarrage avec le capital et les frais des anciens réglages).
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `portefeuilles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`nom` TEXT NOT NULL, `capital` REAL NOT NULL, `fraisPct` REAL NOT NULL, `fraisMin` REAL NOT NULL, " +
                "`creeLe` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `operations_v2` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`portefeuille` INTEGER NOT NULL, `ticker` TEXT NOT NULL, `nom` TEXT NOT NULL, `devise` TEXT NOT NULL, " +
                "`sens` TEXT NOT NULL, `quantite` INTEGER NOT NULL, `prix` REAL NOT NULL, `change` REAL NOT NULL, " +
                "`frais` REAL NOT NULL, `date` INTEGER NOT NULL)",
        )
        db.execSQL(
            "INSERT INTO operations_v2 (id, portefeuille, ticker, nom, devise, sens, quantite, prix, change, frais, date) " +
                "SELECT id, 1, ticker, nom, devise, sens, quantite, prix, change, frais, date FROM operations",
        )
        db.execSQL("DROP TABLE operations")
        db.execSQL("ALTER TABLE operations_v2 RENAME TO operations")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_operations_portefeuille` ON `operations` (`portefeuille`)")
    }
}

@Database(
    entities = [PortefeuilleEntity::class, OperationEntity::class, CoursEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class BaseMyBourse : RoomDatabase() {
    abstract fun dao(): PortefeuilleDao

    companion object {
        @Volatile private var instance: BaseMyBourse? = null
        fun get(context: Context): BaseMyBourse = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, BaseMyBourse::class.java, "mybourse.db")
                .addMigrations(MIGRATION_1_2)
                .build().also { instance = it }
        }
    }
}
