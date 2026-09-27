package net.earthmc.routefinder.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Immutable vote-party progress from the official EarthMC server endpoint. */
public record VotePartyStatus(int target,int remaining,long fetchedAtMs){
    public VotePartyStatus {
        if (target <= 0 || remaining < 0 || remaining > target) throw new IllegalArgumentException("Invalid vote-party counts");
    }
    public int completed(){return target-remaining;}
    public int percent(){return (int)(completed()*100L/target);}
    public static VotePartyStatus parse(String json,long now){
        try {
            JsonObject root=JsonParser.parseString(json).getAsJsonObject();
            JsonObject vp=root.getAsJsonObject("voteParty");
            if(vp==null)return null;
            int target=vp.get("target").getAsBigDecimal().intValueExact();
            int remaining=vp.get("numRemaining").getAsBigDecimal().intValueExact();
            return new VotePartyStatus(target,remaining,now);
        } catch (RuntimeException e) { return null; }
    }
}
