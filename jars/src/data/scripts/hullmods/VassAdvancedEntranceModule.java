package data.scripts.hullmods;

import java.awt.Color;
import java.util.Map;
import java.util.WeakHashMap;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.ShieldAPI.ShieldType;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

public class VassAdvancedEntranceModule extends BaseHullMod {
    public static final float TIME_MULT_PLAYER = 100.0f;
    public static final float TIME_MULT_AI = 100.0f;

    public static final Color JITTER_COLOR = new Color(255, 26, 26,55);
    public static final Color JITTER_UNDER_COLOR = new Color(255, 0, 0,155);

    public static final float ELECTRIC_SIZE = 300.0f;

    //We use a map since the hullmod instance is shared
    public Map<ShipAPI, Boolean> hasFiredLightning = new WeakHashMap<>();

    //Activates a pseudo-hacked periodic breaker while the ship is using its travel drive (and isn't too close to allies to collide)
    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        //Determines if there's a nearby ally we might bump into
        boolean safetyDistanceBreached = false;
        for (ShipAPI possibleCollision : CombatUtils.getShipsWithinRange(ship.getLocation(), ship.getCollisionRadius())) {
            if (!possibleCollision.getCollisionClass().equals(CollisionClass.NONE) &&
                    !possibleCollision.getCollisionClass().equals(CollisionClass.FIGHTER) &&
                    possibleCollision.getOwner() == ship.getOwner()) {
                safetyDistanceBreached = true;
                break;
            }
        }

        if (ship.getTravelDrive().isActive() && !ship.getSystem().isActive() && !ship.isHulk() && !safetyDistanceBreached) {
            //Sets the effectLevel and state variables
            float effectLevel = ship.getTravelDrive().getEffectLevel();
            ShipSystemAPI.SystemState state = ship.getTravelDrive().getState();
			
			//Loops the periodic breaker sound while time is stopped
			Global.getSoundPlayer().playLoop("vass_periodic_breaker_loop", ship, effectLevel, 1f, ship.getLocation(), new Vector2f(0f, 0f));
		
            //Jitter-based code
            float jitterLevel = effectLevel;
            float jitterRangeBonus = 0;
            float maxRangeBonus = 10f;
            if (state == ShipSystemAPI.SystemState.IN) {
                jitterLevel = effectLevel / (1f / ship.getTravelDrive().getChargeUpDur());
                if (jitterLevel > 1) {
                    jitterLevel = 1f;
                }
                jitterRangeBonus = jitterLevel * maxRangeBonus;
            } else if (state == ShipSystemAPI.SystemState.ACTIVE) {
                jitterLevel = 1f;
                jitterRangeBonus = maxRangeBonus;
            } else if (state == ShipSystemAPI.SystemState.OUT) {
                jitterRangeBonus = jitterLevel * maxRangeBonus;
            }
            jitterLevel = (float) Math.sqrt(jitterLevel);
            ship.setJitter(this, JITTER_COLOR, jitterLevel, 3, 0, 0 + jitterRangeBonus);
            ship.setJitterUnder(this, JITTER_UNDER_COLOR, jitterLevel, 25, 0f, 7f + jitterRangeBonus);

            //Makes the effectLevel cubed
            effectLevel *= effectLevel;
            effectLevel *= effectLevel;

            //Adjusts time mult
            float shipTimeMult = 1f;
            if (ship == Global.getCombatEngine().getPlayerShip()) {
                shipTimeMult = 1f + (TIME_MULT_PLAYER - 1f) * effectLevel;
                ship.getMutableStats().getTimeMult().modifyMult("VassAdvancedEntranceModuleDebugID", shipTimeMult);
                Global.getCombatEngine().getTimeMult().modifyMult("VassAdvancedEntranceModuleDebugID", 1f / shipTimeMult);
            } else {
                shipTimeMult = 1f + (TIME_MULT_AI - 1f) * effectLevel;
                ship.getMutableStats().getTimeMult().modifyMult("VassAdvancedEntranceModuleDebugID", shipTimeMult);
                Global.getCombatEngine().getTimeMult().unmodify("VassAdvancedEntranceModuleDebugID");
            }

            //Fires lightning once upon activation
            if (effectLevel >= 0.8f && (hasFiredLightning.get(ship) == null || !hasFiredLightning.get(ship))) {
                hasFiredLightning.put(ship, true);
				/*Lightning based code...*/
                float tempCounter = 0;
                while (tempCounter <= (6.0f / 80f) * ELECTRIC_SIZE) {
                    Global.getCombatEngine().spawnEmpArc(ship,new Vector2f(ship.getLocation().x + MathUtils.getRandomNumberInRange(-ELECTRIC_SIZE, ELECTRIC_SIZE), ship.getLocation().y + MathUtils.getRandomNumberInRange(-ELECTRIC_SIZE, ELECTRIC_SIZE)), null, ship,
                            DamageType.ENERGY, //Damage type
                            0f, //Damage
                            0f, //Emp
                            100000f, //Max range
                            "tachyon_lance_emp_impact",
                            (1f / 8f) * ELECTRIC_SIZE, // thickness
                            JITTER_COLOR, //Central color
                            JITTER_UNDER_COLOR //Fringe Color
                    );
                    tempCounter++;
                }
                //visual effect
                Global.getCombatEngine().spawnExplosion(
                        //where
                        ship.getLocation(),
                        //speed
                        (Vector2f) new Vector2f(0,0),
                        //color
                        JITTER_COLOR,
                        //size
                        (MathUtils.getRandomNumberInRange(75f,100f) / 80f) * ELECTRIC_SIZE,
                        //duration
                        1.0f
                );
            }
        } else {
            ship.getMutableStats().getTimeMult().unmodify("VassAdvancedEntranceModuleDebugID");
            Global.getCombatEngine().getTimeMult().unmodify("VassAdvancedEntranceModuleDebugID");

            //If we aren't using the travel drive, reset the values of everything
            if (!ship.getTravelDrive().isActive()) {
                hasFiredLightning.put(ship, false);
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
