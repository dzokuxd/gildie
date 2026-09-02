package pl.gildie.war;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Reprezentuje jedną wojnę między dwiema gildiami.
 * Zapis flat (YAML) – kiedyś MySQL.
 */
public class War {

    public enum State {
        ACTIVE,
        ENDED_CONQUEST,
        ENDED_KILLS,
        ENDED_TIMEOUT
    }

    private final UUID id;
    private final String attackerTag;
    private final String defenderTag;
    private final long startTime;
    private final long durationMs; // 1h–3h
    private long endTime;
    private State state = State.ACTIVE;

    // Statystyki obu gildii (klucz = tag gildii)
    private final Map<String, WarStats> stats = new HashMap<>();

    // Aktualny sztandar powiązany z tą wojną (jeśli trwa podbicie)
    private UUID activeBannerId;
    private String bannerCarrierGuild; // tag gildii która aktualnie ma sztandar
    private UUID bannerCarrierPlayer;

    public War(UUID id, String attackerTag, String defenderTag, long durationMs) {
        this.id = id;
        this.attackerTag = attackerTag.toUpperCase();
        this.defenderTag = defenderTag.toUpperCase();
        this.startTime = System.currentTimeMillis();
        this.durationMs = durationMs;
        this.endTime = startTime + durationMs;
        this.stats.put(this.attackerTag, new WarStats());
        this.stats.put(this.defenderTag, new WarStats());
    }

    // Konstruktor do ładowania z YAML
    public War(UUID id, String attackerTag, String defenderTag, long startTime, long durationMs,
               long endTime, State state, Map<String, WarStats> stats) {
        this.id = id;
        this.attackerTag = attackerTag.toUpperCase();
        this.defenderTag = defenderTag.toUpperCase();
        this.startTime = startTime;
        this.durationMs = durationMs;
        this.endTime = endTime;
        this.state = state;
        this.stats.putAll(stats);
    }

    public UUID getId() { return id; }
    public String getAttackerTag() { return attackerTag; }
    public String getDefenderTag() { return defenderTag; }
    public long getStartTime() { return startTime; }
    public long getDurationMs() { return durationMs; }
    public long getEndTime() { return endTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }
    public State getState() { return state; }
    public void setState(State state) { this.state = state; }

    public WarStats getStats(String tag) {
        return stats.computeIfAbsent(tag.toUpperCase(), t -> new WarStats());
    }

    public Map<String, WarStats> getAllStats() { return stats; }

    public boolean isParticipant(String tag) {
        if (tag == null) return false;
        tag = tag.toUpperCase();
        return tag.equals(attackerTag) || tag.equals(defenderTag);
    }

    public String getOpponent(String tag) {
        if (tag == null) return null;
        tag = tag.toUpperCase();
        if (tag.equals(attackerTag)) return defenderTag;
        if (tag.equals(defenderTag)) return attackerTag;
        return null;
    }

    public boolean isActive() {
        return state == State.ACTIVE && System.currentTimeMillis() < endTime;
    }

    public boolean isExpired() {
        return state == State.ACTIVE && System.currentTimeMillis() >= endTime;
    }

    public long getRemainingMs() {
        return Math.max(0, endTime - System.currentTimeMillis());
    }

    public UUID getActiveBannerId() { return activeBannerId; }
    public void setActiveBannerId(UUID activeBannerId) { this.activeBannerId = activeBannerId; }
    public String getBannerCarrierGuild() { return bannerCarrierGuild; }
    public void setBannerCarrierGuild(String bannerCarrierGuild) {
        this.bannerCarrierGuild = bannerCarrierGuild == null ? null : bannerCarrierGuild.toUpperCase();
    }
    public UUID getBannerCarrierPlayer() { return bannerCarrierPlayer; }
    public void setBannerCarrierPlayer(UUID bannerCarrierPlayer) { this.bannerCarrierPlayer = bannerCarrierPlayer; }

    public void clearBanner() {
        this.activeBannerId = null;
        this.bannerCarrierGuild = null;
        this.bannerCarrierPlayer = null;
    }
}
