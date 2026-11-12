# HQS Wi-Fi Navi – Android

Nativní diagnostická aplikace pro Android, verze **1.1.0**.

Funkce: skenování Wi-Fi sítí, přehled kanálů 2,4/5/6 GHz, Walking Test s ukládáním měření, Roaming Test, export CSV a síťové testy.

## Sestavení APK

Projekt používá Kotlin, Jetpack Compose, Android Gradle Plugin 8.5.2, Gradle 8.7 a JDK 17.

V záložce [Actions](../../actions) otevři nejnovější běh **Android debug APK**. Pokud uspěje, v části **Artifacts** stáhni **HQS-Wifi-Navi-debug-APK** a v ZIPu najdeš `app-debug.apk` pro Android.

APK zatím není ověřeno na fyzickém telefonu. Android může omezovat četnost skenování Wi-Fi a dostupnost detailních údajů podle verze systému a oprávnění.

## Zdrojový projekt

Zdrojové soubory se automaticky rozbalí a uloží do repozitáře při prvním běhu GitHub Actions. Než se dokončí první běh, mohou být uloženy také v instalačním balíku v adresáři `bootstrap/`.

Součástí jsou příklady lokálního TCP/HTTP test serveru a testovací skripty.
