//By Nicke535, causes a projectile to detonate into a time-distortion effect when fading, hitting a target or otherwise disappear
package data.scripts.utils;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.Misc;
import data.scripts.VassModPlugin;
import data.scripts.plugins.MagicTrailPlugin;
import org.dark.shaders.distortion.DistortionShader;
import org.dark.shaders.distortion.WaveDistortion;
import org.jetbrains.annotations.Nullable;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.lwjgl.opengl.GL11.GL_ONE;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;

public class VassTimeDistortionProjScript extends BaseEveryFrameCombatPlugin {
	//How long do we apply time distortion effects for?
	private static final float TIME_DISTORTION_DURATION = 0.55f;

	//How long the visual distortion lasts (with shaderlib)
	private static final float VISUAL_DISTORTION_DURATION = 0.4f;

	//Base AoE size, this is then modified by the square root of the damage
	private static final float BASE_AOE_SIZE = 6f;

	//The ID of the effect applied by the script (also gets an unique modifier applied)
	private static final String EFFECT_ID = "VassTimeDistortKey_";
	private String effectIdSuffix;


	//The projectile we are tracking
	private DamagingProjectileAPI proj;

	//The most extreme time distortion we can apply
	private float maxTimeDistortMult;

	//The soundclip to play on detonation, if any. Can be Null, meaning no sound
	private String soundClipOnDetonation;

	//Whether the projectile has already "detonated" or not
	private boolean hasProjectileDetonated = false;

	//How far after the detonation we are, time-wise
	private float counter = 0f;

	//Last frame's list of affected fighters
	private List<ShipAPI> fightersAffectedLastFrame = new ArrayList<>();

	//The "affect location" for the explosion. Corresponds to where the projectile detonated
	private Vector2f effectLocation = new Vector2f(0f, 0f);

	//Multiplier for our AoE
	private float aoeMult = 1f;

	//Initializers
	public VassTimeDistortionProjScript(DamagingProjectileAPI proj, float maxTimeDistortMult, @Nullable String soundClipOnDetonation) {
		this.proj = proj;
		this.maxTimeDistortMult = maxTimeDistortMult;
		effectIdSuffix = UUID.randomUUID().toString();
		this.soundClipOnDetonation = soundClipOnDetonation;
	}

	//Initializers
	public VassTimeDistortionProjScript(DamagingProjectileAPI proj, float maxTimeDistortMult, @Nullable String soundClipOnDetonation, float aoeMult) {
		this(proj, maxTimeDistortMult, soundClipOnDetonation);
		this.aoeMult = aoeMult;
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
		if (proj == null || counter >= TIME_DISTORTION_DURATION) {
			for (ShipAPI fighter : fightersAffectedLastFrame) {
				fighter.getMutableStats().getTimeMult().unmodify(EFFECT_ID+effectIdSuffix);
			}
			fightersAffectedLastFrame.clear();
			Global.getCombatEngine().removePlugin(this);
			return;
		}

		//Check if the projectile should detonate, unless it already has
		if (!hasProjectileDetonated) {
			//If the projectile has hit something, it should detonate
			if (proj.didDamage()) {
				detonate();
				return;
			}

			//If the projectile has <=0 health, it should also detonate (if it's a missile)
			if (proj instanceof MissileAPI && proj.getHitpoints() <= 0f) {
				detonate();
				return;
			}

			//Finally, we also detonate if our projectile has vanished somehow
			if (!Global.getCombatEngine().isEntityInPlay(proj)) {
				detonate();
			}
		}

		//If it has already detonated, we start counting our counter and applies time distortion effects to all nearby targets
		else {
			counter += amount;

			//Before we apply any effects, reset last frame's effects
			for (ShipAPI fighter : fightersAffectedLastFrame) {
				fighter.getMutableStats().getTimeMult().unmodify(EFFECT_ID+effectIdSuffix);
			}
			fightersAffectedLastFrame.clear();

			//Then, we apply our time mult to all fighters in range
			float damageMult = (float)Math.sqrt(proj.getDamageAmount());
			float timeMultThisFrame = 1f + (maxTimeDistortMult*(1f-(counter/TIME_DISTORTION_DURATION)));
			for (ShipAPI fighter : CombatUtils.getShipsWithinRange(effectLocation, BASE_AOE_SIZE*aoeMult*damageMult*counter/TIME_DISTORTION_DURATION)) {
				//Only affects fighters
				if (fighter.getHullSize() != ShipAPI.HullSize.FIGHTER) {
					continue;
				}

				fighter.getMutableStats().getTimeMult().modifyMult(EFFECT_ID+effectIdSuffix, timeMultThisFrame);
				fightersAffectedLastFrame.add(fighter);
			}

			//Addition: also accelerates missiles
			for (MissileAPI msl : CombatUtils.getMissilesWithinRange(effectLocation, BASE_AOE_SIZE*aoeMult*damageMult*counter/TIME_DISTORTION_DURATION)) {
				msl.getLocation().x += msl.getVelocity().x * ((timeMultThisFrame - 1f) * amount);
				msl.getLocation().x += msl.getVelocity().x * ((timeMultThisFrame - 1f) * amount);
				msl.setFlightTime(msl.getFlightTime() + ((timeMultThisFrame - 1f) * amount));
			}
		}
	}

	//Function for detonating the projectile
	private void detonate() {
		//Indicate our hit location
		effectLocation = new Vector2f(proj.getLocation());

		//Intensity and size of effects vary by damage of projectile
		float damageMult = (float)Math.sqrt(proj.getDamageAmount());

		//Spawn a particle
		Global.getCombatEngine().addHitParticle(new Vector2f(effectLocation), Misc.ZERO, BASE_AOE_SIZE*aoeMult*damageMult*1.5f,
				1f, VISUAL_DISTORTION_DURATION, new Color(50,255,50));

		//Play our detonation sound (if we have one)
		if (soundClipOnDetonation != null) {
			Global.getSoundPlayer().playSound(soundClipOnDetonation, 1f, 1f, new Vector2f(effectLocation), Misc.ZERO);
		}

		//Spawns a few fancy trails on high-damage blasts for good measure
		SpriteAPI spriteToUse = Global.getSettings().getSprite("vass_fx","projectile_trail_zappy");
		for (float i1 = 1; i1 < 0.65f*damageMult; i1 += MathUtils.getRandomNumberInRange(0.7f, 1.2f)) {
			float id = MagicTrailPlugin.getUniqueID();
			float angle = MathUtils.getRandomNumberInRange(0f, 360f);
			float startSpeed = MathUtils.getRandomNumberInRange(5f, 50f) * damageMult;
			float startAngularVelocity = MathUtils.getRandomNumberInRange(-6f, 6f) * damageMult;
			float startSize = MathUtils.getRandomNumberInRange(1.4f, 3.7f) * damageMult;
			float lifetime = MathUtils.getRandomNumberInRange(0.8f, 1.4f) * VISUAL_DISTORTION_DURATION;
			for (int i2 = 0; i2 < 12; i2++) {
				MagicTrailPlugin.AddTrailMemberAdvanced(null, id, spriteToUse, new Vector2f(effectLocation), startSpeed * ((float)i2 / 12f), 0f,
						angle, startAngularVelocity * ((float)i2 / 12f), 0f, startSize, 0f,
						VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.PERTURBA, 1f), VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.PERTURBA, 1f),
						0.45f, 0f, 0.3f * ((float)i2 / 12f) * lifetime, 0.7f * ((float)i2 / 12f) * lifetime, GL_SRC_ALPHA, GL_ONE,
						500f, 0f, new Vector2f(0f, 0f), null);
			}
		}

		//If we use GraphicsLib, spawn a distortion
		if (VassModPlugin.hasShaderLib) {
			WaveDistortion wave = new WaveDistortion(new Vector2f(effectLocation), Misc.ZERO);
			wave.setIntensity(3f*damageMult);

			//We want it to be a "dip" rather than a bulge
			wave.flip(true);

			//Ensure the effect fades out properly
			wave.setLifetime(VISUAL_DISTORTION_DURATION);
			wave.fadeOutIntensity(VISUAL_DISTORTION_DURATION);

			//We want the size to work a bit wierdly, namely to fade in, but start at 25%. So we add that
			wave.setSize(BASE_AOE_SIZE*aoeMult*damageMult*0.75f);
			wave.fadeInSize(VISUAL_DISTORTION_DURATION);
			wave.setSize(BASE_AOE_SIZE*aoeMult*damageMult*0.25f);

			//And finally ensure the distortion is tracked
			DistortionShader.addDistortion(wave);
		}

		//Addition: Then, apply EMP damage to fighters nearby, and randomly flame-out low-health missiles depending on EMP
		//This doesn't effect allies, solely due to balance concerns
		for (ShipAPI fighter : CombatUtils.getShipsWithinRange(effectLocation, BASE_AOE_SIZE*aoeMult*damageMult)) {
			//Only affects fighters
			if (fighter.getHullSize() != ShipAPI.HullSize.FIGHTER) {
				continue;
			}

			//Doesn't affect allies
			if (fighter.getOwner() == proj.getOwner()) {
				continue;
			}

			//Simply hits the fighter dead-center with EMP, albeit with very random damage
			Global.getCombatEngine().applyDamage(fighter, fighter.getLocation(), 0f, DamageType.ENERGY,
					(float)Math.random() * proj.getEmpAmount() * (BASE_AOE_SIZE*aoeMult*damageMult - MathUtils.getDistance(effectLocation, fighter.getLocation())) /(BASE_AOE_SIZE*aoeMult*damageMult),
					true, true, proj.getSource(), false);
		}
		for (MissileAPI msl : CombatUtils.getMissilesWithinRange(effectLocation, BASE_AOE_SIZE*aoeMult*damageMult)) {
			if (msl.getOwner() != proj.getOwner()) {
				if (msl.getHitpoints() < Math.random() * proj.getEmpAmount() * (BASE_AOE_SIZE*aoeMult*damageMult - MathUtils.getDistance(effectLocation, msl.getLocation())) /(BASE_AOE_SIZE*aoeMult*damageMult)) {
					if (msl.getEmpResistance() > 0) {
						msl.decrEMPResistance();
					} else {
						msl.flameOut();
					}
				}
			}
		}

		//Finally, register that we've detonated
		hasProjectileDetonated = true;
	}
}