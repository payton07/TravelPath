package com.example.travelpath.data.models;

import java.io.Serializable;
import java.util.List;

/**
 * Représente un point d'intérêt retourné par le serveur.
 * Enrichi avec photos et horaires d'ouverture (Sprint 2).
 *
 * Immuable par convention : tous les champs sont valorisés via le Builder,
 * pas de setters publics — évite les mutations accidentelles dans l'UI.
 */
public final class PointOfInterest implements Serializable {

    private final String        id;
    private final String        name;
    private final String        category;
    private final double        latitude;
    private final double        longitude;
    private final double        baseCost;
    private final double        rating;
    private final double        averageDurationHours;
    private final String        preferredTimeSlot;
    private final List<String>  weatherCompatibility;
    private final int           effortScore;
    private final int           comfortLevel;
    private final List<String>  photoUrls;
    private final OpeningHours  openingHours;
    private final String        crowdLevel;
    private final String        address;

    private PointOfInterest(Builder b) {
        this.id                   = b.id;
        this.name                 = b.name;
        this.category             = b.category;
        this.latitude             = b.latitude;
        this.longitude            = b.longitude;
        this.baseCost             = b.baseCost;
        this.rating               = b.rating;
        this.averageDurationHours = b.averageDurationHours;
        this.preferredTimeSlot    = b.preferredTimeSlot;
        this.weatherCompatibility = b.weatherCompatibility;
        this.effortScore          = b.effortScore;
        this.comfortLevel         = b.comfortLevel;
        this.photoUrls            = b.photoUrls;
        this.openingHours         = b.openingHours;
        this.crowdLevel           = b.crowdLevel;
        this.address              = b.address;
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    public String       getId()                   { return id; }
    public String       getName()                 { return name; }
    public String       getCategory()             { return category; }
    public double       getLatitude()             { return latitude; }
    public double       getLongitude()            { return longitude; }
    public double       getBaseCost()             { return baseCost; }
    public double       getRating()               { return rating; }
    public double       getAverageDurationHours() { return averageDurationHours; }
    public String       getPreferredTimeSlot()    { return preferredTimeSlot; }
    public List<String> getWeatherCompatibility() { return weatherCompatibility; }
    public int          getEffortScore()          { return effortScore; }
    public int          getComfortLevel()         { return comfortLevel; }
    public List<String> getPhotoUrls()            { return photoUrls; }
    public OpeningHours getOpeningHours()         { return openingHours; }
    public String       getCrowdLevel()           { return crowdLevel; }
    public String       getAddress()              { return address; }

    /** Retourne la première URL de photo disponible, ou null. */
    public String getPrimaryPhotoUrl() {
        return (photoUrls != null && !photoUrls.isEmpty()) ? photoUrls.get(0) : null;
    }

    // ── OpeningHours ─────────────────────────────────────────────────────────

    public static final class OpeningHours implements Serializable {
        private final boolean      openNow;
        private final List<String> weekdayText;

        public OpeningHours(boolean openNow, List<String> weekdayText) {
            this.openNow     = openNow;
            this.weekdayText = weekdayText;
        }

        public boolean      isOpenNow()     { return openNow; }
        public List<String> getWeekdayText(){ return weekdayText; }
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static final class Builder {
        private String        id;
        private String        name;
        private String        category;
        private double        latitude;
        private double        longitude;
        private double        baseCost;
        private double        rating;
        private double        averageDurationHours;
        private String        preferredTimeSlot;
        private List<String>  weatherCompatibility;
        private int           effortScore;
        private int           comfortLevel;
        private List<String>  photoUrls;
        private OpeningHours  openingHours;
        private String        crowdLevel;
        private String        address;

        public Builder id(String v)                          { this.id = v; return this; }
        public Builder name(String v)                        { this.name = v; return this; }
        public Builder category(String v)                    { this.category = v; return this; }
        public Builder latitude(double v)                    { this.latitude = v; return this; }
        public Builder longitude(double v)                   { this.longitude = v; return this; }
        public Builder baseCost(double v)                    { this.baseCost = v; return this; }
        public Builder rating(double v)                      { this.rating = v; return this; }
        public Builder averageDurationHours(double v)        { this.averageDurationHours = v; return this; }
        public Builder preferredTimeSlot(String v)           { this.preferredTimeSlot = v; return this; }
        public Builder weatherCompatibility(List<String> v)  { this.weatherCompatibility = v; return this; }
        public Builder effortScore(int v)                    { this.effortScore = v; return this; }
        public Builder comfortLevel(int v)                   { this.comfortLevel = v; return this; }
        public Builder photoUrls(List<String> v)             { this.photoUrls = v; return this; }
        public Builder openingHours(OpeningHours v)          { this.openingHours = v; return this; }
        public Builder crowdLevel(String v)                  { this.crowdLevel = v; return this; }
        public Builder address(String v)                     { this.address = v; return this; }
        public PointOfInterest build()                       { return new PointOfInterest(this); }
    }
}
