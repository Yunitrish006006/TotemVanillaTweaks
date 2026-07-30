# TotemVanillaTweaks

TotemVanillaTweaks owns general vanilla gameplay changes: the lectern recipe
override and bookshelf recipe removal, item-entity concrete-powder hardening,
furnace experience release when a hopper extracts a result, and client-requested
sorting of either side of an open container menu. Middle-click is the default
sort binding. It also converts ordinary bookshelves found in survival
inventories into three books and fills generated structure bookshelves. It
depends only on TotemCore and Fabric API, not on another feature module.

`0.1.3` is the current candidate built against TotemCore `0.2.0`; `0.1.0`
remains the immutable rollback artifact.

Portable-container policy is not part of this repository: TotemRemnant already
owns that authority. Alchemy-specific Stone Bowl recipes also remain owned by
TotemAlchemy so this module never adds a feature-module dependency.
