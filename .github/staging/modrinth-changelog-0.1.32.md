## 0.1.32

- Publish the missing Remnant container-sort integration. The released 0.1.31 always targeted the player inventory from the inventory screen and did not call Remnant sorting providers.
- Middle-click now dispatches to the container under the cursor: expanded backpack sidebar, player inventory, or chest storage. Requires Remnant 0.2.32 for the latest backpack input handling.
- Keep configurable sorting bindings and Observer read-only protection.
- Add a packaged-artifact integration check to prevent release files from omitting client/server sorting provider dispatch.
