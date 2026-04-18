package com.example.travelpath.data.models;

import java.io.Serializable;
import java.util.List;

/**
 * Modèle de données pour un point d'intérêt, enrichi avec photos et horaires.
 */
public class PointOfInterest implements Serializable {
    private String id;
    private String name;
    private String category;
    private double latitude;
    private double longitude;
    private double baseCost;
    private double rating;
    private double averageDurationHours;
    private String preferredTimeSlot;
    private List<String> weatherCompatibility;
    private int effortScore;
    private int comfortLevel;
    private List<String> photoUrls;
    private OpeningHours openingHours;

    public static class OpeningHours implements Serializable {
        private boolean isOpenNow;
        private List<String> weekdayText;

        public boolean isOpenNow() { return isOpenNow; }
        public void setOpenNow(boolean openNow) { isOpenNow = openNow; }
        public List<String> getWeekdayText() { return weekdayText; }
        public void setWeekdayText(List<String> weekdayText) { this.weekdayText = weekdayText; }
    }

    // Getters / Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public double getBaseCost() { return baseCost; }
    public void setBaseCost(double baseCost) { this.baseCost = baseCost; }
    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }
    public double getAverageDurationHours() { return averageDurationHours; }
    public void setAverageDurationHours(double averageDurationHours) { this.averageDurationHours = averageDurationHours; }
    public String getPreferredTimeSlot() { return preferredTimeSlot; }
    public void setPreferredTimeSlot(String preferredTimeSlot) { this.preferredTimeSlot = preferredTimeSlot; }
    public List<String> getWeatherCompatibility() { return weatherCompatibility; }
    public void setWeatherCompatibility(List<String> weatherCompatibility) { this.weatherCompatibility = weatherCompatibility; }
    public int getEffortScore() { return effortScore; }
    public void setEffortScore(int effortScore) { this.effortScore = effortScore; }
    public int getComfortLevel() { return comfortLevel; }
    public void setComfortLevel(int comfortLevel) { this.comfortLevel = comfortLevel; }
    public List<String> getPhotoUrls() { return photoUrls; }
    public void setPhotoUrls(List<String> photoUrls) { this.photoUrls = photoUrls; }
    public OpeningHours getOpeningHours() { return openingHours; }
    public void setOpeningHours(OpeningHours openingHours) { this.openingHours = openingHours; }
}
