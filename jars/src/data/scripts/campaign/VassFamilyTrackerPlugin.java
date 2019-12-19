//By Nicke535
package data.scripts.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import data.scripts.utils.VassUtils;
import org.jetbrains.annotations.Nullable;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.campaign.CampaignUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.HashMap;
import java.util.Map;

public class VassFamilyTrackerPlugin implements EveryFrameScript {

    //Keeps track of the actual power each family has in the sector.
    // 100f means that the faction has reached their "final goal"; values above 100 is only possible after said goal
    // 0f means the faction is eliminated from the sector altogether
    private Map<VassUtils.VASS_FAMILY, Float> familyPowerMap = null;

    //Keeps track of the relation score each family has towards the player: this is independent from Vass relation in general
    // Goes between -100f to 100f, but doesn't tell the whole story: Vass relation also plays a part
    private Map<VassUtils.VASS_FAMILY, Float> familyRelationMap = null;

    //Keeps track of which family, if any, the player has membership status to
    private VassUtils.VASS_FAMILY currentFamilyMembership = null;

    //Keeps track of our own plugin instance
    private static VassFamilyTrackerPlugin currentInstance = null;

    //--Loot revenge fleet stats--
    //How long of a cooldown is left until a new looting-punisher fleet can pop out and hunt the player, and the minimum/maximum cooldown of this
    //The more power the family that sends the fleet has, the lower the cooldown. Specified in days.
    private float currentLootRevengeCooldown = 0f;
    private static final float MAX_LOOT_REVENGE_COOLDOWN = 400f;
    private static final float MIN_LOOT_REVENGE_COOLDOWN = 150f;

    //How many fleet points can the families dish out per "power" they have?
    private static final float LOOT_FLEET_FP_PER_POWER = 3f;

    //How many fleet points will a loot revenge fleet have compared to the player fleet?
    private static final float LOOT_FLEET_FP_FACTOR = 1.2f;
    //--Loot revenge fleet stats end--

    //Required for an EveryFrameScript
    @Override
    public boolean isDone() {
        return false;
    }
    @Override
    public boolean runWhilePaused() {
        return false;
    }

    //Main advance() loop
    @Override
    public void advance( float amount ) {
        //Store our plugin for ease-of-use
        currentInstance = this;

        //Initializes the family powers and relations to their starting values, if we haven't already
        if (familyPowerMap == null) {
            initializeFamilyPower();
        }
        if (familyRelationMap == null) {
            initializeFamilyRelations();
        }

        //--  Checks the player fleet for possession of a Vass ship, and orders a fleet to... give them some trouble  --
        currentLootRevengeCooldown -= Misc.getDays(amount);
        if (currentLootRevengeCooldown <= 0f) {
            if (playerHasVassShips()) {
                VassUtils.VASS_FAMILY familyToSpawnVia = VassUtils.VASS_FAMILY.values()[MathUtils.getRandomNumberInRange(0, VassUtils.VASS_FAMILY.values().length-1)];
                int tests = 0;
                while (tests < 50) {
                    if (getPowerOfFamily(familyToSpawnVia) * LOOT_FLEET_FP_PER_POWER >= Global.getSector().getPlayerFleet().getFleetPoints() * LOOT_FLEET_FP_FACTOR) {
                        break;
                    } else {
                        familyToSpawnVia = VassUtils.VASS_FAMILY.values()[MathUtils.getRandomNumberInRange(0, VassUtils.VASS_FAMILY.values().length-1)];
                        tests++;
                    }
                }
                if (tests < 50) {
                    spawnPlayerLootingPunishFleet(familyToSpawnVia);
                    currentLootRevengeCooldown = ((100f - getPowerOfFamily(familyToSpawnVia))/100f)*MAX_LOOT_REVENGE_COOLDOWN + ((getPowerOfFamily(familyToSpawnVia))/100f)*MIN_LOOT_REVENGE_COOLDOWN;
                    if (Global.getSector().getMemoryWithoutUpdate().get("$vass_firstTimeVassShipLooted") instanceof Boolean && !(Boolean)Global.getSector().getMemoryWithoutUpdate().get("$vass_firstTimeVassShipLooted")) {
                        //Not the first time this happens... no extra memory flag needed
                    } else {
                        //First time we're punishing the player; mark that in our memory
                        Global.getSector().getMemoryWithoutUpdate().set("$vass_firstTimeVassShipLooted", true);
                    }

                }
            } else {
                currentLootRevengeCooldown = 0.1f;
            }
        }
        //--  End of loot punisher fleet code  --
    }


    // Initializes the family power map to its default state
    private void initializeFamilyPower() {
        familyPowerMap = new HashMap<>();
        //In the current version, only Perturba has any power; things will not remain as such later
        familyPowerMap.put(VassUtils.VASS_FAMILY.ACCEL, 0f);
        familyPowerMap.put(VassUtils.VASS_FAMILY.TORPOR, 0f);
        familyPowerMap.put(VassUtils.VASS_FAMILY.PERTURBA, 40f);
        familyPowerMap.put(VassUtils.VASS_FAMILY.RECIPRO, 0f);
        familyPowerMap.put(VassUtils.VASS_FAMILY.MULTA, 0f);
    }

    // Initializes the family relations map to its default state. Also sets the player's relation to Vass as a whole
    private void initializeFamilyRelations() {
        Global.getSector().getPlayerFaction().setRelationship("vass", RepLevel.INHOSPITABLE);
        familyRelationMap = new HashMap<>();
        familyRelationMap.put(VassUtils.VASS_FAMILY.ACCEL, 0f);
        familyRelationMap.put(VassUtils.VASS_FAMILY.TORPOR, 0f);
        familyRelationMap.put(VassUtils.VASS_FAMILY.PERTURBA, 0f);
        familyRelationMap.put(VassUtils.VASS_FAMILY.RECIPRO, 0f);
        familyRelationMap.put(VassUtils.VASS_FAMILY.MULTA, 0f);
    }


    // Checks whether the player fleet has any Vass ships in their fleet
    private boolean playerHasVassShips() {
        for (FleetMemberAPI member : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
            if (member.getHullId().contains("vass_")) {
                return true;
            }
        }

        return false;
    }


    //Static functions for modifying and accessing the power of a Vass family. -1 means that you cannot access the power at the moment. A family at 0 power is eliminated
    public static void modifyPowerOfFamily(VassUtils.VASS_FAMILY family, float amount) {
        if (currentInstance != null) {
            currentInstance.familyPowerMap.put(family, currentInstance.familyPowerMap.get(family)+amount);
        }
    }
    public static float getPowerOfFamily(VassUtils.VASS_FAMILY family) {
        if (currentInstance == null) {
            return -1f;
        } else {
            return currentInstance.familyPowerMap.get(family);
        }
    }
    public static boolean isFamilyEliminated(VassUtils.VASS_FAMILY family) {
        return getPowerOfFamily(family) == 0f;
    }


    //Static functions for modifying and accessing the relation of the player to a Vass family
    public static void modifyRelationToFamily(VassUtils.VASS_FAMILY family, float amount) {
        if (currentInstance != null) {
            float newRelation = currentInstance.familyRelationMap.get(family) + amount;
            currentInstance.familyRelationMap.put(family, newRelation);
        }
    }
    public static float getRelationToFamily(VassUtils.VASS_FAMILY family) {
        if (currentInstance == null) {
            return 0f;
        } else {
            return currentInstance.familyRelationMap.get(family);
        }
    }

    //Static functions for modifying and accessing the family which the player is a member of
    public static void setFamilyMembership(@Nullable VassUtils.VASS_FAMILY family) {
        if (currentInstance != null) {
            currentInstance.currentFamilyMembership = family;
        }
    }
    public static VassUtils.VASS_FAMILY getFamilyMembership() {
        if (currentInstance == null) {
            return null;
        } else {
            return currentInstance.currentFamilyMembership;
        }
    }


    //Generates a fleet near the player that hunts them for looting Vass stuff. Spawned from a family, and can take some custom arguments for special fleets
    public static void spawnPlayerLootingPunishFleet(VassUtils.VASS_FAMILY family) {
        spawnPlayerLootingPunishFleet(family, "");
    }
    public static void spawnPlayerLootingPunishFleet(VassUtils.VASS_FAMILY family, String specialOptions) {
        SectorAPI sector = Global.getSector();
        LocationAPI loc = sector.getPlayerFleet().getContainingLocation();
        Vector2f centerPoint = sector.getPlayerFleet().getLocation();

        //Determines faction set to pick ships from
        String factionToPickFrom = null;
        if (family == VassUtils.VASS_FAMILY.PERTURBA) {
            //Normal perturba fleet
            factionToPickFrom = "vass_perturba";
        }
        if (factionToPickFrom == null) {
            return;
        }

        //Creates the fleet, with only combat ships and at a location that isn't optimal yet
        FleetParamsV3 params = new FleetParamsV3(centerPoint, factionToPickFrom, 5f, "taskForce",
                Global.getSector().getPlayerFleet().getFleetPoints() * LOOT_FLEET_FP_PER_POWER * LOOT_FLEET_FP_FACTOR,
                0f, 0f, 0f, 0f, 0f, 1f);
        CampaignFleetAPI newFleet = FleetFactoryV3.createFleet(params);
        newFleet.inflateIfNeeded();
        newFleet.setContainingLocation(loc);
        newFleet.setFaction("vass", true);
        newFleet.getMemoryWithoutUpdate().set("$vass_fleet_family_membership", family);

        //Gets a spawn point that's not too close to a fleet that would wipe us out, and outside the player's (base) sensor range: if they're currently sensor pinging, it's fine to appear "suddenly"
        Vector2f desiredSpawnPoint = MathUtils.getPoint(centerPoint, sector.getPlayerFleet().getBaseSensorRangeToDetect(newFleet.getSensorProfile())*1.2f, MathUtils.getRandomNumberInRange(0f, 360f));
        newFleet.setLocation(desiredSpawnPoint.x, desiredSpawnPoint.y);
        int tries = 0;
        while (tries < 50) {
            CampaignFleetAPI hostileThreat = CampaignUtils.getNearestHostileFleet(newFleet);
            if (hostileThreat == null) {
                break;
            } else if (hostileThreat.getFleetPoints() <= Global.getSector().getPlayerFleet().getFleetPoints() * LOOT_FLEET_FP_PER_POWER * LOOT_FLEET_FP_FACTOR * 0.6f) {
                break;
            } else if (MathUtils.getDistance(hostileThreat.getLocation(), desiredSpawnPoint) >= Math.max(newFleet.getRadius()*3f, hostileThreat.getRadius()*3f)) {
                break;
            }
            tries++;
            desiredSpawnPoint = MathUtils.getPoint(centerPoint, sector.getPlayerFleet().getBaseSensorRangeToDetect(newFleet.getSensorProfile()), MathUtils.getRandomNumberInRange(0f, 360f));
            newFleet.setLocation(desiredSpawnPoint.x, desiredSpawnPoint.y);
        }

        //Finally, makes the fleet hostile against the player's fleet, and register that this is indeed a special "loot punish" fleet, since that needs to be accessed in rules.csv
        newFleet.addTag("$vass_loot_punish_fleet");
        VassCampaignUtils.makeFleetInterceptOtherFleet(newFleet, Global.getSector().getPlayerFleet(), true, 30f);
        loc.addEntity(newFleet);
    }


    //Sets the cooldown of spawning a looting-punishment fleet manually
    public static void setLootingPunishFleetCooldown(float valueToSetTo) {
        if (currentInstance != null) {
            currentInstance.currentLootRevengeCooldown = valueToSetTo;
        }
    }
}
