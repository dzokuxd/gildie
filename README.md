# Gildie 1.4

Plugin Paper/Spigot **1.20+** — gildie z terenem, ochroną budowania, regeneracją TNT, sojuszami, bazami wypadowymi i pełnym systemem **wojen** (jajo, sztandar, statystyki, GUI).

Dane zapisują się w YAML:
- `plugins/Gildie/gildie.yml`
- `plugins/Gildie/regen.yml`
- dane wojen (przez `WarManager`)

**Soft-depend:** [ReiMinimap / WaypointApi](https://github.com/dzokuxd/WaypointApi) — live i stałe waypointy (gildia, sztandar, baza).

## Instalacja

1. Zbuduj: `mvn -q package` (Maven + Java 17).
2. Wrzuć `target/Gildie.jar` do `plugins/` na Paper/Spigot 1.20+.
3. (Opcjonalnie) zainstaluj plugin ReiMinimap z WaypointApi, żeby działały waypointy.
4. Zrestartuj serwer.

## Komendy

### `/g` (aliasy: `/gildia`, `/guild`)

| Komenda | Opis |
|---------|------|
| `/g zaloz <tag>` | Zakłada gildię + teren kołowy o promieniu **50** bloków. Tag 2–5 znaków (litery/cyfry). |
| `/g zapros <nick>` | Zaproszenie gracza (lub różdżka zaproszeń). |
| `/g dolacz <tag>` | Dołącz do gildii po zaproszeniu. |
| `/g opusc` | Opuść gildię. |
| `/g kick <nick>` | Wyrzuć członka (lider/zastępca). |
| `/g lider <nick>` | Przekaż przywództwo. |
| `/g zastepca <nick>` | Nadaj/odbierz zastępcę. |
| `/g rozwiaz` | Rozwiąż gildię (tylko lider). |
| `/g info [tag]` | Informacje o gildii. |
| `/g lista` | Lista gildii. |
| `/g ustawdom` | Ustaw dom gildii. |
| `/g dom` | TP do domu (15 s, poza terenem). |
| `/g ubw` | Ustaw bazę wypadową (1 h, w pobliżu obcego terenu). |
| `/g bw` | TP do bazy wypadowej (15 s). |
| `/g regeneruj` | Regeneruje bloki z TNT **≤ Y=60**. Bossbar: ilość, %, czas. |
| `/g panel` | Menu fosy / dig. |
| `/g peryskop` | Widok z góry na teren gildii. |
| `/g sojusz` | Zarządzanie sojuszami (koszt, limit członków sojuszu). |
| `/g wojna` | Panel wojen (GUI). |
| `/g pp` | Ping lokalizacji do gildii (live WP + scoreboard). Wymaga ReiMinimap. |

### `/wojna` (aliasy: `/wojny`, `/war`)

| Komenda | Opis |
|---------|------|
| `/wojna` | GUI systemu wojen. |
| `/wojna wyzwij <tag> [1-3]` | Wyzwanie na wojnę. |
| `/wojna stats <tag>` | Statystyki. |
| `/wojna historia` | Historia wojen. |

**Permission:** `gildie.admin` (domyślnie OP) — m.in. admin wojna/TNT.

## Zasady terenu i TNT

- Osoba spoza gildii **nie może stawiać ani niszczyć** na terenie.
- Każdy wybuch TNT na terenie gildii zapisuje zniszczone bloki.
- Bloki **powyżej Y=60** wracają automatycznie po **20 sekundach**.
- Bloki **na Y=60 i niżej** wracają dopiero po `/g regeneruj`.
- Na terenie gildii widać bossbar:
  - **zielony** — Twoja gildia
  - **czerwony** — obca gildia

## Wojny (skrót)

- GUI z `/g wojna` / `/wojna`.
- **Jajo** gildii z hologramem (`EggHologram`).
- **Sztandar** — nosiciel ma live waypoint (odświeżany ~1.5 s).
- TNT można wyłączyć globalnie (przez `TntManager`).
- Statystyki: koxy, perły, TNT, zabójstwa itd.
- Ticki: raid bases, wojna, regen jajka, flush, banner waypoints.

## Integracja z WaypointApi

W `plugin.yml` jest już:

```yaml
softdepend: [ReiMinimap]
```

Gildie łączy się z API **refleksją** (`pl.gildie.util.WaypointHook`) — bez twardej zależności Maven:

- waypoint centrum gildii przy założeniu
- live WP nosiciela sztandaru
- możliwość dodawania WP gildyjnych / globalnych z kodu

Szczegóły API: repozytorium [WaypointApi](https://github.com/dzokuxd/WaypointApi) → `API_USAGE.md`.

## Struktura kodu (src)

```
pl.gildie
├── GildiePlugin.java          # onEnable / schedulery / managers
├── commands/
│   ├── GCommand.java          # /g (główna logika)
│   └── WojnaCommand.java      # /wojna
├── listeners/
│   ├── ProtectionListener, ExplosionListener, TerritoryListener
│   ├── JoinListener, InviteWandListener, InventoryListener
│   ├── PeriscopeListener, WarListener
├── managers/
│   ├── GuildManager, RegenManager, TerritoryBarManager
│   ├── DigManager, PeriscopeManager, MenuManager
├── model/ Guild, ItemBuilder
├── util/ ItemCost, TeleportUtil, WaypointHook
└── war/
    ├── WarManager, War, WarStats, WarGui
    ├── BannerItem, EggHologram, TntManager
```

## Build

- Java 17
- Paper API 1.20.4 (provided)
- `mvn -q package` → `target/Gildie.jar`
