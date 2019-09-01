package data.scripts.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.ai.FleetAIFlags;
import com.fs.starfarer.api.campaign.ai.ModularFleetAIAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import org.lwjgl.util.vector.Vector2f;

public class VassCampaignUtils {
    //Utility function for getting a fleet to intercept another fleet
    public static void makeFleetInterceptOtherFleet(CampaignFleetAPI aggressor, CampaignFleetAPI defendant, boolean makeAggressive, float interceptDays) {
        if (aggressor.getAI() == null) {
            aggressor.setAI(Global.getFactory().createFleetAI(aggressor));
            aggressor.setLocation(aggressor.getLocation().x, aggressor.getLocation().y);
        }

        if (makeAggressive) {
            float expire = aggressor.getMemoryWithoutUpdate().getExpire(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE);
            aggressor.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true, Math.max(expire, interceptDays));
        }

        aggressor.getMemoryWithoutUpdate().unset(MemFlags.MEMORY_KEY_MAKE_ALLOW_DISENGAGE);

        aggressor.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOSTILE, true, interceptDays);
        aggressor.getMemoryWithoutUpdate().set(FleetAIFlags.LAST_SEEN_TARGET_LOC, new Vector2f(defendant.getLocation()), interceptDays);

        if (aggressor.getAI() instanceof ModularFleetAIAPI) {
            ((ModularFleetAIAPI)aggressor.getAI()).getTacticalModule().setTarget(defendant);
        }

        aggressor.addAssignmentAtStart(FleetAssignment.INTERCEPT, defendant, interceptDays, null);
    }
}
