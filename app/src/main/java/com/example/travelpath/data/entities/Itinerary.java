package com.example.travelpath.data.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import java.io.Serializable;

/**
 * Entité Room représentant un itinéraire généré par le JourneyEngine.
 */
@Entity(tableName = "itineraries")
public class Itinerary implements Serializable {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private String name;
    private String destinationCity;
    private String description;
    private double cost;
    private String duration;
    private String effort;
    private String weather;
    private String steps;
    private String imageUrl;
    private String poiCoordinatesJson;
    private String fullStepsJson;      // Détails complets des POIs (Photos, Horaires)
    private String encodedPolyline;    // Tracé de la route Google Directions
    private long cachedAt;             // Timestamp de mise en cache (Tâche 9)
    
    private boolean isSaved;
    private String routeType;

    public Itinerary() {}

    // ── Getters / Setters ────────────────────────────────────────────────────
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDestinationCity() { return destinationCity; }
    public void setDestinationCity(String destinationCity) { this.destinationCity = destinationCity; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getCost() { return cost; }
    public void setCost(double cost) { this.cost = cost; }

    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }

    public String getEffort() { return effort; }
    public void setEffort(String effort) { this.effort = effort; }

    public String getWeather() { return weather; }
    public void setWeather(String weather) { this.weather = weather; }

    public String getSteps() { return steps; }
    public void setSteps(String steps) { this.steps = steps; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getPoiCoordinatesJson() { return poiCoordinatesJson; }
    public void setPoiCoordinatesJson(String poiCoordinatesJson) { this.poiCoordinatesJson = poiCoordinatesJson; }

    public String getFullStepsJson() { return fullStepsJson; }
    public void setFullStepsJson(String fullStepsJson) { this.fullStepsJson = fullStepsJson; }

    public String getEncodedPolyline() { return encodedPolyline; }
    public void setEncodedPolyline(String encodedPolyline) { this.encodedPolyline = encodedPolyline; }

    public long getCachedAt() { return cachedAt; }
    public void setCachedAt(long cachedAt) { this.cachedAt = cachedAt; }

    public boolean isSaved() { return isSaved; }
    public void setSaved(boolean saved) { isSaved = saved; }

    public String getRouteType() { return routeType; }
    public void setRouteType(String routeType) { this.routeType = routeType; }

    @Override
    public String toString() {
        return "[" + routeType + "] " + name
                + " | " + duration + " | " + cost + "€";
    }
}
