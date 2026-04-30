# TravelPath — Organisateur de Parcours Touristiques

TravelPath est une application mobile native Android permettant de générer des itinéraires touristiques personnalisés et riches en médias. L'application combine un algorithme de scoring multi-critères côté serveur (Firebase Cloud Functions) avec une interface sombre et éditoriale côté Android.

---

## Fonctionnalités

### Génération d'itinéraires
- **ClassicRuleStrategy** : Génération de 3 variantes (**ECONOMY / BALANCED / COMFORT**) selon le budget, la durée, l'effort et les préférences météo.
- **Lieux obligatoires** : POIs sélectionnés par l'utilisateur inclus en priorité, indépendamment des filtres.
- **Données Google Places** : Photos haute résolution, horaires d'ouverture, rating et niveau de prix réels.
- **Météo temps réel** : Intégration OpenWeatherMap — priorise les activités couvertes en cas de pluie.

### Interface
- **Design dark-editorial** : Palette `color_ink` / `color_bg` / `color_accent`, typographies Fraunces (titres) et Geist (corps).
- **Explore** : Formulaire de configuration avec sélecteur de ville (BottomSheetDialog), chips d'intérêts, slider budget, sélecteur de créneaux horaires, lieux obligatoires personnalisés.
- **Routes** : Carrousel (ViewPager2) de 3 parcours générés avec badge dynamique (Économique / Équilibré / Confort), prix estimatif et météo formatée.
- **Route Detail** : Carte Google Maps avec polyline walking, timeline des étapes (photos, horaires, statut ouverture), stats (coût ~estimé, durée, effort, météo), export PDF, partage, sauvegarde.
- **Saved** : Liste des parcours favoris avec persistance Room.
- **Profil** : Nom utilisateur modifiable.
- **Bannières de messages** : Système slide-up au-dessus de la bottom nav (ERROR / SUCCESS / INFO) remplaçant tous les Toast.

### Persistance & export
- **Cache intelligent** : Room local (TTL configurable) + cache Firestore 24h côté serveur.
- **Mode hors-ligne** : Consultation des parcours sauvegardés sans connexion.
- **Export PDF** : Génération côté serveur (PDFKit) + téléchargement via `DownloadManager`.
- **Partage** : Lien public Firestore partageable.

---

## Architecture Technique

### Backend — Firebase Cloud Functions (TypeScript / Node.js 22)
```
functions/src/
├── index.ts                    # 4 Callable Functions (generateJourneys, generatePDF, saveUserItinerary, shareItinerary)
├── strategies/
│   ├── ClassicRuleStrategy.ts  # Algorithme déterministe multi-critères
│   └── AiItineraryStrategy.ts  # Alternative Gemini 1.5 Flash
├── services/
│   ├── GooglePlacesService.ts  # Places Text Search API (parallélisé)
│   ├── WeatherService.ts       # OpenWeatherMap
│   ├── OsrmService.ts          # Routage walking (polyline)
│   └── JourneyService.ts       # Orchestrateur
└── config/AppConfig.ts         # Paramètres (coûts, durées, URLs)
```

### Android — Java 17 (Single-Activity, Fragment-based)
```
app/src/main/java/com/example/travelpath/
├── MainActivity.java           # Hôte unique, navigation, MessageBanner
├── ui/fragments/               # Explore, Routes, RouteDetail, Saved, Profile
├── ui/viewmodels/              # MainViewModel, RouteViewModel, RouteDetailViewModel, SavedRoutesViewModel
├── ui/adapter/                 # RouteAdapter (COMPACT + CAROUSEL), formatWeather()
├── ui/widget/MessageBanner.java # Bannières slide-up (ERROR/SUCCESS/INFO)
├── data/
│   ├── repository/             # Cache-first (Room → Firebase)
│   ├── remote/                 # FirebaseDataSource, RemoteMapper
│   ├── dao/ + database/        # Room v3
│   └── preferences/            # DataStore (RxJava3)
└── di/AppModule.java           # DI manuelle (pas de Hilt/Dagger)
```

**Navigation :** Tabs (show/hide) + Details (replace + addToBackStack)  
**Async :** RxJava3 (`Single`, `Completable`, `Flowable`) — schedulers dans Repository, observe sur main thread dans ViewModel

---

## Dépendances principales

| Domaine | Bibliothèque |
|---------|-------------|
| Backend | Firebase Functions, Firestore, Firebase Storage, pdfkit, Axios |
| Images | Glide |
| Données | Room v3, DataStore (Proto+RxJava3), Gson |
| Cartographie | Google Maps SDK, Maps Android Utils, Directions API |
| API tierces | Google Places, OpenWeatherMap, OSRM |
| UI | Material Components 3, ViewBinding |
| Async | RxJava3, RxAndroid |
| Logging | Timber |

---

## Installation & Configuration

### Backend
```bash
cd functions
npm install
# Créer functions/.env avec :
# MAPS_API_KEY=<votre_clé>
# OPENWEATHER_API_KEY=<votre_clé>
npm run build
firebase deploy --only functions
```

### Android
```bash
# Prérequis : google-services.json dans app/
# local.properties à la racine :
# MAPS_API_KEY=<votre_clé>

./gradlew assembleDebug
```

---

## Prochaines fonctionnalités (Sprint 4)

Voir [FEATURES_ROADMAP.md](FEATURES_ROADMAP.md) pour le plan détaillé.

1. **Carte plein écran** — tap sur la carte → fragment MapView full-screen avec polyline
2. **Fiche POI** — tap sur une étape → détails (photo, horaires, rating, itinéraire Maps)
3. **Démarrer le parcours** — bouton CTA → Google Maps walking avec tous les waypoints
4. **Support FR / EN** — toggle langue dans le profil, `values-en/strings.xml`

---

## Auteurs
Projet réalisé dans le cadre du Master 1 Génie Logiciel — Faculté des Sciences de Montpellier.
