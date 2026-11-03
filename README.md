# ULI

Android appka na drill zápočtového testu BI-ULI (Úvod do Linuxu, FIT ČVUT): 70 otázok z procvičovacieho testu na
fit-wiki, ostrý test je 50 náhodných z nich, 1,6 b za otázku, treba 64 z 80 b.

- Otázky: `content/questions.json` (zdroj pravdy). Otázky a možnosti sú po anglicky ako na skúške, vysvetlenia, preklad,
  pomôcky a ukážky terminálu po slovensky. `source: "disputed: …"` = kľúč NDG sa líši od reálneho Linuxu, appka platí kľúč
  a ukáže obe. Kontrola: `python3 -I content/check.py`.
- Učenie: Leitner boxy (`engine/Srs.kt`), kolo 10 otázok, chyba sa vráti o 3 karty, správna odpoveď posunie kartu len
  keď je na rade. Odhad a šanca prejsť počítajú z boxov, nikdy nestúpnu po chybe.
- Postup je v `filesDir/progress.json`, rozrobené kolo alebo skúška v `session.json`. Poškodený súbor sa nezmaže, odloží sa
  ako `progress.corrupt-<čas>.json`.

## Build

```sh
scripts/gate.sh                      # testy + debug build + kontrola obsahu
scripts/capture.sh <dir> [serial]    # zábery všetkých stavov v svetlej aj tmavej téme (emulátor)
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:assembleRelease   # podpísané APK
```

Release kľúč: `~/.uli/android-release.jks` + `~/.uli/android-signing.properties` (mimo repa, na Mac mini). Rovnaký kľúč
musí podpisovať každú verziu, inak Android aktualizáciu nenainštaluje. Bez neho release build zlyhá.
