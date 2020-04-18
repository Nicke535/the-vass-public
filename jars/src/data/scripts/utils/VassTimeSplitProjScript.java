package data.scripts.utils;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import data.scripts.VassModPlugin;
import data.scripts.plugins.MagicTrailPlugin;
import org.dark.shaders.distortion.DistortionShader;
import org.dark.shaders.distortion.WaveDistortion;
import org.dark.shaders.light.LightAPI;
import org.dark.shaders.light.LightData;
import org.dark.shaders.light.LightShader;
import org.dark.shaders.light.StandardLight;
import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.FastTrig;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.*;
import java.util.List;

import static org.lwjgl.opengl.GL11.GL_ONE;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;

/**
 * Tracks that a projectile should "grace" nearby timelines to hit targets it barely misses
 * @author Nicke535
 */
public class VassTimeSplitProjScript extends BaseEveryFrameCombatPlugin {
	//How much the projectile's damage is reduced for each grace, at most
	private static final float DAMAGE_LOSS_PER_GRACE = 0.05f;

	//The projectile we are tracking
	private DamagingProjectileAPI proj;

	//Maximum range for grace effect: set when instantiating the script
	private float graceDistanceMax;

	//Damage multiplier for the grace effect: set when instantiating the script
	private float globalDamageMult;

	//Our current "damage adjustment" for the main projectile; this goes down as projectile damage
	//is decreased by timeline gracing
	private float damageAdjustment = 1f;

	//The distance each valid target had to us last frame is stored here
	private Map<CombatEntityAPI, Float> lastFrameDistances = new HashMap<>();

	//All targets we've already "graced" are stored so we don't hit things twice
	private List<CombatEntityAPI> alreadyHitTargets = new ArrayList<>();

	//Stores a timer so we don't spawn too many visual effects on the projectile
	private IntervalUtil timer = new IntervalUtil(0.03f, 0.1f);

	
	//Initializer
	public VassTimeSplitProjScript(DamagingProjectileAPI proj, float graceDistanceMax, float globalDamageMult) {
		this.proj = proj;
		this.graceDistanceMax = graceDistanceMax;
		this.globalDamageMult = globalDamageMult;
	}

	@Override
	public void advance(float amount, List<InputEventAPI> events) {
		//Sanity check
		if (Global.getCombatEngine() == null) {
			return;
		}
		if (Global.getCombatEngine().isPaused()) {
			amount = 0f;
		}

		//Checks if our script should be removed from the combat engine
		if (proj == null || proj.didDamage() || proj.isFading() || !Global.getCombatEngine().isEntityInPlay(proj)) {
			lastFrameDistances.clear();
			alreadyHitTargets.clear();
			Global.getCombatEngine().removePlugin(this);
			return;
		}

		//If we don't remove the script, we spawn visual effects on the projectile
		else {
			timer.advance(amount);

			if (timer.intervalElapsed()) {
				//Calculates some vector math to get the correct velocities
				float mainVelLength = Vector2f.dot(proj.getVelocity(), MathUtils.getPoint(Misc.ZERO, 1f, proj.getFacing()));
				float offsetVelLength = Vector2f.dot(proj.getVelocity(), MathUtils.getPoint(Misc.ZERO, 1f, proj.getFacing()+90f));
				Vector2f offsetVel = MathUtils.getPoint(Misc.ZERO, offsetVelLength, proj.getFacing()+90f);

				//Generates a fancy trail effect, similar to the on-hit effect
				SpriteAPI spriteToUse = Global.getSettings().getSprite("vass_fx","projectile_trail_zappy");
				float id = MagicTrailPlugin.getUniqueID();
				float startSpeed = MathUtils.getRandomNumberInRange(0.95f, 0.85f) * mainVelLength;
				float startSize = MathUtils.getRandomNumberInRange(45f,55f) * damageAdjustment;
				Vector2f originPoint = MathUtils.getRandomPointInCircle(proj.getLocation(), 6f * damageAdjustment);
				Color color = VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.MULTA, 1f);
				for (int i = 0; i < 8; i++) {
					Vector2f spawnPoint = MathUtils.getPoint(originPoint, (i/8f - 0.5f) * 65f * damageAdjustment, proj.getFacing());
					MagicTrailPlugin.AddTrailMemberAdvanced(null, id, spriteToUse, spawnPoint, startSpeed * (((float)i * 0.3f / 8f) + 0.7f), 0f,
							proj.getFacing(), 0f, 0f, startSize * (((float)i * 0.4f / 8f) + 0.6f), startSize*0.3f * (((float)i * 0.4f / 8f) + 0.6f),
							color, color, 0.85f, 0f, 0.25f, 0.3f, GL_SRC_ALPHA, GL_ONE,
							500f, 0f, offsetVel, null);
				}

				//New: GraphicsLib support for point lights on the effects
				if (VassModPlugin.hasShaderLib) {
					StandardLight light = new StandardLight(originPoint, (Vector2f)new Vector2f(proj.getVelocity()).scale(0.5f),
							new Vector2f(0f, 0f),null, damageAdjustment, damageAdjustment*70f);
					light.setColor(color);
					light.setLifetime(0.25f);
					light.setAutoFadeOutTime(0.3f);
					LightShader.addLight(light);
				}
			}
		}


		//Handle all targets already in our tracker
		List<CombatEntityAPI> toRemove = new ArrayList<>();
		for (CombatEntityAPI target : lastFrameDistances.keySet()) {
			//If the target has moved away from us (or started phasing), damage it and store the fact that it's no longer a valid target
			if (lastFrameDistances.get(target) < getRangeToTarget(target) || (target instanceof ShipAPI && ((ShipAPI)target).isPhased())) {
				damageTarget(target, lastFrameDistances.get(target));
				alreadyHitTargets.add(target);
				toRemove.add(target);
			}

			//If they've lost their collision class, we don't damage them, as that goes against convention.
			//Just remove them altogether from the tracker, safest that way
			else if (target.getCollisionClass() == CollisionClass.NONE) {
				toRemove.add(target);
			}

			//Otherwise, we simply update the range to the target
			else {
				lastFrameDistances.put(target, getRangeToTarget(target));
			}
		}
		for (CombatEntityAPI removed : toRemove) {
			lastFrameDistances.remove(removed);
		}

		//Go through all targets near us, ignoring the ones we're already tracking, and add them to our tracker
		//	Also, ignore any module target under a shield
		for (CombatEntityAPI potTarget : CombatUtils.getEntitiesWithinRange(proj.getLocation(), graceDistanceMax*1.2f)) {
			//Ignore anything allied
			if (potTarget.getOwner() == proj.getOwner()) {
				continue;
			}

			//Ignore projectiles that aren't missiles
			if (potTarget instanceof DamagingProjectileAPI && !(potTarget instanceof MissileAPI)) {
				continue;
			}

			//Ignore anything with collision class NONE
			if (potTarget.getCollisionClass().equals(CollisionClass.NONE)) {
				continue;
			}

			//If we've already registered the target, ignore it
			if (alreadyHitTargets.contains(potTarget) || lastFrameDistances.containsKey(potTarget)) {
				continue;
			}

			//If the target is a ship, ignore it if it's phased
			if ((potTarget instanceof ShipAPI && ((ShipAPI)potTarget).isPhased())) {
				continue;
			}

			//If the target is under another ship's shield, ignore it
			if (VassUtils.isTargetUnderOtherShipShield(potTarget, proj.getLocation())) {
				continue;
			}

			//FINALLY, if the target is too far away (with our "better" range calculations) we don't hit them either.
			//Otherwise, register them with the range calculation's result
			float targetRange = getRangeToTarget(potTarget);
			if (targetRange <= graceDistanceMax) {
				lastFrameDistances.put(potTarget, targetRange);
			}
		}
	}


	//Gets the range to a target: this is done differently for non-fighter ships than other targets
	private float getRangeToTarget (CombatEntityAPI target) {
		Vector2f projLoc = new Vector2f(proj.getLocation());

		//Ships require more sophisticated calculations
		if (target instanceof ShipAPI && !(((ShipAPI) target).getHullSize().equals(ShipAPI.HullSize.FIGHTER))) {
			//Originally by MesoTroniK, bypass minor glitches in vanilla
			ShipAPI shipTarget = (ShipAPI) target;
			ShieldAPI shield = shipTarget.getShield();
			if (shield != null && shield.isOn() && shield.isWithinArc(projLoc)) {
				return MathUtils.getDistance(projLoc, shield.getLocation()) - shield.getRadius();
			}
			return Math.max(0f, MathUtils.getDistance(projLoc, target.getLocation()) - Misc.getTargetingRadius(projLoc, target, false));
		}
		//Non-ship and fighter targets are easy: we just take their collision radius and remove it from the center-point distances
		else {
			return (Math.max(0f, MathUtils.getDistance(target.getLocation(), projLoc)-target.getCollisionRadius()));
		}
	}


	//Deals damage to a target and spawns a visual effect
	private void damageTarget (CombatEntityAPI target, float distanceForDamage) {
		//Is the target a non-fighter ship? In that case, we need to use a slightly more complicated formula to determine our damage point
		Vector2f collisionPoint = new Vector2f(target.getLocation().x, target.getLocation().y);
		if (target instanceof ShipAPI && !(((ShipAPI) target).getHullSize().equals(ShipAPI.HullSize.FIGHTER))) {
			Vector2f newCollisionPoint = null;

			//First; did we hit shields? If so, only check against enemy shield radius instead of their "proper" collision bounds
			if (target.getShield() != null && target.getShield().isOn() && target.getShield().isWithinArc(proj.getLocation())) {
				newCollisionPoint = MathUtils.getPointOnCircumference(target.getShield().getLocation(), target.getShield().getRadius(),
						VectorUtils.getAngle(target.getShield().getLocation(), proj.getLocation()));
			}
			//Otherwise, we run some quick math to ensure a collision point (draw a line through the entirety of the enemy ship)
			//  ...unless it *somehow* is split down the middle where we checked... in which case we're all out of luck and will just choose the center
			//  as our target location, consequences be damned: it's too expensive to run even better checks in this extremely rare special case
			else {
				Vector2f checkVector = VectorUtils.getDirectionalVector(proj.getLocation(), target.getLocation());
				checkVector.scale(target.getCollisionRadius());
				checkVector = Vector2f.add(checkVector, target.getLocation(), new Vector2f(0f, 0f));
				newCollisionPoint = CollisionUtils.getCollisionPoint(proj.getLocation(), checkVector, target);
			}

			//If we succesfully achieved a new collisionPoint, we use that one instead of the original one
			if (newCollisionPoint != null) {
				collisionPoint = newCollisionPoint;
			}
		}

		//Now that we have a collision point, we deal damage there (with quadratically less damage based on range)...
		float damageMult = 1f - (distanceForDamage/graceDistanceMax);
		damageMult *= damageMult;
		Global.getCombatEngine().applyDamage(target, collisionPoint, proj.getDamageAmount() * damageMult * globalDamageMult,
				proj.getDamageType(), proj.getEmpAmount() * damageMult * globalDamageMult, false, false,
				proj.getSource(), true);

		//...and spawn some VFX; in this case, a trail, because i like trails
		SpriteAPI spriteToUse = Global.getSettings().getSprite("vass_fx","projectile_trail_zappy");
		float id = MagicTrailPlugin.getUniqueID();
		float startSpeed = MathUtils.getRandomNumberInRange(0.7f, 0.5f) * proj.getVelocity().length();
		float angle = proj.getFacing() + MathUtils.getRandomNumberInRange(-1f - 9f * (distanceForDamage/graceDistanceMax), 1f + 9f * (distanceForDamage/graceDistanceMax));
		float startSize = 45f * (float)Math.sqrt(damageMult) * damageAdjustment;
		Color color = VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.MULTA, 1f);
		for (int i = 0; i < 8; i++) {
			Vector2f spawnPoint = MathUtils.getPoint(collisionPoint, (i/8f - 0.5f) * 65f * damageMult * damageAdjustment, proj.getFacing());
			MagicTrailPlugin.AddTrailMemberAdvanced(null, id, spriteToUse, spawnPoint, startSpeed * (((float)i * 0.8f / 8f) + 0.2f), 0f,
					angle, 0f, 0f, startSize * (((float)i * 0.4f / 8f) + 0.6f), startSize * 0.3f * (((float)i * 0.4f / 8f) + 0.6f),
					color, color, 0.85f, 0f, 0.35f, 0.4f, GL_SRC_ALPHA, GL_ONE,
					500f, 0f, new Vector2f(0f, 0f), null);
		}

		//New: GraphicsLib support for point lights on the effects
		if (VassModPlugin.hasShaderLib) {
			StandardLight light = new StandardLight(collisionPoint, (Vector2f)new Vector2f(proj.getVelocity()).scale(0.5f),
					new Vector2f(0f, 0f),null, damageAdjustment*2f, damageAdjustment*70f);
			light.setColor(color);
			light.setLifetime(0.25f);
			light.setAutoFadeOutTime(0.6f);
			LightShader.addLight(light);
		}

		//Oh, and also reduce the damage of the main projectile once we've graced
		proj.setDamageAmount(proj.getDamageAmount() * (1f - (DAMAGE_LOSS_PER_GRACE*damageMult)));
	}
}