# scripts/

One-shot helpers run from the repo root. **Not** part of the production build
(this directory lives outside `src/main/java`).

| File | Purpose |
|---|---|
| `GenerateSeedHashes.java` | Regenerates PBKDF2 salts and hashes for the eight seeded DML users (passwords like `ali2026`). Paste the resulting `UPDATE` statements into `DML.sql` if you change the demo passwords. |
| `GeneratePlaceholderImages.java` | Writes a `200x200` placeholder JPEG to `./img/` for every menu-item path referenced in `DML.sql`. Run this if you ever wipe the `img/` directory or want fresh placeholder tiles. |

## Usage

```bash
mvn -q compile
javac -d target/classes scripts/GenerateSeedHashes.java
java  -cp target/classes scripts.GenerateSeedHashes        # prints SQL
java  -cp target/classes scripts.GeneratePlaceholderImages # writes JPEGs
```
