package data.scripts.shipsystems.VassIntegratedMountpoints;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;
import com.fs.starfarer.api.util.Misc;
import data.scripts.hullmods.VassIntegratedMountpoint;
import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

public class DefensiveAutotargetingSuite extends SubSystem {
    public static final float DAMAGE_MULT = 4f;
    public static final float TURNRATE_MULT = 21f;
    public static final float DURATION = 5f;
    public static final float COOLDOWN = 12f;

    private CombatEntityAPI target = null;

    @Override
    public void apply(MutableShipStatsAPI stats, String id, ShipSystemStatsScript.State state, ShipAPI ship, boolean player, float effectLevel) {
        //Run unapply if needed
        if (effectLevel <= 0f) {
            unapply(stats, id, ship, player);
            return;
        }

        // Block other weapons from being used
        disableAllOtherWeapons(ship);

        //Stat modifiers
        stats.getDamageToMissiles().modifyMult(id, DAMAGE_MULT);
        stats.getDamageToFighters().modifyMult(id, DAMAGE_MULT);
        stats.getWeaponTurnRateBonus().modifyMult(id, TURNRATE_MULT);
        stats.getBeamWeaponTurnRateBonus().modifyMult(id, TURNRATE_MULT);

        //Set PD on our main weapon
        WeaponAPI weapon = null;
        for (WeaponAPI wep : ship.getAllWeapons()) {
            if (wep.getSlot().getId().equals(VassIntegratedMountpoint.SPECIAL_SLOT_ID)) {
                weapon = wep;
                weapon.setPD(true);
                break;
            }
        }

        // The tricky part: we forcibly re-target the main weapon while the system is active
        if (weapon != null) {
            if (weapon.isDisabled()) {
                return;
            }

            //Do we have a target? If so, verify its validity
            List<ShipAPI> allies = AIUtils.getNearbyAllies(ship, weapon.getRange());
            if (target != null) {
                //Is the target dead?
                if (target.isExpired() || target.getHitpoints() <= 0 || !Global.getCombatEngine().isEntityInPlay(target)) {
                    target = null;
                }
                else if (target instanceof ShipAPI && ((ShipAPI)target).isHulk()) {
                    target = null;
                }

                //Is the target untargetable?
                else if (target.getCollisionClass().equals(CollisionClass.NONE)) {
                    target = null;
                }

                //Is the target in range?
                else if (MathUtils.getDistance(target, weapon.getLocation()) > weapon.getRange()) {
                    target = null;
                }

                //Is there a friendly in the way?
                else if (friendlyInTheWay(ship, weapon, target.getLocation(), allies)) {
                    target = null;
                }
            }

            //If we still have a valid target, there's no need to find a new one
            if (target == null) {
                // Grab all fighters and missiles nearby
                List<CombatEntityAPI> potentialTargets = new ArrayList<>();
                potentialTargets.addAll(AIUtils.getNearbyEnemyMissiles(ship, weapon.getRange()));
                for (ShipAPI potTarget : CombatUtils.getShipsWithinRange(weapon.getLocation(), weapon.getRange())) {
                    if (potTarget.getHullSize() == ShipAPI.HullSize.FIGHTER && potTarget.getOwner() != ship.getOwner()) {
                        potentialTargets.add(potTarget);
                    }
                }

                // Then, grab the nearest target which we don't ignore (for example flares)
                // If we have multiple within 200 SU of the same range, grab the missile with the highest damage (fighters count their damage as 0 for this purpose)
                boolean ignoresFlares = false;
                if (weapon.getShip().getMutableStats().getDynamic().getMod(Stats.PD_IGNORES_FLARES) != null &&
                        weapon.getShip().getMutableStats().getDynamic().getMod(Stats.PD_IGNORES_FLARES).computeEffective(0f) >= 1f) {
                    ignoresFlares = true;
                }
                float highestDamage = 0f;
                float shortestRange = weapon.getRange();
                for (CombatEntityAPI potTarget : potentialTargets) {
                    //Are we a missile, and a flare? If so, ignore if our ship ignores flares
                    if (potTarget instanceof MissileAPI
                            && ((MissileAPI)potTarget).isFlare()
                            && ignoresFlares) {
                        continue;
                    }

                    //Is there a friendly in the way?
                    if (friendlyInTheWay(ship, weapon, potTarget.getLocation(), allies)) {
                        continue;
                    }

                    // Check the range and damage, and pick a high-damage close target (fighters count as low damage)
                    float dist = MathUtils.getDistance(ship, potTarget);
                    if (dist < shortestRange-200f) {
                        shortestRange = dist;
                        highestDamage = 0f;
                        if (potTarget instanceof MissileAPI) {
                            highestDamage = ((MissileAPI) potTarget).getDamageAmount();
                        }
                        target = potTarget;
                    } else if (dist > shortestRange-200f && dist < shortestRange+200f){
                        if (potTarget instanceof MissileAPI) {
                            MissileAPI missile = (MissileAPI)potTarget;
                            if (missile.getDamageAmount() > highestDamage) {
                                highestDamage = missile.getDamageAmount();
                                target = potTarget;
                            }
                        } else {
                            if (highestDamage <= 0f && shortestRange > dist) {
                                shortestRange = dist;
                                target = potTarget;
                            }
                        }
                    }
                }
            }

            //If there was no target after running target detection, we stop firing
            if (target == null) {
                weapon.setForceNoFireOneFrame(true);
            } else {
                //Otherwise, we want to turn towards the target and also fire our weapon
                weapon.setForceFireOneFrame(true);
                float oldAngle = weapon.getCurrAngle();
                float desiredAngle = VectorUtils.getAngle(weapon.getLocation(), target.getLocation());
                float turnThisFrame = weapon.getTurnRate() * Global.getCombatEngine().getElapsedInLastFrame();
                float shortestRot = MathUtils.getShortestRotation(oldAngle, desiredAngle);
                if (Math.abs(shortestRot) < turnThisFrame) {
                    weapon.setCurrAngle(desiredAngle);
                } else {
                    weapon.setCurrAngle(oldAngle + (turnThisFrame* Math.signum(shortestRot)));
                }
            }
        }
    }

    private boolean friendlyInTheWay(ShipAPI ship, WeaponAPI weapon, Vector2f position, List<ShipAPI> allies) {
        //Is there a friendly in the way?
        for (ShipAPI ally : allies) {
            if (ally.getHullSize() == ShipAPI.HullSize.FIGHTER
                || ally.getCollisionClass() == CollisionClass.NONE
                || ally.isPhased()
                || ally == ship) {
                continue;
            }
            if (CollisionUtils.getCollides(weapon.getLocation(), position, ally.getLocation(), ally.getCollisionRadius())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id, ShipAPI ship, boolean player) {
        //Reset our stat increases
        stats.getDamageToMissiles().unmodify(id);
        stats.getDamageToFighters().unmodify(id);
        stats.getWeaponTurnRateBonus().unmodify(id);
        stats.getBeamWeaponTurnRateBonus().unmodify(id);

        //Un-set PD on our main weapon
        for (WeaponAPI wep : ship.getAllWeapons()) {
            if (wep.getSlot().getId().equals(VassIntegratedMountpoint.SPECIAL_SLOT_ID)) {
                wep.setPD(false);
                break;
            }
        }
    }

    @Override
    public String getDisplayName(ShipSystemStatsScript.State state, float effectLevel) {
        return "Defensive Autotargeting";
    }

    @Override
    public String getTextToDisplay(ShipSystemAPI system, ShipAPI ship) {
        if (system.getState().equals(ShipSystemAPI.SystemState.IDLE)) {
            return "Ready";
        } else if (system.getState().equals(ShipSystemAPI.SystemState.ACTIVE)) {
            return "Active";
        } else {
            return null;
        }
    }


    @Override
    public float getActiveOverride(ShipAPI ship) {
        return DURATION;
    }

    @Override
    public float getInOverride(ShipAPI ship) {
        return 0f;
    }

    @Override
    public float getOutOverride(ShipAPI ship) {
        return 0f;
    }

    //Returns (for other scripts that need it) which multiplier we have to apply to our base 10 second cooldown to get an appropriate "new" base cooldown
    public static float getSystemCooldownMult() {
        return (COOLDOWN / 10f);
    }
}
