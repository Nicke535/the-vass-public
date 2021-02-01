package data.scripts.campaign.barEvents;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager;

import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import data.scripts.campaign.VassFamilyTrackerPlugin;
import data.scripts.utils.VassUtils;
import org.apache.log4j.Logger;

import java.awt.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Creates an event where the player can hire a contract for Perturba weapons to their faction
 * @author Nicke535
 */
public class VassPerturbaWeaponContractEvent extends VassPerturbaBaseEvent {
    public static final Logger LOGGER = Global.getLogger(VassPerturbaWeaponContractEvent.class);

    public static final boolean DEBUG_MODE = false;

    //Some memory keys used by the event
    public static final String VASS_PERTURBA_WEAPON_CONTRACT_KEY = "$vass_perturba_weapon_contract";

    //All the blueprints unlocked by the event
    public static final Set<String> UNLOCKED_WEAPONS = new HashSet<>();
    static {
        UNLOCKED_WEAPONS.add("vass_dyrnwyn");
        UNLOCKED_WEAPONS.add("vass_cyllel_farchog");
        UNLOCKED_WEAPONS.add("vass_yawarakai_te");
        UNLOCKED_WEAPONS.add("vass_caladbolg");
        UNLOCKED_WEAPONS.add("vass_asi");
        UNLOCKED_WEAPONS.add("vass_fragarach");
        UNLOCKED_WEAPONS.add("vass_tizona");
    }

    public enum OptionId {
        INIT,
        CONTINUE_1,
        CONTINUE_KNOWS_PERTURBA,
        CONTINUE_DOES_NOT_KNOW_PERTURBA,
        CONTINUE_2,
        CONTINUE_BUY_SERVICE,
        LEAVE_NONHOSTILE,
        LEAVE_HOSTILE,
        LEAVE,
    }

    private static int PURCHASE_COST = 20000;
    private static float RELATIONS_AFTER_DEAL = 35f;
    private static float RELATIONS_AFTER_BAD_DEAL = -25f;

    public VassPerturbaWeaponContractEvent() {
        super();
    }

    // Checks if the event is allowed to appear
    public boolean shouldShowAtMarket(MarketAPI market) {
        if (!super.shouldShowAtMarket(market)) return false;

        if (DEBUG_MODE) { return true; }

        //Don't appear if Perturba are enemies to the player
        if (VassFamilyTrackerPlugin.getRelationToFamily(VassUtils.VASS_FAMILY.PERTURBA) < 0f) {
            LOGGER.info("Threw away Perturba event due to bad relations with Perturba");
            return false;
        }
        if (!Global.getSector().getPlayerFaction().getRelationshipLevel("vass").isAtWorst(RepLevel.INHOSPITABLE)) {
            LOGGER.info("Threw away Perturba event due to bad relations with Vass");
            return false;
        }

        //The player needs to own at least one market with heavy industry
        boolean hasProduction = false;
        for (MarketAPI otherMarket: Misc.getFactionMarkets(Global.getSector().getPlayerFaction())) {
            for (Industry industry : otherMarket.getIndustries()) {
                if (industry.getSpec().hasTag("heavyindustry")) {
                    hasProduction = true;
                    break;
                }
            }
            if (hasProduction) { break; }
        }
        if (!hasProduction) {
            LOGGER.info("Threw away Perturba event due to missing production");
            return false;
        }

        //Don't appear if the player has an active contract with Perturba already
        Object hasContract = Global.getSector().getMemoryWithoutUpdate().get(VASS_PERTURBA_WEAPON_CONTRACT_KEY);
        if (hasContract instanceof Boolean) {
            if ((Boolean) hasContract) {
                LOGGER.info("Threw away Perturba event due to prior contract");
                return false;
            }
        }

        return true;
    }

    // Add the event to the intel screen; doesn't do much for now, but it's good to keep it there for future reference
    private void addIntel() {
        TextPanelAPI text = dialog.getTextPanel();
        VassPerturbaWeaponContractIntel intel = new VassPerturbaWeaponContractIntel(this);
        intel.setImportant(true);
        Global.getSector().getIntelManager().addIntel(intel, false, text);
    }

    // Creates the actual prompt and description when entering the bar. Picks randomly from a list, with some variations based on circumstance
    @Override
    public void addPromptAndOption(InteractionDialogAPI dialog) {
        super.addPromptAndOption(dialog);

        regen(dialog.getInteractionTarget().getMarket());

        TextPanelAPI text = dialog.getTextPanel();
        text.addPara(pickDescription());

        Color c = Misc.getBasePlayerColor();
        //Color c = Misc.getHighlightColor();

        dialog.getOptionPanel().addOption(pickPrompt(), this,
                c, null);
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        super.init(dialog);

        done = false;

        dialog.getVisualPanel().showPersonInfo(person, true);

        optionSelected(null, OptionId.INIT);
    }

    // Handles all dialogue alternatives and outcomes
    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (!(optionData instanceof OptionId)) {
            return;
        }
        OptionId option = (OptionId) optionData;

        Color t = Misc.getTextColor();
        Color h = Misc.getHighlightColor();
        Color n = Misc.getNegativeHighlightColor();
        float pad = 3f;

        OptionPanelAPI options = dialog.getOptionPanel();
        TextPanelAPI text = dialog.getTextPanel();
        options.clearOptions();

        if (!hasMetContact()) {
            optionSelectedHandleFirstTime(text, option, options, t, h, n);
        } else {
            optionSelectedHandleFollowupTime(text, option, options, t, h, n);
        }
    }

    //For first encounters
    private void optionSelectedHandleFirstTime(TextPanelAPI text, OptionId option, OptionPanelAPI options, Color t, Color h, Color n) {
        switch (option) {
            case INIT:
                text.addPara("The suspicious-looking individual nods at you as you approach them.");
                text.addPara("'Why hello there captain. Or... maybe you go by some other name here? Commander? Overlord? Either way; I think I may have a business opportunity that might interest you...'");

                options.addOption("Say you'd rather know who you're dealing with before considering 'business opportunities'.", OptionId.CONTINUE_1);
                options.addOption("Apologize, stating you have other matters to attend to.", OptionId.LEAVE);
                break;
            case CONTINUE_1:
                text.addPara("'Ah, why of course. Tell me, does the name Perturba ring any bells?'", t, h, "Perturba");

                options.addOption("Inform them you indeed know of a weapon smuggling ring going by that name.", OptionId.CONTINUE_KNOWS_PERTURBA);
                options.addOption("Tell them you don't recognize any 'Perturba'.", OptionId.CONTINUE_DOES_NOT_KNOW_PERTURBA);
                options.addOption("Tell them you honestly have other things to do right now, and leave.", OptionId.LEAVE);
                break;
            case CONTINUE_KNOWS_PERTURBA:
                text.addPara("They break a tiny smile. 'Well that makes things easy. See, I'm a... 'contact' of sorts in their employ. I handle external affairs to promising customers. Which is where YOU come in.'");

                options.addOption("Urge him to go on.", OptionId.CONTINUE_2);
                options.addOption("You will have nothing to do with this criminal! Call the guards.", OptionId.LEAVE_HOSTILE);
                break;
            case CONTINUE_DOES_NOT_KNOW_PERTURBA:
                text.addPara("'Is that so? Well, I suppose we technically are supposed to be fairly secretive. Explaining everything we stand for is frankly a waste of both of our time, so I'll give you the summarized version.'");
                text.addPara("'Perturba are a weapon manufacturer and procurer, specializing in some more... unique... ordinance solutions. More specifically, weaponry normally restricted by the 312th clause of the Domain temporal weapons ban, though I suppose what it's referred as to varies from group to group nowadays. While we deal in both personnel- and ship-scale weaponry, I'm mostly only affiliated with the ship-scale side of things.'");

                options.addOption("Ask what the deal they wanted to discuss entails.", OptionId.CONTINUE_2);
                options.addOption("They have the audacity to represent an illegal arms dealing syndicate in plain view? Call the guards!", OptionId.LEAVE_HOSTILE);
                break;
            case CONTINUE_2:
                text.addPara("Simply put, we would like to extend our services to you. You pay us an up-front cost of, say... " + PURCHASE_COST + " credits, and after that you and your fleets can acquire as much ordinance as you want from us.", h, ""+PURCHASE_COST);
                text.addPara("All we ask in return is fair payment for the weapons we deliver, and that you supply a fraction of the more common materials we'll need for their construction; surely, a cheap price to pay for someone capable of owning a colony, no?");
                options.addOption("Agree to pay for their services", OptionId.CONTINUE_BUY_SERVICE);
                if (Global.getSector().getPlayerFleet().getCargo().getCredits().get() < PURCHASE_COST) {
                    options.setEnabled(OptionId.CONTINUE_BUY_SERVICE, false);
                    options.setTooltip(OptionId.CONTINUE_BUY_SERVICE, "You don't have enough credits.");
                }
                options.addOption("Inform the agent you're not interested in their services at this moment.", OptionId.LEAVE_NONHOSTILE);
                options.addOption("You've heard enough from this criminal. Call the guards!", OptionId.LEAVE_HOSTILE);
                break;
            case CONTINUE_BUY_SERVICE:
                text.addPara("'Pleasure doing business with you. Here:' The agent hands you a tiny tri-chip. 'This should provide you with a one-way encrypted comms channel to our sales department and a program for easily managing your orders. It should pop up under your normal administrative functions; just make an order and the program should requisition credits and other resources as necessary to our collection location. Then, we'll bring the wares to the agreed-upon drop point once they goods are ready.'", h, "normal administrative functions");

                text.setFontSmallInsignia();
                text.addPara("Lost " + PURCHASE_COST + " credits", n, h, "" + PURCHASE_COST);
                Global.getSector().getPlayerFleet().getCargo().getCredits().add(-1 * PURCHASE_COST);

                float currentPerturbaRelations = VassFamilyTrackerPlugin.getRelationToFamily(VassUtils.VASS_FAMILY.PERTURBA);
                if (VassFamilyTrackerPlugin.getRelationToFamily(VassUtils.VASS_FAMILY.PERTURBA) < RELATIONS_AFTER_DEAL) {
                    VassFamilyTrackerPlugin.modifyRelationToFamily(VassUtils.VASS_FAMILY.PERTURBA, RELATIONS_AFTER_DEAL - currentPerturbaRelations);
                    text.addPara("Relations with Perturba improved to "+Math.round(RELATIONS_AFTER_DEAL), h, "Perturba", ""+Math.round(RELATIONS_AFTER_DEAL));
                }
                if (Global.getSector().getPlayerFaction().getRelationshipLevel("vass").isAtBest(RepLevel.SUSPICIOUS)) {
                    text.addPara("Relations with the Vass Families improved to Neutral", h, "Vass Families", "Neutral");
                    Global.getSector().getPlayerFaction().setRelationship("vass", RepLevel.NEUTRAL);
                }
                text.setFontInsignia();

                text.addPara("'I hope this is the beginning of a long and profitable partnership for us both.'");

                for (String weapon : VassPerturbaWeaponContractEvent.UNLOCKED_WEAPONS) {
                    Global.getSector().getPlayerFaction().addKnownWeapon(weapon, true);
                }
                BarEventManager.getInstance().notifyWasInteractedWith(this);
                Global.getSector().getMemoryWithoutUpdate().set(VASS_PERTURBA_WEAPON_CONTRACT_KEY, true);
                Global.getSector().getMemoryWithoutUpdate().set(VASS_PERTURBA_HAS_MET_CONTACT_KEY, true);

                addIntel();
                options.addOption("Leave", OptionId.LEAVE);
                break;
            case LEAVE_NONHOSTILE:
                text.addPara("The Perturba agent looks mildly disappointed. 'Is that so? Well, just inform us if you change your mind; I'll probably come back now and then. This IS a rather nice bar, after all.'");
                Global.getSector().getMemoryWithoutUpdate().set(VASS_PERTURBA_HAS_MET_CONTACT_KEY, true);

                options.addOption("Leave", OptionId.LEAVE);
                break;
            case LEAVE_HOSTILE:
                Global.getSoundPlayer().playUISound("vass_perturba_flashbang", 1f, 1f); // Flashbang SFX: he *did* just flashbang you!
                text.addPara("'Your loss.' The agent quickly pulls out something from their pocket and throws it in your direction. Before you can react a massive bang and a bright flash of light throws you to the ground.");
                text.addPara("By the time you've regained your senses, they are nowhere to be seen. Luckily it seems like the bar made it relatively unscathed, even if the guards and bar visitors are visibly shaken; the agent must have thrown some kind of riot suppression weapon.");
                text.addPara("You have a feeling Perturba won't be offering you any new deals any time soon.");

                text.setFontSmallInsignia();
                text.addPara("Relations with Perturba worsened to "+Math.round(RELATIONS_AFTER_BAD_DEAL), h, "Perturba", ""+Math.round(RELATIONS_AFTER_BAD_DEAL));
                text.setFontInsignia();

                VassFamilyTrackerPlugin.modifyRelationToFamily(VassUtils.VASS_FAMILY.PERTURBA,
                        -((-RELATIONS_AFTER_BAD_DEAL) + VassFamilyTrackerPlugin.getRelationToFamily(VassUtils.VASS_FAMILY.PERTURBA)));

                Global.getSector().getMemoryWithoutUpdate().set(VASS_PERTURBA_HAS_MET_CONTACT_KEY, true);

                options.addOption("Leave", OptionId.LEAVE);
                break;
            case LEAVE:
                noContinue = true;
                done = true;
                break;
        }
    }

    //For followup encounters
    private void optionSelectedHandleFollowupTime(TextPanelAPI text, OptionId option, OptionPanelAPI options, Color t, Color h, Color n) {
        switch (option) {
            case INIT:
                text.addPara("The Perturba agent waves at you as you approach them.");
                text.addPara("'Hello there. Made up your mind about our offer yet?'");

                options.addOption("About that deal...", OptionId.CONTINUE_1);
                options.addOption("You've made up your mind alright. Made up your mind to get rid of this lowlife. Guards!", OptionId.LEAVE_HOSTILE);
                options.addOption("Tell them you don't have anything to discuss at the moment.", OptionId.LEAVE);
                break;
            case CONTINUE_1:
                text.addPara("Ah, yes, of course. I'll go through deal again for completeness sake: you pay us an up-front cost of about " + PURCHASE_COST + " credits, and after that you and your fleets can acquire as much ordinance as you want from us.", h, ""+PURCHASE_COST);
                text.addPara("In return, we get fair payment for the weapons we deliver, and you supply a fraction of the more common materials we'll need for their construction.");
                options.addOption("Agree to pay for their services", OptionId.CONTINUE_BUY_SERVICE);
                if (Global.getSector().getPlayerFleet().getCargo().getCredits().get() < PURCHASE_COST) {
                    options.setEnabled(OptionId.CONTINUE_BUY_SERVICE, false);
                    options.setTooltip(OptionId.CONTINUE_BUY_SERVICE, "You don't have enough credits.");
                }
                options.addOption("Inform the agent you're not interested in their services at this moment.", OptionId.LEAVE_NONHOSTILE);
                break;
            case CONTINUE_BUY_SERVICE:
                text.addPara("'Pleasure doing business with you. Here:'");
                text.addPara("The man hands you a tiny tri-chip.");
                text.addPara("'This should provide you with a one-way encrypted comms channel to our sales department and a program for easily managing your orders. It should pop up under your normal administrative functions; just make an order and the program should requisition credits and other resources as necessary to our collection location. Then, we'll bring the wares to the agreed-upon drop point once they are ready.'", h, "normal administrative functions");

                text.setFontSmallInsignia();
                text.addPara("Lost " + PURCHASE_COST + " credits", n, h, "" + PURCHASE_COST);
                Global.getSector().getPlayerFleet().getCargo().getCredits().add(-1 * PURCHASE_COST);

                float currentPerturbaRelations = VassFamilyTrackerPlugin.getRelationToFamily(VassUtils.VASS_FAMILY.PERTURBA);
                if (VassFamilyTrackerPlugin.getRelationToFamily(VassUtils.VASS_FAMILY.PERTURBA) < RELATIONS_AFTER_DEAL) {
                    VassFamilyTrackerPlugin.modifyRelationToFamily(VassUtils.VASS_FAMILY.PERTURBA, RELATIONS_AFTER_DEAL - currentPerturbaRelations);
                    text.addPara("Relations with Perturba improved to "+Math.round(RELATIONS_AFTER_DEAL), h, "Perturba", ""+Math.round(RELATIONS_AFTER_DEAL));
                }
                if (Global.getSector().getPlayerFaction().getRelationshipLevel("vass").isAtBest(RepLevel.SUSPICIOUS)) {
                    text.addPara("Relations with the Vass Families improved to Neutral", h, "Vass Families", "Neutral");
                    Global.getSector().getPlayerFaction().setRelationship("vass", RepLevel.NEUTRAL);
                }
                text.setFontInsignia();

                text.addPara("'I hope this is the beginning of a long and profitable partnership for us both.'");

                for (String weapon : VassPerturbaWeaponContractEvent.UNLOCKED_WEAPONS) {
                    if (!Global.getSector().getPlayerFaction().knowsWeapon(weapon)) {
                        Global.getSector().getPlayerFaction().addKnownWeapon(weapon, true);
                    }
                }
                BarEventManager.getInstance().notifyWasInteractedWith(this);

                addIntel();
                options.addOption("Leave", OptionId.LEAVE);
                break;
            case LEAVE_NONHOSTILE:
                text.addPara("The Perturba agent looks mildly disappointed. 'Is that so? Well, just inform us if you change your mind; I'll probably come back now and then. This IS a rather nice bar, after all.'");

                options.addOption("Leave", OptionId.LEAVE);
                break;
            case LEAVE_HOSTILE:
                Global.getSoundPlayer().playUISound("vass_perturba_flashbang", 1f, 1f); // Flashbang SFX: he *did* just flashbang you!
                text.addPara("'Your loss.' The agent quickly pulls out something from their pocket and throws it in your direction. Before you can react a massive bang and a bright flash of light throws you to the ground.");
                text.addPara("By the time you've regained your senses, they are nowhere to be seen. Luckily it seems like the bar made it relatively unscathed, even if the guards and bar visitors are visibly shaken; the agent must have thrown some kind of riot suppression weapon.");
                text.addPara("You have a feeling Perturba won't be offering you any new deals any time soon.");

                text.setFontSmallInsignia();
                text.addPara("Relations with Perturba worsened to "+Math.round(RELATIONS_AFTER_BAD_DEAL), h, "Perturba", ""+Math.round(RELATIONS_AFTER_DEAL));
                text.setFontInsignia();

                VassFamilyTrackerPlugin.modifyRelationToFamily(VassUtils.VASS_FAMILY.PERTURBA,
                        -((-RELATIONS_AFTER_BAD_DEAL) + VassFamilyTrackerPlugin.getRelationToFamily(VassUtils.VASS_FAMILY.PERTURBA)));

                options.addOption("Leave", OptionId.LEAVE);
                break;
            case LEAVE:
                noContinue = true;
                done = true;
                break;
        }
    }

    //The description is slightly different if you've already met the Perturba contact before
    private String pickDescription() {
        WeightedRandomPicker<String> post = new WeightedRandomPicker<String>();
        Object hasMetContact = Global.getSector().getMemoryWithoutUpdate().get(VASS_PERTURBA_HAS_MET_CONTACT_KEY);
        if (hasMetContact instanceof Boolean && (Boolean)hasMetContact) {
            post.add("The Perturba contact is hanging around in the back of the room; looks like their offer still stands.");
            post.add("You spot Perturba's contact person having a drink at one of the more secluded tables. They nod as they spot you.");
        } else {
            post.add("A suspicious individual is hanging around near the back of the bar. They seem to be sneaking glances in your direction.");
            post.add("Someone is sitting in the bar, giving you quite obvious glances in between shots.");
        }
        return post.pick();
    }

    private String pickPrompt() {
        WeightedRandomPicker<String> post = new WeightedRandomPicker<String>();
        Object hasMetContact = Global.getSector().getMemoryWithoutUpdate().get(VASS_PERTURBA_HAS_MET_CONTACT_KEY);
        if (hasMetContact instanceof Boolean && (Boolean)hasMetContact) {
            post.add("Walk up to the Perturba agent.");
            post.add("Grab a drink and move up to the arms dealer.");
        } else {
            post.add("Confront the suspicious individual.");
            post.add("Sit down with the suspicious individual and see what they want with you.");
        }
        return post.pick();
    }
}
