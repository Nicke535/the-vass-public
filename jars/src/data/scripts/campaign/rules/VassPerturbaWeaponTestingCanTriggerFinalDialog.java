package data.scripts.campaign.rules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import data.scripts.campaign.VassFamilyTrackerPlugin;
import data.scripts.campaign.barEvents.VassPerturbaWeaponTestingEvent;
import data.scripts.campaign.barEvents.VassPerturbaWeaponTestingIntel;
import data.scripts.utils.VassUtils;
import org.lazywizard.lazylib.MathUtils;

import java.awt.*;
import java.util.List;
import java.util.Map;

/**
 * Checks whether the current dialog interaction can trigger the final dialog for the Perturba Weapon Testing quest
 * @author Nicke535
 */
public class VassPerturbaWeaponTestingCanTriggerFinalDialog extends BaseCommandPlugin {

    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;

        //Failsafe
        if (dialog.getInteractionTarget() == null){
            return false;
        }

        //Non-markets can impossibly be the right market
        MarketAPI market = dialog.getInteractionTarget().getMarket();
        if (market == null) {
            return false;
        }

        //No collateral data: something either went wrong, or we haven't started the mission
        Object collateral = Global.getSector().getMemory().get(VassPerturbaWeaponTestingEvent.COLLATERAL_MEM_KEY);
        if (!(collateral instanceof Integer)) {
            return false;
        }

        //Search for our active intel: if it exists, check whether enough tests have been completed and that our interaction target is the right market
        for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel(VassPerturbaWeaponTestingIntel.class)) {
            if (intel instanceof VassPerturbaWeaponTestingIntel) {
                VassPerturbaWeaponTestingIntel intelConverted = (VassPerturbaWeaponTestingIntel) intel;
                return intelConverted.battlesCompleted >= 3 && intelConverted.getMarket() == market;
            }
        }

        //No previous condition matched: return false
        return false;
    }

}