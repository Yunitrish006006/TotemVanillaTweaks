# Extraction contract

TotemVanillaTweaks will own general recipe overrides and `RecipeManagerMixin`,
`ConcretePowderItemHardening`, `ConcretePowderItemEntityMixin`, and any other
small vanilla gameplay rule without a more specific Totem owner.

It must not duplicate portable-container policy, backpack item behavior,
Alchemy Cauldron gameplay, enchanting behavior or existing Automata/Nexus
registrations. Inventory all affected recipes, data resources, Mixins and
GameTests before moving implementation out of DeadRecall.
