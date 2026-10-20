package io.github.geopoliticalwar;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * Initial server configuration surface.
 *
 * <p>This is deliberately small for the foundation phase. Each gameplay
 * subsystem will add its complete, commented configuration section without
 * changing the file name or registration contract.</p>
 */
@Mod.EventBusSubscriber(modid = GeopoliticalWarfare.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ServerConfig {
    public static final String MOD_NAME = "Geopolitical Warfare";
    public static final String MOD_VERSION = "0.1.0";
    public static final String MINECRAFT_VERSION = "1.20.1";

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.LongValue MAX_CLAIM_VOLUME;
    public static final ForgeConfigSpec.IntValue BASE_CLAIM_CAPACITY;
    public static final ForgeConfigSpec.IntValue POPULATION_PER_CLAIM;
    public static final ForgeConfigSpec.IntValue MAX_CLAIMS_PER_NATION;
    public static final ForgeConfigSpec.BooleanValue VANILLA_EXPLOSIVES_ENABLED;
    public static final ForgeConfigSpec.BooleanValue BEDROCK_DAMAGE_ENABLED;
    public static final ForgeConfigSpec.BooleanValue XAERO_OVERLAYS_ENABLED;
    public static final ForgeConfigSpec.BooleanValue SUPERB_WARFARE_ADAPTER_ENABLED;
    public static final ForgeConfigSpec.LongValue ELECTION_CYCLE_SECONDS;
    public static final ForgeConfigSpec.LongValue ELECTION_VOTING_WINDOW_SECONDS;
    public static final ForgeConfigSpec.LongValue NATION_REQUEST_EXPIRY_SECONDS;
    public static final ForgeConfigSpec.IntValue REFERENDUM_POPULATION_THRESHOLD;
    public static final ForgeConfigSpec.LongValue ACTIVE_POPULATION_WINDOW_SECONDS;
    public static final ForgeConfigSpec.LongValue POLICY_PROPOSAL_WINDOW_SECONDS;
    public static final ForgeConfigSpec.IntValue POLICY_QUORUM_BPS;
    public static final ForgeConfigSpec.IntValue POLICY_PASS_BPS;
    public static final ForgeConfigSpec.IntValue POLICY_MAX_OPEN_PROPOSALS;
    public static final ForgeConfigSpec.IntValue POLICY_MAX_HISTORY;
    public static final ForgeConfigSpec.LongValue POLICY_MAX_INTERVAL_SECONDS;
    public static final ForgeConfigSpec.LongValue POLICY_MAX_PAYMENT;
    public static final ForgeConfigSpec.LongValue POLICY_MAX_PROJECT_BUDGET;
    public static final ForgeConfigSpec.ConfigValue<String> DEFAULT_CURRENCY_ID;
    public static final ForgeConfigSpec.ConfigValue<String> DEFAULT_CURRENCY_NAME;
    public static final ForgeConfigSpec.IntValue MINTING_SPEED_TICKS;
    public static final ForgeConfigSpec.LongValue MINTING_NOTE_VALUE;
    public static final ForgeConfigSpec.LongValue MAX_CASH_WITHDRAWAL_AMOUNT;
    public static final ForgeConfigSpec.IntValue INFLATION_SCALING_BPS;
    public static final ForgeConfigSpec.LongValue INFLATION_INTERVAL_SECONDS;
    public static final ForgeConfigSpec.LongValue TAX_COLLECTION_INTERVAL_SECONDS;
    public static final ForgeConfigSpec.LongValue ROLE_PAYROLL_INTERVAL_SECONDS;
    public static final ForgeConfigSpec.IntValue CAPTURE_RADIUS_BLOCKS;
    public static final ForgeConfigSpec.LongValue CAPTURE_HOLD_SECONDS;
    public static final ForgeConfigSpec.IntValue CAPTURE_DECAY_PER_SECOND;
    public static final ForgeConfigSpec.LongValue SIEGE_HOLD_SECONDS;
    public static final ForgeConfigSpec.IntValue PROPERTY_TAX_PER_BLOCK;
    public static final ForgeConfigSpec.IntValue INCOME_TAX_BPS;
    public static final ForgeConfigSpec.IntValue SALES_TAX_BPS;
    public static final ForgeConfigSpec.IntValue DEFAULT_TARIFF_BPS;
    public static final ForgeConfigSpec.LongValue MATERIAL_TAX_TREASURY_CAPACITY;
    public static final ForgeConfigSpec.IntValue LEASE_MAX_MISSED_PAYMENTS;
    public static final ForgeConfigSpec.LongValue TREASURY_TRANSFER_COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.IntValue BEDROCK_DAMAGE_MULTIPLIER_BPS;
    public static final ForgeConfigSpec.ConfigValue<String> DEFAULT_OCCUPATION_RIGHTS;
    public static final ForgeConfigSpec.LongValue OCCUPATION_SETTLEMENT_SECONDS;
    public static final ForgeConfigSpec.IntValue REBELLION_MOMENTUM_THRESHOLD;
    public static final ForgeConfigSpec.IntValue REBELLION_DECAY_PER_MINUTE;
    public static final ForgeConfigSpec.IntValue PROPAGANDA_MOMENTUM;
    public static final ForgeConfigSpec.IntValue SABOTAGE_MOMENTUM;
    public static final ForgeConfigSpec.BooleanValue REBELLION_DEMOCRATIZES;
    public static final ForgeConfigSpec.BooleanValue CAPTIVE_FORCED_LABOR_ENABLED;
    public static final ForgeConfigSpec.IntValue CAPTIVE_WORK_INTERVAL_SECONDS;
    public static final ForgeConfigSpec.IntValue CAPTIVE_UNREST_PER_INTERVAL;
    public static final ForgeConfigSpec.IntValue CAPTIVE_UNREST_LIMIT;
    public static final ForgeConfigSpec.IntValue CAPTIVE_REBELLION_MOMENTUM;
    public static final ForgeConfigSpec.IntValue CAPTIVE_MAX_PER_CLAIM;
    public static final ForgeConfigSpec.LongValue LABOR_SITE_BUFFER_CAPACITY;
    public static final ForgeConfigSpec.LongValue FORECLOSURE_GRACE_SECONDS;

    static {
        BUILDER.comment("Geopolitical Warfare server configuration. Changes are server-authoritative.")
                .push("claims");
        MAX_CLAIM_VOLUME = BUILDER
                .comment("Maximum number of blocks in one volumetric claim.")
                .defineInRange("max_claim_volume", 4_194_304L, 1L, Long.MAX_VALUE);
        BASE_CLAIM_CAPACITY = BUILDER
                .comment("Nation claims available before population-based expansion.")
                .defineInRange("base_claim_capacity", 1, 0, Integer.MAX_VALUE);
        POPULATION_PER_CLAIM = BUILDER
                .comment("Additional population required for each additional nation claim.")
                .defineInRange("population_per_claim", 4, 1, Integer.MAX_VALUE);
        MAX_CLAIMS_PER_NATION = BUILDER
                .comment("Hard maximum number of claims a nation may own; population capacity may be lower.")
                .defineInRange("max_claims_per_nation", 256, 1, Integer.MAX_VALUE);
        BUILDER.pop();

        BUILDER.comment("Initial warfare defaults. External warfare mods remain optional.")
                .push("war");
        VANILLA_EXPLOSIVES_ENABLED = BUILDER
                .comment("Whether vanilla explosive sources may damage structures during formal war.")
                .define("vanilla_explosives_enabled", true);
        BEDROCK_DAMAGE_ENABLED = BUILDER
                .comment("Whether formal war may damage bedrock. Disabled by default for server safety.")
                .define("bedrock_damage_enabled", false);
        BUILDER.pop();

        BUILDER.comment("Governance timing uses real-world seconds and survives server restarts.")
                .push("governance");
        ELECTION_CYCLE_SECONDS = BUILDER
                .comment("Default interval between democratic elections.")
                .defineInRange("election_cycle_seconds", 604_800L, 60L, Long.MAX_VALUE);
        ELECTION_VOTING_WINDOW_SECONDS = BUILDER
                .comment("How long a democratic election remains open.")
                .defineInRange("election_voting_window_seconds", 604_800L, 60L, Long.MAX_VALUE);
        NATION_REQUEST_EXPIRY_SECONDS = BUILDER
                .comment("Real-world lifetime of a civilization invitation or application.")
                .defineInRange("nation_request_expiry_seconds", 604_800L, 60L, Long.MAX_VALUE / 1_000L);
        REFERENDUM_POPULATION_THRESHOLD = BUILDER
                .comment("Active population threshold below which a bill may fall back to a referendum.")
                .defineInRange("referendum_population_threshold", 3, 1, Integer.MAX_VALUE);
        ACTIVE_POPULATION_WINDOW_SECONDS = BUILDER
                .comment("Real-world activity window used to classify active citizens.")
                .defineInRange("active_population_window_seconds", 604_800L, 60L, Long.MAX_VALUE);
        POLICY_PROPOSAL_WINDOW_SECONDS = BUILDER
                .comment("Real-world time a democratic policy proposal remains open.")
                .defineInRange("policy_proposal_window_seconds", 86_400L, 60L, Long.MAX_VALUE / 1_000L);
        POLICY_QUORUM_BPS = BUILDER
                .comment("Minimum fraction of the proposal voter snapshot that must vote.")
                .defineInRange("policy_quorum_bps", 5_000, 0, 10_000);
        POLICY_PASS_BPS = BUILDER
                .comment("Minimum FOR fraction among cast policy votes.")
                .defineInRange("policy_pass_bps", 5_000, 0, 10_000);
        POLICY_MAX_OPEN_PROPOSALS = BUILDER
                .comment("Maximum simultaneous open policy proposals per nation.")
                .defineInRange("policy_max_open_proposals", 16, 1, Integer.MAX_VALUE);
        POLICY_MAX_HISTORY = BUILDER
                .comment("Maximum retained policy and law history entries.")
                .defineInRange("policy_max_history", 1_024, 1, Integer.MAX_VALUE);
        POLICY_MAX_INTERVAL_SECONDS = BUILDER
                .comment("Maximum recurring policy interval.")
                .defineInRange("policy_max_interval_seconds", 2_592_000L, 60L, Long.MAX_VALUE / 1_000L);
        POLICY_MAX_PAYMENT = BUILDER
                .comment("Maximum subsidy or scheduled printing amount per run.")
                .defineInRange("policy_max_payment", 1_000_000L, 1L, Long.MAX_VALUE);
        POLICY_MAX_PROJECT_BUDGET = BUILDER
                .comment("Maximum funded-project budget.")
                .defineInRange("policy_max_project_budget", 100_000_000L, 1L, Long.MAX_VALUE);
        BUILDER.pop();

        BUILDER.comment("Fixed-point monetary and tax defaults.")
                .push("economy");
        DEFAULT_CURRENCY_ID = BUILDER
                .comment("Namespaced ID of the default server currency.")
                .define("default_currency_id", "geopoliticalwar:credits");
        DEFAULT_CURRENCY_NAME = BUILDER
                .comment("Display name of the default server currency.")
                .define("default_currency_name", "Credits");
        MINTING_SPEED_TICKS = BUILDER
                .comment("Ticks required for a configured Minting Press job.")
                .defineInRange("minting_speed_ticks", 200, 1, Integer.MAX_VALUE);
        MINTING_NOTE_VALUE = BUILDER
                .comment("Ledger credits created by one blank banknote processed by a Minting Press.")
                .defineInRange("minting_note_value", 100L, 1L, Long.MAX_VALUE);
        MAX_CASH_WITHDRAWAL_AMOUNT = BUILDER
                .comment("Maximum ledger value converted into physical cash by one withdrawal.")
                .defineInRange("max_cash_withdrawal_amount", 100_000L, 1L, Long.MAX_VALUE);
        INFLATION_SCALING_BPS = BUILDER
                .comment("Supply-to-price scaling in basis points for the inflation engine.")
                .defineInRange("inflation_scaling_bps", 100, 0, Integer.MAX_VALUE);
        INFLATION_INTERVAL_SECONDS = BUILDER
                .comment("Real-world interval between persisted inflation recalculations.")
                .defineInRange("inflation_interval_seconds", 86_400L, 60L, Long.MAX_VALUE / 1_000L);
        TAX_COLLECTION_INTERVAL_SECONDS = BUILDER
                .comment("Real-world interval between scheduled tax collection passes.")
                .defineInRange("tax_collection_interval_seconds", 86_400L, 60L, Long.MAX_VALUE);
        ROLE_PAYROLL_INTERVAL_SECONDS = BUILDER
                .comment("Real-world interval between scheduled role payroll passes.")
                .defineInRange("role_payroll_interval_seconds", 86_400L, 60L, Long.MAX_VALUE / 1_000L);
        FORECLOSURE_GRACE_SECONDS = BUILDER
                .comment("Real-world grace period after a missed property tax payment before foreclosure.")
                .defineInRange("foreclosure_grace_seconds", 604_800L, 60L, Long.MAX_VALUE);
        BUILDER.pop();

        BUILDER.comment("Capture, conquest, and destruction timing.")
                .push("warfare");
        CAPTURE_RADIUS_BLOCKS = BUILDER
                .comment("Default radius for an occupy-and-hold capture point.")
                .defineInRange("capture_radius_blocks", 8, 1, 128);
        CAPTURE_HOLD_SECONDS = BUILDER
                .comment("Real-world seconds required to capture a point while uncontested.")
                .defineInRange("capture_hold_seconds", 300L, 1L, Long.MAX_VALUE);
        CAPTURE_DECAY_PER_SECOND = BUILDER
                .comment("Capture progress lost per real-world second while contested or unattended.")
                .defineInRange("capture_decay_per_second", 1, 0, Integer.MAX_VALUE);
        SIEGE_HOLD_SECONDS = BUILDER
                .comment("Real-world seconds required for an uncontested siege anchor to complete a conquest goal.")
                .defineInRange("siege_hold_seconds", 600L, 1L, Long.MAX_VALUE);
        TREASURY_TRANSFER_COOLDOWN_SECONDS = BUILDER
                .comment("Cooldown before a breached treasury can be transferred to a victor.")
                .defineInRange("treasury_transfer_cooldown_seconds", 86_400L, 1L, Long.MAX_VALUE);
        BEDROCK_DAMAGE_MULTIPLIER_BPS = BUILDER
                .comment("Damage multiplier for bedrock in basis points; zero keeps bedrock protected.")
                .defineInRange("bedrock_damage_multiplier_bps", 0, 0, Integer.MAX_VALUE);
        BUILDER.pop();

        BUILDER.comment("Tax rates are expressed in basis points; 100 bps equals one percent.")
                .push("taxes");
        PROPERTY_TAX_PER_BLOCK = BUILDER
                .comment("Default fixed-point property tax per claimed block per collection pass.")
                .defineInRange("property_tax_per_block", 0, 0, Integer.MAX_VALUE);
        INCOME_TAX_BPS = BUILDER
                .comment("Default income-tax rate in basis points.")
                .defineInRange("income_tax_bps", 0, 0, 10_000);
        SALES_TAX_BPS = BUILDER
                .comment("Default sales-tax rate in basis points.")
                .defineInRange("sales_tax_bps", 0, 0, 10_000);
        DEFAULT_TARIFF_BPS = BUILDER
                .comment("Default tariff charged on cross-civilization treasury trade in basis points.")
                .defineInRange("default_tariff_bps", 0, 0, 10_000);
        MATERIAL_TAX_TREASURY_CAPACITY = BUILDER
                .comment("Maximum number of physical item units held by the tax treasury.")
                .defineInRange("material_tax_treasury_capacity", 1_000_000L, 1L, Long.MAX_VALUE);
        LEASE_MAX_MISSED_PAYMENTS = BUILDER
                .comment("Missed rent cycles before an active lease is evicted.")
                .defineInRange("lease_max_missed_payments", 3, 1, Integer.MAX_VALUE);
        BUILDER.pop();

        BUILDER.comment("Rights applied to defeated populations after conquest.")
                .push("occupation");
        DEFAULT_OCCUPATION_RIGHTS = BUILDER
                .comment("Default occupation rights: equal_citizen, resident, subject, restricted, or none.")
                .defineInList("default_occupation_rights", "subject", java.util.List.of(
                        "equal_citizen", "resident", "subject", "restricted", "none"
                ));
        OCCUPATION_SETTLEMENT_SECONDS = BUILDER
                .comment("Real-world cooldown before an achieved conquest may settle a claim.")
                .defineInRange("occupation_settlement_seconds", 86_400L, 1L, Long.MAX_VALUE / 1_000L);
        BUILDER.pop();

        BUILDER.comment("Rebellion pressure, propaganda, and sabotage.")
                .push("rebellion");
        REBELLION_MOMENTUM_THRESHOLD = BUILDER
                .comment("Momentum required to mark a rebellion as a full civil-war crisis.")
                .defineInRange("momentum_threshold", 100, 1, Integer.MAX_VALUE);
        REBELLION_DECAY_PER_MINUTE = BUILDER
                .comment("Momentum lost per real-world minute without pressure events.")
                .defineInRange("decay_per_minute", 1, 0, Integer.MAX_VALUE);
        PROPAGANDA_MOMENTUM = BUILDER
                .comment("Momentum added by one propaganda action.")
                .defineInRange("propaganda_momentum", 5, 1, Integer.MAX_VALUE);
        SABOTAGE_MOMENTUM = BUILDER
                .comment("Momentum added by one infrastructure sabotage action.")
                .defineInRange("sabotage_momentum", 10, 1, Integer.MAX_VALUE);
        REBELLION_DEMOCRATIZES = BUILDER
                .comment("Whether a rebellion reaching the momentum threshold converts a dictatorship to a democracy.")
                .define("rebellion_democratizes", true);
        BUILDER.pop();

        BUILDER.comment("Occupied-population captive and compulsory-labor mechanics.")
                .push("captives");
        CAPTIVE_FORCED_LABOR_ENABLED = BUILDER
                .comment("Whether an authorized occupier may create player captive records in occupied claims.")
                .define("forced_labor_enabled", true);
        CAPTIVE_WORK_INTERVAL_SECONDS = BUILDER
                .comment("Real-world work accounting interval for active captives.")
                .defineInRange("work_interval_seconds", 60, 1, Integer.MAX_VALUE);
        CAPTIVE_UNREST_PER_INTERVAL = BUILDER
                .comment("Unrest added when a captive is away from the occupied claim during a work interval.")
                .defineInRange("unrest_per_interval", 1, 0, Integer.MAX_VALUE);
        CAPTIVE_UNREST_LIMIT = BUILDER
                .comment("Unrest at which a captive automatically rebels.")
                .defineInRange("unrest_limit", 10, 1, Integer.MAX_VALUE);
        CAPTIVE_REBELLION_MOMENTUM = BUILDER
                .comment("Rebellion momentum added by captive escape or forced-labor unrest.")
                .defineInRange("rebellion_momentum", 15, 1, Integer.MAX_VALUE);
        CAPTIVE_MAX_PER_CLAIM = BUILDER
                .comment("Maximum active captive records assigned to one occupied claim.")
                .defineInRange("max_per_claim", 500, 1, Integer.MAX_VALUE);
        LABOR_SITE_BUFFER_CAPACITY = BUILDER
                .comment("Maximum buffered item units for one captive labor site.")
                .defineInRange("labor_site_buffer_capacity", 100_000L, 1L, Long.MAX_VALUE);
        BUILDER.pop();

        BUILDER.comment("Optional compatibility adapters.")
                .push("compatibility");
        XAERO_OVERLAYS_ENABLED = BUILDER
                .comment("Whether the optional Xaero's Minimap overlay adapter may register when Xaero is installed.")
                .define("xaero_overlays_enabled", true);
        SUPERB_WARFARE_ADAPTER_ENABLED = BUILDER
                .comment("Whether the optional Superb Warfare adapter may register when Superb Warfare is installed.")
                .define("superb_warfare_adapter_enabled", true);
        BUILDER.pop();
    }

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private ServerConfig() {
    }

    @SubscribeEvent
    public static void onConfigLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            GeopoliticalWarfare.LOGGER.info(
                    "Loaded conquest_config.toml: maxClaimVolume={}, vanillaExplosives={}, bedrockDamage={}",
                    MAX_CLAIM_VOLUME.get(),
                    VANILLA_EXPLOSIVES_ENABLED.get(),
                    BEDROCK_DAMAGE_ENABLED.get()
            );
        }
    }
}
