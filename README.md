# Gildie 1.1

Plugin Paper/Spigot 1.20+ — gildie z terenem, ochrona budowania i regeneracja TNT.
Wszystko zapisuje się w plikach YAML (`plugins/Gildie/gildie.yml` i `regen.yml`).

## Instalacja

1. Zbuduj plugin: `mvn -q package` (wymaga Mavena i Javy 17).
2. Wrzuć `target/Gildie.jar` do folderu `plugins/` na serwerze Paper/Spigot 1.20+.
3. Zrestartuj serwer.

## Komendy

| Komenda | Opis |
|---|---|
| `/g zaloz <tag>` | Zakłada gildię + teren kołowy o promieniu 50 bloków. Tag 2–5 znaków. |
| `/g regeneruj` | Regeneruje bloki z TNT **poniżej Y=60**. Bossbar: ilość, %, czas. |

## Zasady

- Osoba spoza gildii **nie może stawiać ani niszczyć** na terenie.
- Każdy wybuch TNT na terenie gildii zapisuje zniszczone bloki.
- Bloki **powyżej Y=60** wracają automatycznie po **20 sekundach**.
- Bloki **na Y=60 i niżej** wracają dopiero po `/g regeneruj`.
- Na terenie gildii widać bossbar:
  - **zielony** — Twoja gildia
  - **czerwony** — obca gildia
