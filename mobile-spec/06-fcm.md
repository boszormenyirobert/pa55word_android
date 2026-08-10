# 15. Firebase Cloud Messaging (FCM)

## Cél

Az alkalmazás Firebase Cloud Messaging (FCM) szolgáltatást használ push értesítések fogadására.

Az FCM token lekérése **nem történhet meg az alkalmazás első indításakor**, és **nem része a regisztrációs requestnek**.

Az FCM token kizárólag sikeres eszközregisztráció után generálható és küldhető el a backend részére.

---

## Firebase konfiguráció

A projekt Firebase konfigurációját a `google-services.json` fájl biztosítja. Location mobile-spec folder.
A file-t helyezzd at a megfelelö helyre, ahol az ertekek, secretek biztonsagban vannak build utan is.

Példa:

```properties
FIREBASE_API_KEY=AIzaSyAwZXKyzRlWXiMs-UsCArsnJbxS0SbsQoM
FIREBASE_AUTH_DOMAIN=zerointrusionlock.firebaseapp.com
FIREBASE_PROJECT_ID=zerointrusionlock
FIREBASE_STORAGE_BUCKET=zerointrusionlock.firebasestorage.app
FIREBASE_MESSAGING_SENDER_ID=561286686541
FIREBASE_APP_ID=1:561286686541:android:eedfd1e0f275e6fcdfb904
```

---

## FCM token generálási folyamat

Az FCM token lekérése csak akkor történhet meg, ha az alábbi feltételek teljesülnek:

- a regisztráció sikeresen befejeződött;
- a backend `success=true` választ adott;
- a backend visszaadta az `accountId` értékét;
- az alkalmazás rendelkezik a regisztráció során generált `deviceId`-val.

Csak ezek után kérhető le az FCM token.

---

## FirebaseService

Az FCM kezelés külön service-ben történjen.

```kotlin
interface FirebaseService {

    suspend fun getFcmToken(): String

    suspend fun registerFcmToken(
        accountId: String,
        deviceId: String
    )
}
```

### Feladatok

- Firebase inicializálása
- FCM token lekérése
- Token elküldése a backendnek
- Hibakezelés
- Dependency Injection kompatibilis
- Interface alapú
- Unit tesztelhető

---

## Backend API

Endpoint:

```text
POST /api/v1/fcm
```

Request:

```json
{
    "accountId": "3def50d7-625f-4fb4-9b8a-7c754607a797",
    "deviceId": "A82kL91zQ0",
    "fcmToken": "<firebase_fcm_token>"
}
```

---

## Meghívási sorrend

```text
Registration
        │
        ▼
Backend Registration API
        │
        ▼
success == true
        │
        ▼
accountId mentése
        │
        ▼
FirebaseService.getFcmToken()
        │
        ▼
FirebaseService.registerFcmToken()
```

---

## Hibakezelés

Kezelni kell:

- Firebase inicializációs hiba
- FCM token lekérési hiba
- nincs internet
- timeout
- HTTP 400
- HTTP 500

Ha az FCM token regisztráció sikertelen, **az eszközregisztráció ettől még sikeresnek tekintendő**.

Az FCM token később újraküldhető.

---

## Fejlesztési szabály

- Az FCM token nem tárolható hardcoded értékként.
- Az FCM token lekérése kizárólag a Firebase Messaging SDK használatával történhet.
- Az FCM token csak sikeres regisztráció után küldhető el a backendnek.
- Az FCM token küldése külön API hívás legyen, ne része a regisztrációs requestnek.
- Az FCM kezelés külön Service-ben valósuljon meg.
- A Service legyen interface alapú, Hilt kompatibilis és unit tesztelhető.