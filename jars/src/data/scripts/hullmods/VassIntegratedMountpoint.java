package data.scripts.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.combat.listeners.DamageDealtModifier;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.scripts.utils.VassTimeDistortionProjScript;
import data.scripts.utils.VassUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.lazywizard.lazylib.FastTrig;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Gives specific bonuses depending on which weapon is mounted in the main medium slot
 * ...this is going to be one HELL of a headache, isn't it?
 * @author Nicke535
 */
public class VassIntegratedMountpoint extends BaseHullMod {
	public static final Logger LOGGER = Global.getLogger(VassIntegratedMountpoint.class);

	public static enum BonusID {
		// Represents no bonus being available, for whatever reason (no weapon in mount?)
		NONE,

		// Generics
		GENERIC_BALLISTIC,
		GENERIC_ENERGY,
		GENERIC_BEAM,
		GENERIC_MISSILE,
		GENERIC_UNIVERSAL,
		GENERIC_HYBRID,
		GENERIC_SYNERGY,
		GENERIC_COMPOSITE,

		// Unique - Vanilla
		GRAVITON_BEAM,
		TYPHOON_REAPER,

		// Unique - Vass
		FRAGARACH,
		CALADBOLG,
		CYLLEL_FARCHOG,

		// Unique - Modded
	}

	//ID for most effects the hullmod applies
	private static final String ID = "VassIntegratedMountpointID";

	//The slot ID that we consider our "integrated" slot
	private static final String SPECIAL_SLOT_ID = "WS0002";

	// Some necessary internal script variables
	private Set<DamagingProjectileAPI> alreadyTriggeredProjectiles = new HashSet<>(); // For tracking which projectiles we've already applied our effect on

	@Override
	public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
		// Gets which BonusID we have, depending on which weapon is mounted
		BonusID bonusID = getBonusID(ship);
		WeaponAPI weapon = getWeaponInSpecialSlot(ship);

		// Add a damage listener to ourselves, if we need one
		if (bonusID == BonusID.GENERIC_BEAM ||
				bonusID == BonusID.GRAVITON_BEAM ||
				bonusID == BonusID.GENERIC_BALLISTIC) {
			ship.addListener(new IntegratedMountpointDamageListener(bonusID, weapon, ship));
		}
	}

	//Changes the ship's time mult at every "advanceInCombat", in order to make sure the global time mult is correct in relation to the player ship
	@Override
	public void advanceInCombat(ShipAPI ship, float amount) {
		CombatEngineAPI engine = Global.getCombatEngine();
		if (engine == null) return;

		// Gets which BonusID we have, depending on which weapon is mounted
		BonusID bonusID = getBonusID(ship);

		// Ensure we apply all bonuses we have to our projectiles
		applyBonusesOnProjectiles(ship, amount, bonusID);
	}

	private void applyBonusesOnProjectiles(ShipAPI ship, float amount, BonusID bonusID) {
		WeaponAPI weapon = getWeaponInSpecialSlot(ship);
		if (weapon == null) return;
		CombatEngineAPI engine = Global.getCombatEngine();

		// Don't bother running this stuff if our bonus ID doesn't need it
		if (bonusID != BonusID.TYPHOON_REAPER) {
			return;
		}

		// Find all projectiles we've fired, and run the correct function on it
		// Only runs once per projectile, and only on projectiles our weapon owns
		for (CombatEntityAPI entity : CombatUtils.getEntitiesWithinRange(weapon.getLocation(), 400f)) {
			if (!(entity instanceof DamagingProjectileAPI)) {
				continue;
			}
			DamagingProjectileAPI proj = (DamagingProjectileAPI)entity;

			//Only run once per projectile
			if (alreadyTriggeredProjectiles.contains(proj)) {
				continue;
			}

			//If the projectile is our own, we can do something with it
			if (proj.getWeapon() == weapon) {
				//Register that we've triggered on the projectile
				alreadyTriggeredProjectiles.add(proj);

				//And add our plugin to the engine to track the effects
				applyBonusOnOneProjectile(ship, weapon, proj, amount, bonusID);
			}
		}

		//Also, we clean up our already triggered projectiles when they stop being loaded into the engine
		List<DamagingProjectileAPI> toRemove = new ArrayList<>();
		for (DamagingProjectileAPI proj : alreadyTriggeredProjectiles) {
			if (!engine.isEntityInPlay(proj)) {
				toRemove.add(proj);
			}
		}
		alreadyTriggeredProjectiles.removeAll(toRemove);
		toRemove.clear();
	}
	private void applyBonusOnOneProjectile(ShipAPI ship, WeaponAPI weapon, DamagingProjectileAPI proj, float amount, BonusID bonusID) {
		// Typhoon reaper launcher: add our Chrono Warhead script to the projectile
		if (bonusID == BonusID.TYPHOON_REAPER) {
			List<VassTimeDistortionProjScript.DetonationListener> listeners = new ArrayList<>();
			listeners.add(new ChronoWarheadScript(proj));
			Global.getCombatEngine().addPlugin(new VassTimeDistortionProjScript(proj, MathUtils.getRandomNumberInRange(0.2f, 1.4f), "vass_excalibur_detonation", 0.8f, listeners));
		}
	}

	//Prevents the hullmod from being put on ships
	@Override
	public boolean isApplicableToShip(ShipAPI ship) {
		return false;
	}

	//A whole bunch of descriptions, most unused for now
	public String getDescriptionParam(int index, HullSize hullSize) {
		if (index == 0) {
			return "medium";
		} else if (index == 1) {
			return "passive benefits";
		} else if (index == 2) {
			return "shipsystem";
		} else {
			return null;
		}
	}

	//For the cool extra description detailing what your mounted weapon is getting from the hullmod
	@Override
	public void addPostDescriptionSection(TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
		if (!isForModSpec) {
			addPostDescriptionMountBonus(tooltip, hullSize, ship, width);
		}
	}

	//For bonuses gotten from being a member of a Vass family; not really used now except for debugging
	private void addPostDescriptionMountBonus (TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship, float width) {
		//This does nothing if we don't have a mounted weapon  TODO Decide if there should be a bonus for not mounting a weapon
		BonusID bonusID = getBonusID(ship);
		if (bonusID == BonusID.NONE) {
			return;
		}

		float pad = 10f;
		tooltip.addSectionHeading("Integrated Weapon Bonus", Alignment.MID, pad);

		//Typhoon Reaper Launcher
		if (bonusID.equals(BonusID.TYPHOON_REAPER)) {
			TooltipMakerAPI text = tooltip.beginImageWithText("graphics/hullmods/phase_anchor.png", 36);
			text.addPara("Passive - Chrono-warheads", 0, Misc.getHighlightColor(), "Passive");
			text.addPara("Loads all the missiles fired by this weapon with Chrono-disrupting warheads, giving them a disruptive effect against weapons, engines, fighters and missiles near the blast zone.", 2, Misc.getHighlightColor(),"Chrono-disrupting warheads");
			tooltip.addImageWithText(pad);
			TooltipMakerAPI text2 = tooltip.beginImageWithText("graphics/hullmods/expanded_missile_racks.png", 36);
			text2.addPara("Active - Auxilary Loading Mechanism", 0, Misc.getHighlightColor(), "Active");
			text2.addPara("Weapon gains +600%% extra firerate for the next 2 shots.", 2, Misc.getHighlightColor(),"600%", "2");
			tooltip.addImageWithText(pad);
		}

		//Graviton Beam
		if (bonusID.equals(BonusID.GRAVITON_BEAM)) {
			TooltipMakerAPI text = tooltip.beginImageWithText("graphics/hullmods/quantum_disruptor.png", 36);
			text.addPara("Passive - Graviton Resonator", 0, Misc.getHighlightColor(), "Passive");
			text.addPara("Increases the beam's shield damage by up to 100%% depending on target flux level.", 2, Misc.getHighlightColor(),"100%");
			tooltip.addImageWithText(pad);
			TooltipMakerAPI text2 = tooltip.beginImageWithText("graphics/hullmods/integrated_point_defense_ai.png", 36);
			text2.addPara("Active - Defensive Autotarget Suite", 0, Misc.getHighlightColor(), "Active");
			text2.addPara("Weapon gains 400%% turning rate, 100%% bonus damage against missiles/fighters and counts as PD for 5 seconds.", 2, Misc.getHighlightColor(),"400%", "100%", "PD", "5");
			tooltip.addImageWithText(pad);
		}

		//Generic: ballistic weapon
		if (bonusID.equals(BonusID.GENERIC_BALLISTIC)) {
			TooltipMakerAPI text = tooltip.beginImageWithText("graphics/hullmods/integrated_targeting_unit.png", 36);
			text.addPara("Passive - Weakpoint Scrying Suite", 0, Misc.getHighlightColor(), "Passive");
			text.addPara("+10/20/30%% damage against destroyers/cruisers/capital ships respectively.", 2, Misc.getHighlightColor(),"10", "20", "30%", "destroyers", "cruisers", "capital ships");
			tooltip.addImageWithText(pad);
			TooltipMakerAPI text2 = tooltip.beginImageWithText("graphics/hullmods/expanded_magazines2.png", 36);
			text2.addPara("Active - Rapid Feed Mechanism", 0, Misc.getHighlightColor(), "Active");
			text2.addPara("Weapon gains +100%% extra firerate for 5 seconds.", 2, Misc.getHighlightColor(),"100%", "5");
			tooltip.addImageWithText(pad);
		}
	}

	// Gets which bonus ID we currently fulfill. Unless we have a unique effect for a given weapon ID, it falls back based on base weapon characteristics
	private BonusID getBonusID(ShipAPI ship) {
		WeaponAPI weapon = getWeaponInSpecialSlot(ship);

		if (weapon == null) {
			return BonusID.NONE;
		}

		// Unique vanilla weapons
		if (weapon.getSpec().getWeaponId().equals("typhoon")) {
			return BonusID.TYPHOON_REAPER;
		}
		if (weapon.getSpec().getWeaponId().equals("gravitonbeam")) {
			return BonusID.GRAVITON_BEAM;
		}

		// Fallbacks, as no unique effect was found
		if (weapon.getType().equals(WeaponAPI.WeaponType.BALLISTIC)) {
			return BonusID.GENERIC_BALLISTIC;
		}
		if (weapon.getType().equals(WeaponAPI.WeaponType.ENERGY)) {
			if (weapon.isBeam()) {
				return BonusID.GENERIC_BEAM;
			} else {
				return BonusID.GENERIC_ENERGY;
			}
		}
		if (weapon.getType().equals(WeaponAPI.WeaponType.MISSILE)) {
			return BonusID.GENERIC_MISSILE;
		}

		// We should never reach this point, so throw an error
		LOGGER.fatal("[VassIntegratedMountpoint] Weapon "+weapon.toString()+" failed to find a valid BonusID");
		throw new IllegalStateException("Please contact Nicke535 with details of the crash: Error in VassIntegratedMountpoint.java");
	}

	private WeaponAPI getWeaponInSpecialSlot(ShipAPI ship) {
		for (WeaponAPI wep : ship.getAllWeapons()) {
			if (wep.getSlot() != null) {
				if (wep.getSlot().getId().equals(SPECIAL_SLOT_ID)) {
					return wep;
				}
			}
		}
		return null;
	}

	// Listener for damage-dealing, to make certain effects possible with the hullmod
	public static class IntegratedMountpointDamageListener implements DamageDealtModifier, AdvanceableListener {
		private BonusID bonusID;
		private WeaponAPI specialWeapon;
		private ShipAPI ship;

		public IntegratedMountpointDamageListener(BonusID bonusID, WeaponAPI weapon, ShipAPI ship) {
			this.bonusID = bonusID;
			this.specialWeapon = weapon;
			this.ship = ship;
		}

		public void advance(float amount) {

		}

		public String modifyDamageDealt(Object param,
										CombatEntityAPI target, DamageAPI damage,
										Vector2f point, boolean shieldHit) {
			Vector2f from = null;
			WeaponAPI weapon = null;
			if (param instanceof DamagingProjectileAPI) {
				from = ((DamagingProjectileAPI)param).getSpawnLocation();
				weapon = ((DamagingProjectileAPI)param).getWeapon();
			} else if (param instanceof BeamAPI) {
				from = ((BeamAPI)param).getFrom();
				weapon = ((BeamAPI)param).getWeapon();
			} else {
				return null;
			}

			if (weapon == null || weapon != specialWeapon || ship == null) return null;

			if (bonusID == BonusID.GRAVITON_BEAM) {
				// Graviton beam: if we hit a shield, we deal bonus damage depending on the target's current flux
				if (shieldHit && (target instanceof ShipAPI)) {
					float fluxLevel = ((ShipAPI)target).getFluxLevel();
					damage.getModifier().modifyMult(ID, fluxLevel + 1f);
				}
			}

			if (bonusID == BonusID.GENERIC_BALLISTIC) {
				// Generic ballistic effect: if we hit a ship, get bonus damage based on its hullsize
				if (target instanceof ShipAPI) {
					HullSize size = ((ShipAPI)target).getHullSize();
					float bonus = 0f;
					if (size == HullSize.DESTROYER) {
						bonus = 0.1f;
					} else if (size == HullSize.CRUISER) {
						bonus = 0.2f;
					} else if (size == HullSize.CAPITAL_SHIP) {
						bonus = 0.3f;
					}
					if (bonus > 0f) {
						damage.getModifier().modifyMult(ID, bonus + 1f);
					}
				}
			}


			return ID;
		}
	}

	//Listener for the detonation event of the standard Time Distortion Projectile Script, but adapted for the Chrono Warhead effect
	private class ChronoWarheadScript implements VassTimeDistortionProjScript.DetonationListener {
		private DamagingProjectileAPI sourceProj;
		private ChronoWarheadScript(DamagingProjectileAPI proj) {
			sourceProj = proj;
		}

		@Override
		public void detonate(Vector2f loc, float damageMult, float basicAoESize) {
			// Find us all ships near the detonation (note that this intentionally hits phased ships, too!)
			for (ShipAPI target : CombatUtils.getShipsWithinRange(loc, basicAoESize)) {
				//Does not affect fighters (those are already hit by the base effect
				if (target.getHullSize() == ShipAPI.HullSize.FIGHTER) {
					continue;
				}

				//We never target things with NONE collision class
				if (target.getCollisionClass() == CollisionClass.NONE) {
					continue;
				}

				// Gets all weapons on the target
				for (WeaponAPI wep : target.getAllWeapons()) {
					// Ignore deco weapons
					if (wep.isDecorative()) {
						continue;
					}

					// If the weapon is close enough to be hit by the AoE, apply some very random EMP damage to it
					// Note that 33% of the EMP penetrates shields
					float distanceMult = (basicAoESize - MathUtils.getDistance(loc, wep.getLocation())) / (basicAoESize);
					if (distanceMult > 0f) {
						float randomFactor = (float)Math.random();
						Global.getCombatEngine().applyDamage(target, wep.getLocation(), 0f, DamageType.ENERGY,
								randomFactor * sourceProj.getDamageAmount() * distanceMult * (1f/3f),
								true, true, sourceProj.getSource(), false);
						Global.getCombatEngine().applyDamage(target, wep.getLocation(), 0f, DamageType.ENERGY,
								randomFactor * sourceProj.getDamageAmount() * distanceMult * (2f/3f),
								false, true, sourceProj.getSource(), false);
					}
				}
			}
		}
	}
}
