package com.example.myfirstappath;

/** One lecturer account as read from the database. */
public class Lecturer {

    public final long id;
    public final String name;
    public final String staffNumber;
    public final String email;
    public final String passwordHash;
    public final String passwordSalt;
    public final boolean activated;     // true once the activation link in the email has been opened
    public final boolean pendingReset;  // true after "Forgot password" sent a reset link that hasn't been used in the app yet

    public Lecturer(long id, String name, String staffNumber, String email,
                    String passwordHash, String passwordSalt, boolean activated, boolean pendingReset) {
        this.id = id;
        this.name = name;
        this.staffNumber = staffNumber;
        this.email = email;
        this.passwordHash = passwordHash;
        this.passwordSalt = passwordSalt;
        this.activated = activated;
        this.pendingReset = pendingReset;
    }

    public boolean passwordMatches(String typedPassword) {
        return PasswordHasher.matches(typedPassword, passwordSalt, passwordHash);
    }
}
