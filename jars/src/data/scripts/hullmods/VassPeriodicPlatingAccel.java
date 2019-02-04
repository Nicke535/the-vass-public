package data.scripts.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.ShipAPI;

import java.awt.*;

public class VassPeriodicPlatingAccel extends BaseHullMod{
    public static final float TIME_MULT_MAX = 1.3f;
    public static final Color AFTERIMAGE_COLOR = new Color(255, 0, 0, 100);
    public static final float AFTERIMAGE_THRESHHOLD = 0.1f;

    //Changes the ships time mult at every "advanceInCombat", in order to make sure the global time mult is correct in relation to the player ship
    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (!ship.getSystem().isActive() && !ship.getFluxTracker().isOverloadedOrVenting() && !ship.isHulk()) {
            float actualTimeMult = 1f + Math.min((TIME_MULT_MAX - 1f), ship.getVelocity().length() * (TIME_MULT_MAX - 1f) / ship.getMutableStats().getMaxSpeed().getModifiedValue());

            if (ship == Global.getCombatEngine().getPlayerShip()) {
                ship.getMutableStats().getTimeMult().modifyMult("VassPeriodicPlatingDebugID", actualTimeMult);
                Global.getCombatEngine().getTimeMult().modifyMult("VassPeriodicPlatingDebugID", 1f / actualTimeMult);
            } else {
                ship.getMutableStats().getTimeMult().modifyMult("VassPeriodicPlatingDebugID", actualTimeMult);
                Global.getCombatEngine().getTimeMult().unmodify("VassPeriodicPlatingDebugID");
            }

            ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").modifyFlat("VassAfterimageTrackerNullerID",-1);
            ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").modifyFlat("VassAfterimageTrackerID",ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").getModifiedValue()+amount);
            if (ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").getModifiedValue() > AFTERIMAGE_THRESHHOLD) {
                ship.addAfterimage(
                        AFTERIMAGE_COLOR,
                        0, //X-location
                        0, //Y-location
                        ship.getVelocity().getX() * (-0.7f), //X-velocity
                        ship.getVelocity().getY() * (-0.7f), //Y-velocity
                        2f, //Maximum jitter (what does that do?)
                        0f, //In duration
                        0f, //Mid duration
                        0.3f, //Out duration
                        true, //Additive blend?
                        true, //Combine with sprite color?
                        false //Above ship?
                );
                ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").modifyFlat("VassAfterimageTrackerID",ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").getModifiedValue()-AFTERIMAGE_THRESHHOLD);
            }
        } else {
            ship.getMutableStats().getTimeMult().unmodify("VassPeriodicPlatingDebugID");
            if (ship == Global.getCombatEngine().getPlayerShip()) {
                Global.getCombatEngine().getTimeMult().unmodify("VassPeriodicPlatingDebugID");
            }
        }
    }

    //Prevents the hullmod from being put on ships
    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        boolean canBeApplied = false;
        return canBeApplied;
    }
}
