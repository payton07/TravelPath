# 🌍 TravelPath — Organisateur de Parcours Touristiques

**TravelPath** est une application mobile native Android permettant de générer des itinéraires de visite personnalisés et riches en médias. L'application combine une logique algorithmique sophistiquée sur le serveur (Firebase) avec une interface utilisateur moderne et réactive.

---

## ✨ Fonctionnalités Clés

### 🧠 Génération Intelligente
- **Algorithme Déterministe (ClassicRuleStrategy)** : Génération de 3 variantes de parcours (**ECONOMY**, **BALANCED**, **COMFORT**) basées sur le budget, la durée et l'effort.
- **Lieux Obligatoires** : Prise en compte prioritaire des POIs sélectionnés par l'utilisateur.
- **Scoring Multicritères** : Prise en compte du rating, du coût, de la proximité géographique et de la diversité des activités.

### 🖼️ Présentation Riche & Médias
- **Données réelles Google Places** : Photos haute résolution pour chaque étape et informations sur les horaires d'ouverture.
- **Météo Temps Réel** : Intégration de l'API **OpenWeatherMap** pour adapter le parcours aux conditions climatiques actuelles (priorise les musées en cas de pluie, etc.).
- **Carte Interactive** : Tracé précis du chemin à pied entre les étapes via **Google Directions API (Polyline)**.

### 💾 Persistance & Social
- **Sauvegarde & Like** : Système de favoris avec animation "Heart Pop" et persistance locale (Room).
- **Partage Cloud** : Génération d'un lien de partage public stocké sur **Firestore**.
- **Export PDF** : Génération de fiches récapitulatives professionnelles côté serveur (`pdfkit`) téléchargeables sur le mobile.

### 📶 Mode Hors-ligne
- **Cache Intelligent** : Cache serveur (Firestore 24h) et cache mobile (Room v3) avec timestamp d'expiration (`cachedAt`), permettant de consulter ses parcours même sans connexion.

---

## 🛠️ Architecture Technique

### Backend (Firebase Functions — Node.js 22 / TypeScript)
- **SOLID & Clean Code** : Architecture modulaire découpée en Services, Stratégies et Validateurs.
- **Injection de Dépendances** : Services (Google, Weather, Routing) injectés pour une meilleure testabilité.
- **Performance** : Parallélisation des appels API (Places API) via `Promise.allSettled`.

### Frontend (Android — Java 17)
- **Architecture MVVM** : Séparation stricte entre la logique métier (`ViewModel`), les données (`Repository`) et la vue.
- **Reactive Programming** : Utilisation intensive de **RxJava 3** pour la gestion asynchrone (DB, Réseau).
- **View Binding** : Code UI sécurisé et performant.

---

## 📦 Bibliothèques Principales

| Domaine | Technologie |
| :--- | :--- |
| **Backend** | Firebase Functions, Firestore, pdfkit, Axios |
| **Image** | Glide (Chargement & Mise en cache) |
| **Données** | Room (Persistance), DataStore (Settings), Gson |
| **Cartographie** | Google Maps SDK, Directions API, Maps Utils |
| **API Tierces** | Google Places, OpenWeatherMap |

---

## 🚀 Installation & Configuration

1. **Backend** :
   - Configurer un projet Firebase.
   - Ajouter `MAPS_API_KEY` et `OPENWEATHER_API_KEY` dans le fichier `functions/.env`.
   - Déployer : `firebase deploy --only functions`.

2. **Android** :
   - Ajouter le fichier `google-services.json`.
   - Configurer `MAPS_API_KEY` dans `local.properties`.
   - Compiler via Android Studio (AGP 8.2.2).

---

## 📝 Auteurs
Projet réalisé dans le cadre du Master 1 Génie Logiciel — Faculté des Sciences de Montpellier.
