package gitlet;

import java.io.File;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

import static gitlet.Utils.*;

public class Commit implements Serializable {

    /** The message of this Commit. */
    private String message;
    /** The commit time */
    private String timestamp;
    /** Linkage between Commits */
    private String parentID;
    /** Commit SHA1 */
    private String ID;
    /** Merge info */
    private boolean isMerge;
    private String mergeParents;
    private String otherParent;
    /** Blob log
     *  File name, sha1 */
    private HashMap<String, String> commitFiles;


    /** Constructor for none-merge */
    public Commit(String message, String parentID) {
        // Log message
        this.isMerge = false;
        this.message = message;
        this.parentID = parentID;

        SimpleDateFormat timeEpoch = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy Z");
        if (parentID == null) {
            this.timestamp = timeEpoch.format(new Date(0));
        } else {
            this.timestamp = timeEpoch.format(new Date());
        }
        this.ID = makeID();
        commitFiles = new HashMap<>();
    }

    /** Constructor for merge */
    public Commit(String mainName, String mergeInName, String mainID, String mergeInID) {
        // Log message
        this.otherParent = mergeInID;
        this.isMerge = true;
        this.message = "Merged " + mergeInName + " into " + mainName + ".";
        this.parentID = mainID;
        this.mergeParents = "Merge: " + mainID.substring(0, 7) + " " + mergeInID.substring(0, 7);

        SimpleDateFormat timeEpoch = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy Z");
        if (parentID == null) {
            this.timestamp = timeEpoch.format(new Date(0));
        } else {
            this.timestamp = timeEpoch.format(new Date());
        }
        this.ID = makeID();
        commitFiles = new HashMap<>();
    }

    public void addStaged(HashMap<String, String> outStage) {
        commitFiles.putAll(outStage);
    }

    public void saveCommit() {
        File newSave = join(".gitlet", ID);
        writeObject(newSave, this);
    }

    private String makeID() {
        if (parentID == null) {
            return sha1(timestamp, message);
        }
        return sha1(timestamp, message, parentID);
    }

    public String getID() {
        return ID;
    }

    public String getMessage() {
        return this.message;
    }

    public String getTimestamp() {
        return this.timestamp;
    }

    public String getParent() {
        return this.parentID;
    }

    public HashMap<String, String> getList() {
        return commitFiles;
    }

    public boolean isMerge() {
        return isMerge;
    }
    public String getParents() {
        return mergeParents;
    }

    public String getOtherParent() {
        return otherParent;
    }
}
