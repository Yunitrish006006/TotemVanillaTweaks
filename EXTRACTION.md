# Extraction contract

TotemVanillaTweaks owns the lectern recipe override and `RecipeManagerMixin`,
`ConcretePowderItemHardening`, `ConcretePowderItemEntityMixin`,
`HopperBlockEntityMixin` with `AbstractFurnaceBlockEntityAccessor`, and other
small vanilla gameplay rules without a more specific Totem owner.

It must not duplicate portable-container policy, backpack item behavior,
Alchemy Cauldron gameplay (including Stone Bowl recipes), enchanting behavior
or existing Automata/Nexus registrations. The transferred standalone GameTests
cover lectern behavior, concrete-powder hardening and hopper furnace experience.
