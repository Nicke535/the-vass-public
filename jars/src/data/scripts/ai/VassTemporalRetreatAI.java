//Man, is this not a good way to handle it. An overhaul is in order, but hey.
package data.scripts.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.shipsystems.VassTemporalRetreat;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

public class VassTemporalRetreatAI implements ShipSystemAIScript {
    //Handles the "weight" of each stat, IE how much each stat weighs when considering a jump-location
    //Note that "lowhull" weights apply once hull reached 50% of max
    private static final float HITPOINT_WEIGHT = 3f;
    private static final float HITPOINT_WEIGHT_LOWHULL = 6f;
    private static final float ARMOR_WEIGHT = 1.2f;
    private static final float ARMOR_WEIGHT_LOWHULL = 2f;
    private static final float HARDFLUX_WEIGHT = 0.4f;
    private static final float SOFTFLUX_WEIGHT = 0.15f;

    //How high weight we need, in total, to jump back in time
    private static final float WEIGHT_THRESHHOLD = 1200f;

    //Internal varibles
    private ShipSystemAPI system;
    private ShipAPI ship;

    //Checks quite often for a good activation time
    private final IntervalUtil tracker = new IntervalUtil(0.03f, 0.05f);


    @Override
    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.system = system;
    }

    @Override
    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        tracker.advance(amount);

        //Once the interval has elapsed...
        if (tracker.intervalElapsed() && !system.isActive()) {
            //Check if we have any data for our "destination time"; if we don't, we can't jump anyhow
            if (!(Global.getCombatEngine().getCustomData().get(ship.getId()+"VASS_TEMPORAL_RETREAT_KEY") instanceof VassTemporalRetreat.TimePointData)) {
                return;
            }
            VassTemporalRetreat.TimePointData timePointData = (VassTemporalRetreat.TimePointData)Global.getCombatEngine().getCustomData().get(ship.getId()+"VASS_TEMPORAL_RETREAT_KEY");

            //Look at the time point data and see if there's a ship in the way; if there is, we don't use the system
            for (ShipAPI possibleCollision : CombatUtils.getShipsWithinRange(timePointData.position, ship.getCollisionRadius())) {
                if (possibleCollision == ship) { continue; }
                else if (possibleCollision.getHullSize().equals(ShipAPI.HullSize.FIGHTER)) { continue; }
                else { return; }
            }

            //Then, we compare projectiles: are there *lethal* amounts of projectiles at the destination time? If so, don't jump
            float damageAtDest = 0f;
            List<DamagingProjectileAPI> projList = CombatUtils.getProjectilesWithinRange(timePointData.position, ship.getCollisionRadius());
            projList.addAll(CombatUtils.getMissilesWithinRange(timePointData.position, ship.getCollisionRadius()));
            for (DamagingProjectileAPI proj : projList) {
                damageAtDest += proj.getDamageAmount();
            }
            if (damageAtDest > timePointData.hitPoints *2f) { return; } //Use 2x hitPoints as approximation, since armor and shields are a thing but take too much power to calculate

            //Finally, check if we actually were better last frame (IE did we have significantly higher hitPoints, armor or flux)
            //For this we use an arbitrary "weight"; it increases the more hitPoints/armor we had at the destination, and decreases the more flux we had
            float jumpWeight = 0f;
            float hitPointWeight = HITPOINT_WEIGHT;
            if (ship.getHullLevel() < 0.5f) {
                hitPointWeight = HITPOINT_WEIGHT_LOWHULL;
            }
            jumpWeight += (timePointData.hitPoints - ship.getHitpoints()) * hitPointWeight;
            jumpWeight += (ship.getFluxTracker().getHardFlux() - timePointData.hardFlux) * HARDFLUX_WEIGHT;
            jumpWeight += ((ship.getFluxTracker().getCurrFlux() - ship.getFluxTracker().getHardFlux()) - timePointData.softFlux) * SOFTFLUX_WEIGHT;

            //Armor is trickier, but uses the same overall method
            float armorCalcWeight = 0f;
            int numberOfCells = 0;
            for (int ix = 0; ix < (ship.getArmorGrid().getLeftOf() + ship.getArmorGrid().getRightOf()); ix++) {
                for (int iy = 0; iy < (ship.getArmorGrid().getAbove() + ship.getArmorGrid().getBelow()); iy++) {
                    armorCalcWeight += timePointData.armor[ix][iy] - ship.getArmorGrid().getGrid()[ix][iy];
                    numberOfCells++;
                }
            }
            armorCalcWeight /= (float)numberOfCells;
            float armorWeight = ARMOR_WEIGHT;
            if (ship.getHullLevel() < 0.5f) {
                armorWeight = ARMOR_WEIGHT_LOWHULL;
            }
            jumpWeight += armorCalcWeight * armorWeight;

            //Finally, if our weight is good enough, jump
            if (jumpWeight > WEIGHT_THRESHHOLD) {
                activateSystem();
            }
        }
    }

    private void deactivateSystem() {
        if (system.isOn()) {
            ship.useSystem();
        }
    }

    private void activateSystem() {
        if (!system.isOn()) {
            ship.useSystem();
        }
    }
}
