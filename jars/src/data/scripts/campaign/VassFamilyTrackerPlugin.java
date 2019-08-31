//By Nicke535
package data.scripts.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import data.scripts.utils.VassUtils;

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

    //Keeps track of our own plugin instance
    private static VassFamilyTrackerPlugin currentInstance = null;

    @Override
    public void advance( float amount ) {
        //--Initializes the family powers to their starting values--
        if (familyPowerMap == null) {
            familyPowerMap = new HashMap<>();
            //In the current version, only Perturba has any power; things will not remain as such later
            familyPowerMap.put(VassUtils.VASS_FAMILY.ACCEL, 0f);
            familyPowerMap.put(VassUtils.VASS_FAMILY.TORPOR, 0f);
            familyPowerMap.put(VassUtils.VASS_FAMILY.PERTURBA, 30f);
            familyPowerMap.put(VassUtils.VASS_FAMILY.RECIPRO, 0f);
            familyPowerMap.put(VassUtils.VASS_FAMILY.MULTA, 0f);
        }
        //--End of power initialization

        //Checks the player fleet for possession of a Vass ship, and orders a fleet to... give them some trouble, should they decide to not comply


        //Store our plugin for ease-of-use
        currentInstance = this;
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    //Static functions for modifying and accessing the power of a Vass family. -1 means that you cannot access the power at the moment. A family at 0 power is eliminated
    public static void ModifyPowerOfFamily(VassUtils.VASS_FAMILY family, float amount) {
        if (currentInstance != null) {
            currentInstance.familyPowerMap.put(family, currentInstance.familyPowerMap.get(family)+amount);
        }
    }
    public static float GetPowerOfFamily(VassUtils.VASS_FAMILY family) {
        if (currentInstance == null) {
            return -1f;
        } else {
            return currentInstance.familyPowerMap.get(family);
        }
    }
    public static boolean IsFamilyEliminated(VassUtils.VASS_FAMILY family) {
        return GetPowerOfFamily(family) == 0f;
    }


    //Static functions for modifying and accessing the relation of the player to a Vass family
    public static void ModifyRelationToFamily(VassUtils.VASS_FAMILY family, float amount) {
        if (currentInstance != null) {
            currentInstance.familyRelationMap.put(family, currentInstance.familyRelationMap.get(family)+amount);
        }
    }
    public static float GetRelationToFamily(VassUtils.VASS_FAMILY family) {
        if (currentInstance == null) {
            return 0f;
        } else {
            return currentInstance.familyRelationMap.get(family);
        }
    }
}
