//By Nicke535, speeds up the firerate of a weapon depending on a ship's time mult
package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import org.lazywizard.lazylib.combat.CombatUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class VassYawarakaiTeScript implements EveryFrameWeaponEffectPlugin {

    //Interval for periodic pulses
    private static final float PULSE_TIME = 0.15f;

    //Extra effect weight to add to targets within our arc
    private static final float ARC_WEIGHT = 3f;

    //Our hashmap to keep track of damage dealt to each missile.
    //Note that it's a WEAK hashmap: don't go iterating over it willy-nilly or try anything smart, just do insertions and lookups
    private MissileStatusHashmap missileStatusMap = null;

    //Counter to run periodically
    private float counter = 0f;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        //To enable automatic cleanup, yet to keep the semblance of a statically saved hashmap, we store it in the
        //engine's customData and load it in for each script
        if (missileStatusMap == null) {
            if (engine.getCustomData().get("VassYawarakaiTeEffectID") instanceof MissileStatusHashmap) {
                //Unchecked conversion, but we know what we're doing in this rare instance
                missileStatusMap = (MissileStatusHashmap) engine.getCustomData().get("VassYawarakaiTeEffectID");
            } else {
                missileStatusMap = new MissileStatusHashmap();
                engine.getCustomData().put("VassYawarakaiTeEffectID", missileStatusMap);
            }
        }

        //Only run while firing, and run in pulses
        if (weapon.getChargeLevel() >= 1f) {
            counter+=amount;
            if (counter > PULSE_TIME) {
                counter -= PULSE_TIME;


                //Then, we do the real part of the script: find nearby missiles so we can do stuff
                float missileCountWeighted = 0f;
                List<MissileAPI> highPrioMissiles = new ArrayList<>();
                List<MissileAPI> lowPrioMissiles = new ArrayList<>();
                for (MissileAPI msl : CombatUtils.getMissilesWithinRange(weapon.getLocation(), weapon.getRange())) {
                    //Ignore friendlies
                    if (msl.getOwner() == weapon.getShip().getOwner()) {
                        continue;
                    }

                    if (missileStatusMap.get(msl) == null) {
                        missileStatusMap.put(msl, 0f);
                    } else {
                        if (missileStatusMap.get(msl) > msl.getHitpoints()) {
                            //Flame out missiles that take too much "fake damage" from us
                            msl.flameOut();
                            continue;
                        }
                    }

                    //Register the missile as something to affect, either in the prioritized effect group or the low-prio one depending on if it's within arc
                    if (weapon.distanceFromArc(msl.getLocation()) <= 0f) {
                        missileCountWeighted += ARC_WEIGHT;
                        highPrioMissiles.add(msl);
                    } else {
                        missileCountWeighted += 1f;
                        lowPrioMissiles.add(msl);
                    }
                }

                //Then, we deal "fake damage" to the missiles; degradation damage, which triggers a flameout when built up
                for (MissileAPI msl : highPrioMissiles) {
                    float addedDamage = weapon.getDamage().computeDamageDealt(PULSE_TIME) * ARC_WEIGHT / missileCountWeighted;
                    missileStatusMap.put(msl, missileStatusMap.get(msl)+addedDamage);
                }
                for (MissileAPI msl : highPrioMissiles) {
                    float addedDamage = weapon.getDamage().computeDamageDealt(PULSE_TIME) * ARC_WEIGHT / missileCountWeighted;
                    missileStatusMap.put(msl, missileStatusMap.get(msl)+addedDamage);
                }
                //TODO: fix bloody VFX
            }
        }
    }

    /**
     * It's really only here to solve typecasting safety issues
     */
    private class MissileStatusHashmap extends HashMap<MissileAPI, Float> {}
}