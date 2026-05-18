# TravelPath — Organisateur de Parcours Touristiques

TravelPath est une application mobile native Android qui génère des itinéraires touristiques personnalisés et riches en médias. Elle combine un algorithme de scoring multi-critères côté serveur (Firebase Cloud Functions) avec une interface pastel "Bright & Airy" entièrement in-app.

---

## Fonctionnalités

### Génération d'itinéraires
- **ClassicRuleStrategy** : 3 variantes (**ECONOMY / BALANCED / COMFORT**) selon budget, durée, effort et météo — scoring normalisé [0,1] avec réordonnancement nearest-neighbor.
- **AiItineraryStrategy** : Gemini 1.5 Flash avec fallback automatique vers Classic.
- **Lieux obligatoires** : POIs personnalisés inclus en priorité, indépendamment des filtres.
- **Données Google Places** : Photos haute résolution, horaires d'ouverture, rating et niveau de prix réels.
- **Météo temps réel** : Intégration OpenWeatherMap — priorise les activités couvertes en cas de pluie.

### Interface & Navigation
- **Design "Bright & Airy"** : Palette pastel (crème / blanc / tomate), typographies Fraunces (titres) + Geist (corps). Night mode désactivé.
- **Explore** : Formulaire tout-en-un avec sélecteur de ville, swipe card mood, chips d'intérêts, slider budget (jusqu'à 1000€), lieux obligatoires personnalisés, bouton CTA sticky.
- **Routes** : Carrousel ViewPager2 de 3 parcours avec badge dynamique (Économique / Équilibré / Confort), prix estimatif `~X€` et météo formatée.
- **Route Detail** : Stats (coût, durée, effort, météo) + carte Maps préview avec bouton zoom → plein écran + timeline cliquable + bouton "Démarrer".
- **Navigation in-app** : `NavigationFragment` — carte plein écran, badge "Étape X / N", panel POI courant, boutons Précédent / Suivant / Terminer, point bleu localisation. Tout reste dans l'app.
- **Fiche POI** : `PoiDetailFragment` — photo collapsible, horaires semaine, rating, bouton directions.
- **Saved** : Liste des parcours favoris avec persistance Room.
- **Profil** : Nom modifiable + toggle langue FR / EN (persisté automatiquement).
- **Bannières** : Système `MessageBanner` slide-up (ERROR / SUCCESS / INFO) — zéro Toast.

### Persistance & Export
- **Cache intelligent** : Room local (TTL configurable) + cache Firestore 24h côté serveur.
- **Mode hors-ligne** : Consultation des parcours sauvegardés sans connexion.
- **Export PDF** : Génération côté serveur (PDFKit) + téléchargement via `DownloadManager`.
- **Partage** : Lien public Firestore partageable.
- **Bilingue FR / EN** : Toggle dans le profil, `AppCompatDelegate.setApplicationLocales`.

---

## Architecture Technique

### Backend — Firebase Cloud Functions (TypeScript / Node.js 22)
```
functions/src/
├── index.ts                    # 4 Callable Functions
├── strategies/
│   ├── ClassicRuleStrategy.ts  # Algorithme déterministe multi-critères
│   └── AiItineraryStrategy.ts  # Alternative Gemini 1.5 Flash
├── services/
│   ├── GooglePlacesService.ts  # Places Text Search API (parallélisé + retry)
│   ├── WeatherService.ts       # OpenWeatherMap
│   ├── OsrmService.ts          # Routage walking (polyline encodée)
│   ├── JourneyService.ts       # Orchestrateur (constructor injection)
│   └── PdfService.ts           # PDFKit + Firebase Storage
├── config/AppConfig.ts         # COST_BY_PRICE_LEVEL, DURATION_BY_CATEGORY
└── utils/                      # Logger, CacheKeyBuilder, Result<T,E>
```

### Android — Java 17 (Single-Activity, Fragment-based)
```
app/src/main/java/com/example/travelpath/
├── MainActivity.java               # Hôte unique, navigation, MessageBanner
├── ui/fragments/
│   ├── ExploreFragment.java        # Formulaire de configuration
│   ├── RoutesFragment.java         # Carrousel ViewPager2
│   ├── RouteDetailFragment.java    # Détail itinéraire + timeline
│   ├── FullScreenMapFragment.java  # Carte plein écran (zoom)
│   ├── NavigationFragment.java     # Navigation in-app étape par étape
│   ├── PoiDetailFragment.java      # Fiche détaillée d'un POI
│   ├── SavedFragment.java          # Parcours sauvegardés
│   └── ProfileFragment.java        # Profil + toggle langue
├── ui/viewmodels/                  # MainViewModel, RouteViewModel, RouteDetailViewModel, SavedRoutesViewModel
├── ui/adapter/                     # RouteAdapter (COMPACT + CAROUSEL)
├── ui/widget/MessageBanner.java    # Bannières slide-up
├── domain/usecase/                 # GenerateJourneys, SaveItinerary, GetSavedItineraries
├── domain/validation/              # CriteriaValidator + règles (Chain of Responsibility)
├── data/
│   ├── repository/                 # Cache-first (Room → Firebase)
│   ├── remote/                     # FirebaseDataSource, RemoteMapper
│   ├── dao/ + database/            # Room v3
│   └── preferences/                # DataStore (RxJava3)
└── di/AppModule.java               # DI manuelle (pas de Hilt/Dagger)
```

**Async :** RxJava3 (`Single`, `Completable`, `Flowable`) — schedulers dans Repository, observe sur main thread dans ViewModel.  
**Navigation :** Tabs (show/hide, état préservé) + détails (replace + addToBackStack).

---

## Dépendances principales

| Domaine | Bibliothèque |
|---------|-------------|
| Backend | Firebase Functions, Firestore, Firebase Storage, pdfkit |
| Images | Glide |
| Données | Room v3, DataStore (Proto+RxJava3), Gson |
| Cartographie | Google Maps SDK, Maps Android Utils |
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
# Prérequis : app/google-services.json (Firebase)
# local.properties à la racine :
# MAPS_API_KEY=<votre_clé>

./gradlew assembleDebug
```

---

## Auteurs
Projet réalisé dans le cadre du Master 1 Génie Logiciel — Faculté des Sciences de Montpellier.
