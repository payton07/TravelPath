package com.example.travelpath.data.database;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import com.example.travelpath.data.dao.ItineraryDao;
import com.example.travelpath.data.entities.Itinerary;

@Database(entities = {Itinerary.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract ItineraryDao itineraryDao();

    public static AppDatabase getInstance(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "travelpath_db")
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
