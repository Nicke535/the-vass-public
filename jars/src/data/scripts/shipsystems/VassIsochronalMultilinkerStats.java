//By Nicke535
//Causes projectile weapons to fire duplicates of their shots at random, and adds strange multi-timeline effects on top to give visual flair
package data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import data.scripts.utils.VassUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class VassIsochronalMultilinkerStats extends BaseShipSystemScript {

	//Basic jitter stats
    public static final float JITTER_OPACITY = 0.07f;
    public static final float JITTER_UNDER_OPACITY = 0.3f;

    //How far away from the original will any given projectile be spawned?
	private static final float PROJECTILE_OFFSET_DISTANCE = 8f;

	//How inaccurate are the new projectiles?
	private static final float PROJECTILE_OFFSET_ANGLE = 3f;

	//How big of a chance is there for each potential extra shot to be spawned?
    private static final float PROJECTILE_SPAWN_CHANCE = 0.7f;

    //How many extra projectiles can be spawned, at most?
    private static final int PROJECTILE_MAX_SPAWNS = 2;

    //Stores projectile's we're not allowed to touch again
	private List<DamagingProjectileAPI> registeredProjectiles = new ArrayList<>();
	
	public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = null;
        if (stats.getEntity() instanceof ShipAPI) {
            ship = (ShipAPI) stats.getEntity();
            id = id + "_" + ship.getId();
        } else {
            return;
        }
		
		//Adds some random-color jitter
	    float jitterLevel = effectLevel;
        float jitterRangeBonus = 0;
        float maxRangeBonus = 10f;
        if (state == State.IN) {
            jitterLevel = effectLevel / (1f / ship.getSystem().getChargeUpDur());
            if (jitterLevel > 1) {
                jitterLevel = 1f;
            }
            jitterRangeBonus = jitterLevel * maxRangeBonus;
        } else if (state == State.ACTIVE) {
            jitterLevel = 1f;
            jitterRangeBonus = maxRangeBonus;
        } else if (state == State.OUT) {
            jitterRangeBonus = jitterLevel * maxRangeBonus;
        }
        jitterLevel = (float) Math.sqrt(jitterLevel);

        //Adds multiple jitters, each with a different color
		for (int i = 0; i < 5; i++) {
			Color jitterColor = VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.MULTA, JITTER_OPACITY);
			ship.setJitter(id + i, jitterColor, jitterLevel, 3, 5f, 7f + jitterRangeBonus);
		}
		for (int i = 0; i < 25; i++) {
			Color jitterUnderColor = VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.MULTA, JITTER_UNDER_OPACITY);
			ship.setJitterUnder(id + i, jitterUnderColor, jitterLevel, 25, 1f, 7f + jitterRangeBonus);
		}

        //Runs the main effect: splitting weapon shots to pieces
        List<DamagingProjectileAPI> projList = CombatUtils.getProjectilesWithinRange(ship.getLocation(), ship.getCollisionRadius()*2f);
		projList.addAll(CombatUtils.getMissilesWithinRange(ship.getLocation(), ship.getCollisionRadius()*2f));
		for (DamagingProjectileAPI proj : CombatUtils.getProjectilesWithinRange(ship.getLocation(), ship.getCollisionRadius()*2f)) {
			//Don't trigger on projectile's that aren't our own
			if (proj.getSource() != ship) {
				continue;
			}

			//Only trigger on NEW projectiles
			if (proj.getElapsed() > 0.1f) {
				continue;
			}

			//Don't trigger on projectile's we've spawned or already split from
			if (registeredProjectiles.contains(proj)) {
				continue;
			}

			//Otherwise, we prepare to spawn projectiles and register both the new and old one so nothing triggers twice on the same projectile
			if (proj.getWeapon() != null && proj.getWeapon().getSpec().getWeaponId() != null) {
                registeredProjectiles.add(proj);

			    //Run and check once for each potential extra projectile spawn
                for (int i = 0; i < PROJECTILE_MAX_SPAWNS; i++) {
                    if (Math.random() < PROJECTILE_SPAWN_CHANCE*effectLevel) {
                        //Spawns the projectile, with some offsets for angle and position
                        DamagingProjectileAPI newProj = (DamagingProjectileAPI)Global.getCombatEngine().spawnProjectile(ship, proj.getWeapon(),
                                proj.getWeapon().getSpec().getWeaponId(), proj.getProjectileSpecId(),MathUtils.getPoint(new Vector2f(proj.getLocation()),
                                PROJECTILE_OFFSET_DISTANCE, MathUtils.getRandomNumberInRange(0f, 360f)),
                                proj.getFacing() + MathUtils.getRandomNumberInRange(-PROJECTILE_OFFSET_ANGLE, PROJECTILE_OFFSET_ANGLE), ship.getVelocity());

                        //Assigns the same target to the AI if possible (we can only do this for vanilla guided missiles, but hey)
                        if (proj instanceof MissileAPI) {
                            if (((MissileAPI)proj).getMissileAI() != null) {
                                if (((MissileAPI)proj).getMissileAI() instanceof GuidedMissileAI) {
                                    ((GuidedMissileAI) ((MissileAPI) newProj).getMissileAI()).setTarget(((GuidedMissileAI) ((MissileAPI) proj).getMissileAI()).getTarget());
                                }
                            }
                        }

                        //Registers the projectiles so we don't trigger twice on them
                        registeredProjectiles.add(newProj);
                    }
                }
			}
		}
	}
	
	public void unapply(MutableShipStatsAPI stats, String id) {
        ShipAPI ship = null;
        if (stats.getEntity() instanceof ShipAPI) {
            ship = (ShipAPI) stats.getEntity();
            id = id + "_" + ship.getId();
        } else {
            return;
        }
	}
	
	public StatusData getStatusData(int index, State state, float effectLevel) {
		if (index == 0) {
			return new StatusData("Timeline instability established", false);
		}
		return null;
	}
}