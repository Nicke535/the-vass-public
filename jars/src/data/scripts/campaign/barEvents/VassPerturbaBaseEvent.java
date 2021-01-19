package data.scripts.campaign.barEvents;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.FullName.Gender;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEventWithPerson;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import data.scripts.campaign.VassFamilyTrackerPlugin;
import data.scripts.utils.VassUtils;
import org.apache.log4j.Logger;

import java.awt.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Base bar event for most Perturba interactions
 * @author Nicke535
 */
public class VassPerturbaBaseEvent extends BaseBarEventWithPerson {
    public VassPerturbaBaseEvent() {
        super();
    }

    public static final String VASS_PERTURBA_CONTACT_KEY = "$vass_perturba_contact_person";
    public static final String VASS_PERTURBA_HAS_MET_CONTACT_KEY = "$vass_perturba_has_met_contact";

    public static final String CURRENT_EVENT_ALLOWED_KEY = "$vass_perturba_current_event_allowed";
    public static final String LAST_MARKET_ALLOW_CHECK_KEY = "$vass_perturba_last_market_allow_checked";
    public static final String PERTURBA_EVENTS_BLOCKED_KEY = "$vass_perturba_events_blocked";

    /**
     * Gets perturba's contact
     */
    public static PersonAPI getContact () {
        if (!(Global.getSector().getMemoryWithoutUpdate().get(VASS_PERTURBA_CONTACT_KEY) instanceof PersonAPI)) {
            return null;
        } else {
            return (PersonAPI) Global.getSector().getMemoryWithoutUpdate().get(VASS_PERTURBA_CONTACT_KEY);
        }
    }

    /**
     * Checks if the player has met Perturba's contact yet
     */
    public static boolean hasMetContact() {
        Object hasMetContact = Global.getSector().getMemoryWithoutUpdate().get(VASS_PERTURBA_HAS_MET_CONTACT_KEY);
        if (hasMetContact instanceof Boolean) {
            return (boolean)hasMetContact;
        } else {
            return false;
        }
    }

    /**
     * Checks and eventually sets a special event to appear, instead of repeatable events.
     * Currently only used for the "get a submarket" event
     */
    public static void checkAndSetAllowedEvent() {
        SectorAPI sector = Global.getSector();
        if (sector == null) return;

        //Currently, we have a 50% chance of getting the special submarket event after we've hit the reputation threshold
        //      ...though, it doesn't trigger if the submarket has already been handed out
        if (VassFamilyTrackerPlugin.getRelationToFamily(VassUtils.VASS_FAMILY.PERTURBA) > 30f) {
            if (sector.getFaction("vass").getRelToPlayer().isAtWorst(RepLevel.FRIENDLY)) {
                Object hasContract = Global.getSector().getMemoryWithoutUpdate().get(VassPerturbaGetShipSubmarketEvent.VASS_PERTURBA_SHIP_SUBMARKET_CONTRACT_KEY);
                if (Math.random() < 0.5f && (!(hasContract instanceof Boolean) || !((boolean)hasContract))) {
                    Global.getSector().getMemoryWithoutUpdate().set(VassPerturbaBaseEvent.CURRENT_EVENT_ALLOWED_KEY, "get_ship_submarket");
                    return;
                }
            }
        }

        //If no event was registered, remove the flag and move on
        Global.getSector().getMemoryWithoutUpdate().unset(VassPerturbaBaseEvent.CURRENT_EVENT_ALLOWED_KEY);
    }

    /**
     * Base implementation for checking if an event should appear: just checks for player markets and that Perturba hasn't disappeared
     */
    public boolean shouldShowAtMarket(MarketAPI market) {
        if (!super.shouldShowAtMarket(market)) return false;

        //Don't appear if Perturba has been knocked out
        if (VassFamilyTrackerPlugin.isFamilyEliminated(VassUtils.VASS_FAMILY.PERTURBA)) {
            return false;
        }

        //Only the player's markets can get the event
        if (!market.getFaction().isPlayerFaction()) {
            return false;
        }

        //If we're currently blocked from showing Perturba events, don't show up either
        Object blocked = Global.getSector().getMemoryWithoutUpdate().get(PERTURBA_EVENTS_BLOCKED_KEY);
        if (blocked instanceof Boolean && blocked.equals(Boolean.TRUE)) {
            return false;
        }

        return true;
    }

    // Creates an NPC if necessary, ensuring they have a portrait that fits and that they are stored in the global memory
    @Override
    protected void regen(MarketAPI market) {
        if (this.market == market) {
            return;
        }
        super.regen(market);

        //If we had an existing person, use that
        PersonAPI oldPerson = getContact();
        if (oldPerson != null) {
            person = oldPerson;
        } else {
            if (person.getGender() == Gender.MALE) {
                person.setPortraitSprite(pickMalePortrait());
            } else {
                person.setPortraitSprite(pickFemalePortrait());
            }
            Global.getSector().getMemoryWithoutUpdate().set(VASS_PERTURBA_CONTACT_KEY, person);
        }
    }

    protected transient boolean failed = false;
    protected void doDataFail() {
        failed = true;
    }

    // All of these are settings for the interaction target
    @Override
    protected String getPersonFaction() {
        return "vass_perturba";
    }

    @Override
    protected String getPersonRank() {
        return Ranks.POST_CITIZEN;
    }

    @Override
    protected String getPersonPost() {
        return Ranks.POST_SHADY;
    }

    @Override
    protected String getPersonPortrait() {
        return null;
    }

    @Override
    protected Gender getPersonGender() {
        return Gender.ANY;
    }

    private String pickMalePortrait() {
        WeightedRandomPicker<String> post = new WeightedRandomPicker<String>();
        post.add("graphics/portraits/portrait_mercenary01.png");
        post.add("graphics/portraits/portrait_corporate03.png");
        post.add("graphics/portraits/portrait33.png");
        return post.pick();
    }

    private String pickFemalePortrait() {
        WeightedRandomPicker<String> post = new WeightedRandomPicker<String>();
        post.add("graphics/portraits/portrait_corporate02.png");
        post.add("graphics/portraits/portrait_pirate02.png");
        post.add("graphics/portraits/portrait27.png");
        return post.pick();
    }
}
