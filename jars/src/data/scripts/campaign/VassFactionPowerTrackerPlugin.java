//By Nicke535
package data.scripts.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import data.scripts.utils.VassUtils;

import java.util.HashMap;
import java.util.Map;

public class VassFactionPowerTrackerPlugin implements EveryFrameScript {

    //Keeps track of the actual power each family has in the sector
    private Map<VassUtils.VASS_FAMILY, Float> familyPowerMap = null;

    //Keeps track of our own plugin instance
    static VassFactionPowerTrackerPlugin currentInstance = null;

    @Override
    public void advance( float amount ) {
        //--Initializes the family powers to their starting values--
        if (familyPowerMap == null) {
            familyPowerMap = new HashMap<>();
            //In the current version, only Perturba has any power; things will not remain as such later
            familyPowerMap.put(VassUtils.VASS_FAMILY.ACCEL, 0f);
            familyPowerMap.put(VassUtils.VASS_FAMILY.TORPOR, 0f);
            familyPowerMap.put(VassUtils.VASS_FAMILY.PERTURBA, 0.3f);
            familyPowerMap.put(VassUtils.VASS_FAMILY.RECIPRO, 0f);
            familyPowerMap.put(VassUtils.VASS_FAMILY.MULTA, 0f);
        }
        //--End of power initialization


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

    //Static functions for modifying and accessing the power of a Vass family
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
}
