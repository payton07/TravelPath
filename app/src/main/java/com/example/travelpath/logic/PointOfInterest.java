package com.example.travelpath.logic;

import java.util.Arrays;
import java.util.List;

public class PointOfInterest {

    public static final String CAT_CULTURE   = "Culture";
    public static final String CAT_FOOD      = "Food";
    public static final String CAT_NATURE    = "Nature";
    public static final String CAT_SHOPPING  = "Shopping";
    public static final String CAT_SPORT     = "Sport";
    public static final String CAT_NIGHTLIFE = "Nightlife";
    public static final String CAT_WELLNESS  = "Wellness";

    public static final String SLOT_MORNING   = "morning";
    public static final String SLOT_AFTERNOON = "afternoon";
    public static final String SLOT_EVENING   = "evening";

    public static final String WEATHER_SUN   = "SUN";
    public static final String WEATHER_CLOUD = "CLOUD";
    public static final String WEATHER_RAIN  = "RAIN";
    public static final String WEATHER_ANY   = "ANY";

    private final String id;
    private final String name;
    private final String category;
    private final double baseCost;
    private final double averageDurationHours;
    private final int effortScore;
    private final List<String> weatherCompatibility;
    private final double rating;
    private final int comfortLevel;
    
    // Nouveaux champs pour la stabilisation
    private final double latitude;
    private final double longitude;
    private final String openingTime; // Format "HH:mm"
    private final String closingTime;
    private final String preferredTimeSlot;

    public PointOfInterest(String id, String name, String category, double baseCost, 
                            double averageDurationHours, int effortScore, 
                            List<String> weatherCompatibility, double rating, int comfortLevel,
                            double latitude, double longitude, String openingTime, 
                            String closingTime, String preferredTimeSlot) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.baseCost = baseCost;
        this.averageDurationHours = averageDurationHours;
        this.effortScore = effortScore;
        this.weatherCompatibility = weatherCompatibility;
        this.rating = rating;
        this.comfortLevel = comfortLevel;
        this.latitude = latitude;
        this.longitude = longitude;
        this.openingTime = openingTime;
        this.closingTime = closingTime;
        this.preferredTimeSlot = preferredTimeSlot;
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public double getBaseCost() { return baseCost; }
    public double getAverageDurationHours() { return averageDurationHours; }
    public int getEffortScore() { return effortScore; }
    public List<String> getWeatherCompatibility() { return weatherCompatibility; }
    public double getRating() { return rating; }
    public int getComfortLevel() { return comfortLevel; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getOpeningTime() { return openingTime; }
    public String getClosingTime() { return closingTime; }
    public String getPreferredTimeSlot() { return preferredTimeSlot; }

    public boolean isWeatherCompatible(List<String> userWeatherPrefs) {
        if (weatherCompatibility.contains(WEATHER_ANY)) return true;
        for (String pref : userWeatherPrefs) {
            if (weatherCompatibility.contains(pref)) return true;
        }
        return false;
    }

    public boolean matchesInterests(List<String> interests) {
        return interests.contains(category);
    }

    public static List<PointOfInterest> getMockedPOIs() {
        return Arrays.asList(
            // PARIS
            new PointOfInterest("PAR_01", "Musée du Louvre", CAT_CULTURE, 17.0, 3.0, 1, 
                Arrays.asList(WEATHER_ANY), 4.8, 4, 48.8606, 2.3376, "09:00", "18:00", SLOT_MORNING),
            new PointOfInterest("PAR_02", "Musée d'Orsay", CAT_CULTURE, 16.0, 2.5, 1, 
                Arrays.asList(WEATHER_ANY), 4.7, 4, 48.8599, 2.3265, "09:30", "18:00", SLOT_MORNING),
            new PointOfInterest("PAR_06", "Jardin des Tuileries", CAT_NATURE, 0.0, 1.5, 1, 
                Arrays.asList(WEATHER_SUN, WEATHER_CLOUD), 4.5, 2, 48.8635, 2.3275, "07:00", "21:00", SLOT_AFTERNOON),
            new PointOfInterest("PAR_11", "Bistrot Chez Paul", CAT_FOOD, 28.0, 1.5, 1, 
                Arrays.asList(WEATHER_ANY), 4.5, 3, 48.8531, 2.3861, "12:00", "23:00", SLOT_EVENING),
            new PointOfInterest("PAR_19", "Le Perchoir Marais", CAT_NIGHTLIFE, 12.0, 2.0, 1, 
                Arrays.asList(WEATHER_SUN, WEATHER_CLOUD), 4.4, 4, 48.8578, 2.3534, "18:00", "02:00", SLOT_EVENING),
            
            // LONDON
            new PointOfInterest("LON_01", "British Museum", CAT_CULTURE, 0.0, 3.0, 1, 
                Arrays.asList(WEATHER_ANY), 4.8, 3, 51.5194, -0.1270, "10:00", "17:00", SLOT_MORNING),
            new PointOfInterest("LON_05", "Hyde Park", CAT_NATURE, 0.0, 2.0, 1, 
                Arrays.asList(WEATHER_SUN, WEATHER_CLOUD), 4.6, 2, 51.5073, -0.1657, "05:00", "00:00", SLOT_AFTERNOON),
            new PointOfInterest("LON_08", "Dishoom Covent Garden", CAT_FOOD, 32.0, 1.5, 1, 
                Arrays.asList(WEATHER_ANY), 4.7, 4, 51.5124, -0.1268, "08:00", "23:00", SLOT_AFTERNOON)
        );
    }
}
