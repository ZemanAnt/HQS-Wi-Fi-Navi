# HQS Wi-Fi Navi – Android 1.2.0

Nativní diagnostická aplikace v Kotlinu / Jetpack Compose pro telefony s **Androidem 8.0 a vyšším** (minSdk 26). Zaměřuje se na Wi-Fi diagnostiku při obchůzkách areálů a budov. Není vázána na konkrétní značku AP.

> **Stav ověření:** zdrojový projekt je připraven pro Android Studio. Model frekvencí a CSV export prošly lokálním Kotlin testem. Model, CSV, výpočty (`NetCalc`) a síťová vrstva (`NetTools`: paralelní TCP, skenery) prošly lokálním během na JVM. **Kompletní sestavení Android aplikace a provoz na telefonu nebyly v tomto prostředí ověřeny**, protože zde není Android SDK a přístup k repozitářům Gradle. APK není přiloženo.

## Funkce

| Karta | Funkce |
|---|---|
| **Skener** | Aktuální AP a RSSI, frekvence, kanál, BSSID, šířka, zabezpečení, graf RSSI; export seznamu viditelných AP do CSV |
| **Kanály** | Graf překryvu v pásmech 2,4 / 5 / 6 GHz, rozlišení kanálu 80+80 MHz, orientační doporučení 1/6/11 podle RSSI a překryvu okolních AP |
| **Chůze** | Půdorys PNG/JPG/WEBP, dotykový záznam RSSI na místech, heatmapa, % bodů ≥ −67 dBm, CSV. Body a odkaz na poslední půdorys se automaticky uchovávají v telefonu a po restartu obnovují. |
| **Roaming** | Přechody BSSID během testu v rámci stejného SSID, graf RSSI, ping-pong detekce, **ping na bránu (~5×/s) – ztráty a latence kolem každého přechodu**, export CSV. Čas přerušení je pouze orientační. |
| **Testy** | **Wi-Fi integrovaný test** (signál, zabezpečení, rušení, brána, DNS, internet, rychlost, sdílení reportu), **rychlost internetu** (Cloudflare), ping/jitter/ztráty, HTTP download, **iPerf klient** (TCP, více vláken). Testy ověřují aktivní Wi-Fi síť, aby nečerpaly mobilní data. |
| **Nástroje** | **Veřejná IP**, **traceroute**, **IP scanner** (hledání zařízení v podsíti), **skener portů**, **kalkulačka šířky pásma** (čas přenosu, kapacita uplinku, CCTV úložiště), **kalkulačka PoE** (třídy 802.3af/at/bt, rozpočet switche). |

## Nejsnazší sestavení ve Windows

1. Nainstalujte **Android Studio** (např. aktuální stabilní verzi), při instalaci také **Android SDK 34** a Android SDK Platform Tools. Pro projekt použijte **JDK 17** (Android Studio obvykle dodá vlastní).
2. Rozbalte celý ZIP. V Android Studiu zvolte **Open** a vyberte složku `HQSWifiNavi` se souborem `settings.gradle.kts`.
3. Vyčkejte na dokončení **Gradle Sync**. První otevření potřebuje internet pro Gradle a knihovny.
4. Pro instalaci připojte telefon USB kabelem a povolte **Možnosti pro vývojáře → Ladění USB**; poté klikněte na zelené tlačítko **Run**.
5. Pro vytvoření APK zvolte **Build → Build Bundle(s) / APK(s) → Build APK(s)** nebo spusťte `SESTAVIT_APK_WINDOWS.bat`.
6. Výsledný soubor: `app/build/outputs/apk/debug/app-debug.apk`.

Debug APK lze do telefonu nainstalovat i ručně, pokud má telefon povolenou instalaci z příslušného zdroje. Pro distribuci mimo vlastní testování je vhodné připravit samostatně **podepsaný release APK**.

### Alternativa: GitHub Actions

V projektu je `.github/workflows/android-apk.yml`. Pokud celý projekt nahrajete do vlastního GitHub repozitáře, v **Actions → Android debug APK → Run workflow** lze na vzdáleném běhovém prostředí vytvořit debug APK. V sekci Artifacts bude ke stažení pod názvem `HQS-Wifi-Navi-debug-APK`. Toto workflow nebylo na GitHubu spuštěno ani ověřeno.

## Síťový test v místní síti

K projektu je přiložena složka `nastroje`. Na PC s Pythonem 3 spusťte `nastroje/SPUSTIT_TEST_SERVER.bat` (případně `python nastroje/HQS_test_server.py`).

- Zjistěte IP počítače příkazem `ipconfig`, např. `192.168.1.10`.
- Telefon i PC musí být na stejné síti Wi-Fi/LAN a firewall musí povolovat porty **8080/TCP** a **5001/TCP** na soukromé síti.
- **Stažení:** v aplikaci `Testy → Rychlost – stažení` zadejte `http://192.168.1.10:8080/download` (nahraďte svou IP).
- **Odesílání:** v `Testy → Rychlost – odeslání` zadejte IP počítače a port `5001`.
- Server zastavíte pomocí **Ctrl+C**. Neprovozujte ho na veřejně přístupné síti; neobsahuje přihlašování.

**Poznámka k iperf:** TCP odesílač není plnohodnotný protokol iperf2/iperf3. Použijte přiložený HQS server, jinak nelze zaručit kompatibilní výsledek.

## Novinky ve verzi 1.2.0 a jejich omezení

- **iPerf klient** odesílá TCP data klient → server ve více vláknech. Je určen pro `iperf -s` (iPerf2) a přiložený HQS server; kompatibilita s iPerf2 nebyla ověřena na reálném serveru. Směr server → klient (`-R`), UDP a retransmise (Android je nedává k dispozici) nejsou podporovány. Místo toho se zobrazuje kolísání propustnosti.
- **Rychlost internetu a integrovaný test** používají `speed.cloudflare.com` a `1.1.1.1`; spotřebují desítky MB dat. Veřejná IP se zjišťuje z `1.1.1.1/cdn-cgi/trace` (záloha ipify).
- **Traceroute** používá systémový `ping -t TTL` (jen IPv4). Časy mezilehlých uzlů jsou orientační.
- **IP scanner** využívá ICMP a krátké TCP sondy; zařízení, která neodpovídají vůbec, se nezobrazí. MAC adresy jsou od Androidu 10 obvykle nedostupné. Skenery používejte jen ve vlastních sítích.
- **Ping při roamingu** míří na bránu zjištěnou při startu testu; vzorkování ≈ 200 ms.

## Omezení Androidu a interpretace

- K načtení okolních AP, SSID a BSSID aplikace potřebuje **oprávnění k přesné poloze** a **zapnuté Polohové služby**. Tato poloha se neukládá, nikam neodesílá, a aplikace nevyžaduje GPS souřadnice.
- Android 9+ omezuje ve výchozím stavu aktivní skenování přibližně na **4 pokusy za 2 minuty v popředí**. Aplikace používá interval 35 sekund. Na některých telefonech mohou být výsledky starší. Čas v UI se odvozuje z oznámení systému o úspěšném skenu.
- Walking Test snímá RSSI **aktuálně připojeného** AP. Pokud telefon přejde na jiné AP, body mohou představovat více různých BSSID. CSV je rozlišuje.
- Heatmapa je **interpolace jednotlivých odečtů**, nikoli přesná měřená mapa všech AP nebo měření spektra.
- Roaming měří stav Wi-Fi podle Android API (běžně cca 300 ms) a **neměří přesnou dobu výpadku datových paketů**. Údaj „výpadek nezaznamenán“ tedy neznamená bezztrátový roaming.
- Při přepnutí aplikace do pozadí se živé skenování a roaming zastaví.
- Zobrazené dBm závisí na konkrétním telefonu a není kalibrovaným měřením.

## Soukromí a data

- Bez reklam, cloudového účtu nebo analytiky.
- Poslední Walking Test a odkaz na půdorys se ukládají do **interního úložiště aplikace**. Půdorys se čte z uživatelem vybraného souboru; po odstranění souboru může být nutné jej načíst znovu.
- CSV se uloží jen tam, kam uživatel explicitně vybere. Vytvořené CSV je UTF-8 s BOM a středníkem, včetně uvozovek a ochrany textových polí před interpretací jako vzorce v tabulce.
- Při odinstalaci aplikace se interní měření smaže. Před odinstalací exportujte CSV.

## Vývoj / testy

- `app/src/main/java/cz/hqsnet/wifinavi/`: UI, model, práce s Wi-Fi, diagnostika, persistence a export.
- `tests/ModelAndCsvTest.kt`: rychlé ověření modelu a exportu čistě v Kotlin/JVM bez Android SDK.
- `nastroje/HQS_test_server.py`: volitelný HTTP/TCP server pro vlastní síť.
- `.github/workflows/android-apk.yml`: volitelné vzdálené sestavení debug APK.

Lokalní JVM test (se samostatně nainstalovaným `kotlinc`):

```sh
kotlinc app/src/main/java/cz/hqsnet/wifinavi/Model.kt \
        app/src/main/java/cz/hqsnet/wifinavi/CsvExport.kt \
        tests/ModelAndCsvTest.kt -include-runtime -d model-tests.jar
java -jar model-tests.jar
```

Pro první průchod na reálném telefonu otestujte oprávnění, Wi-Fi skenování, import PNG, uložení bodů, opětovné spuštění aplikace, export všech tří typů CSV a měření proti LAN serveru.
