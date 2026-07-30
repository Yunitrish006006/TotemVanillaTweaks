# Extraction contract

TotemVanillaTweaks owns the lectern recipe override and `RecipeManagerMixin`,
`ConcretePowderItemHardening`, `ConcretePowderItemEntityMixin`,
`HopperBlockEntityMixin` with `AbstractFurnaceBlockEntityAccessor`, and other
small vanilla gameplay rules without a more specific Totem owner. It also owns
the `deadrecall:sort_backpack` payload, server-authoritative open-menu sorting,
the middle-click key binding and the container-screen client hooks.
It also owns the survival-inventory bookshelf replacement tick rule and the
`StructureTemplateMixin` that converts generated bookshelves into filled
chiseled bookshelves.

It must not duplicate portable-container policy, backpack item behavior,
Alchemy Cauldron gameplay (including Stone Bowl recipes), enchanting behavior
or existing Automata/Nexus registrations. The transferred standalone GameTests
cover lectern behavior, concrete-powder hardening, hopper furnace experience,
sorting both sides of an open menu and survival bookshelf conversion.
