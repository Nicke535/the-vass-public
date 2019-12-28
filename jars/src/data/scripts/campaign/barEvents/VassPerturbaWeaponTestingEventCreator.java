package data.scripts.campaign.barEvents;

import com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarEvent;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEventCreator;

/**
 * Spawns the bar event for the Perturba weapon testing contract
 * Repeatable quest, similar to fetch quests from other factions
 */
public class VassPerturbaWeaponTestingEventCreator extends BaseBarEventCreator {

    public PortsideBarEvent createBarEvent() {
        return new VassPerturbaWeaponTestingEvent();
    }

    @Override
    public float getBarEventAcceptedTimeoutDuration() {
        return 30f;
    }

    //I want to see it ALL THE TIME in debug mode
    @Override
    public float getBarEventFrequencyWeight() {
        if (VassPerturbaWeaponTestingEvent.DEBUG_MODE) {
            return 99999999999f;
        } else {
            return super.getBarEventFrequencyWeight();
        }
    }
}