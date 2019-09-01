package data.scripts.campaign.rules;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import data.scripts.campaign.VassFamilyTrackerPlugin;
import data.scripts.utils.VassUtils;

import java.util.List;
import java.util.Map;

public class VassFamilyPowerIsAtLeast extends BaseCommandPlugin {

    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;

        String stringFamily = params.get(0).getString(memoryMap);
        VassUtils.VASS_FAMILY family = VassUtils.VASS_FAMILY.ACCEL;
        if (stringFamily.equals("TORPOR")) {
            family = VassUtils.VASS_FAMILY.TORPOR;
        } else if (stringFamily.equals("PERTURBA")) {
            family = VassUtils.VASS_FAMILY.PERTURBA;
        } else if (stringFamily.equals("MULTA")) {
            family = VassUtils.VASS_FAMILY.MULTA;
        } else if (stringFamily.equals("RECIPRO")) {
            family = VassUtils.VASS_FAMILY.RECIPRO;
        }

        float levelToCheck = params.get(1).getFloat(memoryMap);

        return VassFamilyTrackerPlugin.GetPowerOfFamily(family) >= levelToCheck;
    }

}