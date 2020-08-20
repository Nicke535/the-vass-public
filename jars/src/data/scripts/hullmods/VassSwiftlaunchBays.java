package data.scripts.hullmods;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Saves extra fighters for the carrier to deploy when the ordinary ones run out; it's basically a passive reserve deployment?
 * @author Nicke535
 */
public class VassSwiftlaunchBays extends BaseHullMod {
    //Speed mult for replacing bonus fighters
    private static final float REPLACEMENT_TIME_MULT = 0.5f;

    //Hull-level for fighters to count as being in "trouble"
    private static final float LOW_HULL_LEVEL = 0.6f;

    //How much of a refit-refill we get from returning fighters
    private static final float RETURNER_BONUS_BASE = 0.7f;
    private static final float RETURNER_BONUS_HULL_LEVEL = 0.25f;

    private CombatEngineAPI engine = null;
    private HashMap<FighterWingAPI, Float> replacementFighters = new HashMap<>();
    private Set<ShipAPI> alreadyTriggeredFighters = new HashSet<>();

	@Override
	public void advanceInCombat(ShipAPI ship, float amount) {
	    //If our engine isn't the same one as the one we worked with most recently, clear all data
        if (Global.getCombatEngine() != engine) {
            engine = Global.getCombatEngine();
            replacementFighters.clear();
        }
        if (engine == null || !Global.getCurrentState().equals(GameState.COMBAT)) {
            return;
        }

        //Run once for each fighter wing on the ship
        float readyBackupCounter = 0f;
        for (FighterWingAPI wing : ship.getAllWings()) {
            //Instantiate map if necessary
            if (replacementFighters.get(wing) == null) {
                //Only run AFTER we've deployed the wing at least once
                if (wing.getWingMembers().size() >= wing.getSpec().getNumFighters()) {
                    replacementFighters.put(wing, (float)wing.getSpec().getNumFighters());
                } else {
                    continue;
                }
            }

            readyBackupCounter += (replacementFighters.get(wing)/wing.getSpec().getNumFighters());

            FighterLaunchBayAPI bayToAssist = null;
            for (FighterLaunchBayAPI bay : ship.getLaunchBaysCopy()) {
                if (bay.getWing() == wing) {
                    bayToAssist = bay;
                    break;
                }
            }
            if (bayToAssist == null) {
                //We didn't find a fighter bay for this fighter wing... something is awry, so stop for now
                continue;
            }

            //If our wing has more losses than it can instantly replace, we help it along
            int replacementsNeeded = wing.getSpec().getNumFighters()-wing.getWingMembers().size();
            if (bayToAssist.getFastReplacements() < replacementsNeeded) {
                int addedReplacements = (int)Math.floor(Math.min(replacementFighters.get(wing), replacementsNeeded - bayToAssist.getFastReplacements()));
                bayToAssist.setFastReplacements(bayToAssist.getFastReplacements()+addedReplacements);
                replacementFighters.put(wing, replacementFighters.get(wing)-addedReplacements);
            }

            //Tick up our replacements to simulate a second "line" of replacement. Only runs if all fighters are intact
            if (wing.getWingMembers().size() >= wing.getSpec().getNumFighters()) {
                float previousFrameProgress = replacementFighters.get(wing);
                float progressAddedThisFrame = amount* ship.getSharedFighterReplacementRate()*ship.getMutableStats().getFighterRefitTimeMult().getModifiedValue()*REPLACEMENT_TIME_MULT
                        / wing.getSpec().getRefitTime();
                replacementFighters.put(wing, Math.min(previousFrameProgress+progressAddedThisFrame, wing.getSpec().getNumFighters()));
            }

            //Check if any of our fighters are currently landing and refill a replacement in that case
            for (FighterWingAPI.ReturningFighter returner : wing.getReturning()) {
                if (alreadyTriggeredFighters.contains(returner.fighter) || !engine.isEntityInPlay(returner.fighter)) {
                    continue;
                }
                if (returner.fighter.isFinishedLanding()) {
                    alreadyTriggeredFighters.add(returner.fighter);
                    //Only refill replacement fighters if our fighters are already out; otherwise, we get a double-replace
                    if (wing.getWingMembers().size() >= wing.getSpec().getNumFighters()) {
                        replacementFighters.put(wing, Math.min(replacementFighters.get(wing) + RETURNER_BONUS_BASE + (RETURNER_BONUS_HULL_LEVEL*returner.fighter.getHullLevel()),
                                wing.getSpec().getNumFighters()));
                    }
                }
            }

            //Lastly, we check if any of our deployed fighters are in a pinch: in that case, order their retreat
            for (ShipAPI fighter : wing.getWingMembers()) {
                if (fighter.getHullLevel() < LOW_HULL_LEVEL) {
                    wing.orderReturn(fighter);
                }
            }
        }

        //If we're the player ship, display how ready our reserves are
        readyBackupCounter /= ship.getAllWings().size();
        readyBackupCounter = Math.round(100f * readyBackupCounter);
        if (ship.equals(engine.getPlayerShip())) {
            engine.maintainStatusForPlayerShip("vass_swiftlaunch_bays_displaydata",
                    "graphics/icons/hullsys/reserve_deployment.png", "Swiftlaunch Bays",
                    (int)readyBackupCounter + "% of backup craft readied", false);
        }

        //Lastly, clean up our already-triggered fighters
        Set<ShipAPI> toRemove = new HashSet<>();
        for (ShipAPI fighter : alreadyTriggeredFighters) {
            if (engine.isEntityInPlay(fighter)) {
                toRemove.add(fighter);
            }
        }
        for (ShipAPI fighter : toRemove) {
            alreadyTriggeredFighters.remove(fighter);
        }
	}

	//Prevents the hullmod from being put on ships; it's built-in only
	@Override
	public boolean isApplicableToShip(ShipAPI ship) {
		boolean canBeApplied = false;
		return canBeApplied;
	}

    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) {
            return "stores a copy";
        } else if (index == 1) {
            return "sends out its replacement";
        } else if (index == 2) {
            return "half as fast";
        } else if (index == 3) {
            return "called back to the carrier";
        } else {
            return null;
        }
    }
}
