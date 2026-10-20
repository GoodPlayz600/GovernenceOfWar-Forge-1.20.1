# Geopolitical Warfare

Geopolitical Warfare is a server-authoritative Minecraft Forge 1.20.1 mod for
three-dimensional claims, nations, governance, economy, rebellion, conquest,
and tactical warfare.

The project is being built in small, tested phases. The current implementation
includes the reproducible Forge project plus the first usable claim, nation,
governance, economy, capture, rebellion, estate, tax, role, and payroll slices.
The remaining blueprint systems are being added incrementally behind the same
server-authoritative APIs.

## Requirements

- Java 17 (64-bit)
- A Linux, macOS, or Windows development environment supported by ForgeGradle
- Network access for the first Gradle dependency bootstrap

The project pins Minecraft 1.20.1, Forge 1.20.1-47.4.10, official Mojang
mappings, and Gradle through the checked-in wrapper.

## Bootstrap

From the project root:

```text
./gradlew build
```

The first build downloads the pinned ForgeGradle and Minecraft development
artifacts. It produces the mod jar in `build/libs/`.

### Standalone Forge 1.20.1 jar smoke test

The development server is useful for debugging, but the release jar should
also be tested in a clean Forge server directory. The mod has no required
runtime mod dependencies.

From the project root, build the re-obfuscated jar:

```bash
export JAVA_HOME=/path/to/jdk-17
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew --no-daemon clean build
ls -l build/libs/geopoliticalwar-0.1.0.jar
```

Then create a separate test server directory and install the exact Forge
version pinned by this project:

```bash
mkdir -p ../geopoliticalwar-forge-test/mods
cd ../geopoliticalwar-forge-test
curl -fL -o forge-installer.jar \
  https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-47.4.10/forge-1.20.1-47.4.10-installer.jar
java -jar forge-installer.jar --installServer
cp ../mcmod/build/libs/geopoliticalwar-0.1.0.jar mods/
printf 'eula=true\n' > eula.txt
./run.sh nogui
```

Use the generated `run.sh` rather than launching the installer again. On the
first launch, confirm that `logs/latest.log` contains the mod ID
`geopoliticalwar` and no `ModLoadingException`. Stop the server with `stop`,
restart it once, and verify that the saved nation, claim, economy, and
government state remains available. For a quick command smoke test, join as
an operator and run:

```text
/geopoliticalwar nation create TestNation DICTATORSHIP
/geopoliticalwar nation status
/geopoliticalwar law decree PAYROLL set multiplier_bps=12500
/geopoliticalwar law status
/geopoliticalwar law history
```

To test democratic legislation, create a second nation as a citizen, submit a
proposal, vote with the eligible citizens before the configured window closes,
then inspect it with `law proposal <proposal-uuid>` and finalize it after the
window. The exact policy fields are listed below. For a clean regression run,
remove the test server directory and repeat the install with a newly built
jar; do not use the development `run/` world as evidence for a clean jar test.

## Development runs

Launch a client:

```text
make run-client
```

Launch the ForgeGradle development server:

```text
make run-server
```

The first server launch creates `run/eula.txt`. Set `eula=true` in that local
development file before launching again.

## Tests

The single project check command is:

```text
make test
```

This runs a clean offline Gradle build after the initial dependency bootstrap:

```text
./gradlew --no-daemon --offline clean build
```

GameTests will be added as gameplay systems become available:

```text
make run-gametest
```

## Current command workflows

The mod treats a nation as a civilization: it owns territory, a treasury,
roles, governance, and a membership roster. Membership is server-owned and is
not inferred from a client packet or a player name.

The initial claim workflow uses `/geopoliticalwar claim select1`,
`select2`, `create <name> <zone>`, `list`, `info`, and `delete <uuid>`.
Claims may optionally be created for a nation with
`create <name> <zone> <nation-uuid>`. Nation citizens and scoped role
permissions are checked on the server.

Claim owners and nation administrators can manage persisted ACL rules with:

```text
/geopoliticalwar claim grant <claim-uuid> <subject> <permission>
/geopoliticalwar claim deny <claim-uuid> <subject> <permission>
/geopoliticalwar claim revoke <claim-uuid> <subject> <permission>
```

Subjects use stable IDs such as `player:<uuid>`, `nation:<uuid>`, and
`role:<id>`. Permission nodes are hierarchical; for example, granting
`claim.build` also permits the more specific `claim.build.place` check.

Administrators can define and assign roles:

```text
/geopoliticalwar role create <id> <display> <wage> [permission1,permission2]
/geopoliticalwar role assign <player-uuid> <role-id> [nation-uuid]
/geopoliticalwar role assign-nation <player-uuid> <role-id> <nation-uuid>
/geopoliticalwar role revoke <player-uuid> <role-id> [nation-uuid]
/geopoliticalwar role revoke-nation <player-uuid> <role-id> <nation-uuid>
/geopoliticalwar role list
/geopoliticalwar role self
```

### Civilization onboarding

```text
/geopoliticalwar nation create <name> <regime>
/geopoliticalwar nation list
/geopoliticalwar nation info <nation-uuid>
/geopoliticalwar nation status
/geopoliticalwar nation government
/geopoliticalwar nation admission <open|application|invite_only|closed>
/geopoliticalwar nation join <nation-uuid>
/geopoliticalwar nation immigrate <nation-uuid> [citizen|resident]
/geopoliticalwar nation apply <nation-uuid>
/geopoliticalwar nation invite <player-uuid>
/geopoliticalwar nation requests
/geopoliticalwar nation accept invite <request-uuid>
/geopoliticalwar nation accept application <request-uuid>
/geopoliticalwar nation decline <request-uuid>
/geopoliticalwar nation transfer <player-uuid>
/geopoliticalwar nation leave
```

`OPEN` civilizations accept direct joins. `APPLICATION` civilizations require
an application and leader/membership-manager approval. `INVITE_ONLY`
civilizations require an invitation. `CLOSED` civilizations reject both.
Requests expire using `nation_request_expiry_seconds` and persist across
restarts. Citizens may vote and hold office; residents are admitted without
political office or voting rights; subjects are created by occupation. A
leader must transfer leadership before leaving. The `gwar nation` alias is
available for this workflow.

Population is reconciled from citizens, residents, and subjects and persisted
separately so old worlds migrate safely. A one-person nation starts with one
nation claim; every configured population increment adds another claim slot up
to the hard ceiling. Existing claims are never deleted when a nation loses
population, but new expansion is blocked until the population can support it.
`immigrate` chooses direct admission for an open civilization and creates an
application for an application-based civilization, making the normal path a
single command.

### Government policy laws

Dictatorship leaders (or administrators) can enact policy decrees immediately.
Eligible citizens of democracies can open proposals, vote once per proposal
(their vote may be changed before closing), and inspect the result after the
configured proposal window. Quorum and pass thresholds are configured with
`policy_quorum_bps` and `policy_pass_bps`.

```text
/geopoliticalwar law decree <category> set <key=value ...>
/geopoliticalwar law decree <category> clear
/geopoliticalwar law propose <category> set <key=value ...>
/geopoliticalwar law propose <category> clear
/geopoliticalwar law vote <proposal-uuid> <FOR|AGAINST>
/geopoliticalwar law finalize <proposal-uuid>
/geopoliticalwar law proposal <proposal-uuid>
/geopoliticalwar law status
/geopoliticalwar law history
```

The supported policy categories and fields are:

* `TAXES`: `property_mode=LEDGER|DIAMONDS|ITEM`, `property_rate`, optional
  `property_currency` and `property_item`, `income_bps`, `sales_bps`,
  `tariff_bps`, and `revenue=SYSTEM_TAX|NATION_TREASURY`.
* `PAYROLL`: `multiplier_bps` (10,000 is 100%).
* `SUBSIDIES`: `currency`, `amount`, `interval`, and
  `scope=CITIZENS|CITIZENS_AND_RESIDENTS`.
* `MONEY_PRINTING`: `currency`, `amount`, and `interval`.
* `FUNDED_PROJECTS`: `name`, `recipient`, `currency`, `budget`,
  `installment`, and `interval`.

For example:

```text
/geopoliticalwar law decree PAYROLL set multiplier_bps=12500
/geopoliticalwar law propose TAXES set income_bps=750 sales_bps=500 revenue=NATION_TREASURY
/geopoliticalwar law propose SUBSIDIES set currency=geopoliticalwar:credits amount=100 interval=86400 scope=CITIZENS
```

Recurring payments are treasury-funded. Underfunded subsidies and projects are
retried rather than paid partially, and recurring transfers are recorded with
stable job IDs so a restart cannot duplicate an already completed payment.

Role payroll is paid once per configured interval from either the persisted
`system:payroll` account or a nation treasury account. Administrators can fund
those accounts with `/geopoliticalwar money mint-payroll <amount>` and
`/geopoliticalwar money mint-nation <nation-uuid> <amount>`. Payroll skips
underfunded sources rather than paying only part of a source's assignment set.
Democracies may delegate nation-role appointments to a scoped role with
`nation.membership.manage`; dictatorships keep role appointments with the
leader while still allowing explicitly delegated operational permissions.

### Money, cash, taxes, and trade

```text
/geopoliticalwar money balance
/geopoliticalwar money pay <player-uuid> <amount>
/geopoliticalwar money withdraw <amount>
/geopoliticalwar money deposit
/geopoliticalwar money audit
/geopoliticalwar treasury balance <nation-uuid>
/geopoliticalwar treasury pay <nation-uuid> <player-uuid> <amount>
/geopoliticalwar trade tariff <amount>
/geopoliticalwar trade transfer <from-nation> <to-nation> <amount>
/geopoliticalwar tax status
/geopoliticalwar tax status <claim-uuid>
/geopoliticalwar tax set <claim-uuid> <ledger|diamonds|item> <rate-per-block>
/geopoliticalwar tax clear <claim-uuid>
/geopoliticalwar tax pay <claim-uuid>
/geopoliticalwar tax treasury
/geopoliticalwar estate rent <claim-uuid> <rent-per-cycle>
/geopoliticalwar estate lease <listing-uuid>
/geopoliticalwar estate terminate <lease-uuid>
/geopoliticalwar estate evict <lease-uuid>
/geopoliticalwar estate status <claim-uuid>
/geopoliticalwar estate leases
/geopoliticalwar estate lease-status <lease-uuid>
/geopoliticalwar estate history <claim-uuid>
/geopoliticalwar estate transfer player <claim-uuid> <player-uuid>
/geopoliticalwar estate transfer nation <claim-uuid> <nation-uuid>
```

Balances use fixed-point ledger units. `withdraw` converts an account balance
into authenticated denomination-bearing banknotes and coins; `deposit` only
accepts issued cash with a currency ID, value, and serial UUID. Blank creative
banknotes have no value and cannot be deposited. Cash withdrawal and deposit
move value between the player account and the canonical `system:cash` account;
they do not create or destroy supply.

Role payroll withholds `income_tax_bps`. Estate sales apply `sales_tax_bps`.
Property tax is assessed per claim volume and can use the fixed-point ledger,
diamonds, or an arbitrary item held while configuring the policy. Physical
tender is removed atomically from the online player owner (or an eligible
citizen acting as the nation treasury representative) and stored in a bounded tax
material treasury. The debtor is the current claim owner, including a nation
treasury for nation-owned property. Physical tax that cannot be collected
enters the same persisted grace/foreclosure workflow as monetary tax.
For `item` policies, hold one exemplar item while running `tax set`; the
server stores the namespaced item type and never trusts client-supplied NBT.
Operators can inspect and withdraw collected material with `tax treasury` and
`tax treasury withdraw <item-id> <amount>`.
Cross-civilization treasury trade applies `default_tariff_bps` and records a
separate tariff ledger leg. The persisted inflation pass adjusts currency
purchasing power from supply pressure using `inflation_scaling_bps`. The
operator audit command shows recent ledger entries; every economic action uses
the same fixed-point ledger rather than a second balance store.

Estate rent listings create persisted leases. A tenant accepts a rental
listing with `estate lease`; the scheduled lease collector transfers rent to
the current landlord account and records missed payments without inventing a
second currency balance. Active leases block sale, re-rental, and conquest
settlement until the lease is explicitly cleaned up.
The tenant, current claim manager, or an administrator can explicitly clean up a
lease with `estate terminate` before conquest settlement.
While active, a lease grants its tenant container/interaction access to the
leased claim but not build or claim-management rights.
Rent collectors evict a tenant after the configured missed-payment threshold,
and stale leases are ended if the claim owner changes. An estate board opens a
read-only server-generated status screen showing ownership, listings, lease,
tax, and occupation data; commands remain the only mutation path.
Ownership transfers clear inherited ACLs, remove stale listings, reject active
leases/occupations/war references/unpaid tax, and append bounded ownership
history that can be inspected with `estate history`.

Nation citizens can inspect their treasury with
`/geopoliticalwar treasury balance <nation-uuid>`. Nation leaders or roles
with `economy.treasury` can pay a player using
`/geopoliticalwar treasury pay <nation-uuid> <player-uuid> <amount>`.
Stand on a Capital Vault or Reserve Vault and use
`/geopoliticalwar vault bind <nation-uuid>` to bind the block, then use
`/geopoliticalwar vault status` for a server-authoritative balance view.

## Formal warfare and conquest

The formal war workflow is server-persisted and separate from the legacy
phase-administration commands:

```text
/geopoliticalwar war declare <attacker-nation> <defender-nation>
/geopoliticalwar war goal claim <war> <attacker-nation> <defender-claim>
/geopoliticalwar war goal capture <war> <beneficiary-nation> <capture-point>
/geopoliticalwar war activate <war>
/geopoliticalwar war status
/geopoliticalwar war resolve <war> <victor-nation>
/geopoliticalwar war settle <claim>
```

Formal wars require distinct existing nations and cannot overlap on the same
dimension, claim target, or capture point. A conquest claim must be owned by a
defender nation and must name an attacking beneficiary. Capture points and
siege anchors only progress against active formal goals; neutral players do
not silently gain wartime access.

Capture is side-aware and contested. Each server pass classifies eligible
non-spectator players as attacker-side, defender-side, or neutral. The goal
beneficiary attacker must be present and no defender or neutral player may be
inside the capture radius for progress to advance. Allied attackers may hold
together, but only the configured beneficiary can complete the objective.
Contested or unattended progress decays at `capture_decay_per_second` instead
of resetting instantly. Capture goals use their required hold duration,
persist progress, reject duplicate targets, and cannot be deleted while
referenced by any formal war. Completion updates both the point owner and the
war goal on the server.

Place a `siege_anchor` inside an active conquest target as a participating
attacker, then right-click it to bind it. The anchor tracks uncontested hold
time across saves and completes the conquest goal server-side. Once the war is
resolved, the occupation cooldown must elapse before `/war settle` transfers
the claim. Settlement clears inherited ACLs and removes estate/tax records for
the transferred claim.

Occupation rights are persisted and affect access during the cooldown:
occupiers may administer the claim, while the former population receives the
configured `equal_citizen`, `resident`, `subject`, `restricted`, or `none`
rights.

An authorized occupier can capture a former-population player during an active
occupation:

```text
/geopoliticalwar captive list
/geopoliticalwar captive status <player-uuid>
/geopoliticalwar captive capture <player-uuid> <claim-uuid>
/geopoliticalwar captive liberate <player-uuid>
/geopoliticalwar captive release <player-uuid>
/geopoliticalwar captive rebel <player-uuid>
/geopoliticalwar captive site list
/geopoliticalwar captive site create <claim-uuid> <item-id> <output-per-cycle>
/geopoliticalwar captive site status <site-uuid>
/geopoliticalwar captive site collect <site-uuid>
```

Captive records persist across restarts. Active captives are tracked at their
occupied claim as compulsory labor, may perform ordinary non-agricultural
activities, and cannot farm or cut trees. Another nation, former civilians, an
operator, or the captive's own rebellion can end the record. Time away from
the occupied claim increases unrest; unrest can trigger rebellion momentum and
a transition to democracy when that server policy is enabled.
Occupiers can create bounded labor sites inside the occupation. Each active
each active captive who reports to the claim contributes the configured output
rate to the site buffer, and the occupying nation can collect that output as
ordinary Minecraft items. Output buffers and captive counts are capped by
server configuration.

## Strategic blocks

Strategic blocks now have real server-side state rather than being decorative
registrations. `capture_beacon`, `siege_anchor`, and the vault/anchor family
persist durable links without duplicating the canonical SavedData records.
`minting_press` has a one-slot menu and a restart-safe, idempotent ledger job:
insert a `banknote`, open the press, and start the job. A completed job is
recorded by UUID in `EconomySavedData`, so a save/restart boundary cannot mint
the same job twice.

## Optional integrations

The base mod does not require another warfare mod or minimap mod.

- Superb Warfare compatibility targets Forge 1.20.1 release 0.8.9.2.
- Xaero's Minimap compatibility targets Forge 1.20.1 release 26.6.0.

These mods are detected at runtime. Their jars and assets are not bundled with
Geopolitical Warfare. Vanilla combat and vanilla explosive sources remain the
base warfare path when no optional integration is installed.

## Server configuration

Forge creates the server configuration at:

```text
<world>/serverconfig/conquest_config.toml
```

The current settings cover claim limits and population-scaled expansion, vanilla war explosives, bedrock
damage, election timing, fixed-point currency, minting, tax collection,
inflation, tariffs, civilization admission, capture difficulty/decay,
rebellion, occupation, role payroll, physical tax stockpiles, lease eviction,
captive labor/unrest, and optional adapter toggles.
Later gameplay phases extend the same file with logistics, settlement, and
block-damage settings.

During `WAR` or `REBELLION`, the server filters vanilla explosion block lists
through the configured explosive and bedrock-damage gates. Operators bypass
claim protection and destruction filtering for administrative recovery.

## Deployment

Build the distributable jar:

```text
make build
```

Install the resulting `build/libs/geopoliticalwar-*.jar` into the `mods/`
directory of a Forge 1.20.1 server. Install optional compatibility mods
separately when their features are desired.

The Docker image performs the project build and starts the ForgeGradle
development server:

```text
docker build -t geopoliticalwar:dev .
docker run --rm -it -p 25565:25565 geopoliticalwar:dev
```
