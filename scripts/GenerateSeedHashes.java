package scripts;


/**
 * One-shot helper: prints PBKDF2-HMAC-SHA256 salts and hashes for the eight
 * seeded DML users so the seeded passwords actually work with
 * UserService.login. Re-run only if the demo passwords change.
 *
 * Run from repo root after `mvn -q compile`:
 *   javac -d target/classes scripts/GenerateSeedHashes.java
 *   java  -cp target/classes scripts.GenerateSeedHashes
 *
 * Output goes to stdout. Paste into DML.sql.
 */
public class GenerateSeedHashes {
    public static void main(String[] args) {
        String[][] users = {
                {"manager_ali",    "ali2026"},
                {"manager_ayse",   "ayse2026"},
                {"manager_mehmet", "mehmet2026"},
                {"customer_ece",   "ece2026"},
                {"customer_berk",  "berk2026"},
                {"customer_selin", "selin2026"},
                {"customer_can",   "can2026"},
                {"customer_pinar", "pinar2026"},
        };
        for (String[] u : users) {
            String salt = org.example.util.PasswordUtil.generateSalt();
            String hash = org.example.util.PasswordUtil.hash(u[1], salt);
            System.out.printf("-- %-16s / %s%n", u[0], u[1]);
            System.out.printf("UPDATE Users SET password='%s', salt='%s' WHERE username='%s';%n%n",
                    hash, salt, u[0]);
        }
    }
}
