Here's the improved `README.md` file incorporating the new content while maintaining the existing structure and coherence:

# Project Title

## Description
A brief description of the project, its purpose, and functionality.

## Installation
Instructions on how to install and set up the project.

## Usage
How to use the project, including examples and code snippets.

## AI Implementációs specifikáció – QR beolvasás és új eszköz regisztráció

### 1) UI követelmény
- A főoldalon legyen egy gomb: **`Read QR Code`** amely kizarolag akkor elerheto, ha a felhasznalo mar tul van a sikeres regisztracion.

### 2) Kamera jogosultság kezelése
- App indulásakor egyszer ellenőrizni kell a kameraengedély állapotát.
- Ha korábban nem volt megadva, engedélykérés jelenjen meg.
- Ha a felhasználó elutasítja, a QR-beolvasás ne induljon; jelenjen meg felhasználói hibaüzenet.
- Ha engedélyezett, a `Read QR Code` funkció használható.

### 3) QR payload feldolgozás
- A beolvasott QR payloadból kötelezően ki kell nyerni:
  - `deviceId`
  - `publicKey`
- Validáció szükséges: hiányzó vagy üres mező esetén hiba.

### 4) DTO összeállítás (`RegistrationApi.kt`)
A végső cél a következő objektum felépítése:

NewDeviceRegistrationDto(
    accountId: String,
    device: DeviceDto,
    dek: DekDto,
)

#### Mezőforrások
- `accountId`: a telefonon, a regisztrációs folyamat során már eltárolt értékből.
- `device` (`DeviceDto`):
  - `deviceId` = QR payload `deviceId`
  - `publicKey` = QR payload `publicKey`
- `dek` (`DekDto`):
  - `deviceId` = QR payload `deviceId`
  - `encDek` = külön folyamat eredménye (ennek részlete NEM része ennek a specifikációnak)

### 5) Hibakezelés (minimum)
- Kameraengedély hiányzik/elutasított.
- QR nem olvasható vagy érvénytelen.
- QR payloadból hiányzik `deviceId` vagy `publicKey`.
- `accountId` nincs lokálisan elérhető állapotban.
- `encDek` előállítás sikertelen.

### 6) Elfogadási kritérium
- A `Read QR Code` gombbal inditott QR-olvaso, a sikeres beolvasás után létrehozott egy érvényes `NewDeviceRegistrationDto`.
- A `/api/v1/root-device` -nak a json DTO elküldesre kerül

Az encDEK folyamat leirasa:
    1. A sajat eszközön tarolt adatok:
        => accountId
        => deviceId
        
    2. Egy post request a szervernek: http://localhost:3000/api/v1/root-device/{accountId}/{deviceId}
        response:
        {
            "id": 8,
            "accountId": "034a4e48-3f60-4b9e-91f0-54cbefe0dede",
            "deviceId": "Hng3I9Ybv5",
            "encDek": "RKSKm6eavE06TjBQelCqFhRf0BHPpV8QdbvdeoYvONNKXt7UaFuqaqihDEQdxxtEkOEcJ7aI9fmTc5yzq6fvhjt0g21561YoSCeUALHwcivIjZO9rBLnWJhR3nHORS3XfRWfi5FJ/hahrPFmc91lcAATnl2UPYELiCWYCBxnayV7lgGHBWpU7MOo5xOMHwFXUEDpcpWdPeX24RmIE0fsiXkFTKw2nPuBv227geWdjf9WlLMBMJfFI+9Yh8Fus/RtCK97B29QyqA5GR7ARmnAJ/vopNU7oAOlo8uRUDh58khD/Z1AIv+Fu+rSuj9spcvlvlDUYkSmxKy2nfsY/DZ00AdFxVBOqdmB1JeCI8v2Wwp/iL3dMvW2QZFx5wM2dFixO4N2bfBSnAMUW3jnAlF15iAR6clu/gCKHd7iYvPo/3Ft4OQy0bDfiP0nl4KKPu+Zxq5f45IEROT4e+PmUXfIRommDS5r9K2F1rcdBH7FoL7UwGlQQB1nJXzJOFiYsj7ZrxX/EXur150BtDmvYGJ2enhjZeQRVcvYKO7feGBH4IrtOLxc+AXojaZL1OOyk/HShwf8cDSKxhpT3qV0r18X5djJ2Vqs8+EHsOPVturmTzdK5pFM2AULIKksNpT34wU1Ky14FZkjC/rGE+AxpYuJuU3dgWATQG2bPopOalIfPZM="
        }
    3. Az encDek a mobil-telefon sajat publicKey-yel lett encryptelve. A CryptoServices elvileg mar rendelkezik a decrypt metodussal. 
    A QR-code-bol beolvasott publicKey ebben a formatumban van: Base64 kódolt X.509 SubjectPublicKeyInfo DER bájtfolyam 

    Az encrypteleshez a metodus mar megvan irva a CryptoService-ben. Ugyan azt hasznald
    

    A decryptalt "DEK" erteket, azzal a publicKey-yel kell encryptelni, amelyet a QR-code-bol beolvastunk.

    4. Az encryptalt erteket a
    - `accountId`: a telefonon, a regisztrációs folyamat során már eltárolt értékből.
    - `device` (`DeviceDto`):
    - `deviceId` = QR payload `deviceId`
    - `publicKey` = QR payload `publicKey`, Base64 kódolt X.509 SubjectPublicKeyInfo DER bájtfolyam
    - `dek` (`DekDto`):
    - `deviceId` = QR payload `deviceId`
    - `encDek` = $NEW_ENCRYPTED_DEK$

Igy mar az objektumnak mindenhol van erteke.
