#!/usr/bin/env bash
set -euo pipefail

cat > src/main/resources/totem-vanilla-tweaks.mixins.json <<'EOF'
{
  "required": true,
  "package": "dev.totem.vanillatweaks.mixin",
  "compatibilityLevel": "JAVA_25",
  "mixins": [
    "AbstractArrowPickupMixin",
    "AbstractFurnaceBlockEntityAccessor",
    "AbstractSkeletonAmmoMixin",
    "ConcretePowderItemEntityMixin",
    "HopperBlockEntityMixin",
    "RecipeManagerMixin",
    "StructureTemplateMixin"
  ],
  "client": [],
  "injectors": {
    "defaultRequire": 1
  }
}
EOF

! grep -n 'Observer' src/main/resources/totem-vanilla-tweaks.mixins.json
