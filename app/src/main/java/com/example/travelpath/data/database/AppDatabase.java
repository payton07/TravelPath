package com.example.travelpath.data.database;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import com.example.travelpath.data.dao.ItineraryDao;
import com.example.travelpath.data.entities.Itinerary;

/**
 * Base de données Room — singleton thread-safe (double-checked locking).
 *
 * Politique de migration :
 *   - v1→v2 : ajout colonne encodedPolyline (Sprint 3)
 *   - v2→v3 : ajout colonnes fullStepsJson, cachedAt, isSaved, destinationCity
 *
 * IMPORTANT : ne jamais utiliser fallbackToDestructiveMigration() en production
 * car cela efface les données sauvegardées de l'utilisateur. On le garde uniquement
 * comme filet de sécurité via allowDestructiveMigrationOnDowngrade().
 */
@Database(
    entities  = { Itinerary.class },
    version   = 4,
    exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract ItineraryDao itineraryDao();

    // ── Migrations ────────────────────────────────────────────────────────────

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE itineraries ADD COLUMN encodedPolyline TEXT");
        }
    };

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE itineraries ADD COLUMN fullStepsJson TEXT");
            db.execSQL("ALTER TABLE itineraries ADD COLUMN cachedAt INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE itineraries ADD COLUMN isSaved INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE itineraries ADD COLUMN destinationCity TEXT");
        }
    };

    // ── Singleton ─────────────────────────────────────────────────────────────

    public static AppDatabase getInstance(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "travelpath_db"
                        )
                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                        .fallbackToDestructiveMigration() // Reset DB in development if schema changes
                        .build();
                }
            }
        }
        return INSTANCE;
    }
}
