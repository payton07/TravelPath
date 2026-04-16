package com.example.travelpath.data.entities;

import androidx.room.Entity;
import androidx.room.Ignore;
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
    private String destinationCity; // Ville de destination
    private String description;
    private double cost;
    private String duration; // e.g., "5.5h"
    private String effort;   // e.g., "Easy", "Moderate", "High"
    private String weather;  // e.g., "SUN, CLOUD"
    private String steps;    // Noms des étapes séparés par des virgules
    private String imageUrl;
    private String poiCoordinatesJson; // Liste des coordonnées GPS au format JSON

    private boolean isSaved;

    /**
     * Type de route : ECONOMY / BALANCED / COMFORT.
     */
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
