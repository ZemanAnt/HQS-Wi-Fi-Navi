# HQS Wi-Fi Navi – vytvoření repozitáře a sestavení Android APK

**Cíl:** `https://github.com/ZemanAnt/hqs-wifi-navi` (soukromý repozitář).

## Doporučený postup (Windows)

1. Rozbalte **celý** ZIP do jedné složky; soubory `settings.gradle.kts`, `PUBLIKOVAT_NA_GITHUB.bat` a složka `.github` musejí být v kořeni projektu.
2. Nainstalujte [Git pro Windows](https://git-scm.com/downloads/win) a [GitHub CLI](https://cli.github.com/). Po instalaci zavřete a znovu otevřete Průzkumníka či terminál, aby se aktualizovala proměnná PATH.
3. Dvakrát klikněte na `PUBLIKOVAT_NA_GITHUB.bat`. Pokud bude potřeba, otevře se přihlášení ke GitHubu v prohlížeči. Zkontrolujte, že se přihlašujete jako **ZemanAnt**.
4. Skript založí **soukromý** repozitář `ZemanAnt/hqs-wifi-navi`, nahraje zdrojový kód a otevře stránku GitHub Actions.
5. Po automatickém běhu workflow `Android debug APK` otevřete daný běh a dole v sekci **Artifacts** stáhněte `HQS-Wifi-Navi-debug-APK`. Stažený ZIP rozbalte; obsahuje `app-debug.apk`.

GitHub Actions může vyžadovat povolení na stránce repozitáře. Pokud se sestavení samo nespustí, otevřete **Actions → Android debug APK → Run workflow**.

**Důležité:** Jde o **debug APK pro testování**. Na reálném telefonu dosud neproběhl instalační ani funkční test. Pro veřejnou distribuci je nutné podepsané release APK nebo AAB.

## Pokud si chcete repozitář založit ručně

1. Otevřete [github.com/new](https://github.com/new).
2. Owner: `ZemanAnt`; Repository name: `hqs-wifi-navi`; visibility: **Private**.
3. **Nezaškrtávejte** vytvoření README, `.gitignore` ani licence, aby byl nový repozitář prázdný.
4. Rozbalte celý projekt; otevřete PowerShell v jeho kořeni a spusťte:

```powershell
git init -b main
git add .
git commit -m "Initial HQS Wi-Fi Navi Android"
git remote add origin https://github.com/ZemanAnt/hqs-wifi-navi.git
git push -u origin main
```

Pokud `git commit` žádá identitu, nastavte ji dle svého Git účtu nebo použijte `PUBLIKOVAT_NA_GITHUB.bat` (používá noreply e-mail). Přihlášení při push lze provést přes Git Credential Manager.

## Důležitá poznámka k propojení ChatGPT a GitHubu

Pokud ChatGPT po vytvoření repozitáře nový projekt neuvidí, může být nutné v nastavení instalace GitHub aplikace povolit přístup i k novému repozitáři. V GitHubu hledejte **Settings → Applications → Installed GitHub Apps → Configure → Repository access**. Není nutné repozitář zveřejňovat.

## Stav sestavení

Workflow je připraveno v `.github/workflows/android-apk.yml` a používá JDK 17, Android SDK 34 a Gradle wrapper 8.7. Zatím nebylo spuštěno na GitHubu; výsledné APK zde tudíž není ověřené. Podrobnosti k aplikaci a měření jsou v `README.md`.
