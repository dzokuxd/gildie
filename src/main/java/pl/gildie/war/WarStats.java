package pl.gildie.war;

/**
 * Statystyki jednej gildii w ramach wojny.
 */
public class WarStats {
    private int kills;
    private int deaths;
    private int rankingGained; // na razie zawsze 0, kiedyś dodamy
    private int koxyEaten;
    private int refillsUsed;
    private int pearlsUsed;
    private int tntFired;
    private int eggHits; // uderzenia w jajo przeciwnika

    public WarStats() {}

    public WarStats(int kills, int deaths, int rankingGained, int koxyEaten,
                    int refillsUsed, int pearlsUsed, int tntFired, int eggHits) {
        this.kills = kills;
        this.deaths = deaths;
        this.rankingGained = rankingGained;
        this.koxyEaten = koxyEaten;
        this.refillsUsed = refillsUsed;
        this.pearlsUsed = pearlsUsed;
        this.tntFired = tntFired;
        this.eggHits = eggHits;
    }

    public int getKills() { return kills; }
    public void addKill() { kills++; }
    public void setKills(int kills) { this.kills = kills; }

    public int getDeaths() { return deaths; }
    public void addDeath() { deaths++; }
    public void setDeaths(int deaths) { this.deaths = deaths; }

    public int getRankingGained() { return rankingGained; }
    public void setRankingGained(int rankingGained) { this.rankingGained = rankingGained; }

    public int getKoxyEaten() { return koxyEaten; }
    public void addKox() { koxyEaten++; }
    public void setKoxyEaten(int koxyEaten) { this.koxyEaten = koxyEaten; }

    public int getRefillsUsed() { return refillsUsed; }
    public void addRefill() { refillsUsed++; }
    public void setRefillsUsed(int refillsUsed) { this.refillsUsed = refillsUsed; }

    public int getPearlsUsed() { return pearlsUsed; }
    public void addPearl() { pearlsUsed++; }
    public void setPearlsUsed(int pearlsUsed) { this.pearlsUsed = pearlsUsed; }

    public int getTntFired() { return tntFired; }
    public void addTnt() { tntFired++; }
    public void setTntFired(int tntFired) { this.tntFired = tntFired; }

    public int getEggHits() { return eggHits; }
    public void addEggHit() { eggHits++; }
    public void setEggHits(int eggHits) { this.eggHits = eggHits; }

    public void reset() {
        kills = 0;
        deaths = 0;
        rankingGained = 0;
        koxyEaten = 0;
        refillsUsed = 0;
        pearlsUsed = 0;
        tntFired = 0;
        eggHits = 0;
    }
}
