# Focus

Lock-in pour Android. Tu définis une durée. Les apps de distraction que tu as cochées restent utilisables par le système — jusqu’au moment où tu les ouvres. Là, Focus les scelle.

## La punition

Ouvrir une app bloquée pendant une session :

1. Focus passe devant, plein écran, fond noir.
2. L’app est **scellée**. Retour, Home, relance : tu retombes sur le compteur.
3. Durée : **5 min**, puis **10**, puis **20**, puis **40** (plafond) si tu réouvres la même app après un sceau.
4. Le reste du téléphone n’est pas pris en otage : appels, SMS, réglages, launcher.

On ne sépare pas le feed Instagram des DMs. Android ne le permet pas de façon fiable. Le compromis : Insta est libre tant que tu ne l’ouvres pas. Dès que tu l’ouvres, tout Insta est scellé.

## Ce que ça ne promet pas

Un service d’accessibilité se désactive dans les réglages. Focus ne cache pas ce chemin. Le but est d’ajouter assez de friction pour tuer le réflexe des 20 secondes, pas d’emprisonner le téléphone.

## Build

Ouvre le dossier `android/` dans Android Studio (JDK 17). Laisse-le générer le Gradle wrapper, puis :

`Build > Build APK(s)`

Sideload l’APK. Au premier lancement : active le service d’accessibilité Focus, et retire l’optimisation batterie.

## Itération

Le comportement (liste d’apps, durée des sceaux, écran de honte) vit dans :

- `SessionStore.kt` — session, strikes, durées
- `FocusAccessibilityService.kt` — détection de l’app au premier plan
- `ShameActivity.kt` — écran de honte

Les retours d’usage se règlent là, pas dans une surcouche.
